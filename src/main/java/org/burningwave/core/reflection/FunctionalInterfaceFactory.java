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

import java.lang.reflect.Method;

import org.burningwave.core.Cache;
import org.burningwave.core.Component;
import org.burningwave.core.function.ThrowingSupplier;

public class FunctionalInterfaceFactory implements Component {
	private RunnableBinder runnableBinder;
	private SupplierBinder supplierBinder;
	private ConsumerBinder consumerBinder;
	private FunctionBinder functionBinder;
	private PredicateBinder predicateBinder;
	
	private FunctionalInterfaceFactory(RunnableBinder runnableBinder, SupplierBinder supplierBinder,
			ConsumerBinder consumerBinder, FunctionBinder functionBinder, PredicateBinder predicateBinder) {
		this.runnableBinder = runnableBinder;
		this.supplierBinder = supplierBinder;
		this.consumerBinder = consumerBinder;
		this.functionBinder = functionBinder;
		this.predicateBinder = predicateBinder;
	}

	public static FunctionalInterfaceFactory create(
			RunnableBinder runnableBinder, SupplierBinder supplierBinder,
			ConsumerBinder consumerBinder, FunctionBinder functionBinder,
			PredicateBinder predicateBinder) {
		return new FunctionalInterfaceFactory(runnableBinder, supplierBinder, consumerBinder, functionBinder, predicateBinder);
	}

	@SuppressWarnings("unchecked")
	public <F> F create(Method mth) throws Throwable {
		
		if (mth.getParameterTypes().length == 0 && mth.getReturnType() == void.class) {
			return (F) Cache.BINDED_FUNCTIONAL_INTERFACES.getOrDefault(mth, () -> ThrowingSupplier.get(() -> runnableBinder.bindTo(mth)));
		} else if (mth.getParameterTypes().length == 0 && mth.getReturnType() != void.class) {
			return (F) Cache.BINDED_FUNCTIONAL_INTERFACES.getOrDefault(mth, () -> ThrowingSupplier.get(() -> supplierBinder.bindTo(mth)));
		} else if (mth.getParameterTypes().length > 0 && mth.getReturnType() == void.class) {
			return (F) Cache.BINDED_FUNCTIONAL_INTERFACES.getOrDefault(mth, () -> ThrowingSupplier.get(() -> consumerBinder.bindTo(mth)));
		} else if (mth.getParameterTypes().length > 0 && (mth.getReturnType() == boolean.class || mth.getReturnType() == Boolean.class)) {
			return (F) Cache.BINDED_FUNCTIONAL_INTERFACES.getOrDefault(mth, () -> ThrowingSupplier.get(() -> predicateBinder.bindTo(mth)));
		} else if (mth.getParameterTypes().length > 0 && mth.getReturnType() != void.class) {
			return (F) Cache.BINDED_FUNCTIONAL_INTERFACES.getOrDefault(mth, () -> ThrowingSupplier.get(() -> functionBinder.bindTo(mth)));
		}
		return null;
	}
	
}
