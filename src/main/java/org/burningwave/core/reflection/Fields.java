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

import static org.burningwave.core.assembler.StaticComponentContainer.Cache;
import static org.burningwave.core.assembler.StaticComponentContainer.Classes;
import static org.burningwave.core.assembler.StaticComponentContainer.LowLevelObjectsHandler;
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.stream.Collectors;

import org.burningwave.core.classes.FieldCriteria;
import org.burningwave.core.function.ThrowingSupplier;

@SuppressWarnings("unchecked")
public class Fields extends MemberHelper<Field> {
	
	private Fields() {
		super();
	}
	
	public static Fields create() {
		return new Fields();
	}
	
	public <T> T get(Object target, String fieldName) {
		return ThrowingSupplier.get(() -> (T)findFirstAndMakeItAccessible(target, fieldName).get(target));
	}	
	
	public <T> T getDirect(Object target, String fieldName) {
		return ThrowingSupplier.get(() -> (T)LowLevelObjectsHandler.getFieldValue(target, findFirstAndMakeItAccessible(target, fieldName)));
	}
	
	public <T> Map<String, T> getAll(Object target) {
		Map<String, T> fieldValues = new HashMap<>();
		Collection<Field> fields = findAllAndMakeThemAccessible(target);
		for (Field field : fields) {
			fieldValues.put(
				field.getDeclaringClass() + "." + field.getName(),
				ThrowingSupplier.get(() ->
					(T)field.get(
						Modifier.isStatic(field.getModifiers()) ? null : target)
					)
			);
		}
		return fieldValues;
	}
	
	public <T> Map<String, T> getAllDirect(Object target) {
		Map<String, T> fieldValues = new HashMap<>();
		Collection<Field> fields = findAllAndMakeThemAccessible(target);
		for (Field field : fields) {
			fieldValues.put(
				field.getDeclaringClass() + "." + field.getName(),
				ThrowingSupplier.get(() -> (T)LowLevelObjectsHandler.getFieldValue(target, field))
			);
		}
		return fieldValues;
	}
	
	public Field findOneAndMakeItAccessible(Object target, String memberName, Object... arguments) {
		Collection<Field> members = findAllByExactNameAndMakeThemAccessible(target, memberName);
		if (members.size() != 1) {
			Throwables.toRuntimeException("Field " + memberName
				+ " not found or found more than one field in " + Classes.retrieveFrom(target).getName()
				+ " hierarchy");
		}
		return members.stream().findFirst().get();
	}
	
	public Field findFirstAndMakeItAccessible(Object target, String fieldName, Object... arguments) {
		Collection<Field> members = findAllByExactNameAndMakeThemAccessible(target, fieldName);
		if (members.size() < 1) {
			Throwables.toRuntimeException("Field " + fieldName
				+ " not found in " + Classes.retrieveFrom(target).getName()
				+ " hierarchy");
		}
		return members.stream().findFirst().get();
	}

	public Collection<Field> findAllByExactNameAndMakeThemAccessible(
		Object target,
		String fieldName
	) {	
		Class<?> targetClass = Classes.retrieveFrom(target);
		String cacheKey = getCacheKey(targetClass, "equals " + fieldName, (Object[])null);
		ClassLoader targetClassClassLoader = Classes.getClassLoader(targetClass);
		return Cache.uniqueKeyForFields.getOrUploadIfAbsent(
			targetClassClassLoader,
			cacheKey, 
			() -> 
				Collections.unmodifiableCollection(
					findAllAndMakeThemAccessible(target).stream().filter(field -> field.getName().equals(fieldName)).collect(Collectors.toCollection(LinkedHashSet::new))
				)
		);
	}
	
	public Collection<Field> findAllAndMakeThemAccessible(
		Object target
	) {	
		Class<?> targetClass = Classes.retrieveFrom(target);
		String cacheKey = getCacheKey(targetClass, "all fields", (Object[])null);
		ClassLoader targetClassClassLoader = Classes.getClassLoader(targetClass);
		return Cache.uniqueKeyForFields.getOrUploadIfAbsent(
			targetClassClassLoader, 
			cacheKey, 
			() -> 
				Collections.unmodifiableCollection(
					findAllAndApply(
						FieldCriteria.create(),
						target,
						(field) -> 
							field.setAccessible(true)
					)
				)
			
		);
	}	
}
