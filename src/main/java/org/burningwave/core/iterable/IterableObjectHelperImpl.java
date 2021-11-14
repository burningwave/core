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

import static org.burningwave.core.assembler.StaticComponentContainer.BackgroundExecutor;
import static org.burningwave.core.assembler.StaticComponentContainer.Driver;
import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggersRepository;
import static org.burningwave.core.assembler.StaticComponentContainer.Objects;
import static org.burningwave.core.assembler.StaticComponentContainer.Strings;
import static org.burningwave.core.assembler.StaticComponentContainer.Synchronizer;
import static org.burningwave.core.assembler.StaticComponentContainer.ThreadSupplier;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.burningwave.core.Identifiable;
import org.burningwave.core.assembler.StaticComponentContainer;
import org.burningwave.core.concurrent.QueuedTasksExecutor;
import org.burningwave.core.function.ThrowingBiConsumer;
import org.burningwave.core.function.ThrowingConsumer;
import org.burningwave.core.iterable.Properties.Event;

@SuppressWarnings("unchecked")
public class IterableObjectHelperImpl implements IterableObjectHelper, Properties.Listener, Identifiable {
	private Predicate<Collection<?>> defaultMinimumCollectionSizeForParallelIterationPredicate;
	private String defaultValuesSeparator;
	private Integer maxThreadCountsForParallelIteration;
	//Deferred initialized
	private Supplier<Class<?>[]> parallelCollectionClassesSupplier;
	private Class<?>[] parallelCollectionClasses;


	IterableObjectHelperImpl(Properties config) {
		this.defaultValuesSeparator = resolveStringValue(
			ResolveConfig.ForNamedKey.forNamedKey(
				Configuration.Key.DEFAULT_VALUES_SEPERATOR
			).on(config)
		);
		this.defaultMinimumCollectionSizeForParallelIterationPredicate =
			buildDefaultMinimumCollectionSizeForParallelIterationPredicate(config);
		this.maxThreadCountsForParallelIteration = computeMaxRuntimeThreadsCountThreshold(config);
		this.parallelCollectionClassesSupplier = () -> retrieveParallelCollectionClasses(config);
	}

	private Class<?>[] retrieveParallelCollectionClasses(Properties config) {
		Collection<Class<?>> parallelCollectionClasses = new LinkedHashSet<Class<?>>();
		Collection<String> classNames = resolveStringValues(
			ResolveConfig.ForNamedKey.forNamedKey(
				Configuration.Key.PARELLEL_ITERATION_APPLICABILITY_OUTPUT_COLLECTION_ENABLED_TYPES
			).on(config)
		);
		if (classNames != null) {
			for (String className : classNames) {
				parallelCollectionClasses.add(
					Driver.getClassByName(
						className,
						false,
						this.getClass().getClassLoader(),
						this.getClass()
					)
				);
			}
		}
		return parallelCollectionClasses.toArray(new Class[parallelCollectionClasses.size()]);
	}

	private Predicate<Collection<?>> buildDefaultMinimumCollectionSizeForParallelIterationPredicate(Properties config) {
		int defaultMinimumCollectionSizeForParallelIteration = Objects.toInt(
			resolveValue(
				ResolveConfig.ForNamedKey.forNamedKey(
					Configuration.Key.PARELLEL_ITERATION_APPLICABILITY_DEFAULT_MINIMUM_COLLECTION_SIZE
				).on(config)
			)
		);
		if (defaultMinimumCollectionSizeForParallelIteration >= 0) {
			return coll ->
				coll.size() >= defaultMinimumCollectionSizeForParallelIteration;
		} else {
			 return coll -> false;
		}
	}

	@Override
	public String getDefaultValuesSeparator() {
		return this.defaultValuesSeparator;
	}


	@Override
	public Predicate<Collection<?>> getDefaultMinimumCollectionSizeForParallelIterationPredicate() {
		return defaultMinimumCollectionSizeForParallelIterationPredicate;
	}

	Integer computeMaxRuntimeThreadsCountThreshold(Properties config) {
		try {
			return Objects.toInt(
				resolveValue(
					ResolveConfig.ForNamedKey.forNamedKey(
						Configuration.Key.PARELLEL_ITERATION_APPLICABILITY_MAX_RUNTIME_THREAD_COUNT_THRESHOLD
					).on(config)
				)
			);
		} catch (Throwable exc) {
			return autodetectMaxRuntimeThreadsCountThreshold();
		}
	}
	
