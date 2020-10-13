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

import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggersRepository;
import static org.burningwave.core.assembler.StaticComponentContainer.Methods;
import static org.burningwave.core.assembler.StaticComponentContainer.Strings;
import static org.burningwave.core.assembler.StaticComponentContainer.Synchronizer;
import static org.burningwave.core.assembler.StaticComponentContainer.ThreadHolder;
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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
	Thread.Supplier threadSupplier;
	String name;
	java.lang.Thread tasksLauncher;
	List<TaskAbst<?, ?>> tasksQueue;
	Set<TaskAbst<?, ?>> tasksInExecution;
	Boolean supended;
	volatile int defaultPriority;
	long executedTasksCount;
	volatile long executorsIndex;
	boolean isDaemon;
	Boolean terminated;
	Runnable initializer;
	boolean taskCreationTrackingEnabled;
	Object resumeCallerMutex;
	Object executingFinishedWaiterMutex;
	Object suspensionCallerMutex;
	Object executableCollectionFillerMutex;
	Object terminatingMutex;
	
	static {
		runOnlyOnceTasksToBeExecuted = new ConcurrentHashMap<>();
	}
	
	QueuedTasksExecutor(String name, Thread.Supplier threadSupplier, int defaultPriority, boolean isDaemon) {
		initializer = () -> {
			this.threadSupplier = threadSupplier;
			tasksQueue = new CopyOnWriteArrayList<>();
			tasksInExecution = ConcurrentHashMap.newKeySet();
			this.resumeCallerMutex = new Object();
			this.executingFinishedWaiterMutex = new Object();
			this.suspensionCallerMutex = new Object();
			this.executableCollectionFillerMutex = new Object();
			this.terminatingMutex = new Object();
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
	
	void init0() {		
		supended = Boolean.FALSE;
		terminated = Boolean.FALSE;
		executedTasksCount = 0;
		tasksLauncher = threadSupplier.getOrCreate(name + " launcher").setExecutable(thread -> {
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
						task.setExecutor(threadSupplier.getOrCreate()).start();
					}
				} else {
					synchronized(executableCollectionFillerMutex) {
						if (tasksQueue.isEmpty()) {
							try {
								synchronized(executingFinishedWaiterMutex) {
									executingFinishedWaiterMutex.notifyAll();
								}
								if (!supended) {
									executableCollectionFillerMutex.wait();
								}
							} catch (InterruptedException exc) {
								logError(exc);
							}
						}
					}
				}
			}
			synchronized(terminatingMutex) {
				tasksLauncher = null;
				terminatingMutex.notifyAll();
			}
		});
		tasksLauncher.setPriority(this.defaultPriority);
		tasksLauncher.setDaemon(isDaemon);
		tasksLauncher.start();
	}
	
	private boolean checkAndNotifySuspension() {
		if (supended) {
			synchronized(resumeCallerMutex) {
				synchronized (suspensionCallerMutex) {
					suspensionCallerMutex.notifyAll();
				}
				try {
					resumeCallerMutex.wait();
					return true;
				} catch (InterruptedException exc) {
					logError(exc);
				}
			}
		}
		return false;
	}

	public static QueuedTasksExecutor create(String executorName, Thread.Supplier threadSupplier, int initialPriority) {
		return create(executorName, threadSupplier, initialPriority, false, false);
	}
	
	public static QueuedTasksExecutor create(String executorName, Thread.Supplier threadSupplier, int initialPriority, boolean daemon, boolean undestroyable) {
		if (undestroyable) {
			return new QueuedTasksExecutor(executorName, threadSupplier, initialPriority, daemon) {
				StackTraceElement[] stackTraceOnCreation = Thread.currentThread().getStackTrace();
				@Override
				public boolean shutDown(boolean waitForTasksTermination) {
					if (Methods.retrieveExternalCallerInfo().getClassName().equals(Methods.retrieveExternalCallerInfo(stackTraceOnCreation).getClassName())) {
						return super.shutDown(waitForTasksTermination);
					}
					return false;
				}
				
			};
		} else {
			return new QueuedTasksExecutor(executorName, threadSupplier, initialPriority, daemon);
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
			
			QueuedTasksExecutor getQueuedTasksExecutor() {
				return QueuedTasksExecutor.this;
			}

			@Override
			QueuedTasksExecutor retrieveQueuedTasksExecutorOf(java.lang.Thread thread) {
				return QueuedTasksExecutor.this;
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
			
			QueuedTasksExecutor getQueuedTasksExecutor() {
				return QueuedTasksExecutor.this;
			};
			
			@Override
			QueuedTasksExecutor retrieveQueuedTasksExecutorOf(java.lang.Thread thread) {
				return QueuedTasksExecutor.this;
			};
			
		};
	}

	<E, T extends TaskAbst<E, T>> T addToQueue(T task, boolean skipCheck) {
		Object[] canBeExecutedBag = null;
		if (skipCheck || (Boolean)(canBeExecutedBag = canBeExecuted(task))[1]) {
			try {
				tasksQueue.add(task);
				synchronized(executableCollectionFillerMutex) {
					executableCollectionFillerMutex.notifyAll();
				}
			} catch (Throwable exc) {
				logError(exc);
			}
		}
		return canBeExecutedBag != null ? (T)canBeExecutedBag[0] : task;
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
	
	void kill(TaskAbst<?, ?> task) {
		if (task.hasFinished()) {
			return;
		}
		task.aborted = true;
		tasksQueue.remove(task);
		tasksInExecution.remove(task);
		if (task.runOnlyOnce) {
			TaskAbst<?, ?> runOnlyOnceTask = runOnlyOnceTasksToBeExecuted.get(task.id);
			if (runOnlyOnceTask != null) {
				runOnlyOnceTasksToBeExecuted.remove(runOnlyOnceTask.id);
				tasksQueue.remove(runOnlyOnceTask);
				tasksInExecution.remove(runOnlyOnceTask);
				if (!runOnlyOnceTask.hasFinished()) {
					runOnlyOnceTask.aborted = true;
					Thread taskExecutor = runOnlyOnceTask.executor;
					if (taskExecutor != null) {
						taskExecutor.interrupt();
					}
					runOnlyOnceTask.clear();
					synchronized(runOnlyOnceTask) {
						runOnlyOnceTask.notifyAll();
					}
				}
			}
		}
		Thread taskExecutor = task.executor;
		if (taskExecutor != null) {
			taskExecutor.interrupt();
		}
		task.clear();
		synchronized (task) {
			task.notifyAll();
		}
	}
	
	public <E, T extends TaskAbst<E, T>> boolean abort(T task) {
		synchronized (task) {
			if (!task.isSubmitted()) {
				task.aborted = true;
				task.clear();
			}
		}
		if (!task.isStarted()) {
			if (task.runOnlyOnce) {
				for (TaskAbst<?, ?> queuedTask : tasksQueue) {
					if (task.id.equals(queuedTask.id)) {
						synchronized (queuedTask) {
							if (tasksQueue.remove(queuedTask)) {
								if (!queuedTask.isStarted()) {
									task.aborted = queuedTask.aborted = true;
									queuedTask.clear();
									task.clear();
									queuedTask.notifyAll();
									synchronized(task) {
										task.notifyAll();
									}
									runOnlyOnceTasksToBeExecuted.remove(queuedTask.id);
									return task.aborted;
								}
							}
						}
					}
				}
				return task.aborted;
			}
			synchronized (task) {
				if (task.aborted = tasksQueue.remove(task)) {
					task.notifyAll();
					task.clear();
					return task.aborted;
				}
			}
		}
		return task.aborted;
	}
	
	public QueuedTasksExecutor waitForTasksEnding(int priority, boolean waitForNewAddedTasks) {
		waitForTasksEnding(priority);
		if (waitForNewAddedTasks) {
			while (!tasksInExecution.isEmpty() || !tasksQueue.isEmpty()) {
				waitForTasksEnding(priority);
			}
		}
		return this;
	}
	
	public QueuedTasksExecutor waitForTasksEnding(int priority) {
		tasksLauncher.setPriority(priority);
		tasksQueue.stream().forEach(executable -> executable.changePriority(priority)); 
		if (!tasksQueue.isEmpty()) {
			synchronized(executingFinishedWaiterMutex) {
				if (!tasksQueue.isEmpty()) {
					try {
						executingFinishedWaiterMutex.wait();
					} catch (InterruptedException exc) {
						logError(exc);
					}
				}
			}
		}
		waitForTasksInExecutionEnding(priority);
		tasksLauncher.setPriority(this.defaultPriority);
		return this;
	}
	
	public QueuedTasksExecutor changePriority(int priority) {
		this.defaultPriority = priority;
		tasksLauncher.setPriority(priority);
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
		tasksLauncher.setPriority(priority);
		if (immediately) {
			synchronized (suspensionCallerMutex) {
				supended = Boolean.TRUE;
				waitForTasksInExecutionEnding(priority);
				try {
					synchronized(executableCollectionFillerMutex) {
						if (this.tasksLauncher.getState().equals(Thread.State.WAITING)) {
							executableCollectionFillerMutex.notifyAll();
						}
					}
					suspensionCallerMutex.wait();
				} catch (InterruptedException exc) {
					logError(exc);
				}
			}
		} else {
			waitForTasksInExecutionEnding(priority);
			Task supendingTask = createSuspendingTask(priority);
			changePriorityToAllTaskBefore(supendingTask.addToQueue(), priority);
			supendingTask.waitForFinish();
		}
		tasksLauncher.setPriority(this.defaultPriority);
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
			//logInfo("{}", queueConsumer);
			//task.logInfo();
			task.waitForFinish();
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
		synchronized(resumeCallerMutex) {
			try {
				supended = Boolean.FALSE;
				resumeCallerMutex.notifyAll();
			} catch (Throwable exc) {
				logError(exc);
			}
		}	
		return this;
	}
	
	public boolean shutDown(boolean waitForTasksTermination) {
		Collection<TaskAbst<?, ?>> executables = this.tasksQueue;
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
		if (tasksLauncher != null) {
			synchronized (terminatingMutex) {
				if (tasksLauncher != null) {
					try {
						terminatingMutex.wait();
					} catch (InterruptedException exc) {
						logError(exc);
					}
				}
			}
		}
		closeResources();			
		return true;
	}
	
	public void logStatus() {
		List<TaskAbst<?, ?>> tasks = new ArrayList<>(tasksQueue);
		tasks.addAll(this.tasksInExecution);
		logStatus(this.executedTasksCount, tasks);
	}
	
	private void logStatus(Long executedTasksCount, Collection<TaskAbst<?, ?>> executables) {
		Collection<String> executablesLog = executables.stream().map(task -> {
			Object executable = task.executable;
			if (executable != null) {
				return "\t" + executable;
			}
			return null;
		}).filter(threadInfo -> threadInfo != null).collect(Collectors.toList());
		StringBuffer log = new StringBuffer(this.tasksLauncher.getName() + " - launched tasks: ")
			.append(executedTasksCount).append(", not launched tasks: ")
			.append(executablesLog.size());
			
		if (executablesLog.size() > 0) {
			log.append(":\n\t")
			.append(String.join("\n\t", executablesLog));
		}		
		logInfo(log.toString());
	}
	
	public String getInfoAsString() {
		StringBuffer log = new StringBuffer("");
		Collection<TaskAbst<?, ?>> tasksQueue = this.tasksQueue;
		if (!tasksQueue.isEmpty()) {
			log.append("\n\n");
			log.append(Strings.compile("{} - Tasks to be executed:", tasksLauncher));
			for (TaskAbst<?,?> task : tasksQueue) {
				log.append("\n" + task.getInfoAsString());
			}			
		}
		tasksQueue = this.tasksInExecution;
		if (!tasksQueue.isEmpty()) {
			log.append("\n\n");
			log.append(Strings.compile("{} - Tasks in execution:", tasksLauncher));
			for (TaskAbst<?,?> task : tasksQueue) {
				log.append("\n" + task.getInfoAsString());
			}
		}
		return log.toString();
	}
	
	public void logInfo() {
		String message = getInfoAsString();
		if (!message.isEmpty()) {
			logInfo(message);
		}
	}
	
	@Override
	public void close() {
		shutDown(true);
	}
	
	void closeResources() {
		//queueConsumer = null;
		threadSupplier = null;
		tasksQueue = null;
		tasksInExecution = null;
		initializer = null;
		terminated = null;
		supended = null;
		resumeCallerMutex = null;            
		executingFinishedWaiterMutex = null;    
		suspensionCallerMutex = null;           
		executableCollectionFillerMutex = null; 
		logInfo("All resources of '{}' have been closed", name);
		name = null;		
	}
	
	public static abstract class TaskAbst<E, T extends TaskAbst<E, T>> implements ManagedLogger {
		
		String name;
		Long executorIndex;
		StackTraceElement[] stackTraceOnCreation;
		List<StackTraceElement> creatorInfos;
		Supplier<Boolean> hasBeenExecutedChecker;
		volatile boolean runOnlyOnce;		
		volatile String id;
		volatile int priority;
		volatile Long startTime;
		volatile boolean submitted;
		volatile boolean aborted;
		volatile boolean finished;
		volatile boolean queueConsumerUnlockingRequested;
		E executable;		
		Thread executor;
		Throwable exc;
		ThrowingBiConsumer<T, Throwable, Throwable> exceptionHandler;
		QueuedTasksExecutor queuedTasksExecutor;
		
		public TaskAbst(E executable, boolean creationTracking) {
			this.executable = executable;
			if (creationTracking) {
				stackTraceOnCreation = Thread.currentThread().getStackTrace();
			}
		}
		
		T start() {
			executor.start();
			return (T)this;
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
		
		public Long getStartTime() {
			return startTime;
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
			return startTime != null;
		}
		
		public boolean hasFinished() {
			return finished;
		}
		
		public T runOnlyOnce(String id, Supplier<Boolean> hasBeenExecutedChecker) {
			if (isSubmitted()) {
				Throwables.throwException(new TaskStateException(this, "is submitted"));
			}
			runOnlyOnce = true;
			this.id = id;
			this.hasBeenExecutedChecker = hasBeenExecutedChecker;
			return (T)this;
		}
		
		public boolean isAborted() {
			return aborted;
		}
		
		public boolean isSubmitted() {
			return submitted;
		}
		
		public T waitForStarting() {
			while(waitForStarting0());
			return (T)this;
		}
		
		public boolean waitForStarting0() {
			java.lang.Thread currentThread = Thread.currentThread();
			if (currentThread == this.executor) {
				return false;
			}
			if (isSubmitted()) {
				if (!isStarted()) {
					synchronized (this) {
						if (!isStarted()) {
							try {
								if (isAborted()) {
									Throwables.throwException(new TaskStateException(this, "is aborted"));
								}
								wait();
								return true;
							} catch (InterruptedException exc) {
								Throwables.throwException(exc);
							}
						}
					}
				}
			} else {
				Throwables.throwException(new TaskStateException(this, "is not submitted"));
			}
			return false;
		}		
		
		public T waitForFinish() {
			while(waitForFinish0());
			return (T)this;
		}

		private boolean waitForFinish0() {
			java.lang.Thread currentThread = Thread.currentThread();
			if (currentThread == this.executor) {
				return false;
			}
			if (isSubmitted()) {
				if (!hasFinished()) {
					synchronized (this) {
						if (!hasFinished()) {
							try {
								if (isAborted()) {
									Throwables.throwException(new TaskStateException(this, "is aborted"));
								}
								wait();
								return true;
							} catch (InterruptedException exc) {
								Throwables.throwException(exc);
							}
						}
					}
				}
			} else {
				Throwables.throwException(new TaskStateException(this, "is not submitted"));
			}
			return false;
		}
		
		void execute() {
			synchronized (this) {
				if (aborted) {
					notifyAll();
					clear();
					return;
				}

			}
			preparingToExecute();
			synchronized (this) {
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
			} finally {
				markAsFinished();
			}
		}
		
		String getInfoAsString() {
			if (this.getCreatorInfos() != null) {
				Thread executor = this.executor;
				return Strings.compile("\n\tTask status: {} {} \n\tcreated by: {}",
					Strings.compile("\n\t\tpriority: {}\n\t\tstarted: {}\n\t\taborted: {}\n\t\tfinished: {}", priority, isStarted(), isAborted(), hasFinished()),
					executor != null ? "\n\t" + executor + Strings.from(executor.getStackTrace(),2) : "",
					Strings.from(this.getCreatorInfos(), 2)
				);
			}
			return "";
		}
		
		void logInfo() {
			if (this.getCreatorInfos() != null) {
				logInfo(getInfoAsString());
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
		
		void clear() {
			if (runOnlyOnce) {
				runOnlyOnceTasksToBeExecuted.remove(((Task)this).id);
			}
			if (executorIndex != null) {
				executorIndex = null;
				--queuedTasksExecutor.executorsIndex;
			}
			executable = null;
			executor = null;
			queuedTasksExecutor = null;
		}
	
		void markAsFinished() {
			finished = true;
			synchronized(this) {
				queuedTasksExecutor.tasksInExecution.remove(this);
				++queuedTasksExecutor.executedTasksCount;
				notifyAll();	
			}
			clear();			
		}
		
		abstract void execute0() throws Throwable;
		
		T setExecutor(Thread thread) {
			executor = thread.setExecutable(thr -> this.execute());
			executor.setPriority(this.priority);
			QueuedTasksExecutor queuedTasksExecutor = getQueuedTasksExecutor();
			if (name != null) {
				executor.setName(queuedTasksExecutor.name + " - " + name);
			} else {				;
				executor.setName(queuedTasksExecutor.name + " executor " + (executorIndex = ++queuedTasksExecutor.executorsIndex));
			}
			return (T)this;
		}
		
		public T changePriority(int priority) {
			this.priority = priority;
			if (executor != null) {
				executor.setPriority(this.priority);
			}
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
			if (aborted) {
				Throwables.throwException(new TaskStateException(this, "is aborted"));
			}
			if (!submitted) {
				synchronized(this) {
					if (!submitted) {
						submitted = true;
					} else {
						Throwables.throwException(new TaskStateException(this, "is already submitted"));
					}
				}
			} else {
				Throwables.throwException(new TaskStateException(this, "is already submitted"));
			}
			return addToQueue();
		}
		
		T addToQueue() {
			return getQueuedTasksExecutor().addToQueue((T)this, false);
		}

		void preparingToExecute() {
			queuedTasksExecutor = getQueuedTasksExecutor();
			startTime = System.currentTimeMillis();
			queuedTasksExecutor.tasksInExecution.add(this);				
		}
		
		public T abortOrWaitForFinish() {
			if (!abort().isAborted() && isStarted()) {
				waitForFinish();
			}
			return (T)this;
		}
		
		public T abort() {
			getQueuedTasksExecutor().abort((T)this);
			return (T)this;
		}
		
		abstract QueuedTasksExecutor getQueuedTasksExecutor();
		
		abstract QueuedTasksExecutor retrieveQueuedTasksExecutorOf(java.lang.Thread thread);
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
			waitForFinish();
			return result;
		}
		
		public T get() {
			return result;
		}
		
	}
	
	public static class Group implements ManagedLogger{
		Map<String, QueuedTasksExecutor> queuedTasksExecutors;
		Map<TaskAbst<?, ?>, StackTraceElement[]> waitingTasksAndLastStackTrace;
		
		Group(String name, 
			Thread.Supplier threadSupplierForHighPriorityTasksExecutor,
			Thread.Supplier threadSupplierForNormalPriorityTasksExecutor,
			Thread.Supplier threadSupplierForLowPriorityTasksExecutor,
			boolean isDaemon
		) {
			queuedTasksExecutors = new HashMap<>();
			waitingTasksAndLastStackTrace = new HashMap<>();
			queuedTasksExecutors.put(
				String.valueOf(Thread.MAX_PRIORITY),
				createQueuedTasksExecutor(
					name + " - High priority tasks",
					threadSupplierForHighPriorityTasksExecutor,
					Thread.MAX_PRIORITY, isDaemon
				)
			);
			queuedTasksExecutors.put(
				String.valueOf(Thread.NORM_PRIORITY),
				createQueuedTasksExecutor(
					name + " - Normal priority tasks",
					threadSupplierForNormalPriorityTasksExecutor,
					Thread.NORM_PRIORITY, isDaemon
				)
			);
			queuedTasksExecutors.put(
				String.valueOf(Thread.MIN_PRIORITY),
				createQueuedTasksExecutor(
					name + " - Low priority tasks", 
					threadSupplierForLowPriorityTasksExecutor,
					Thread.MIN_PRIORITY, isDaemon
				)
			);
		}
		
		public static Group create(
			String name,
			Thread.Supplier threadSupplierForHighPriorityTasksExecutor,
			Thread.Supplier threadSupplierForNormalPriorityTasksExecutor,
			Thread.Supplier threadSupplierForLowPriorityTasksExecutor,
			boolean isDaemon,
			long allTasksMonitoringInterval,
			long minimumElapsedTimeToConsiderATaskAsDeadLocked,
			boolean deadLockedTasksKillingEnabled
		) {
			return create(
				name,
				threadSupplierForHighPriorityTasksExecutor, 
				threadSupplierForNormalPriorityTasksExecutor,
				threadSupplierForLowPriorityTasksExecutor,
				isDaemon,
				false
			);
		}
		
		public static Group create(
			String name,
			Thread.Supplier threadSupplier,
			boolean isDaemon,
			long allTasksMonitoringInterval,
			long minimumElapsedTimeToConsiderATaskAsDeadLocked,
			boolean deadLockedTasksKillingEnabled,
			boolean undestroyableFromExternal
		) {
			return create(name, threadSupplier, threadSupplier, threadSupplier, isDaemon, allTasksMonitoringInterval, minimumElapsedTimeToConsiderATaskAsDeadLocked, undestroyableFromExternal);
		}
		
		public static Group create(
			String name,
			Thread.Supplier threadSupplierForHighPriorityTasksExecutor,
			Thread.Supplier threadSupplierForNormalPriorityTasksExecutor,
			Thread.Supplier threadSupplierForLowPriorityTasksExecutor,
			boolean isDaemon,
			boolean undestroyableFromExternal
		) {
			if (!undestroyableFromExternal) {
				return new Group(
					name, 
					threadSupplierForHighPriorityTasksExecutor, 
					threadSupplierForNormalPriorityTasksExecutor,
					threadSupplierForLowPriorityTasksExecutor,
					isDaemon
				);
			} else {
				return new Group(name, 
					threadSupplierForHighPriorityTasksExecutor, 
					threadSupplierForNormalPriorityTasksExecutor,
					threadSupplierForLowPriorityTasksExecutor,
					isDaemon
				) {
					StackTraceElement[] stackTraceOnCreation = Thread.currentThread().getStackTrace();
					
					@Override
					public boolean shutDown(boolean waitForTasksTermination) {
						if (Methods.retrieveExternalCallerInfo().getClassName().equals(Methods.retrieveExternalCallerInfo(stackTraceOnCreation).getClassName())) {
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
		
		QueuedTasksExecutor createQueuedTasksExecutor(String executorName, Thread.Supplier threadSupplier, int priority, boolean isDaemon) {
			return new QueuedTasksExecutor(executorName, threadSupplier, priority, isDaemon) {
				
				<T> Function<ThrowingSupplier<T, ? extends Throwable>, QueuedTasksExecutor.ProducerTask<T>> getProducerTaskSupplier() {
					return executable -> new QueuedTasksExecutor.ProducerTask<T>(executable, taskCreationTrackingEnabled) {
						
						@Override
						QueuedTasksExecutor getQueuedTasksExecutor() {
							return this.queuedTasksExecutor != null?
								this.queuedTasksExecutor : Group.this.getByPriority(this.priority);
						};

						@Override
						public QueuedTasksExecutor.ProducerTask<T> changePriority(int priority) {
							Group.this.changePriority(this, priority);
							return this;
						};
						
						@Override
						QueuedTasksExecutor retrieveQueuedTasksExecutorOf(java.lang.Thread thread) {
							return Group.this.getByPriority(thread.getPriority());
						};
						
					};
				}
				
				<T> Function<ThrowingRunnable<? extends Throwable> , QueuedTasksExecutor.Task> getTaskSupplier() {
					return executable -> new QueuedTasksExecutor.Task(executable, taskCreationTrackingEnabled) {
						
						@Override
						QueuedTasksExecutor getQueuedTasksExecutor() {
							return this.queuedTasksExecutor != null?
								this.queuedTasksExecutor : Group.this.getByPriority(this.priority);
						};

						@Override
						public QueuedTasksExecutor.Task changePriority(int priority) {
							Group.this.changePriority(this, priority);
							return this;
						}

						@Override
						QueuedTasksExecutor retrieveQueuedTasksExecutorOf(java.lang.Thread thread) {
							return Group.this.getByPriority(thread.getPriority());
						};
					};
				}

				@Override
				public QueuedTasksExecutor waitForTasksEnding(int priority) {
					if (priority == defaultPriority) {
						if (!tasksQueue.isEmpty()) {
							synchronized(executingFinishedWaiterMutex) {
								if (!tasksQueue.isEmpty()) {
									try {
										executingFinishedWaiterMutex.wait();
									} catch (InterruptedException exc) {
										logError(exc);
									}
								}
							}
						}
						tasksInExecution.stream().forEach(task -> {
							//logInfo("{}", queueConsumer);
							//task.logInfo();
							task.waitForFinish();
						});
					} else {	
						tasksQueue.stream().forEach(executable ->
							executable.changePriority(priority)
						); 
						waitForTasksInExecutionEnding(priority);				
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
			int newPriority = checkAndCorrectPriority(priority);
			if (oldPriority != priority) {
				synchronized (task) {
					if (getByPriority(oldPriority).tasksQueue.remove(task)) {
						task.priority = newPriority;
						QueuedTasksExecutor queuedTasksExecutor = getByPriority(newPriority);
						task.queuedTasksExecutor = null;
						task.executor = null;
						queuedTasksExecutor.addToQueue(task, true);
					}
				}
			}
			return this;
		}
		
		public boolean isClosed() {
			return queuedTasksExecutors == null;
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
		
		public Group logInfo() {
			String loggableMessage = getInfoAsString();
			loggableMessage = getInfoAsString();
			if (!loggableMessage.isEmpty()) {
				logInfo(loggableMessage);
			}
			return this;
		}
		
		public String getInfoAsString() {
			StringBuffer loggableMessage = new StringBuffer("");
			for (Entry<String, QueuedTasksExecutor> queuedTasksExecutorBox : queuedTasksExecutors.entrySet()) {
				loggableMessage.append(queuedTasksExecutorBox.getValue().getInfoAsString());
			}
			return loggableMessage.toString();
		}

		public <E, T extends TaskAbst<E, T>> boolean abort(T task) {
			for (Entry<String, QueuedTasksExecutor> queuedTasksExecutorBox : queuedTasksExecutors.entrySet()) {
				if (queuedTasksExecutorBox.getValue().abort(task)) {
					return true;
				}
			}
			return false;
		}
		
		public Collection<TaskAbst<?, ?>> getAllTasksInExecution() {
			Collection<TaskAbst<?, ?>> tasksInExecution = new HashSet<>();
			for (Entry<String, QueuedTasksExecutor> queuedTasksExecutorBox : queuedTasksExecutors.entrySet()) {
				tasksInExecution.addAll(
					queuedTasksExecutorBox.getValue().tasksInExecution
				);
			}
			return tasksInExecution;
		}
		
		void kill(TaskAbst<?,?> task) {
			for (Entry<String, QueuedTasksExecutor> queuedTasksExecutorBox : queuedTasksExecutors.entrySet()) {
				queuedTasksExecutorBox.getValue().kill(task);
			}
		}
		
		void checkAndKillForDeadLockedTasks(long minimumElapsedTimeToConsiderATaskAsDeadLocked, boolean killDeadLockedTasks) {
			Iterator<Entry<TaskAbst<?, ?>, StackTraceElement[]>> tasksAndStackTracesIterator = waitingTasksAndLastStackTrace.entrySet().iterator();
			while (tasksAndStackTracesIterator.hasNext()) {
				TaskAbst<?, ?> task = tasksAndStackTracesIterator.next().getKey();
				if(task.hasFinished() || task.isAborted()) {
					tasksAndStackTracesIterator.remove();
				}
			}
			long currentTime = System.currentTimeMillis();
			for (TaskAbst<?, ?> task : getAllTasksInExecution()) {
				if (currentTime - task.startTime > minimumElapsedTimeToConsiderATaskAsDeadLocked) {
					java.lang.Thread taskThread = task.executor;
					if (taskThread != null &&
					(taskThread.getState().equals(Thread.State.BLOCKED) ||
					taskThread.getState().equals(Thread.State.WAITING) ||
					taskThread.getState().equals(Thread.State.TIMED_WAITING))) {
						StackTraceElement[] previousRegisteredStackTrace = waitingTasksAndLastStackTrace.get(task);
						if (previousRegisteredStackTrace != null) {
							if (areStrackTracesEquals(previousRegisteredStackTrace, taskThread.getStackTrace())) {
								StringBuffer log = new StringBuffer(
									Strings.compile(
										"Possible deadlock detected for task:{}\n\t",
										task.getInfoAsString()
									)
								);
								if (killDeadLockedTasks) {
									kill(task);
									if (task.isAborted()) {
										taskThread.setName("DEADLOCKED THREAD -> " + taskThread.getName());
										log.append("... So the task has been aborted");
									}
								}	
								ManagedLoggersRepository.logWarn(
									() -> this.getClass().getName(),
									log.toString()
								);
								Synchronizer.logAllThreadsState(true);
							}
						} else {
							waitingTasksAndLastStackTrace.put(task, taskThread.getStackTrace());
						}
					}
				}
			}
		}
		
		private boolean areStrackTracesEquals(StackTraceElement[] stackTraceOne, StackTraceElement[] stackTraceTwo) {
			if (stackTraceOne.length == stackTraceTwo.length) {
				for (int i = 0; i < stackTraceOne.length; i++) {
					if (!stackTraceOne[i].toString().equals(stackTraceTwo[i].toString()) ) {
						return false;
					}
				}
				return true;
			}
			return false;
		}
		
		public synchronized void startAllTasksMonitoring(long interval, long minimumElapsedTimeToConsiderATaskAsDeadLocked, boolean killDeadLockedTasks, boolean allTasksLoggerEnabled) {
			ThreadHolder.startLooping("All tasks monitorer", true, Thread.MIN_PRIORITY, thread -> {
				thread.waitFor(interval);
				if (thread.isLooping()) {
					if (allTasksLoggerEnabled) {
						logInfo();
					}
					checkAndKillForDeadLockedTasks(minimumElapsedTimeToConsiderATaskAsDeadLocked, killDeadLockedTasks);
				}
			});
		}
		
		public void stopAllTasksMonitoring() {
			stopAllTasksMonitoring(false);
		}
		
		public synchronized void stopAllTasksMonitoring(boolean waitThreadToFinish) {
			ThreadHolder.stop("All tasks monitorer");
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
	}

}
