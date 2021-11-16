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
package org.burningwave.core.iterable;


import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.burningwave.core.concurrent.QueuedTasksExecutor;
import org.burningwave.core.function.ThrowingBiConsumer;
import org.burningwave.core.function.ThrowingConsumer;
import org.burningwave.core.iterable.Properties.Event;


@SuppressWarnings("unchecked")
public interface IterableObjectHelper {

	public static class Configuration {
		public static class Key {
			public final static String DEFAULT_VALUES_SEPERATOR = "iterable-object-helper.default-values-separator";
			public final static String PARELLEL_ITERATION_APPLICABILITY_MAX_RUNTIME_THREAD_COUNT_THRESHOLD =
				"iterable-object-helper.parallel-iteration.applicability.max-runtime-thread-count-threshold";
			public final static String PARELLEL_ITERATION_APPLICABILITY_DEFAULT_MINIMUM_COLLECTION_SIZE =
				"iterable-object-helper.parallel-iteration.applicability.default-minimum-collection-size";
			public final static String PARELLEL_ITERATION_APPLICABILITY_OUTPUT_COLLECTION_ENABLED_TYPES =
				"iterable-object-helper.parallel-iteration.applicability.output-collection-enabled-types";
		}

		public final static Map<String, Object> DEFAULT_VALUES;

		static {
			Map<String, Object> defaultValues = new HashMap<>();

			defaultValues.put(Key.DEFAULT_VALUES_SEPERATOR, ";");

			defaultValues.put(Key.PARELLEL_ITERATION_APPLICABILITY_MAX_RUNTIME_THREAD_COUNT_THRESHOLD, "autodetect");

			defaultValues.put(Key.PARELLEL_ITERATION_APPLICABILITY_DEFAULT_MINIMUM_COLLECTION_SIZE, 2);
			
			//The semicolons in this value value will be replaced by the method StaticComponentContainer.adjustConfigurationValues
			defaultValues.put(
				Key.PARELLEL_ITERATION_APPLICABILITY_OUTPUT_COLLECTION_ENABLED_TYPES,
				ConcurrentHashMap.class.getName() + "$CollectionView" + ";" +
				CopyOnWriteArrayList.class.getName() + ";" +
				CopyOnWriteArraySet.class.getName() + ";" +
				BlockingQueue.class.getName() + ";" +
				ConcurrentSkipListSet.class.getName() + ";" +
				ConcurrentSkipListMap.class.getName() + "$EntrySet" + ";" +
				ConcurrentSkipListMap.class.getName() + "$KeySet" + ";" +
				ConcurrentSkipListMap.class.getName() + "$Values" + ";"
			);

			DEFAULT_VALUES = Collections.unmodifiableMap(defaultValues);
		}
	}

	public static IterableObjectHelper create(Map<?, ?> config) {
		IterableObjectHelperImpl iterableObjectHelper = new IterableObjectHelperImpl(config);
		iterableObjectHelper.checkAndListenTo(config);
		return iterableObjectHelper;
	}

	public Predicate<Object> getDefaultMinimumCollectionSizeForParallelIterationPredicate();

	public String getDefaultValuesSeparator();

	public <K, V> void processChangeNotification(Properties properties, Event event, K key, V newValue, V previousValue);

	public <K, V> void deepClear(Map<K, V> map);

	public <K, V, E extends Throwable> void deepClear(Map<K, V> map, ThrowingBiConsumer<K, V, E> itemDestroyer) throws E;

	public <V> void deepClear(Collection<V> map);

	public <V, E extends Throwable> void deepClear(Collection<V> map, ThrowingConsumer<V, E> itemDestroyer) throws E;

	public <T> Collection<T> merge(
		Supplier<Collection<T>> baseCollectionSupplier,
		Supplier<Collection<T>> additionalCollectionSupplier,
		Supplier<Collection<T>> defaultCollectionSupplier
	);

	public <T> T getRandom(Collection<T> coll);

	public <T> Stream<T> retrieveStream(Object object);

	public long getSize(Object object);


	public <T> T resolveValue(ResolveConfig.ForNamedKey config);

	public <K, T> T resolveValue(ResolveConfig.ForAllKeysThat<K> config);

	public String resolveStringValue(ResolveConfig.ForNamedKey config);

	public <K> String resolveStringValue(ResolveConfig.ForAllKeysThat<K> config);


	public <T> Collection<T> resolveValues(ResolveConfig.ForNamedKey config);

	public <K, V> Map<K, V> resolveValues(ResolveConfig.ForAllKeysThat<K> config);

	public Collection<String> resolveStringValues(ResolveConfig.ForNamedKey config);

	public <K> Map<K, Collection<String>> resolveStringValues(ResolveConfig.ForAllKeysThat<K> config);


	public Collection<String> getAllPlaceHolders(Map<?, ?> map);

	public Collection<String> getAllPlaceHolders(Map<?, ?> map, Predicate<String> propertyFilter);

