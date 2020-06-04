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
						if (!propName.startsWith("system.properties:") && containsValue(properties, propName, defaultValues, toBeTested)) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}
	
	public <T> T get(Properties properties, String propertyName) {
		return get(properties, propertyName, null, false, null);
	}
	
	public <T> T get(Properties properties, String propertyName, Map<String, ?> defaultValues) {
		return get(properties, propertyName, null, false, defaultValues);
	}
	
	public <T> T get(Properties properties, String propertyName, String propertyValuesSeparator) {
		return get(properties, propertyName, propertyValuesSeparator, false, null);
	}
	

	public String get(
		Properties properties, String propertyName,
		String propertyValuesSeparator, boolean deleteUnresolvedPlaceHolder
	) {
		return get(properties, propertyName, propertyValuesSeparator, deleteUnresolvedPlaceHolder, null);
	}
	
	public <T> T get(
		Properties properties,
		String propertyName,
		String propertyValuesSeparator,
		boolean deleteUnresolvedPlaceHolder,
		Map<String, ?> defaultValues
	) {
		T value = (T) properties.get(propertyName);
		if (value == null && defaultValues != null) {
			value = (T) defaultValues.get(propertyName);
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
								replacement = get(properties, propName, propertyValuesSeparator, deleteUnresolvedPlaceHolder, defaultValues);
							} else {
								replacement = System.getProperty(propName.split(":")[1]);
								if (propertyValuesSeparator != null) {
									replacement = replacement.replace(
										System.getProperty("path.separator"), propertyValuesSeparator
									);
								}
							}
							if (deleteUnresolvedPlaceHolder && replacement == null) {
								propertyValue = propertyValue.replaceAll(Strings.placeHolderToRegEx("${" + propName + "}") + ".*?" + propertyValuesSeparator, "");
							} else if (replacement != null) {
								if (propertyValuesSeparator == null) {
									propertyValue = propertyValue.replace("${" + propName + "}", replacement);
								} else {
									String finalPropertyValue = "";
									for (String vl : replacement.split(propertyValuesSeparator)) {
										finalPropertyValue += propertyValue.replace("${" + propName + "}", vl)+
											(vl.endsWith(propertyValuesSeparator) ?
													"" : propertyValuesSeparator);
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
