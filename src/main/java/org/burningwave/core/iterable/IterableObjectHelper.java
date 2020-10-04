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
import static org.burningwave.core.assembler.StaticComponentContainer.Strings;
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.burningwave.core.Component;
import org.burningwave.core.concurrent.QueuedTasksExecutor;
import org.burningwave.core.function.ThrowingBiConsumer;
import org.burningwave.core.function.ThrowingConsumer;
import org.burningwave.core.iterable.Properties.Event;

@SuppressWarnings("unchecked")
public class IterableObjectHelper implements Component {
	
	public static class Configuration {
		public static class Key {
			public final static String DEFAULT_VALUES_SEPERATOR = "iterable-object-helper.default-values-separator";
		}
		
		public final static Map<String, Object> DEFAULT_VALUES;
		
		static {
			Map<String, Object> defaultValues = new HashMap<>();

			defaultValues.put(Key.DEFAULT_VALUES_SEPERATOR, ";");
						
			DEFAULT_VALUES = Collections.unmodifiableMap(defaultValues);
		}
	}
	
	private String defaultValuesSeparator;
	
	private IterableObjectHelper(String defaultValuesSeparator) {
		if (defaultValuesSeparator == null || defaultValuesSeparator.isEmpty()) {
			defaultValuesSeparator = (String)Configuration.DEFAULT_VALUES.get(Configuration.Key.DEFAULT_VALUES_SEPERATOR);
		}
		this.defaultValuesSeparator = defaultValuesSeparator;
	}

	public String getDefaultValuesSeparator() {
		return this.defaultValuesSeparator;
	}
	
	public static IterableObjectHelper create(Properties globalproperties) {
		IterableObjectHelper iterableObjectHelper = new IterableObjectHelper(globalproperties.getProperty(Configuration.Key.DEFAULT_VALUES_SEPERATOR));
		iterableObjectHelper.listenTo(globalproperties);
		return iterableObjectHelper;
	}
	
	@Override
	public <K, V> void processChangeNotification(Properties properties, Event event, K key, V newValue, V previousValue) {
		if (event.name().equals(Event.PUT.name()) && key.equals(Configuration.Key.DEFAULT_VALUES_SEPERATOR) && newValue != null) {
			this.defaultValuesSeparator = (String)newValue;
		}
	}
	
	public <K, V> void deepClear(Map<K,V> map) {
		Iterator<Entry<K, V>> itr = map.entrySet().iterator();
		while (itr.hasNext()) {
			itr.next();
			itr.remove();
		}
	}
	
	public <K, V, E extends Throwable> void deepClear(Map<K,V> map, ThrowingBiConsumer<K, V, E> itemDestroyer) throws E {
		Iterator<Entry<K, V>> itr = map.entrySet().iterator();
		while (itr.hasNext()) {
			Entry<K, V> entry = itr.next();
			itr.remove();
			itemDestroyer.accept(entry.getKey(), entry.getValue());
		}
	}
	
	public <V> void deepClear(Collection<V> map) {
		Iterator<V> itr = map.iterator();
		while (itr.hasNext()) {
			itr.next();
			itr.remove();
		}
	}
	
	public <V, E extends Throwable > void deepClear(Collection<V> map, ThrowingConsumer<V, E> itemDestroyer) throws E {
		Iterator<V> itr = map.iterator();
		while (itr.hasNext()) {
			itr.remove();
			itemDestroyer.accept(itr.next());
		}
	}
	
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
	
	public <T> T getRandom(Collection<T> coll) {
		int num = (int) (Math.random() * coll.size());
	    for(T t: coll) {
	    	if (--num < 0) return t;
	    }
	    return null;
	}
	
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

	public long getSize(Object object) {
		return retrieveStream(object).count();
	}

////////////////////	
	
	public <T> T resolveValue(Map<?,?> map, String key) {
		return resolveValue(key, () -> resolve(map, key, null, null, false, null));
	}	
	
	public <T> Collection<T> resolveValues(Map<?,?> map, String key) {
		return resolve(map, key, null, null, false, null);
	}	
	
