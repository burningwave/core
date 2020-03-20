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
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import org.burningwave.core.classes.MethodCriteria;
import org.burningwave.core.function.ThrowingSupplier;


public class MethodHelper extends MemberHelper<Method> {
	
	private MethodHelper() {
		super();
	}
	
	public static MethodHelper create() {
		return new MethodHelper();
	}

	public String createGetterMethodNameByPropertyName(String property) {
		String methodName = 
				"get" + capitalizeFirstCharacter(property);
		return methodName;
	}

	public String createSetterMethodNameByPropertyName(String property) {
		String methodName = 
				"set" + capitalizeFirstCharacter(property);
		return methodName;
	}
	
	private String capitalizeFirstCharacter(String value) {
		return Character.valueOf(value.charAt(0)).toString().toUpperCase()
		+ value.substring(1, value.length());
	}
	
	public Method findOneAndMakeItAccessible(Object target, String methodName, Object... arguments) {
		return findOneAndMakeItAccessible(target, methodName, true, arguments);
	}
	
	public Method findOneAndMakeItAccessible(Object target, String methodName, boolean cacheMethod, Object... arguments) {
		String cacheKey = getCacheKey(target, "equals " + methodName, arguments);
		Collection<Method> members = Cache.uniqueKeyForMethods.get(cacheKey);
		if (members == null) {	
			 members = findAllAndMakeThemAccessible(target, methodName::equals, arguments);
			 if (members.size() != 1) {
					Throwables.toRuntimeException("Method " + methodName
						+ " not found or found more than one methods in " + Classes.retrieveFrom(target).getName()
						+ " hierarchy");
			 }
			 if (cacheMethod) {
				final Collection<Method> toUpload = members;
				Cache.uniqueKeyForMethods.upload(cacheKey, () -> toUpload);
			 }
		}		
		return members.stream().findFirst().get();
	}

	@SuppressWarnings("unchecked")
	public Collection<Method> findAllAndMakeThemAccessible(Object target, Predicate<String> predicateForName, Object... arguments) {
		MethodCriteria criteria = MethodCriteria.create();
		if (predicateForName != null) {
			criteria.name(predicateForName
			).and();
		}
		criteria.parameterTypesAreAssignableFrom(arguments);
		Collection<Method> members = findAllAndApply(
			criteria, target, (member) -> member.setAccessible(true)
		);
		if (members.isEmpty()) {
			Throwables.toRuntimeException("Method not found in any class of " + Classes.retrieveFrom(target).getName()
				+ " hierarchy");
		}
		return members;
	}
	
	public <T> T invoke(Object target, String methodName, Object... arguments) {
		return invoke(target, methodName, true, arguments);
	}

	@SuppressWarnings("unchecked")
	public <T> T invoke(Object target, String methodName, boolean cacheMethod, Object... arguments) {
		return ThrowingSupplier.get(() -> {
			Method method = findOneAndMakeItAccessible(target, methodName, cacheMethod, arguments);
			return (T)method.invoke(Modifier.isStatic(method.getModifiers()) ? null : target, arguments);
		});
	}
	
	public <T> T invokeDirect(Object target, String methodName, Object... arguments) {
		return invokeDirect(target, methodName, true, arguments);
	}
	
	@SuppressWarnings("unchecked")
	public <T> T invokeDirect(Object target, String methodName, boolean cacheMethod, Object... arguments) {
		Method method = findOneAndMakeItAccessible(target, methodName, arguments);
		final AtomicReference<MethodHandle> methodHandleWrapper = new AtomicReference<>(Cache.uniqueKeyForMethodHandle.get(method));
		if (methodHandleWrapper.get() == null) {
			methodHandleWrapper.set(
				convertToMethodHandle(
					method
				)
			);
			if (cacheMethod) {
				Cache.uniqueKeyForMethodHandle.upload(method, methodHandleWrapper.get());
			}
		}
		return ThrowingSupplier.get(() -> {
				if (Modifier.isStatic(method.getModifiers())) {
					return (T)methodHandleWrapper.get().invokeWithArguments(arguments);
				} else {
					return (T)methodHandleWrapper.get().bindTo(target).invokeWithArguments(arguments);
				}
			}
		);
	}
	
	public <T> Collection<T> invokeAll(Object target, String methodNameRegEx, Object... arguments) {
		return invokeAll(target, methodNameRegEx, true, arguments);
	}
	
	@SuppressWarnings("unchecked")
	public <T> Collection<T> invokeAll(Object target, String methodNameRegEx, boolean cacheMember, Object... arguments) {
		return ThrowingSupplier.get(() -> {
			String cacheKey = getCacheKey(target, "matches " + methodNameRegEx, arguments);
			Collection<Method> members = Cache.uniqueKeyForMethods.get(cacheKey);
			if (members == null) {	
				members = findAllAndMakeThemAccessible(target, (name) -> name.matches(methodNameRegEx), arguments);
				if (cacheMember) {
					final Collection<Method> toUpload = members;
					Cache.uniqueKeyForMethods.upload(cacheKey, () -> toUpload);
				}
			}			
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
