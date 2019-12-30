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
