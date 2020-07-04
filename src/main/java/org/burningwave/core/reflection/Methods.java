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
import static org.burningwave.core.assembler.StaticComponentContainer.Strings;
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.burningwave.core.classes.MethodCriteria;
import org.burningwave.core.function.ThrowingSupplier;

@SuppressWarnings("unchecked")
public class Methods extends MemberHelper<Method> {
	
	private Methods() {
		super();
	}
	
	public static Methods create() {
		return new Methods();
	}

	String createGetterMethodNameByPropertyName(String property) {
		String methodName = 
			"get" + Strings.capitalizeFirstCharacter(property);
		return methodName;
	}

	String createSetterMethodNameByPropertyName(String property) {
		String methodName = 
			"set" + Strings.capitalizeFirstCharacter(property);
		return methodName;
	}
	
	public Method findOneAndMakeItAccessible(Object target, String memberName, Object... arguments) {
		Collection<Method> members = findAllByExactNameAndMakeThemAccessible(target, memberName, arguments);
		if (members.size() != 1) {
			Throwables.toRuntimeException("Method " + memberName
				+ " not found or found more than one method in " + Classes.retrieveFrom(target).getName()
				+ " hierarchy");
		}
		return members.stream().findFirst().get();
	}
	
	public Method findFirstAndMakeItAccessible(Object target, String memberName, Object... arguments) {
		Collection<Method> members = findAllByExactNameAndMakeThemAccessible(target, memberName, arguments);
		if (members.size() < 1) {
			Throwables.toRuntimeException("Method " + memberName
				+ " not found in " + Classes.retrieveFrom(target).getName()
				+ " hierarchy");
		}
		return members.stream().findFirst().get();
	}
	
	public Collection<Method> findAllByExactNameAndMakeThemAccessible(
		Object target,
		String methodName,
		Object... arguments
	) {	
		return findAllByNamePredicateAndMakeThemAccessible(target, "equals " + methodName, methodName::equals, arguments);
	}
	
	public Collection<Method> findAllByMatchedNameAndMakeThemAccessible(
		Object target,
		String methodName,
		Object... arguments
	) {	
		return findAllByNamePredicateAndMakeThemAccessible(target, "match " + methodName, methodName::matches, arguments);
	}
	
	private Collection<Method> findAllByNamePredicateAndMakeThemAccessible(
		Object target,
		String cacheFirstKey,
		Predicate<String> namePredicate,
		Object... arguments
	) {	
		Class<?> targetClass = Classes.retrieveFrom(target);
		String cacheKey = getCacheKey(targetClass, cacheFirstKey);
		ClassLoader targetClassClassLoader = Classes.getClassLoader(targetClass);
		MethodCriteria criteria = MethodCriteria.create()
			.name(namePredicate)
			.and().parameterTypesAreAssignableFrom(arguments);
		return Cache.uniqueKeyForMethods.getOrUploadIfAbsent(targetClassClassLoader, cacheKey, () -> 
			Collections.unmodifiableCollection(
				findAllAndMakeThemAccessible(target).stream().filter(
					criteria.getPredicateOrTruePredicateIfPredicateIsNull()
				).collect(
					Collectors.toCollection(LinkedHashSet::new)
				)
			)
		);
	}

	public Collection<Method> findAllAndMakeThemAccessible(
		Object target
	) {
		Class<?> targetClass = Classes.retrieveFrom(target);
		String cacheKey = getCacheKey(targetClass, "all methods");
		ClassLoader targetClassClassLoader = Classes.getClassLoader(targetClass);
		Collection<Method> members = Cache.uniqueKeyForMethods.getOrUploadIfAbsent(
			targetClassClassLoader, cacheKey, () -> {
				return Collections.unmodifiableCollection(
					findAllAndApply(
						MethodCriteria.create(), target, (member) -> member.setAccessible(true)
					)
				);
			}
		);
		return members;
	}

	public <T> T invoke(Object target, String methodName, Object... arguments) {
		return ThrowingSupplier.get(() -> {
			Method method = findFirstAndMakeItAccessible(target, methodName, arguments);
			logInfo("Invoking " + method);
			return (T)method.invoke(Modifier.isStatic(method.getModifiers()) ? null : target, arguments);
		});
	}
	
	public <T> T invokeDirect(Object target, String methodName, Object... arguments) {
		Class<?> targetClass = Classes.retrieveFrom(target);
		Method method = findFirstAndMakeItAccessible(targetClass, methodName, arguments);
		String cacheKey = getCacheKey(targetClass, "equals " + methodName, arguments);
		ClassLoader targetClassClassLoader = Classes.getClassLoader(targetClass);
		MethodHandle methodHandle = Cache.uniqueKeyForMethodHandle.getOrUploadIfAbsent(
			targetClassClassLoader, cacheKey, 
			() -> 
				convertToMethodHandle(
					method
				)
		);
		return ThrowingSupplier.get(() -> {
				if (!Modifier.isStatic(method.getModifiers())) {
					return (T)methodHandle.bindTo(target).invokeWithArguments(arguments);
				}
				return (T)methodHandle.invokeWithArguments(arguments);
			}
		);
	}
	
	public <T> Collection<T> invokeAll(Object target, String methodNameRegEx, Object... arguments) {
		return ThrowingSupplier.get(() -> {
			Collection<Method> members = findAllByMatchedNameAndMakeThemAccessible(target, methodNameRegEx, arguments);		
			Collection<T> results = new ArrayList<>();
			for (Method member : members) {
				results.add((T)member.invoke(
					Modifier.isStatic(member.getModifiers()) ? null : target,
					arguments
				));
			}			
			return results;
		});
	}
	
	public MethodHandle convertToMethodHandle(Method method) {
		return convertToMethodHandleBag(method).getValue();
	}
	
	public Map.Entry<Lookup, MethodHandle> convertToMethodHandleBag(Method method) {
		try {
			Class<?> methodDeclaringClass = method.getDeclaringClass();
			MethodHandles.Lookup consulter = LowLevelObjectsHandler.getConsulter(methodDeclaringClass);
			return new AbstractMap.SimpleEntry<>(consulter,
				!Modifier.isStatic(method.getModifiers())?
					consulter.findSpecial(
						methodDeclaringClass, method.getName(),
						MethodType.methodType(method.getReturnType(), method.getParameterTypes()),
						methodDeclaringClass
					):
					consulter.findStatic(
						methodDeclaringClass, method.getName(),
						MethodType.methodType(method.getReturnType(), method.getParameterTypes())
					)
			);
		} catch (NoSuchMethodException | IllegalAccessException exc) {
			throw Throwables.toRuntimeException(exc);
		}
	}
}