	public Collection<String> resolveStringValues(Map<?,?> map, String key) {
		return resolveValues(map, key);
	}
	
	public String resolveStringValue(Map<?,?> map, String key) {
		return resolveValue(map, key);
	}

////////////////////
	
	public <T> T resolveValue(Map<?,?> map, String key, Map<String, ?> defaultValues) {
		return resolveValue(key, () -> resolve(map, key, null, null, false, defaultValues));
	}	
	
	public <T> Collection<T> resolveValues(Map<?,?> map, String key, Map<String, ?> defaultValues) {
		return resolve(map, key, null, null, false, defaultValues);
	}
	
	public String resolveStringValue(Map<?,?> map, String key, Map<String, ?> defaultValues) {
		return resolveValue(map, key, defaultValues);
	}
	
	public Collection<String> resolveStringValues(Map<?,?> map, String key, Map<String, ?> defaultValues) {
		return resolveValues(map, key, defaultValues);
	}

////////////////////
	
	public <T> T resolveValue(Map<?,?> map, String key, String valuesSeparator) {
		return resolveValue(key, () -> resolve(map, key, valuesSeparator, null, false, null));
	}	
	
	public <T> Collection<T> resolveValues(Map<?,?> map, String key, String valuesSeparator) {
		return resolve(map, key, valuesSeparator, null, false, null);
	}
	
	public String resolveStringValue(Map<?,?> map, String key, String valuesSeparator) {
		return resolveValue(map, key, valuesSeparator);
	}
	
	public Collection<String> resolveStringValues(Map<?,?> map, String key, String valuesSeparator) {
		return resolveValues(map, key, valuesSeparator);
	}
	
////////////////////

	
	public <T> T resolveValue(
		Map<?,?> map,
		String key,
		String valuesSeparator,
		boolean deleteUnresolvedPlaceHolder
	) {
		return resolveValue(key, () -> resolve(map, key, valuesSeparator, null, deleteUnresolvedPlaceHolder, null));
	}

	public <T> Collection<T> resolveValues(
		Map<?,?> map, String key,
		String valuesSeparator,
		boolean deleteUnresolvedPlaceHolder
	) {
		return resolve(map, key, valuesSeparator, null, deleteUnresolvedPlaceHolder, null);
	}

	public String resolveStringValue(
		Map<?,?> map,
		String key,
		String valuesSeparator,
		boolean deleteUnresolvedPlaceHolder
	) {
		return resolveValue(map, key, valuesSeparator, deleteUnresolvedPlaceHolder);
	}

	public Collection<String> resolveStringValues(
		Map<?,?> map,
		String key,
		String valuesSeparator,
		boolean deleteUnresolvedPlaceHolder
	) {
		return resolveValues(map, key, valuesSeparator, deleteUnresolvedPlaceHolder);
	}
	
////////////////////
	public <T> T resolveValue(
		Map<?,?> map,
		String key,
		String valuesSeparator,
		String defaultValuesSeparator,
		boolean deleteUnresolvedPlaceHolder,
		Map<?,?> defaultValues
	) {
		return resolveValue(key, () -> resolve(map, key, valuesSeparator, defaultValuesSeparator, deleteUnresolvedPlaceHolder, defaultValues));
	}

	public <T> Collection<T> resolveValues(
		Map<?,?> map,
		String key,
		String valuesSeparator,
		String defaultValuesSeparator,
		boolean deleteUnresolvedPlaceHolder,
		Map<?,?> defaultValues
	) {
		return resolve(map, key, valuesSeparator, defaultValuesSeparator, deleteUnresolvedPlaceHolder, defaultValues);
	}

	public String resolveStringValue(
		Map<?,?> map,
		String key,
		String valuesSeparator,
		String defaultValuesSeparator,
		boolean deleteUnresolvedPlaceHolder,
		Map<?,?> defaultValues
	) {
		return resolveValue(map, key, valuesSeparator, defaultValuesSeparator, deleteUnresolvedPlaceHolder, defaultValues);
	}

