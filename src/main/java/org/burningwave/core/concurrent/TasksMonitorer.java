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
 * Copyright (c) 2019-2022 Roberto Gentili
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

import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository;
import static org.burningwave.core.assembler.StaticComponentContainer.Synchronizer;
import static org.burningwave.core.assembler.StaticComponentContainer.ThreadHolder;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Consumer;

import org.burningwave.core.Closeable;
import org.burningwave.core.concurrent.QueuedTaskExecutor.TaskAbst;

@SuppressWarnings("deprecation")
public class TasksMonitorer implements Closeable {
	Map<QueuedTaskExecutor.TaskAbst<?, ?>, StackTraceElement[]> waitingTasksAndLastStackTrace;
	QueuedTaskExecutor.Group queuedTasksExecutorGroup;
	TasksMonitorer.Config config;

	TasksMonitorer(QueuedTaskExecutor.Group queuedTasksExecutorGroup, TasksMonitorer.Config config) {
		waitingTasksAndLastStackTrace = new HashMap<>();
		this.queuedTasksExecutorGroup = queuedTasksExecutorGroup;
		this.config = config;
	}

	void checkAndHandleProbableDeadLockedTasks(
		long minimumElapsedTimeToConsiderATaskAsProbablyDeadLocked,
		boolean markAsProbableDeadLocked,
		Consumer<QueuedTaskExecutor.TaskAbst<?, ?>> terminateProbableDeadLockedTasksFunction
	) {
		Iterator<Entry<QueuedTaskExecutor.TaskAbst<?, ?>, StackTraceElement[]>> tasksAndStackTracesIterator = waitingTasksAndLastStackTrace.entrySet().iterator();
		while (tasksAndStackTracesIterator.hasNext()) {
			QueuedTaskExecutor.TaskAbst<?, ?> task = tasksAndStackTracesIterator.next().getKey();
			if(task.hasFinished()) {
				tasksAndStackTracesIterator.remove();
			}
		}
		long currentTime = System.currentTimeMillis();
		for (QueuedTaskExecutor.TaskAbst<?, ?> task : queuedTasksExecutorGroup.getAllTasksInExecution()) {
			if (currentTime - task.startTime > minimumElapsedTimeToConsiderATaskAsProbablyDeadLocked) {
				java.lang.Thread taskThread = task.executor;
				Thread.State threadState = Optional.ofNullable(taskThread).map(java.lang.Thread::getState).orElseGet(() -> null);
				if (taskThread != null &&
				(Thread.State.BLOCKED.equals(threadState) ||
				Thread.State.WAITING.equals(threadState) ||
				Thread.State.TIMED_WAITING.equals(threadState))) {
					StackTraceElement[] previousRegisteredStackTrace = waitingTasksAndLastStackTrace.get(task);
					StackTraceElement[] currentStackTrace = taskThread.getStackTrace();
					if (previousRegisteredStackTrace != null) {
						if (areStrackTracesEquals(previousRegisteredStackTrace, currentStackTrace)) {
							if (!task.hasFinished()) {
								ManagedLoggerRepository.logWarn(
									getClass()::getName,
									"Possible deadlock detected for task:{}",
									task.getInfoAsString()
								);
								if (markAsProbableDeadLocked) {
									task.markAsProbablyDeadLocked();
								}
								if (terminateProbableDeadLockedTasksFunction != null && !task.hasFinished()) {
									ManagedLoggerRepository.logWarn(
										getClass()::getName,
										"Trying to terminate task {}",
										task.hashCode()
									);
									terminateProbableDeadLockedTasksFunction.accept(task);
								}
								if (markAsProbableDeadLocked) {
									task.clear();
									synchronized(task) {
										task.notifyAll();
									}
								}
								ManagedLoggerRepository.logWarn(
									getClass()::getName,
									Synchronizer.getAllThreadsInfoAsString(true)
								);
								Synchronizer.logAllThreadsState(true);
							}
						} else {
							waitingTasksAndLastStackTrace.put(task, currentStackTrace);
						}
					} else {
						waitingTasksAndLastStackTrace.put(task, currentStackTrace);
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

	private String getName() {
		return Optional.ofNullable(queuedTasksExecutorGroup.name).map(nm -> nm + " - ").orElseGet(() -> "") + "All tasks monitorer";
	}

	public TasksMonitorer start() {
		ManagedLoggerRepository.logInfo(
			() -> this.getClass().getName(),
			"Starting {}", getName()
		);
		ThreadHolder.startLooping(getName(), true, java.lang.Thread.MIN_PRIORITY, thread -> {
			Thread.waitFor(config.getInterval());
			if (thread.isLooping()) {
				if (config.isAllTasksLoggerEnabled()) {
					queuedTasksExecutorGroup.logInfo();
				}
				try {
					checkAndHandleProbableDeadLockedTasks(
						config.getMinimumElapsedTimeToConsiderATaskAsProbablyDeadLocked(),
						config.isMarkAsProablyDeadLockedEnabled(),
						config.getTerminateProablyDeadLockedTasksFunction()
					);
				} catch (Throwable exc) {
					ManagedLoggerRepository.logError(
						() -> this.getClass().getName(),
						"Exception occurred while checking dead locked tasks", exc
					);
				}
			}
		});
		return this;
	}

	public void stop() {
		stop(false);
	}

	public void stop(boolean waitThreadToFinish) {
		ManagedLoggerRepository.logInfo(
			() -> this.getClass().getName(),
			"Starting {}", getName()
		);
		ThreadHolder.stop(getName());
	}

	@Override
	public void close() {
		close(false);
	}

	public void close(boolean waitForTasksTermination) {
		stop(waitForTasksTermination);
		this.queuedTasksExecutorGroup = null;
		this.waitingTasksAndLastStackTrace.clear();
		this.waitingTasksAndLastStackTrace = null;
	}

	public static class Config {
		private long interval;
		private long minimumElapsedTimeToConsiderATaskAsProbablyDeadLocked;
		private boolean markAsProbableDeadLocked;
		private Consumer<QueuedTaskExecutor.TaskAbst<?, ?>> terminateProbableDeadLockedTasksFunction;
		private boolean allTasksLoggerEnabled;

		public long getInterval() {
			return interval;
		}

		public TasksMonitorer.Config setInterval(long interval) {
			this.interval = interval;
			return this;
		}

		public long getMinimumElapsedTimeToConsiderATaskAsProbablyDeadLocked() {
			return minimumElapsedTimeToConsiderATaskAsProbablyDeadLocked;
		}

		public TasksMonitorer.Config setMinimumElapsedTimeToConsiderATaskAsProbablyDeadLocked(
				long minimumElapsedTimeToConsiderATaskAsProbablyDeadLocked) {
			this.minimumElapsedTimeToConsiderATaskAsProbablyDeadLocked = minimumElapsedTimeToConsiderATaskAsProbablyDeadLocked;
			return this;
		}

		public boolean isMarkAsProablyDeadLockedEnabled() {
			return markAsProbableDeadLocked;
		}

		public TasksMonitorer.Config setMarkAsProbableDeadLocked(String policy) {
			this.markAsProbableDeadLocked = policy.toLowerCase().contains("mark as probable dead locked");
			return this;
		}

		public boolean isTerminateProablyDeadLockedTasksEnabled() {
			return terminateProbableDeadLockedTasksFunction != null;
		}

		public Consumer<QueuedTaskExecutor.TaskAbst<?, ?>> getTerminateProablyDeadLockedTasksFunction() {
			return terminateProbableDeadLockedTasksFunction;
		}

		public TasksMonitorer.Config setTerminateProbableDeadLockedTasksOperation(String policy) {
			this.terminateProbableDeadLockedTasksFunction = policy.toLowerCase().contains("interrupt") ?
				TaskAbst::interrupt :
					policy.toLowerCase().contains("kill") ?
						TaskAbst::kill :
						null;
			return this;
		}

		public boolean isAllTasksLoggerEnabled() {
			return allTasksLoggerEnabled;
		}

		public TasksMonitorer.Config setAllTasksLoggerEnabled(boolean allTasksLoggerEnabled) {
			this.allTasksLoggerEnabled = allTasksLoggerEnabled;
			return this;
		}
	}
}