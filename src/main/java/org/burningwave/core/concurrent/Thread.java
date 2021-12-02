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
 * Copyright (c) 2019-2021 Roberto Gentili
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
import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository;
import static org.burningwave.core.assembler.StaticComponentContainer.Methods;
import static org.burningwave.core.assembler.StaticComponentContainer.Objects;
import static org.burningwave.core.assembler.StaticComponentContainer.Strings;
import static org.burningwave.core.assembler.StaticComponentContainer.Synchronizer;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

import org.burningwave.core.Closeable;
import org.burningwave.core.Identifiable;
import org.burningwave.core.concurrent.Synchronizer.Mutex;
import org.burningwave.core.function.ThrowingConsumer;
import org.burningwave.core.iterable.IterableObjectHelper.ResolveConfig;

public abstract class Thread extends java.lang.Thread {
	
	volatile ThrowingConsumer<Thread, ? extends Throwable> originalExecutable;
	volatile ThrowingConsumer<Thread, ? extends Throwable> executable;
	boolean looper;
	boolean looping;
	private long number;
	Boolean running;
	Supplier supplier;
	
	private Thread(Supplier threadSupplier, long number) {
		super(threadSupplier.name + " - Executor " + number);
		this.supplier = threadSupplier;
		this.number = number;
		setIndexedName();
		setDaemon(threadSupplier.daemon);
	}
	
	public void setIndexedName() {
		setIndexedName(null);
	}

	public void setIndexedName(String prefix) {
		setName(Optional.ofNullable(prefix).orElseGet(() -> supplier.name + " - Executor") + " " + number);
	}
	
	public Thread setExecutable(ThrowingConsumer<Thread, ? extends Throwable> executable) {
		return setExecutable(executable, false);
	}
	
	public Thread setExecutable(ThrowingConsumer<Thread, ? extends Throwable> executable, boolean isLooper) {
		checkExecutable(executable);
		this.originalExecutable = executable;
		this.looper = isLooper;
		return this;
	}

	private ThrowingConsumer<Thread, ? extends Throwable> checkExecutable(ThrowingConsumer<Thread, ? extends Throwable> executable) {
		if (executable == null) {
			executable = thread -> {
				ManagedLoggerRepository.logError(getClass()::getName, "Executable of {} was set to null", this);
			};
			this.originalExecutable = executable;
			this.looper = false;
			start();
			throw new NullExecutableException(Strings.compile("Executable of {} was set to null", this));
		}
		return executable;
	}
	
	public boolean isDetached() {
		return this instanceof Detached;
	}
	
	public boolean isPoolable() {
		return this instanceof Poolable;
	}

	@Override
	public void start() {
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
		if (running != null) {
			synchronized (this) {
				notifyAll();
			}
		} else {
			this.running = true;
			super.start();
		}
	}

	public void stopLooping() {
		looping = false;
		synchronized(this) {
			notifyAll();
		}
	}
	
	public boolean isRunning() {
		return running;
	}
	
	public boolean isLooping() {
		return looping;
	}

	public synchronized void waitFor(long millis) {
		try {
			wait(millis);
		} catch (InterruptedException exc) {
			ManagedLoggerRepository.logError(getClass()::getName, exc);
		}
	}

	void shutDown() {
		shutDown(false);
	}

	void shutDown(boolean waitForFinish) {
		running = false;
		stopLooping();
		if (waitForFinish && Thread.currentThread() != this) {
			try {
				join();
			} catch (InterruptedException exc) {
				ManagedLoggerRepository.logError(getClass()::getName, exc);
			}
		}
	}
	
	public void kill() {
		terminate(thread -> 
			Methods.invokeDirect(thread, "stop0", new ThreadDeath()), 
			"stop"
		);
	}
	
	@Override
	public void interrupt() {
		terminate(thread -> super.interrupt(), "interrupt");
	}
	
	abstract void terminate(Consumer<Thread> operation, String operationName);
	
	@Override
	public String toString() {
		return super.toString() + Optional.ofNullable(getState()).map(threadState ->
			" (" + Strings.capitalizeFirstCharacter(threadState.name().toLowerCase().replace("_", " ")) + ")"
		).orElseGet(() -> "");
	}
	
	private static class Poolable extends Thread {
		