	private Integer autodetectMaxRuntimeThreadsCountThreshold() {
		if (ThreadSupplier != null) {
			if (ThreadSupplier.getMaxDetachedThreadCountIncreasingStep() > 0) {
				return Integer.MAX_VALUE;
			}
			return ThreadSupplier.getInititialMaxThreadCount();
		}
		return null;
	}
	
	@Override
	public <K, V> void processChangeNotification(Properties config, Event event, K key, V newValue, V previousValue) {
		if (event.name().equals(Event.PUT.name()) && key.equals(Configuration.Key.DEFAULT_VALUES_SEPERATOR) && newValue != null) {
			this.defaultValuesSeparator = (String)newValue;
		}
		if (event.name().equals(Event.PUT.name()) && key.equals(Configuration.Key.PARELLEL_ITERATION_APPLICABILITY_DEFAULT_MINIMUM_COLLECTION_SIZE) && newValue != null) {
			this.defaultMinimumCollectionSizeForParallelIterationPredicate =
				buildDefaultMinimumCollectionSizeForParallelIterationPredicate(config);
		}
		if (event.name().equals(Event.PUT.name()) && key.equals(Configuration.Key.PARELLEL_ITERATION_APPLICABILITY_MAX_RUNTIME_THREAD_COUNT_THRESHOLD)) {
			this.maxThreadCountsForParallelIteration = computeMaxRuntimeThreadsCountThreshold(config);
		}
		if (event.name().equals(Event.PUT.name()) && key.equals(Configuration.Key.PARELLEL_ITERATION_APPLICABILITY_OUTPUT_COLLECTION_ENABLED_TYPES)) {
			this.parallelCollectionClasses = retrieveParallelCollectionClasses(config);
		}
	}

	@Override
	public <K, V> void deepClear(Map<K,V> map) {
		Iterator<Entry<K, V>> itr = map.entrySet().iterator();
		while (itr.hasNext()) {
			itr.next();
			itr.remove();
		}
	}

	@Override
	public <K, V, E extends Throwable> void deepClear(Map<K,V> map, ThrowingBiConsumer<K, V, E> itemDestroyer) throws E {
		Iterator<Entry<K, V>> itr = map.entrySet().iterator();
		while (itr.hasNext()) {
			Entry<K, V> entry = itr.next();
			try {
				itr.remove();
				itemDestroyer.accept(entry.getKey(), entry.getValue());
			} catch (Throwable exc) {
				ManagedLoggersRepository.logError(getClass()::getName,"Exception occurred while removing and cleraring " + entry.getValue(), exc);
			}
			
		}
	}

	@Override
	public <V> void deepClear(Collection<V> map) {
		Iterator<V> itr = map.iterator();
		while (itr.hasNext()) {
			itr.next();
			itr.remove();
		}
	}

	@Override
	public <V, E extends Throwable > void deepClear(Collection<V> map, ThrowingConsumer<V, E> itemDestroyer) throws E {
		Iterator<V> itr = map.iterator();
		while (itr.hasNext()) {
			itr.remove();
			itemDestroyer.accept(itr.next());
		}
	}

	@Override
	public <T> Collection<T> merge(
		Supplier<Collection<T>> baseCollectionSupplier,
		Supplier<Collection<T>> additionalCollectionSupplier,
		Supplier<Collection<T>> defaultCollectionSupplier
	) {
		Collection<T> mergedCollection = Optional.ofNullable(
			baseCollectionSupplier.get()
		).orElseGet(() ->
			defaultCollectionSupplier.get()
		);
		Collection<T> additionalClassPaths = additionalCollectionSupplier.get();
		if (additionalClassPaths != null) {
			mergedCollection.addAll(additionalClassPaths);
		}
		return mergedCollection;
	}

	@Override
	public <T> T getRandom(Collection<T> coll) {
		int num = (int) (Math.random() * coll.size());
	    for(T t: coll) {
	    	if (--num < 0) return t;
	    }
	    return null;
	}

	@Override
	public <T> Stream<T> retrieveStream(Object object) {
		Stream<T> stream = null;
		if (object != null) {
			if (object instanceof Collection) {
				return ((Collection<T>)object).stream();
			} else if (object.getClass().isArray()) {
				return Stream.of((T[])object);
			} else if (object instanceof Map) {
				return (Stream<T>) ((Map<T, ?>)object).entrySet().stream();
			}
		}
		return stream;
	}

