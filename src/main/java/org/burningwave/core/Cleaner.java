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

public class Cleaner implements Component{
	private final Collection<Runnable> cleaners;
	private Boolean supended;
	
	private Cleaner() {
		supended = Boolean.FALSE;
		cleaners = ConcurrentHashMap.newKeySet();
		Thread cleaner = new Thread(() -> {
			while (cleaners != null) {
				if (!cleaners.isEmpty()) {
					Iterator<Runnable> cleanersItr = cleaners.iterator();
					while (cleanersItr.hasNext()) {
						synchronized(cleaners) {
							try {
								if (supended) {
									cleaners.wait();
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
					synchronized(cleaners) {
						if (cleaners.isEmpty()) {
							try {
								cleaners.notify();
							} catch (Throwable exc) {
								logWarn("Exception occurred", exc);
							}
						} else {
							continue;
						}
					}
				} else {
					synchronized(cleaners) {
						try {
							if (cleaners.isEmpty()) {
								cleaners.wait();
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
	
	public static Cleaner create() {
		return new Cleaner();
	}
	
	public void add(Runnable cleaner) {
		cleaners.add(cleaner);
		try {
			synchronized(cleaners) {
				cleaners.notify();
			}
		} catch (Throwable exc) {
			logWarn("Exception occurred", exc);
		}
	}
	
	public void waitForCleanEnding() {
		if (!cleaners.isEmpty()) {
			synchronized(cleaners) {
				try {
					if (!cleaners.isEmpty()) {
						cleaners.wait();
					}
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
		synchronized(cleaners) {
			try {
				supended = Boolean.FALSE;
				cleaners.notify();
			} catch (Throwable exc) {
				logWarn("Exception occurred", exc);
			}
		}		
	}

}
