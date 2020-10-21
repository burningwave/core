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
package org.burningwave.core.classes;

import static org.burningwave.core.assembler.StaticComponentContainer.Fields;
import static org.burningwave.core.assembler.StaticComponentContainer.Methods;
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.burningwave.core.Component;
import org.burningwave.core.function.ThrowingBiFunction;
import org.burningwave.core.function.ThrowingFunction;

public abstract class PropertyAccessor implements Component {
	public final static String REG_EXP_FOR_JAVA_PROPERTIES = "([a-zA-Z\\$\\_\\-0-9]*)(\\[*.*)";
	public final static String REG_EXP_FOR_INDEXES_OF_JAVA_INDEXED_PROPERTIES = "\\[([a-zA-Z0-9]*)\\]";

	private List<ThrowingBiFunction<Object, String, Object, Throwable>> propertyRetrievers;
	private List<ThrowingFunction<Object[], Boolean, Throwable>> propertySetters;

	
	PropertyAccessor() {
		this.propertyRetrievers = getPropertyRetrievers();
		this.propertySetters= getPropertySetters();
	}
	
	abstract List<ThrowingFunction<Object[], Boolean, Throwable>> getPropertySetters();

	abstract List<ThrowingBiFunction<Object, String, Object, Throwable>> getPropertyRetrievers();
	
	@SuppressWarnings("unchecked")
	public <T> T get(Object obj, String propertyPath) {
		String[] propertyAddress = propertyPath.split("\\.");
		Object objToReturn = obj;
		for (int j = 0; j < propertyAddress.length; j++) {
			objToReturn = getProperty(j != 0 ? objToReturn : obj,
					propertyAddress[j]);
		}
		return (T)objToReturn;
	}
	
	private Object getProperty(Object obj, String property) {
		Object objToReturn = null;
		Matcher matcher = Pattern.compile(REG_EXP_FOR_JAVA_PROPERTIES).matcher(property);
		matcher.find();
		List<Throwable> exceptions = new ArrayList<>();
		for (ThrowingBiFunction<Object, String, Object, Throwable> retriever : propertyRetrievers) {
			try {
				if ((objToReturn = retriever.apply(obj, matcher.group(1))) != null) {
					break;
				}
			} catch (Throwable exc) {
				exceptions.add(exc);
			}
		}
		manageGetPropertyExceptions(exceptions);
		if (!matcher.group(2).isEmpty()) {
			objToReturn = retrieveFromIndexedProperty(objToReturn, matcher.group(2));
		}
		return objToReturn;
	}

	private void manageGetPropertyExceptions(List<Throwable> exceptions) {
		if (exceptions.size() > 0) {
			String message = "";
			for (Throwable exception : exceptions) {
				message += exception.getMessage() + "\n";	
			}
			message = message.substring(0, message.length() - 1);
			if (exceptions.size() == propertyRetrievers.size()) {
				Throwables.throwException(message.toString());
			} else {
				//logDebug("Warning: " + message);
			}
		}
	}
	
	public void set(Object obj, String propertyPath, Object value) {
		Object target = 
			propertyPath.contains(".")?
				get(obj, propertyPath.substring(0, propertyPath.lastIndexOf("."))) :
				obj;
		String targetPropertyName =
			propertyPath.contains(".")?
				propertyPath.substring(propertyPath.lastIndexOf(".") + 1, propertyPath.length()) :
				propertyPath;
		setProperty(target, targetPropertyName, value);
	}
	
	private void setProperty(Object target, String property,
			Object value) {
		List<Throwable> exceptions = new ArrayList<>();
		for (ThrowingFunction<Object[], Boolean, Throwable> propertySetter : propertySetters) {
			try {
				propertySetter.apply(new Object[] {target, property, value});
				break;
			} catch (Throwable exc) {
				exceptions.add(exc);
			}
		}
		manageGetPropertyExceptions(exceptions);
	}


	private Object retrieveFromIndexedProperty(Object property, String indexes) {
		Matcher matcher = Pattern.compile(REG_EXP_FOR_INDEXES_OF_JAVA_INDEXED_PROPERTIES).matcher(indexes);
		if (matcher.find()) {
			String index = matcher.group(1);
			Supplier<Object> propertyRetriever = null;
			if (property.getClass().isArray()) {
				propertyRetriever = () -> Array.get(property, Integer.valueOf(index));
			} else if (List.class.isAssignableFrom(property.getClass())) {
				propertyRetriever = () -> ((List<?>)property).get(Integer.valueOf(index));
			} else if (Map.class.isAssignableFrom(property.getClass())) {
				propertyRetriever = () -> ((Map<?, ?>)property).get(index);
			} else {
				Throwables.throwException("indexed property {} of type {} is not supporterd", property, property.getClass());
			}
			return retrieveFromIndexedProperty(
					propertyRetriever.get(), 
				indexes.substring(matcher.end(), indexes.length())
			);
		}
		return property;
	}

