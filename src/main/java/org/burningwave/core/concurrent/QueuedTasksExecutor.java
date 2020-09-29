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

import static org.burningwave.core.assembler.StaticComponentContainer.Methods;
import static org.burningwave.core.assembler.StaticComponentContainer.Strings;
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.burningwave.core.Component;
import org.burningwave.core.ManagedLogger;
import org.burningwave.core.function.ThrowingBiConsumer;
import org.burningwave.core.function.ThrowingRunnable;
import org.burningwave.core.function.ThrowingSupplier;

@SuppressWarnings({"unchecked", "resource"})
public class QueuedTasksExecutor implements Component {
	private final static Map<String, TaskAbst<?,?>> runOnlyOnceTasksToBeExecuted;
	java.lang.Thread queueConsumer;
	List<TaskAbst<?, ?>> tasksQueue;
	Set<TaskAbst<?, ?>> tasksInExecution;
	Boolean supended;
	int defaultPriority;
	private long executedTasksCount;
	private boolean isDaemon;
	private String queueConsumerName;
	private Boolean terminated;
	private Runnable initializer;
	boolean taskCreationTrackingEnabled;
	Object resumeCaller;
	Object executingFinishedWaiter;
	Object suspensionCaller;
	Object executableCollectionFiller;
	
	static {
		runOnlyOnceTasksToBeExecuted = new ConcurrentHashMap<>();
	}
	
	QueuedTasksExecutor(String executorName, int defaultPriority, boolean isDaemon) {
		tasksQueue = new CopyOnWriteArrayList<>();
		tasksInExecution = ConcurrentHashMap.newKeySet();
		this.resumeCaller = new Object();
		this.executingFinishedWaiter = new Object();
		this.suspensionCaller = new Object();
		this.executableCollectionFiller = new Object();
		initializer = () -> {
			this.queueConsumerName = executorName;
			this.defaultPriority = defaultPriority;
			this.isDaemon = isDaemon;
			init0();
		};		
		init();
	}
	
	void init() {
		initializer.run();
	}
	
	void init0() {		
		supended = Boolean.FALSE;
		terminated = Boolean.FALSE;
		executedTasksCount = 0;
		queueConsumer = new java.lang.Thread(() -> {
			while (!terminated) {
				if (checkAndNotifySuspension()) {
					continue;
				}
				if (!tasksQueue.isEmpty()) {
					Iterator<TaskAbst<?, ?>> taskIterator = tasksQueue.iterator();
					while (taskIterator.hasNext()) {
						if (checkAndNotifySuspension() || terminated) {
							break;
						}
						TaskAbst<?, ?> task = taskIterator.next();
						synchronized (task) {
							if (!tasksQueue.remove(task)) {
								continue;
							}
						}
						setExecutorOf(task);
						Thread currentExecutor = task.executor;
						int currentExecutablePriority = task.getPriority();
						if (currentExecutor.getPriority() != currentExecutablePriority) {
							currentExecutor.setPriority(currentExecutablePriority);
						}
						currentExecutor.start();
					}
				} else {
					synchronized(executableCollectionFiller) {
						if (tasksQueue.isEmpty()) {
							try {
								synchronized(executingFinishedWaiter) {
									executingFinishedWaiter.notifyAll();
								}
								if (!supended) {
									executableCollectionFiller.wait();
								}
							} catch (InterruptedException exc) {
								logWarn("Exception occurred", exc);
							}
						}
					}
				}
			}
		}, queueConsumerName);
		queueConsumer.setPriority(this.defaultPriority);
		queueConsumer.setDaemon(isDaemon);
		queueConsumer.start();
	}

	private boolean checkAndNotifySuspension() {
		if (supended) {
			synchronized(resumeCaller) {
				synchronized (suspensionCaller) {
					suspensionCaller.notifyAll();
				}
				try {
					resumeCaller.wait();
					return true;
				} catch (InterruptedException exc) {
					logWarn("Exception occurred", exc);
				}
			}
		}
		return false;
	}
	
	public static QueuedTasksExecutor create(String executorName, int initialPriority) {
		return create(executorName, initialPriority, false, false);
	}
	
