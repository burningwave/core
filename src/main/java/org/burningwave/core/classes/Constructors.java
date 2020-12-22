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
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

import org.burningwave.core.function.Executor;


@SuppressWarnings("unchecked")
public class Constructors extends Members.Handler.OfExecutable<Constructor<?>, ConstructorCriteria>  {
	
	public static Constructors create() {
		return new Constructors();
	}	
	
	public <T> T newInstanceOf(
		Class<?> targetClass,
		Object... arguments
	) {	
		Constructor<?> ctor = findFirstAndMakeItAccessible(targetClass, Classes.retrieveFrom(arguments));
		if (ctor == null) {
			Throwables.throwException("Constructor not found in {}", targetClass.getName());
		}
		return Executor.get(() -> {
			//logInfo("Invoking " + ctor);
			return (T)LowLevelObjectsHandler.newInstance(
				ctor,
				getArgumentArray(
					ctor,
					this::getArgumentListWithArrayForVarArgs,
					ArrayList::new, 
					arguments
				)
			);
		});
	}
	
	public <T> T newInstanceDirectOf(
		Class<?> targetClass,
		Object... arguments
	) {
		Class<?>[] argsType = Classes.retrieveFrom(arguments);
		Members.Handler.OfExecutable.Box<Constructor<?>> methodHandleBox = findDirectHandleBox(targetClass, argsType);
		return Executor.get(() -> {
				Constructor<?> ctor = methodHandleBox.getExecutable();
				//logInfo("Direct invoking of " + ctor);
				return (T)methodHandleBox.getHandler().invokeWithArguments(
					getFlatArgumentList(ctor, ArrayList::new, arguments)
				);
			}
		);
	}

	public Constructor<?> findOneAndMakeItAccessible(Class<?> targetClass, Class<?>... argumentTypes) {
		Collection<Constructor<?>> members = findAllAndMakeThemAccessible(targetClass, argumentTypes);
		if (members.size() == 1) {
			return members.stream().findFirst().get();
		} else if (members.size() > 1) {
			Collection<Constructor<?>> membersThatMatch = searchForExactMatch(members, argumentTypes);
			if (membersThatMatch.size() == 1) {
				return membersThatMatch.stream().findFirst().get();
			}
			Throwables.throwException(
				"Found more than one of constructor with argument types {} in {} class",
				String.join(", ", Arrays.asList(argumentTypes).stream().map(cls -> cls.getName()).collect(Collectors.toList())),
				targetClass.getName()
			);
		}
		return null; 
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
		return null;
	}
	
	public Collection<Constructor<?>> findAllAndMakeThemAccessible(
		Class<?> targetClass,
		Class<?>... arguments
	) {	
		String cacheKey = getCacheKey(targetClass, "all constructors with input parameters", arguments);
		ClassLoader targetClassClassLoader = Classes.getClassLoader(targetClass);
		return Cache.uniqueKeyForConstructors.getOrUploadIfAbsent(targetClassClassLoader, cacheKey, () -> {
			ConstructorCriteria criteria = ConstructorCriteria.withoutConsideringParentClasses().parameterTypesAreAssignableFrom(arguments);
			if (arguments != null && arguments.length == 0) {
				criteria.or().parameter((parameters, idx) -> parameters.length == 1 && parameters[0].isVarArgs());
			}
			return Collections.unmodifiableCollection(
				findAllAndApply(
					criteria, 
					targetClass,
					(member) -> 
						setAccessible(member, true)
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
						ConstructorCriteria.withoutConsideringParentClasses(), targetClass, (member) -> 
							setAccessible(member, true)
					)
				);
			}
		);
		return members;
	}
	
	public MethodHandle findDirectHandle(Class<?> targetClass, Class<?>... arguments) {
		return findDirectHandleBox(targetClass, arguments).getHandler();
	}
	
	private Members.Handler.OfExecutable.Box<Constructor<?>> findDirectHandleBox(Class<?> targetClass, Class<?>... argsType) {
		String nameForCaching = retrieveNameForCaching(targetClass);
		String cacheKey = getCacheKey(targetClass, "equals " + nameForCaching, argsType);
		ClassLoader targetClassClassLoader = Classes.getClassLoader(targetClass);
		Members.Handler.OfExecutable.Box<Constructor<?>> entry =
			(Box<Constructor<?>>)Cache.uniqueKeyForExecutableAndMethodHandle.get(targetClassClassLoader, cacheKey);
		if (entry == null) {
			Constructor<?> ctor = findFirstAndMakeItAccessible(targetClass, argsType);
			entry = findDirectHandleBox(
				ctor, targetClassClassLoader, cacheKey
			);
		}
		return entry;
	}	
	
	@Override
	MethodHandle retrieveMethodHandle(MethodHandles.Lookup consulter, Constructor<?> constructor) throws NoSuchMethodException, IllegalAccessException {
		return consulter.findConstructor(
			constructor.getDeclaringClass(),
			MethodType.methodType(void.class, constructor.getParameterTypes())
		);
	}
	
	@Override
	String retrieveNameForCaching(Constructor<?> constructor) {
		return retrieveNameForCaching(constructor.getDeclaringClass());
	}
	
	String retrieveNameForCaching(Class<?> cls) {
		return Classes.retrieveSimpleName(cls.getName());
	}
}
