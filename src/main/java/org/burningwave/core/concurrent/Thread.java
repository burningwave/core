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

import java.util.Collection;
import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.burningwave.core.ManagedLogger;

public class Thread extends java.lang.Thread implements ManagedLogger {
	
	Consumer<Thread> originalExecutable;
	Consumer<Thread> executable;
	boolean looper;
	boolean looping;
	private final long index;
	boolean alive;
	Pool pool;
	
	private Thread(Pool pool, long index) {
		super(pool.name + " - executor " + index);
		this.index = index;
		this.pool = pool;
		setDaemon(pool.daemon);
	}
	
	public void setIndexedName() {
		setIndexedName(null);
	}
	
	public void setIndexedName(String prefix) {
		setName(Optional.ofNullable(prefix).orElseGet(() -> pool.name + " - executor") + " " + index);
	}
	
	public Thread setExecutable(Consumer<Thread> executable) {
		this.originalExecutable = executable;
		return setExecutable(executable, false);
	}
	
	public Thread setExecutable(Consumer<Thread> executable, boolean flag) {
		this.originalExecutable = executable;
		this.looper = flag;
		return this;
	}
	
	@Override
	public synchronized void start() {
		if (!looper) {
			this.executable = originalExecutable;
		} else {
			this.executable = thread -> {
				looping = true;
				while (looping) {
					originalExecutable.accept(this);
				}
			};
		}
		if (alive) {
			synchronized(this) {
				notifyAll();
			}
		} else {
			this.alive = true;
			super.start();
		}
	}
	
	public void stopLooping() {
		looping = false;
		synchronized (this) {
			notifyAll();
		}
	}
	
	public boolean isLooping() {
		return looping;
	}
	
	void waitFor(long millis) {
		synchronized (this) {
			try {
				wait(millis);
			} catch (InterruptedException exc) {
				ManagedLoggersRepository.logError(() -> this.getClass().getName(), "Exception occurred", exc);
			}
		}
	}
	
	void shutDown() {
		alive = false;
		looping = false;
	}	
	
	public static class Pool {
		private String name;
		private volatile long threadsCount;
		private int maxThreadsCount;
		private int maxNewThreadsCount;
		private Collection<Thread> runningThreads;
		private Collection<Thread> sleepingThreads;
		private boolean daemon;
		
		Pool (String name, int maxThreadsCount, int maxNewThreadsCount, boolean daemon) {
			this.name = name;
			this.daemon = daemon;
			this.runningThreads = ConcurrentHashMap.newKeySet();
			this.sleepingThreads = ConcurrentHashMap.newKeySet();
			if (maxNewThreadsCount <= 0) {
				maxNewThreadsCount = Integer.MAX_VALUE;
			}
			if (maxNewThreadsCount < maxThreadsCount) {
				throw new IllegalArgumentException("maxNewThreadsCount must be greater than maxThreadsCount");
			}
			this.maxThreadsCount = maxThreadsCount;
			this.maxNewThreadsCount = maxNewThreadsCount;
		}
		
		public Thread getOrCreate(String name) {
			Thread thread = getOrCreate();
			thread.setName(name);
			return thread;
		}
		
		public final Thread getOrCreate() {
			return getOrCreate(1);
		}
		
		public final Thread getOrCreate(int count) {
			Thread thread = get();
			if (thread != null) {
				return thread;
			}
			if (count > 0 && threadsCount > maxThreadsCount && threadsCount > maxNewThreadsCount) {
				synchronized (sleepingThreads) {
					try {
						if ((thread = get()) == null && threadsCount > maxThreadsCount && threadsCount > maxNewThreadsCount) {
							//This block of code is for preventing dead locks
							long startWaitTime = System.currentTimeMillis();
							sleepingThreads.wait(15000);
							long endWaitTime = System.currentTimeMillis();
							long waitTime = endWaitTime - startWaitTime;
							if (waitTime < 15000) {
								return getOrCreate(count);								
							} else {
								ManagedLoggersRepository.logWarn(() -> this.getClass().getName(), "Wait time of {}ms", waitTime);
								return getOrCreate(--count);
							}
						}
					} catch (InterruptedException exc) {
						ManagedLoggersRepository.logError(() -> Thread.class.getName(), "Exception occurred", exc);
					}
				}
			} else if (threadsCount > maxThreadsCount) {
				return new Thread(this, ++threadsCount) {
					@Override
					public void run() {
						try {
							executable.accept(this);
						} catch (Throwable exc) {
							ManagedLoggersRepository.logError(() -> this.getClass().getName(), "Exception occurred", exc);
						}
						--pool.threadsCount;
						synchronized (sleepingThreads) {
							sleepingThreads.notifyAll();
						}
						synchronized(this) {
							notifyAll();
						}
					}
				};
			}
			synchronized (sleepingThreads) {
				if (threadsCount > maxThreadsCount) {
					return getOrCreate(count);
				}
				return new Thread(this, ++threadsCount) {
					@Override
					public void run() {
						while (alive) {
							synchronized (this) {
								runningThreads.add(this);
							}
							try {
								executable.accept(this);
							} catch (Throwable exc) {
								ManagedLoggersRepository.logError(() -> this.getClass().getName(), "Exception occurred", exc);
							}				
							try {
								synchronized (this) {
									runningThreads.remove(this);
									executable = null;
									setIndexedName();
									sleepingThreads.add(this);
									synchronized (sleepingThreads) {
										sleepingThreads.notifyAll();
									}
									if (!alive) {
										continue;
									}
									wait();
								}
							} catch (InterruptedException exc) {
								logError("Exception occurred", exc);
							}
						}
						pool.sleepingThreads.remove(this);
						pool.runningThreads.remove(this);
						--pool.threadsCount;
						synchronized (sleepingThreads) {
							sleepingThreads.notifyAll();
						}
						synchronized(this) {
							notifyAll();
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
		
		public static Pool create(String name, int maxThreadsCount, int maxNewThreadsCount, boolean daemon) {
			return create(name, maxThreadsCount, maxNewThreadsCount, daemon, false);
		}
		
		public static Pool create(String name, int maxThreadsCount, int maxNewThreadsCount, boolean daemon, boolean undestroyable) {
			if (undestroyable) {
				return new Pool(name, maxThreadsCount, maxNewThreadsCount, daemon) {
					StackTraceElement[] stackTraceOnCreation = Thread.currentThread().getStackTrace();					
					@Override
					public void shutDownAll() {
						if (Methods.retrieveExternalCallerInfo().getClassName().equals(Methods.retrieveExternalCallerInfo(stackTraceOnCreation).getClassName())) {
							super.shutDownAll();
						}
					}
				};
			} else {
				return new Pool(name, maxThreadsCount, maxNewThreadsCount, daemon);
			}
		}
	}
}