	public static QueuedTasksExecutor create(String executorName, int initialPriority, boolean daemon, boolean undestroyable) {
		if (undestroyable) {
			String creatorClass = Thread.currentThread().getStackTrace()[2].getClassName();
			return new QueuedTasksExecutor(executorName, initialPriority, daemon) {
				
				@Override
				public boolean shutDown(boolean waitForTasksTermination) {
					if (Thread.currentThread().getStackTrace()[4].getClassName().equals(creatorClass)) {
						return super.shutDown(waitForTasksTermination);
					}
					return false;
				}
				
			};
		} else {
			return new QueuedTasksExecutor(executorName, initialPriority, daemon);
		}
	}
	
	public QueuedTasksExecutor setTasksCreationTrackingFlag(boolean flag) {
		this.taskCreationTrackingEnabled = flag;
		return this;
	}
	
	public <T> ProducerTask<T> createTask(ThrowingSupplier<T, ? extends Throwable> executable) {
		ProducerTask<T> task = (ProducerTask<T>) getProducerTaskSupplier().apply((ThrowingSupplier<Object, ? extends Throwable>) executable);
		task.priority = this.defaultPriority;
		return task;
	}
	
	<T> Function<ThrowingSupplier<T, ? extends Throwable>, ProducerTask<T>> getProducerTaskSupplier() {
		return executable -> new ProducerTask<T>(executable, taskCreationTrackingEnabled) {
			@Override
			ProducerTask<T> addToQueue() {
				return QueuedTasksExecutor.this.addToQueue(this, false);
			}

			@Override
			void preparingToStart() {
				QueuedTasksExecutor.this.tasksInExecution.add(this);				
			}

			@Override
			void preparingToFinish() {
				QueuedTasksExecutor.this.tasksInExecution.remove(this);
				++QueuedTasksExecutor.this.executedTasksCount;			
			};
		};
	}
	
	public Task createTask(ThrowingRunnable<? extends Throwable> executable) {
		Task task = getTaskSupplier().apply((ThrowingRunnable<? extends Throwable>) executable);
		task.priority = this.defaultPriority;
		return task;
	}
	
	<T> Function<ThrowingRunnable<? extends Throwable> , Task> getTaskSupplier() {
		return executable -> new Task(executable, taskCreationTrackingEnabled) {
			@Override
			Task addToQueue() {
				return QueuedTasksExecutor.this.addToQueue(this, false);
			};
			
			@Override
			void preparingToStart() {
				QueuedTasksExecutor.this.tasksInExecution.add(this);				
			}

			@Override
			void preparingToFinish() {
				QueuedTasksExecutor.this.tasksInExecution.remove(this);
				++QueuedTasksExecutor.this.executedTasksCount;			
			};
		};
	}

	<E, T extends TaskAbst<E, T>> T addToQueue(T task, boolean skipCheck) {
		Object[] canBeExecutedBag = null;
		if (skipCheck || (Boolean)(canBeExecutedBag = canBeExecuted(task))[1]) {
			try {
				tasksQueue.add(task);
				synchronized(executableCollectionFiller) {
					executableCollectionFiller.notifyAll();
				}
			} catch (Throwable exc) {
				logWarn("Exception occurred", exc);
			}
		}
		return canBeExecutedBag != null ? (T)canBeExecutedBag[0] : task;
	}

	private void setExecutorOf(TaskAbst<?, ?> task) {
		Thread executor = Thread.getOrCreate().setExecutable(() -> {
			task.execute();
		});
		if (task.name != null) {
			executor.setName(queueConsumerName + " -> " + task.name);
		} else {
			executor.setIndexedName(queueConsumerName);
		}		
		executor.setPriority(task.priority);
		task.setExecutor(executor);	
	}

	<E, T extends TaskAbst<E, T>> Object[] canBeExecuted(T task) {
		Object[] bag = new Object[]{task, true};
		if (task.runOnlyOnce) {
			bag[1] =(!task.hasBeenExecutedChecker.get() && 
				Optional.ofNullable(runOnlyOnceTasksToBeExecuted.putIfAbsent(
					task.id, task
				)).map(taskk -> {
					bag[0] = taskk;
					return false;
				}).orElseGet(() -> true)
			);
		}
		return bag;
	}
	
