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
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.burningwave.core.function.ThrowingBiConsumer;
import org.burningwave.core.function.ThrowingConsumer;
import org.burningwave.core.iterable.Properties.Event;


@SuppressWarnings("unchecked")
public interface IterableObjectHelper {

	public static class Configuration {
		public static class Key {
			public final static String DEFAULT_VALUES_SEPERATOR = "iterable-object-helper.default-values-separator";
			public final static String PARELLEL_ITERATION_APPLICABILITY_MAX_RUNTIME_THREADS_COUNT_THRESHOLD =
				"iterable-object-helper.parallel-iteration.applicability.max-runtime-threads-count-threshold";
			public final static String DEFAULT_MINIMUM_COLLECTION_SIZE_FOR_PARALLEL_ITERATION =
				"iterable-object-helper.parallel-iteration.applicability.default-minimum-collection-size";
		}

		public final static Map<String, Object> DEFAULT_VALUES;

		static {
			Map<String, Object> defaultValues = new HashMap<>();

			defaultValues.put(Key.DEFAULT_VALUES_SEPERATOR, ";");

			defaultValues.put(Key.PARELLEL_ITERATION_APPLICABILITY_MAX_RUNTIME_THREADS_COUNT_THRESHOLD, "autodetect");

			defaultValues.put(Key.DEFAULT_MINIMUM_COLLECTION_SIZE_FOR_PARALLEL_ITERATION, 2);

			DEFAULT_VALUES = Collections.unmodifiableMap(defaultValues);
		}
	}

	public static IterableObjectHelper create(Properties config) {
		IterableObjectHelperImpl iterableObjectHelper = new IterableObjectHelperImpl(config);
		iterableObjectHelper.listenTo(config);
		return iterableObjectHelper;
	}

	public Predicate<Collection<?>> getDefaultMinimumCollectionSizeForParallelIterationPredicate();

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

	public <I, O> Collection<O> iterate(IterationConfig<I, O> config);

	public boolean containsValue(Map<?, ?> map, String key, Object object);

	public <K, V> void refresh(Map<K, V> source, Map<K, V> newValues);

	public boolean containsValue(Map<?, ?> map, String key, Object object, Map<?, ?> defaultValues);

	public String toPrettyString(Map<?, ?> map, String valuesSeparator, int marginTabCount);

	public <K, V> String toString(Map<K, V> map, int marginTabCount);

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


	public static class IterationConfig<I, O> {
		Collection<I> items;
		BiConsumer<I, Consumer<Consumer<Collection<O>>>> action;
		Collection<O> outputCollection;
		Predicate<Collection<?>> predicateForParallelIteration;
		Integer priority;

		public IterationConfig(
			Collection<I> items
		) {
			this.items = items;
		}

		public static <I, O> IterationConfig<I, O> of(Collection<I> input) {
			IterationConfig<I, O> config = new IterationConfig<>(input);
			return config;
		}

		public IterationConfig<I, O> withAction(BiConsumer<I, Consumer<Consumer<Collection<O>>>> action) {
			if (outputCollection == null) {

			}
			this.action = action;
			return this;
		}

		public IterationConfig<I, O> withAction(Consumer<I> action) {
			this.action = (item, outputItemCollector) -> action.accept(item);
			return this;
		}

		public IterationConfig<I, O> withPriority(Integer priority) {
			this.priority = priority;
			return this;
		}

		public IterationConfig<I, O> parallelIf(Predicate<Collection<?>> predicate) {
			this.predicateForParallelIteration = predicate;
			return this;
		}

		public IterationConfig<I, O> collectTo(Collection<O> output) {
			this.outputCollection = output;
			return this;
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