	@Override
	public long getSize(Object object) {
		return retrieveStream(object).count();
	}

/////////////

	@Override
	public <T> T resolveValue(ResolveConfig.ForNamedKey config) {
		return resolveValue(
			config.filter, () ->
			resolve(
				config.map, config.filter,
				config.valuesSeparator, config.defaultValueSeparator,
				config.deleteUnresolvedPlaceHolder, config.defaultValues
			)
		);
	}

	@Override
	public <K, T> T resolveValue(ResolveConfig.ForAllKeysThat<K> config) {
		return resolveValue(
			config.filter, () ->
			resolve(
				config.map, config.filter, 
				config.valuesSeparator, config.defaultValueSeparator,
				config.deleteUnresolvedPlaceHolder, config.defaultValues
			)
		);
	}

	@Override
	public String resolveStringValue(ResolveConfig.ForNamedKey config) {
		return resolveValue(config);
	}

	@Override
	public <K> String resolveStringValue(ResolveConfig.ForAllKeysThat<K> config) {
		return resolveValue(config);
	}


	@Override
	public <T> Collection<T> resolveValues(ResolveConfig.ForNamedKey config) {
		return resolve(
			config.map, config.filter,
			config.valuesSeparator != null ? 
				config.valuesSeparator : 
				config.defaultValueSeparator != null ?
					config.defaultValueSeparator : 
					defaultValuesSeparator,
			config.defaultValueSeparator,
			config.deleteUnresolvedPlaceHolder, config.defaultValues
		);
	}

	@Override
	public <K, V> Map<K, V> resolveValues(ResolveConfig.ForAllKeysThat<K> config) {
		return (Map<K, V>) resolveForKeys(
			config.map, config.filter,
			config.valuesSeparator != null ? 
				config.valuesSeparator : 
				config.defaultValueSeparator != null ? 
					config.defaultValueSeparator :
					defaultValuesSeparator,
			config.defaultValueSeparator,
			config.deleteUnresolvedPlaceHolder, config.defaultValues
		);
	}

	@Override
	public Collection<String> resolveStringValues(ResolveConfig.ForNamedKey config) {
		return resolve(
			config.map, config.filter,
			config.valuesSeparator != null ? 
				config.valuesSeparator :
				config.defaultValueSeparator != null ?
					config.defaultValueSeparator : 
					defaultValuesSeparator,
			config.defaultValueSeparator,
			config.deleteUnresolvedPlaceHolder, config.defaultValues
		);
	}

	@Override
	public <K> Map<K, Collection<String>> resolveStringValues(ResolveConfig.ForAllKeysThat<K> config) {
		return resolveForKeys(
			config.map, config.filter,
			config.valuesSeparator != null ?
				config.valuesSeparator :
				config.defaultValueSeparator != null ?
					config.defaultValueSeparator :
					defaultValuesSeparator,
			config.defaultValueSeparator,
			config.deleteUnresolvedPlaceHolder, config.defaultValues
		);
	}

	private <T> T resolveValue(Object key, Supplier<Object> valuesSupplier) {
		Object value = valuesSupplier.get();
		if (value instanceof Collection) {
			Collection<T> values = (Collection<T>)value;
			if (values.size() > 1) {
				Driver.throwException("Found more than one item under key/predicate {}", key);
			}
			return values.stream().findFirst().orElseGet(() -> null);
		} else {
			return (T)value;
		}
	}

	private <K, V> Map<K, V> resolveForKeys(
		Map<?,?> map,
		Predicate<K> keyPredicate,
		String valuesSeparator,
		String defaultValueSeparator,
		boolean deleteUnresolvedPlaceHolder,
		Map<?,?> defaultValues
	) {
		Collection<K> keys = new LinkedHashSet<>();
		for (Object key : map.keySet()) {
			try {
				if (keyPredicate.test((K) key)) {
					keys.add((K)key);
				}
			} catch (ClassCastException exc) {

			}
		}
		if (defaultValues != null) {
			for (Object key : defaultValues.keySet()) {
				try {
					if (keyPredicate.test((K) key)) {
						keys.add((K)key);
					}
				} catch (ClassCastException exc) {

				}
			}
		}
		Map<K, V> values = new HashMap<>();
		for (K key : keys) {
			values.put(
				key,
				resolve(
					map, key,
					valuesSeparator,
					defaultValueSeparator,
					deleteUnresolvedPlaceHolder,
					defaultValues
				)
			);
		}
		return values;
	}

