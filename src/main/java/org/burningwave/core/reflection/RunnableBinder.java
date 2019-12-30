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

public class RunnableBinder extends Binder.Abst {
	
	private RunnableBinder(MemberFinder memberFinder) {
		super(memberFinder);
	}
	
	public static RunnableBinder create(MemberFinder memberFinder) {
		return new RunnableBinder(memberFinder);
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public <F> F bindTo(Object targetObject, String methodName, Class<?>... inputAndOutputTypes) throws Throwable {
		return (F)bindTo(
			targetObject, memberFinder.findOne(
				MethodCriteria.forName(
					methodName::equals
				).and().returnType(
					void.class::equals
				).and().parameterTypes(
					(parameterTypes) -> parameterTypes.length == 0
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

	private Runnable dynamicBindTo(Object targetObject, Method method) throws Throwable {
		MethodHandles.Lookup caller = MethodHandles.lookup();
		MethodType methodParameters = MethodType.methodType(method.getReturnType());
		MethodHandle targetClass = caller.unreflect(method);
		CallSite site = LambdaMetafactory.metafactory(
			caller,
		    "run", // include types of the values to bind:
		    MethodType.methodType(Runnable.class, targetObject.getClass()),
		    methodParameters.erase(), 
		    targetClass, 
		    methodParameters);
		return (Runnable) site.getTarget().bindTo(targetObject).invoke();
	}

	private Runnable staticBindTo(Method method) throws Throwable {
		MethodHandles.Lookup caller = MethodHandles.lookup();
		MethodType methodParameters = MethodType.methodType(method.getReturnType());
		MethodHandle targetClass = caller.unreflect(method);
		CallSite site = LambdaMetafactory.metafactory(
			caller,
		    "run", // include types of the values to bind:
		    MethodType.methodType(Runnable.class),
		    methodParameters.erase(), 
		    targetClass, 
		    methodParameters);
		return (Runnable) site.getTarget().invokeExact();
	}
}