		private Poolable(Thread.Supplier supplier, long number) {
			super(supplier, number);
		}
		
		@Override
		public void run() {
			NullExecutableException nullExecutableException = null;
			while (running) {
				supplier.runningThreads.add(this);
				try {
					executable.accept(this);
				} catch (Throwable exc) {
					if (executable == null || originalExecutable == null) {
						nullExecutableException = new NullExecutableException(Strings.compile("Executable of thread {} is null", this));
						ManagedLoggerRepository.logError(getClass()::getName, "{}, {}, {}", exc, this, executable, originalExecutable);
						ManagedLoggerRepository.logWarn(getClass()::getName, "The thread {} will be shutted down", this);
						shutDown();
					} else {
						ManagedLoggerRepository.logError(getClass()::getName, "{}, {}, {}", exc);
					}
				}
				try {
					supplier.runningThreads.remove(this);
					executable = null;
					originalExecutable = null;
					setIndexedName();
					if (!running) {
						continue;
					}
					synchronized(this) {
						if (supplier.addPoolableSleepingThreadFunction.apply(this) == null) {
							ManagedLoggerRepository.logWarn(
								getClass()::getName,
								"Could not add thread '{}' to poolable sleeping container: it will be shutted down",
								this
							);
							this.shutDown();
							continue;
						}
						synchronized(supplier.poolableSleepingThreads) {
							supplier.poolableSleepingThreads.notifyAll();
						}					
						wait();
					}
				} catch (InterruptedException exc) {
					ManagedLoggerRepository.logError(getClass()::getName, exc);
					this.shutDown();
				}
			}
			removePermanently();
			synchronized(supplier.poolableSleepingThreads) {
				supplier.poolableSleepingThreads.notifyAll();
			}
			synchronized(this) {
				notifyAll();
			}
			if (nullExecutableException != null) {
				throw nullExecutableException;
			}
		}
		
		@Override
		void terminate(Consumer<Thread> operation, String operationName) {
			ManagedLoggerRepository.logWarn(
				getClass()::getName,
				"Called {} by {}{}\n\ton {} (executable: {}):{}",
				operationName,
				Thread.currentThread(),
				Strings.from(Methods.retrieveExternalCallersInfo(), 2),
				this,
				executable,
				Strings.from(getStackTrace(), 2)
			);
			shutDown();
			removePermanently();
			java.lang.Thread currentThread = Thread.currentThread();
			if (this != currentThread) {	
				try {
					operation.accept(this);
				} catch (Throwable exc) {
					ManagedLoggerRepository.logError(getClass()::getName, "Exception occurred", exc);						
				}
			}
			synchronized(supplier.poolableSleepingThreads) {
				supplier.poolableSleepingThreads.notifyAll();
			}
			synchronized(this) {
				notifyAll();
			}
			if (this == currentThread) {	
				Thread killer = supplier.getOrCreate().setExecutable(thread -> {
					operation.accept(this);
				});
				killer.setPriority(currentThread.getPriority());
				killer.start();
			}
		}

		private void removePermanently () {
			if (supplier.runningThreads.remove(this)) {
				--supplier.threadCount;
				--supplier.poolableThreadCount;
			}
			if (supplier.removePoolableSleepingThread(this)) {
				--supplier.threadCount;
				--supplier.poolableThreadCount;
			}
		}
	}
	
	private static class Detached extends Thread {
		
		private Detached(Thread.Supplier supplier, long number) {
			super(supplier, number);
		}

		@Override
		public void run() {
			try {
				supplier.runningThreads.add(this);
				executable.accept(this);
			} catch (Throwable exc) {
				ManagedLoggerRepository.logError(getClass()::getName, exc);
			}
			if (supplier.runningThreads.remove(this)) {
				--supplier.threadCount;
			}
			synchronized(supplier.poolableSleepingThreads) {
				supplier.poolableSleepingThreads.notifyAll();
			}
			synchronized(this) {
				notifyAll();
			}
		}

