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

import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.function.Function;
import java.util.function.Supplier;

import org.burningwave.core.classes.MemberFinder;
import org.burningwave.core.classes.MethodCriteria;

public class SupplierBinder extends Binder.Abst {
	
	private SupplierBinder(MemberFinder memberFinder, ConsulterRetriever consulterRetriever) {
		super(memberFinder, consulterRetriever);
	}
	
	public static SupplierBinder create(MemberFinder memberFinder, ConsulterRetriever consulterRetriever) {
		return new SupplierBinder(memberFinder, consulterRetriever);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <O> O bindTo(Class<?> targetObjectClass, String methodName, Class<?>... outputType) throws Throwable {
		return (O)bindTo(
			memberFinder.findOne(
				MethodCriteria.forName(
					methodName::equals
				).and().returnType(
					(returnType) -> returnType == outputType[0]
				),
				targetObjectClass
			)
		);
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
		MethodHandles.Lookup caller = consulterRetriever.retrieve(targetMethod.getDeclaringClass());
		MethodType methodReturnType = MethodType.methodType(targetMethod.getReturnType());
		MethodHandle targetClass = 	caller.unreflect(targetMethod);
		return (F) LambdaMetafactory.metafactory(
			caller,
		    "get",
		    MethodType.methodType(Supplier.class),
		    methodReturnType.generic(), 
		    targetClass, 
		    methodReturnType).getTarget().invoke();
	}
	
	private <F> F dynamicBindTo(Method targetMethod) throws Throwable {
		Lookup consulter = consulterRetriever.retrieve(targetMethod.getDeclaringClass());
		MethodType methodParameters = MethodType.methodType(targetMethod.getReturnType(), targetMethod.getParameterTypes());
		MethodHandle methodHandle = consulter.findSpecial(targetMethod.getDeclaringClass(), targetMethod.getName(), methodParameters, targetMethod.getDeclaringClass());
		return (F)LambdaMetafactory.metafactory(
			consulter, "apply",
			MethodType.methodType(Function.class),
			methodHandle.type().generic(),
			methodHandle,
			methodHandle.type()
		).getTarget().invoke();
	}

}