	public <E, T extends TaskAbst<E, T>> QueuedTasksExecutor waitFor(T task) {
		return waitFor(task, Thread.currentThread().getPriority());
	}
	
	public <E, T extends TaskAbst<E, T>> QueuedTasksExecutor waitFor(T task, int priority) {
		changePriorityToAllTaskBefore(task, priority);
		task.waitForFinish();
		return this;
	}
	
	public QueuedTasksExecutor waitForTasksEnding() {
		return waitForTasksEnding(Thread.currentThread().getPriority(), false);
	}
	
	public <E, T extends TaskAbst<E, T>> boolean abort(T task) {
		if (!task.isStarted()) {
			if (task instanceof Task) {
				Task taskToBeAborted = (Task)task;
				if (taskToBeAborted.runOnlyOnce) {
					for (TaskAbst<?, ?> queuedTaskAbst : tasksQueue) {
						if (queuedTaskAbst instanceof Task) {
							Task queuedTask = (Task)queuedTaskAbst;
							if (taskToBeAborted.id.equals(queuedTask.id)) {
								synchronized (queuedTask) {
									if (tasksQueue.remove(queuedTask)) {
										if (!queuedTask.isStarted()) {
											queuedTask.aborted = true;
											queuedTask.removeExecutableAndExecutor();
											queuedTask.notifyAll();
											runOnlyOnceTasksToBeExecuted.remove(queuedTask.id);
											return queuedTask.aborted;
										}
									}
								}
							}
						}
					}
					return false;
				}
			}
			synchronized (task) {
				if (task.aborted = tasksQueue.remove(task)) {
					task.notifyAll();
					return task.aborted;
				}
			}
		}
		return false;
	}
	
	public QueuedTasksExecutor waitForTasksEnding(int priority, boolean waitForNewAddedTasks) {
		queueConsumer.setPriority(priority);
		tasksQueue.stream().forEach(executable -> executable.changePriority(priority)); 
		if (!tasksQueue.isEmpty()) {
			synchronized(executingFinishedWaiter) {
				if (!tasksQueue.isEmpty()) {
					try {
						executingFinishedWaiter.wait();
					} catch (InterruptedException exc) {
						logWarn("Exception occurred", exc);
					}
				}
			}
		}
		waitForTasksInExecutionEnding(priority);
		queueConsumer.setPriority(this.defaultPriority);
		if (waitForNewAddedTasks && (!tasksQueue.isEmpty() || !tasksInExecution.isEmpty())) {
			waitForTasksEnding(priority, waitForNewAddedTasks);
		}
		return this;
	}
	
	public QueuedTasksExecutor changePriority(int priority) {
		this.defaultPriority = priority;
		queueConsumer.setPriority(priority);
		tasksQueue.stream().forEach(executable -> executable.changePriority(priority));
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
		queueConsumer.setPriority(priority);
		if (immediately) {
			synchronized (suspensionCaller) {
				supended = Boolean.TRUE;
				waitForTasksInExecutionEnding(priority);
				try {
					synchronized(executableCollectionFiller) {
						if (this.queueConsumer.getState().equals(Thread.State.WAITING)) {
							executableCollectionFiller.notifyAll();
						}
					}
					suspensionCaller.wait();
				} catch (InterruptedException exc) {
					logWarn("Exception occurred", exc);
				}
			}
		} else {
			waitForTasksInExecutionEnding(priority);
			Task supendingTask = createSuspendingTask(priority);
			changePriorityToAllTaskBefore(supendingTask.addToQueue(), priority);
			supendingTask.waitForFinish();
		}
		queueConsumer.setPriority(this.defaultPriority);
		return this;
	}
	
	Task createSuspendingTask(int priority) {
		return createTask((ThrowingRunnable<?>)() -> supended = Boolean.TRUE).runOnlyOnce(getOperationId("suspend"), () -> supended).changePriority(priority);
	}