		@Override
		void terminate(Consumer<Thread> operation, String operationName) {
			ManagedLoggerRepository.logWarn(
				getClass()::getName,
				"Called {} by {}{}\n\ton {} (executable: {}):{}",
				operationName,
				Thread.currentThread(),
				Strings.from(Methods.retrieveExternalCallersInfo(), 2),
				this,
				executable,
				Strings.from(getStackTrace(), 2)
			);
			shutDown();
			if (supplier.runningThreads.remove(this)) {
				--supplier.threadCount;
			}
			try {
				operation.accept(this);
			} catch (Throwable exc) {
				ManagedLoggerRepository.logError(getClass()::getName, "Exception occurred", exc);
			}
			synchronized(supplier.poolableSleepingThreads) {
				supplier.poolableSleepingThreads.notifyAll();
			}
			synchronized(this) {
				notifyAll();
			}
		}
	}
	
	
	public static class Supplier implements Identifiable {
		public static class Configuration {
			public static class Key {
				public static final String MAX_POOLABLE_THREAD_COUNT = "thread-supplier.max-poolable-thread-count";
				public static final String MAX_DETACHED_THREAD_COUNT = "thread-supplier.max-detached-thread-count";
				public static final String DEFAULT_DAEMON_FLAG_VALUE = "thread-supplier.default-daemon-flag-value";
				public static final String POOLABLE_THREAD_REQUEST_TIMEOUT = "thread-supplier.poolable-thread-request-timeout";
				public static final String MAX_DETACHED_THREAD_COUNT_ELAPSED_TIME_THRESHOLD_FROM_LAST_INCREASE_FOR_GRADUAL_DECREASING_TO_INITIAL_VALUE = "thread-supplier.max-detached-thread-count.elapsed-time-threshold-from-last-increase-for-gradual-decreasing-to-initial-value";
				public static final String MAX_DETACHED_THREAD_COUNT_INCREASING_STEP = "thread-supplier.max-detached-thread-count.increasing-step";
			}

			public final static Map<String, Object> DEFAULT_VALUES;

			static {
				Map<String, Object> defaultValues =  new HashMap<>();

				defaultValues.put(
					Key.MAX_POOLABLE_THREAD_COUNT,
					"autodetect"
				);

				defaultValues.put(
					Key.MAX_DETACHED_THREAD_COUNT,
					"${" + Key.MAX_POOLABLE_THREAD_COUNT + "}"
				);

				defaultValues.put(
					Key.POOLABLE_THREAD_REQUEST_TIMEOUT,
					6000
				);

				defaultValues.put(
					Key.DEFAULT_DAEMON_FLAG_VALUE,
					true
				);

				defaultValues.put(
					Key.MAX_DETACHED_THREAD_COUNT_ELAPSED_TIME_THRESHOLD_FROM_LAST_INCREASE_FOR_GRADUAL_DECREASING_TO_INITIAL_VALUE,
					30000
				);

				defaultValues.put(
					Key.MAX_DETACHED_THREAD_COUNT_INCREASING_STEP,
					"autodetect"
				);

				DEFAULT_VALUES = Collections.unmodifiableMap(defaultValues);
			}
		}
		
		private static long threadNumberSupplier;
		
		private String name;
		private volatile int threadCount;
		private volatile int poolableThreadCount;
		private int maxPoolableThreadCount;
		private int inititialMaxThreadCount;		
		private int maxThreadCount;
		private int maxDetachedThreadCountIncreasingStep;
		private long poolableThreadRequestTimeout;
		private long elapsedTimeThresholdFromLastIncreaseForGradualDecreasingOfMaxDetachedThreadsCount;
		private Collection<Thread> runningThreads;
		//Changed poolable thread container to array (since 12.15.2, the previous version is 12.15.1)
		private Thread.Poolable[] poolableSleepingThreads;
		private long timeOfLastIncreaseOfMaxDetachedThreadCount;
		private boolean daemon;
		private Function<Thread.Poolable, Integer> addForwardPoolableSleepingThreadFunction;
		private Function<Thread.Poolable, Integer> addReversePoolableSleepingThreadFunction;
		private Function<Thread.Poolable, Integer> addPoolableSleepingThreadFunction;
		private java.util.function.Supplier<Thread.Poolable> getForwardPoolableThreadFunction;
		private java.util.function.Supplier<Thread.Poolable> getReversePoolableThreadFunction;
		private java.util.function.Supplier<Thread.Poolable> getPoolableThreadFunction;
		//Cached operation id
		private String accessForIndexToPoolableSleepingThreadsOperationId;
		
