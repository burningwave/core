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

import static org.burningwave.core.assembler.StaticComponentContainer.BackgroundExecutor;
import static org.burningwave.core.assembler.StaticComponentContainer.IterableObjectHelper;
import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggersRepository;
import static org.burningwave.core.assembler.StaticComponentContainer.Methods;
import static org.burningwave.core.assembler.StaticComponentContainer.Strings;
import static org.burningwave.core.assembler.StaticComponentContainer.ThreadSupplier;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.burningwave.core.ManagedLogger;
import org.burningwave.core.function.ThrowingRunnable;
import org.burningwave.core.function.ThrowingSupplier;

@SuppressWarnings("unused")
public class Synchronizer implements AutoCloseable, ManagedLogger {
	Map<String, Mutex> mutexes;
	Thread allThreadsStateLogger;
	
	private Synchronizer() {
		mutexes = new ConcurrentHashMap<>();
	}
	
	public static Synchronizer create(boolean undestroyable) {
		if (undestroyable) {
			return new Synchronizer() {
				StackTraceElement[] stackTraceOnCreation = Thread.currentThread().getStackTrace();
				@Override
				public void close() {
					if (Methods.retrieveExternalCallerInfo().getClassName().equals(Methods.retrieveExternalCallerInfo(stackTraceOnCreation).getClassName())) {
						super.close();
					}
				}
			};
		} else {
			return new Synchronizer();
		}
	}
	
	synchronized void stop(Thread thread, boolean waitThreadToFinish) {
		if (thread == null) {
			return;
		}
		thread.shutDown();
		if (waitThreadToFinish) {
			try {				
				thread.join();
			} catch (InterruptedException exc) {
				ManagedLoggersRepository.logError(() -> this.getClass().getName(), exc);
			}
		}
	}
	
	public Mutex getMutex(String id) {
		Mutex newMutex = new Mutex();
		while (true) {			
			Mutex oldMutex = mutexes.putIfAbsent(id, newMutex);
	        if (oldMutex != null) {
	        	Mutex.Client client = oldMutex.client;
	        	synchronized (client) {
	        		++client.counter;
	        	}
	        	if (oldMutex.client.counter > 1) {
		        	if (mutexes.get(id) != oldMutex) {
		        		logError("Unvalid mutex with id {}", id);
		        	}
	        		return oldMutex;
	        	}
	        	continue;
	        }
	        newMutex.id = id;
        	if (mutexes.get(id) != newMutex) {
        		logError("Unvalid new mutex with id {}", id);
        	}
	        return newMutex;
		}
    }
	
	public void removeIfUnused(Mutex mutex) {
		try {
        	Mutex.Client client = mutex.client;
        	synchronized (client) {
        		--client.counter;
        	}
			if (client.counter < 1) {
				mutexes.remove(mutex.id);
			}
		} catch (Throwable exc) {}
	}
	
	public void execute(String id, Runnable executable) {
		Mutex mutex = getMutex(id);
		synchronized (mutex) {
			try {
				executable.run();
			} finally {
				removeIfUnused(mutex);
			}
		}
	}
	
	public <E extends Throwable> void executeThrower(String id, ThrowingRunnable<E> executable) throws E {
		Mutex mutex = getMutex(id);
		synchronized (mutex) {
			try {
				executable.run();
			} finally {
				removeIfUnused(mutex);
			}
		}
	}
	
	public <T> T execute(String id, Supplier<T> executable) {
		Mutex mutex = getMutex(id);
		synchronized (mutex) {
			try {
				return executable.get();
			} finally {
				removeIfUnused(mutex);
			}
		}
	}
	
	public <T, E extends Throwable> T executeThrower(String id, ThrowingSupplier<T, E> executable) throws E {
		Mutex mutex = getMutex(id);
		synchronized (mutex) {
			try {
				return executable.get();
			} finally {
				removeIfUnused(mutex);
			}
		}
	}

	public void clear() {
		mutexes.clear();
	}
	
	@Override
	public void close() {
		stopLoggingAllThreadsState();		
		clear();
		mutexes = null;
	}
	
	public synchronized void startLoggingAllThreadsState(Long interval) {
		if (allThreadsStateLogger != null) {
			stopLoggingAllThreadsState();
		}
		allThreadsStateLogger = ThreadSupplier.getOrCreate().setExecutable(thread -> {
			try {
				thread.waitFor(interval);
				if (thread.isLooping()) {
					logAllThreadsState();
				}
			} catch (Throwable exc) {
				logError(exc);
			}
		}, true);
		allThreadsStateLogger.setName("All threads state logger");
		allThreadsStateLogger.setPriority(Thread.MIN_PRIORITY);
		allThreadsStateLogger.start();
	}

	private void logAllThreadsState() {
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
		log.append(BackgroundExecutor.getInfoAsString());
		log.append("\n\n\n");
		log.append(
			Strings.compile(
				"Mutexes count: {}",
				mutexes.size()
			)
		);
//		log.append(
//			":\n" +
//			IterableObjectHelper.toString(mutexes, key -> key, value -> "" + value.clientsCount + " clients", 1)
//		);
		log.append("\n");
		ManagedLoggersRepository.logInfo(
			() -> this.getClass().getName(),
			log.toString()
		);
	}
	
	public java.lang.Thread[] getAllThreads() {
		return Methods.invokeStaticDirect(java.lang.Thread.class, "getThreads");
	}
	
	public void stopLoggingAllThreadsState() {
		stopLoggingAllThreadsState(false);
	}
	
	public synchronized void stopLoggingAllThreadsState(boolean waitThreadToFinish) {
		stop(allThreadsStateLogger, waitThreadToFinish);
		allThreadsStateLogger = null;
	}
	
	public static class Mutex  {	
		String id;
		private final Client client = new Client();
		
		public static class Client {
			volatile int counter = 1;
		}
	}
}
