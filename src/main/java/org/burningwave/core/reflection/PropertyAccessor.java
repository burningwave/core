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
package org.burningwave.core.reflection;

import static org.burningwave.core.assembler.StaticComponentContainer.FieldHelper;
import static org.burningwave.core.assembler.StaticComponentContainer.MemberFinder;
import static org.burningwave.core.assembler.StaticComponentContainer.MethodHelper;
import static org.burningwave.core.assembler.StaticComponentContainer.Strings;
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.burningwave.core.Component;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.ClassFactory;
import org.burningwave.core.classes.CodeExecutor;
import org.burningwave.core.classes.FieldCriteria;
import org.burningwave.core.classes.StatementSourceGenerator;
import org.burningwave.core.function.ThrowingBiFunction;
import org.burningwave.core.function.ThrowingFunction;
import org.burningwave.core.iterable.IterableObjectHelper;
import org.burningwave.core.iterable.Properties;

public abstract class PropertyAccessor implements Component {
	public final static String SUPPLIER_IMPORTS_KEY_SUFFIX = ".supplier.imports";
	public final static String REG_EXP_FOR_JAVA_PROPERTIES = "([a-zA-Z\\$\\_\\-0-9]*)(\\[*.*)";
	public final static String REG_EXP_FOR_INDEXES_OF_JAVA_INDEXED_PROPERTIES = "\\[([a-zA-Z0-9]*)\\]";

	private ClassFactory classFactory;
	private List<ThrowingBiFunction<Object, String, Object, Throwable>> propertyRetrievers;
	private List<ThrowingFunction<Object[], Boolean, Throwable>> propertySetters;
	private Supplier<ClassFactory> classFactorySupplier;
	private IterableObjectHelper iterableObjectHelper;	
	private Supplier<IterableObjectHelper> iterableObjectHelperSupplier;
	
	PropertyAccessor(
		Supplier<ClassFactory> classFactorySupplier,
		Supplier<IterableObjectHelper> iterableObjectHelperSupplier
	) {
		this.iterableObjectHelperSupplier = iterableObjectHelperSupplier;
		this.classFactorySupplier = classFactorySupplier;
		this.propertyRetrievers = getPropertyRetrievers();
		this.propertySetters= getPropertySetters();
	}
	
	protected IterableObjectHelper getIterableObjectHelper() {
		return iterableObjectHelper != null ?
			iterableObjectHelper :
			(iterableObjectHelper = iterableObjectHelperSupplier.get());
	}
	