	Object retrievePropertyByField(Object target, String propertyName) throws IllegalAccessException {
		return Fields.getDirect(target, propertyName);
	}

	Object retrievePropertyByGetterMethod(Object obj, String propertyName) {
		Object objToReturn;
		objToReturn = Methods.invokeDirect(
			obj, 
			Methods.createGetterMethodNameByPropertyName(propertyName)
		);
		return objToReturn;
	}

	@SuppressWarnings("unchecked")
	private <T> void setInIndexedProperty(Object property, String indexes, Object value) {
		Matcher matcher = Pattern.compile(REG_EXP_FOR_INDEXES_OF_JAVA_INDEXED_PROPERTIES).matcher(indexes);
		int lastIndexOf = 0;
		String index = null;
		while (matcher.find()) {
			index = matcher.group(1);
			lastIndexOf = matcher.start();
		}
		Object targetObject = retrieveFromIndexedProperty(property, indexes.substring(0, lastIndexOf));
		if (targetObject.getClass().isArray()) {
			Array.set(targetObject, Integer.valueOf(index), value);
		} else if (List.class.isAssignableFrom(targetObject.getClass())) {
			((List<T>)targetObject).set(Integer.valueOf(index), (T)value);
		} else if (Map.class.isAssignableFrom(property.getClass())) {
			((Map<String, T>)property).put(index, (T)value);
		} else {
			Throwables.throwException("indexed property {} of type {} is not supporterd", property, property.getClass());
		}
	}

	Boolean setPropertyByField(Object target, String propertyPath, Object value) throws IllegalAccessException {
		Matcher matcher = Pattern.compile(REG_EXP_FOR_JAVA_PROPERTIES).matcher(propertyPath);
		matcher.find();
		Field field = Fields.findOneAndMakeItAccessible(target.getClass(),
				matcher.group(1));
		if (matcher.group(2).isEmpty()) {
			Fields.setDirect(target, field, value);
		} else {
			setInIndexedProperty(field.get(target), matcher.group(2), value);
		}
		return Boolean.TRUE;
	}

	Boolean setPropertyByMethod(Object target, String propertyPath, Object value) {
		Matcher matcher = Pattern.compile(REG_EXP_FOR_JAVA_PROPERTIES).matcher(propertyPath);
		matcher.find();
		if (matcher.group(2).isEmpty()) {
			Methods.invokeDirect(
				target, Methods.createSetterMethodNameByPropertyName(matcher.group(1)), value
			);
		} else {
			setInIndexedProperty(Methods.invokeDirect(
				target, Methods.createGetterMethodNameByPropertyName(matcher.group(1))
			), matcher.group(2), value);
		}
		return Boolean.TRUE;
	}
	
	public static class ByFieldOrByMethod extends PropertyAccessor {

		private ByFieldOrByMethod() {
			super();
		}
		
		public static ByFieldOrByMethod create() {
			return new ByFieldOrByMethod();
		}

		@Override
		List<ThrowingBiFunction<Object, String, Object, Throwable>> getPropertyRetrievers() {
			List<ThrowingBiFunction<Object, String, Object, Throwable>> propertyRetrievers = new ArrayList<>();
			propertyRetrievers.add((object, propertyName) -> retrievePropertyByField(object, propertyName));
			propertyRetrievers.add((object, propertyName) -> retrievePropertyByGetterMethod(object, propertyName));
			return propertyRetrievers;
		}

		@Override
		List<ThrowingFunction<Object[], Boolean, Throwable>> getPropertySetters() {
			List<ThrowingFunction<Object[], Boolean, Throwable>> propertySetters  = new ArrayList<>();
			propertySetters.add(objects -> setPropertyByField(objects[0], (String)objects[1], objects[2]));
			propertySetters.add(objects -> setPropertyByMethod(objects[0], (String)objects[1], objects[2]));
			return propertySetters;
		}		
	}
	
	
	public static class ByMethodOrByField extends PropertyAccessor {

		private ByMethodOrByField() {
			super();
		}
		
		public static ByMethodOrByField create() {
			return new ByMethodOrByField();
		}

		@Override
		List<ThrowingBiFunction<Object, String, Object, Throwable>> getPropertyRetrievers() {
			List<ThrowingBiFunction<Object, String, Object, Throwable>> propertyRetrievers = new ArrayList<>();
			propertyRetrievers.add((object, propertyName) -> retrievePropertyByGetterMethod(object, propertyName));
			propertyRetrievers.add((object, propertyName) -> retrievePropertyByField(object, propertyName));
			return propertyRetrievers;
		}

		@Override
		List<ThrowingFunction<Object[], Boolean, Throwable>> getPropertySetters() {
			List<ThrowingFunction<Object[], Boolean, Throwable>> propertySetters  = new ArrayList<>();
			propertySetters.add(objects -> setPropertyByMethod(objects[0], (String)objects[1], objects[2]));
			propertySetters.add(objects -> setPropertyByField(objects[0], (String)objects[1], objects[2]));
			return propertySetters;
		}		
	}
}
