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

import static org.burningwave.core.assembler.StaticComponentContainer.IterableObjectHelper;
import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggersRepository;
import static org.burningwave.core.assembler.StaticComponentContainer.Methods;
import static org.burningwave.core.assembler.StaticComponentContainer.Strings;
import static org.burningwave.core.assembler.StaticComponentContainer.ThreadHolder;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.burningwave.core.Closeable;
import org.burningwave.core.ManagedLogger;
import org.burningwave.core.function.ThrowingRunnable;
import org.burningwave.core.function.ThrowingSupplier;


public class Synchronizer implements Closeable, ManagedLogger {
	Map<String, Mutex> mutexes;
	String name;
	ThreadsMonitorer allThreadsMonitorer;
	
	private Synchronizer(String name) {
		this.name = name;
		mutexes = new ConcurrentHashMap<>();
	}
	
	public static Synchronizer create(String name, boolean undestroyable) {
		if (undestroyable) {
			return new Synchronizer(name) {
				StackTraceElement[] stackTraceOnCreation = Thread.currentThread().getStackTrace();
				@Override
				public void close() {
					if (Methods.retrieveExternalCallerInfo().getClassName().equals(Methods.retrieveExternalCallerInfo(stackTraceOnCreation).getClassName())) {
						super.close();
					}
				}
			};
		} else {
			return new Synchronizer(name);
		}
	}
	
	public Mutex getMutex(String id) {
		Mutex newMutex = new Mutex(id);
		while (true) {			
			Mutex oldMutex = mutexes.putIfAbsent(id, newMutex);
	        if (oldMutex == null) {
		        return newMutex;
	        }
	        if (++oldMutex.clientsCount > 1 && mutexes.get(id) == oldMutex) {
	        	return oldMutex;
        	}
        	//logWarn("Unvalid mutex with id \"{}\": a new mutex will be created", id);
        	continue;
		}
    }

	public void removeIfUnused(Mutex mutex) {
		try {
			if (--mutex.clientsCount < 1) {
				mutexes.remove(mutex.id);
			}
		} catch (Throwable exc) {

		}
	}
	
	public void execute(String id, Runnable executable) {
		Mutex mutex = getMutex(id);
		try {
			synchronized (mutex) {
				executable.run();
			}			
		} finally {
			removeIfUnused(mutex);
		}
	}
	
	public <E extends Throwable> void executeThrower(String id, ThrowingRunnable<E> executable) throws E {
		Mutex mutex = getMutex(id);
		try {
			synchronized (mutex) {
				executable.run();
			}			
		} finally {
			removeIfUnused(mutex);
		}	
	}
	
	public <T> T execute(String id, Supplier<T> executable) {
		Mutex mutex = getMutex(id);
		try {
			synchronized (mutex) {
				return executable.get();
			}			
		} finally {
			removeIfUnused(mutex);
		}	
	}
	
	public <T, E extends Throwable> T executeThrower(String id, ThrowingSupplier<T, E> executable) throws E {
		Mutex mutex = getMutex(id);
		try {
			synchronized (mutex) {
				return executable.get();
			}			
		} finally {
			removeIfUnused(mutex);
		}
	}

	public void clear() {
		mutexes.clear();
	}
	
	@Override
	public void close() {
		if (this.allThreadsMonitorer != null) {
			this.allThreadsMonitorer.close();
			this.allThreadsMonitorer = null;
		}
		clear();
		mutexes = null;
	}

	public void logAllThreadsState(boolean logMutexes) {
		ManagedLoggersRepository.logInfo(
			() -> this.getClass().getName(),
			getAllThreadsInfoAsString(logMutexes)
		);
	}

	public String getAllThreadsInfoAsString(boolean getMutexesInfo) {
		StringBuffer log = new StringBuffer("\n\n");
		log.append("Current threads state: \n\n");
		Iterator<Entry<java.lang.Thread, StackTraceElement[]>> allStackTracesItr = java.lang.Thread.getAllStackTraces().entrySet().iterator();
		while(allStackTracesItr.hasNext()) {
			Map.Entry<java.lang.Thread, StackTraceElement[]> threadAndStackTrace = allStackTracesItr.next();
			log.append("\t" + threadAndStackTrace.getKey());
			log.append(Strings.from(threadAndStackTrace.getValue(), 2));
			if (allStackTracesItr.hasNext()) {
				log.append("\n\n");
			}
		}
		log.append("\n\n\n");
		log.append(
			Strings.compile(
				"Mutexes count: {}",
				mutexes.size()
			)
		);
		if (getMutexesInfo) {
			log.append(
				":\n" +
				IterableObjectHelper.toString(mutexes, key -> key, value -> "" + value.clientsCount + " clients", 1)
			);
		}
		log.append("\n");
		return log.toString();
	}
	
	public java.lang.Thread[] getAllThreads() {
		return Methods.invokeStaticDirect(java.lang.Thread.class, "getThreads");
	}
	
	public void startAllThreadsMonitoring(Long interval) {
		ThreadsMonitorer allThreadsMonitorer = this.allThreadsMonitorer;
		if (allThreadsMonitorer == null) {
			synchronized(this) {
				allThreadsMonitorer = this.allThreadsMonitorer;
				if (allThreadsMonitorer == null) {
					this.allThreadsMonitorer = new ThreadsMonitorer(this);
				}
			}
		}
		this.allThreadsMonitorer.start(interval);
	}
	
	public void stopAllThreadsMonitoring() {
		stopAllThreadsMonitoring(false);
	}
	
	public void stopAllThreadsMonitoring(boolean waitThreadToFinish) {
		ThreadsMonitorer allThreadsMonitorer = this.allThreadsMonitorer;
		if (allThreadsMonitorer != null) {
			allThreadsMonitorer.stop(waitThreadToFinish);
		}		
	}
	
	public static class Mutex {
		Mutex(String id) {
			this.id = id;
		}
		String id;
		int clientsCount = 1;
	}
	
	static class ThreadsMonitorer implements Closeable {
		Synchronizer synchronizer;
		
		ThreadsMonitorer(Synchronizer synchronizer) {
			this.synchronizer = synchronizer;
		}
		
		public ThreadsMonitorer start(Long interval) {
			ThreadHolder.startLooping(getName(), true, java.lang.Thread.MIN_PRIORITY, thread -> {
				thread.waitFor(interval);
				if (thread.isLooping()) {
					synchronizer.logAllThreadsState(false);
				}
			});
			return this;
		}
		
		private String getName() {
			return Optional.ofNullable(synchronizer.name).map(nm -> nm + " - ").orElseGet(() -> "") + "All threads state logger";
		}
		
		public void stop(boolean waitThreadToFinish) {
			ThreadHolder.stop(getName());
		}
		
		@Override
		public void close() {
			close(false);
		}
		
		public void close(boolean waitForTasksTermination) {
			stop(waitForTasksTermination);
			synchronizer = null;
		}
	}
}
