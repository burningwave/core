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
 * Copyright (c) 2019-2022 Roberto Gentili
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

public abstract class FieldAccessor implements Component {
	public final static String REG_EXP_FOR_SIMPLE_FIELDS = "([a-zA-Z\\$\\_\\-0-9]*)(\\[*.*)";
	public final static String REG_EXP_FOR_INDEXES_OF_INDEXED_FIELDS = "\\[([a-zA-Z0-9]*)\\]";

	private List<ThrowingBiFunction<Object, String, Object, Throwable>> fieldRetrievers;
	private List<ThrowingFunction<Object[], Boolean, Throwable>> fieldSetters;
	private Pattern simpleFieldSearcher;
	private Pattern indexesSearcherForIndexedField;

	FieldAccessor() {
		this.fieldRetrievers = getFieldRetrievers();
		this.fieldSetters= getFieldSetters();
		simpleFieldSearcher = Pattern.compile(REG_EXP_FOR_SIMPLE_FIELDS);
		indexesSearcherForIndexedField = Pattern.compile(REG_EXP_FOR_INDEXES_OF_INDEXED_FIELDS);
	}

	abstract List<ThrowingFunction<Object[], Boolean, Throwable>> getFieldSetters();

	abstract List<ThrowingBiFunction<Object, String, Object, Throwable>> getFieldRetrievers();

	@SuppressWarnings("unchecked")
	public <T> T get(Object obj, String propertyPath) {
		String[] propertyAddress = propertyPath.split("\\.");
		Object objToReturn = obj;
		for (int j = 0; j < propertyAddress.length; j++) {
			objToReturn = getField(j != 0 ? objToReturn : obj,
					propertyAddress[j]);
		}
		return (T)objToReturn;
	}

	private Object getField(Object obj, String property) {
		Object objToReturn = null;
		Matcher matcher = simpleFieldSearcher.matcher(property);
		matcher.find();
		List<Throwable> exceptions = new ArrayList<>();
		for (ThrowingBiFunction<Object, String, Object, Throwable> retriever : fieldRetrievers) {
			try {
				if ((objToReturn = retriever.apply(obj, matcher.group(1))) != null) {
					break;
				}
			} catch (Throwable exc) {
				exceptions.add(exc);
			}
		}
		manageGetFieldExceptions(exceptions);
		if (!matcher.group(2).isEmpty()) {
			objToReturn = retrieveFromIndexedField(objToReturn, matcher.group(2));
		}
		return objToReturn;
	}

	private void manageGetFieldExceptions(List<Throwable> exceptions) {
		if (exceptions.size() > 0) {
			String message = "";
			for (Throwable exception : exceptions) {
				message += exception.getMessage() + "\n";
			}
			message = message.substring(0, message.length() - 1);
			if (exceptions.size() == fieldRetrievers.size()) {
				org.burningwave.core.assembler.StaticComponentContainer.Driver.throwException(message.toString());
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
		setField(target, targetPropertyName, value);
	}

	private void setField(Object target, String property,
			Object value) {
		List<Throwable> exceptions = new ArrayList<>();
		for (ThrowingFunction<Object[], Boolean, Throwable> setter : fieldSetters) {
			try {
				setter.apply(new Object[] {target, property, value});
				break;
			} catch (Throwable exc) {
				exceptions.add(exc);
			}
		}
		manageGetFieldExceptions(exceptions);
	}


	private Object retrieveFromIndexedField(Object property, String indexes) {
		Matcher matcher = indexesSearcherForIndexedField.matcher(indexes);
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
				return org.burningwave.core.assembler.StaticComponentContainer.Driver.throwException("indexed property {} of type {} is not supporterd", property, property.getClass());
			}
			return retrieveFromIndexedField(
					propertyRetriever.get(),
					indexes.substring(matcher.end(), indexes.length())
					);
		}
		return property;
	}

	Object retrieveFieldByDirectAccess(Object target, String propertyName) throws IllegalAccessException {
		return Fields.getDirect(target, propertyName);
	}

	Object retrieveFieldByGetterMethod(Object obj, String propertyName) {
		Object objToReturn;
		objToReturn = Methods.invokeDirect(
				obj,
				Methods.createGetterMethodNameByPropertyName(propertyName)
				);
		return objToReturn;
	}

