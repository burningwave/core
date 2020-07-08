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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.stream.Collectors;

import org.burningwave.core.classes.ConstructorCriteria;
import org.burningwave.core.function.ThrowingSupplier;


@SuppressWarnings("unchecked")
public class Constructors extends MemberHelper<Constructor<?>>  {

	private Constructors() {
		super();
	}
	
	public static Constructors create() {
		return new Constructors();
	}	
	
	public <T> T newInstanceOf(
		Object object,
		Object... arguments
	) {
		return ThrowingSupplier.get(() -> 
			(T)findFirstAndMakeItAccessible(object, arguments).newInstance(arguments != null? arguments : new Object[]{null})
		);
	}
	
	public <T> T newInstanceDirectOf(
		Object target,
		Object... arguments
	) {
		Class<?> targetClass = Classes.retrieveFrom(target);
		String cacheKey = getCacheKey(targetClass, "equals " + Classes.retrieveSimpleName(targetClass.getName()), arguments);
		ClassLoader targetClassClassLoader = Classes.getClassLoader(targetClass);
		Map.Entry<java.lang.reflect.Executable, MethodHandle> methodHandleBag = Cache.uniqueKeyForExecutableAndMethodHandle.getOrUploadIfAbsent(
			targetClassClassLoader, cacheKey, 
			() -> {
				Constructor<?> constructor = findFirstAndMakeItAccessible(targetClass, arguments);
				return new AbstractMap.SimpleEntry<>(
					constructor,
					convertToMethodHandle(
						constructor
					)
				);
			}
		);
		return ThrowingSupplier.get(() -> {
				return (T)methodHandleBag.getValue().invokeWithArguments(getArgumentList(methodHandleBag.getKey(), arguments));
			}
		);
	}

	public Constructor<?> findOneAndMakeItAccessible(Object target, Object... arguments) {
		Collection<Constructor<?>> members = findAllAndMakeThemAccessible(target, arguments);
		if (members.size() != 1) {
			throw Throwables.toRuntimeException("Constructor not found or found more than one constructor in " + Classes.retrieveFrom(target).getName());
		}
		return members.stream().findFirst().get();
	}
	
	public Constructor<?> findFirstAndMakeItAccessible(Object target, Object... arguments) {
		Collection<Constructor<?>> members = findAllAndMakeThemAccessible(target, arguments);
		if (members.size() < 1) {
			throw Throwables.toRuntimeException("Constructor not found in " + Classes.retrieveFrom(target).getName());
		}
		return members.stream().findFirst().get();
	}
	
	public Collection<Constructor<?>> findAllAndMakeThemAccessible(
		Object target,
		Object... arguments
	) {	
		Class<?> targetClass = Classes.retrieveFrom(target);
		String cacheKey = getCacheKey(targetClass, "all constructors with input parameters", arguments);
		ClassLoader targetClassClassLoader = Classes.getClassLoader(targetClass);
		ConstructorCriteria criteria = ConstructorCriteria.create()
			.and().parameterTypesAreAssignableFrom(arguments);
		return Cache.uniqueKeyForConstructors.getOrUploadIfAbsent(targetClassClassLoader, cacheKey, () -> 
			Collections.unmodifiableCollection(
				findAllAndMakeThemAccessible(target).stream().filter(
					criteria.getPredicateOrTruePredicateIfPredicateIsNull()
				).collect(
					Collectors.toCollection(LinkedHashSet::new)
				)
			)
		);
	}
	
	public Collection<Constructor<?>> findAllAndMakeThemAccessible(
		Object target
	) {
		Class<?> targetClass = Classes.retrieveFrom(target);
		String cacheKey = getCacheKey(targetClass, "all constructors");
		ClassLoader targetClassClassLoader = Classes.getClassLoader(targetClass);
		Collection<Constructor<?>> members = Cache.uniqueKeyForConstructors.getOrUploadIfAbsent(
			targetClassClassLoader, cacheKey, () -> {
				return Collections.unmodifiableCollection(
					findAllAndApply(
						ConstructorCriteria.byScanUpTo((lastClassInHierarchy, currentScannedClass) -> {
		                    return lastClassInHierarchy.equals(currentScannedClass);
		                }), target, (member) -> 
							member.setAccessible(true)
					)
				);
			}
		);
		return members;
	}
	
	public MethodHandle convertToMethodHandle(Constructor<?> constructor) {
		return convertToMethodHandleBag(constructor).getValue();
	}
	
	public Map.Entry<Lookup, MethodHandle> convertToMethodHandleBag(Constructor<?> constructor) {
		try {
			Class<?> constructorDeclaringClass = constructor.getDeclaringClass();
			MethodHandles.Lookup consulter = LowLevelObjectsHandler.getConsulter(constructorDeclaringClass);
			return new AbstractMap.SimpleEntry<>(consulter,
				consulter.findConstructor(
					constructorDeclaringClass,
					MethodType.methodType(void.class, constructor.getParameterTypes())
				)
					
			);
		} catch (NoSuchMethodException | IllegalAccessException exc) {
			throw Throwables.toRuntimeException(exc);
		}
	}

}