	public Collection<String> getAllPlaceHolders(Map<?, ?> map, String propertyName);
	
	public <I, IC, O, OC> OC iterateAndGet(
		IterableObjectHelper.IterationConfig.WithOutputOfCollection<I, IC, O, OC> config
	);
	
	public <I, IC, K, O, OM> OM iterateAndGet(
		IterableObjectHelper.IterationConfig.WithOutputOfMap<I, IC, K, O, OM> config
	);
	
	public <I, IC> void iterate(IterationConfig<I, IC, ?> config);
	
	public <I, IC, O, OC> QueuedTasksExecutor.ProducerTask<OC> createIterateAndGetTask(
		IterableObjectHelper.IterationConfig.WithOutputOfCollection<I, IC, O, OC> config
	);
	
	public <I, IC, K, O, OM> QueuedTasksExecutor.ProducerTask<OM> createIterateAndGetTask(
		IterableObjectHelper.IterationConfig.WithOutputOfMap<I, IC, K, O, OM> config
	);
	
	public <I, IC> QueuedTasksExecutor.Task createIterateTask(IterationConfig<I, IC, ?> config);

	public boolean containsValue(Map<?, ?> map, String key, Object object);

	public <K, V> void refresh(Map<K, V> source, Map<K, V> newValues);

	public boolean containsValue(Map<?, ?> map, String key, Object object, Map<?, ?> defaultValues);

	public String toPrettyString(Map<?, ?> map, String valuesSeparator, int marginTabCount);

	public <K, V> String toString(Map<K, V> map, int marginTabCount);
	
	public default void terminateIteration() {
		throw TerminateIteration.NOTIFICATION;
	}
	
	public default boolean isIterationTerminatedNotification(Throwable exc) {
		return exc instanceof TerminateIteration;
	}
	
	public <K, V> String toString(
		Map<K, V> map,
		Function<K, String> keyTransformer,
		Function<V, String> valueTransformer,
		int marginTabCount
	);

	public static class TerminateIteration extends RuntimeException {
		private static final long serialVersionUID = 4182825598193659018L;

		public static final TerminateIteration NOTIFICATION;

