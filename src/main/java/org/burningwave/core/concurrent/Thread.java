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
import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggersRepository;
import static org.burningwave.core.assembler.StaticComponentContainer.Methods;
import static org.burningwave.core.assembler.StaticComponentContainer.Objects;
import static org.burningwave.core.assembler.StaticComponentContainer.Synchronizer;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.burningwave.core.Closeable;
import org.burningwave.core.Identifiable;
import org.burningwave.core.ManagedLogger;
import org.burningwave.core.function.ThrowingConsumer;
import org.burningwave.core.iterable.IterableObjectHelper.ResolveConfig;

public abstract class Thread extends java.lang.Thread implements ManagedLogger {
	
	ThrowingConsumer<Thread, ? extends Throwable> originalExecutable;
	ThrowingConsumer<Thread, ? extends Throwable> executable;
	boolean looper;
	boolean looping;
	private long number;
	boolean alive;
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

	public synchronized void waitFor(long millis) {
		try {
			wait(millis);
		} catch (InterruptedException exc) {
			ManagedLoggersRepository.logError(() -> this.getClass().getName(), exc);
		}
	}

	void shutDown() {
		shutDown(false);
	}

	void shutDown(boolean waitForFinish) {
		alive = false;
		stopLooping();
		if (waitForFinish && Thread.currentThread() != this) {
			try {
				join();
			} catch (InterruptedException exc) {
				ManagedLoggersRepository.logError(getClass()::getName, exc);
			}
		}
	}
	
	private static class Poolable extends Thread {
		
		private Poolable(Thread.Supplier supplier, long number) {
			super(supplier, number);
		}
		
		@Override
		public void run() {
			while (alive) {
				synchronized (this) {
					supplier.runningThreads.add(this);
				}
				try {
					executable.accept(this);
				} catch (Throwable exc) {
					ManagedLoggersRepository.logError(() -> this.getClass().getName(), exc);
				}
				try {
					supplier.runningThreads.remove(this);
					executable = null;
					originalExecutable = null;
					if (!alive) {
						continue;
					}
					setIndexedName();
					synchronized (this) {
						if (!supplier.addPoolableSleepingThreadFunction.test(this)) {
							ManagedLoggersRepository.logWarn(
								getClass()::getName,
								"Could not add thread {} to poolable sleeping container: it will be shutted down",
								Objects.getId(this)
							);
							this.shutDown();
							continue;
						}
						supplier.notifyToPoolableSleepingThreadCollectionWaiter();
						wait();
					}
				} catch (InterruptedException exc) {
					ManagedLoggersRepository.logError(getClass()::getName, exc);
					this.shutDown();
				}
			}
			removePermanently();
			supplier.notifyToPoolableSleepingThreadCollectionWaiter();
			synchronized(this) {
				notifyAll();
			}
		}

		@Override
		public void interrupt() {
			shutDown();
			removePermanently();
			try {
				super.interrupt();
			} catch (Throwable exc) {
				ManagedLoggersRepository.logError(getClass()::getName, "Exception occurred", exc);
			}
			supplier.notifyToPoolableSleepingThreadCollectionWaiter();
			synchronized(this) {
				notifyAll();
			}
		}

		private void removePermanently () {
			synchronized (this) {
				if (supplier.runningThreads.remove(this)) {
					--supplier.threadsCount;
					--supplier.poolableThreadsCount;
				}
			}
			if (supplier.removePoolableSleepingThread(this)) {
				--supplier.threadsCount;
				--supplier.poolableThreadsCount;
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
				ManagedLoggersRepository.logError(() -> this.getClass().getName(), exc);
			}
			synchronized (this) {
				if (supplier.runningThreads.remove(this)) {
					--supplier.threadsCount;
				}
			}
			supplier.notifyToPoolableSleepingThreadCollectionWaiter();
			synchronized(this) {
				notifyAll();
			}
		}

