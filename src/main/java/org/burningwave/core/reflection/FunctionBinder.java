/*
 * This file is part of Burningwave Core.
 *
 * Author: Roberto Gentli
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
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;

import org.burningwave.core.classes.MemberFinder;
import org.burningwave.core.classes.MethodCriteria;

public class FunctionBinder  extends Binder.Multi.Abst {
		

	private FunctionBinder(MemberFinder memberFinder, CallerRetriever lambdaCallerRetriever,
			Function<Integer, Class<?>> classRetriever) {
		super(memberFinder, lambdaCallerRetriever, classRetriever);
	}
	
	public static FunctionBinder create(MemberFinder memberFinder, CallerRetriever lambdaCallerRetriever, Function<Integer, Class<?>> classRetriever) {
		return new FunctionBinder(memberFinder, lambdaCallerRetriever, classRetriever);
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public <F> F bindTo(Object targetObject, String methodName, Class<?>... inputType) throws Throwable {
		return (F)bindTo(targetObject, memberFinder.findOne(
			MethodCriteria.forName(
				methodName::equals
			).and().parameterTypes((parameterTypes) -> parameterTypes.length == inputType.length),
			targetObject
		));
	}

	@SuppressWarnings("unchecked")
	@Override
	public <F, I> Map<I, F> bindToMany(Object targetObject, String methodName) throws Throwable {
		Collection<Method> methods = memberFinder.findAll(
			MethodCriteria.forName((name) -> 
				name.matches(methodName)
			).and().returnType((returnType) ->
				returnType != void.class
			).and().parameterTypes((parameterTypes) ->
				parameterTypes.length > 0
			),
			targetObject
		);
		Map<I, F> functions = createResultMap();
		for (Method method: methods) {
			functions.put((I)method, bindTo(targetObject, method));
		}
		return (Map<I, F>) functions;
	}
	
	
	@SuppressWarnings("unchecked")
	@Override
	public <F> F bindTo(Object targetObject, Method method) throws Throwable {
		if (Modifier.isStatic(method.getModifiers())) {
			return (F)staticBindTo(method);
		} else {
			return (F)dynamicBindTo(targetObject, method);
		}
	}
	
	private <F> F staticBindTo(Method method) throws Throwable {
		MethodHandles.Lookup caller = MethodHandles.lookup();
		MethodType methodParameters = MethodType.methodType(method.getReturnType(), method.getParameterTypes());
		Class<?> cls = retrieveClass(Function.class, method.getParameterTypes().length);
		MethodHandle _targetClass = caller.unreflect(method);
		CallSite site = LambdaMetafactory.metafactory(
			caller,
		    "apply", // include types of the values to bind:
		    MethodType.methodType(cls),
		    methodParameters.generic(), 
		    _targetClass, 
		    methodParameters);
		return (F)site.getTarget().invoke();
	}


	private <F> F dynamicBindTo(Object targetObject, Method method) throws Throwable {
		MethodType methodParameters = MethodType.methodType(method.getReturnType(), method.getParameterTypes());
		Class<?> cls = retrieveClass(Function.class, method.getParameterTypes().length);
		MethodHandles.Lookup caller = lambdaCallerRetriever.retrieve(cls);
		MethodHandle targetClass = 	caller.unreflect(method);
		CallSite site = LambdaMetafactory.metafactory(
			caller,
		    "apply", // include types of the values to bind:
		    MethodType.methodType(cls, targetObject.getClass()),
		    methodParameters.generic(), 
		    targetClass, 
		    methodParameters);
		return (F) site.getTarget().bindTo(targetObject).invoke();
	}
}
