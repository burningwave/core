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
import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository;
import static org.burningwave.core.assembler.StaticComponentContainer.Methods;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.burningwave.core.Component;
import org.burningwave.core.function.ThrowingBiFunction;
import org.burningwave.core.function.ThrowingFunction;

@SuppressWarnings("unchecked")
public abstract class FieldAccessor implements Component {
	public final static String REG_EXP_FOR_SIMPLE_FIELDS = "([a-zA-Z\\$\\_\\-0-9]*)(\\[*.*)";
	public final static String REG_EXP_FOR_INDEXES_OF_INDEXED_FIELDS = "\\[(.*?)\\]";

	private List<ThrowingBiFunction<Object, String, Object, Throwable>> fieldRetrievers;
	private List<ThrowingFunction<Object[], Boolean, Throwable>> fieldSetters;
	private Pattern simpleFieldSearcher;
	private Pattern indexesSearcherForIndexedField;

	FieldAccessor() {
		this.fieldRetrievers = getFieldRetrievers();
		this.fieldSetters= getFieldSetters();
		this.simpleFieldSearcher = Pattern.compile(REG_EXP_FOR_SIMPLE_FIELDS);
		this.indexesSearcherForIndexedField = Pattern.compile(REG_EXP_FOR_INDEXES_OF_INDEXED_FIELDS);
	}

	abstract List<ThrowingFunction<Object[], Boolean, Throwable>> getFieldSetters();

	abstract List<ThrowingBiFunction<Object, String, Object, Throwable>> getFieldRetrievers();

	public <T> T get(Object obj, String path) {
		if (path == null) {
			throw new IllegalArgumentException("Field path cannot be null");
		}
		String[] pathSegments = path.split("\\.");
		Object objToReturn = obj;
		for (int j = 0; j < pathSegments.length; j++) {
			objToReturn = getField(j != 0 ? objToReturn : obj, pathSegments[j]);
		}
		return (T)objToReturn;
	}

