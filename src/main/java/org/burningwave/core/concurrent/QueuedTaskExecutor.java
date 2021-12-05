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
 * Copyright (c) 2019-2021 Roberto Gentili
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

import static org.burningwave.core.assembler.StaticComponentContainer.IterableObjectHelper;
import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository;
import static org.burningwave.core.assembler.StaticComponentContainer.Methods;
import static org.burningwave.core.assembler.StaticComponentContainer.Objects;
import static org.burningwave.core.assembler.StaticComponentContainer.Strings;
import static org.burningwave.core.assembler.StaticComponentContainer.Synchronizer;

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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.burningwave.core.Closeable;
import org.burningwave.core.Identifiable;
import org.burningwave.core.function.Executor;
import org.burningwave.core.function.ThrowingBiPredicate;
import org.burningwave.core.function.ThrowingConsumer;
import org.burningwave.core.function.ThrowingFunction;
import org.burningwave.core.function.ThrowingRunnable;
import org.burningwave.core.function.ThrowingSupplier;
import org.burningwave.core.iterable.IterableObjectHelper.ResolveConfig;

@SuppressWarnings({"unchecked", "resource"})
public class QueuedTaskExecutor implements Closeable {
	private final static Map<String, TaskAbst<?,?>> runOnlyOnceTasks;
	private final static Map<java.lang.Thread, Collection<TaskAbst<?,?>>> taskCreatorThreadsForChildTasks;
	private final static Map<TaskAbst<?, ?>, TaskAbst<?, ?>> allTasksInExecution;
	Map<TaskAbst<?, ?>, TaskAbst<?, ?>> tasksInExecution;
	Thread.Supplier threadSupplier;
	String name;
	java.lang.Thread tasksLauncher;
	List<TaskAbst<?, ?>> tasksQueue;
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
		runOnlyOnceTasks = new ConcurrentHashMap<>();
		taskCreatorThreadsForChildTasks = new ConcurrentHashMap<>();
		allTasksInExecution = new ConcurrentHashMap<>();
	}

	QueuedTaskExecutor(String name, Thread.Supplier threadSupplier, int defaultPriority, boolean isDaemon) {
		initializer = () -> {
			this.threadSupplier = threadSupplier;
			tasksQueue = new CopyOnWriteArrayList<TaskAbst<?, ?>>() {
				private static final long serialVersionUID = -176528742161426076L;
				int min = 1500;
				int max = 2000;
				
				@Override
				public boolean add(TaskAbst<?, ?> e) {
					while (tasksQueue.size() > max) {
						synchronized(this) {
							try {
								wait();
							} catch (Throwable exc) {
								org.burningwave.core.Throwables.throwException(exc);
							}
						}
					}
					return super.add(e);
				}
				
				@Override
				public boolean remove(Object task) {
					boolean removed;
					int size;
					if ((removed = super.remove(task)) && (size = size()) > min && size < max) {
						//ManagedLoggerRepository.logInfo(getClass()::getName, "Collection size: {}", size);
						synchronized(this) {
							this.notifyAll();
						}
					}
					return removed;
				}
				
			};
			
			tasksInExecution = new ConcurrentHashMap<TaskAbst<?, ?>, TaskAbst<?, ?>>() {

				private static final long serialVersionUID = 4138691488536653865L;
				
			    @Override
				public TaskAbst<?, ?> put(TaskAbst<?, ?> key, TaskAbst<?, ?> value) {
			    	allTasksInExecution.put(key, value);
			        return super.put(key, value);
			    }
			    
			    @Override
				public TaskAbst<?, ?> remove(Object key) {
			    	allTasksInExecution.remove(key);
			    	return super.remove(key); 
			    }
			};
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
		tasksLauncher = threadSupplier.createDetachedThread().setExecutable(thread -> {
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
						task.setExecutor(threadSupplier.getOrCreateThread()).start();
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
								ManagedLoggerRepository.logError(getClass()::getName, exc);
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
		tasksLauncher.setName(name + " launcher");
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
					ManagedLoggerRepository.logError(getClass()::getName, exc);
				}
			}
		}
		return false;
	}

	public static QueuedTaskExecutor create(String executorName, Thread.Supplier threadSupplier, int initialPriority) {
		return create(executorName, threadSupplier, initialPriority, false, false);
	}

	public static QueuedTaskExecutor create(String executorName, Thread.Supplier threadSupplier, int initialPriority, boolean daemon, boolean undestroyable) {
		if (undestroyable) {
			return new QueuedTaskExecutor(executorName, threadSupplier, initialPriority, daemon) {
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
			return new QueuedTaskExecutor(executorName, threadSupplier, initialPriority, daemon);
		}
	}

	public QueuedTaskExecutor setTasksCreationTrackingFlag(boolean flag) {
		this.taskCreationTrackingEnabled = flag;
		return this;
	}
	
	public <T> ProducerTask<T> createProducerTask(ThrowingSupplier<T, ? extends Throwable> executable) {
		return createProducerTask(task -> executable.get());
	}
	
	public <T> ProducerTask<T> createProducerTask(ThrowingFunction<ProducerTask<T>, T, ? extends Throwable> executable) {
		Function<ThrowingFunction<ProducerTask<T>, T, ? extends Throwable>, ProducerTask<T>> taskCreator = getProducerTaskSupplier();
		ProducerTask<T> task = taskCreator.apply(executable);
		task.priority = this.defaultPriority;
		return task;
	}

	<T> Function<ThrowingFunction<ProducerTask<T>, T, ? extends Throwable>, ProducerTask<T>> getProducerTaskSupplier() {
		return executable -> new ProducerTask<T>(executable, taskCreationTrackingEnabled) {

			@Override
			QueuedTaskExecutor getQueuedTasksExecutor() {
				return QueuedTaskExecutor.this;
			}

			@Override
			QueuedTaskExecutor retrieveQueuedTasksExecutorOf(java.lang.Thread thread) {
				return QueuedTaskExecutor.this;
			}

		};
	}
	
	public Task createTask(ThrowingRunnable<? extends Throwable> executable) {
		return createTask(task -> executable.run());
	}
	
	public Task createTask(ThrowingConsumer<QueuedTaskExecutor.Task, ? extends Throwable> executable) {
		Task task = getTaskSupplier().apply(executable);
		task.priority = this.defaultPriority;
		return task;
	}
	
	<T> Function<ThrowingConsumer<QueuedTaskExecutor.Task, ? extends Throwable>, Task> getTaskSupplier() {
		return executable -> new Task(executable, taskCreationTrackingEnabled) {

			@Override
			QueuedTaskExecutor getQueuedTasksExecutor() {
				return QueuedTaskExecutor.this;
			}

			@Override
			QueuedTaskExecutor retrieveQueuedTasksExecutorOf(java.lang.Thread thread) {
				return QueuedTaskExecutor.this;
			}

		};
	}

	<E, T extends TaskAbst<E, T>> T addToQueue(T task, boolean skipCheck) {
		Object[] canBeExecutedBag = null;
		if (skipCheck || (Boolean)(canBeExecutedBag = canBeExecuted(task))[1]) {
			try {
				task.creator = Thread.currentThread();
				Synchronizer.execute(Objects.getId(task.creator), () -> {
					Collection<TaskAbst<?,?>> childrenTask = taskCreatorThreadsForChildTasks.computeIfAbsent(task.creator, key -> ConcurrentHashMap.newKeySet());
					childrenTask.add(task);
				});
				tasksQueue.add(task);
				synchronized(executableCollectionFillerMutex) {
					executableCollectionFillerMutex.notifyAll();
				}
			} catch (Throwable exc) {
				ManagedLoggerRepository.logError(getClass()::getName, exc);
			}
		}
		return canBeExecutedBag != null ? (T)canBeExecutedBag[0] : task;
	}

	<E, T extends TaskAbst<E, T>> Object[] canBeExecuted(T task) {
		Object[] bag = new Object[]{task, true};
		if (task.runOnlyOnce) {
			bag[1] =(!task.hasBeenExecutedChecker.get() &&
				Optional.ofNullable(runOnlyOnceTasks.putIfAbsent(
					task.id, task
				)).map(taskk -> {
					bag[0] = taskk;
					return false;
				}).orElseGet(() -> true)
			);
		}
		return bag;
	}

	public <E, T extends TaskAbst<E, T>> QueuedTaskExecutor waitFor(T task) {
		return waitFor(task, java.lang.Thread.currentThread().getPriority(), false);
	}

	public <E, T extends TaskAbst<E, T>> QueuedTaskExecutor waitFor(T task, boolean ignoreDeadLocked) {
		return waitFor(task, java.lang.Thread.currentThread().getPriority(), ignoreDeadLocked);
	}

	public <E, T extends TaskAbst<E, T>> QueuedTaskExecutor waitFor(T task, int priority, boolean ignoreDeadLocked) {
		changePriorityToAllTaskBeforeAndWaitThem(task, priority, ignoreDeadLocked);
		task.waitForFinish(ignoreDeadLocked, false);
		return this;
	}

	public QueuedTaskExecutor waitForTasksEnding() {
		return waitForTasksEnding(java.lang.Thread.currentThread().getPriority(), false);
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
	
	public <E, T extends TaskAbst<E, T>> boolean interrupt(T task) {
		return terminate(task, Thread::interrupt, TaskAbst::interrupt, true);
	}
	
	public <E, T extends TaskAbst<E, T>> boolean kill(T task) {
		return terminate(task, Thread::kill, TaskAbst::kill, true);
	}
	
	public <E, T extends TaskAbst<E, T>> boolean interrupt(T task, boolean terminateChildren) {
		return terminate(task, Thread::interrupt, TaskAbst::interrupt, terminateChildren);
	}
	
	public <E, T extends TaskAbst<E, T>> boolean kill(T task, boolean terminateChildren) {
		return terminate(task, Thread::kill, TaskAbst::kill, terminateChildren);
	}
	
	private <E, T extends TaskAbst<E, T>> boolean terminate(
		T task,
		Consumer<Thread> terminateOperation,
		Consumer<TaskAbst<?,?>> childTerminateOperation,
		boolean terminateChildren
	) {
		if (abort(task)) {
			return task.aborted;
		}
		if (!task.runOnlyOnce) {
			if (tasksInExecution.remove(task) != null) {
				task.aborted = true;
				Thread taskThread = task.executor;
				if (taskThread != null) {
					terminateOperation.accept(taskThread);
					task.executorOrTerminatedExecutorFlag = taskThread;
					taskThread.setPriority(Thread.MIN_PRIORITY);
					if (terminateChildren) {
						terminateChildren(childTerminateOperation, taskThread);
					}
					task.aborted = !task.executed;
				}			
				task.clear();
				synchronized(task) {
					task.notifyAll();
				}
			}
		} else {
			for (TaskAbst<?, ?> queuedTask : tasksInExecution.keySet()) {
				if (task.id.equals(queuedTask.id)) {
					synchronized (queuedTask) {
						if (tasksInExecution.remove(queuedTask) != null) {
							task.aborted = queuedTask.aborted = true;
							Thread queuedTaskThread = queuedTask.executor;
							if (queuedTaskThread != null) {
								terminateOperation.accept(queuedTaskThread);
								task.executorOrTerminatedExecutorFlag = queuedTaskThread;
								queuedTask.executorOrTerminatedExecutorFlag = queuedTaskThread;
								queuedTaskThread.setPriority(Thread.MIN_PRIORITY);
								if (terminateChildren) {
									terminateChildren(childTerminateOperation, queuedTaskThread);
								}
								task.aborted = queuedTask.aborted = !task.executed; 
							}
							queuedTask.clear();
							task.clear();
							queuedTask.notifyAll();
							synchronized(task) {
								task.notifyAll();
							}
							return task.aborted;
						}
					}
				}
			}
		}
		return task.aborted;
	}

	private void terminateChildren(Consumer<TaskAbst<?, ?>> childTerminateOperation, Thread taskThread) {
		Collection<TaskAbst<?,?>> childTasks = taskCreatorThreadsForChildTasks.get(taskThread);
		if (childTasks != null) {
			for (TaskAbst<?,?> childTask : childTasks) {
				childTerminateOperation.accept(childTask);
			}
		}
	}

	public QueuedTaskExecutor waitForTasksEnding(int priority, boolean waitForNewAddedTasks, boolean ignoreDeadLocked) {
		waitForTasksEnding(priority, ignoreDeadLocked);
		if (waitForNewAddedTasks) {
			while (!tasksInExecution.isEmpty() || !tasksQueue.isEmpty()) {
				waitForTasksEnding(priority, ignoreDeadLocked);
			}
		}
		return this;
	}

	public QueuedTaskExecutor waitForTasksEnding(int priority, boolean ignoreDeadLocked) {
		tasksLauncher.setPriority(priority);
		tasksQueue.stream().forEach(executable -> executable.changePriority(priority));
		if (!tasksQueue.isEmpty()) {
			synchronized(executingFinishedWaiterMutex) {
				if (!tasksQueue.isEmpty()) {
					try {
						executingFinishedWaiterMutex.wait();
					} catch (InterruptedException exc) {
						ManagedLoggerRepository.logError(getClass()::getName, exc);
					}
				}
			}
		}
		waitForTasksInExecutionEnding(priority, ignoreDeadLocked);
		tasksLauncher.setPriority(this.defaultPriority);
		return this;
	}

	public QueuedTaskExecutor changePriority(int priority) {
		this.defaultPriority = priority;
		tasksLauncher.setPriority(priority);
		tasksQueue.stream().forEach(executable -> executable.changePriority(priority));
		return this;
	}

	public QueuedTaskExecutor suspend(boolean immediately, boolean ignoreDeadLocked) {
		return suspend0(immediately, java.lang.Thread.currentThread().getPriority(), ignoreDeadLocked);
	}

	public QueuedTaskExecutor suspend(boolean immediately, int priority, boolean ignoreDeadLocked) {
		return suspend0(immediately, priority, ignoreDeadLocked);
	}

	QueuedTaskExecutor suspend0(boolean immediately, int priority, boolean ignoreDeadLocked) {
		tasksLauncher.setPriority(priority);
		if (immediately) {
			synchronized (suspensionCallerMutex) {
				supended = Boolean.TRUE;
				waitForTasksInExecutionEnding(priority, ignoreDeadLocked);
				try {
					synchronized(executableCollectionFillerMutex) {
						if (this.tasksLauncher.getState().equals(Thread.State.WAITING)) {
							executableCollectionFillerMutex.notifyAll();
						}
					}
					suspensionCallerMutex.wait();
				} catch (InterruptedException exc) {
					ManagedLoggerRepository.logError(getClass()::getName, exc);
				}
			}
		} else {
			waitForTasksInExecutionEnding(priority, ignoreDeadLocked);
			Task supendingTask = createSuspendingTask(priority);
			changePriorityToAllTaskBeforeAndWaitThem(supendingTask.addToQueue(), priority, ignoreDeadLocked);
			supendingTask.waitForFinish(ignoreDeadLocked, false);
		}
		tasksLauncher.setPriority(this.defaultPriority);
		return this;
	}

	Task createSuspendingTask(int priority) {
		Task tsk = createTask((ThrowingConsumer<QueuedTaskExecutor.Task, ? extends Throwable>)task ->
			supended = Boolean.TRUE
		).runOnlyOnce(getOperationId("suspend"), () -> 
			supended
		);
		tsk.changePriority(priority);
		return tsk;
	}

	void waitForTasksInExecutionEnding(int priority, boolean ignoreDeadLocked) {
		tasksInExecution.keySet().stream().forEach(task -> {
			Thread taskExecutor = task.executor;
			if (taskExecutor != null) {
				taskExecutor.setPriority(priority);
			}
			task.waitForFinish(ignoreDeadLocked, false);
		});
	}

	<E, T extends TaskAbst<E, T>> void changePriorityToAllTaskBeforeAndWaitThem(T task, int priority, boolean ignoreDeadLocked) {
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
		waitForTasksInExecutionEnding(priority, ignoreDeadLocked);
	}

	public QueuedTaskExecutor resumeFromSuspension() {
		synchronized(resumeCallerMutex) {
			try {
				supended = Boolean.FALSE;
				resumeCallerMutex.notifyAll();
			} catch (Throwable exc) {
				ManagedLoggerRepository.logError(getClass()::getName, exc);
			}
		}
		return this;
	}

	public boolean shutDown(boolean waitForTasksTermination) {
		Collection<TaskAbst<?, ?>> executables = this.tasksQueue;
		if (waitForTasksTermination) {
			suspend(false, true);
		} else {
			suspend(true, true);
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
						ManagedLoggerRepository.logError(getClass()::getName, exc);
					}
				}
			}
		}
		closeResources();
		return true;
	}

	public void logStatus() {
		List<TaskAbst<?, ?>> tasks = new ArrayList<>(tasksQueue);
		tasks.addAll(this.tasksInExecution.keySet());
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
		ManagedLoggerRepository.logInfo(getClass()::getName, log.toString());
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
		tasksQueue = this.tasksInExecution.keySet();
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
			ManagedLoggerRepository.logInfo(getClass()::getName, message);
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
		ManagedLoggerRepository.logInfo(getClass()::getName, "All resources of '{}' have been closed", name);
		name = null;
	}

	public static abstract class TaskAbst<E, T extends TaskAbst<E, T>> {

		String name;
		StackTraceElement[] stackTraceOnCreation;
		List<StackTraceElement> creatorInfos;
		Supplier<Boolean> hasBeenExecutedChecker;
		volatile boolean probablyDeadLocked;
		volatile boolean runOnlyOnce;
		volatile String id;
		volatile int priority;
		volatile Long startTime;
		volatile boolean submitted;
		volatile boolean aborted;
		volatile boolean finished;
		volatile boolean executed;
		boolean exceptionHandled;
		volatile E executable;
		java.lang.Thread creator;
		Thread executor;
		Object executorOrTerminatedExecutorFlag;
		Throwable exc;
		ThrowingBiPredicate<T, Throwable, Throwable> exceptionHandler;
		QueuedTaskExecutor queuedTasksExecutor;

		public TaskAbst(E executable, boolean creationTracking) {
			if (executable == null) {
				throw new NullExecutableException("executable could not be null");
			}
			this.executable = executable;
			if (creationTracking) {
				stackTraceOnCreation = java.lang.Thread.currentThread().getStackTrace();
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
							(clientMethodSTE, currentIteratedSTE) -> !currentIteratedSTE.getClassName().startsWith(QueuedTaskExecutor.class.getName()),
							-1
						)
					);
				} else {
					ManagedLoggerRepository.logWarn(getClass()::getName, "Tasks creation tracking was disabled when {} was created", this);
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

		public T setExceptionHandler(ThrowingBiPredicate<T, Throwable, Throwable> exceptionHandler) {
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
				throw new TaskStateException(this, "is submitted");
			}
			runOnlyOnce = true;
			this.id = id;
			this.hasBeenExecutedChecker = hasBeenExecutedChecker;
			return (T)this;
		}
		
		public boolean isAborted() {
			Thread executor = this.executor;
			return aborted && !executed && (executor == null || !executor.isAlive());
		}
		
		private boolean isExecutorTerminated() {
			Object executorOrTerminatedExecutorFlag = this.executorOrTerminatedExecutorFlag;	
			if (executorOrTerminatedExecutorFlag instanceof Boolean) {
				return(Boolean)executorOrTerminatedExecutorFlag; 
			}					
			if (executorOrTerminatedExecutorFlag != null) {
				boolean isAlive = ((Thread)executorOrTerminatedExecutorFlag).isAlive();
				if (!isAlive) {
					return (Boolean)(this.executorOrTerminatedExecutorFlag = !isAlive);
				}
				return !isAlive;				
			}
			return false;
		}
		
		public boolean isTerminatedThreadNotAlive() {
			return isTerminatedThreadNotAlive(0);
		}
		
		public boolean isTerminatedThreadNotAlive(long waitingTime) {
			if (checkSubmitted() && isExecutorTerminated()) {
				return true;
			}
			if (waitingTime > 0) {
				Thread.waitFor(waitingTime);
			}
			return isExecutorTerminated();
		}
		
		public T waitForTerminatedThreadNotAlive(long pingTime) {
			return waitForTerminatedThreadNotAlive(pingTime, 0);
		}
		
		public T waitForTerminatedThreadNotAlive(long pingTime, long tentative) {
			if (tentative > 0) {
				while(!isTerminatedThreadNotAlive(pingTime) && tentative-- > 0) {}
			} else {
				while(!isTerminatedThreadNotAlive(pingTime)) {}
			}
			return (T)this;
		}
		
		public <EXC extends Throwable> T waitForTerminatedThreadNotAlive(long pingTime, ThrowingConsumer<Integer, EXC> consumer) {
			return waitForTerminatedThreadNotAlive(pingTime, 0, consumer);
		}
		
		public <EXC extends Throwable> T waitForTerminatedThreadNotAlive(long pingTime, long tentative, ThrowingConsumer<Integer, EXC> consumer) {
			Integer tentativeCount = 0;
			if (tentative > 0) {
				while(!isTerminatedThreadNotAlive(pingTime) && tentative-- > 0) {
					Executor.accept(consumer, ++tentativeCount);
				}
			} else {
				while(!isTerminatedThreadNotAlive(pingTime)) {
					Executor.accept(consumer, ++tentativeCount);
				}
			}
			return (T)this;
		}
		
		public boolean wasExecuted() {
			return executed;
		}
		
		public boolean wasExecutedWithException() {
			return isStarted() && exc != null;
		}

		public boolean isSubmitted() {
			return submitted;
		}

		public boolean isProbablyDeadLocked() {
			return probablyDeadLocked;
		}

		synchronized void markAsProbablyDeadLocked() {
			probablyDeadLocked = true;
			executor.setName("PROBABLE DEAD-LOCKED THREAD -> " + executor.getName());
		}
		
		public T waitForStarting() {
			return waitForStarting(false, false, 0);
		}
		
		public T waitForStarting(long timeout) {
			return waitForStarting(false, false, timeout);
		}
		
		public T waitForStarting(boolean ignoreDeadLocked, boolean ignoreSubmittedCheck) {
			return waitForStarting(ignoreDeadLocked, ignoreSubmittedCheck, 0);
		}

		public T waitForStarting(boolean ignoreDeadLocked, boolean ignoreSubmittedCheck, long timeout) {
			if (timeout <= 0) {
				while(waitForStarting0(ignoreDeadLocked, ignoreSubmittedCheck, 0));
				return (T)this;
			}
			long timeAtStartWaiting = System.currentTimeMillis();
			while(waitForStarting0(ignoreDeadLocked, ignoreSubmittedCheck, timeout) &&
				System.currentTimeMillis() - timeAtStartWaiting < timeout
			) {}
			return (T)this;
		}

		private boolean waitForStarting0(boolean ignoreDeadLocked, boolean ignoreSubmittedCheck, long timeout) {
			java.lang.Thread currentThread = java.lang.Thread.currentThread();
			if (currentThread == this.executor) {
				return false;
			}
			if (ignoreSubmittedCheck || checkSubmitted()) {
				if (!isStarted()) {
					synchronized (this) {
						if (!isStarted()) {
							try {
								if (probablyDeadLocked) {
									if (ignoreDeadLocked) {
										return false;
									}
									throw new TaskStateException(this, "could be dead locked");
								}
								if (isAborted()) {
									throw new TaskStateException(this, "is aborted");
								}
								wait(timeout);
								return true;
							} catch (InterruptedException exc) {
								throw new TaskStateException(this, "has been interrupted", exc);
							}
						}
					}
				}
			}
			return false;
		}
		
		private boolean checkSubmitted() {
			if (!isSubmitted()) {
				throw new TaskStateException(this, "is not submitted");
			}
			return true;
		}

		public T waitForFinish() {
			return waitForFinish(false, false, 0);
		}

		public T waitForFinish(long timeout) {
			return waitForFinish(false, false, timeout);
		}
		
		public T waitForFinish(boolean ignoreDeadLocked, boolean ignoreSubmittedCheck) {
			return waitForFinish(ignoreDeadLocked, ignoreSubmittedCheck, 0);
		}
		
		public T waitForFinish(boolean ignoreDeadLocked, boolean ignoreSubmittedCheck, long timeout) {
			if (timeout <= 0) {
				while(waitForFinish0(ignoreDeadLocked, ignoreSubmittedCheck, 0));
				return (T)this;
			}
			long timeAtStartWaiting = System.currentTimeMillis();
			while(waitForFinish0(ignoreDeadLocked, ignoreSubmittedCheck, timeout) &&
				System.currentTimeMillis() - timeAtStartWaiting < timeout
			) {}
			return (T)this;
		}

		private boolean waitForFinish0(boolean ignoreDeadLocked, boolean ignoreSubmittedCheck, long timeout) {
			java.lang.Thread currentThread = java.lang.Thread.currentThread();
			if (currentThread == this.executor) {
				return false;
			}
			if (ignoreSubmittedCheck || checkSubmitted()) {
				if (!hasFinished()) {
					synchronized (this) {
						if (!hasFinished()) {
							try {
								if (probablyDeadLocked) {
									if (ignoreDeadLocked) {
										return false;
									}
									throw new TaskStateException(this, "could be dead locked");
								}
								if (isAborted()) {
									ManagedLoggerRepository.logWarn(getClass()::getName, "Task is aborted:{} ", getInfoAsString());
									return false;
								}
								wait(timeout);
								return true;
							} catch (InterruptedException exc) {
								throw new TaskStateException(this, "has been interrupted", exc);
							}
						}
					}
				}
			}
			return false;
		}

		void execute() {
			try {
				try {
					synchronized (this) {
						if (aborted) {
							notifyAll();
							clear();
							return;
						}
					}
					startTime = System.currentTimeMillis();
					getQueuedTasksExecutor().tasksInExecution.put(this, this);
					synchronized (this) {
						notifyAll();
					}
				} catch (Throwable exc) {
					this.exc = exc;
					startTime = null;
					if (exceptionHandler == null || !(exceptionHandled = exceptionHandler.test((T)this, exc))) {
						throw exc;
					}
					forceAbort();
					return;
				}
				try {
					execute0();
					executed = true;
					++getQueuedTasksExecutor().executedTasksCount;
				} catch (Throwable exc) {
					this.exc = exc;
					if (exceptionHandler == null || !(exceptionHandled = exceptionHandler.test((T)this, exc))) {
						throw exc;
					}
				}
			} catch (Throwable exc) {
				logException(exc);
				forceAbort();
				return;
			} finally {
				markAsFinished();
			}
		}

		private synchronized void forceAbort() {
			aborted = true;
			notifyAll();
			clear();
		}

		public String getInfoAsString() {
			if (this.getCreatorInfos() != null) {
				Thread executor = this.executor;
				return Strings.compile("\n\tTask hash code: {}\n\tTask status: {} {} \n\tcreated at: {}",
					this.hashCode(),
					Strings.compile("\n\t\tpriority: {}\n\t\tstarted: {}\n\t\taborted: {}\n\t\tfinished: {}", priority, isStarted(), isAborted(), hasFinished()),
					executor != null ? "\n\t" + executor + Strings.from(executor.getStackTrace(),2) : "",
					Strings.from(this.getCreatorInfos(), 2)
				);
			}
			return Strings.compile("\n\tTask hash code: {}\n\tTask status: {} {}",
				this.hashCode(),
				Strings.compile("\n\t\tpriority: {}\n\t\tstarted: {}\n\t\taborted: {}\n\t\tfinished: {}", priority, isStarted(), isAborted(), hasFinished()),
				executor != null ? "\n\t" + executor + Strings.from(executor.getStackTrace(),2) : ""
			);
		}
		
		public void logInfo() {
			ManagedLoggerRepository.logInfo(getClass()::getName, getInfoAsString());
		}
		
		public void logException() {
			logException(exc);
		}
		
		private void logException(Throwable exc) {
			java.lang.Thread executor = this.executor;
			if (executor != null) {
				ManagedLoggerRepository.logError(getClass()::getName, Strings.compile(
					"Exception occurred while executing {} ({}): \n\t\t{}: {}{}",
					this,
					this.executor,
					exc.toString(),
					Strings.from(exc.getStackTrace(), 2),
					this.getCreatorInfos() != null ?
						"\n\tthat was created at:" + Strings.from(this.getCreatorInfos(), 2)
						: ""
				));
				return;
			}			
			ManagedLoggerRepository.logError(getClass()::getName, Strings.compile(
				"Exception occurred while executing {}: \n\t\t{}: {}{}",
				this,
				exc.toString(),
				Strings.from(exc.getStackTrace(), 2),
				this.getCreatorInfos() != null ?
					"\n\tthat was created at:" + Strings.from(this.getCreatorInfos(), 2)
					: ""
			));
		}

		void clear() {
			getQueuedTasksExecutor().tasksInExecution.remove(this);
			if (runOnlyOnce) {
				runOnlyOnceTasks.remove(id);
			}
			executable = null;
			java.lang.Thread creator = this.creator;
			if (creator != null) {
				Synchronizer.execute(Objects.getId(creator), () -> {
					Collection<TaskAbst<?, ?>> creatorChildTasks = taskCreatorThreadsForChildTasks.get(creator);
					if (creatorChildTasks != null) {
						creatorChildTasks.remove(this);
						if (creatorChildTasks.isEmpty()) {
							taskCreatorThreadsForChildTasks.remove(creator);
						}
					}
				});
			}
			this.creator = null;
			executor = null;
			this.queuedTasksExecutor = null;
		}

		void markAsFinished() {
			try {
				finished = true;
			} finally {
				synchronized(this) {
					notifyAll();
				}
				clear();
			}
		}

		abstract void execute0() throws Throwable;

		T setExecutor(Thread thread) {
			executor = thread.setExecutable(thr -> this.execute());
			executor.setPriority(this.priority);
			QueuedTaskExecutor queuedTasksExecutor = getQueuedTasksExecutor();
			if (name != null) {
				executor.setName(queuedTasksExecutor.name + " - " + name);
			} else {
				executor.setIndexedName(queuedTasksExecutor.name + " executor");
			}
			return (T)this;
		}

		public boolean changePriority(int priority) {
			this.priority = priority;
			if (executor != null) {
				executor.setPriority(this.priority);
				return true;
			}
			return false;
		}

		public boolean setPriorityToCurrentThreadPriority() {
			return changePriority(java.lang.Thread.currentThread().getPriority());
		}

		public int getPriority() {
			return priority;
		}

		public Throwable getException() {
			return exc;
		}

		public final T submit() {
			if (isAborted()) {
				throw new TaskStateException(this, "is aborted");
			}
			if (!submitted) {
				synchronized(this) {
					if (!submitted) {
						submitted = true;
					} else {
						throw new TaskStateException(this, "is already submitted");
					}
				}
			} else {
				throw new TaskStateException(this, "is already submitted");
			}
			return addToQueue();
		}

		T addToQueue() {
			return getQueuedTasksExecutor().addToQueue((T)this, false);
		}

		public T abortOrWaitForFinish() {
			return abortOrWaitForFinish(false, false);
		}

		public T abortOrWaitForFinish(boolean ignoreDeadLocked, boolean ignoreSubmittedCheck) {
			if (!abort().isAborted()) {
				waitForFinish(ignoreDeadLocked, ignoreSubmittedCheck);
			}
			return (T)this;
		}

		public T abort() {
			getQueuedTasksExecutor().abort((T)this);
			return (T)this;
		}
		
		public T kill() {
			return kill(true);
		}
		
		public T interrupt() {
			return interrupt(true);
		}
		
		public T kill(boolean terminateChildren) {
			getQueuedTasksExecutor().kill((T)this, terminateChildren);
			return (T)this;
		}
		
		public T interrupt(boolean terminateChildren) {
			getQueuedTasksExecutor().interrupt((T)this, terminateChildren);
			return (T)this;
		}

		abstract QueuedTaskExecutor getQueuedTasksExecutor();

		abstract QueuedTaskExecutor retrieveQueuedTasksExecutorOf(java.lang.Thread thread);
	}

	public static abstract class Task extends TaskAbst<ThrowingConsumer<QueuedTaskExecutor.Task, ? extends Throwable>, Task> {

		Task(ThrowingConsumer<Task, ? extends Throwable> executable, boolean creationTracking) {
			super(executable, creationTracking);
		}

		@Override
		void execute0() throws Throwable {
			this.executable.accept(this);
		}
		
		public void join() {
			join(false, false, 0);
		}
		
		public void join(long timeout) {
			join(false, false, timeout);
		}

		public void join(boolean ignoreDeadLocked, boolean ignoreSubmittedCheck, long timeout) {
			waitForFinish(ignoreDeadLocked, ignoreSubmittedCheck, timeout);
			Throwable exception = getException();
			if (exception != null && !exceptionHandled) {
				org.burningwave.core.Throwables.throwException(exception);
			}
			if (!wasExecuted()) {
				throw new TaskStateException(this, "is not completed");
			}
		}

	}

	public static abstract class ProducerTask<T> extends TaskAbst<ThrowingFunction<QueuedTaskExecutor.ProducerTask<T>, T, ? extends Throwable>, ProducerTask<T>> {
		private T result;

		ProducerTask(ThrowingFunction<QueuedTaskExecutor.ProducerTask<T>, T, ? extends Throwable> executable, boolean creationTracking) {
			super(executable, creationTracking);
		}

		@Override
		void execute0() throws Throwable {
			result = executable.apply(this);
		}
		
		public T join() {
			return join(false, false, 0);
		}
		
		public T join(long timeout) {
			return join(false, false, timeout);
		}

		public T join(boolean ignoreDeadLocked, boolean ignoreSubmittedCheck, long timeout) {
			waitForFinish(ignoreDeadLocked, ignoreSubmittedCheck, timeout);
			Throwable exception = getException();
			if (exception != null && !exceptionHandled) {
				return org.burningwave.core.Throwables.throwException(exception);
			}
			if (!wasExecuted()) {
				throw new TaskStateException(this, "is not completed");
			}
			return result;
		}

		public T get() {
			return result;
		}

	}

	public static class Group implements Identifiable {
		String name;
		Map<Integer, QueuedTaskExecutor> queuedTasksExecutors;
		TasksMonitorer allTasksMonitorer;
		Consumer<Group> initializator;
		Integer[] definedPriorites;

		Group(Map<String, Object> configuration) {
			//Implemented deferred initialization (since 10.0.0, the previous version is 9.5.2)
			initializator = queuedTasksExecutorGroup -> {
				String name = IterableObjectHelper.resolveStringValue(
					ResolveConfig.forNamedKey("name")
					.on(configuration)
				);
				Thread.Supplier mainThreadSupplier = (Thread.Supplier)configuration.get("thread-supplier");
				Boolean isDaemon = Objects.toBoolean(
					IterableObjectHelper.resolveValue(
						ResolveConfig.forNamedKey("daemon")
						.on(configuration)
					)
				);
				queuedTasksExecutorGroup.name = name;
				Map<Integer, QueuedTaskExecutor> queuedTasksExecutors = new HashMap<>();
				for (int i = 0;  i < java.lang.Thread.MAX_PRIORITY; i++) {
					Object priorityAsObject = IterableObjectHelper.resolveValue(
						ResolveConfig.forNamedKey("queued-task-executor[" + i + "].priority")
						.on(configuration)
					);
					if (priorityAsObject != null) {
						int priority = Objects.toInt(priorityAsObject);
						if (priority < java.lang.Thread.MIN_PRIORITY || priority > java.lang.Thread.MAX_PRIORITY) {
							throw new IllegalArgumentException(
								Strings.compile(
									"Value of '{}' is not correct: it must be between {} and {}",
									"queued-task-executor[" + i + "].priority",
									java.lang.Thread.MIN_PRIORITY, java.lang.Thread.MAX_PRIORITY
								)
							);
						}
						String queuedTasksExecutorName =
							IterableObjectHelper.resolveStringValue(
								ResolveConfig.forNamedKey("queued-task-executor[" + i + "].name")
								.on(configuration)
							);
						Thread.Supplier queuedTasksExecutorThreadSupplier =
							IterableObjectHelper.resolveValue(
								ResolveConfig.forNamedKey("queued-task-executor[" + i + "].thread-supplier")
								.on(configuration)
						);
						if (queuedTasksExecutorThreadSupplier == null) {
							queuedTasksExecutorThreadSupplier = mainThreadSupplier;
						}
						Object isQueuedTasksExecutorDaemonAsObject =
							IterableObjectHelper.resolveValue(
								ResolveConfig.forNamedKey("queued-task-executor[" + i + "].daemon")
								.on(configuration)
							);
						Boolean isQueuedTasksExecutorDaemon = isDaemon;
						if (isQueuedTasksExecutorDaemonAsObject != null) {
							isQueuedTasksExecutorDaemon = Objects.toBoolean(
								isQueuedTasksExecutorDaemonAsObject
							);
						}
						queuedTasksExecutors.put(
							priority,
							createQueuedTasksExecutor(
								name + " - " + queuedTasksExecutorName,
								queuedTasksExecutorThreadSupplier,
								priority,
								isQueuedTasksExecutorDaemon
							)
						);
					}
				}
				definedPriorites = queuedTasksExecutors.keySet().toArray(
					definedPriorites = new Integer[queuedTasksExecutors.size()]
				);
				this.queuedTasksExecutors = queuedTasksExecutors;
			};
		}

		public Group setTasksCreationTrackingFlag(boolean flag) {
			if (initializator == null) {
				setTasksCreationTrackingFlag(this, flag);
			} else {
				initializator = initializator.andThen(queuedTasksExecutorGroup -> {
					setTasksCreationTrackingFlag(queuedTasksExecutorGroup, flag);
				});
			}
			return this;
		}

		private void setTasksCreationTrackingFlag(Group queuedTasksExecutorGroup, boolean flag) {
			for (Entry<Integer, QueuedTaskExecutor> queuedTasksExecutorBox : queuedTasksExecutorGroup.queuedTasksExecutors.entrySet()) {
				queuedTasksExecutorBox.getValue().setTasksCreationTrackingFlag(flag);
			}
		}

		public Group startAllTasksMonitoring(TasksMonitorer.Config config) {
			if (initializator == null) {
				startAllTasksMonitoring(this, config);
			} else {
				Synchronizer.execute(getOperationId("initialization"), () -> {
					if (initializator != null) {
						initializator = initializator.andThen(queuedTasksExecutorGroup -> {
							startAllTasksMonitoring(this, config);
						});
					} else {
						startAllTasksMonitoring(this, config);
					}
				});
			}
			return this;
		}

		synchronized void startAllTasksMonitoring(Group queuedTasksExecutorGroup, TasksMonitorer.Config config) {
			TasksMonitorer allTasksMonitorer = queuedTasksExecutorGroup.allTasksMonitorer;
			if (allTasksMonitorer != null) {
				allTasksMonitorer.close();
			}
			queuedTasksExecutorGroup.allTasksMonitorer = new TasksMonitorer(queuedTasksExecutorGroup, config).start();
		}

		public static Group create(
			String keyPrefix,
			Map<String, Object> configuration
		) {
			configuration = IterableObjectHelper.resolveValues(
				ResolveConfig.forAllKeysThat((Predicate<String>)(key) ->
					key.startsWith(keyPrefix + "."))
				.on(configuration)
			);
			Map<String, Object> finalConfiguration = new HashMap<>();
			boolean undestroyableFromExternal = Objects.toBoolean(
				IterableObjectHelper.resolveValue(
					ResolveConfig.forNamedKey(keyPrefix + ".undestroyable-from-external")
					.on(configuration)
				)
			);
			for (Entry<String, Object> entry : configuration.entrySet()) {
				Object value = entry.getValue();
				if (value instanceof Collection && ((Collection<Object>)value).size() == 1) {
					value = ((Collection<Object>)value).iterator().next();
				}
				finalConfiguration.put(entry.getKey().replace(keyPrefix +".", ""), value);
			}
			if (!undestroyableFromExternal) {
				return new Group(
					finalConfiguration
				);
			} else {
				return new Group(
					finalConfiguration
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
		
		public <T> ProducerTask<T> createProducerTask(ThrowingFunction<ProducerTask<T>, T, ? extends Throwable> executable) {
			return createProducerTask(executable, java.lang.Thread.currentThread().getPriority());
		}
		
		public <T> ProducerTask<T> createProducerTask(ThrowingFunction<ProducerTask<T>, T, ? extends Throwable> executable, int priority) {
			return getByPriority(priority).createProducerTask(executable);
		}
		
		public <T> ProducerTask<T> createProducerTask(ThrowingSupplier<T, ? extends Throwable> executable) {
			return createProducerTask(executable, java.lang.Thread.currentThread().getPriority());
		}
		
		public <T> ProducerTask<T> createProducerTask(ThrowingSupplier<T, ? extends Throwable> executable, int priority) {
			return getByPriority(priority).createProducerTask(executable);
		}

		QueuedTaskExecutor getByPriority(int priority) {
			QueuedTaskExecutor queuedTasksExecutor = null;
			//Implemented deferred initialization (since 10.0.0, the previous version is 9.5.2)
			try {
				queuedTasksExecutor = queuedTasksExecutors.get(priority);
			} catch (NullPointerException exc) {
				if (queuedTasksExecutors == null) {
					if (initializator != null) {
						Synchronizer.execute(getOperationId("initialization"), () -> {
							if (initializator != null) {
								initializator.accept(this);
								initializator = null;
							}
						});
					}
				}
				queuedTasksExecutor = queuedTasksExecutors.get(priority);
			}
			if (queuedTasksExecutor == null) {
				queuedTasksExecutor = queuedTasksExecutors.get(checkAndCorrectPriority(priority));
			}
			return queuedTasksExecutor;
		}

		int checkAndCorrectPriority(int priority) {
			if (queuedTasksExecutors.get(priority) != null) {
				return priority;
			}
			if (priority < java.lang.Thread.MIN_PRIORITY || priority > java.lang.Thread.MAX_PRIORITY) {
				throw new IllegalArgumentException(
					Strings.compile(
						"Priority value must be between {} and {}",
						java.lang.Thread.MIN_PRIORITY, java.lang.Thread.MAX_PRIORITY
					)
				);
			}
			Integer currentMaxPriorityHandled = definedPriorites[definedPriorites.length -1];
			if (priority > currentMaxPriorityHandled) {
				return currentMaxPriorityHandled;
			}
			for (Integer definedPriorite : definedPriorites) {
				if (priority < definedPriorite) {
					return definedPriorite;
				}
			}
			return definedPriorites[definedPriorites.length -1];
		}

		public Task createTask(ThrowingConsumer<QueuedTaskExecutor.Task, ? extends Throwable> executable) {
			return createTask(executable, java.lang.Thread.currentThread().getPriority());
		}
		
		public Task createTask(ThrowingConsumer<QueuedTaskExecutor.Task, ? extends Throwable> executable, int priority) {
			return getByPriority(priority).createTask(executable);
		}
		
		public Task createTask(ThrowingRunnable<? extends Throwable> executable) {
			return createTask(executable, java.lang.Thread.currentThread().getPriority());
		}
		
		public Task createTask(ThrowingRunnable<? extends Throwable> executable, int priority) {
			return getByPriority(priority).createTask(executable);
		}

		QueuedTaskExecutor createQueuedTasksExecutor(String executorName, Thread.Supplier threadSupplier, int priority, boolean isDaemon) {
			return new QueuedTaskExecutor(executorName, threadSupplier, priority, isDaemon) {

				@Override
				<T> Function<ThrowingFunction<QueuedTaskExecutor.ProducerTask<T>, T, ? extends Throwable>, QueuedTaskExecutor.ProducerTask<T>> getProducerTaskSupplier() {
					return executable -> new QueuedTaskExecutor.ProducerTask<T>(executable, taskCreationTrackingEnabled) {

						@Override
						QueuedTaskExecutor getQueuedTasksExecutor() {
							return this.queuedTasksExecutor != null?
								this.queuedTasksExecutor : Group.this.getByPriority(this.priority);
						}

						@Override
						public boolean changePriority(int priority) {
							return Group.this.changePriority(this, priority);
						}

						@Override
						QueuedTaskExecutor retrieveQueuedTasksExecutorOf(java.lang.Thread thread) {
							return Group.this.getByPriority(thread.getPriority());
						}

					};
				}

				@Override
				<T> Function<ThrowingConsumer<QueuedTaskExecutor.Task, ? extends Throwable> , QueuedTaskExecutor.Task> getTaskSupplier() {
					return executable -> new QueuedTaskExecutor.Task(executable, taskCreationTrackingEnabled) {

						@Override
						QueuedTaskExecutor getQueuedTasksExecutor() {
							return this.queuedTasksExecutor != null?
								this.queuedTasksExecutor : Group.this.getByPriority(this.priority);
						}

						@Override
						public boolean changePriority(int priority) {
							return Group.this.changePriority(this, priority);
						}

						@Override
						QueuedTaskExecutor retrieveQueuedTasksExecutorOf(java.lang.Thread thread) {
							return Group.this.getByPriority(thread.getPriority());
						}
					};
				}

				@Override
				public QueuedTaskExecutor waitForTasksEnding(int priority, boolean ignoreDeadLocked) {
					if (priority == defaultPriority) {
						if (!tasksQueue.isEmpty()) {
							synchronized(executingFinishedWaiterMutex) {
								if (!tasksQueue.isEmpty()) {
									try {
										executingFinishedWaiterMutex.wait();
									} catch (InterruptedException exc) {
										ManagedLoggerRepository.logError(getClass()::getName, exc);
									}
								}
							}
						}
						tasksInExecution.keySet().stream().forEach(task -> {
							//logInfo("{}", queueConsumer);
							//task.logInfo();
							task.waitForFinish(ignoreDeadLocked, false);
						});
					} else {
						tasksQueue.stream().forEach(executable ->
							executable.changePriority(priority)
						);
						waitForTasksInExecutionEnding(priority, ignoreDeadLocked);
					}
					return this;
				}

				@Override
				public <E, T extends TaskAbst<E, T>> QueuedTaskExecutor waitFor(T task, int priority, boolean ignoreDeadLocked) {
					task.waitForFinish(ignoreDeadLocked, false);
					return this;
				}

				@Override
				Task createSuspendingTask(int priority) {
					return createTask((ThrowingConsumer<QueuedTaskExecutor.Task, ? extends Throwable>)task ->
						supended = Boolean.TRUE
					);
				}
				
			};
		}

		<E, T extends TaskAbst<E, T>> boolean changePriority(T task, int priority) {
			int oldPriority = task.priority;
			int newPriority = checkAndCorrectPriority(priority);
			if (oldPriority != priority) {
				synchronized (task) {
					if (getByPriority(oldPriority).tasksQueue.remove(task)) {
						task.priority = newPriority;
						QueuedTaskExecutor queuedTasksExecutor = getByPriority(newPriority);
						task.queuedTasksExecutor = null;
						task.executor = null;
						queuedTasksExecutor.addToQueue(task, true);
						return true;
					}
				}
			}
			return false;
		}

		public boolean isClosed() {
			return queuedTasksExecutors == null;
		}

		public Group waitForTasksEnding() {
			return waitForTasksEnding(java.lang.Thread.currentThread().getPriority(), false, false);
		}

		public Group waitForTasksEnding(boolean ignoreDeadLocked) {
			return waitForTasksEnding(java.lang.Thread.currentThread().getPriority(), false, ignoreDeadLocked);
		}

		public Group waitForTasksEnding(boolean waitForNewAddedTasks, boolean ignoreDeadLocked) {
			return waitForTasksEnding(java.lang.Thread.currentThread().getPriority(), waitForNewAddedTasks, ignoreDeadLocked);
		}

		public Group waitForTasksEnding(int priority, boolean waitForNewAddedTasks, boolean ignoreDeadLocked) {
			//Implemented deferred initialization (since 10.0.0, the previous version is 9.5.2)
			Synchronizer.execute(getOperationId("initialization"), () -> {
				if (initializator != null) {
					return;
				}
				QueuedTaskExecutor lastToBeWaitedFor = getByPriority(priority);
				for (Entry<Integer, QueuedTaskExecutor> queuedTasksExecutorBox : queuedTasksExecutors.entrySet()) {
					QueuedTaskExecutor queuedTasksExecutor = queuedTasksExecutorBox.getValue();
					if (queuedTasksExecutor != lastToBeWaitedFor) {
						queuedTasksExecutor.waitForTasksEnding(priority, waitForNewAddedTasks, ignoreDeadLocked);
					}
				}
				lastToBeWaitedFor.waitForTasksEnding(priority, waitForNewAddedTasks, ignoreDeadLocked);
				for (Entry<Integer, QueuedTaskExecutor> queuedTasksExecutorBox : queuedTasksExecutors.entrySet()) {
					QueuedTaskExecutor queuedTasksExecutor = queuedTasksExecutorBox.getValue();
					if (waitForNewAddedTasks && (!queuedTasksExecutor.tasksQueue.isEmpty() || !queuedTasksExecutor.tasksInExecution.isEmpty())) {
						waitForTasksEnding(priority, waitForNewAddedTasks, ignoreDeadLocked);
						break;
					}
				}
			});
			return this;
		}

		public <E, T extends TaskAbst<E, T>> Group waitFor(T task, boolean ignoreDeadLocked) {
			return waitFor(task, java.lang.Thread.currentThread().getPriority(), ignoreDeadLocked);
		}

		public <E, T extends TaskAbst<E, T>> Group waitFor(T task, int priority, boolean ignoreDeadLocked) {
			if (task.getPriority() != priority) {
				task.changePriority(priority);
			}
			task.waitForFinish(ignoreDeadLocked, false);
			return this;
		}

		public Group logInfo() {
			String loggableMessage = getInfoAsString();
			loggableMessage = getInfoAsString();
			if (!loggableMessage.isEmpty()) {
				ManagedLoggerRepository.logInfo(getClass()::getName, loggableMessage);
			}
			return this;
		}

		public String getInfoAsString() {
			StringBuffer loggableMessage = new StringBuffer("");
			for (Entry<Integer, QueuedTaskExecutor> queuedTasksExecutorBox : queuedTasksExecutors.entrySet()) {
				loggableMessage.append(queuedTasksExecutorBox.getValue().getInfoAsString());
			}
			return loggableMessage.toString();
		}

		public <E, T extends TaskAbst<E, T>> boolean abort(T task) {
			for (Entry<Integer, QueuedTaskExecutor> queuedTasksExecutorBox : queuedTasksExecutors.entrySet()) {
				if (queuedTasksExecutorBox.getValue().abort(task)) {
					return true;
				}
			}
			return false;
		}
		
		public <E, T extends TaskAbst<E, T>> boolean kill(T task) {
			for (Entry<Integer, QueuedTaskExecutor> queuedTasksExecutorBox : queuedTasksExecutors.entrySet()) {
				if (queuedTasksExecutorBox.getValue().kill(task)) {
					return true;
				}
			}
			return false;
		}

		public Collection<TaskAbst<?, ?>> getAllTasksInExecution() {
			Collection<TaskAbst<?, ?>> tasksInExecution = new HashSet<>();
			for (Entry<Integer, QueuedTaskExecutor> queuedTasksExecutorBox : queuedTasksExecutors.entrySet()) {
				tasksInExecution.addAll(
					queuedTasksExecutorBox.getValue().tasksInExecution.keySet()
				);
			}
			return tasksInExecution;
		}

		public Group startAllTasksMonitoring() {
			TasksMonitorer allTasksMonitorer = this.allTasksMonitorer;
			if (allTasksMonitorer != null) {
				allTasksMonitorer.start();
				return this;
			}
			return org.burningwave.core.Throwables.throwException("All tasks monitorer has not been configured");
		}

		public Group stopAllTasksMonitoring() {
			TasksMonitorer allTasksMonitorer = this.allTasksMonitorer;
			if (allTasksMonitorer != null) {
				allTasksMonitorer.stop();
			}
			return this;
		}

		public boolean shutDown(boolean waitForTasksTermination) {
			//Implemented deferred initialization (since 10.0.0, the previous version is 9.5.2)
			Synchronizer.execute(getOperationId("initialization"), () -> {
				if (initializator != null) {
					initializator = null;
					return;
				}
				QueuedTaskExecutor lastToBeWaitedFor = getByPriority(java.lang.Thread.currentThread().getPriority());
				for (Entry<Integer, QueuedTaskExecutor> queuedTasksExecutorBox : queuedTasksExecutors.entrySet()) {
					QueuedTaskExecutor queuedTasksExecutor = queuedTasksExecutorBox.getValue();
					if (queuedTasksExecutor != lastToBeWaitedFor) {
						queuedTasksExecutor.shutDown(waitForTasksTermination);
					}
				}
				lastToBeWaitedFor.shutDown(waitForTasksTermination);
				allTasksMonitorer.close(waitForTasksTermination);
				allTasksMonitorer = null;
				queuedTasksExecutors.clear();
				queuedTasksExecutors = null;
			});
			return true;
		}
	}

}