	void waitForTasksInExecutionEnding(int priority) {
		tasksInExecution.stream().forEach(task -> {
			Thread taskExecutor = task.executor;
			if (taskExecutor != null) {
				taskExecutor.setPriority(priority);
			}
			logInfo("{}", queueConsumer);
			task.logInfo();
			task.join0();
		});
	}

	<E, T extends TaskAbst<E, T>> void changePriorityToAllTaskBefore(T task, int priority) {
		int taskIndex = tasksQueue.indexOf(task);
		if (taskIndex != -1) {
			Iterator<TaskAbst<?, ?>> taskIterator = tasksQueue.iterator();
			int idx = 0;
			while (taskIterator.hasNext()) {
				TaskAbst<?, ?> currentIterated = taskIterator.next();
				if (idx < taskIndex) {					
					if (currentIterated != task) {
						task.changePriority(priority);
					} else {
						break;
					}
				}
				idx++;
			}
		}
		waitForTasksInExecutionEnding(priority);
	}

	public QueuedTasksExecutor resumeFromSuspension() {
		synchronized(resumeCaller) {
			try {
				supended = Boolean.FALSE;
				resumeCaller.notifyAll();
			} catch (Throwable exc) {
				logWarn("Exception occurred", exc);
			}
		}	
		return this;
	}
	
	public boolean shutDown(boolean waitForTasksTermination) {
		Collection<TaskAbst<?, ?>> executables = this.tasksQueue;
		java.lang.Thread executor = this.queueConsumer;
		if (waitForTasksTermination) {
			suspend(false);
		} else {
			suspend();
		}
		this.terminated = Boolean.TRUE;
		logStatus();
		executables.clear();
		tasksInExecution.clear();
		resumeFromSuspension();
		try {
			executor.join();
			closeResources();			
		} catch (InterruptedException exc) {
			logError("Exception occurred", exc);
		}
		return true;
	}
	
	public void logStatus() {
		List<TaskAbst<?, ?>> tasks = new ArrayList<>(tasksQueue);
		tasks.addAll(this.tasksInExecution);
		logStatus(this.executedTasksCount, tasks);
	}
	
	private void logStatus(Long executedTasksCount, Collection<TaskAbst<?, ?>> executables) {
		Collection<String> executablesLog = executables.stream().map(task -> "\t" + task.executable.toString()).collect(Collectors.toList());
		StringBuffer log = new StringBuffer(this.queueConsumerName + " - Executed tasks: ")
			.append(executedTasksCount).append(", Unexecuted tasks: ")
			.append(executablesLog.size());
			
		if (executablesLog.size() > 0) {
			log.append(":\n\t")
			.append(String.join("\n\t", executablesLog));
		}		
		logInfo(log.toString());
	}
	
	public void logQueueInfo() {
		Collection<TaskAbst<?, ?>> tasksQueue = this.tasksQueue;
		if (!tasksQueue.isEmpty()) {
			logInfo("{} - Tasks to be executed:", queueConsumer);
			for (TaskAbst<?,?> task : tasksQueue) {
				task.logInfo();
			}
		}
		tasksQueue = this.tasksInExecution;
		if (!tasksQueue.isEmpty()) {
			logInfo("{} - Tasks in execution:", queueConsumer);
			for (TaskAbst<?,?> task : tasksInExecution) {
				task.logInfo();
			}
		}
	}
	
	@Override
	public void close() {
		shutDown(true);
	}
	
	void closeResources() {
		try {
			queueConsumer.interrupt();
		} catch (Throwable e) {
			logWarn("Exception occurred while interrupting thread {} of {}", queueConsumer, this);
		}
		queueConsumer = null;
		tasksQueue = null;
		tasksInExecution = null;
		initializer = null;
		terminated = null;
		supended = null;
		resumeCaller = null;            
		executingFinishedWaiter = null;    
		suspensionCaller = null;           
		executableCollectionFiller = null; 
		logInfo("All resources of '{}' have been closed", queueConsumerName);
		queueConsumerName = null;		
	}
	
	public static abstract class TaskAbst<E, T extends TaskAbst<E, T>> implements ManagedLogger {
		
