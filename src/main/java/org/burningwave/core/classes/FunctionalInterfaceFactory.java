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
import static org.burningwave.core.assembler.StaticComponentContainer.Constructors;
import static org.burningwave.core.assembler.StaticComponentContainer.Methods;
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.burningwave.core.Component;
import org.burningwave.core.function.Executor;
import org.burningwave.core.function.ThrowingSupplier;

public class FunctionalInterfaceFactory implements Component {
	private ClassFactory classFactory;
	
	private FunctionalInterfaceFactory(ClassFactory classFactory) {
		this.classFactory = classFactory;
	}

	public static FunctionalInterfaceFactory create(ClassFactory classFactory) {
		return new FunctionalInterfaceFactory(classFactory);
	}
	
	public <T> T getOrCreate(Class<?> targetClass, String methodName, Class<?>... argumentTypes) {
		Method method = Methods.findOneAndMakeItAccessible(targetClass, methodName, argumentTypes);
		if (method == null) {
			Throwables.throwException("Method {} not found or found more than one method in {} hierarchy", methodName, targetClass.getName());
		}		
		return getOrCreate(method);
	}
	
	public <F> F getOrCreate(Executable executable) {
		if (executable instanceof Method) {
			Method targetMethod = (Method)executable;
			if (targetMethod.getParameterTypes().length == 0 && targetMethod.getReturnType() == void.class) {
				return getBindedRunnable(targetMethod);
			} else if (targetMethod.getParameterTypes().length == 0 && targetMethod.getReturnType() != void.class) {
				return getBindedSupplier(targetMethod);
			} else if (targetMethod.getParameterTypes().length > 0 && targetMethod.getReturnType() == void.class) {
				return getBindedConsumer(targetMethod);
			} else if (targetMethod.getParameterTypes().length > 0 && (targetMethod.getReturnType() == boolean.class || targetMethod.getReturnType() == Boolean.class)) {
				return getBindedPredicate(targetMethod);
			} else if (targetMethod.getParameterTypes().length > 0 && targetMethod.getReturnType() != void.class) {
				return getBindedFunction(targetMethod);
			}
		} else if (executable instanceof Constructor) {
			Constructor<?> targetConstructor = (Constructor<?>)executable;
			if (targetConstructor.getParameterTypes().length == 0) {
				return getBindedSupplier(targetConstructor);
			} else {
				return getBindedFunction(targetConstructor);
			}
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	<F> F getBindedRunnable(Executable executable) {
		return (F) Cache.bindedFunctionalInterfaces.getOrUploadIfAbsent(
			Classes.getClassLoader(executable.getDeclaringClass()), 
			getCacheKey(executable), () -> 
			Executor.get(() -> {
				Supplier<Members.Handler.OfExecutable.Box<? extends Executable>> methodHandleBoxSupplier = 
					executable instanceof Constructor ?	
						() -> Constructors.findDirectHandleBox((Constructor<?>)executable) :
						() -> Methods.findDirectHandleBox((Method)executable);
				return bindTo(
					methodHandleBoxSupplier, () -> 
					Modifier.isStatic(executable.getModifiers()) || executable instanceof Constructor ?
						new AbstractMap.SimpleEntry<>(Runnable.class, "run") :
						new AbstractMap.SimpleEntry<>(Consumer.class, "accept"),
					methodHandle ->
						methodHandle.type().generic().changeReturnType(void.class)
				);
			})
		);
	}

	@SuppressWarnings("unchecked")
	<F> F getBindedSupplier(Executable executable) {
		return (F) Cache.bindedFunctionalInterfaces.getOrUploadIfAbsent(
			Classes.getClassLoader(executable.getDeclaringClass()),	
			getCacheKey(executable), () -> 
			Executor.get(() -> {
				Supplier<Members.Handler.OfExecutable.Box<? extends Executable>> methodHandleBoxSupplier = 
					executable instanceof Constructor ?	
						() -> Constructors.findDirectHandleBox((Constructor<?>)executable) :
						() -> Methods.findDirectHandleBox((Method)executable);
				return bindTo(
					methodHandleBoxSupplier, () -> 
					Modifier.isStatic(executable.getModifiers()) || executable instanceof Constructor ?
						new AbstractMap.SimpleEntry<>(Supplier.class, "get") :
						new AbstractMap.SimpleEntry<>(Function.class, "apply"),
					methodHandle ->
						methodHandle.type().generic()
				);
			})
		);
	}

	@SuppressWarnings("unchecked")
	<F> F getBindedFunction(Executable executable) {
		return (F) Cache.bindedFunctionalInterfaces.getOrUploadIfAbsent(
			Classes.getClassLoader(executable.getDeclaringClass()),
			getCacheKey(executable), () -> 
				Executor.get(() -> {
					Supplier<Members.Handler.OfExecutable.Box<? extends Executable>> methodHandleBoxSupplier = 
						executable instanceof Constructor ?	
							() -> Constructors.findDirectHandleBox((Constructor<?>)executable) :
							() -> Methods.findDirectHandleBox((Method)executable);
					return bindTo(
						methodHandleBoxSupplier, () -> 
						new AbstractMap.SimpleEntry<>(
							retrieveClass(
								Function.class,
								(parameterCount) -> 
									classFactory.loadOrBuildAndDefineFunctionSubType(
										executable.getDeclaringClass().getClassLoader(), parameterCount
									),
								Modifier.isStatic(executable.getModifiers()) || executable instanceof Constructor ?
									executable.getParameterCount() : 
									executable.getParameterCount() + 1
							), 
							"apply"
						),
						methodHandle ->
							methodHandle.type().generic()
				);
			})
		);
	}

	@SuppressWarnings("unchecked")
	<F> F getBindedConsumer(Method targetMethod) {
		return (F) Cache.bindedFunctionalInterfaces.getOrUploadIfAbsent(
			Classes.getClassLoader(targetMethod.getDeclaringClass()),
			getCacheKey(targetMethod), () -> 
			Executor.get(() ->
				bindTo(() -> 
					Methods.findDirectHandleBox(targetMethod), () -> 
					new AbstractMap.SimpleEntry<>(
						retrieveClass(
							Consumer.class,
							(parameterCount) ->
								classFactory.loadOrBuildAndDefineConsumerSubType(targetMethod.getDeclaringClass().getClassLoader(), parameterCount),
							Modifier.isStatic(targetMethod.getModifiers()) ?
								targetMethod.getParameterCount() : 
								targetMethod.getParameterCount() + 1
						), 
						"accept"
					),
					methodHandle ->
						methodHandle.type().generic().changeReturnType(void.class)
				)
			)
		);
	}

	@SuppressWarnings("unchecked")
	<F> F getBindedPredicate(Method targetMethod) {
		return (F) Cache.bindedFunctionalInterfaces.getOrUploadIfAbsent(
			Classes.getClassLoader(targetMethod.getDeclaringClass()),
			getCacheKey(targetMethod), () -> 
			Executor.get(() -> bindTo(() -> 
					Methods.findDirectHandleBox(targetMethod), () -> 
					new AbstractMap.SimpleEntry<>(
						retrieveClass(
							Predicate.class,
							(parameterCount) ->
								classFactory.loadOrBuildAndDefinePredicateSubType(targetMethod.getDeclaringClass().getClassLoader(), parameterCount),
							Modifier.isStatic(targetMethod.getModifiers()) ?
								targetMethod.getParameterCount() : 
								targetMethod.getParameterCount() + 1
						), 
						"test"
					),
					methodHandle ->
						methodHandle.type().generic().changeReturnType(boolean.class)
				)
			)
		);
	}
	
	private <F> F bindTo(
		Supplier<Members.Handler.OfExecutable.Box<? extends Executable>> methodHandleBoxSupplier, 
		ThrowingSupplier<Map.Entry<Class<?>, String>, Throwable> functionalInterfaceBagSupplier,
		Function<MethodHandle, MethodType> functionalInterfaceSignatureSupplier
	) throws Throwable {
		Members.Handler.OfExecutable.Box<? extends Executable> methodHandleBox = methodHandleBoxSupplier.get();
		MethodHandle methodHandle = methodHandleBox.getHandler();
		Map.Entry<Class<?>, String> functionalInterfaceBag = functionalInterfaceBagSupplier.get();
		return (F)LambdaMetafactory.metafactory(
			methodHandleBox.getConsulter(),
			functionalInterfaceBag.getValue(),
		    MethodType.methodType(functionalInterfaceBag.getKey()),
		    functionalInterfaceSignatureSupplier.apply(methodHandle),
		    methodHandle, 
		    methodHandle.type()
		).getTarget().invoke();
	}
	
	Class<?> retrieveClass(Class<?> cls, Function<Integer, Class<?>> classRetriever, int parametersCount) throws ClassNotFoundException {
		if (parametersCount < 3) {
			String className = parametersCount == 2 ?
				Optional.ofNullable(cls.getPackage()).map(pkg -> pkg.getName() + ".").orElse("") + "Bi" + cls.getSimpleName() :
				cls.getName();
			return Class.forName(
				className, 
				true,
				Classes.getClassLoader(cls)
			);	
		} else {
			return classRetriever.apply(parametersCount);
		}
	}
	String getCacheKey(Executable executable) {
		Class<?> targetMethodDeclaringClass = executable.getDeclaringClass();
		Parameter[] parameters = executable.getParameters();
		String argumentsKey = "";
		if (parameters != null && parameters.length > 0) {
			StringBuffer argumentsKeyStringBuffer = new StringBuffer();
			Stream.of(parameters).forEach(parameter ->
				argumentsKeyStringBuffer.append("/" + parameter.getType().getName())
			);
			argumentsKey = argumentsKeyStringBuffer.toString();
		}
		String cacheKey = "/" + targetMethodDeclaringClass.getName() + "@" + targetMethodDeclaringClass.hashCode() +
			"/" + executable.getName() +
			argumentsKey;
		return cacheKey;		
	}
}
