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
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.burningwave.core.Component;
import org.burningwave.core.reflection.PropertyAccessor;

@SuppressWarnings("unchecked")
public class IterableObjectHelper implements Component {
	private PropertyAccessor propertyAccessor;
	
	private Pattern PLACE_HOLDER_FOR_PROPERTIES_PATTERN = Pattern.compile("\\$\\{([\\w\\d\\.\\:\\-]*)\\}");
	
	
	private IterableObjectHelper(PropertyAccessor propertyAccessor) {
		this.propertyAccessor = propertyAccessor;
	}
	
	public static IterableObjectHelper create(PropertyAccessor propertyAccessor) {
		return new IterableObjectHelper(propertyAccessor);
	}
	
	public <T> Stream<T> retrieveStream(Object object, String propertyPath) {
		return retrieveStream(propertyAccessor.get(object, propertyPath));
	}
	
	public <T> Stream<T> retrieveStream(Object object) {
		Stream<T> stream = null;
		if (object != null) {
			if (object instanceof Collection) {
				return ((Collection<T>)object).stream();
			} else if (object.getClass().isArray()) {
				return Stream.of((T[])object);
			} else if (object instanceof Map) {
				return ((Map<T, ?>)object).keySet().stream();
			}
		}
		return stream;
	}

	public long getSize(Object object) {
		return retrieveStream(object).count();
	}
	
	public <T> T get(Properties properties, String propertyName) {
		return get(properties, propertyName, null);
	}
	
	public <T> boolean containsValue(Properties properties, String propertyName, Map<String, Object> defaultValues, String toBeTested) {
		T propertyValueObject = (T)properties.get(propertyName);
		if (propertyValueObject == null && defaultValues != null) {
			propertyValueObject = (T)defaultValues.get(propertyName);
		}
		if (propertyValueObject instanceof String && !Strings.isEmpty((String)propertyValueObject)) {
			String propertyValue = (String)propertyValueObject;
			if (propertyValue.contains(toBeTested)) {
				return true;
			}
			Map<Integer, List<String>> subProperties = Strings.extractAllGroups(PLACE_HOLDER_FOR_PROPERTIES_PATTERN, propertyValue);		
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
		return propertyValueObject != null;
	}
	
	
	public <T> T get(Properties properties, String propertyName, Map<String, Object> defaultValues) {
		T propertyValueObject = (T)properties.get(propertyName);
		if (propertyValueObject == null && defaultValues != null) {
			propertyValueObject = (T)defaultValues.get(propertyName);
		}
		if (propertyValueObject instanceof String && !Strings.isEmpty((String)propertyValueObject)) {
			String propertyValue = (String)propertyValueObject;
			Map<Integer, List<String>> subProperties = Strings.extractAllGroups(PLACE_HOLDER_FOR_PROPERTIES_PATTERN, propertyValue);		
			if (!subProperties.isEmpty()) {
				for (Map.Entry<Integer, List<String>> entry : subProperties.entrySet()) {
					for (String propName : entry.getValue()) {
						if (!propName.startsWith("system.properties:")) {
							propertyValue = propertyValue.replace("${" + propName + "}", get(properties, propName, defaultValues));
						} else {
							propertyValue = propertyValue.replace("${" + propName + "}", System.getProperty(propName.split(":")[1]));
						}
					}
				}
			}
			return (T)propertyValue;
		} else {
			return propertyValueObject;
		}
		
	}
}