	@SuppressWarnings("unchecked")
	private <T> void setInIndexedField(Object property, String indexes, Object value) {
		Matcher matcher = indexesSearcherForIndexedField.matcher(indexes);
		int lastIndexOf = 0;
		String index = null;
		while (matcher.find()) {
			index = matcher.group(1);
			lastIndexOf = matcher.start();
		}
		Object targetObject = retrieveFromIndexedField(property, indexes.substring(0, lastIndexOf));
		if (targetObject.getClass().isArray()) {
			Array.set(targetObject, Integer.valueOf(index), value);
		} else if (List.class.isAssignableFrom(targetObject.getClass())) {
			((List<T>)targetObject).set(Integer.valueOf(index), (T)value);
		} else if (Map.class.isAssignableFrom(property.getClass())) {
			((Map<String, T>)property).put(index, (T)value);
		} else {
			org.burningwave.core.assembler.StaticComponentContainer.Driver.throwException("indexed property {} of type {} is not supporterd", property, property.getClass());
		}
	}

	Boolean setFieldByDirectAccess(Object target, String propertyPath, Object value) throws IllegalAccessException {
		Matcher matcher = simpleFieldSearcher.matcher(propertyPath);
		matcher.find();
		Field field = Fields.findOneAndMakeItAccessible(target.getClass(),
				matcher.group(1));
		if (matcher.group(2).isEmpty()) {
			Fields.setDirect(target, field, value);
		} else {
			setInIndexedField(field.get(target), matcher.group(2), value);
		}
		return Boolean.TRUE;
	}

	Boolean setFieldBySetterMethod(Object target, String propertyPath, Object value) {
		Matcher matcher = simpleFieldSearcher.matcher(propertyPath);
		matcher.find();
		if (matcher.group(2).isEmpty()) {
			Methods.invokeDirect(
					target, Methods.createSetterMethodNameByPropertyName(matcher.group(1)), value
					);
		} else {
			setInIndexedField(Methods.invokeDirect(
					target, Methods.createGetterMethodNameByPropertyName(matcher.group(1))
					), matcher.group(2), value);
		}
		return Boolean.TRUE;
	}

	public static class ByFieldOrByMethod extends FieldAccessor {

		private ByFieldOrByMethod() {
			super();
		}

		public static ByFieldOrByMethod create() {
			return new ByFieldOrByMethod();
		}

		@Override
		List<ThrowingBiFunction<Object, String, Object, Throwable>> getFieldRetrievers() {
			List<ThrowingBiFunction<Object, String, Object, Throwable>> retrievers = new ArrayList<>();
			retrievers.add((object, propertyName) -> retrieveFieldByDirectAccess(object, propertyName));
			retrievers.add((object, propertyName) -> retrieveFieldByGetterMethod(object, propertyName));
			return retrievers;
		}

		@Override
		List<ThrowingFunction<Object[], Boolean, Throwable>> getFieldSetters() {
			List<ThrowingFunction<Object[], Boolean, Throwable>> setters  = new ArrayList<>();
			setters.add(objects -> setFieldByDirectAccess(objects[0], (String)objects[1], objects[2]));
			setters.add(objects -> setFieldBySetterMethod(objects[0], (String)objects[1], objects[2]));
			return setters;
		}
	}


	public static class ByMethodOrByField extends FieldAccessor {

		private ByMethodOrByField() {
			super();
		}

		public static ByMethodOrByField create() {
			return new ByMethodOrByField();
		}

		@Override
		List<ThrowingBiFunction<Object, String, Object, Throwable>> getFieldRetrievers() {
			List<ThrowingBiFunction<Object, String, Object, Throwable>> retrievers = new ArrayList<>();
			retrievers.add((object, propertyName) -> retrieveFieldByGetterMethod(object, propertyName));
			retrievers.add((object, propertyName) -> retrieveFieldByDirectAccess(object, propertyName));
			return retrievers;
		}

		@Override
		List<ThrowingFunction<Object[], Boolean, Throwable>> getFieldSetters() {
			List<ThrowingFunction<Object[], Boolean, Throwable>> retrievers  = new ArrayList<>();
			retrievers.add(objects -> setFieldBySetterMethod(objects[0], (String)objects[1], objects[2]));
			retrievers.add(objects -> setFieldByDirectAccess(objects[0], (String)objects[1], objects[2]));
			return retrievers;
		}
	}
}
