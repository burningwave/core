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


import static org.burningwave.core.assembler.StaticComponentContainer.Cache;
import static org.burningwave.core.assembler.StaticComponentContainer.Classes;
import static org.burningwave.core.assembler.StaticComponentContainer.Driver;
import static org.burningwave.core.assembler.StaticComponentContainer.Strings;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.burningwave.core.function.Executor;
import org.burningwave.core.function.ThrowingFunction;

@SuppressWarnings("unchecked")
public class Methods extends Members.Handler.OfExecutable<Method, MethodCriteria> {

	public static Methods create() {
		return new Methods();
	}

	String createGetterMethodNameByFieldPath(String fieldPath) {
		String methodName =
			"get" + Strings.capitalizeFirstCharacter(fieldPath);
		return methodName;
	}

	String createSetterMethodNameByFieldPath(String fieldPath) {
		String methodName =
			"set" + Strings.capitalizeFirstCharacter(fieldPath);
		return methodName;
	}

	public Method findOneAndMakeItAccessible(Class<?> targetClass, String memberName, Class<?>... inputParameterTypesOrSubTypes) {
		Collection<Method> members = findAllByExactNameAndMakeThemAccessible(targetClass, memberName, inputParameterTypesOrSubTypes);
		if (members.size() == 1) {
			return members.stream().findFirst().get();
		} else if (members.size() > 1) {
			Collection<Method> membersThatMatch = searchForExactMatch(members, inputParameterTypesOrSubTypes);
			if (membersThatMatch.size() == 1) {
				return membersThatMatch.stream().findFirst().get();
			}
			org.burningwave.core.assembler.StaticComponentContainer.Driver.throwException(
				new IllegalArgumentException(
					Strings.compile(
						"Found more than one of method named {} with argument types {} in {} hierarchy",
						memberName,
						String.join(", ", Arrays.asList(inputParameterTypesOrSubTypes).stream().map(cls -> cls.getName()).collect(Collectors.toList())),
						targetClass.getName()
					)
				)
			);
		}
		return null;
	}

	public Method findFirstAndMakeItAccessible(Class<?> targetClass, String memberName, Class<?>... inputParameterTypesOrSubTypes) {
		Collection<Method> members = findAllByExactNameAndMakeThemAccessible(targetClass, memberName, inputParameterTypesOrSubTypes);
		if (members.size() == 1) {
			return members.stream().findFirst().get();
		} else if (members.size() > 1) {
			Collection<Method> membersThatMatch = searchForExactMatch(members, inputParameterTypesOrSubTypes);
			if (!membersThatMatch.isEmpty()) {
				return membersThatMatch.stream().findFirst().get();
			}
			return members.stream().findFirst().get();
		}
		return null;
	}

	public Collection<Method> findAllByExactNameAndMakeThemAccessible(
		Class<?> targetClass,
		String methodName,
		Class<?>... inputParameterTypesOrSubTypes
	) {
		return findAllByNamePredicateAndMakeThemAccessible(targetClass, "equals " + methodName, methodName::equals, inputParameterTypesOrSubTypes);
	}

	public Collection<Method> findAllByMatchedNameAndMakeThemAccessible(
		Class<?> targetClass,
		String methodName,
		Class<?>... inputParameterTypesOrSubTypes
	) {
		return findAllByNamePredicateAndMakeThemAccessible(targetClass, "match " + methodName, methodName::matches, inputParameterTypesOrSubTypes);
	}

	private Collection<Method> findAllByNamePredicateAndMakeThemAccessible(
		Class<?> targetClass,
		String cacheKeyPrefix,
		Predicate<String> namePredicate,
		Class<?>... inputParameterTypesOrSubTypes
	) {
		String cacheKey = getCacheKey(targetClass, cacheKeyPrefix, inputParameterTypesOrSubTypes);
		ClassLoader targetClassClassLoader = Classes.getClassLoader(targetClass);
		return Cache.uniqueKeyForMethods.getOrUploadIfAbsent(targetClassClassLoader, cacheKey, () -> {
			MethodCriteria criteria = MethodCriteria.forEntireClassHierarchy()
				.name(namePredicate)
				.and().parameterTypesAreAssignableFrom(inputParameterTypesOrSubTypes);
			if (inputParameterTypesOrSubTypes != null && inputParameterTypesOrSubTypes.length == 0) {
				criteria = criteria.or(MethodCriteria.forEntireClassHierarchy().name(namePredicate).and().parameter((parameters, idx) -> parameters.length == 1 && parameters[0].isVarArgs()));
			}
			MethodCriteria finalCriteria = criteria;
			return Cache.uniqueKeyForMethods.getOrUploadIfAbsent(targetClassClassLoader, cacheKey, () ->
				findAllAndApply(
						finalCriteria, targetClass, (member) -> {
						setAccessible(member, true);
					}
				)
			);
		});
	}

	public Collection<Method> findAllAndMakeThemAccessible(
		Class<?> targetClass
	) {
		String cacheKey = getCacheKey(targetClass, "all methods");
		ClassLoader targetClassClassLoader = Classes.getClassLoader(targetClass);
		Collection<Method> members = Cache.uniqueKeyForMethods.getOrUploadIfAbsent(
			targetClassClassLoader, cacheKey, () -> {
				return findAllAndMakeThemAccessible(
					MethodCriteria.forEntireClassHierarchy(), targetClass
				);
			}
		);
		return members;
	}