		String name;
		StackTraceElement[] stackTraceOnCreation;
		List<StackTraceElement> creatorInfos;
		boolean runOnlyOnce;
		Supplier<Boolean> hasBeenExecutedChecker;
		public String id;
		boolean started;
		boolean submited;
		boolean aborted;
		boolean finished;
		E executable;
		int priority;
		Thread executor;
		Throwable exc;
		ThrowingBiConsumer<T, Throwable, Throwable> exceptionHandler;
		
		public TaskAbst(E executable, boolean creationTracking) {
			this.executable = executable;
			if (creationTracking) {
				stackTraceOnCreation = Thread.currentThread().getStackTrace();
			}
		}
		
		public List<StackTraceElement> getCreatorInfos() {
			if (this.creatorInfos == null) {
				if (stackTraceOnCreation != null) {
					this.creatorInfos = Collections.unmodifiableList(
						Methods.retrieveExternalCallersInfo(
							this.stackTraceOnCreation,
							(clientMethodSTE, currentIteratedSTE) -> !currentIteratedSTE.getClassName().startsWith(QueuedTasksExecutor.class.getName()),
							-1
						)
					);
				} else {
					logWarn("Tasks creation tracking was disabled when {} was created", this);
				}
			}
			return creatorInfos;
		}
		
		public T setName(String name) {
			this.name = name;
			return (T)this;
		}
		
		public T setExceptionHandler(ThrowingBiConsumer<T, Throwable, Throwable> exceptionHandler) {
			this.exceptionHandler = exceptionHandler;
			return (T)this;
		}
		
		public boolean isStarted() {
			return started;
		}
		
		public boolean hasFinished() {
			if (!runOnlyOnce) {
				return finished;
			} else {
				return finished = hasBeenExecutedChecker.get();
			}
		}
		
		public T runOnlyOnce(String id, Supplier<Boolean> hasBeenExecutedChecker) {
			runOnlyOnce = true;
			this.id = id;
			this.hasBeenExecutedChecker = hasBeenExecutedChecker;
			return (T)this;
		}
		
		public boolean isAborted() {
			return aborted;
		}
		
		public boolean isSubmited() {
			return submited;
		}
		
		public T waitForStarting() {
			if (isSubmited()) {
				if (!started) {
					synchronized (this) {
						if (!started) {
							try {
								if (isAborted()) {
									throw new TaskStateException(this, "is aborted");
								}
								wait();
								waitForStarting();
							} catch (InterruptedException exc) {
								throw Throwables.toRuntimeException(exc);
							}
						}
					}
				}
			} else {
				throw new TaskStateException(this, "is not submitted");
			}
			return (T)this;
		}		
		
		public T waitForFinish() {
			join0();
			return (T)this;
		}
		
		void join0() {
			if (Thread.currentThread() == this.executor) {
				return;
			}
			if (isSubmited()) {
				if (!hasFinished()) {	
					synchronized (this) {
						if (!hasFinished()) {
							try {
								if (isAborted()) {
									throw new TaskStateException(this, "is aborted");
								}
								wait();
								join0();
							} catch (InterruptedException exc) {
								throw Throwables.toRuntimeException(exc);
							}
						}
					}
				}
			} else {
				throw new TaskStateException(this, "is not submitted");
			}
		}
		
		abstract void preparingToStart();
		
		void execute() {
			synchronized (this) {
				started = true;
				preparingToStart();
				if (aborted) {
					notifyAll();
					removeExecutableAndExecutor();
					return;
				}
				notifyAll();
			}
			try {
				try {
					execute0();					
				} catch (Throwable exc) {
					this.exc = exc;
					if (exceptionHandler != null) {
						exceptionHandler.accept((T)this, exc);
					} else {
						throw exc;
					}
				}
			} catch (Throwable exc) {
				logException(exc);
			}
			markAsFinished();			
		}
		
		void logInfo() {
			if (this.getCreatorInfos() != null) {
				Thread executor = this.executor;
				logInfo(
					"\n\tTask status: {} \n\t{} \n\tcreated by: {}", 
						Strings.compile("\n\t\tpriority: {}\n\t\tstarted: {}\n\t\taborted: {}\n\t\tfinished: {}", priority, isStarted(), isAborted(), hasFinished()),
						executor != null ? executor + Strings.from(executor.getStackTrace(),2) : "",
						Strings.from(this.getCreatorInfos(), 2)
				);
			}			
		}
		
