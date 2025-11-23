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

import static org.burningwave.core.assembler.StaticComponentContainer.Driver;
import static org.burningwave.core.assembler.StaticComponentContainer.IterableObjectHelper;
import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository;
import static org.burningwave.core.assembler.StaticComponentContainer.Methods;
import static org.burningwave.core.assembler.StaticComponentContainer.Objects;
import static org.burningwave.core.assembler.StaticComponentContainer.Strings;
import static org.burningwave.core.assembler.StaticComponentContainer.Synchronizer;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import org.burningwave.core.Closeable;
import org.burningwave.core.Identifiable;
import org.burningwave.core.StringUtils;
import org.burningwave.core.function.ThrowingConsumer;
import org.burningwave.core.iterable.IterableObjectHelper.ResolveConfig;

@SuppressWarnings("deprecation")
public abstract class Thread extends java.lang.Thread {
	private final static ThrowingConsumer<Thread, ? extends Throwable> nullExecutableNotifier;

	static {
		nullExecutableNotifier = thread -> {
			ManagedLoggerRepository.logError(thread.getClass()::getName, "Executable is null");
		};
	}

	ThrowingConsumer<Thread, ? extends Throwable> originalExecutable;
	AtomicReference<ThrowingConsumer<Thread, ? extends Throwable>> executableWrapper;
	boolean looper;
	boolean looping;
	Boolean running;
	Supplier supplier;
	String defaultName;
	String typeName;

	private Thread(Supplier threadSupplier, long number) {
		super(threadSupplier.name + " - Executor " + number);
		executableWrapper = new AtomicReference<>();
		this.supplier = threadSupplier;
		typeName = getClass().getSimpleName();
		defaultName = Strings.compile(
			"{} - {} executor {}",
			supplier.name,
			typeName,
			number
		);
		setIndexedName();
		setDaemon(threadSupplier.daemon);
		setPriority(supplier.defaultThreadPriority);
	}

	abstract void removePermanently();

	abstract void startRunning();

	void callStart() {
		super.start();
	}

	public void setIndexedName() {
		setIndexedName(null);
	}

	public void setIndexedName(String prefix) {
		setName(Optional.ofNullable(prefix).orElse(defaultName));
	}

	public Thread setExecutable(ThrowingConsumer<Thread, ? extends Throwable> executable) {
		return setExecutable(executable, false);
	}

	public Thread setExecutable(ThrowingConsumer<Thread, ? extends Throwable> executable, boolean isLooper) {
		this.originalExecutable = executable;
		this.looper = isLooper;
		return this;
	}

	public boolean isDetached() {
		return this instanceof Detached;
	}

	public boolean isPoolable() {
		return this instanceof Poolable;
	}

	@Override
	public void start() {
		if (this.originalExecutable == null) {
			this.originalExecutable = nullExecutableNotifier;
			this.looper = false;
			startExecution();
			throw new NullExecutableException(Strings.compile("Executable of {} is null", this));
		}
		startExecution();
	}

	void startExecution() {
		if (!looper) {
			executableWrapper.set(originalExecutable);
		} else {
			this.executableWrapper.set(thread -> {
				looping = true;
				while (looping) {
					originalExecutable.accept(this);
				}
			});
		}
		try {
			supplier.runningAndWaitingForRunThreads.put(this, this);
			startRunning();
		} catch (Throwable exc) {
			supplier.runningAndWaitingForRunThreads.remove(this);
			throw exc;
		}
	}



	public void stopLooping() {
		looping = false;
		synchronized(executableWrapper) {
			executableWrapper.notifyAll();
		}
	}

	public boolean isRunning() {
		return isAlive() && running;
	}

	public boolean isLooping() {
		return looping;
	}

	void shutDown() {
		shutDown(false);
	}

	void shutDown(boolean waitForFinish) {
		running = false;
		stopLooping();
		if (waitForFinish) {
			supplier.joinThread(this);
		}
	}

	@Deprecated/*(since="12.60.0")*/
	public void kill() {
		terminate(thread ->
			Driver.stop(thread),
			"stop"
		);
	}

	@Override
	public void interrupt() {
		terminate(thread -> super.interrupt(), "interrupt");
	}

	void terminate(Consumer<Thread> operation, String operationName) {
		ManagedLoggerRepository.logWarn(
			getClass()::getName,
			"Called {} by {}{}\n\ton {} (executable: {}):{}",
			operationName,
			java.lang.Thread.currentThread(),
			Strings.from(Methods.retrieveExternalCallersInfo(), 2),
			this,
			executableWrapper.get(),
			Strings.from(getStackTrace(), 2)
		);
		shutDown();
		removePermanently();
		java.lang.Thread currentThread = java.lang.Thread.currentThread();
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
		synchronized(executableWrapper) {
			executableWrapper.notifyAll();
		}
		if (this == currentThread) {
			Thread killer = supplier.getOrCreateThread().setExecutable(thread -> {
				operation.accept(this);
			});
			killer.setPriority(currentThread.getPriority());
			killer.start();
		}
	}