		Supplier (
			String name,
			Map<Object, Object> config
		) {	
			this.addForwardPoolableSleepingThreadFunction = this::addForwardPoolableSleepingThread;
			this.addReversePoolableSleepingThreadFunction = this::addReversePoolableSleepingThread;
			this.addPoolableSleepingThreadFunction = addForwardPoolableSleepingThreadFunction;
			this.accessForIndexToPoolableSleepingThreadsOperationId = getOperationId("accessForIndexToPoolableSleepingThreads");
			this.getForwardPoolableThreadFunction = this::getForwardPoolableThread;
			this.getReversePoolableThreadFunction = this::getReversePoolableThread;
			this.getPoolableThreadFunction = this.getForwardPoolableThreadFunction;
			this.name = name;
			this.daemon = Objects.toBoolean(
				IterableObjectHelper.resolveValue(
					ResolveConfig.forNamedKey(Configuration.Key.DEFAULT_DAEMON_FLAG_VALUE)
					.on(config)
				)
			);
			int availableProcessors = Runtime.getRuntime().availableProcessors();
			int multiplier = 3;
			try {
				maxPoolableThreadCount = Objects.toInt(
					IterableObjectHelper.resolveValue(
						ResolveConfig.forNamedKey(Configuration.Key.MAX_POOLABLE_THREAD_COUNT)
						.on(config)
					)
				);
			} catch (Throwable exc) {
				maxPoolableThreadCount = availableProcessors * multiplier;
			}
			
			if (!(maxPoolableThreadCount >= 0)) {
				throw new IllegalArgumentException("maxPoolableThreadCount must be greater than or equal to zero");
			}
			int maxDetachedThreadCount;
			try {
				maxDetachedThreadCount = Objects.toInt(
					IterableObjectHelper.resolveValue(
						ResolveConfig.forNamedKey(Configuration.Key.MAX_DETACHED_THREAD_COUNT)
						.on(config)
					)
				);
			} catch (Throwable exc) {
				maxDetachedThreadCount = availableProcessors * multiplier;
			}
			if (maxDetachedThreadCount < 0) {
				maxDetachedThreadCount = Integer.MAX_VALUE - maxPoolableThreadCount;
			}
			
			this.runningThreads = ConcurrentHashMap.newKeySet();
			this.poolableSleepingThreads = new Thread.Poolable[maxPoolableThreadCount];
			
			this.inititialMaxThreadCount = this.maxThreadCount = maxPoolableThreadCount + maxDetachedThreadCount;
			this.poolableThreadRequestTimeout = Objects.toLong(
				IterableObjectHelper.resolveValue(
					ResolveConfig.forNamedKey(Configuration.Key.POOLABLE_THREAD_REQUEST_TIMEOUT)
					.on(config)
				)
			);
			this.elapsedTimeThresholdFromLastIncreaseForGradualDecreasingOfMaxDetachedThreadsCount =
				Objects.toLong(IterableObjectHelper.resolveValue(
					ResolveConfig.forNamedKey(
						Configuration.Key.MAX_DETACHED_THREAD_COUNT_ELAPSED_TIME_THRESHOLD_FROM_LAST_INCREASE_FOR_GRADUAL_DECREASING_TO_INITIAL_VALUE
					)
					.on(config)
				)
			);
			try {
				this.maxDetachedThreadCountIncreasingStep = Objects.toInt(
					IterableObjectHelper.resolveValue(
						ResolveConfig.forNamedKey(Configuration.Key.MAX_DETACHED_THREAD_COUNT_INCREASING_STEP)
						.on(config)
					)
				);
			} catch (Throwable exc) {
				maxDetachedThreadCountIncreasingStep = availableProcessors;
			}
			if (maxDetachedThreadCountIncreasingStep < 1) {
				poolableThreadRequestTimeout = 0;
				config.put(Configuration.Key.POOLABLE_THREAD_REQUEST_TIMEOUT, poolableThreadRequestTimeout);
			}
			this.timeOfLastIncreaseOfMaxDetachedThreadCount = Long.MAX_VALUE;
		}
		
