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

public interface IterableObjectHelper {
	
	public static class Configuration {
		public static class Key {
			public final static String DEFAULT_VALUES_SEPERATOR = "iterable-object-helper.default-values-separator";
			public final static String PARELLEL_ITERATION_APPLICABILITY_MAX_RUNTIME_THREADS_COUNT_THRESHOLD =
				"iterable-object-helper.parallel-iteration.applicability.max-runtime-threads-count-threshold";
		}
		
		public final static Map<String, Object> DEFAULT_VALUES;
		
		static {
			Map<String, Object> defaultValues = new HashMap<>();

			defaultValues.put(Key.DEFAULT_VALUES_SEPERATOR, ";");
			
			defaultValues.put(Key.PARELLEL_ITERATION_APPLICABILITY_MAX_RUNTIME_THREADS_COUNT_THRESHOLD, "autodetect");
						
			DEFAULT_VALUES = Collections.unmodifiableMap(defaultValues);
		}
	}
	
	public static IterableObjectHelper create(Properties config) {
		IterableObjectHelperImpl iterableObjectHelper = new IterableObjectHelperImpl(
			config.getProperty(Configuration.Key.DEFAULT_VALUES_SEPERATOR),
			IterableObjectHelperImpl.computeMatxRuntimeThreadsCountThreshold(config)
		);
		iterableObjectHelper.listenTo(config);
		return iterableObjectHelper;
	}
	
	
	public String getDefaultValuesSeparator();

	public <K, V> void processChangeNotification(Properties properties, Event event, K key, V newValue, V previousValue);

	public <K, V> void deepClear(Map<K, V> map);

	public <K, V, E extends Throwable> void deepClear(Map<K, V> map, ThrowingBiConsumer<K, V, E> itemDestroyer) throws E;

	public <V> void deepClear(Collection<V> map);

	public <V, E extends Throwable> void deepClear(Collection<V> map, ThrowingConsumer<V, E> itemDestroyer) throws E;

	public <T> Collection<T> merge(Supplier<Collection<T>> baseCollectionSupplier,
			Supplier<Collection<T>> additionalCollectionSupplier, Supplier<Collection<T>> defaultCollectionSupplier);

	public <T> T getRandom(Collection<T> coll);

	public <T> Stream<T> retrieveStream(Object object);

	public long getSize(Object object);

	public <T> T resolveValue(Map<?, ?> map, String key);

	public <T> Collection<T> resolveValues(Map<?, ?> map, String key);

	public Collection<String> resolveStringValues(Map<?, ?> map, String key);

	public String resolveStringValue(Map<?, ?> map, String key);

	public <T> T resolveValue(Map<?, ?> map, String key, Map<String, ?> defaultValues);

	public <T> Collection<T> resolveValues(Map<?, ?> map, String key, Map<String, ?> defaultValues);

	public String resolveStringValue(Map<?, ?> map, String key, Map<String, ?> defaultValues);

	public Collection<String> resolveStringValues(Map<?, ?> map, String key, Map<String, ?> defaultValues);

	public <T> T resolveValue(Map<?, ?> map, String key, String valuesSeparator);

	public <T> Collection<T> resolveValues(Map<?, ?> map, String key, String valuesSeparator);

	public String resolveStringValue(Map<?, ?> map, String key, String valuesSeparator);

	public Collection<String> resolveStringValues(Map<?, ?> map, String key, String valuesSeparator);

	public <T> T resolveValue(Map<?, ?> map, String key, String valuesSeparator, boolean deleteUnresolvedPlaceHolder);

	public <T> Collection<T> resolveValues(Map<?, ?> map, String key, String valuesSeparator,
			boolean deleteUnresolvedPlaceHolder);

	public String resolveStringValue(Map<?, ?> map, String key, String valuesSeparator, boolean deleteUnresolvedPlaceHolder);

	public Collection<String> resolveStringValues(Map<?, ?> map, String key, String valuesSeparator,
			boolean deleteUnresolvedPlaceHolder);

	public <T> T resolveValue(Map<?, ?> map, String key, String valuesSeparator, String defaultValuesSeparator,
			boolean deleteUnresolvedPlaceHolder, Map<?, ?> defaultValues);

	public <T> Collection<T> resolveValues(Map<?, ?> map, String key, String valuesSeparator, String defaultValuesSeparator,
			boolean deleteUnresolvedPlaceHolder, Map<?, ?> defaultValues);

	public String resolveStringValue(Map<?, ?> map, String key, String valuesSeparator, String defaultValuesSeparator,
			boolean deleteUnresolvedPlaceHolder, Map<?, ?> defaultValues);

	public Collection<String> resolveStringValues(Map<?, ?> map, String key, String valuesSeparator,
			String defaultValuesSeparator, boolean deleteUnresolvedPlaceHolder, Map<?, ?> defaultValues);

	public Collection<String> getAllPlaceHolders(Map<?, ?> map);

	public Collection<String> getAllPlaceHolders(Map<?, ?> map, Predicate<String> propertyFilter);

	public Collection<String> getAllPlaceHolders(Map<?, ?> map, String propertyName);

	public boolean containsValue(Map<?, ?> map, String key, Object object);

	public <K, V> void refresh(Map<K, V> source, Map<K, V> newValues);

	public boolean containsValue(Map<?, ?> map, String key, Object object, Map<?, ?> defaultValues);

	public <T, O> Collection<O> iterateParallelIf(Collection<T> items, Consumer<T> action, Predicate<Collection<T>> predicate);

	public <T, O> Collection<O> iterateParallelIf(Collection<T> items, BiConsumer<T, Consumer<O>> action,
			Collection<O> outputCollection, Predicate<Collection<T>> predicate);

	public <T, O> void iterateParallel(Collection<T> items, Consumer<T> action);

	public <T, O> Collection<O> iterateParallel(Collection<T> items, BiConsumer<T, Consumer<O>> action,
			Collection<O> outputCollection);

	public String toPrettyString(Map<?, ?> map, String valuesSeparator, int marginTabCount);

	public <K, V> String toString(Map<K, V> map, int marginTabCount);

	public <K, V> String toString(Map<K, V> map, Function<K, String> keyTransformer, Function<V, String> valueTransformer,
			int marginTabCount);
}