		private void logException(Throwable exc) {
			logError(Strings.compile(
				"Exception occurred while executing {}: \n{}: {}{}{}", 
				this,
				exc.toString(),
				exc.getMessage(),
				Strings.from(exc.getStackTrace()),
				this.getCreatorInfos() != null ?
					"\nthat was created:" + Strings.from(this.getCreatorInfos())
					: "" 
			));
		}
		
		void removeExecutableAndExecutor() {
			executable = null;
			executor = null;
		}
		
		abstract void preparingToFinish();
		
		void markAsFinished() {
			synchronized(this) {
				preparingToFinish();
				finished = true;
				notifyAll();
			}
			if (runOnlyOnce) {
				runOnlyOnceTasksToBeExecuted.remove(((Task)this).id);
			}
			removeExecutableAndExecutor();			
		}
		
		abstract void execute0() throws Throwable;
		
		T setExecutor(Thread executor) {
			this.executor = executor;
			return (T)this;
		}
		
		public T changePriority(int priority) {
			this.priority = priority;
			return (T)this;
		}
		
		public T setPriorityToCurrentThreadPriority() {
			return changePriority(Thread.currentThread().getPriority());
		}
		
		public int getPriority() {
			return priority;
		}
		
		public Throwable getException() {
			return exc;
		}
		
		public final T submit() {
			if (!submited) {
				synchronized(this) {
					if (!submited) {
						submited = true;
					} else {
						throw new TaskStateException(this, "is already submited");
					}
				}
			} else {
				throw new TaskStateException(this, "is already submited");
			}
			return addToQueue();
		}
		
		abstract T addToQueue();
		
	}
	
	public static abstract class Task extends TaskAbst<ThrowingRunnable<? extends Throwable>, Task> {
		
		Task(ThrowingRunnable<? extends Throwable> executable, boolean creationTracking) {
			super(executable, creationTracking);
		}
		
		@Override
		void execute0() throws Throwable {
			this.executable.run();			
		}
		
	}
	
	public static abstract class ProducerTask<T> extends TaskAbst<ThrowingSupplier<T, ? extends Throwable>, ProducerTask<T>> {
		private T result;
		
		ProducerTask(ThrowingSupplier<T, ? extends Throwable> executable, boolean creationTracking) {
			super(executable, creationTracking);
		}		
		
		@Override
		void execute0() throws Throwable {
			result = executable.get();			
		}

		
		public T join() {
			join0();
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
			queuedTasksExecutors.put(
				String.valueOf(Thread.MAX_PRIORITY),
				createQueuedTasksExecutor(
					name + " - High priority tasks executor",
					Thread.MAX_PRIORITY, isDaemon
				)
			);
			queuedTasksExecutors.put(
				String.valueOf(Thread.NORM_PRIORITY),
				createQueuedTasksExecutor(
					name + " - Normal priority tasks executor", 
					Thread.NORM_PRIORITY, isDaemon
				)
			);
			queuedTasksExecutors.put(
				String.valueOf(Thread.MIN_PRIORITY),
				createQueuedTasksExecutor(
					name + " - Low priority tasks executor",
					Thread.MIN_PRIORITY, isDaemon
				)
			);
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
			return getByPriority(priority).createTask(executable);
		}

		QueuedTasksExecutor getByPriority(int priority) {
			QueuedTasksExecutor queuedTasksExecutor = queuedTasksExecutors.get(String.valueOf(priority));
			if (queuedTasksExecutor == null) {
				queuedTasksExecutor = queuedTasksExecutors.get(String.valueOf(checkAndCorrectPriority(priority)));
			}	
			return queuedTasksExecutor;
		}

		int checkAndCorrectPriority(int priority) {
			if (priority != Thread.MIN_PRIORITY || 
				priority != Thread.NORM_PRIORITY || 
				priority != Thread.MAX_PRIORITY	
			) {
				if (priority < Thread.NORM_PRIORITY) {
					return Thread.MIN_PRIORITY;
				} else if (priority < Thread.MAX_PRIORITY) {
					return Thread.NORM_PRIORITY;
				} else {
					return Thread.MAX_PRIORITY;
				}
			}
			return priority;
		}
		
