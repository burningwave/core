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

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.burningwave.core.concurrent.Mutex;

public class AsynExecutor implements Component{
	private final Map<Runnable, Integer> executables;
	private Entry<Runnable, Integer> currentExecutable;
	private Boolean supended;
	private Mutex.Manager mutexManager;
	private Thread executor;
	private Consumer<Integer> prioritySetter;
	private AsynExecutor(String name, int initialPriority) {
		mutexManager = Mutex.Manager.create(this);
		supended = Boolean.FALSE;
		executables = new ConcurrentHashMap<>();
		executor = new Thread(() -> {
			while (executables != null) {
				if (!executables.isEmpty()) {
					Iterator<Entry<Runnable, Integer>> cleanersItr = executables.entrySet().iterator();
					while (cleanersItr.hasNext()) {
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
						try {
							currentExecutable = cleanersItr.next();
							prioritySetter.accept(currentExecutable.getValue());
							currentExecutable.getKey().run();
							cleanersItr.remove();
							synchronized(mutexManager.getMutex("suspensionCaller")) {
								currentExecutable = null;
								mutexManager.getMutex("suspensionCaller").notifyAll();
							}
						} catch (Throwable exc) {
							logWarn("Exception occurred", exc);
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
		executor.setPriority(initialPriority);
		prioritySetter = priority -> executor.setPriority(priority);
		executor.start();
	}
	
	public static AsynExecutor create(String name, int initialPriority) {
		return new AsynExecutor(name, initialPriority);
	}
	
	public void add(Runnable executable, int priority) {
		executables.put(executable, priority);
		try {
			synchronized(mutexManager.getMutex("executableCollectionFiller")) {
				mutexManager.getMutex("executableCollectionFiller").notifyAll();
			}
		} catch (Throwable exc) {
			logWarn("Exception occurred", exc);
		}
	}
	
	public void waitForExecutablesEnding() {
		executor.setPriority(Thread.MAX_PRIORITY);
		while (!executables.isEmpty()) {
			executables.replaceAll((executable, priority) -> Thread.MAX_PRIORITY);
			synchronized(mutexManager.getMutex("executingFinishedWaiter")) {
				try {
					mutexManager.getMutex("executingFinishedWaiter").wait();
				} catch (InterruptedException exc) {
					logWarn("Exception occurred", exc);
				}
			}
		}
	}

	public void suspend() {
		executor.setPriority(Thread.MAX_PRIORITY);
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
	}

	public void resume() {
		synchronized(mutexManager.getMutex("resumeCaller")) {
			try {
				supended = Boolean.FALSE;
				mutexManager.getMutex("resumeCaller").notifyAll();
			} catch (Throwable exc) {
				logWarn("Exception occurred", exc);
			}
		}		
	}

}