	private <T> T resolve(
		Map<?,?> map,
		Object key,
		String valuesSeparator,
		String defaultValueSeparator,
		boolean deleteUnresolvedPlaceHolder,
		Map<?,?> defaultValues
	) {
		String valuesSeparatorForSplitting = valuesSeparator != null ? valuesSeparator : defaultValueSeparator != null ? defaultValueSeparator : defaultValuesSeparator;
		T value = (T) map.get(key);
		if (value == null && defaultValues != null) {
			value = (T) resolve(defaultValues, key, valuesSeparator, defaultValueSeparator, deleteUnresolvedPlaceHolder, null);
		}
		if (value != null && value instanceof String) {
			String stringValue = (String)value;
			Collection<Object> values = new IterableObjectHelperImpl.ArrayList<>();
			if (!Strings.isEmpty(stringValue)) {
				Map<Integer, List<String>> subProperties = Strings.extractAllGroups(Strings.PLACE_HOLDER_NAME_EXTRACTOR_PATTERN, stringValue);
				if (!subProperties.isEmpty()) {
					for (Map.Entry<Integer, List<String>> entry : subProperties.entrySet()) {
						for (String placeHolder : entry.getValue()) {
							Object valueObjects = null;
							if (!placeHolder.startsWith("system.properties:")) {
								valueObjects = resolve(map, placeHolder, valuesSeparator, defaultValueSeparator, deleteUnresolvedPlaceHolder, defaultValues);
							} else {
								valueObjects = StaticComponentContainer.SystemProperties.get(placeHolder.split(":")[1]);
								if (valuesSeparatorForSplitting != null) {
									valueObjects = ((String)valueObjects).replace(
										StaticComponentContainer.SystemProperties.get("path.separator"), valuesSeparatorForSplitting
									);
								}
							}
							if (valueObjects == null) {
								if (deleteUnresolvedPlaceHolder) {
									stringValue = stringValue.replaceAll("[^{" + valuesSeparatorForSplitting + "}]*?" + Strings.placeHolderToRegEx("${" + placeHolder + "}") + ".*?" + valuesSeparatorForSplitting, "");
								}
								continue;
							}
							Collection<Object> replacements = new ArrayList<>();
							if (valueObjects instanceof IterableObjectHelperImpl.ArrayList) {
								replacements.addAll((Collection<?>)valueObjects);
							} else {
								replacements.add(valueObjects);
							}
							String regExpPattern = null;
							if (stringValue.contains(valuesSeparatorForSplitting)) {
								regExpPattern = "([^{" + valuesSeparatorForSplitting + "}]*?" + Strings.placeHolderToRegEx("${" + placeHolder + "}") + ".*?" + valuesSeparatorForSplitting +")";
							} else {
								regExpPattern = "(.*?"+ Strings.placeHolderToRegEx("${" + placeHolder + "}") + ".*?)";
							}
							Map<Integer, List<String>> placeHolderedValues = Strings.extractAllGroups(
								Pattern.compile(regExpPattern), stringValue
							);
							for (Map.Entry<Integer, List<String>> placeHolderedValuesEntry : placeHolderedValues.entrySet()) {
								for (String placeHolderedValue : placeHolderedValuesEntry.getValue()) {
									String newReplacement = "";
									for (Object valueObject : replacements) {
										if (valueObject instanceof String) {
											String replacement = (String)valueObject;
											if (valuesSeparator != null) {
												for (String replacementUnit : replacement.split(valuesSeparator)) {
													newReplacement += placeHolderedValue.replace("${" + placeHolder + "}", replacementUnit);
													newReplacement += newReplacement.endsWith(valuesSeparator) ? "" : valuesSeparator;
												}
											} else {
												newReplacement += placeHolderedValue.replace("${" + placeHolder + "}", replacement);
											}
										} else {
											values.add(valueObject);
										}
									}
									stringValue = stringValue.replace(placeHolderedValue, newReplacement);
								}
							}
						}
					}
					if (stringValue != null && !stringValue.isEmpty()) {
						if (valuesSeparator == null) {
							values.add(stringValue);
						} else {
							for (String valueToAdd : stringValue.split(valuesSeparator)) {
								values.add(valueToAdd);
							}
						}
					}
				} else {
					if (valuesSeparator != null) {
						for (String valueToAdd : stringValue.split(valuesSeparator)) {
							values.add(valueToAdd);
						}
					} else {
						values.add(stringValue);
					}
				}
			}
			return (T)values;
		} else {
			return value;
		}

	}