		public Task createTask(ThrowingRunnable<? extends Throwable> executable) {
			return createTask(executable, Thread.currentThread().getPriority());
		}
		
		public Task createTask(ThrowingRunnable<? extends Throwable> executable, int priority) {
			return getByPriority(priority).createTask(executable);
		}
		
		QueuedTasksExecutor createQueuedTasksExecutor(String executorName, int priority, boolean isDaemon) {
			return new QueuedTasksExecutor(executorName, priority, isDaemon) {
				
				<T> Function<ThrowingSupplier<T, ? extends Throwable>, QueuedTasksExecutor.ProducerTask<T>> getProducerTaskSupplier() {
					return executable -> new QueuedTasksExecutor.ProducerTask<T>(executable, taskCreationTrackingEnabled) {
						
						@Override
						QueuedTasksExecutor.ProducerTask<T> addToQueue() {
							return Group.this.getByPriority(this.priority).addToQueue(this, false);
						};
						
						@Override
						public QueuedTasksExecutor.ProducerTask<T> changePriority(int priority) {
							Group.this.changePriority(this, priority);
							return this;
						};
						
						@Override
						void preparingToStart() {
							QueuedTasksExecutor queuedTasksExecutor = Group.this.getByPriority(this.priority);
							queuedTasksExecutor.tasksInExecution.add(this);			
						}

						@Override
						void preparingToFinish() {
							QueuedTasksExecutor queuedTasksExecutor = Group.this.getByPriority(this.priority);
							queuedTasksExecutor.tasksInExecution.remove(this);
							++queuedTasksExecutor.executedTasksCount;					
						};
					};
				}
				
				<T> Function<ThrowingRunnable<? extends Throwable> , QueuedTasksExecutor.Task> getTaskSupplier() {
					return executable -> new QueuedTasksExecutor.Task(executable, taskCreationTrackingEnabled) {
						
						@Override
						QueuedTasksExecutor.Task addToQueue() {
							return Group.this.getByPriority(this.priority).addToQueue(this, false);
						};
						
						@Override
						public QueuedTasksExecutor.Task changePriority(int priority) {
							Group.this.changePriority(this, priority);
							return this;
						};
						
						@Override
						void preparingToStart() {
							QueuedTasksExecutor queuedTasksExecutor = Group.this.getByPriority(this.priority);
							queuedTasksExecutor.tasksInExecution.add(this);				
						}

						@Override
						void preparingToFinish() {
							QueuedTasksExecutor queuedTasksExecutor = Group.this.getByPriority(this.priority);
							queuedTasksExecutor.tasksInExecution.remove(this);
							++queuedTasksExecutor.executedTasksCount;				
						};
						
					};
				}
				
				@Override
				public QueuedTasksExecutor waitForTasksEnding(int priority, boolean waitForNewAddedTasks) {
					if (priority == defaultPriority) {
						if (!tasksQueue.isEmpty()) {
							synchronized(executingFinishedWaiter) {
								if (!tasksQueue.isEmpty()) {
									try {
										executingFinishedWaiter.wait();
									} catch (InterruptedException exc) {
										logWarn("Exception occurred", exc);
									}
								}
							}
						}
						tasksInExecution.stream().forEach(task -> {
							logInfo("{}", queueConsumer);
							task.logInfo();
							task.join0();
						});
					} else {	
						tasksQueue.stream().forEach(executable -> executable.changePriority(priority)); 
						waitForTasksInExecutionEnding(priority);				
					}
					if (waitForNewAddedTasks && (!tasksInExecution.isEmpty() || !tasksQueue.isEmpty())) {
						waitForTasksEnding(priority, waitForNewAddedTasks);
					}
					return this;
				}
				
				@Override
				public <E, T extends TaskAbst<E, T>> QueuedTasksExecutor waitFor(T task, int priority) {
					task.waitForFinish();
					return this;
				}
				
				@Override
				Task createSuspendingTask(int priority) {
					return createTask((ThrowingRunnable<?>)() -> supended = Boolean.TRUE);
				}
			};
		}
		