	public Collection<String> resolveStringValues(
		Map<?,?> map,
		String key,
		String valuesSeparator,
		String defaultValuesSeparator,
		boolean deleteUnresolvedPlaceHolder,
		Map<?,?> defaultValues
	) {
		return resolveValues(map, key, valuesSeparator, defaultValuesSeparator, deleteUnresolvedPlaceHolder, defaultValues);
	}
	
////////////////////	
	
	
	private <T> T resolveValue(String key, Supplier<Object> valuesSupplier) {
		Object value = valuesSupplier.get();
		if (value instanceof Collection) {
			Collection<T> values = (Collection<T>)value;
			if (values.size() > 1) {
				Throwables.throwException("Found more than one item under key {}", key);
			}
			return (T)values.stream().findFirst().orElseGet(() -> null);
		} else {
			return (T)value;
		}	
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
			Collection<Object> values = new IterableObjectHelper.ArrayList<>();
			if (!Strings.isEmpty(stringValue)) {
				Map<Integer, List<String>> subProperties = Strings.extractAllGroups(Strings.PLACE_HOLDER_NAME_EXTRACTOR_PATTERN, stringValue);		
				if (!subProperties.isEmpty()) {
					for (Map.Entry<Integer, List<String>> entry : subProperties.entrySet()) {
						for (String placeHolder : entry.getValue()) {
							Object valueObjects = null;
							if (!placeHolder.startsWith("system.properties:")) {
								valueObjects = resolve(map, placeHolder, valuesSeparator, defaultValueSeparator, deleteUnresolvedPlaceHolder, defaultValues);
							} else {
								valueObjects = System.getProperty(placeHolder.split(":")[1]);
								if (valuesSeparatorForSplitting != null) {
									valueObjects = ((String)valueObjects).replace(
										System.getProperty("path.separator"), valuesSeparatorForSplitting
									);
								}
							}
							if (valueObjects == null) {
								if (deleteUnresolvedPlaceHolder) {
									stringValue = stringValue.replaceAll(Strings.placeHolderToRegEx("${" + placeHolder + "}") + ".*?" + valuesSeparatorForSplitting, "");
								}
								continue;
							}
							Collection<Object> replacements = new ArrayList<>();
							if (valueObjects instanceof IterableObjectHelper.ArrayList) {
								replacements.addAll((Collection<?>)valueObjects);
							} else {
								replacements.add(valueObjects);
							}
							String regExpPattern = "("+Strings.placeHolderToRegEx("${" + placeHolder + "}") + ".*?" + valuesSeparatorForSplitting +")";
							Map<Integer, List<String>> placeHolderedValues = Strings.extractAllGroups(
								Pattern.compile(regExpPattern), stringValue 
							);
							if (placeHolderedValues.isEmpty()) {
								regExpPattern = "("+Strings.placeHolderToRegEx("${" + placeHolder + "}") + ".*?)";
								placeHolderedValues = Strings.extractAllGroups(
									Pattern.compile(regExpPattern), stringValue 
								);
							}									
							for (Map.Entry<Integer, List<String>> placeHolderedValuesEntry : placeHolderedValues.entrySet()) {												
								for (String placeHolderedValue : placeHolderedValuesEntry.getValue()) {
									String newReplacement = "";
									for (Object valueObject : replacements) {
										if (valueObject instanceof String) {
											String replacement = (String)valueObject;
											if (valuesSeparator != null) {
												for (String replacementUnit : replacement.split(valuesSeparatorForSplitting)) {
													newReplacement += placeHolderedValue.replace("${" + placeHolder + "}", replacementUnit);
													newReplacement += newReplacement.endsWith(valuesSeparatorForSplitting) ? "" : valuesSeparatorForSplitting;
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
							for (String valueToAdd : stringValue.split(valuesSeparatorForSplitting)) {
								values.add(valueToAdd);
							}
						}
					}
				} else {
					if (valuesSeparator != null) {
						for (String valueToAdd : stringValue.split(valuesSeparatorForSplitting)) {
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
	
	public Collection<String> getAllPlaceHolders(Map<?, ?> map) {
		return getAllPlaceHolders(map, object -> true);
	}
	
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
	
	public boolean containsValue(Map<?, ?> map, String key, Object object) {
		return containsValue(map, key, object, null);		
	}
	
	public <K, V> void refresh(Map<K, V> source, Map<K, V> newValues) {
		Collection<K> keyToBeRemoved = new HashSet<>();
		Map<K, V> keyAndValuesToBePut = new HashMap<>();
		for(Map.Entry<K, V> keyAndValue : source.entrySet()) {
			K key = keyAndValue.getKey();
			if (newValues.containsKey(key)) {
				V oldValue = newValues.get(key);
				V newValue = newValues.get(key);
				if (!Objects.equals(oldValue, newValue)) {
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
	
	public <T, O> Collection<O> iterateParallelIf(
		Collection<T> items, 		
		Consumer<T> action,
		Predicate<Collection<T>> predicate
	) {
		return iterateParallelIf(items, (item, collector) -> action.accept(item), null, predicate);
	}
	
	
	public <T, O> Collection<O> iterateParallelIf(
		Collection<T> items, 		
		BiConsumer<T, Consumer<O>> action,
		Collection<O> outputCollection,
		Predicate<Collection<T>> predicate
	) {
		if (predicate.test(items) ) {
			return iterateParallel(items, action, outputCollection);
		} else {
			Consumer<O> outputItemCollector = outputCollection != null ? 
				outputItem -> {
					outputCollection.add(outputItem);
				} 
				: null;
			for (T item : items) {
				action.accept(item, outputItemCollector);
			}
			return outputCollection;
		}		
	}
	
	public <T, O> Collection<O> iterateParallel(
		Collection<T> items,
		BiConsumer<T, Consumer<O>> action,
		Collection<O> outputCollection
	) {
		Iterator<T> itemIterator = items.iterator();
		Consumer<O> outputItemCollector =
			outputCollection != null ? 
				outputCollection instanceof ConcurrentHashMap.KeySetView ||
				outputCollection instanceof CopyOnWriteArrayList?	
					outputItem -> {
						outputCollection.add(outputItem);
					} : 
					outputItem -> {
						synchronized (outputCollection) {
							outputCollection.add(outputItem);
						}
					}
				: null;
		Collection<QueuedTasksExecutor.Task> tasks = new HashSet<>();
		int taskCount = Math.min(Runtime.getRuntime().availableProcessors(), items.size());
		for (int i = 0; i < taskCount; i++) {
			tasks.add(
				BackgroundExecutor.createTask(() -> {
					while (itemIterator.hasNext()) {
						T item = null;
						synchronized (itemIterator) {
							try {
								item = itemIterator.next();
							} catch (NoSuchElementException exc) {
								break;
							}
						}
						action.accept(item, outputItemCollector);
					}
				}).async().submit()
			);
		}
		tasks.stream().forEach(task -> task.waitForFinish());
		return outputCollection;
	}

	
	private String toPrettyKeyValueLabel(Entry<?, ?> entry, String valuesSeparator, int marginTabCount) {
		String margin = new String(new char[marginTabCount]).replace('\0', '\t');
		String keyValueLabel = margin + entry.getKey() + "=\\\n" + margin + "\t" + entry.getValue().toString().replace(valuesSeparator, valuesSeparator + "\\\n" + margin +"\t");
		keyValueLabel = keyValueLabel.endsWith(valuesSeparator + "\\\n" + margin +"\t")? keyValueLabel.substring(0, keyValueLabel.lastIndexOf("\\\n" + margin + "\t")) : keyValueLabel;
		return keyValueLabel;
	}
	
	public String toPrettyString(Map<?, ?> map, String valuesSeparator, int marginTabCount) {
		TreeMap<?, ?> allValues = map instanceof TreeMap ? (TreeMap<?, ?>)map : new TreeMap<>(map);
		return allValues.entrySet().stream().map(entry -> toPrettyKeyValueLabel(entry, valuesSeparator, marginTabCount)).collect(Collectors.joining("\n"));
	}	
	
	public <K, V> String toString(Map<K, V> map, int marginTabCount) {
		return toString(map, key -> key.toString(), value -> value.toString(), marginTabCount);
	}
	
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
