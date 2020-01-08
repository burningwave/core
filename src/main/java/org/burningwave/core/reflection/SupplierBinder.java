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

import org.burningwave.core.classes.MemberFinder;
import org.burningwave.core.classes.MethodCriteria;

public class SupplierBinder extends Binder.Abst {
	
	private SupplierBinder(MemberFinder memberFinder) {
		super(memberFinder);
	}
	
	public static SupplierBinder create(MemberFinder memberFinder) {
		return new SupplierBinder(memberFinder);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <O> O bindTo(Object targetObject, String methodName, Class<?>... outputType) throws Throwable {
		return (O)bindTo(
			targetObject, memberFinder.findOne(
				MethodCriteria.forName(
					methodName::equals
				).and().returnType(
					(returnType) -> returnType == outputType[0]
				),
				targetObject
			)
		);
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
	
	private <O> java.util.function.Supplier<O> staticBindTo(Method method) throws Throwable {
		MethodHandles.Lookup caller = MethodHandles.lookup();
		MethodType methodReturnType = MethodType.methodType(method.getReturnType());
		MethodHandle targetClass = 	caller.unreflect(method);
		CallSite site = LambdaMetafactory.metafactory(
			caller,
		    "get", // include types of the values to bind:
		    MethodType.methodType(java.util.function.Supplier.class),
		    methodReturnType.generic(), 
		    targetClass, 
		    methodReturnType);
		return (java.util.function.Supplier<O>) site.getTarget().invokeExact();
	}
	
	private <O> java.util.function.Supplier<O> dynamicBindTo(Object targetObject, Method method) throws Throwable {
		MethodHandles.Lookup caller = MethodHandles.lookup();
		MethodType methodParameters = MethodType.methodType(method.getReturnType());
		MethodHandle targetClass = 	caller.unreflect(method);
		CallSite site = LambdaMetafactory.metafactory(
			caller,
		    "get", // include types of the values to bind:
		    MethodType.methodType(java.util.function.Supplier.class, targetObject.getClass()),
		    methodParameters.generic(), 
		    targetClass, 
		    methodParameters);
		return (java.util.function.Supplier<O>) site.getTarget().bindTo(targetObject).invoke();
	}

}