	public 	<T> T invokeStatic(Class<?> targetClass, String methodName, Object... arguments) {
		return invoke(
			targetClass, null, methodName, method ->
				(T)invoke(null,
					method,
					getArgumentArray(
						method,
						this::getArgumentListWithArrayForVarArgs,
						ArrayList::new,
						arguments
					)
				),
			arguments
		);
	}

	public <T> T invoke(Object target, String methodName, Object... arguments) {
		return invoke(
			Classes.retrieveFrom(target),
			null, methodName, method ->
				invoke(
						target, method, getArgumentArray(
						method,
						this::getArgumentListWithArrayForVarArgs,
						ArrayList::new,
						arguments
					)
				),
			arguments
		);
	}

	public <T> T invoke(Object target, Method method, Object... params) {
		if (params == null) {
			params = new Object[] {null};
		}
		try {
			return (T)method.invoke(target, params);
		} catch (Throwable exc) {
			return Driver.invoke(target, method, params);
		}
	}

	private <T> T invoke(Class<?> targetClass, Object target, String methodName, ThrowingFunction<Method, T, Throwable> methodInvoker, Object... arguments) {
		return Executor.get(() -> {
			Method method = findFirstAndMakeItAccessible(targetClass, methodName, Classes.retrieveFrom(arguments));
			if (method == null) {
				org.burningwave.core.assembler.StaticComponentContainer.Driver.throwException(
					new NoSuchMethodException(
						Strings.compile(
							"Method {} not found in {} hierarchy", methodName, targetClass.getName()
						)
					)
				);
			}
			return methodInvoker.apply(method);
		});
	}

	public 	<T> T invokeStaticDirect(Class<?> targetClass, String methodName, Object... arguments) {
		return (T) invokeDirect(targetClass, null, methodName, ArrayList::new, arguments);
	}

	public <T> T invokeDirect(Object target, String methodName, Object... arguments) {
		return (T) invokeDirect(
			Classes.retrieveFrom(target),
			target, methodName, () -> {
				List<Object> argumentList = new ArrayList<>();
				argumentList.add(target);
				return argumentList;
			},
			arguments
		);
	}

	private <T> T invokeDirect(Class<?> targetClass, Object target, String methodName, Supplier<List<Object>> listSupplier,  Object... arguments) {
		Class<?>[] argsType = Classes.retrieveFrom(arguments);
		Members.Handler.OfExecutable.Box<Method> methodHandleBox = findDirectHandleBox(targetClass, methodName, argsType);
		return Executor.get(() -> {
				Method method = methodHandleBox.getExecutable();
				List<Object> argumentList = getFlatArgumentList(method, listSupplier, arguments);
				return (T)methodHandleBox.getHandler().invokeWithArguments(argumentList);
			}
		);
	}

	public MethodHandle findDirectHandle(Class<?> targetClass, String methodName, Class<?>... inputParameterTypesOrSubTypes) {
		return findDirectHandleBox(targetClass, methodName, inputParameterTypesOrSubTypes).getHandler();
	}

	private Members.Handler.OfExecutable.Box<Method> findDirectHandleBox(Class<?> targetClass, String methodName, Class<?>... inputParameterTypesOrSubTypes) {
		String cacheKey = getCacheKey(targetClass, "equals " + methodName, inputParameterTypesOrSubTypes);
		ClassLoader targetClassClassLoader = Classes.getClassLoader(targetClass);
		Members.Handler.OfExecutable.Box<Method> entry =
			(Box<Method>)Cache.uniqueKeyForExecutableAndMethodHandle.get(targetClassClassLoader, cacheKey);
		if (entry == null) {
			Method method = findFirstAndMakeItAccessible(targetClass, methodName, inputParameterTypesOrSubTypes);
			if (method == null) {
				org.burningwave.core.assembler.StaticComponentContainer.Driver.throwException(
					new NoSuchMethodException(
						Strings.compile(
							"Method {} not found in {} hierarchy", methodName, targetClass.getName()
						)
					)
				);
			}
			entry = findDirectHandleBox(
				method, targetClassClassLoader, cacheKey
			);
		}
		return entry;
	}


	@Override
	MethodHandle retrieveMethodHandle(MethodHandles.Lookup consulter, Method method) throws java.lang.NoSuchMethodException, IllegalAccessException {
		Class<?> methodDeclaringClass = method.getDeclaringClass();
		return !Modifier.isStatic(method.getModifiers())?
			consulter.findSpecial(
				methodDeclaringClass, retrieveNameForCaching(method),
				MethodType.methodType(method.getReturnType(), method.getParameterTypes()),
				methodDeclaringClass
			):
			consulter.findStatic(
				methodDeclaringClass, retrieveNameForCaching(method),
				MethodType.methodType(method.getReturnType(), method.getParameterTypes())
			);
	}

	@Override
	String retrieveNameForCaching(Method method) {
		return method.getName();
	}

	public static class NoSuchMethodException extends RuntimeException {

		private static final long serialVersionUID = -2912826056405333039L;

		public NoSuchMethodException(String message) {
			super(message);
		}

	}
}