		@Override
		public void interrupt() {
			shutDown();
			synchronized (this) {
				if (supplier.runningThreads.remove(this)) {
					--supplier.threadsCount;
				}
			}
			try {
				super.interrupt();
			} catch (Throwable exc) {
				ManagedLoggersRepository.logError(getClass()::getName, "Exception occurred", exc);
			}
			supplier.notifyToPoolableSleepingThreadCollectionWaiter();
			synchronized(this) {
				notifyAll();
			}
		}
	}
	
	
	public static class Supplier implements Identifiable {
		public static class Configuration {
			public static class Key {
				public static final String MAX_POOLABLE_THREADS_COUNT = "thread-supplier.max-poolable-threads-count";
				public static final String MAX_DETACHED_THREADS_COUNT = "thread-supplier.max-detached-threads-count";
				public static final String DEFAULT_DAEMON_FLAG_VALUE = "thread-supplier.default-daemon-flag-value";
				public static final String POOLABLE_THREAD_REQUEST_TIMEOUT = "thread-supplier.poolable-thread-request-timeout";
				public static final String MAX_DETACHED_THREADS_COUNT_ELAPSED_TIME_THRESHOLD_FROM_LAST_INCREASE_FOR_GRADUAL_DECREASING_TO_INITIAL_VALUE = "thread-supplier.max-detached-threads-count.elapsed-time-threshold-from-last-increase-for-gradual-decreasing-to-initial-value";
				public static final String MAX_DETACHED_THREADS_COUNT_INCREASING_STEP = "thread-supplier.max-detached-threads-count.increasing-step";

			}

			public final static Map<String, Object> DEFAULT_VALUES;

			static {
				Map<String, Object> defaultValues =  new HashMap<>();

				defaultValues.put(
					Key.MAX_POOLABLE_THREADS_COUNT,
					"autodetect"
				);

				defaultValues.put(
					Key.MAX_DETACHED_THREADS_COUNT,
					"autodetect"
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
					Key.MAX_DETACHED_THREADS_COUNT_ELAPSED_TIME_THRESHOLD_FROM_LAST_INCREASE_FOR_GRADUAL_DECREASING_TO_INITIAL_VALUE,
					30000
				);

				defaultValues.put(
					Key.MAX_DETACHED_THREADS_COUNT_INCREASING_STEP,
					8
				);

				DEFAULT_VALUES = Collections.unmodifiableMap(defaultValues);
			}
		}
		
		private static long threadNumberSupplier;
		
		private String name;
		private volatile long threadsCount;
		private volatile long poolableThreadsCount;
		private int maxPoolableThreadsCount;
		private int inititialMaxThreadsCount;
		private int maxThreadsCount;
		private int maxDetachedThreadsCountIncreasingStep;
		private long poolableThreadRequestTimeout;
		private long elapsedTimeThresholdFromLastIncreaseForGradualDecreasingOfMaxDetachedThreadsCount;
		private Collection<Thread> runningThreads;
		//Changed poolable thread container to array (since 12.15.2, the previous version is 12.15.1)
		private Thread[] poolableSleepingThreads;
		private Object[] poolableSleepingThreadMutexes;
		private Thread poolableSleepingThreadCollectionNotifier;
		private long timeOfLastIncreaseOfMaxDetachedThreadsCount;
		private boolean daemon;
		private Predicate<Thread> addForwardPoolableSleepingThreadFunction;
		private Predicate<Thread> addReversePoolableSleepingThreadFunction;
		private Predicate<Thread> addPoolableSleepingThreadFunction;
		private java.util.function.Supplier<Thread> getForwardPoolableThreadFunction;
		private java.util.function.Supplier<Thread> getReversePoolableThreadFunction;
		private java.util.function.Supplier<Thread> getPoolableThreadFunction;
		