		public static Supplier create(
			String name,
			Map<Object, Object> config,
			boolean undestroyable
		) {
			if (undestroyable) {
				return new Supplier(name, config) {
					StackTraceElement[] stackTraceOnCreation = Thread.currentThread().getStackTrace();
					@Override
					public void shutDownAll() {
						if (Methods.retrieveExternalCallerInfo().getClassName().equals(Methods.retrieveExternalCallerInfo(stackTraceOnCreation).getClassName())) {
							super.shutDownAll();
						}
					}
				};
			} else {
				return new Supplier(name, config);
			}
		}

		public Thread getOrCreate(String name) {
			Thread thread = getOrCreate();
			thread.setName(name);
			return thread;
		}

		public final Thread getOrCreate() {
			return getOrCreate(1);
		}

		public final Thread getOrCreate(int requestCount) {
			return getOrCreate(requestCount, requestCount);
		}

		final Thread getOrCreate(int initialValue, int requestCount) {
			Thread thread = getPoolableThreadFunction.get();
			if (thread != null) {
				return thread;
			}
			if (requestCount > 0 && poolableThreadCount >= maxPoolableThreadCount && threadCount >= maxThreadCount) {
				synchronized(poolableSleepingThreads) {
					try {
						if ((thread = getPoolableThreadFunction.get()) != null) {
							return thread;
						}
						if (poolableThreadCount >= maxPoolableThreadCount && threadCount >= maxThreadCount) {
							//This block of code is used to avoid performance degradation
							long startWaitTime = System.currentTimeMillis();
							poolableSleepingThreads.wait(poolableThreadRequestTimeout);
							if (maxDetachedThreadCountIncreasingStep < 1) {
								return getOrCreate(initialValue, requestCount);
							}
							long endWaitTime = System.currentTimeMillis();
							long waitElapsedTime = endWaitTime - startWaitTime;
							if (waitElapsedTime < poolableThreadRequestTimeout) {
								if (inititialMaxThreadCount < maxThreadCount &&
									(System.currentTimeMillis() - timeOfLastIncreaseOfMaxDetachedThreadCount) >
										elapsedTimeThresholdFromLastIncreaseForGradualDecreasingOfMaxDetachedThreadsCount
								) {
									maxThreadCount -= (maxDetachedThreadCountIncreasingStep / 2);
									ManagedLoggerRepository.logInfo(
										getClass()::getName,
										"{}: decreasing maxThreadCount to {}",
										java.lang.Thread.currentThread(), maxThreadCount
									);
									timeOfLastIncreaseOfMaxDetachedThreadCount = Long.MAX_VALUE;
								}
								return getOrCreate(initialValue, requestCount);
							} else {
								timeOfLastIncreaseOfMaxDetachedThreadCount = System.currentTimeMillis();
								maxThreadCount += maxDetachedThreadCountIncreasingStep;
								ManagedLoggerRepository.logInfo(
									getClass()::getName,
									"{} waited for {}ms: maxThreadCount will be temporarily increased to {} to avoid performance degradation",
									java.lang.Thread.currentThread(), waitElapsedTime, maxThreadCount
								);
								return getOrCreate(initialValue, --requestCount);
							}
						}
					} catch (InterruptedException exc) {
						ManagedLoggerRepository.logError(Thread.class::getName, exc);
					}
				}
			} else if (poolableThreadCount >= maxPoolableThreadCount) {
				if (threadCount < maxThreadCount) {
					return createDetachedThread();
				} else {
					return getOrCreate(initialValue, initialValue);
				}
			}
			synchronized(poolableSleepingThreads) {
				if (poolableThreadCount >= maxPoolableThreadCount) {
					return getOrCreate(initialValue, requestCount);
				}
				return createPoolableThread();
			}
		}

		Thread createPoolableThread() {
			++poolableThreadCount;
			++threadCount;
			return new Poolable(this, ++threadNumberSupplier);
		}

		Thread createDetachedThread() {
			++threadCount;
			return new Detached(this, ++threadNumberSupplier);
		}
		
		private Integer addForwardPoolableSleepingThread(Thread.Poolable thread) {
			addPoolableSleepingThreadFunction = addReversePoolableSleepingThreadFunction;
			for (int index = 0; index < poolableSleepingThreads.length; index++) {
				if (poolableSleepingThreads[index] == null && addPoolableSleepingThread(thread, index)) {
					return index;
				}
			}
			return null;		
		}
		