	private Object getField(Object obj, String pathSegment) {
		Object objToReturn = null;
		Matcher matcher = simpleFieldSearcher.matcher(pathSegment);
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
			try {
				objToReturn = retrieveFromIndexedField(objToReturn, matcher.group(2));
			} catch (Throwable exc) {
				exceptions.add(exc);
			}
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
				ManagedLoggerRepository.logError(getClass()::getName, message.toString());
				org.burningwave.core.assembler.StaticComponentContainer.Driver.throwException(exceptions.iterator().next());
			} else {
				//logDebug("Warning: " + message);
			}
		}
	}

	public void set(Object obj, String path, Object value) {
		if (path == null) {
			throw new IllegalArgumentException("Field path cannot be null");
		}
		if (path.trim().isEmpty()) {
			return;
		}
		Object target =
				path.contains(".")?
						get(obj, path.substring(0, path.lastIndexOf("."))) :
							obj;
		String targetPathSegment =
				path.contains(".")?
						path.substring(path.lastIndexOf(".") + 1, path.length()) :
							path;
		setField(target, targetPathSegment, value);
	}

	private void setField(Object target, String pathSegment, Object value) {
		List<Throwable> exceptions = new ArrayList<>();
		for (ThrowingFunction<Object[], Boolean, Throwable> setter : fieldSetters) {
			try {
				setter.apply(new Object[] {target, pathSegment, value});
				break;
			} catch (Throwable exc) {
				exceptions.add(exc);
			}
		}
		manageGetFieldExceptions(exceptions);
	}


	private <T> Object retrieveFromIndexedField(Object fieldValue, String indexes) {
		Matcher matcher = indexesSearcherForIndexedField.matcher(indexes);
		if (matcher.find()) {
			String index = matcher.group(1);
			Supplier<Object> propertyRetriever = null;
			if (fieldValue.getClass().isArray()) {
				propertyRetriever = () -> Array.get(fieldValue, Integer.valueOf(index));
			} else if (fieldValue instanceof List) {
				propertyRetriever = () -> ((List<?>)fieldValue).get(Integer.valueOf(index));
			} else if (fieldValue instanceof Map) {
				propertyRetriever = () -> ((Map<?, ?>)fieldValue).get(index);
			} else if (fieldValue instanceof Collection) {
				propertyRetriever = () -> {
					Collection<T> collection = (Collection<T>)fieldValue;
					int indexAsInt = convertAndCheckIndex(collection, index);
					Iterator<T> itr = collection.iterator();
					int currentIterationIndex = 0;
					while (itr.hasNext()) {
						Object currentIteartedObject = itr.next();
						if (currentIterationIndex++ == indexAsInt) {
							return currentIteartedObject;
						}
					}
					return null;
				};
			} else {
				return org.burningwave.core.assembler.StaticComponentContainer.Driver.throwException("indexed property {} of type {} is not supporterd", fieldValue, fieldValue.getClass());
			}
			return retrieveFromIndexedField(
				propertyRetriever.get(),
				indexes.substring(matcher.end(), indexes.length())
			);
		}
		return fieldValue;
	}

	Object retrieveFieldByDirectAccess(Object target, String pathSegment) throws IllegalAccessException {
		if (pathSegment.trim().isEmpty()) {
			return target;
		}
		return Fields.getDirect(target, pathSegment);
	}

	Object retrieveFieldByGetterMethod(Object target, String pathSegment) {
		if (pathSegment.trim().isEmpty()) {
			return target;
		}
		Object objToReturn;
		objToReturn = Methods.invokeDirect(
			target,
			Methods.createGetterMethodNameByFieldPath(pathSegment)
		);
		return objToReturn;
	}

	private <T> void setInIndexedField(Object fieldValue, String indexes, Object value) {
		Matcher matcher = indexesSearcherForIndexedField.matcher(indexes);
		int lastIndexOf = 0;
		String index = null;
		while (matcher.find()) {
			index = matcher.group(1);
			lastIndexOf = matcher.start();
		}
		Object targetObject = retrieveFromIndexedField(fieldValue, indexes.substring(0, lastIndexOf));
		if (targetObject.getClass().isArray()) {
			Array.set(targetObject, Integer.valueOf(index), value);
		} else if (targetObject instanceof List) {
			((List<T>)targetObject).set(Integer.valueOf(index), (T)value);
		} else if (targetObject instanceof Map) {
			((Map<String, T>)targetObject).put(index, (T)value);
		} else if (targetObject instanceof Collection) {
			setIndexedValue((Collection<T>) targetObject, index, value);
		} else {
			org.burningwave.core.assembler.StaticComponentContainer.Driver.throwException("indexed property {} of type {} is not supporterd", fieldValue, fieldValue.getClass());
		}
	}

	private <T> void setIndexedValue(Collection<T> collection, String index, Object value) {
		int indexAsInt = convertAndCheckIndex(collection, index);
		List<T> tempList = new ArrayList<>();
		Iterator<T> itr = collection.iterator();
		while (itr.hasNext()) {
			tempList.add(itr.next());
		}
		int iterationIndex = 0;
		collection.clear();
		itr = tempList.iterator();
		while (itr.hasNext()) {
			T origVal = itr.next();
			if (iterationIndex++ != indexAsInt) {
				collection.add(origVal);
			} else {
				collection.add((T)value);
			}
		}
	}

	private <T> int convertAndCheckIndex(Collection<T> collection, String indexAsString) {
		int index = Integer.valueOf(indexAsString);
		if (collection.size() < index) {
			throw new IndexOutOfBoundsException(("Illegal index "+ indexAsString +", collection size " + collection.size()));
		}
		return index;
	}

	Boolean setFieldByDirectAccess(Object target, String pathSegment, Object value) throws IllegalAccessException {
		Matcher matcher = simpleFieldSearcher.matcher(pathSegment);
		matcher.find();
		if (matcher.group(2).isEmpty()) {
			Field field = Fields.findOneAndMakeItAccessible(target.getClass(), matcher.group(1));
			Fields.setDirect(target, field, value);
		} else {
			if (target.getClass().isArray() || target instanceof Map || target instanceof Collection) {
				setInIndexedField(target, matcher.group(2), value);
			} else {
				Field field = Fields.findOneAndMakeItAccessible(target.getClass(), matcher.group(1));
				setInIndexedField(field.get(target), matcher.group(2), value);
			}
		}
		return Boolean.TRUE;
	}

	Boolean setFieldBySetterMethod(Object target, String pathSegment, Object value) {
		Matcher matcher = simpleFieldSearcher.matcher(pathSegment);
		matcher.find();
		if (matcher.group(2).isEmpty()) {
			Methods.invokeDirect(
				target, Methods.createSetterMethodNameByFieldPath(matcher.group(1)), value
			);
		} else {
			if (target.getClass().isArray() || target instanceof Map || target instanceof Collection) {
				setInIndexedField(target, matcher.group(2), value);
			} else {
				setInIndexedField(Methods.invokeDirect(
					target, Methods.createGetterMethodNameByFieldPath(matcher.group(1))
				), matcher.group(2), value);
			}
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
			retrievers.add((object, pathSegment) -> retrieveFieldByDirectAccess(object, pathSegment));
			retrievers.add((object, pathSegment) -> retrieveFieldByGetterMethod(object, pathSegment));
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
			retrievers.add((object, pathSegment) -> retrieveFieldByGetterMethod(object, pathSegment));
			retrievers.add((object, pathSegment) -> retrieveFieldByDirectAccess(object, pathSegment));
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