		Supplier (
			String name,
			Properties config
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

			int maxPoolableThreadsCountAsInt;
			double multiplier = 3;
			try {
				maxPoolableThreadsCountAsInt = Objects.toInt(
					IterableObjectHelper.resolveValue(
						ResolveConfig.forNamedKey(Configuration.Key.MAX_POOLABLE_THREADS_COUNT)
						.on(config)
					)
				);
			} catch (Throwable exc) {
				maxPoolableThreadsCountAsInt = (int)(Runtime.getRuntime().availableProcessors() * multiplier);
			}
			
			if (!(maxPoolableThreadsCountAsInt >= 0)) {
				throw new IllegalArgumentException("maxPoolableThreadsCount must be greater than or equal to zero");
			}
			
			int maxDetachedThreadsCountAsInt;
			try {
				maxDetachedThreadsCountAsInt = Objects.toInt(
					IterableObjectHelper.resolveValue(
						ResolveConfig.forNamedKey(Configuration.Key.MAX_DETACHED_THREADS_COUNT)
						.on(config)
					)
				);
			} catch (Throwable exc) {
				maxDetachedThreadsCountAsInt =
					((int)(Runtime.getRuntime().availableProcessors() * 3 * multiplier)) -
					((int)(Runtime.getRuntime().availableProcessors() * multiplier));
			}
			if (maxDetachedThreadsCountAsInt < 0) {
				maxDetachedThreadsCountAsInt = Integer.MAX_VALUE - maxPoolableThreadsCountAsInt;
			}
			this.maxPoolableThreadsCount = maxPoolableThreadsCountAsInt;
			
			this.runningThreads = ConcurrentHashMap.newKeySet();
			this.poolableSleepingThreads = new Thread[maxPoolableThreadsCount];
			this.poolableSleepingThreadMutexes = new Object[this.poolableSleepingThreads.length];
			for(int i = 0; i < poolableSleepingThreadMutexes.length; i++) {
				poolableSleepingThreadMutexes[i] = new Object();
			}
			
			this.inititialMaxThreadsCount = this.maxThreadsCount = maxPoolableThreadsCountAsInt + maxDetachedThreadsCountAsInt;
			this.poolableThreadRequestTimeout = Objects.toLong(
				IterableObjectHelper.resolveValue(
					ResolveConfig.forNamedKey(Configuration.Key.POOLABLE_THREAD_REQUEST_TIMEOUT)
					.on(config)
				)
			);
			this.elapsedTimeThresholdFromLastIncreaseForGradualDecreasingOfMaxDetachedThreadsCount =
				Objects.toLong(IterableObjectHelper.resolveValue(
					ResolveConfig.forNamedKey(
						Configuration.Key.MAX_DETACHED_THREADS_COUNT_ELAPSED_TIME_THRESHOLD_FROM_LAST_INCREASE_FOR_GRADUAL_DECREASING_TO_INITIAL_VALUE
					)
					.on(config)
				)
			);
			this.maxDetachedThreadsCountIncreasingStep = Objects.toInt(
				IterableObjectHelper.resolveValue(
					ResolveConfig.forNamedKey(Configuration.Key.MAX_DETACHED_THREADS_COUNT_INCREASING_STEP)
					.on(config)
				)
			);
			if (maxDetachedThreadsCountIncreasingStep < 1) {
				poolableThreadRequestTimeout = 0;
				config.put(Configuration.Key.POOLABLE_THREAD_REQUEST_TIMEOUT, poolableThreadRequestTimeout);
			}
			this.timeOfLastIncreaseOfMaxDetachedThreadsCount = Long.MAX_VALUE;
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
			if (requestCount > 0 && poolableThreadsCount >= maxPoolableThreadsCount && threadsCount >= maxThreadsCount) {
				synchronized (poolableSleepingThreads) {
					try {
						if ((thread = getPoolableThreadFunction.get()) != null) {
							return thread;
						}
						if (poolableThreadsCount >= maxPoolableThreadsCount && threadsCount >= maxThreadsCount) {
							//This block of code is for preventing dead locks
							long startWaitTime = System.currentTimeMillis();
							poolableSleepingThreads.wait(poolableThreadRequestTimeout);
							if (maxDetachedThreadsCountIncreasingStep < 1) {
								return getOrCreate(initialValue, requestCount);
							}
							long endWaitTime = System.currentTimeMillis();
							long waitElapsedTime = endWaitTime - startWaitTime;
							if (waitElapsedTime < poolableThreadRequestTimeout) {
								if (inititialMaxThreadsCount < maxThreadsCount &&
									(System.currentTimeMillis() - timeOfLastIncreaseOfMaxDetachedThreadsCount) >
										elapsedTimeThresholdFromLastIncreaseForGradualDecreasingOfMaxDetachedThreadsCount
								) {
									maxThreadsCount -= (maxDetachedThreadsCountIncreasingStep / 2);
									ManagedLoggersRepository.logInfo(
										() -> this.getClass().getName(),
										"{}: decreasing maxTemporarilyThreadsCount to {}",
										java.lang.Thread.currentThread(), (maxThreadsCount - maxPoolableThreadsCount)
									);
									timeOfLastIncreaseOfMaxDetachedThreadsCount = Long.MAX_VALUE;
								}
								return getOrCreate(initialValue, requestCount);
							} else {
								timeOfLastIncreaseOfMaxDetachedThreadsCount = System.currentTimeMillis();
								maxThreadsCount += maxDetachedThreadsCountIncreasingStep;
								ManagedLoggersRepository.logInfo(
									() -> this.getClass().getName(),
									"{} waited for {}ms: maxTemporarilyThreadsCount will be temporarily increased to {} for preventing dead lock",
									java.lang.Thread.currentThread(), waitElapsedTime, (maxThreadsCount - maxPoolableThreadsCount)
								);
								return getOrCreate(initialValue, --requestCount);
							}
						}
					} catch (InterruptedException exc) {
						ManagedLoggersRepository.logError(() -> Thread.class.getName(), exc);
					}
				}
			} else if (poolableThreadsCount >= maxPoolableThreadsCount) {
				if (threadsCount < maxThreadsCount) {
					return createDetachedThread();
				} else {
					return getOrCreate(initialValue, initialValue);
				}
			}
			synchronized (poolableSleepingThreads) {
				if (poolableThreadsCount >= maxPoolableThreadsCount) {
					return getOrCreate(initialValue, requestCount);
				}
				return createPoolableThread();
			}
		}