	protected ClassFactory getClassFactory() {
		return classFactory != null ?
			classFactory :
			(classFactory = classFactorySupplier.get());
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
				throw Throwables.toRuntimeException(message.toString());
			} else {
				logDebug("Warning: " + message);
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
	

	@SuppressWarnings({"unchecked" })
	public <T> Map<String, T> getAll(Object obj)
			throws IllegalArgumentException, IllegalAccessException {
		Map<String, T> propertyValues = new LinkedHashMap<>();
		Collection<Field> fields = MemberFinder.findAll(
			FieldCriteria.create(),
			obj
		);
		for (Field field : fields) {
			field.setAccessible(true);
			propertyValues.put(field.getName(), (T)field.get(obj));
		}
		return propertyValues;
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
				throw Throwables.toRuntimeException("indexed property " + property + " of type " + property.getClass() + " is not supporterd");
			}
			return retrieveFromIndexedProperty(
					propertyRetriever.get(), 
				indexes.substring(matcher.end(), indexes.length())
			);
		}
		return property;
	}

	Object retrievePropertyByField(Object obj, String propertyName) throws IllegalAccessException {
		Object objToReturn;
		Field field = FieldHelper.findOneAndMakeItAccessible(obj,
			propertyName
		);
		objToReturn = field.get(obj);
		return objToReturn;
	}

	Object retrievePropertyByGetterMethod(Object obj, String propertyName) {
		Object objToReturn;
		objToReturn = MethodHelper.invoke(
			obj, 
			MethodHelper.createGetterMethodNameByPropertyName(propertyName), 
			(Object[])null
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
			throw Throwables.toRuntimeException("indexed property " + property + " of type " + property.getClass() + " is not supporterd");
		}
	}

	Boolean setPropertyByField(Object target, String propertyPath, Object value) throws IllegalAccessException {
		Matcher matcher = Pattern.compile(REG_EXP_FOR_JAVA_PROPERTIES).matcher(propertyPath);
		matcher.find();
		Field field = FieldHelper.findOneAndMakeItAccessible(target.getClass(),
				matcher.group(1));
		if (matcher.group(2).isEmpty()) {
			field.set(target, value);
		} else {
			setInIndexedProperty(field.get(target), matcher.group(2), value);
		}
		return Boolean.TRUE;
	}

	Boolean setPropertyByMethod(Object target, String propertyPath, Object value) {
		Matcher matcher = Pattern.compile(REG_EXP_FOR_JAVA_PROPERTIES).matcher(propertyPath);
		matcher.find();
		if (matcher.group(2).isEmpty()) {
			MethodHelper.invoke(
				target, MethodHelper.createSetterMethodNameByPropertyName(matcher.group(1)), value
			);
		} else {
			setInIndexedProperty(MethodHelper.invoke(
				target, MethodHelper.createGetterMethodNameByPropertyName(matcher.group(1))
			), matcher.group(2), value);
		}
		return Boolean.TRUE;
	}
	
	public <T> T retrieveFrom(
		Properties properties, 
		String key,
		Map<String, String> defaultValues,
		Object... params
	) {	
		StatementSourceGenerator statement = StatementSourceGenerator.createSimple().setElementPrefix("\t");
		if (params != null && params.length > 0) {
			for (Object param : params) {
				statement.useType(ComponentSupplier.class, param.getClass());
			}
		}
		String importFromConfig = getIterableObjectHelper().get(properties, key + SUPPLIER_IMPORTS_KEY_SUFFIX, defaultValues);
		if (Strings.isNotEmpty(importFromConfig)) {
			Arrays.stream(importFromConfig.split(";")).forEach(imp -> {
				statement.useType(imp);
			});
		}
		String supplierCode = getIterableObjectHelper().get(properties, key, defaultValues);
		statement.addCodeRow(supplierCode.contains("return")?
			supplierCode:
			"return (T)" + supplierCode + ";"
		);
		return getClassFactory().execute(
			CodeExecutor.class.getClassLoader(), statement, params
		);
	}
	
	public static class ByFieldOrByMethod extends PropertyAccessor {

		private ByFieldOrByMethod(Supplier<ClassFactory> sourceCodeHandlerSupplier, Supplier<IterableObjectHelper> iterableObjectHelperSupplier) {
			super(sourceCodeHandlerSupplier, iterableObjectHelperSupplier);
		}
		
		public static ByFieldOrByMethod create(Supplier<ClassFactory> sourceCodeHandlerSupplier, Supplier<IterableObjectHelper> iterableObjectHelperSupplier) {
			return new ByFieldOrByMethod(sourceCodeHandlerSupplier, iterableObjectHelperSupplier);
		}

		List<ThrowingBiFunction<Object, String, Object, Throwable>> getPropertyRetrievers() {
			List<ThrowingBiFunction<Object, String, Object, Throwable>> propertyRetrievers = new ArrayList<>();
			propertyRetrievers.add((object, propertyName) -> retrievePropertyByField(object, propertyName));
			propertyRetrievers.add((object, propertyName) -> retrievePropertyByGetterMethod(object, propertyName));
			return propertyRetrievers;
		}

		List<ThrowingFunction<Object[], Boolean, Throwable>> getPropertySetters() {
			List<ThrowingFunction<Object[], Boolean, Throwable>> propertySetters  = new ArrayList<>();
			propertySetters.add(objects -> setPropertyByField(objects[0], (String)objects[1], objects[2]));
			propertySetters.add(objects -> setPropertyByMethod(objects[0], (String)objects[1], objects[2]));
			return propertySetters;
		}		
	}
	
	
	public static class ByMethodOrByField extends PropertyAccessor {

		private ByMethodOrByField(Supplier<ClassFactory> sourceCodeHandlerSupplier, Supplier<IterableObjectHelper> iterableObjectHelperSupplier) {
			super(sourceCodeHandlerSupplier, iterableObjectHelperSupplier);
		}
		
		public static ByMethodOrByField create(Supplier<ClassFactory> sourceCodeHandlerSupplier, Supplier<IterableObjectHelper> iterableObjectHelperSupplier) {
			return new ByMethodOrByField(sourceCodeHandlerSupplier, iterableObjectHelperSupplier);
		}

		List<ThrowingBiFunction<Object, String, Object, Throwable>> getPropertyRetrievers() {
			List<ThrowingBiFunction<Object, String, Object, Throwable>> propertyRetrievers = new ArrayList<>();
			propertyRetrievers.add((object, propertyName) -> retrievePropertyByGetterMethod(object, propertyName));
			propertyRetrievers.add((object, propertyName) -> retrievePropertyByField(object, propertyName));
			return propertyRetrievers;
		}

		List<ThrowingFunction<Object[], Boolean, Throwable>> getPropertySetters() {
			List<ThrowingFunction<Object[], Boolean, Throwable>> propertySetters  = new ArrayList<>();
			propertySetters.add(objects -> setPropertyByMethod(objects[0], (String)objects[1], objects[2]));
			propertySetters.add(objects -> setPropertyByField(objects[0], (String)objects[1], objects[2]));
			return propertySetters;
		}		
	}
}
