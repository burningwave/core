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

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Predicate;
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
	
	public boolean containsValue(Properties properties, String propertyName, Map<String, String> defaultValues, String toBeTested) {
		String propertyValue = (String)properties.get(propertyName);
		if (Strings.isEmpty(propertyValue) && defaultValues != null) {
			propertyValue = defaultValues.get(propertyName);
		}
		if (!Strings.isEmpty(propertyValue)) {
			if (propertyValue.contains(toBeTested)) {
				return true;
			}
			Map<Integer, List<String>> subProperties = Strings.extractAllGroups(Strings.PLACE_HOLDER_NAME_EXTRACTOR_PATTERN, propertyValue);		
			if (!subProperties.isEmpty()) {
				for (Map.Entry<Integer, List<String>> entry : subProperties.entrySet()) {
					for (String propName : entry.getValue()) {
						return containsValue(properties, propName, defaultValues, toBeTested);
					}
				}
			}
		}
		return false;
	}
	
	public <T> T resolve(Map<?,?> map, String key) {
		return resolve(map, key, null, false, null);
	}
	
	public <T> T resolve(Map<?,?> map, String key, Map<String, ?> defaultValues) {
		return resolve(map, key, null, false, defaultValues);
	}
	
	public <T> T resolve(Map<?,?> map, Object key, String valuesSeparator) {
		return resolve(map, key, valuesSeparator, false, null);
	}
	

	public <T> T resolve(
		Map<?,?> map, String key,
		String valuesSeparator, boolean deleteUnresolvedPlaceHolder
	) {
		return resolve(map, key, valuesSeparator, deleteUnresolvedPlaceHolder, null);
	}
	
	public <T> T resolve(
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
			String propertyValue = (String)value;
			if (!Strings.isEmpty(propertyValue)) {
				Map<Integer, List<String>> subProperties = Strings.extractAllGroups(Strings.PLACE_HOLDER_NAME_EXTRACTOR_PATTERN, propertyValue);		
				if (!subProperties.isEmpty()) {
					for (Map.Entry<Integer, List<String>> entry : subProperties.entrySet()) {
						for (String propName : entry.getValue()) {
							String replacement;
							if (!propName.startsWith("system.properties:")) {
								replacement = resolve(map, propName, valuesSeparator, deleteUnresolvedPlaceHolder, defaultValues);
							} else {
								replacement = System.getProperty(propName.split(":")[1]);
								if (valuesSeparator != null) {
									replacement = replacement.replace(
										System.getProperty("path.separator"), valuesSeparator
									);
								}
							}
							if (deleteUnresolvedPlaceHolder && replacement == null) {
								propertyValue = propertyValue.replaceAll(Strings.placeHolderToRegEx("${" + propName + "}") + ".*?" + valuesSeparator, "");
							} else if (replacement != null) {
								if (valuesSeparator == null) {
									propertyValue = propertyValue.replace("${" + propName + "}", replacement);
								} else {
									String finalPropertyValue = "";
									for (String replacementUnit : replacement.split(valuesSeparator)) {
										finalPropertyValue += propertyValue.replace("${" + propName + "}", replacementUnit)+
											(replacementUnit.endsWith(valuesSeparator) ?
													"" : valuesSeparator);
									}
									propertyValue = finalPropertyValue;
								}
							}
						}
					}
				}
				
			}
			return (T)propertyValue;
		} else {
			return value;
		}
		
	}
	
	public Collection<String> getAllPlaceHolders(Properties properties) {
		return getAllPlaceHolders(properties, object -> true);
	}
	
	public Collection<String> getAllPlaceHolders(Properties properties, Predicate<String> propertyFilter) {
		Collection<String> placeHolders = new HashSet<>();
		for (Map.Entry<Object, Object> entry : properties.entrySet().stream().filter(entry -> (entry.getValue() == null || entry.getValue() instanceof String) && propertyFilter.test((String) entry.getKey())).collect(Collectors.toSet())) {
			String value = (String)entry.getValue();
			for(List<String> placeHoldersFound : Strings.extractAllGroups(Strings.PLACE_HOLDER_EXTRACTOR_PATTERN, value).values()) {
				placeHolders.addAll(placeHoldersFound);
			}
		}
		return placeHolders;
	}
	
	public Collection<String> getAllPlaceHolders(Properties properties, String propertyName) {
		Collection<String> placeHolders = getAllPlaceHolders(properties);
		Iterator<String> placeHoldersItr = placeHolders.iterator();
		while (placeHoldersItr.hasNext()) {
			if (!containsValue(properties, propertyName, null, placeHoldersItr.next())) {
				placeHoldersItr.remove();
			}
		}
		return placeHolders;
	}

}