		<E, T extends TaskAbst<E, T>> Group changePriority(T task, int priority) {
			int oldPriority = task.priority;
			task.priority = checkAndCorrectPriority(priority);
			if (oldPriority != priority) {
				synchronized (task) {
					if (getByPriority(oldPriority).tasksQueue.remove(task)) {
						getByPriority(priority).addToQueue(task, true);
					}
				}
			}
			return this;
		}
		
		public boolean shutDown(boolean waitForTasksTermination) {
			QueuedTasksExecutor lastToBeWaitedFor = getByPriority(Thread.currentThread().getPriority());
			for (Entry<String, QueuedTasksExecutor> queuedTasksExecutorBox : queuedTasksExecutors.entrySet()) {
				QueuedTasksExecutor queuedTasksExecutor = queuedTasksExecutorBox.getValue();
				if (queuedTasksExecutor != lastToBeWaitedFor) {
					queuedTasksExecutor.shutDown(waitForTasksTermination);
				}
			}
			lastToBeWaitedFor.shutDown(waitForTasksTermination);	
			queuedTasksExecutors.clear();
			queuedTasksExecutors = null;
			return true;
		}
		
		public Group waitForTasksEnding() {
			return waitForTasksEnding(Thread.currentThread().getPriority(), false);
		}
		
		public Group waitForTasksEnding(boolean waitForNewAddedTasks) {
			return waitForTasksEnding(Thread.currentThread().getPriority(), waitForNewAddedTasks);
		}
		
		public Group waitForTasksEnding(int priority, boolean waitForNewAddedTasks) {
			QueuedTasksExecutor lastToBeWaitedFor = getByPriority(priority);
			for (Entry<String, QueuedTasksExecutor> queuedTasksExecutorBox : queuedTasksExecutors.entrySet()) {
				QueuedTasksExecutor queuedTasksExecutor = queuedTasksExecutorBox.getValue();
				if (queuedTasksExecutor != lastToBeWaitedFor) {
					queuedTasksExecutor.waitForTasksEnding(priority, waitForNewAddedTasks);
				}
			}
			lastToBeWaitedFor.waitForTasksEnding(priority, waitForNewAddedTasks);	
			for (Entry<String, QueuedTasksExecutor> queuedTasksExecutorBox : queuedTasksExecutors.entrySet()) {
				QueuedTasksExecutor queuedTasksExecutor = queuedTasksExecutorBox.getValue();
				if (waitForNewAddedTasks && (!queuedTasksExecutor.tasksQueue.isEmpty() || !queuedTasksExecutor.tasksInExecution.isEmpty())) {
					waitForTasksEnding(priority, waitForNewAddedTasks);
					break;
				}
			}
			return this;
		}

		public <E, T extends TaskAbst<E, T>> Group waitFor(T task) {
			return waitFor(task, Thread.currentThread().getPriority());	
		}
		
		public <E, T extends TaskAbst<E, T>> Group waitFor(T task, int priority) {
			if (task.getPriority() != priority) {
				task.changePriority(priority);
			}
			task.waitForFinish();
			return this;
		}
		
		public Group setTasksCreationTrackingFlag(boolean flag) {
			for (Entry<String, QueuedTasksExecutor> queuedTasksExecutorBox : queuedTasksExecutors.entrySet()) {
				queuedTasksExecutorBox.getValue().setTasksCreationTrackingFlag(flag);
			}
			return this;
		}
		
		public Group logQueuesInfo() {
			for (Entry<String, QueuedTasksExecutor> queuedTasksExecutorBox : queuedTasksExecutors.entrySet()) {
				queuedTasksExecutorBox.getValue().logQueueInfo();
			}
			return this;
		}

		public <E, T extends TaskAbst<E, T>> boolean abort(T task) {
			for (Entry<String, QueuedTasksExecutor> queuedTasksExecutorBox : queuedTasksExecutors.entrySet()) {
				if (queuedTasksExecutorBox.getValue().abort(task)) {
					return true;
				}
			}
			return false;
		}
	}

}