	@Override
	public Collection<String> getAllPlaceHolders(Map<?, ?> map) {
		return getAllPlaceHolders(map, object -> true);
	}

	@Override
	public Collection<String> getAllPlaceHolders(Map<?, ?> map, Predicate<String> propertyFilter) {
		Collection<String> placeHolders = new HashSet<>();
		for (Map.Entry<?, ?> entry : map.entrySet().stream().filter(entry ->
				(entry.getValue() == null || entry.getValue() instanceof String) && (entry.getKey() instanceof String && propertyFilter.test((String)entry.getKey()))
			).collect(Collectors.toSet())) {
			String value = (String)entry.getValue();
			for(List<String> placeHoldersFound : Strings.extractAllGroups(Strings.PLACE_HOLDER_EXTRACTOR_PATTERN, value).values()) {
				placeHolders.addAll(placeHoldersFound);
			}
		}
		return placeHolders;
	}

	@Override
	public Collection<String> getAllPlaceHolders(Map<?, ?> map, String propertyName) {
		Collection<String> placeHolders = getAllPlaceHolders(map);
		Iterator<String> placeHoldersItr = placeHolders.iterator();
		while (placeHoldersItr.hasNext()) {
			if (!containsValue(map, propertyName, placeHoldersItr.next())) {
				placeHoldersItr.remove();
			}
		}
		return placeHolders;
	}

	@Override
	public boolean containsValue(Map<?, ?> map, String key, Object object) {
		return containsValue(map, key, object, null);
	}

	@Override
	public <K, V> void refresh(Map<K, V> source, Map<K, V> newValues) {
		Collection<K> keyToBeRemoved = new HashSet<>();
		Map<K, V> keyAndValuesToBePut = new HashMap<>();
		for(Map.Entry<K, V> keyAndValue : source.entrySet()) {
			K key = keyAndValue.getKey();
			if (newValues.containsKey(key)) {
				V oldValue = newValues.get(key);
				V newValue = newValues.get(key);
				if (!java.util.Objects.equals(oldValue, newValue)) {
					keyAndValuesToBePut.put(key, newValue);
				}
			} else {
				keyToBeRemoved.add(key);
			}
		}

		for(Map.Entry<K, V> keyAndValue : newValues.entrySet()) {
			K key = keyAndValue.getKey();
			if (!source.containsKey(key)) {
				keyAndValuesToBePut.put(key, keyAndValue.getValue());
			}
		}

		source.keySet().removeAll(keyToBeRemoved);
		source.putAll(keyAndValuesToBePut);
	}

	@Override
	public boolean containsValue(Map<?, ?> map, String key, Object object, Map<?, ?> defaultValues) {
		Object value = map.get(key);
		if (value == null && defaultValues != null) {
			value = defaultValues.get(key);
		}
		if (value != null && value instanceof String) {
			if (Strings.isEmpty((String)value) && defaultValues != null) {
				value = defaultValues.get(key);
			}
			if (value != null && value instanceof String) {
				String stringValue = (String)value;
				if (!Strings.isEmpty(stringValue)) {
					if (object instanceof String) {
						String objectString = (String)object;
						if (stringValue.contains(objectString)) {
							return true;
						}
					}
					Map<Integer, List<String>> subProperties = Strings.extractAllGroups(Strings.PLACE_HOLDER_NAME_EXTRACTOR_PATTERN, stringValue);
					if (!subProperties.isEmpty()) {
						for (Map.Entry<Integer, List<String>> entry : subProperties.entrySet()) {
							for (String propName : entry.getValue()) {
								return containsValue(map, propName, object, defaultValues);
							}
						}
					}
				}
			}
		}
		return object != null && value != null && object.equals(value);
	}
	
	@Override
	public <I, K, O> Map<K, O> iterate(
		IterableObjectHelper.IterationConfig.WithOutputOfMap<I, K, O> configuration
	) {
		IterationConfigImpl<I> config = configuration.getWrappedConfiguration();
		return iterate(
			config.items,
			config.predicateForParallelIteration,
			(Map<K, O>)config.output,
			(BiConsumer<I, Consumer<Consumer<Map<K, O>>>>)config.action,
			config.priority
		);
	}
	
