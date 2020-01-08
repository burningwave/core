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

import java.lang.reflect.Method;

import org.burningwave.core.Component;

public class FunctionalInterfaceFactory implements Component {
	private RunnableBinder runnableBinder;
	private SupplierBinder supplierBinder;
	private ConsumerBinder consumerBinder;
	private FunctionBinder functionBinder;
	
	private FunctionalInterfaceFactory(RunnableBinder runnableBinder, SupplierBinder supplierBinder,
			ConsumerBinder consumerBinder, FunctionBinder functionBinder) {
		this.runnableBinder = runnableBinder;
		this.supplierBinder = supplierBinder;
		this.consumerBinder = consumerBinder;
		this.functionBinder = functionBinder;
	}

	public static FunctionalInterfaceFactory create(
			RunnableBinder runnableBinder, SupplierBinder supplierBinder,
			ConsumerBinder consumerBinder, FunctionBinder functionBinder) {
		return new FunctionalInterfaceFactory(runnableBinder, supplierBinder, consumerBinder, functionBinder);
	}

	@SuppressWarnings("unchecked")
	public <F> F create(Object targetObject, Method mth) throws Throwable {
		F function = null;
		if (mth.getParameterTypes().length == 0 && mth.getReturnType() == void.class) {
			function = (F)runnableBinder.bindTo(targetObject, mth);
		} else if (mth.getParameterTypes().length == 0 && mth.getReturnType() != void.class) {
			function = (F)supplierBinder.bindTo(targetObject, mth);
		} else if (mth.getParameterTypes().length > 0 && mth.getReturnType() == void.class) {
			function = (F)consumerBinder.bindTo(targetObject, mth);
		} else if (mth.getParameterTypes().length > 0 && mth.getReturnType() != void.class) {
			function = (F)functionBinder.bindTo(targetObject, mth);
		}
		return function;
	}
	
}