		Thread createPoolableThread() {
			++poolableThreadsCount;
			++threadsCount;
			return new Poolable(this, ++threadNumberSupplier);
		}

		Thread createDetachedThread() {
			++threadsCount;
			return new Detached(this, ++threadNumberSupplier);
		}
		
		private boolean addForwardPoolableSleepingThread(Thread thread) {
			addPoolableSleepingThreadFunction = addReversePoolableSleepingThreadFunction;
			for (int i = 0; i < poolableSleepingThreads.length; i++) {
				if (poolableSleepingThreads[i] == null && addPoolableSleepingThread(thread, i)) {
					return true;
				}
			}
			return false;		
		}
		
		private boolean addReversePoolableSleepingThread(Thread thread) {
			addPoolableSleepingThreadFunction = addForwardPoolableSleepingThreadFunction;
			for (int i = poolableSleepingThreads.length - 1; i >= 0; i--) {
				if (poolableSleepingThreads[i] == null && addPoolableSleepingThread(thread, i)) {
					return true;
				}
			}
			return false;
		}
		
		private boolean addPoolableSleepingThread(Thread thread, int currentIndex) {
			synchronized (poolableSleepingThreadMutexes[currentIndex]) {
				if (poolableSleepingThreads[currentIndex] == null) {
					poolableSleepingThreads[currentIndex] = thread;
					return true;
				}
				return false;
			}
		}
		
		private Thread getForwardPoolableThread() {
			this.getPoolableThreadFunction = this.getReversePoolableThreadFunction;
			for (int i = 0; i < poolableSleepingThreads.length; i++) {
				Thread thread = poolableSleepingThreads[i];
				if (thread == null) {
					continue;
				}
				if (poolableSleepingThreads[i] == thread) {
					synchronized (thread) {
						if (poolableSleepingThreads[i] == thread) {
							poolableSleepingThreads[i] = null;
							return thread;
						}
					}
				}
			}
			return null;
		}
		
		private Thread getReversePoolableThread() {
			this.getPoolableThreadFunction = this.getForwardPoolableThreadFunction;
			for (int i = poolableSleepingThreads.length - 1; i >= 0; i--) {
				Thread thread = poolableSleepingThreads[i];
				if (thread == null) {
					continue;
				}
				if (poolableSleepingThreads[i] == thread) {
					synchronized (thread) {
						if (poolableSleepingThreads[i] == thread) {
							poolableSleepingThreads[i] = null;
							return thread;
						}
					}
				}
			}
			return null;
		}
		
