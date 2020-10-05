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
import static org.burningwave.core.assembler.StaticComponentContainer.ThreadPool;

import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.burningwave.core.ManagedLogger;
import org.burningwave.core.function.ThrowingRunnable;
import org.burningwave.core.function.ThrowingSupplier;

public class Synchronizer implements AutoCloseable, ManagedLogger {
	Map<String, Mutex> mutexes;
	Collection<Mutex> mutexesMarkedAsDeletable;
	Thread mutexCleaner;
	Thread allThreadsStateLogger;
	boolean deadlock;
	private String latestLog;
	
	private Synchronizer() {
		mutexes = new ConcurrentHashMap<>();
		mutexesMarkedAsDeletable = ConcurrentHashMap.newKeySet();
		enableMutexesCleaner();
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
	
	synchronized void enableMutexesCleaner() {
		if (mutexCleaner != null) {
			disableMutexesCleaner();
		}
		mutexCleaner = ThreadPool.getOrCreate("Mutexes cleaner").setExecutable(thread -> {
			thread.waitFor(2000);
			if (thread.isLooping()) {
				for (Mutex mutex : mutexesMarkedAsDeletable) {
					mutexesMarkedAsDeletable.remove(mutex);
					if (mutex.clientsCount <= 0) {
						mutexes.remove(mutex.id);
					}
				}
			}
		}, true);
		mutexCleaner.setPriority(Thread.NORM_PRIORITY);
		mutexCleaner.setDaemon(true);
		mutexCleaner.start();
	}
	
	public void disableMutexesCleaner() {
		disableMutexesCleaner(false);
	}
	
	synchronized void disableMutexesCleaner(boolean waitThreadToFinish) {
		stop(mutexCleaner, waitThreadToFinish);
		mutexCleaner = null;
	}
	
	synchronized void stop(Thread thread, boolean waitThreadToFinish) {
		if (thread == null) {
			return;
		}
		thread.stopLooping();
		if (waitThreadToFinish) {
			try {
				thread.shutDown();
				thread.join();
			} catch (InterruptedException exc) {
				ManagedLoggersRepository.logError(() -> this.getClass().getName(), "Exception occurred", exc);
			}
		}
	}
	
	public Mutex getMutex(String id) {
		Mutex newLock = new Mutex();
		Mutex lock = mutexes.putIfAbsent(id, newLock);
        if (lock != null) {
        	++lock.clientsCount;
        	return lock;
        }
        ++newLock.clientsCount;
        newLock.id = id;
        return newLock;
    }
	
	public void markAsRemovable(Mutex mutex) {
		if (--mutex.clientsCount <= 0) {
			mutexesMarkedAsDeletable.add(mutex);
		}		
	}
	
	public void execute(String id, Runnable executable) {
		Mutex mutex = getMutex(id);
		synchronized (mutex) {
			try {
				executable.run();
			} finally {
				markAsRemovable(mutex);
			}
		}
	}
	
	public <E extends Throwable> void executeThrower(String id, ThrowingRunnable<E> executable) throws E {
		Mutex mutex = getMutex(id);
		synchronized (mutex) {
			try {
				executable.run();
			} finally {
				markAsRemovable(mutex);
			}
		}
	}
	
	public <T> T execute(String id, Supplier<T> executable) {
		Mutex mutex = getMutex(id);
		synchronized (mutex) {
			try {
				return executable.get();
			} finally {
				markAsRemovable(mutex);
			}
		}
	}
	
	public <T, E extends Throwable> T executeThrower(String id, ThrowingSupplier<T, E> executable) throws E {
		Mutex mutex = getMutex(id);
		synchronized (mutex) {
			try {
				return executable.get();
			} finally {
				markAsRemovable(mutex);
			}
		}
	}

	public void clear() {
		mutexesMarkedAsDeletable.clear();
		mutexes.clear();
	}
	
	@Override
	public void close() {
		disableMutexesCleaner(true);
		stopLoggingAllThreadsState();		
		clear();
		mutexes = null;
		mutexesMarkedAsDeletable = null;
	}
	
	public synchronized void startLoggingAllThreadsState(Long interval) {
		if (allThreadsStateLogger != null) {
			stopLoggingAllThreadsState();
		}
		allThreadsStateLogger = ThreadPool.getOrCreate().setExecutable(thread -> {
			thread.waitFor(interval);
			if (thread.isLooping()) {
				logAllThreadsState();
			}
		}, true);
		allThreadsStateLogger.setName("All threads state logger");
		allThreadsStateLogger.setPriority(Thread.MIN_PRIORITY);
		allThreadsStateLogger.start();
	}

	private void logAllThreadsState() {
		StringBuffer log = new StringBuffer("\n\n");
		log.append("Current threads state: \n\n");
		for (Entry<java.lang.Thread, StackTraceElement[]> threadAndStackTrace : java.lang.Thread.getAllStackTraces().entrySet()) {
			log.append("\t" + threadAndStackTrace.getKey());
			log.append(Strings.from(threadAndStackTrace.getValue(), 2));
			log.append("\n\n");
		}
		
		log.append("\n\n" + BackgroundExecutor.getInfoAsString());
		log.append("\n\n");
//		log.append(
//			Strings.compile(
//				"\n\tMutexes count: {}\n{}",
//				mutexes.size(),
//				IterableObjectHelper.toString(mutexes, key -> key, value -> "" + value.clientsCount + " clients", 2)
//			)
//		);
		ManagedLoggersRepository.logInfo(
			() -> this.getClass().getName(),
			log.toString()
		);
//		log.append("\n\n");
//		if (log.toString().equals(latestLog)) {
//			ManagedLoggersRepository.logError(
//				() -> this.getClass().getName(),
//				"Deadlock occurs: interrupting all threads and exit"
//			);
//			ManagedLoggersRepository.logInfo(
//				() -> this.getClass().getName(),
//				"Deadlock occurs: interrupting all threads and exit:{}",
//				log.toString()
//			);
//			deadlock = true;
//			//System.exit(-1);
//		}
//		if (deadlock) {
//			for (java.lang.Thread thread : getAllThreads()) {
//				thread.interrupt();
//			}
//		}
//		latestLog = log.toString();
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
		volatile int clientsCount;
	}
}
