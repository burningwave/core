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

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

public class AsynExecutor implements Component{
	private final Collection<Runnable> executables;
	private Boolean supended;
	
	private AsynExecutor() {
		supended = Boolean.FALSE;
		executables = ConcurrentHashMap.newKeySet();
		Thread cleaner = new Thread(() -> {
			while (executables != null) {
				if (!executables.isEmpty()) {
					Iterator<Runnable> cleanersItr = executables.iterator();
					while (cleanersItr.hasNext()) {
						synchronized(executables) {
							try {
								if (supended) {
									executables.wait();
									continue;
								}
							} catch (InterruptedException exc) {
								logWarn("Exception occurred", exc);
							}
						}
						try {
							cleanersItr.next().run();
							cleanersItr.remove();
						} catch (Throwable exc) {
							logWarn("Exception occurred", exc);
						}						
					}
					synchronized(executables) {
						if (executables.isEmpty()) {
							try {
								executables.notifyAll();
							} catch (Throwable exc) {
								logWarn("Exception occurred", exc);
							}
						} else {
							continue;
						}
					}
				} else {
					synchronized(executables) {
						try {
							while (executables.isEmpty()) {
								executables.wait();
							}
						} catch (InterruptedException exc) {
							logWarn("Exception occurred", exc);
						}
					}
				}
			}
		}, this.toString());
		cleaner.setPriority(Thread.MIN_PRIORITY);
		cleaner.start();
	}
	
	public static AsynExecutor create() {
		return new AsynExecutor();
	}
	
	public void add(Runnable cleaner) {
		executables.add(cleaner);
		try {
			synchronized(executables) {
				executables.notifyAll();
			}
		} catch (Throwable exc) {
			logWarn("Exception occurred", exc);
		}
	}
	
	public void waitForExecutorsEnding() {
		while (!executables.isEmpty()) {
			synchronized(executables) {
				try {
					executables.wait();
				} catch (InterruptedException exc) {
					logWarn("Exception occurred", exc);
				}
			}
		}
	}

	public void suspend() {
		supended = Boolean.TRUE;
	}

	public void resume() {
		synchronized(executables) {
			try {
				supended = Boolean.FALSE;
				executables.notifyAll();
			} catch (Throwable exc) {
				logWarn("Exception occurred", exc);
			}
		}		
	}

}
