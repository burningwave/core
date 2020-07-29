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

import org.burningwave.core.function.ThrowingSupplier;


@SuppressWarnings("unchecked")
public class Constructors extends Members.Handler.OfExecutable<Constructor<?>>  {
	
	public static Constructors create() {
		return new Constructors();
	}	
	
	public <T> T newInstanceOf(
		Class<?> targetClass,
		Object... arguments
	) {	
		Constructor<?> ctor = findFirstAndMakeItAccessible(targetClass, Classes.deepRetrieveFrom(arguments));
		return ThrowingSupplier.get(() -> {
			//logInfo("Invoking " + ctor);
			return (T)ctor.newInstance(getArgumentArray(ctor, arguments));
		});
	}
	
	public <T> T newInstanceDirectOf(
		Class<?> targetClass,
		Object... arguments
	) {
		Class<?>[] argsType = Classes.deepRetrieveFrom(arguments);
		String cacheKey = getCacheKey(targetClass, "equals " + Classes.retrieveSimpleName(targetClass.getName()), argsType);
		ClassLoader targetClassClassLoader = Classes.getClassLoader(targetClass);
		Map.Entry<java.lang.reflect.Executable, MethodHandle> methodHandleBag = Cache.uniqueKeyForExecutableAndMethodHandle.getOrUploadIfAbsent(
			targetClassClassLoader, cacheKey, 
			() -> {
				Constructor<?> constructor = findFirstAndMakeItAccessible(targetClass, argsType);
				return new AbstractMap.SimpleEntry<>(
					constructor,
					convertToMethodHandle(
						constructor
					)
				);
			}
		);
		return ThrowingSupplier.get(() -> {
				Constructor<?> ctor = (Constructor<?>) methodHandleBag.getKey();
				//logInfo("Direct invoking of " + ctor);
				return (T)methodHandleBag.getValue().invokeWithArguments(
					getArgumentList(ctor, arguments)
				);
			}
		);
	}

	public Constructor<?> findOneAndMakeItAccessible(Class<?> targetClass, Class<?>... arguments) {
		Collection<Constructor<?>> members = findAllAndMakeThemAccessible(targetClass, arguments);
		if (members.size() == 1) {
			return members.stream().findFirst().get();
		} else if (members.size() > 1) {
			Collection<Constructor<?>> membersThatMatch = searchForExactMatch(members, arguments);
			if (membersThatMatch.size() == 1) {
				return membersThatMatch.stream().findFirst().get();
			}
		}
		throw Throwables.toRuntimeException("Constructor not found or found more than one constructor in " + targetClass.getName());
	}
	
	public Constructor<?> findFirstAndMakeItAccessible(Class<?> targetClass, Class<?>... arguments) {
		Collection<Constructor<?>> members = findAllAndMakeThemAccessible(targetClass, arguments);
		if (members.size() == 1) {
			return members.stream().findFirst().get();
		} else if (members.size() > 1) {
			Collection<Constructor<?>> membersThatMatch = searchForExactMatch(members, arguments);
			if (!membersThatMatch.isEmpty()) {
				return membersThatMatch.stream().findFirst().get();
			}
			return members.stream().findFirst().get();
		}
		throw Throwables.toRuntimeException("Constructor not found in " + targetClass.getName());
	}
	
	public Collection<Constructor<?>> findAllAndMakeThemAccessible(
		Class<?> targetClass,
		Class<?>... arguments
	) {	
		String cacheKey = getCacheKey(targetClass, "all constructors with input parameters", arguments);
		ClassLoader targetClassClassLoader = Classes.getClassLoader(targetClass);
		return Cache.uniqueKeyForConstructors.getOrUploadIfAbsent(targetClassClassLoader, cacheKey, () -> {
			ConstructorCriteria criteria = ConstructorCriteria.create().parameterTypesAreAssignableFrom(arguments);
			if (arguments != null && arguments.length == 0) {
				criteria.or().parameter((parameters, idx) -> parameters.length == 1 && parameters[0].isVarArgs());
			}
			return Collections.unmodifiableCollection(
				findAllAndMakeThemAccessible(targetClass).stream().filter(
					criteria.getPredicateOrTruePredicateIfPredicateIsNull()
				).collect(
					Collectors.toCollection(LinkedHashSet::new)
				)
			);
		});
	}
	
	public Collection<Constructor<?>> findAllAndMakeThemAccessible(
		Class<?> targetClass
	) {
		String cacheKey = getCacheKey(targetClass, "all constructors");
		ClassLoader targetClassClassLoader = Classes.getClassLoader(targetClass);
		Collection<Constructor<?>> members = Cache.uniqueKeyForConstructors.getOrUploadIfAbsent(
			targetClassClassLoader, cacheKey, () -> {
				return Collections.unmodifiableCollection(
					findAllAndApply(
						ConstructorCriteria.byScanUpTo((lastClassInHierarchy, currentScannedClass) -> {
		                    return lastClassInHierarchy.equals(currentScannedClass);
		                }), targetClass, (member) -> 
							LowLevelObjectsHandler.setAccessible(member, true)
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