		private Integer addReversePoolableSleepingThread(Thread.Poolable thread) {
			addPoolableSleepingThreadFunction = addForwardPoolableSleepingThreadFunction;
			for (int index = poolableSleepingThreads.length - 1; index >= 0; index--) {
				if (poolableSleepingThreads[index] == null && addPoolableSleepingThread(thread, index)) {
					return index;
				}
			}
			return null;
		}
		
		private boolean addPoolableSleepingThread(Thread.Poolable thread, int index) {
			try (Mutex mutex = Synchronizer.getMutex(getOperationId(accessForIndexToPoolableSleepingThreadsOperationId+ "[" + index + "]"))) {
				synchronized(mutex) {
					if (poolableSleepingThreads[index] == null) {
						poolableSleepingThreads[index] = thread;
						return true;
					}
				}
				return false;
			}
		}
		
		private Thread.Poolable getForwardPoolableThread() {
			this.getPoolableThreadFunction = this.getReversePoolableThreadFunction;
			for (int index = 0; index < poolableSleepingThreads.length;	index++) {
				if (poolableSleepingThreads[index] != null) {
					try (Mutex mutex = Synchronizer.getMutex(getOperationId(accessForIndexToPoolableSleepingThreadsOperationId+ "[" + index + "]"))) {
						synchronized(mutex) {
							if (poolableSleepingThreads[index] != null) {
								Thread.Poolable thread = poolableSleepingThreads[index];
								poolableSleepingThreads[index] = null;
								return thread;
							}
						}
					}
				}
			}
			return null;
		}

		private Thread.Poolable getReversePoolableThread() {
			this.getPoolableThreadFunction = this.getForwardPoolableThreadFunction;
			for (int index = poolableSleepingThreads.length - 1; index >= 0; index--) {
				if (poolableSleepingThreads[index] != null) {
					try (Mutex mutex = Synchronizer.getMutex(getOperationId(accessForIndexToPoolableSleepingThreadsOperationId+ "[" + index + "]"))) {
						synchronized(mutex) {
							if (poolableSleepingThreads[index] != null) {
								Thread.Poolable thread = poolableSleepingThreads[index];
								poolableSleepingThreads[index] = null;
								return thread;
							}
						}
					}
				}
			}
			return null;
		}
		
		private boolean removePoolableSleepingThread(Thread.Poolable thread) {
			for (int index = 0; index < poolableSleepingThreads.length; index++) {
				if (poolableSleepingThreads[index] == thread) {
					try (Mutex mutex = Synchronizer.getMutex(getOperationId(accessForIndexToPoolableSleepingThreadsOperationId+ "[" + index + "]"))) {
						synchronized(mutex) {
							if (poolableSleepingThreads[index] == thread) {
								poolableSleepingThreads[index] = null;
								return true;
							}
						}
					}
				}
			}
			return false;
		}
		
		public void shutDownAllPoolableSleeping() {
			for (Thread thread : poolableSleepingThreads) {
				if (thread != null) {
					thread.shutDown();
				}
			}
		}

		public void shutDownAll() {
			Iterator<Thread> itr = runningThreads.iterator();
			while (itr.hasNext()) {
				itr.next().shutDown();
			}
			shutDownAllPoolableSleeping();
		}
		
		public int getPoolableThreadCount() {
			return poolableThreadCount;
		}
		
		public int getDetachedThreadCount() {
			return threadCount - poolableThreadCount;
		}
		
		public int getThreadCount() {
			return threadCount;
		}
		
		public int getPoolableSleepingThreadCount() {
			int count = 0;
			for (Thread thread : poolableSleepingThreads) {
				if (thread != null) {
					count++;
				}
			}
			return count;
		}
		
		public int getRunningThreadCount() {
			return runningThreads.size();
		}
		
		public int getInititialMaxThreadCount() {
			return inititialMaxThreadCount;
		}
		
		public int getMaxDetachedThreadCountIncreasingStep() {
			return maxDetachedThreadCountIncreasingStep;
		}
		
		public int getCountOfThreadsThatCanBeSupplied() {
			if (maxDetachedThreadCountIncreasingStep > 0) {
				return Integer.MAX_VALUE - runningThreads.size();
			}
			return maxThreadCount - runningThreads.size();
		}
		
