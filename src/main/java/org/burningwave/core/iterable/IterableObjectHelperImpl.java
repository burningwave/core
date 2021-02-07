package org.burningwave.core.iterable;

import static org.burningwave.core.assembler.StaticComponentContainer.BackgroundExecutor;
import static org.burningwave.core.assembler.StaticComponentContainer.Objects;
import static org.burningwave.core.assembler.StaticComponentContainer.Strings;
import static org.burningwave.core.assembler.StaticComponentContainer.Synchronizer;
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.burningwave.core.concurrent.QueuedTasksExecutor;
import org.burningwave.core.function.ThrowingBiConsumer;
import org.burningwave.core.function.ThrowingConsumer;
import org.burningwave.core.iterable.Properties.Event;

@SuppressWarnings("unchecked")
public class IterableObjectHelperImpl implements IterableObjectHelper, Properties.Listener {
	private String defaultValuesSeparator;
	private int maxThreadCountsForParallelIteration;
	
	IterableObjectHelperImpl(String defaultValuesSeparator, int maxThreadCountsForParallelIteration) {
		if (defaultValuesSeparator == null || defaultValuesSeparator.isEmpty()) {
			defaultValuesSeparator = (String)Configuration.DEFAULT_VALUES.get(Configuration.Key.DEFAULT_VALUES_SEPERATOR);
		}
		this.defaultValuesSeparator = defaultValuesSeparator;
		this.maxThreadCountsForParallelIteration = maxThreadCountsForParallelIteration;
	}

	@Override
	public String getDefaultValuesSeparator() {
		return this.defaultValuesSeparator;
	}

	static int computeMatxRuntimeThreadsCountThreshold(Properties config) {
		try {
			return Objects.toInt(
				config.getProperty(Configuration.Key.PARELLEL_ITERATION_APPLICABILITY_MAX_RUNTIME_THREADS_COUNT_THRESHOLD)
			);
		} catch (Throwable exc) {
			return Runtime.getRuntime().availableProcessors() * 12;
		}
	}
	
