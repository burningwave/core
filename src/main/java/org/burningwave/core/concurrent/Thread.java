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

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

import org.burningwave.core.ManagedLogger;

public class Thread extends java.lang.Thread implements ManagedLogger {
	
	Runnable executable;
	private final long index;
	boolean isAlive;
	Pool pool;
	
	private Thread(Pool pool, long index) {
		this.index = index;
		this.pool = pool;
		setDaemon(pool.daemon);
	}
	
	public void setIndexedName(String prefix) {
		setName(prefix + " -> worker " + index);
	}
	
	public Thread setExecutable(Runnable executable) {
		this.executable = executable;
		return this;
	}
	
	@Override
	public synchronized void start() {
		if (isAlive) {
			synchronized(this) {
				notifyAll();
			}
		} else {
			this.isAlive = true;
			super.start();
		}
	}
	
	void shutDown() {
		isAlive = false;
		synchronized(this) {
			if (pool.sleepingThreads.remove(this) || pool.runningThreads.remove(this)) {
				notifyAll();
				--pool.threadsCount;
			}
		}
	}	
	
	public static class Pool {
		private long threadsCount;
		private int maxThreadsCount;
		
		private Collection<Thread> runningThreads;
		private Collection<Thread> sleepingThreads;
		private boolean waitForAThreadToFreeUp;
		private boolean daemon;
		
		Pool (int maxThreadsCount, boolean daemon, boolean waitForAThreadToFreeUp) {
			this.daemon = daemon;
			runningThreads = ConcurrentHashMap.newKeySet();
			sleepingThreads = ConcurrentHashMap.newKeySet();
			this.maxThreadsCount = maxThreadsCount;
			this.waitForAThreadToFreeUp = waitForAThreadToFreeUp;
		}
		
		public final Thread getOrCreate() {
			Thread thread = get();
			if (thread != null) {
				return thread;
			}
			if (threadsCount >= maxThreadsCount && waitForAThreadToFreeUp) {
				synchronized (sleepingThreads) {
					try {
						if ((thread = get()) == null) {
							sleepingThreads.wait();
							return getOrCreate();
						}
					} catch (InterruptedException exc) {
						ManagedLoggersRepository.logError(() -> Thread.class.getName(), "Exception occurred", exc);
					}
				}
			} else if (threadsCount >= maxThreadsCount) {
				return new Thread(this, ++threadsCount) {
					@Override
					public void run() {
						executable.run();
						--threadsCount;
					}
				};
			}
			synchronized (this) {
				if (threadsCount >= maxThreadsCount) {
					return getOrCreate();
				}
				return new Thread(this, ++threadsCount) {
					@Override
					public void run() {
						while (isAlive) {
							synchronized (this) {
								runningThreads.add(this);
							}
							executable.run();				
							try {
								synchronized (this) {
									runningThreads.remove(this);
									executable = null;
									sleepingThreads.add(this);
									synchronized (sleepingThreads) {
										sleepingThreads.notifyAll();
									}
									wait();
								}
							} catch (InterruptedException exc) {
								logError("Exception occurred", exc);
							}
						}			
					}
					
				};
			}
		}

		private Thread get() {
			Iterator<Thread> itr = sleepingThreads.iterator();
			while (itr.hasNext()) {
				Thread thread = itr.next();
				if (sleepingThreads.remove(thread)) {
					return thread;
				}
			}
			return null;
		}
		
		public void shutDownAllSleeping() {
			Iterator<Thread> itr = sleepingThreads.iterator();
			while (itr.hasNext()) {
				itr.next().shutDown();
			}
		}
		
		public void shutDownAll() {
			Iterator<Thread> itr = sleepingThreads.iterator();
			while (itr.hasNext()) {
				itr.next().shutDown();
			}
			itr = runningThreads.iterator();
			while (itr.hasNext()) {
				itr.next().shutDown();
			}
		}
		
		public void setDaemon(boolean flag) {
			daemon = flag;
			Iterator<Thread> itr = sleepingThreads.iterator();
			while (itr.hasNext()) {
				itr.next().setDaemon(flag);
			}
			itr = runningThreads.iterator();
			while (itr.hasNext()) {
				itr.next().setDaemon(flag);
			}
		}
		
		public static Pool create(int maxThreadsCount, boolean daemon, boolean waitForAThreadToFreeUp) {
			return new Pool(maxThreadsCount, daemon, waitForAThreadToFreeUp);
		}
	}
}