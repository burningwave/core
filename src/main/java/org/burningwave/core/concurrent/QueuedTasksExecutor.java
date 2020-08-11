/*
 * This file is part of Burningwave Core.
 *
 * Author: Roberto Gentili
 *
 * Hosted at: https://github.com/burningwave/core
 *
 * --
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 Roberto Gentili
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without
 * limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
 * EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.burningwave.core.concurrent;

import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Supplier;

import org.burningwave.core.Component;
import org.burningwave.core.ManagedLogger;
import org.burningwave.core.function.ThrowingRunnable;
import org.burningwave.core.function.ThrowingSupplier;

@SuppressWarnings({"unchecked", "resource"})
public class QueuedTasksExecutor implements Component {
	private final static Map<String, TaskAbst<?, ?>> runOnlyOnceTasksToBeExecuted;
	private Mutex.Manager mutexManager;
	private String id;
	Thread executor;
	List<TaskAbst<?, ?>> tasksQueue;
	List<TaskAbst<?, ?>> asyncTasksInExecution;
	private TaskAbst<?, ?> currentTask;
	private Boolean supended;
	private int loggingThreshold;
	private int defaultPriority;
	private long executedTasksCount;
	private boolean isDaemon;
	private String name;
	private Boolean terminated;
	private Runnable initializer;
	
	static {
		runOnlyOnceTasksToBeExecuted = new ConcurrentHashMap<>();
	}
	
	QueuedTasksExecutor(String name, int defaultPriority, boolean isDaemon, int loggingThreshold) {
		mutexManager = Mutex.Manager.create(this);
		tasksQueue = new CopyOnWriteArrayList<>();
		asyncTasksInExecution = new CopyOnWriteArrayList<>();
		id = UUID.randomUUID().toString();
		initializer = () -> {
			this.name = name;
			this.defaultPriority = defaultPriority;
			this.loggingThreshold = loggingThreshold;
			this.isDaemon = isDaemon;
			init0();
		};		
		init();
	}
	
	void init() {
		initializer.run();
	}
	
	Object getMutex(String name) {
		return mutexManager.getMutex(id + name);
	}
	
	void init0() {		
		supended = Boolean.FALSE;
		terminated = Boolean.FALSE;
		executedTasksCount = 0;
		executor = new Thread(() -> {
			while (!terminated) {
				if (!tasksQueue.isEmpty()) {
					Iterator<TaskAbst<?, ?>> taskIterator = tasksQueue.iterator();
					while (taskIterator.hasNext()) {
						synchronized(getMutex("resumeCaller")) {
							try {
								if (supended) {
									getMutex("resumeCaller").wait();
									break;
								}
							} catch (InterruptedException exc) {
								logWarn("Exception occurred", exc);
							}
						}
						TaskAbst<?, ?> task =	this.currentTask = taskIterator.next();
						tasksQueue.remove(task);
						Thread executor = task.executor;
						if (!task.hasFinished()) {
							int currentExecutablePriority = task.getPriority();
							if (executor.getPriority() != currentExecutablePriority) {
								executor.setPriority(currentExecutablePriority);
							}
							boolean isSync = executor == this.executor;
							if (isSync) {
								task.execute();
							} else {
								asyncTasksInExecution.add(task);
								executor.start();
							}
							if (task.runOnlyOnce) {
								runOnlyOnceTasksToBeExecuted.remove(task.id);
							}
							if (executor.getPriority() != this.defaultPriority) {
								executor.setPriority(this.defaultPriority);
							}
							if (isSync) {
								++executedTasksCount;
								logExecutedTaskCount();
							}
						}
						synchronized(getMutex("suspensionCaller")) {
							getMutex("suspensionCaller").notifyAll();
						}
						if (terminated) {
							break;
						}
					}
				} else {
					synchronized(getMutex("executingFinishedWaiter")) {
						getMutex("executingFinishedWaiter").notifyAll();
					}
					synchronized(getMutex("executableCollectionFiller")) {
						if (tasksQueue.isEmpty()) {
							try {
								getMutex("executableCollectionFiller").wait();
							} catch (InterruptedException exc) {
								logWarn("Exception occurred", exc);
							}
						}
					}
				}
			}
		}, name);
		executor.setPriority(this.defaultPriority);
		executor.setDaemon(isDaemon);
		executor.start();
	}

	void logExecutedTaskCount() {
		if (executedTasksCount % loggingThreshold == 0) {
			logInfo("Executed {} tasks", executedTasksCount);
		}
	}
	
	public static QueuedTasksExecutor create(String name, int initialPriority) {
		return create(name, initialPriority, false, 100, false);
	}
	
	public static QueuedTasksExecutor create(String name, int initialPriority, boolean daemon, int loggingThreshold, boolean undestroyable) {
		if (undestroyable) {
			String creatorClass = Thread.currentThread().getStackTrace()[2].getClassName();
			return new QueuedTasksExecutor(name, initialPriority, daemon, loggingThreshold) {
				
				@Override
				public boolean shutDown(boolean waitForTasksTermination) {
					if (Thread.currentThread().getStackTrace()[4].getClassName().equals(creatorClass)) {
						return super.shutDown(waitForTasksTermination);
					}
					return false;
				}
				
			};
		} else {
			return new QueuedTasksExecutor(name, initialPriority, daemon, loggingThreshold);
		}
	}
	
	public <T> ProducerTask<T> createTask(ThrowingSupplier<T, ? extends Throwable> executable) {
		ProducerTask<T> task = (ProducerTask<T>) getProducerTaskSupplier().apply((ThrowingSupplier<Object, ? extends Throwable>) executable);
		task.setPriority(this.defaultPriority);
		return task;
	}
	
	<T> Function<ThrowingSupplier<T, ? extends Throwable>, ProducerTask<T>> getProducerTaskSupplier() {
		return executable -> new ProducerTask<T>(executable) {
			public ProducerTask<T> addToQueue() {
				return add(this);
			};
		};
	}
	
	public Task createTask(ThrowingRunnable<? extends Throwable> executable) {
		Task task = getTaskSupplier().apply((ThrowingRunnable<? extends Throwable>) executable);
		task.setPriority(this.defaultPriority);
		return task;
	}
	
	<T> Function<ThrowingRunnable<? extends Throwable> , Task> getTaskSupplier() {
		return executable -> new Task(executable) {
			public Task addToQueue() {
				return add(this);
			};
		};
	}

	<E, T extends TaskAbst<E, T>> T add(T task) {
		if (canBeExecuted(task)) {
			try {
				setExecutorOf(task);
				tasksQueue.add(task);
				synchronized(getMutex("executableCollectionFiller")) {
					getMutex("executableCollectionFiller").notifyAll();
				}
			} catch (Throwable exc) {
				logWarn("Exception occurred", exc);
			}
			return task;
		}
		return null;
	}

	private <E, T extends TaskAbst<E, T>> void setExecutorOf(T task) {
		if (TaskAbst.Execution.Mode.SYNC.equals(task.executionMode)) {
			task.setExecutor(this.executor);
		} else if (TaskAbst.Execution.Mode.ASYNC.equals(task.executionMode)) {
			Thread executor = new Thread(() -> {
				task.execute();
				asyncTasksInExecution.remove(task);
				++executedTasksCount;
				logExecutedTaskCount();
			}, name + " - worker");
			executor.setPriority(defaultPriority);
			task.setExecutor(executor);
		}		
	}

	<E, T extends TaskAbst<E, T>> boolean canBeExecuted(T task) {
		if (task.runOnlyOnce) {
			return !task.hasBeenExecutedChecker.get() && runOnlyOnceTasksToBeExecuted.putIfAbsent(task.id, task) == null && !task.hasFinished();
		}
		return !task.hasFinished();
	}
	
	public <E, T extends TaskAbst<E, T>> QueuedTasksExecutor waitFor(T task) {
		return waitFor(task, task.getPriority());
	}
	
	public <E, T extends TaskAbst<E, T>> QueuedTasksExecutor waitFor(T task, int priority) {
		changePriorityToAllTaskBefore(task, priority);
		task.join0(false);
		return this;
	}
	
	public QueuedTasksExecutor waitForTasksEnding() {
		return waitForTasksEnding(Thread.currentThread().getPriority());
	}
	
	public QueuedTasksExecutor waitForTasksEnding(int priority) {
		executor.setPriority(priority);
		tasksQueue.stream().forEach(executable -> executable.setPriority(priority));
		while (!tasksQueue.isEmpty()) {
			synchronized(getMutex("executingFinishedWaiter")) {
				if (!tasksQueue.isEmpty()) {
					try {
						getMutex("executingFinishedWaiter").wait();
					} catch (InterruptedException exc) {
						logWarn("Exception occurred", exc);
					}
				}
			}
		}
		asyncTasksInExecution.stream().forEach(task -> {
			Thread taskExecutor = task.executor;
			if (taskExecutor != null) {
				taskExecutor.setPriority(priority);
			}
			task.join0(false);
		});
		executor.setPriority(this.defaultPriority);
		return this;
	}
	
	public QueuedTasksExecutor changePriority(int priority) {
		this.defaultPriority = priority;
		executor.setPriority(priority);
		tasksQueue.stream().forEach(executable -> executable.setPriority(priority));
		return this;
	}
	
	public QueuedTasksExecutor suspend() {
		return suspend(true);
	}
	
	public QueuedTasksExecutor suspend(boolean immediately) {
		return suspend0(immediately, Thread.currentThread().getPriority());
	}
	
	public QueuedTasksExecutor suspend(boolean immediately, int priority) {
		return suspend0(immediately, priority);
	}
	
	QueuedTasksExecutor suspend0(boolean immediately, int priority) {
		executor.setPriority(priority);
		if (immediately) {
			supended = Boolean.TRUE;
			if (!currentTask.hasFinished()) {
				for (TaskAbst<?, ?> asynTask : asyncTasksInExecution) {
					asynTask.join0(false);
				}
				synchronized (getMutex("suspensionCaller")) {
					if (!currentTask.hasFinished()) {
						try {
							getMutex("suspensionCaller").wait();
						} catch (InterruptedException exc) {
							logWarn("Exception occurred", exc);
						}
					}
				}
			}
		} else {
			changePriorityToAllTaskBefore(createTask((ThrowingRunnable<?>)() -> supended = Boolean.TRUE).setPriority(priority).addToQueue(), priority);
		}
		return this;
	}

	<E, T extends TaskAbst<E, T>> boolean changePriorityToAllTaskBefore(T task, int priority) {
		int taskIndex = tasksQueue.indexOf(task);
		if (taskIndex != -1) {
			Iterator<TaskAbst<?, ?>> taskIterator = tasksQueue.iterator();
			int idx = 0;
			while (taskIterator.hasNext()) {
				TaskAbst<?, ?> currentIterated = taskIterator.next();
				if (idx < taskIndex) {					
					if (currentIterated != task) {
						task.setPriority(priority);
					} else {
						break;
					}
				}
				idx++;
			}
			return true;
		}
		for (TaskAbst<?, ?> asynTask : asyncTasksInExecution) {
			Thread executor = asynTask.executor;
			if (executor != null) {
				executor.setPriority(priority);
			}
		}
		return false;
	}

	public QueuedTasksExecutor resume() {
		synchronized(getMutex("resumeCaller")) {
			try {
				supended = Boolean.FALSE;
				getMutex("resumeCaller").notifyAll();
			} catch (Throwable exc) {
				logWarn("Exception occurred", exc);
			}
		}	
		return this;
	}
	
	public boolean isSuspended() {
		return supended;
	}
	
	public boolean shutDown(boolean waitForTasksTermination) {
		Collection<TaskAbst<?, ?>> executables = this.tasksQueue;
		Collection<TaskAbst<?, ?>> asyncTasksInExecution = this.asyncTasksInExecution;
		Thread executor = this.executor;
		if (waitForTasksTermination) {
			createTask(() -> {
				this.terminated = Boolean.TRUE;
				logInfo("Executed tasks {}", executedTasksCount);
				logInfo("Unexecuted tasks {}", executables.size());
				executables.clear();
				asyncTasksInExecution.clear();
			}).setPriorityToCurrentThreadPriority().addToQueue();
		} else {
			suspend();
			this.terminated = Boolean.TRUE;
			logInfo("Executed tasks {}", executedTasksCount);
			logInfo("Unexecuted tasks {}", executables.size());
			executables.clear();
			asyncTasksInExecution.clear();
			resume();
			try {
				synchronized(getMutex("executableCollectionFiller")) {
					getMutex("executableCollectionFiller").notifyAll();
				}
			} catch (Throwable exc) {
				logWarn("Exception occurred", exc);
			}	
		}
		try {
			executor.join();
			closeResources();			
		} catch (InterruptedException exc) {
			logError("Exception occurred", exc);
		}
		return true;
	}
	
	@Override
	public void close() {
		shutDown(true);
	}
	
	void closeResources() {
		try {
			executor.interrupt();
		} catch (Throwable e) {
			logWarn("Exception occurred while interrupting thread {} of {}", executor, this);
		}
		executor = null;
		tasksQueue = null;
		asyncTasksInExecution = null;
		currentTask = null;
		initializer = null;
		terminated = null;
		supended = null;
		logInfo("All resources of '{}' have been closed", name);
		name = null;		
	}
	
	public static abstract class TaskAbst<E, T extends TaskAbst<E, T>> implements ManagedLogger {
		
		static class Execution {
			public static enum Mode {
				SYNC, ASYNC
			}
		}
		
		String id;
		boolean runOnlyOnce;
		Supplier<Boolean> hasBeenExecutedChecker;
		E executable;
		Execution.Mode executionMode;
		int priority;
		Thread executor;
		Throwable exc;
		
		public TaskAbst() {
			this.executionMode = Execution.Mode.SYNC;
		}
		
		public boolean hasFinished() {
			return executable == null;
		}
		
		void join0(boolean ignoreThreadCheck) {
			if (!hasFinished() && ((ignoreThreadCheck) ||
				(!ignoreThreadCheck && Thread.currentThread() != executor && executor != null))
			) {
				synchronized (this) {
					if (!hasFinished() && ((ignoreThreadCheck) ||
						(!ignoreThreadCheck && Thread.currentThread() != executor && executor != null))) {
						try {
							wait();
						} catch (InterruptedException exc) {
							throw Throwables.toRuntimeException(exc);
						}
					}
				}
			}
		}		
		
		public T async() {
			this.executionMode = Execution.Mode.ASYNC;
			return (T)this;
		}
		
		void execute() {
			try {
				execute0();						
			} catch (Throwable exc) {
				this.exc = exc;
				logError("Exception occurred while executing " + this, exc);
			}
			executable = null;
			executor = null;
			synchronized (this) {
				notifyAll();
			}
		}
		
		abstract void execute0() throws Throwable;
		
		T setExecutor(Thread executor) {
			this.executor = executor;
			return (T)this;
		}
		
		public T setPriority(int priority) {
			this.priority = priority;
			return (T)this;
		}
		
		public T setPriorityToCurrentThreadPriority() {
			return setPriority(Thread.currentThread().getPriority());
		}
		
		public T runOnlyOnce(String id, Supplier<Boolean> hasBeenExecutedChecker) {
			runOnlyOnce = true;
			this.id = id;
			this.hasBeenExecutedChecker = hasBeenExecutedChecker;
			return (T)this;
		}
		
		public int getPriority() {
			return priority;
		}
		
		public Throwable getException() {
			return exc;
		}
		
		public boolean endedWithErrors() {
			return exc != null;
		}
		
		public abstract T addToQueue();
		
	}
	
	public static abstract class Task extends TaskAbst<ThrowingRunnable<? extends Throwable>, Task> {
		
		Task(ThrowingRunnable<? extends Throwable> executable) {
			this.executable = executable;
		}

		@Override
		void execute0() throws Throwable {
			this.executable.run();			
		}
		
		public void join(boolean ignoreThread) {
			join0(ignoreThread);
		}
		
		public void join() {
			join0(false);
		}
		
	}
	
	public static abstract class ProducerTask<T> extends TaskAbst<ThrowingSupplier<T, ? extends Throwable>, ProducerTask<T>> {
		private T result;
		
		ProducerTask(ThrowingSupplier<T, ? extends Throwable> executable) {
			super();
			this.executable = executable;
		}		
		
		@Override
		void execute0() throws Throwable {
			result = executable.get();			
		}
		
		public T join() {
			return join(false);
		}
		
		public T join(boolean ignoreThread) {
			join0(ignoreThread);
			return result;
		}
		
		public T get() {
			return result;
		}
	}
	
	public static class Group {
		Map<String, QueuedTasksExecutor> queuedTasksExecutors;
		
		Group(String name, boolean isDaemon) {
			queuedTasksExecutors = new HashMap<>();
			queuedTasksExecutors.put(String.valueOf(Thread.MAX_PRIORITY), createQueuedTasksExecutor(name + " - High priority tasks executor", Thread.MAX_PRIORITY, isDaemon, 10));
			queuedTasksExecutors.put(String.valueOf(Thread.NORM_PRIORITY), createQueuedTasksExecutor(name + " - Normal priority tasks executor", Thread.NORM_PRIORITY, isDaemon, 100));
			queuedTasksExecutors.put(String.valueOf(Thread.MIN_PRIORITY), createQueuedTasksExecutor(name + " - Low priority tasks executor", Thread.MIN_PRIORITY, isDaemon, 1000));
		}
		
		public static Group create(String name, boolean isDaemon) {
			return create(name, isDaemon, false);
		}
		
		public static Group create(String name, boolean isDaemon, boolean undestroyableFromExternal) {
			if (!undestroyableFromExternal) {
				return new Group(name, isDaemon);
			} else {
				String creatorClass = Thread.currentThread().getStackTrace()[2].getClassName();
				return new Group(name, isDaemon) {
					@Override
					public boolean shutDown(boolean waitForTasksTermination) {
						if (Thread.currentThread().getStackTrace()[2].getClassName().equals(creatorClass)) {	
							return super.shutDown(waitForTasksTermination);
						}
						return false;
					}
				};
			}
		}
		
		public <T> ProducerTask<T> createTask(ThrowingSupplier<T, ? extends Throwable> executable) {
			return createTask(executable, Thread.currentThread().getPriority());
		}
		
		public <T> ProducerTask<T> createTask(ThrowingSupplier<T, ? extends Throwable> executable, int priority) {
			QueuedTasksExecutor queuedTasksExecutor = getByPriority(priority);
			if (queuedTasksExecutor == null) {
				if (priority < Thread.NORM_PRIORITY) {
					priority = Thread.MIN_PRIORITY;
				} else if (priority < Thread.MAX_PRIORITY) {
					priority = Thread.NORM_PRIORITY;
				} else {
					priority = Thread.MAX_PRIORITY;
				}
			}			
			return queuedTasksExecutor.createTask(executable);
		}

		QueuedTasksExecutor getByPriority(int priority) {
			return queuedTasksExecutors.get(String.valueOf(priority));
		}
		
		public Task createTask(ThrowingRunnable<? extends Throwable> executable) {
			return createTask(executable, Thread.currentThread().getPriority());
		}
		
		public Task createTask(ThrowingRunnable<? extends Throwable> executable, int priority) {
			return getByPriority(priority).createTask(executable);
		}
		
		QueuedTasksExecutor createQueuedTasksExecutor(String name, int priority, boolean isDaemon, int loggingThreshold) {
			return new QueuedTasksExecutor(name, priority, isDaemon, loggingThreshold) {
				
				<T> Function<ThrowingSupplier<T, ? extends Throwable>, ProducerTask<T>> getProducerTaskSupplier() {
					return executable -> new ProducerTask<T>(executable) {
						public ProducerTask<T> addToQueue() {
							return add(this);
						};
						
						public QueuedTasksExecutor.ProducerTask<T> setPriority(int priority) {
							int oldPriority = this.priority;
							super.setPriority(priority);
							if (oldPriority != priority && oldPriority != 0) {
								if (getByPriority(oldPriority).tasksQueue.remove(this)) {
									if (!this.hasFinished()) {
										getByPriority(priority).add(this);										
									}
								}
							}
							return this;
						};
					};
				}
				
				<T> Function<ThrowingRunnable<? extends Throwable> , Task> getTaskSupplier() {
					return executable -> new Task(executable) {
						public Task addToQueue() {
							return add(this);
						};
						
						public Task setPriority(int priority) {
							int oldPriority = this.priority;
							super.setPriority(priority);
							if (oldPriority != priority && oldPriority != 0) {
								if (getByPriority(oldPriority).tasksQueue.remove(this)) {
									if (!this.hasFinished()) {
										getByPriority(priority).add(this);										
									}
								}
							}
							return this;
						};
					};
				}
				
				public QueuedTasksExecutor waitForTasksEnding(int priority) {
					while (!tasksQueue.isEmpty()) {
						synchronized(getMutex("executingFinishedWaiter")) {
							if (!tasksQueue.isEmpty()) {
								try {
									getMutex("executingFinishedWaiter").wait();
								} catch (InterruptedException exc) {
									logWarn("Exception occurred", exc);
								}
							}
						}
					}
					asyncTasksInExecution.stream().forEach(task -> {
						task.join0(false);
					});
					return this;
				}
				
				public <E, T extends TaskAbst<E, T>> QueuedTasksExecutor waitFor(T task, int priority) {
					task.join0(false);
					return this;
				}
			};
		}

		public boolean shutDown(boolean waitForTasksTermination) {
			for (Entry<String, QueuedTasksExecutor> queuedTasksExecutorBox : queuedTasksExecutors.entrySet()) {
				queuedTasksExecutorBox.getValue().shutDown(waitForTasksTermination);
			}
			queuedTasksExecutors.clear();
			queuedTasksExecutors = null;
			return true;
		}

		public void waitForTasksEnding() {
			for (Entry<String, QueuedTasksExecutor> queuedTasksExecutorBox : queuedTasksExecutors.entrySet()) {
				QueuedTasksExecutor queuedTasksExecutor = queuedTasksExecutorBox.getValue();
				queuedTasksExecutor.waitForTasksEnding(queuedTasksExecutor.defaultPriority);
			}			
		}

		public void waitFor(Task task) {
			for (Entry<String, QueuedTasksExecutor> queuedTasksExecutorBox : queuedTasksExecutors.entrySet()) {
				QueuedTasksExecutor queuedTasksExecutor = queuedTasksExecutorBox.getValue();
				if (queuedTasksExecutor.tasksQueue.contains(task)) {	
					queuedTasksExecutor.waitFor(task, queuedTasksExecutor.defaultPriority);
				}
			}			
		}		
	}
}