		private boolean removePoolableSleepingThread(Thread thread) {
			for (int i = 0; i < poolableSleepingThreads.length; i++) {
				if (poolableSleepingThreads[i] == thread) {
					synchronized (thread) {
						if (poolableSleepingThreads[i] == thread) {
							poolableSleepingThreads[i] = null;
							return true;
						}
					}
				}
			}
			return false;
		}
		
		private int getPoolableSleepingThreadCount() {
			int count = 0;
			for (Thread thread : poolableSleepingThreads) {
				if (thread != null) {
					count++;
				}
			}
			return count;
		}
		
		public void printStatus() {
			ManagedLoggersRepository.logInfo(
				getClass()::getName,
				"\n\tRunning threads: {}\n\tPoolable sleeping threads: {}\n\tThreads count: {}",
				runningThreads.size(), getPoolableSleepingThreadCount(), threadsCount
			);
		}

		private void notifyToPoolableSleepingThreadCollectionWaiter() {
			try {
				synchronized (poolableSleepingThreadCollectionNotifier) {
					poolableSleepingThreadCollectionNotifier.notify();
				}
			} catch (NullPointerException exception) {
				//Deferred initialization
				Synchronizer.execute(getOperationId("createPoolableSleepingThreadCollectionNotifier"), () -> {
					if (this.poolableSleepingThreadCollectionNotifier == null) {
						Thread poolableSleepingThreadCollectionNotifier = createDetachedThread().setExecutable(thread -> {
							try {
								synchronized (thread) {
									thread.wait();
								}
								synchronized (poolableSleepingThreads) {
									poolableSleepingThreads.notifyAll();
								}
							} catch (Throwable exc) {
								ManagedLoggersRepository.logError(getClass()::getName, exc);
							}
						}, true);
						poolableSleepingThreadCollectionNotifier.setName(name + " - Notifier");
						poolableSleepingThreadCollectionNotifier.setPriority(Thread.MAX_PRIORITY);
						poolableSleepingThreadCollectionNotifier.setDaemon(daemon);
						poolableSleepingThreadCollectionNotifier.start();
						this.poolableSleepingThreadCollectionNotifier = poolableSleepingThreadCollectionNotifier;
					}
				});
				notifyToPoolableSleepingThreadCollectionWaiter();
			}
		}

		public void shutDownAllPoolableSleeping() {
			for (Thread thread : poolableSleepingThreads) {
				if (thread != null) {
					thread.shutDown();
				}
			}
		}

		public void shutDownAll() {
			shutDownAllPoolableSleeping();
			Iterator<Thread> itr = runningThreads.iterator();
			while (itr.hasNext()) {
				itr.next().shutDown();
			}
			Synchronizer.execute(getOperationId("createPoolableSleepingThreadCollectionNotifier"), () -> {
				Thread poolableSleepingThreadCollectionNotifier = this.poolableSleepingThreadCollectionNotifier;
				this.poolableSleepingThreadCollectionNotifier = null;
				synchronized(poolableSleepingThreadCollectionNotifier) {
					poolableSleepingThreadCollectionNotifier.notify();
				}
				poolableSleepingThreadCollectionNotifier.shutDown();
			});
			
		}

		public static Supplier create(
			String name,
			java.util.Properties config,
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
	}

	public static class Holder implements Closeable, ManagedLogger {
		private Supplier threadSupplier;
		private Map<String, Thread> threads;

		public Holder() {
			this(org.burningwave.core.assembler.StaticComponentContainer.ThreadSupplier);
		}

		public Holder(Supplier threadSupplier) {
			this.threadSupplier = threadSupplier;
			this.threads = new ConcurrentHashMap<>();
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
						ManagedLoggersRepository.logError(getClass()::getName, exc);
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
					ManagedLoggersRepository.logError(getClass()::getName, exc);
				}
			}
		}

		public boolean isAlive(String threadName) {
			Thread thr = threads.get(threadName);
			if (thr != null) {
				return thr.alive;
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