	@Override
	public <K, V> void processChangeNotification(Properties properties, Event event, K key, V newValue, V previousValue) {
		if (event.name().equals(Event.PUT.name()) && key.equals(Configuration.Key.DEFAULT_VALUES_SEPERATOR) && newValue != null) {
			this.defaultValuesSeparator = (String)newValue;
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
			itr.remove();
			itemDestroyer.accept(entry.getKey(), entry.getValue());
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

////////////////////	
	
	@Override
	public <T> T resolveValue(Map<?,?> map, String key) {
		return resolveValue(key, () -> resolve(map, key, null, null, false, null));
	}	
	
	@Override
	public <T> Collection<T> resolveValues(Map<?,?> map, String key) {
		return resolve(map, key, null, null, false, null);
	}	
	
	@Override
	public Collection<String> resolveStringValues(Map<?,?> map, String key) {
		return resolveValues(map, key);
	}
	
	@Override
	public String resolveStringValue(Map<?,?> map, String key) {
		return resolveValue(map, key);
	}

////////////////////
	
	@Override
	public <T> T resolveValue(Map<?,?> map, String key, Map<String, ?> defaultValues) {
		return resolveValue(key, () -> resolve(map, key, null, null, false, defaultValues));
	}	
	
	@Override
	public <T> Collection<T> resolveValues(Map<?,?> map, String key, Map<String, ?> defaultValues) {
		return resolve(map, key, null, null, false, defaultValues);
	}
	
	@Override
	public String resolveStringValue(Map<?,?> map, String key, Map<String, ?> defaultValues) {
		return resolveValue(map, key, defaultValues);
	}
	
	@Override
	public Collection<String> resolveStringValues(Map<?,?> map, String key, Map<String, ?> defaultValues) {
		return resolveValues(map, key, defaultValues);
	}

////////////////////
	
	@Override
	public <T> T resolveValue(Map<?,?> map, String key, String valuesSeparator) {
		return resolveValue(key, () -> resolve(map, key, valuesSeparator, null, false, null));
	}	
	
	@Override
	public <T> Collection<T> resolveValues(Map<?,?> map, String key, String valuesSeparator) {
		return resolve(map, key, valuesSeparator, null, false, null);
	}
	
	@Override
	public String resolveStringValue(Map<?,?> map, String key, String valuesSeparator) {
		return resolveValue(map, key, valuesSeparator);
	}
	
	@Override
	public Collection<String> resolveStringValues(Map<?,?> map, String key, String valuesSeparator) {
		return resolveValues(map, key, valuesSeparator);
	}
	
////////////////////

	
	@Override
	public <T> T resolveValue(
		Map<?,?> map,
		String key,
		String valuesSeparator,
		boolean deleteUnresolvedPlaceHolder
	) {
		return resolveValue(key, () -> resolve(map, key, valuesSeparator, null, deleteUnresolvedPlaceHolder, null));
	}

	@Override
	public <T> Collection<T> resolveValues(
		Map<?,?> map, String key,
		String valuesSeparator,
		boolean deleteUnresolvedPlaceHolder
	) {
		return resolve(map, key, valuesSeparator, null, deleteUnresolvedPlaceHolder, null);
	}

	@Override
	public String resolveStringValue(
		Map<?,?> map,
		String key,
		String valuesSeparator,
		boolean deleteUnresolvedPlaceHolder
	) {
		return resolveValue(map, key, valuesSeparator, deleteUnresolvedPlaceHolder);
	}

	@Override
	public Collection<String> resolveStringValues(
		Map<?,?> map,
		String key,
		String valuesSeparator,
		boolean deleteUnresolvedPlaceHolder
	) {
		return resolveValues(map, key, valuesSeparator, deleteUnresolvedPlaceHolder);
	}
	
////////////////////
	@Override
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

	@Override
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

	@Override
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

	@Override
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
			return values.stream().findFirst().orElseGet(() -> null);
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
								valueObjects = System.getProperty(placeHolder.split(":")[1]);
								if (valuesSeparatorForSplitting != null) {
									valueObjects = ((String)valueObjects).replace(
										System.getProperty("path.separator"), valuesSeparatorForSplitting
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
	public <T, O> Collection<O> iterateParallelIf(
		Collection<T> items, 		
		Consumer<T> action,
		Predicate<Collection<T>> predicate
	) {
		return iterateParallelIf(items, (item, outputItemCollector) -> action.accept(item), null, predicate);
	}
	
	
	@Override
	public <T, O> Collection<O> iterateParallelIf(
		Collection<T> items, 		
		BiConsumer<T, Consumer<O>> action,
		Collection<O> outputCollection,
		Predicate<Collection<T>> predicate
	) {
		if (predicate.test(items) && maxThreadCountsForParallelIteration >= Synchronizer.getAllThreads().length) {
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
	
	@Override
	public <T, O> void iterateParallel(
		Collection<T> items,
		Consumer<T> action
	) {
		iterateParallel(
			items, (item, outputItemCollector) -> 
				action.accept(item), 
			null
		);
	}

	
	@Override
	public <T, O> Collection<O> iterateParallel(
		Collection<T> items,
		BiConsumer<T, Consumer<O>> action,
		Collection<O> outputCollection
	) {
		Iterator<T> itemIterator = items.iterator();
		Consumer<O> outputItemCollector =
			outputCollection != null ? 
				outputCollection instanceof ConcurrentHashMap.KeySetView ||
				outputCollection instanceof CopyOnWriteArrayList ||
				outputCollection instanceof CopyOnWriteArraySet ?	
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
					while (true) {
						T item = null;
						try {
							synchronized (itemIterator) {
								item = itemIterator.next();
							}
						} catch (NoSuchElementException exc) {
							break;
						}						
						action.accept(item, outputItemCollector);
					}
				}).submit()
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