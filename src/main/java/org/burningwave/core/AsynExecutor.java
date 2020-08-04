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
package org.burningwave.core;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CopyOnWriteArrayList;

import org.burningwave.core.concurrent.Mutex;

public class AsynExecutor implements Component{
	private final Collection<Map.Entry<Runnable, Integer>> executables;
	private Entry<Runnable, Integer> currentExecutable;
	private Boolean supended;
	private Mutex.Manager mutexManager;
	private Thread executor;
	private int defaultPriority;
	
	private AsynExecutor(String name, int defaultPriority, boolean daemon) {
		mutexManager = Mutex.Manager.create(this);
		supended = Boolean.FALSE;
		executables = new CopyOnWriteArrayList<>();
		executor = new Thread(() -> {
			while (executables != null) {
				if (!executables.isEmpty()) {
					for (Entry<Runnable, Integer> executable : executables) {
						synchronized(mutexManager.getMutex("resumeCaller")) {
							try {
								if (supended) {
									mutexManager.getMutex("resumeCaller").wait();
									continue;
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
							logInfo("Executing {}", runnable.toString());
							runnable.run();
							executables.remove(executable);
							synchronized(mutexManager.getMutex("suspensionCaller")) {
								currentExecutable = null;
								mutexManager.getMutex("suspensionCaller").notifyAll();
							}
						} catch (Throwable exc) {
							logError("Exception occurred while executing " + runnable.toString(), exc);
						}						
					}
					synchronized(mutexManager.getMutex("executingFinishedWaiter")) {
						if (executables.isEmpty()) {
							try {
								mutexManager.getMutex("executingFinishedWaiter").notifyAll();
							} catch (Throwable exc) {
								logWarn("Exception occurred", exc);
							}
						} else {
							continue;
						}
					}
				} else {
					synchronized(mutexManager.getMutex("executableCollectionFiller")) {
						try {
							while (executables.isEmpty()) {
								mutexManager.getMutex("executableCollectionFiller").wait();
							}
						} catch (InterruptedException exc) {
							logWarn("Exception occurred", exc);
						}
					}
				}
			}
		}, name);
		executor.setPriority(this.defaultPriority = defaultPriority);
		executor.setDaemon(daemon);
		executor.start();
	}
	
	public static AsynExecutor create(String name, int initialPriority) {
		return create(name, initialPriority, false);
	}
	
	public static AsynExecutor create(String name, int initialPriority, boolean daemon) {
		return new AsynExecutor(name, initialPriority, daemon);
	}
	
	public AsynExecutor addWithCurrentThreadPriority(Runnable executable) {
		return add(executable, Thread.currentThread().getPriority());
	}
	
	public AsynExecutor add(Runnable executable) {
		return add(executable, this.defaultPriority);
	}
	
	public AsynExecutor add(Runnable executable, int priority) {
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
	
	public AsynExecutor waitForExecutablesEnding() {
		return waitForExecutablesEnding(Thread.currentThread().getPriority());
	}
	
	public AsynExecutor waitForExecutablesEnding(int priority) {
		executor.setPriority(priority);
		while (!executables.isEmpty()) {
			changePriority(priority);
			executables.stream().map(executable -> executable.setValue(priority));
			synchronized(mutexManager.getMutex("executingFinishedWaiter")) {
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
	
	public AsynExecutor changePriority(int priority) {
		this.defaultPriority= priority;
		executor.setPriority(priority);
		executables.stream().map(executable -> executable.setValue(priority));
		return this;
	}
	
	public AsynExecutor suspend() {
		return suspend(Thread.currentThread().getPriority());
	}
	
	public AsynExecutor suspend(int priority) {
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

	public AsynExecutor resume() {
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

}
