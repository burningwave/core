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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import org.burningwave.core.Component;
import org.burningwave.core.ManagedLogger;
import org.burningwave.core.function.ThrowingRunnable;
import org.burningwave.core.function.ThrowingSupplier;

@SuppressWarnings("unchecked")
public class QueuedTasksExecutor implements Component {
	private final static Map<String, TaskAbst<?, ?>> runOnlyOnceTasksToBeExecuted;
	private Mutex.Manager mutexManager;
	private String id;
	private List<TaskAbst<?, ?>> tasksQueue;
	private TaskAbst<?, ?> currentTask;
	private Boolean supended;
	
	Thread executor;
	private int defaultPriority;
	private long executedTasksCount;
	private boolean isDaemon;
	private String name;
	private Boolean terminated;
	private Runnable initializer;
	
	static {
		runOnlyOnceTasksToBeExecuted = new ConcurrentHashMap<>();;
	}
	
	private QueuedTasksExecutor(String name, int defaultPriority, boolean isDaemon) {
		mutexManager = Mutex.Manager.create(this);
		tasksQueue = new CopyOnWriteArrayList<>();
		id = UUID.randomUUID().toString();
		initializer = () -> {
			this.name = name;
			this.defaultPriority = defaultPriority;
			this.isDaemon = isDaemon;
			init0();
		};		
		init();
	}
	
	void init() {
		initializer.run();
	}
	
	private Object getMutex(String name) {
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
						int currentExecutablePriority = task.getPriority();
						if (executor.getPriority() != currentExecutablePriority) {
							executor.setPriority(currentExecutablePriority);
						}
						task.execute();
						if (task.runOnlyOnce) {
							runOnlyOnceTasksToBeExecuted.remove(task.id);
						}
						if (executor.getPriority() != this.defaultPriority) {
							executor.setPriority(this.defaultPriority);
						}
						++executedTasksCount;
						if (executedTasksCount % 100 == 0) {
							logInfo("Executed {} tasks", executedTasksCount);
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
	
	public static QueuedTasksExecutor create(String name, int initialPriority) {
		return create(name, initialPriority, false, false);
	}
	
	public static QueuedTasksExecutor create(String name, int initialPriority, boolean daemon, boolean undestroyable) {
		if (undestroyable) {
			String creatorClass = Thread.currentThread().getStackTrace()[2].getClassName();
			return new QueuedTasksExecutor(name, initialPriority, daemon) {
				
				@Override
				public void shutDown(boolean waitForTasksTermination) {
					super.shutDown(waitForTasksTermination);
				}
				
				@Override
				void closeResources() {
					if (!Thread.currentThread().getStackTrace()[4].getClassName().equals(creatorClass)) {	
						this.executor = null;
						init();
					} else {
						super.closeResources();
					}
				}
				
			};
		} else {
			return new QueuedTasksExecutor(name, initialPriority, daemon);
		}
	}
	
	public <T> ProducerTask<T> createTask(ThrowingSupplier<T, ? extends Throwable> executable) {
		ProducerTask<T> task = new ProducerTask<T>(executable, this.defaultPriority, this.executor) {
			public ProducerTask<T> addToQueue() {
				return add(this);
			};
		};
		return task;
	}
	
	public Task createTask(ThrowingRunnable<? extends Throwable> executable) {
		Task task = new Task(executable, this.defaultPriority, this.executor) {
			public Task addToQueue() {
				return add(this);
			};
		};
		return task;
	}

	<E, T extends TaskAbst<E, T>> T add(T task) {
		if (canBeExecuted(task)) {
			try {
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

	<E, T extends TaskAbst<E, T>> boolean canBeExecuted(T task) {
		if (task.runOnlyOnce) {
			return !task.hasBeenExecutedChecker.get() && runOnlyOnceTasksToBeExecuted.putIfAbsent(task.id, task) == null;
		}
		return true;
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
			if (!currentTask.hasFinished) {
				synchronized (getMutex("suspensionCaller")) {
					if (!currentTask.hasFinished) {
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
	
	public void shutDown(boolean waitForTasksTermination) {
		Collection<TaskAbst<?, ?>> executables = this.tasksQueue;
		Thread executor = this.executor;
		if (waitForTasksTermination) {
			createTask(() -> {
				this.terminated = Boolean.TRUE;
				logInfo("Executed tasks {}", executedTasksCount);
				logInfo("Unexecuted tasks {}", executables.size());
				executables.clear();
			}).setPriorityToCurrentThreadPriority().addToQueue();
		} else {
			suspend();
			this.terminated = Boolean.TRUE;
			logInfo("Executed tasks {}", executedTasksCount);
			logInfo("Unexecuted tasks {}", executables.size());
			executables.clear();
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
		currentTask = null;
		initializer = null;
		terminated = null;
		supended = null;
		logInfo("All resources of '{}' have been closed", name);
		name = null;		
	}
	
	public static abstract class TaskAbst<E, T extends TaskAbst<E, T>> implements ManagedLogger {
		String id;
		boolean runOnlyOnce;
		Supplier<Boolean> hasBeenExecutedChecker;
		E executable;
		boolean hasFinished;
		int priority;
		Thread queuedTasksExecutorThread;
		Throwable exc;
		
		TaskAbst(int priority, Thread queuedTasksExecutorThread) {
			this.queuedTasksExecutorThread = queuedTasksExecutorThread;
			this.priority = priority;
		}
		
		public boolean hasFinished() {
			return hasFinished;
		}
		
		void join0(boolean ignoreThread) {
			if (!hasFinished() && ((ignoreThread) ||
				(!ignoreThread && Thread.currentThread() != queuedTasksExecutorThread && queuedTasksExecutorThread != null))
			) {
				synchronized (this) {
					if (!hasFinished() && ((ignoreThread) ||
						(!ignoreThread && Thread.currentThread() != queuedTasksExecutorThread && queuedTasksExecutorThread != null))) {
						try {
							wait();
						} catch (InterruptedException exc) {
							throw Throwables.toRuntimeException(exc);
						}
					}
				}
			}
		}
		
		void execute() {
			try {
				execute0();						
			} catch (Throwable exc) {
				this.exc = exc;
				logError("Exception occurred while executing " + this, exc);
			}
			markHasFinished();
			executable = null;
			queuedTasksExecutorThread = null;
			synchronized (this) {
				notifyAll();
			}
		}
		
		abstract void execute0() throws Throwable;
		
		void markHasFinished() {
			hasFinished = true;
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
		
		Task(ThrowingRunnable<? extends Throwable> executable, int priority, Thread queuedTasksExecutorThread) {
			super(priority, queuedTasksExecutorThread);
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
		
		ProducerTask(ThrowingSupplier<T, ? extends Throwable> executable, int priority, Thread queuedTasksExecutorThread) {
			super(priority, queuedTasksExecutorThread);
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
}
