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

import static org.burningwave.core.assembler.StaticComponentContainer.Cache;
import static org.burningwave.core.assembler.StaticComponentContainer.Classes;
import static org.burningwave.core.assembler.StaticComponentContainer.LowLevelObjectsHandler;
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.burningwave.core.function.Executor;

@SuppressWarnings("unchecked")
public class Fields extends Members.Handler<Field, FieldCriteria> {
	
	public static Fields create() {
		return new Fields();
	}
	
	public <T> T getStatic(Field field) {
		return Executor.get(() -> (T)field.get(null));
	}
	
	public <T> T get(Object target, Field field) {
		return Executor.get(() -> (T)field.get(target));
	}
	
	public <T> T getStatic(Class<?> targetClass, String fieldName) {
		return getStatic(findFirstAndMakeItAccessible(targetClass, fieldName, null));
	}
	
	public <T> T get(Object target, String fieldName) {
		return get(target, findFirstAndMakeItAccessible(Classes.retrieveFrom(target), fieldName, null));
	}	
	
	public <T> T getStaticDirect(Field field) {
		return Executor.get(() -> (T)LowLevelObjectsHandler.getFieldValue(null, field));
	}
	
	public <T> T getDirect(Object target, Field field) {
		return Executor.get(() -> (T)LowLevelObjectsHandler.getFieldValue(target, field));
	}
	
	public <T> T getStaticDirect(Class<?> targetClass, String fieldName) {
		return getStaticDirect(findFirstAndMakeItAccessible(targetClass, fieldName, null));
	}
	
	public <T> T getDirect(Object target, String fieldName) {
		return getDirect(target, findFirstAndMakeItAccessible(Classes.retrieveFrom(target), fieldName, null));
	}
	
	private void set(Class<?> targetClass, Object target, String fieldName, Object value) {
		set(target, findFirstAndMakeItAccessible(targetClass, fieldName, Classes.retrieveFrom(value)), value);
	}
	
	public void set(Object target, Field field, Object value) {
		Executor.run(() -> field.set(target, value));
	}
	
	public void setStatic(Class<?> targetClass, String fieldName, Object value) {
		set(targetClass, null, fieldName, value);
	}
	
	public void set(Object target, String fieldName, Object value) {
		set(Classes.retrieveFrom(target), target, fieldName, value);
	}
	
	public void setDirect(Object target, Field field, Object value) {
		LowLevelObjectsHandler.setFieldValue(target, field, value);
	}
	
	private void setDirect(Class<?> targetClass, Object target, String fieldName, Object value) {
		setDirect(target, findFirstAndMakeItAccessible(targetClass, fieldName, Classes.retrieveFrom(value)), value);
	}
	
	public void setStaticDirect(Class<?> targetClass, String fieldName, Object value) {
		setDirect(targetClass, null, fieldName, value);
	}
	
	public void setDirect(Object target, String fieldName, Object value) {
		setDirect(Classes.retrieveFrom(target), target, fieldName, value);
	}
	
	public Map<Field, ?> getAllStatic(Class<?> targetClass) {
		return getAll(() -> findAllAndMakeThemAccessible(targetClass), null);
	}
	
	public Map<Field, ?> getAll(Object target) {
		return getAll(() -> findAllAndMakeThemAccessible(Classes.retrieveFrom(target)), target);
	}
	
	public Map<Field, ?> getAll(FieldCriteria criteria, Object target) {
		return getAll(() -> findAllAndMakeThemAccessible(criteria, Classes.retrieveFrom(target)), target);
	}


	private Map<Field, Object> getAll(Supplier<Collection<Field>> fieldsSupplier, Object target) {
		Map<Field, Object> fieldValues = new HashMap<>();
		for (Field field : fieldsSupplier.get()) {
			if (target != null) {
				fieldValues.put(
					field,
					Executor.get(
						() ->
							field.get(
								Modifier.isStatic(field.getModifiers()) ? null : target
							)
					)
				);
			} else if (Modifier.isStatic(field.getModifiers())) {
				fieldValues.put(
					field,
					Executor.get(
						() ->
							field.get(null)
					)
				);
			}			
		}
		return fieldValues;
	}
	
