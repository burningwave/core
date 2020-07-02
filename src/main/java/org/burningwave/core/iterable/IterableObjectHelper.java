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

import static org.burningwave.core.assembler.StaticComponentContainer.Strings;
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.burningwave.core.Component;

@SuppressWarnings("unchecked")
public class IterableObjectHelper implements Component {
	private IterableObjectHelper() {}
	
	public static IterableObjectHelper create() {
		return new IterableObjectHelper();
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
	
	private <T> T resolve(Map<?,?> map, String key) {
		return resolve(map, key, null, false, null);
	}
	
	public <T> T resolveObjectValue(Map<?,?> map, String key) {
		return resolveObjectValue(key, () -> resolve(map, key));
	}	
	
	public <T> Collection<T> resolveObjectValues(Map<?,?> map, String key) {
		return resolve(map, key);
	}	
	
	public Collection<String> resolveStringValues(Map<?,?> map, String key) {
		return resolveObjectValues(map, key);
	}
	
	public String resolveStringValue(Map<?,?> map, String key) {
		return resolveObjectValue(map, key);
	}

////////////////////
	
	private <T> T resolve(Map<?,?> map, String key, Map<String, ?> defaultValues) {
		return resolve(map, key, null, false, defaultValues);
	}
	
	public <T> T resolveObjectValue(Map<?,?> map, String key, Map<String, ?> defaultValues) {
		return resolveObjectValue(key, () -> resolve(map, key, defaultValues));
	}	
	
	public <T> Collection<T> resolveObjectValues(Map<?,?> map, String key, Map<String, ?> defaultValues) {
		return resolve(map, key, defaultValues);
	}
	
	public String resolveStringValue(Map<?,?> map, String key, Map<String, ?> defaultValues) {
		return resolveObjectValue(map, key, defaultValues);
	}
	
	public Collection<String> resolveStringValues(Map<?,?> map, String key, Map<String, ?> defaultValues) {
		return resolveObjectValues(map, key, defaultValues);
	}

////////////////////
	
	private <T> T resolve(Map<?,?> map, Object key, String valuesSeparator) {
		return resolve(map, key, valuesSeparator, false, null);
	}
	
	public <T> T resolveObjectValue(Map<?,?> map, String key, String valuesSeparator) {
		return resolveObjectValue(key, () -> resolve(map, key, valuesSeparator));
	}	
	
	public <T> Collection<T> resolveObjectValues(Map<?,?> map, String key, String valuesSeparator) {
		return resolve(map, key, valuesSeparator);
	}
	
	public String resolveStringValue(Map<?,?> map, String key, String valuesSeparator) {
		return resolveObjectValue(map, key, valuesSeparator);
	}
	
	public Collection<String> resolveStringValues(Map<?,?> map, String key, String valuesSeparator) {
		return resolveObjectValues(map, key, valuesSeparator);
	}
	
////////////////////
	
	private <T> T resolve(
		Map<?,?> map,
		String key,
		String valuesSeparator, 
		boolean deleteUnresolvedPlaceHolder
	) {
		return resolve(map, key, valuesSeparator, deleteUnresolvedPlaceHolder, null);
	}
	
	public <T> T resolveObjectValue(
		Map<?,?> map,
		String key,
		String valuesSeparator,
		boolean deleteUnresolvedPlaceHolder
	) {
		return resolveObjectValue(key, () -> resolve(map, key, valuesSeparator, deleteUnresolvedPlaceHolder));
	}

	public <T> Collection<T> resolveObjectValues(
		Map<?,?> map, String key,
		String valuesSeparator,
		boolean deleteUnresolvedPlaceHolder
	) {
		return resolve(map, key, valuesSeparator, deleteUnresolvedPlaceHolder);
	}

	public String resolveStringValue(
		Map<?,?> map,
		String key,
		String valuesSeparator,
		boolean deleteUnresolvedPlaceHolder
	) {
		return resolveObjectValue(map, key, valuesSeparator, deleteUnresolvedPlaceHolder);
	}

	public Collection<String> resolveStringValues(
		Map<?,?> map,
		String key,
		String valuesSeparator,
		boolean deleteUnresolvedPlaceHolder
	) {
		return resolveObjectValues(map, key, valuesSeparator, deleteUnresolvedPlaceHolder);
	}
	
////////////////////

		
	public <T> T resolveObjectValue(
		Map<?,?> map,
		String key,
		String valuesSeparator,
		boolean deleteUnresolvedPlaceHolder,
		Map<?,?> defaultValues
	) {
		return resolveObjectValue(key, () -> resolve(map, key, valuesSeparator, deleteUnresolvedPlaceHolder, defaultValues));
	}

	public <T> Collection<T> resolveObjectValues(
		Map<?,?> map, String key,
		String valuesSeparator,
		boolean deleteUnresolvedPlaceHolder,
		Map<?,?> defaultValues
	) {
		return resolve(map, key, valuesSeparator, deleteUnresolvedPlaceHolder, defaultValues);
	}

	public String resolveStringValue(
		Map<?,?> map,
		String key,
		String valuesSeparator,
		boolean deleteUnresolvedPlaceHolder,
		Map<?,?> defaultValues
	) {
		return resolveObjectValue(map, key, valuesSeparator, deleteUnresolvedPlaceHolder, defaultValues);
	}

	public Collection<String> resolveStringValues(
		Map<?,?> map,
		String key,
		String valuesSeparator,
		boolean deleteUnresolvedPlaceHolder,
		Map<?,?> defaultValues
	) {
		return resolveObjectValues(map, key, valuesSeparator, deleteUnresolvedPlaceHolder, defaultValues);
	}
	
////////////////////	
	
	
	private <T> T resolveObjectValue(String key, Supplier<Object> valuesSupplier) {
		Object value = valuesSupplier.get();
		if (value instanceof Collection) {
			Collection<T> values = (Collection<T>)value;
			if (values.size() > 1) {
				throw Throwables.toRuntimeException("Found more than one item under key " + key);
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
		boolean deleteUnresolvedPlaceHolder,
		Map<?,?> defaultValues
	) {
		T value = (T) map.get(key);
		if (value == null && defaultValues != null) {
			value = (T) defaultValues.get(key);
		}
		if (value != null && value instanceof String) {
			String stringValue = (String)value;
			Collection<Object> values = new ArrayList<>();
			if (!Strings.isEmpty(stringValue)) {
				Map<Integer, List<String>> subProperties = Strings.extractAllGroups(Strings.PLACE_HOLDER_NAME_EXTRACTOR_PATTERN, stringValue);		
				if (!subProperties.isEmpty()) {
					for (Map.Entry<Integer, List<String>> entry : subProperties.entrySet()) {
						for (String propName : entry.getValue()) {
							Object valueObjects = null;
							if (!propName.startsWith("system.properties:")) {
								valueObjects = resolve(map, propName, valuesSeparator, deleteUnresolvedPlaceHolder, defaultValues);
							} else {
								valueObjects = System.getProperty(propName.split(":")[1]);
								if (valuesSeparator != null) {
									valueObjects = ((String)valueObjects).replace(
										System.getProperty("path.separator"), valuesSeparator
									);
								}
							}
							if (deleteUnresolvedPlaceHolder && valueObjects == null) {
								stringValue = stringValue.replaceAll(Strings.placeHolderToRegEx("${" + propName + "}") + ".*?" + Optional.ofNullable(valuesSeparator).orElseGet(() -> ""), "");
								values.add(stringValue);
							} else if (valueObjects != null) {
								Collection<Object> replacements = new ArrayList<>();
								if (valueObjects instanceof String) {
									replacements.add(valueObjects);
								} else if (valueObjects instanceof Collection) {
									replacements.addAll((Collection<?>)valueObjects);
								} else {
									replacements.add(valueObjects);
								}
								for (Object valueObject : replacements) {
									if (valueObject instanceof String) {
										String replacement = (String)valueObject;
										if (valuesSeparator == null) {
											values.add(stringValue.replace("${" + propName + "}", replacement));
										} else {
											for (String replacementUnit : replacement.split(valuesSeparator)) {
												String valuesToAdd = stringValue.replace("${" + propName + "}", replacementUnit);
												if (valuesToAdd.contains(valuesSeparator)) {
													for (String valueToAdd : valuesToAdd.split(valuesSeparator)) {
														values.add(valueToAdd);
													}
												} else {
													values.add(valuesToAdd);
												}
											}
										}
									} else {
										values.add(valueObject);
									}
								}
							} else {
								values.add(stringValue);
							}
						}
					}
				} else {
					values.add(stringValue);
				}
			} else {
				values.add(stringValue);
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
}