	@Override
	public String toString() {
		return Strings.compile(
			"{}{}",
			super.toString(),
			Optional.ofNullable(getState()).map(threadState ->
				Strings.compile("({})", StringUtils.capitalizeFirstCharacter(threadState.name().toLowerCase().replace("_", " ")))
			).orElseGet(() -> "")
		);
	}

	public static long waitFor(long millis) {
		long initialTime = System.currentTimeMillis();
		if (millis > 0) {
			try {
				Object object;
				synchronized(object = new Object()) {
					object.wait(millis);
				}
			} catch (InterruptedException exc) {
				ManagedLoggerRepository.logError(Thread.class::getName, exc);
			}
		}
		return System.currentTimeMillis() - initialTime;
	}

	private static class Poolable extends Thread {

		private Poolable(Thread.Supplier supplier, long number) {
			super(supplier, number);
		}

		@Override
		void startRunning() {
			if (running != null) {
				synchronized (executableWrapper) {
					executableWrapper.notifyAll();
				}
			} else {
				callStart();
			}
		}

		@Override
		public void run() {
			if (this.running != null) {
				throw new IllegalStateException(Strings.compile("{} could not be restarted", this));
			}
			this.running = true;
			while (running) {
				supplier.runningThreads.put(this, this);
				try {
					runExecutable();
					supplier.runningThreads.remove(this);
					//Synchronization needed by the method joinAllRunningThreads
					synchronized(executableWrapper) {
						executableWrapper.set(null);
						executableWrapper.notifyAll();
					}
					originalExecutable = null;
					setIndexedName();
					if (!running) {
						continue;
					}
					synchronized(executableWrapper) {
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
						setPriority(supplier.defaultThreadPriority);
						executableWrapper.wait();
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
			synchronized(executableWrapper) {
				executableWrapper.notifyAll();
			}
		}

		private void runExecutable() {
			ThrowingConsumer<Thread, ? extends Throwable> executable = this.executableWrapper.get();
			try {
				executable.accept(this);
			} catch (Throwable exc) {
				if (executable != null) {
					ManagedLoggerRepository.logError(getClass()::getName, exc);
				} else {
					ManagedLoggerRepository.logError(getClass()::getName, "The thread runned a null executable");
					if (this.executableWrapper.get() == null) {
						int waitTime = 10;
						ManagedLoggerRepository.logWarn(getClass()::getName, "Executable is still null: thread will wait {} milliseconds until next retry", waitTime);
						waitFor(waitTime);
					}
					runExecutable();
				}
			}
		}

		@Override
		void removePermanently () {
			if (supplier.runningThreads.remove(this, this)) {
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
		void startRunning() {
			callStart();
		}

		@Override
		public void run() {
			this.running = true;
			supplier.runningThreads.put(this, this);
			try {
				executableWrapper.get().accept(this);
			} catch (Throwable exc) {
				ManagedLoggerRepository.logError(getClass()::getName, exc);
			}
			executableWrapper.set(null);
			originalExecutable = null;
			removePermanently();
			synchronized(supplier.poolableSleepingThreads) {
				supplier.poolableSleepingThreads.notifyAll();
			}
			synchronized(executableWrapper) {
				executableWrapper.notifyAll();
			}
			running = false;
		}

		@Override
		void removePermanently () {
			if (supplier.runningThreads.remove(this) != null) {
				--supplier.threadCount;
			}
		}
	}


	public static class Supplier implements Identifiable {
		public static abstract class Configuration {
			public static abstract class Key {
				public static final String MAX_POOLABLE_THREAD_COUNT = "thread-supplier.max-poolable-thread-count";
				public static final String MAX_DETACHED_THREAD_COUNT = "thread-supplier.max-detached-thread-count";
				public static final String DEFAULT_DAEMON_FLAG_VALUE = "thread-supplier.default-daemon-flag-value";
				public static final String POOLABLE_THREAD_REQUEST_TIMEOUT = "thread-supplier.poolable-thread-request-timeout";
				public static final String MAX_DETACHED_THREAD_COUNT_ELAPSED_TIME_THRESHOLD_FROM_LAST_INCREASE_FOR_GRADUAL_DECREASING_TO_INITIAL_VALUE =
					"thread-supplier.max-detached-thread-count.elapsed-time-threshold-from-last-increase-for-gradual-decreasing-to-initial-value";
				public static final String MAX_DETACHED_THREAD_COUNT_INCREASING_STEP = "thread-supplier.max-detached-thread-count.increasing-step";
				public static final String DEFAULT_THREAD_PRIORITY = "thread-supplier.default-thread-priority";
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

				defaultValues.put(
					Key.DEFAULT_THREAD_PRIORITY,
					java.lang.Thread.NORM_PRIORITY
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
		private Map<Thread, Thread> runningThreads;
		private Map<Thread, Thread> runningAndWaitingForRunThreads;
		//Changed poolable thread container to array (since 12.15.2, the previous version is 12.15.1)
		private Thread.Poolable[] poolableSleepingThreads;
		private Object[] poolableSleepingThreadMutexes;
		private long timeOfLastIncreaseOfMaxDetachedThreadCount;
		private boolean daemon;
		private Function<Thread.Poolable, Integer> addForwardPoolableSleepingThreadFunction;
		private Function<Thread.Poolable, Integer> addReversePoolableSleepingThreadFunction;
		private Function<Thread.Poolable, Integer> addPoolableSleepingThreadFunction;
		private java.util.function.Supplier<Thread.Poolable> getForwardPoolableThreadFunction;
		private java.util.function.Supplier<Thread.Poolable> getReversePoolableThreadFunction;
		private java.util.function.Supplier<Thread.Poolable> getPoolableThreadFunction;
		private int defaultThreadPriority;

		Supplier (
			String name,
			Map<Object, Object> config
		) {
			this.addForwardPoolableSleepingThreadFunction = this::addForwardPoolableSleepingThread;
			this.addReversePoolableSleepingThreadFunction = this::addReversePoolableSleepingThread;
			this.addPoolableSleepingThreadFunction = addForwardPoolableSleepingThreadFunction;
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
			this.runningAndWaitingForRunThreads = new ConcurrentHashMap<Thread, Thread>();
			this.runningThreads = new ConcurrentHashMap<Thread, Thread>() {

				private static final long serialVersionUID = 3434004576787151770L;

				@Override
				public Thread remove(Object key) {
					runningAndWaitingForRunThreads.remove(key);
					return super.remove(key);
				}

			};

			this.poolableSleepingThreads = new Thread.Poolable[maxPoolableThreadCount];
			this.poolableSleepingThreadMutexes = new Object[poolableSleepingThreads.length];
			for (int i = 0; i < poolableSleepingThreadMutexes.length; i++) {
				poolableSleepingThreadMutexes[i] = new Object();
			}

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
			try {
				this.defaultThreadPriority = Objects.toInt(
					IterableObjectHelper.resolveValue(
						ResolveConfig.forNamedKey(
							Configuration.Key.DEFAULT_THREAD_PRIORITY
						)
						.on(config)
					)
				);
			} catch (Throwable exc) {
				this.defaultThreadPriority = java.lang.Thread.currentThread().getPriority();
			}
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
					public Supplier shutDownAllThreads(boolean joinThreads) {
						String shutDownRequestorClass = Methods.retrieveExternalCallerInfo().getClassName();
						if (shutDownRequestorClass.equals(Methods.retrieveExternalCallerInfo(stackTraceOnCreation).getClassName())) {
							super.shutDownAllThreads(joinThreads);
						} else {
							ManagedLoggerRepository.logWarn(getClass()::getName, "{} is not authorized to shutdown {}", shutDownRequestorClass, this);
						}
						return this;
					}
				};
			} else {
				return new Supplier(name, config);
			}
		}

		public Thread getOrCreatePoolableThread() {
			Thread thread;
			while ((thread = getPoolableThreadFunction.get()) == null) {
				synchronized(poolableSleepingThreads) {
					if ((thread = getPoolableThreadFunction.get()) != null) {
						return thread;
					}
					if (poolableThreadCount >= maxPoolableThreadCount) {
						try {
							poolableSleepingThreads.wait();
						} catch (InterruptedException exc) {
							ManagedLoggerRepository.logError(Thread.class::getName, exc);
						}
						continue;
					}
					return createPoolableThread();
				}
			}
			return thread;
		}

		public Thread getOrCreateThread(String name) {
			Thread thread = getOrCreateThread();
			thread.setName(name);
			return thread;
		}

		public final Thread getOrCreateThread() {
			return getOrCreateThread(1);
		}

		public final Thread getOrCreateThread(int tentativeCount) {
			return getOrCreateThread(tentativeCount, tentativeCount);
		}

		final Thread getOrCreateThread(int initialValue, int tentativeCount) {
			Thread thread = getPoolableThreadFunction.get();
			if (thread != null) {
				return thread;
			}
			if (tentativeCount > 0 && poolableThreadCount >= maxPoolableThreadCount && threadCount >= maxThreadCount) {
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
								return getOrCreateThread(initialValue, tentativeCount);
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
								return getOrCreateThread(initialValue, tentativeCount);
							} else {
								timeOfLastIncreaseOfMaxDetachedThreadCount = System.currentTimeMillis();
								maxThreadCount += maxDetachedThreadCountIncreasingStep;
								ManagedLoggerRepository.logInfo(
									getClass()::getName,
									"{} waited for {}ms: maxThreadCount will be temporarily increased to {} to avoid performance degradation",
									java.lang.Thread.currentThread(), waitElapsedTime, maxThreadCount
								);
								return getOrCreateThread(initialValue, --tentativeCount);
							}
						}
					} catch (InterruptedException exc) {
						ManagedLoggerRepository.logError(getClass()::getName, exc);
					}
				}
			} else if (poolableThreadCount >= maxPoolableThreadCount) {
				if (threadCount < maxThreadCount) {
					return createDetachedThread();
				} else {
					return getOrCreateThread(initialValue, initialValue);
				}
			}
			synchronized(poolableSleepingThreads) {
				if (poolableThreadCount >= maxPoolableThreadCount) {
					return getOrCreateThread(initialValue, tentativeCount);
				}
				return createPoolableThread();
			}
		}

		Thread createPoolableThread() {
			++poolableThreadCount;
			++threadCount;
			return new Poolable(this, ++threadNumberSupplier);
		}

		public Thread createDetachedThread() {
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
			synchronized(poolableSleepingThreadMutexes[index]) {
				if (poolableSleepingThreads[index] == null) {
					poolableSleepingThreads[index] = thread;
					return true;
				}
			}
			return false;
		}

		private Thread.Poolable getForwardPoolableThread() {
			this.getPoolableThreadFunction = this.getReversePoolableThreadFunction;
			for (int index = 0; index < poolableSleepingThreads.length;	index++) {
				if (poolableSleepingThreads[index] != null) {
					synchronized(poolableSleepingThreadMutexes[index]) {
						if (poolableSleepingThreads[index] != null) {
							Thread.Poolable thread = poolableSleepingThreads[index];
							poolableSleepingThreads[index] = null;
							return thread;
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
					synchronized(poolableSleepingThreadMutexes[index]) {
						if (poolableSleepingThreads[index] != null) {
							Thread.Poolable thread = poolableSleepingThreads[index];
							poolableSleepingThreads[index] = null;
							return thread;
						}
					}
				}
			}
			return null;
		}

		private boolean removePoolableSleepingThread(Thread.Poolable thread) {
			for (int index = 0; index < poolableSleepingThreads.length; index++) {
				if (poolableSleepingThreads[index] == thread) {
					synchronized(poolableSleepingThreadMutexes[index]) {
						if (poolableSleepingThreads[index] == thread) {
							poolableSleepingThreads[index] = null;
							return true;
						}
					}
				}
			}
			return false;
		}

		public Supplier shutDownAllPoolableSleepingThreads() {
			return shutDownAllPoolableSleepingThreads(false);
		}

		public Supplier shutDownAllPoolableSleepingThreads(boolean joinThreads) {
			boolean areThereRunningThreads = false;
			for (Thread thread : poolableSleepingThreads) {
				if (thread != null && thread.isRunning()) {
					areThereRunningThreads = true;
					thread.shutDown(joinThreads);
				}
			}
			if (areThereRunningThreads) {
				shutDownAllPoolableSleepingThreads(joinThreads);
			}
			return this;
		}

		public Supplier shutDownAllThreads() {
			return shutDownAllThreads(false);
		}

		public Supplier shutDownAllThreads(boolean joinThreads) {
			Iterator<Thread> itr = runningAndWaitingForRunThreads.keySet().iterator();
			while (itr.hasNext()) {
				itr.next().shutDown(joinThreads);
			}
			shutDownAllPoolableSleepingThreads(joinThreads);
			return this;
		}

		public Supplier joinAllRunningThreads() {
			Iterator<Thread> itr = runningAndWaitingForRunThreads.keySet().iterator();
			while (itr.hasNext()) {
				joinThread(itr.next());
			}
			return this;
		}

		public Thread joinThread(Thread thread) {
			if (java.lang.Thread.currentThread() == thread) {
				ManagedLoggerRepository.logWarn(getClass()::getName, "Join ignored: the current thread could not wait itself");
				return thread;
			}
			while (thread.executableWrapper.get() != null) {
				synchronized(thread.executableWrapper) {
					if (thread.executableWrapper.get() != null) {
						try {
							thread.executableWrapper.wait();
						} catch (InterruptedException exc) {
							ManagedLoggerRepository.logError(getClass()::getName, exc);
						}
					}
				}
			}
			return thread;
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
						String shutDownRequestorClass = Methods.retrieveExternalCallerInfo().getClassName();
						if (shutDownRequestorClass.equals(Methods.retrieveExternalCallerInfo(stackTraceOnCreation).getClassName())) {
							super.close();
						} else {
							ManagedLoggerRepository.logWarn(getClass()::getName, "{} is not authorized to shutdown {}", shutDownRequestorClass, this);
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