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
import static org.burningwave.core.assembler.StaticComponentContainer.GlobalProperties;
import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggersRepository;
import static org.burningwave.core.assembler.StaticComponentContainer.Methods;
import static org.burningwave.core.assembler.StaticComponentContainer.Strings;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class Synchronizer implements AutoCloseable {
	
	public static class Configuration {
		
		public static class Key {
			
			public static final String ALL_THREADS_STATE_LOGGER_ENABLED = "synchronizer.all-threads-state-logger.enabled";
			public static final String ALL_THREADS_STATE_LOGGER_LOG_INTERVAL = "synchronizer.all-threads-state-logger.log.interval";
			
		}
		
		public final static Map<String, Object> DEFAULT_VALUES;
		
		static {
			Map<String, Object> defaultValues = new HashMap<>();
	
			defaultValues.put(
				Key.ALL_THREADS_STATE_LOGGER_ENABLED, 
				"false"
			);
			defaultValues.put(
				Key.ALL_THREADS_STATE_LOGGER_LOG_INTERVAL,
				"30000"
			);				
			DEFAULT_VALUES = Collections.unmodifiableMap(defaultValues);
		}
		
	}
	
	Map<String, Object> parallelLockMap;
	java.lang.Thread allThreadsStateLogger;
	
	private Synchronizer() {
		this.parallelLockMap = new ConcurrentHashMap<>();
	}
	
	public static Synchronizer create() {
		return new Synchronizer();
	}
	
	public Object getMutex(String id) {
		Object newLock = new Object();
    	Object lock = parallelLockMap.putIfAbsent(id, newLock);
        if (lock != null) {
        	return lock;
        }
        return newLock;
    }
	
	public void execute(String id, Runnable executable) {
		synchronized (getMutex(id)) {
			try {
				executable.run();
			} finally {
				parallelLockMap.remove(id);
			}
		}
	}
	
	public <T> T execute(String id, Supplier<T> executable) {
		T result = null;
		synchronized (getMutex(id)) {
			try {
				result = executable.get();
			} finally {
				parallelLockMap.remove(id);
			}
		}
		return result;
	}

	public void clear() {
		parallelLockMap.clear();
	}
	
	@Override
	public void close() {
		clear();
		stopLoggingAllThreadsState();
		parallelLockMap = null;
	}

	public void removeMutex(String id) {
		parallelLockMap.remove(id);	
	}
	
	public synchronized void startLoggingAllThreadsState() {
		startLoggingAllThreadsState(Long.valueOf(GlobalProperties.resolveValue(Configuration.Key.ALL_THREADS_STATE_LOGGER_LOG_INTERVAL)));
	}
	
	public synchronized void startLoggingAllThreadsState(Long interval) {
		if (allThreadsStateLogger != null) {
			stopLoggingAllThreadsState();
		}
		AtomicBoolean isAliveWrapper = new AtomicBoolean();
		allThreadsStateLogger = new java.lang.Thread(() -> {
			isAliveWrapper.set(true);
			while (isAliveWrapper.get()) {
				logAllThreadsState();
				waitFor(interval);
			}
		}, "All threads state logger") {
			
			@Override
			public void interrupt() {
				isAliveWrapper.set(false);
				synchronized(allThreadsStateLogger) {
					allThreadsStateLogger.notifyAll();
				}
			}
		};
		allThreadsStateLogger.setPriority(Thread.MIN_PRIORITY);
		allThreadsStateLogger.setDaemon(true);
		allThreadsStateLogger.start();
	}

	private void logAllThreadsState() {
		StringBuffer log = new StringBuffer("\n\n");
		for (Entry<java.lang.Thread, StackTraceElement[]> threadAndStackTrace : java.lang.Thread.getAllStackTraces().entrySet()) {
			log.append("\t" + threadAndStackTrace.getKey() + ":\n");
			log.append(Strings.from(threadAndStackTrace.getValue(), 2));
			log.append("\n\n\n");
		}
		ManagedLoggersRepository.logInfo(() -> this.getClass().getName(), "Current threads state: {}", log.toString());
		BackgroundExecutor.logQueuesInfo();
	}
	
	public Thread[] getAllThreads() {
		return Methods.invokeStaticDirect(Thread.class, "getThreads");
	}
	
	private void waitFor(long timeout) {
		synchronized(allThreadsStateLogger) {
			try {
				allThreadsStateLogger.wait(timeout);
			} catch (InterruptedException exc) {
				ManagedLoggersRepository.logError(() -> this.getClass().getName(), "Exception occurred", exc);
			}
		}
	}
	
	public synchronized void stopLoggingAllThreadsState() {
		if (allThreadsStateLogger == null) {
			return;
		}
		allThreadsStateLogger.interrupt();
		allThreadsStateLogger = null;
	}
	
}
