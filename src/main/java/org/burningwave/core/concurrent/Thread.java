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
	Supplier supplier;
	
	private Thread(Supplier pool, long index) {
		super(pool.name + " - executor " + index);
		this.index = index;
		this.supplier = pool;
		setDaemon(pool.daemon);
	}
	
	public void setIndexedName() {
		setIndexedName(null);
	}
	
	public void setIndexedName(String prefix) {
		setName(Optional.ofNullable(prefix).orElseGet(() -> supplier.name + " - executor") + " " + index);
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
				ManagedLoggersRepository.logError(() -> this.getClass().getName(), exc);
			}
		}
	}
	
	void shutDown() {
		alive = false;
		stopLooping();
	}	
	
	public static class Supplier {
		private String name;
		private volatile long threadsCount;
		private int maxPoolableThreadsCount;
		private int inititialMaxThreadsCount;
		private int maxThreadsCount;
		private int maxTemporarilyThreadsCountIncreasingStep;
		private long threadRequestTimeout;
		private long elapsedTimeThresholdForResetOfMaxTemporarilyThreadsCount;
		private Collection<Thread> runningThreads;
		private Collection<Thread> poolableSleepingThreads;
		private long timeOfLastIncreaseOfMaxTemporarilyThreadsCount;
		private boolean daemon;
		
		Supplier (
			String name,
			int maxPoolableThreadsCount,
			int maxTemporarilyThreadsCount,
			boolean daemon,
			long threadRequestTimeout,
			int maxTemporarilyThreadsCountIncreasingStep,
			long elapsedTimeThresholdForResetOfMaxTemporarilyThreadsCount
		) {
			this.name = name;
			this.daemon = daemon;
			this.runningThreads = ConcurrentHashMap.newKeySet();
			this.poolableSleepingThreads = ConcurrentHashMap.newKeySet();
			if (maxPoolableThreadsCount <= 0) {
				throw new IllegalArgumentException("maxPoolableThreadsCount must be greater than zero");
			}
			if (maxTemporarilyThreadsCount <= 0) {
				maxTemporarilyThreadsCount = Integer.MAX_VALUE - maxPoolableThreadsCount;
			}
			this.maxPoolableThreadsCount = maxPoolableThreadsCount;
			this.inititialMaxThreadsCount = this.maxThreadsCount = maxPoolableThreadsCount + maxTemporarilyThreadsCount;
			this.threadRequestTimeout = threadRequestTimeout;
			this.elapsedTimeThresholdForResetOfMaxTemporarilyThreadsCount = elapsedTimeThresholdForResetOfMaxTemporarilyThreadsCount;
			this.maxTemporarilyThreadsCountIncreasingStep = maxTemporarilyThreadsCountIncreasingStep;
			this.timeOfLastIncreaseOfMaxTemporarilyThreadsCount = Long.MAX_VALUE;
		}
		
		public Thread getOrCreate(String name) {
			Thread thread = getOrCreate();
			thread.setName(name);
			return thread;
		}
		
		public final Thread getOrCreate() {
			return getOrCreate(1);
		}
		
		final Thread getOrCreate(int requestCount) {
			Thread thread = get();
			if (thread != null) {
				return thread;
			}
			if (requestCount > 0 && threadsCount > maxPoolableThreadsCount && threadsCount > maxThreadsCount) {
				synchronized (poolableSleepingThreads) {
					try {
						if ((thread = get()) == null && threadsCount > maxPoolableThreadsCount && threadsCount > maxThreadsCount) {
							//This block of code is for preventing dead locks
							long startWaitTime = System.currentTimeMillis();
							poolableSleepingThreads.wait(threadRequestTimeout);
							long endWaitTime = System.currentTimeMillis();
							long waitElapsedTime = endWaitTime - startWaitTime;
							if (waitElapsedTime < threadRequestTimeout) {
								if (inititialMaxThreadsCount < maxThreadsCount &&
									(System.currentTimeMillis() - timeOfLastIncreaseOfMaxTemporarilyThreadsCount) > 
										elapsedTimeThresholdForResetOfMaxTemporarilyThreadsCount
								) {
									maxThreadsCount -= maxTemporarilyThreadsCountIncreasingStep;
									ManagedLoggersRepository.logInfo(
										() -> this.getClass().getName(), 
										"{}: decreasing maxThreadsCount to {}", 
										Thread.currentThread(), maxThreadsCount
									);
									timeOfLastIncreaseOfMaxTemporarilyThreadsCount = Long.MAX_VALUE;
								}
								return getOrCreate(requestCount);
							} else {
								timeOfLastIncreaseOfMaxTemporarilyThreadsCount = System.currentTimeMillis();
								maxThreadsCount += maxTemporarilyThreadsCountIncreasingStep;
								ManagedLoggersRepository.logInfo(
									() -> this.getClass().getName(), 
									"{}: wait time of {}ms: preventing dead lock by temporarily increasing maxThreadsCount to {}",
									Thread.currentThread(), waitElapsedTime, maxThreadsCount
								);
								return getOrCreate(--requestCount);
							}
						}
					} catch (InterruptedException exc) {
						ManagedLoggersRepository.logError(() -> Thread.class.getName(), exc);
					}
				}
			} else if (threadsCount > maxPoolableThreadsCount) {
				return new Thread(this, ++threadsCount) {
					@Override
					public void run() {
						try {
							runningThreads.add(this);
							executable.accept(this);
						} catch (Throwable exc) {
							ManagedLoggersRepository.logError(() -> this.getClass().getName(), exc);
						}
						runningThreads.remove(this);
						--supplier.threadsCount;
						synchronized (poolableSleepingThreads) {
							poolableSleepingThreads.notifyAll();
						}
						synchronized(this) {
							notifyAll();
						}
					}
				};
			}
			synchronized (poolableSleepingThreads) {
				if (threadsCount > maxPoolableThreadsCount) {
					return getOrCreate(requestCount);
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
								ManagedLoggersRepository.logError(() -> this.getClass().getName(), exc);
							}				
							try {
								synchronized (this) {
									runningThreads.remove(this);
									executable = null;
									setIndexedName();
									poolableSleepingThreads.add(this);
									synchronized (poolableSleepingThreads) {
										poolableSleepingThreads.notifyAll();
									}
									if (!alive) {
										continue;
									}
									wait();
								}
							} catch (InterruptedException exc) {
								logError(exc);
							}
						}
						supplier.poolableSleepingThreads.remove(this);
						supplier.runningThreads.remove(this);
						--supplier.threadsCount;
						synchronized (poolableSleepingThreads) {
							poolableSleepingThreads.notifyAll();
						}
						synchronized(this) {
							notifyAll();
						}
					}
					
				};
			}
		}

		private Thread get() {
			Iterator<Thread> itr = poolableSleepingThreads.iterator();
			while (itr.hasNext()) {
				Thread thread = itr.next();
				if (poolableSleepingThreads.remove(thread)) {
					return thread;
				}
			}
			return null;
		}
		
		public void shutDownAllPoolableSleeping() {
			Iterator<Thread> itr = poolableSleepingThreads.iterator();
			while (itr.hasNext()) {
				itr.next().shutDown();
			}
		}
		
		public void shutDownAll() {
			Iterator<Thread> itr = poolableSleepingThreads.iterator();
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
			Iterator<Thread> itr = poolableSleepingThreads.iterator();
			while (itr.hasNext()) {
				itr.next().setDaemon(flag);
			}
			itr = runningThreads.iterator();
			while (itr.hasNext()) {
				itr.next().setDaemon(flag);
			}
		}

		public static Supplier create(
				String name,
				int maxPoolableThreadsCount,
				int maxTemporarilyThreadsCount,
				boolean daemon,
				long threadRequestTimeout,
				int maxTemporarilyThreadsCountIncreasingStep,
				long elapsedTimeThresholdForResetOfMaxTemporarilyThreadsCount, boolean undestroyable
		) {
			if (undestroyable) {
				return new Supplier(name, maxPoolableThreadsCount, maxTemporarilyThreadsCount, daemon, 
					threadRequestTimeout, maxTemporarilyThreadsCountIncreasingStep,
					elapsedTimeThresholdForResetOfMaxTemporarilyThreadsCount
				) {
					StackTraceElement[] stackTraceOnCreation = Thread.currentThread().getStackTrace();					
					@Override
					public void shutDownAll() {
						if (Methods.retrieveExternalCallerInfo().getClassName().equals(Methods.retrieveExternalCallerInfo(stackTraceOnCreation).getClassName())) {
							super.shutDownAll();
						}
					}
				};
			} else {
				return new Supplier(name, maxPoolableThreadsCount, maxTemporarilyThreadsCount, daemon,
					threadRequestTimeout, maxTemporarilyThreadsCountIncreasingStep,
					elapsedTimeThresholdForResetOfMaxTemporarilyThreadsCount
				);
			}
		}
	}
}