		public void printStatus() {
			int threadCount = this.threadCount;
			int runningThreadCount = runningThreads.size();
			int poolableThreadCount = this.poolableThreadCount;
			int poolableSleepingThreadCount = getPoolableSleepingThreadCount();
			int detachedThreadCount = threadCount - poolableThreadCount;
			ManagedLoggerRepository.logInfo(
				getClass()::getName,
				"\n" + 
				"\tThread count: {}" +
				"\tRunning threads: {}\n" + 
				"\tPoolable threads: {}\n" + 
				"\tPoolable running threads: {}\n" + 
				"\tPoolable sleeping threads: {}\n" +
				"\tDetached threads: {}\n",
				threadCount,
				runningThreadCount,
				poolableThreadCount,
				poolableThreadCount - poolableSleepingThreadCount,
				poolableSleepingThreadCount,
				detachedThreadCount
			);
		}

	}
	
	public static class Holder implements Closeable {
		private Supplier threadSupplier;
		private Map<String, Thread> threads;

		private Holder() {
			this(org.burningwave.core.assembler.StaticComponentContainer.ThreadSupplier);
		}
		
		private Holder(Thread.Supplier threadSupplier) {
			this.threadSupplier = threadSupplier;
			this.threads = new ConcurrentHashMap<>();
		}
		
		public static Holder create(
			Thread.Supplier supplier,
			boolean undestroyable
		) {
			if (undestroyable) {
				return new Holder(supplier) {
					StackTraceElement[] stackTraceOnCreation = Thread.currentThread().getStackTrace();
					@Override
					public void close() {
						if (Methods.retrieveExternalCallerInfo().getClassName().equals(Methods.retrieveExternalCallerInfo(stackTraceOnCreation).getClassName())) {
							super.close();
						}
					}
				};
			} else {
				return new Holder(supplier);
			}
		}

		public String startLooping(boolean isDaemon, int priority, Consumer<Thread> executable) {
			return start(null, true, isDaemon, priority, executable).getName();
		}

		public String start(boolean isDaemon, int priority, Consumer<Thread> executable) {
			return start(null, false, isDaemon, priority, executable).getName();
		}

		public void startLooping(String threadName, boolean isDaemon, int priority, Consumer<Thread> executable) {
			start(threadName, true, isDaemon, priority, executable);
		}

		public void start(String threadName, boolean isDaemon, int priority, Consumer<Thread> executable) {
			start(threadName, false, isDaemon, priority, executable);
		}

		private Thread start(String threadName, boolean isLooper, boolean isDaemon, int priority, Consumer<Thread> executable) {
			return Synchronizer.execute(threadName, () -> {
				Thread thr = threads.get(threadName);
				if (thr != null) {
					stop(threadName);
				}
				thr = threadSupplier.createDetachedThread().setExecutable(thread -> {
					try {
						executable.accept(thread);
					} catch (Throwable exc) {
						ManagedLoggerRepository.logError(getClass()::getName, exc);
					}
				}, isLooper);
				if (threadName != null) {
					thr.setName(threadName);
				}
				thr.setPriority(priority);
				thr.setDaemon(isDaemon);
				threads.put(threadName, thr);
				thr.start();
				return thr;
			});
		}

		public void stop(String threadName) {
			stop(threadName, false);
		}

		public void stop(String threadName, boolean waitThreadToFinish) {
			Synchronizer.execute(threadName, () -> {
				Thread thr = threads.get(threadName);
				if (thr == null) {
					return;
				}
				threads.remove(threadName);
				thr.shutDown(waitThreadToFinish);
				thr = null;
			});
		}

		public void join(String threadName) {
			Thread thr = threads.get(threadName);
			if (thr != null) {
				try {
					thr.join();
				} catch (InterruptedException exc) {
					ManagedLoggerRepository.logError(getClass()::getName, exc);
				}
			}
		}

		public boolean isAlive(String threadName) {
			Thread thr = threads.get(threadName);
			if (thr != null) {
				return thr.running;
			}
			return false;
		}

		@Override
		public void close() {
			threads.forEach((threadName, thread) -> {
				thread.shutDown();
				threads.remove(threadName);
			});
			threads = null;
			threadSupplier = null;
		}
	}
	
}