	public Map<Field, ?> getAllStaticDirect(Class<?> targetClass) {
		return getAllDirect(() -> findAllAndMakeThemAccessible(targetClass), null);
	}
	
	public Map<Field, ?> getAllDirect(Object target) {
		return getAllDirect(() -> findAllAndMakeThemAccessible(Classes.retrieveFrom(target)), target);
	}
	
	public Map<Field, ?> getAllDirect(FieldCriteria criteria, Object target) {
		return getAllDirect(() -> findAllAndMakeThemAccessible(criteria, Classes.retrieveFrom(target)), target);
	}
	
	private Map<Field, ?> getAllDirect(Supplier<Collection<Field>> fieldsSupplier, Object target) {
		Map<Field, ?> fieldValues = new HashMap<>();
		for (Field field : fieldsSupplier.get()) {
			fieldValues.put(
				field,
				Executor.get(() -> LowLevelObjectsHandler.getFieldValue(target, field))
			);
		}
		return fieldValues;
	}
	
	public Field findOneAndMakeItAccessible(Class<?> targetClass, String memberName) {
		Collection<Field> members = findAllByExactNameAndMakeThemAccessible(targetClass, memberName, null);
		if (members.size() != 1) {
			Throwables.throwException("Field {} not found or found more than one field in {} hierarchy", memberName, targetClass.getName());
		}
		return members.stream().findFirst().get();
	}
	
	public Field findFirstAndMakeItAccessible(Class<?> targetClass, String fieldName, Class<?> valueClass) {
		Collection<Field> members = findAllByExactNameAndMakeThemAccessible(targetClass, fieldName, valueClass);
		if (members.size() < 1) {
			Throwables.throwException("Field {} not found in {} hierarchy", fieldName, targetClass.getName());
		}
		return members.stream().findFirst().get();
	}
	
	public Collection<Field> findAllByExactNameAndMakeThemAccessible(
		Class<?> targetClass,
		String fieldName
	) {
		return findAllByExactNameAndMakeThemAccessible(targetClass, fieldName, null);
	}
	
	public Collection<Field> findAllByExactNameAndMakeThemAccessible(
		Class<?> targetClass,
		String fieldName, 
		Class<?> valueType
	) {	
		String cacheKey = getCacheKey(targetClass, "equals " + fieldName, valueType);
		ClassLoader targetClassClassLoader = Classes.getClassLoader(targetClass);
		return Cache.uniqueKeyForFields.getOrUploadIfAbsent(
			targetClassClassLoader,
			cacheKey, 
			() -> 
				Collections.unmodifiableCollection(
					findAllAndMakeThemAccessible(
						FieldCriteria.forEntireClassHierarchy().allThoseThatMatch(field -> {
							if (valueType == null) {
								return field.getName().equals(fieldName);
							} else {
								return field.getName().equals(fieldName) && Classes.isAssignableFrom(field.getType(), valueType);
							}
						}), targetClass
					)
				)
		);
	}
	
	public Collection<Field> findAllAndMakeThemAccessible(
		Class<?> targetClass
	) {	
		String cacheKey = getCacheKey(targetClass, "all fields", (Class<?>[])null);
		ClassLoader targetClassClassLoader = Classes.getClassLoader(targetClass);
		return Cache.uniqueKeyForFields.getOrUploadIfAbsent(
			targetClassClassLoader, 
			cacheKey, 
			() -> 
				Collections.unmodifiableCollection(
					findAllAndMakeThemAccessible(
						FieldCriteria.forEntireClassHierarchy(), targetClass
					)
				)
			
		);
	}	
}
