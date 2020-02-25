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

import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import org.burningwave.core.classes.MemberFinder;
import org.burningwave.core.classes.MethodCriteria;

public class PredicateBinder  extends Binder.Multi.Abst {
		

	private PredicateBinder(
		MemberFinder memberFinder,
		ConsulterRetriever lambdaCallerRetriever,
		BiFunction<ClassLoader, Integer, Class<?>> classRetriever
	) {
		super(memberFinder, lambdaCallerRetriever, classRetriever);
	}
	
	public static PredicateBinder create(MemberFinder memberFinder, ConsulterRetriever lambdaCallerRetriever, BiFunction<ClassLoader, Integer, Class<?>> classRetriever) {
		return new PredicateBinder(memberFinder, lambdaCallerRetriever, classRetriever);
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public <F> F bindTo(Class<?> targetObjectClass, String methodName, Class<?>... inputType) throws Throwable {
		return (F)bindTo(memberFinder.findOne(
			MethodCriteria.forName(
				methodName::equals
			).and().parameterTypes((parameterTypes) -> parameterTypes.length == inputType.length),
			targetObjectClass
		));
	}

	@SuppressWarnings("unchecked")
	@Override
	public <F, I> Map<I, F> bindToMany(Class<?> targetObjectClass, String methodName) throws Throwable {
		Collection<Method> methods = memberFinder.findAll(
			MethodCriteria.forName((name) -> 
				name.matches(methodName)
			).and().returnType((returnType) ->
				returnType == boolean.class || returnType == Boolean.class
			).and().parameterTypes((parameterTypes) ->
				parameterTypes.length > 0
			),
			targetObjectClass
		);
		Map<I, F> functions = createResultMap();
		for (Method method: methods) {
			functions.put((I)method, bindTo(method));
		}
		return (Map<I, F>) functions;
	}
	
	
	@SuppressWarnings("unchecked")
	@Override
	public <F> F bindTo(Method method) throws Throwable {
		if (Modifier.isStatic(method.getModifiers())) {
			return (F)staticBindTo(method);
		} else {
			return (F)dynamicBindTo(method);
		}
	}
	
	private <F> F staticBindTo(Method targetMethod) throws Throwable {
		Lookup caller = consulterRetriever.retrieve(targetMethod.getDeclaringClass());
		MethodType methodParameters = MethodType.methodType(targetMethod.getReturnType(), targetMethod.getParameterTypes());
		Class<?> functionalInterfaceClass = retrieveClass(Predicate.class, targetMethod.getDeclaringClass().getClassLoader(), targetMethod.getParameterTypes().length);
		CallSite site = LambdaMetafactory.metafactory(
			caller,
		    "test",
		    MethodType.methodType(functionalInterfaceClass),
		    methodParameters.generic().changeReturnType(boolean.class), 
		    caller.unreflect(targetMethod), 
		    methodParameters
		);
		return (F)site.getTarget().invoke();
	}


	private <F> F dynamicBindTo(Method targetMethod) throws Throwable {
		Lookup classLoaderConsulter = consulterRetriever.retrieve(targetMethod.getDeclaringClass());
		MethodType methodParameters = MethodType.methodType(targetMethod.getReturnType(), targetMethod.getParameterTypes());
		MethodHandle methodHandle = classLoaderConsulter.findSpecial(targetMethod.getDeclaringClass(), targetMethod.getName(), methodParameters, targetMethod.getDeclaringClass());
		Class<?> functionalInterfaceClass = retrieveClass(Function.class, targetMethod.getDeclaringClass().getClassLoader(), targetMethod.getParameterTypes().length + 1);
		return (F)LambdaMetafactory.metafactory(
			classLoaderConsulter, "test",
			MethodType.methodType(functionalInterfaceClass),
			methodHandle.type().generic().changeReturnType(boolean.class),
			methodHandle,
			methodHandle.type()
		).getTarget().invoke();
	}
}