	@Override
	public <I, O> Collection<O> iterate(
		IterableObjectHelper.IterationConfig.WithOutputOfCollection<I, O> configuration
	) {
		IterationConfigImpl<I> config = configuration.getWrappedConfiguration();
		return iterate(
			config.items,
			config.predicateForParallelIteration,
			(Collection<O>)config.output,
			(BiConsumer<I, Consumer<Consumer<Collection<O>>>>)config.action,
			config.priority
		);
		
	}
	
	@Override
	public <I> void iterate(IterationConfig<I, ?> configuration) {
		IterationConfigImpl<I> config = (IterationConfigImpl<I>)configuration;
		iterate(
			config.items,
			config.predicateForParallelIteration,
			null,
			(BiConsumer<I, Consumer<Consumer<Collection<?>>>>)config.action,
			config.priority
		);
	}
	
	private <I, C> C iterate(
		Collection<I> items,
		Predicate<Collection<?>> predicateForParallelIteration,
		C output,
		BiConsumer<I, Consumer<Consumer<C>>> action,
		Integer priority
	) {
		Thread currentThread = Thread.currentThread();
		int initialThreadPriority = currentThread.getPriority();
		if (priority == null) {
			priority = initialThreadPriority;
		}
		if (predicateForParallelIteration == null) {
			predicateForParallelIteration = this.defaultMinimumCollectionSizeForParallelIterationPredicate;
		}
		int taskCountThatCanBeCreated = getCountOfTasksThatCanBeCreated(items, predicateForParallelIteration);
		if (taskCountThatCanBeCreated > 1) {
			Consumer<Consumer<C>> outputItemsHandler =
				output != null ?
					isConcurrent(output) ?
						(outputHandler) -> {
							outputHandler.accept(output);
						} :
						(outputHandler) -> {
							synchronized (output) {
								outputHandler.accept(output);
							}
						}
					: null;
			AtomicReference<Throwable> exceptionWrapper = new AtomicReference<>();
			Collection<QueuedTasksExecutor.Task> tasks = new HashSet<>();
			if (items instanceof List) {
				List<I> itemList = (List<I>)items;
				final int splittedIteratorSize = itemList.size() / taskCountThatCanBeCreated;
				for (
					int currentIndex = 0, splittedIteratorIndex = 0; 
					currentIndex < taskCountThatCanBeCreated && exceptionWrapper.get() == null;
					++currentIndex, splittedIteratorIndex+=splittedIteratorSize
				) {
					Iterator<I> itemIterator = itemList.listIterator(splittedIteratorIndex);
					final int itemsCount = currentIndex != taskCountThatCanBeCreated -1 ?
						splittedIteratorSize :
						(itemList.size() - (splittedIteratorSize * currentIndex));
					tasks.add(
						BackgroundExecutor.createTask(
							task -> {					
								try {
									int remainedItems = itemsCount;
									while (exceptionWrapper.get() == null && remainedItems > 0) {
										action.accept(itemIterator.next(), outputItemsHandler);
										--remainedItems;							
									}
								} catch (IterableObjectHelper.TerminateIteration exc) {
									exceptionWrapper.set(exc);
								}
							},
							priority
						).submit()
					);
				}
			} else {
				Iterator<I> itemIterator = items.iterator();
				ThrowingConsumer<QueuedTasksExecutor.Task, ?> iterator = task -> {
					I item = null;
					try {
						while (exceptionWrapper.get() == null) {
							try {
								synchronized (itemIterator) {
									item = itemIterator.next();
								}
							} catch (NoSuchElementException exc) {
								exceptionWrapper.set(IterableObjectHelper.TerminateIteration.NOTIFICATION);
								break;
							}
							action.accept(item, outputItemsHandler);
						}
					} catch (IterableObjectHelper.TerminateIteration exc) {
						exceptionWrapper.set(exc);
					}
				};
				for (int i = 0; i < taskCountThatCanBeCreated && exceptionWrapper.get() == null; i++) {
					tasks.add(
						BackgroundExecutor.createTask(iterator, priority).submit()
					);
				}
			}
			tasks.stream().forEach(task -> task.waitForFinish());
			return output;
		} 
		Consumer<Consumer<C>> outputItemCollectionHandler =
			output != null ?
				(outputCollectionConsumer) -> {
					outputCollectionConsumer.accept(output);
				}
			: null;
		if (initialThreadPriority != priority) {
			currentThread.setPriority(priority);
		}
		try {
			for (I item : items) {
				action.accept(item, outputItemCollectionHandler);
			}
		} catch (IterableObjectHelper.TerminateIteration t) {
		
		} catch (Throwable exc) {
			ManagedLoggersRepository.logError(getClass()::getName, exc);
		} finally {
			if (initialThreadPriority != priority) {
				currentThread.setPriority(initialThreadPriority);
			}
		}
		return output;
	}

	
	private <I> int getCountOfTasksThatCanBeCreated(Collection<I> items, Predicate<Collection<?>> predicate) {
		try {
			if (predicate.test(items) && maxThreadCountsForParallelIteration > ThreadSupplier.getRunningThreadCount()) {
				int taskCount = Math.min(Runtime.getRuntime().availableProcessors(), items.size());
				taskCount = Math.min(ThreadSupplier.getCountOfThreadsThatCanBeSupplied(), taskCount);
				return taskCount;
			}
			return 0;
		} catch (NullPointerException exc) {
			if (maxThreadCountsForParallelIteration == null) {
				Synchronizer.execute(
					getOperationId("initMaxThreadCountsForParallelIteration"), 
					() -> {
						if (maxThreadCountsForParallelIteration == null) {
							maxThreadCountsForParallelIteration = autodetectMaxRuntimeThreadsCountThreshold();
						}
					}
				);				
				return getCountOfTasksThatCanBeCreated(items, predicate);
			}
			throw exc;
		}
	}

