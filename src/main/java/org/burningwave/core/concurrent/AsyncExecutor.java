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

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CopyOnWriteArrayList;

import org.burningwave.core.Component;

public class AsyncExecutor implements Component {
	private Collection<Map.Entry<Runnable, Integer>> executables;
	private Entry<Runnable, Integer> currentExecutable;
	private Boolean supended;
	private Mutex.Manager mutexManager;
	Thread executor;
	private int defaultPriority;
	private long executedExecutableCount;
	private boolean isDaemon;
	private String name;
	private Boolean terminated;
	private Runnable initializer;
	
	private AsyncExecutor(String name, int defaultPriority, boolean isDaemon) {
		mutexManager = Mutex.Manager.create(this);
		executables = new CopyOnWriteArrayList<>();
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
	
	void init0() {		
		supended = Boolean.FALSE;
		terminated = Boolean.FALSE;
		executedExecutableCount = 0;
		executor = new Thread(() -> {
			while (!terminated) {
				if (!executables.isEmpty()) {
					for (Entry<Runnable, Integer> executable : executables) {
						synchronized(mutexManager.getMutex("resumeCaller")) {
							try {
								if (supended) {
									mutexManager.getMutex("resumeCaller").wait();
									break;
								}
							} catch (InterruptedException exc) {
								logWarn("Exception occurred", exc);
							}
						}
						this.currentExecutable = executable;
						Runnable runnable = currentExecutable.getKey();
						try {
							int currentExecutablePriority = currentExecutable.getValue();
							if (executor.getPriority() != currentExecutablePriority) {
								executor.setPriority(currentExecutablePriority);
							}
							executables.remove(executable);
							runnable.run();							
							++executedExecutableCount;
							if (executedExecutableCount % 10000 == 0) {
								logInfo("Executed {} tasks", executedExecutableCount);
							}
							synchronized(mutexManager.getMutex("suspensionCaller")) {
								currentExecutable = null;
								mutexManager.getMutex("suspensionCaller").notifyAll();
							}
						} catch (Throwable exc) {
							logError("Exception occurred while executing " + runnable.toString(), exc);
						}
						if (terminated) {
							break;
						}
					}
				} else {
					synchronized(mutexManager.getMutex("executingFinishedWaiter")) {
						mutexManager.getMutex("executingFinishedWaiter").notifyAll();
					}
					synchronized(mutexManager.getMutex("executableCollectionFiller")) {
						try {
							mutexManager.getMutex("executableCollectionFiller").wait();
						} catch (InterruptedException exc) {
							logWarn("Exception occurred", exc);
						}
					}
				}
			}
		}, name);
		executor.setPriority(this.defaultPriority);
		executor.setDaemon(isDaemon);
		executor.start();
	}
	
	public static AsyncExecutor create(String name, int initialPriority) {
		return create(name, initialPriority, false, false);
	}
	
	public static AsyncExecutor create(String name, int initialPriority, boolean daemon, boolean undestroyable) {
		if (undestroyable) {
			String creatorClass = Thread.currentThread().getStackTrace()[2].getClassName();
			return new AsyncExecutor(name, initialPriority, daemon) {
				
				@Override
				public void shutDown(boolean waitForTasksTermination) {
					super.shutDown(waitForTasksTermination);
				}
				
				@Override
				void closeResources() {
					this.executor = null;
					if (!Thread.currentThread().getStackTrace()[4].getClassName().equals(creatorClass)) {	
						init();
					} else {
						super.closeResources();
					}
				}
				
			};
		} else {
			return new AsyncExecutor(name, initialPriority, daemon);
		}
	}
	
	public AsyncExecutor addWithCurrentThreadPriority(Runnable executable) {
		return add(executable, Thread.currentThread().getPriority());
	}
	
	public AsyncExecutor add(Runnable executable) {
		return add(executable, this.defaultPriority);
	}
	
	public AsyncExecutor add(Runnable executable, int priority) {
		executables.add(new AbstractMap.SimpleEntry<>(executable, priority));
		try {
			synchronized(mutexManager.getMutex("executableCollectionFiller")) {
				mutexManager.getMutex("executableCollectionFiller").notifyAll();
			}
		} catch (Throwable exc) {
			logWarn("Exception occurred", exc);
		}
		return this;
	}
	
	public AsyncExecutor waitForExecutablesEnding() {
		return waitForExecutablesEnding(Thread.currentThread().getPriority());
	}
	
	public AsyncExecutor waitForExecutablesEnding(int priority) {
		executor.setPriority(priority);
		executables.stream().map(executable -> executable.setValue(priority));
		synchronized(mutexManager.getMutex("executingFinishedWaiter")) {
			if (!executables.isEmpty()) {
				try {
					mutexManager.getMutex("executingFinishedWaiter").wait();
				} catch (InterruptedException exc) {
					logWarn("Exception occurred", exc);
				}
			}
		}
		executor.setPriority(this.defaultPriority);
		return this;
	}
	
	public AsyncExecutor changePriority(int priority) {
		this.defaultPriority = priority;
		executor.setPriority(priority);
		executables.stream().map(executable -> executable.setValue(priority));
		return this;
	}
	
	public AsyncExecutor suspend() {
		return suspend(Thread.currentThread().getPriority());
	}
	
	public AsyncExecutor suspend(int priority) {
		executor.setPriority(priority);
		supended = Boolean.TRUE;
		if (currentExecutable != null) {
			synchronized (mutexManager.getMutex("suspensionCaller")) {
				if (currentExecutable != null) {
					try {
						mutexManager.getMutex("suspensionCaller").wait();
					} catch (InterruptedException exc) {
						logWarn("Exception occurred", exc);
					}
				}
			}
		}
		return this;
	}

	public AsyncExecutor resume() {
		synchronized(mutexManager.getMutex("resumeCaller")) {
			try {
				supended = Boolean.FALSE;
				mutexManager.getMutex("resumeCaller").notifyAll();
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
		Collection<Map.Entry<Runnable, Integer>> executables = this.executables;
		Thread executor = this.executor;
		if (waitForTasksTermination) {
			addWithCurrentThreadPriority(() -> {
				this.terminated = Boolean.TRUE;
				logInfo("Unexecuted tasks {}", executables.size());
				executables.clear();
			});
		} else {
			suspend();
			this.terminated = Boolean.TRUE;
			logInfo("Unexecuted tasks {}", executables.size());
			executables.clear();
			resume();
			try {
				synchronized(mutexManager.getMutex("executableCollectionFiller")) {
					mutexManager.getMutex("executableCollectionFiller").notifyAll();
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
		this.executor = null;
		mutexManager.clear();
		mutexManager = null;
		executables = null;
		currentExecutable = null;
		initializer = null;
		terminated = null;
		supended = null;
		name = null;
	}
}