		static {
			NOTIFICATION = new IterableObjectHelper.TerminateIteration();
		}

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }

	}


	public static interface IterationConfig<I, IC, C extends IterationConfig<I, IC, C>> {
		
		
		public static <J, I, C extends IterationConfig<Map.Entry<J, I>, Collection<I>, C>> C of(Map<J, I> input) {
			return (C)new IterationConfigImpl<Map.Entry<J, I>, Collection<I>>(input != null ? input.entrySet() : null);
		}
		
		public static <I, C extends IterationConfig<I, Collection<I>, C>> C of(Collection<I> input) {
			return (C)new IterationConfigImpl<I, Collection<I>>(input);
		}
		
		public static <I, C extends IterationConfig<I, I[], C>> C of(I[] input) {
			return (C)new IterationConfigImpl<I, I[]>(input);
		}
		
		public static <I, C extends IterationConfig<Integer, int[], C>> C ofInts(int[] input) {
			return (C)new IterationConfigImpl<Integer, int[]>(input);
		}
		
		public static <I, C extends IterationConfig<Long, long[], C>> C ofLongs(long[] input) {
			return (C)new IterationConfigImpl<Long, long[]>(input);
		}
		
		public static <I, C extends IterationConfig<Float, float[], C>> C ofFloats(float[] input) {
			return (C)new IterationConfigImpl<Float, float[]>(input);
		}
		
		public static <I, C extends IterationConfig<Double, double[], C>> C ofDoubles(double[] input) {
			return (C)new IterationConfigImpl<Double, double[]>(input);
		}
		
		public static <I, C extends IterationConfig<Boolean, boolean[], C>> C ofBooleans(boolean[] input) {
			return (C)new IterationConfigImpl<Boolean, boolean[]>(input);
		}
		
		public static <I, C extends IterationConfig<Byte, byte[], C>> C ofBytes(byte[] input) {
			return (C)new IterationConfigImpl<Byte, byte[]>(input);
		}
		
		public static <I, C extends IterationConfig<Character, char[], C>> C ofChars(char[] input) {
			return (C)new IterationConfigImpl<Character, char[]>(input);
		}
		
		public static <J, I, C extends IterationConfig<Map.Entry<J, I>, Collection<I>, C>> C ofNullable(Map<J, I> input) {
			return (C)new IterationConfigImpl<Map.Entry<J, I>, Collection<I>>(input != null ? input.entrySet() : IterationConfigImpl.NO_ITEMS);
		}
		
		public static <I, C extends IterationConfig<I, Collection<I>, C>> C ofNullable(Collection<I> input) {
			return (C)new IterationConfigImpl<I, Collection<I>>(input != null ? input : IterationConfigImpl.NO_ITEMS);
		}
		
		public static <I, C extends IterationConfig<I, I[], C>> C ofNullable(I[] input) {
			return (C)new IterationConfigImpl<I, I[]>(input != null ? input : IterationConfigImpl.NO_ITEMS);
		}
		
		public static <I, C extends IterationConfig<Integer, int[], C>> C ofNullableInts(int[] input) {
			return (C)new IterationConfigImpl<Integer, int[]>(input != null ? input : IterationConfigImpl.NO_ITEMS);
		}
		
		public static <I, C extends IterationConfig<Long, long[], C>> C ofNullableLongs(long[] input) {
			return (C)new IterationConfigImpl<Long, long[]>(input != null ? input : IterationConfigImpl.NO_ITEMS);
		}
		
		public static <I, C extends IterationConfig<Float, float[], C>> C ofNullableFloats(float[] input) {
			return (C)new IterationConfigImpl<Float, float[]>(input != null ? input : IterationConfigImpl.NO_ITEMS);
		}
		
		public static <I, C extends IterationConfig<Double, double[], C>> C ofNullableDoubles(double[] input) {
			return (C)new IterationConfigImpl<Double, double[]>(input != null ? input : IterationConfigImpl.NO_ITEMS);
		}
		
		public static <I, C extends IterationConfig<Boolean, boolean[], C>> C ofNullableBooleans(boolean[] input) {
			return (C)new IterationConfigImpl<Boolean, boolean[]>(input != null ? input : IterationConfigImpl.NO_ITEMS);
		}
		
		public static <I, C extends IterationConfig<Byte, byte[], C>> C ofNullableBytes(byte[] input) {
			return (C)new IterationConfigImpl<Byte, byte[]>(input != null ? input : IterationConfigImpl.NO_ITEMS);
		}
		
		public static <I, C extends IterationConfig<Character, char[], C>> C ofNullableChars(char[] input) {
			return (C)new IterationConfigImpl<Character, char[]>(input != null ? input : IterationConfigImpl.NO_ITEMS);
		}
				
		public C withAction(Consumer<I> action);
		
		public <O, OC extends Collection<O>> WithOutputOfCollection<I, IC, O, OC> withOutput(OC output);
		
		public <K, O, OM extends Map<K, O>> WithOutputOfMap<I, IC, K, O, OM> withOutput(OM output);
		
		public C parallelIf(Predicate<IC> predicate);
		
		public C withPriority(Integer priority);
		
		public static class WithOutputOfMap<I, IC, K, O, OM> extends IterationConfigImpl.WithOutput<I, IC, WithOutputOfMap<I, IC, K, O, OM>> {
			
			WithOutputOfMap(IterationConfigImpl<I, IC> configuration) {
				super(configuration);
			}

			public WithOutputOfMap<I, IC, K, O, OM> withAction(BiConsumer<I, Consumer<Consumer<OM>>> action) {
				wrappedConfiguration.withAction(action);
				return this;
			}
			
		}
		
		public static class WithOutputOfCollection<I, IC, O, OC> extends IterationConfigImpl.WithOutput<I, IC, WithOutputOfCollection<I, IC, O, OC>> {
			
			WithOutputOfCollection(IterationConfigImpl<I, IC> configuration) {
				super(configuration);
			}

			public WithOutputOfCollection<I, IC, O, OC> withAction(BiConsumer<I, Consumer<Consumer<OC>>> action) {
				wrappedConfiguration.withAction(action);
				return this;
			}

		}
		
	}


	public static class ResolveConfig<T, K> {

		Map<?,?> map;
		K filter;
		String valuesSeparator;
		String defaultValueSeparator;
		boolean deleteUnresolvedPlaceHolder;
		Map<?,?> defaultValues;

		private ResolveConfig(K filter) {
			this.filter = filter;
		}

		public static ForNamedKey forNamedKey(Object key) {
			return new ForNamedKey(key);
		}

		public static <K> ForAllKeysThat<K> forAllKeysThat(Predicate<K> filter) {
			return new ForAllKeysThat<>(filter);
		}

		public T on(Map<?,?> map) {
			this.map = map;
			return (T)this;
		}

		public T withDefaultValues(Map<?,?> defaultValues) {
			this.defaultValues = defaultValues;
			return (T)this;
		}

		public T withValuesSeparator(String valuesSeparator) {
			this.valuesSeparator = valuesSeparator;
			return (T)this;
		}

		public T withDefaultValueSeparator(String defaultValueSeparator) {
			this.defaultValueSeparator = defaultValueSeparator;
			return (T)this;
		}

		public T deleteUnresolvedPlaceHolder(boolean flag) {
			this.deleteUnresolvedPlaceHolder = flag;
			return (T)this;
		}

		public static class ForNamedKey extends ResolveConfig<ForNamedKey, Object> {
			private ForNamedKey(Object filter) {
				super(filter);
			}
		}

		public static class ForAllKeysThat<K> extends ResolveConfig<ForAllKeysThat<K>, Predicate<K>> {
			private ForAllKeysThat(Predicate<K> filter) {
				super(filter);
			}
		}

	}

}