	private boolean isConcurrent(Object coll) {
		try {
			for (Class<?> parallelCollectionsClass : parallelCollectionClasses) {
				if (parallelCollectionsClass.isAssignableFrom(coll.getClass())) {
					return true;
				}
			}
			return false;
		} catch (NullPointerException exc) {
			if (this.parallelCollectionClasses == null) {
				Synchronizer.execute(
					getOperationId("initParallelCollectionClassesCollection"),
					() -> {
						if (this.parallelCollectionClasses == null) {
							this.parallelCollectionClasses = parallelCollectionClassesSupplier.get();
						}
					}
				);				
				return isConcurrent(coll);
			}
			throw exc;
		}
	}

	private String toPrettyKeyValueLabel(Entry<?, ?> entry, String valuesSeparator, int marginTabCount) {
		String margin = new String(new char[marginTabCount]).replace('\0', '\t');
		String keyValueLabel = margin + entry.getKey() + "=\\\n" + margin + "\t" + entry.getValue().toString().replace(valuesSeparator, valuesSeparator + "\\\n" + margin +"\t");
		keyValueLabel = keyValueLabel.endsWith(valuesSeparator + "\\\n" + margin +"\t")? keyValueLabel.substring(0, keyValueLabel.lastIndexOf("\\\n" + margin + "\t")) : keyValueLabel;
		return keyValueLabel;
	}

	@Override
	public String toPrettyString(Map<?, ?> map, String valuesSeparator, int marginTabCount) {
		TreeMap<?, ?> allValues = map instanceof TreeMap ? (TreeMap<?, ?>)map : new TreeMap<>(map);
		return allValues.entrySet().stream().map(entry -> toPrettyKeyValueLabel(entry, valuesSeparator, marginTabCount)).collect(Collectors.joining("\n"));
	}

	@Override
	public <K, V> String toString(Map<K, V> map, int marginTabCount) {
		return toString(map, key -> key.toString(), value -> value.toString(), marginTabCount);
	}

	@Override
	public <K, V> String toString(Map<K, V> map, Function<K, String> keyTransformer, Function<V,String> valueTransformer,int marginTabCount) {
		TreeMap<K, V> allValues = map instanceof TreeMap ? (TreeMap<K, V>)map : new TreeMap<>(map);
		String margin = new String(new char[marginTabCount]).replace('\0', '\t');
		return allValues.entrySet().stream().map(entry ->
			margin + keyTransformer.apply(entry.getKey()) + "=" + Optional.ofNullable(entry.getValue()).map(value -> valueTransformer.apply(value)).orElseGet(() -> "null")
		).collect(Collectors.joining("\n"));
	}

	private class ArrayList<E> extends java.util.ArrayList<E> {

		private static final long serialVersionUID = -8096435103182655041L;

	}
	
}