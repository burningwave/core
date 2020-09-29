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

public class Thread extends java.lang.Thread implements ManagedLogger{
	private final static Collection<Thread> runningThreads;
	private final static Collection<Thread> sleepingThreads;
	private static long threadsCount;
	private static int maxThreadsCount = 60;
	static {
		runningThreads = ConcurrentHashMap.newKeySet();
		sleepingThreads = ConcurrentHashMap.newKeySet();
	}
	
	Runnable executable;
	private long index;
	boolean isAlive;
	
	private Thread(long index) {
		this.index = index;
	}
	
	public void setIndexedName(String prefix) {
		setName(prefix + " -> worker " + index);
	}

	Thread setExecutable(Runnable executable) {
		this.executable = executable;
		return this;
	}
	
	final static Thread getOrCreate() {
		Thread thread = get();
		if (thread != null) {
			return thread;
		}
		if (threadsCount > maxThreadsCount) {
			if (maxThreadsCount < 1) {
				return new Thread(++threadsCount) {
					@Override
					public void run() {
						executable.run();
					}
				};
			}
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
		}
		return new Thread(++threadsCount) {
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

	private static Thread get() {
		Iterator<Thread> itr = sleepingThreads.iterator();
		while (itr.hasNext()) {
			Thread thread = itr.next();
			if (sleepingThreads.remove(thread)) {
				return thread;
			}
		}
		return null;
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
			if (sleepingThreads.remove(this) || runningThreads.remove(this)) {
				notifyAll();
			}
		}
	}
	
	public static void shutDownAllSleeping() {
		Iterator<Thread> itr = sleepingThreads.iterator();
		while (itr.hasNext()) {
			itr.next().shutDown();
		}
	}
	
	public static void shutDownAll() {
		Iterator<Thread> itr = sleepingThreads.iterator();
		while (itr.hasNext()) {
			itr.next().shutDown();
		}
		itr = runningThreads.iterator();
		while (itr.hasNext()) {
			itr.next().shutDown();
		}
	}
}