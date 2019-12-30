package org.burningwave.core.reflection;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.burningwave.core.Component;
import org.burningwave.core.Virtual;
import org.burningwave.core.classes.MemberFinder;
import org.burningwave.core.classes.MethodCriteria;

public class CallerRetriever implements Component {
	private MemberFinder memberFinder;
	private final String CALLER_RETRIEVER_METHOD_NAME;
	
	private CallerRetriever(MemberFinder memberFinder, String callerRetrieverMethodName) {
		this.memberFinder = memberFinder;
		this.CALLER_RETRIEVER_METHOD_NAME = callerRetrieverMethodName;
	}
	

	public static CallerRetriever create(MemberFinder memberFinder, String callerRetrieverMethodName) {
		return new CallerRetriever(memberFinder, callerRetrieverMethodName);
	}
	
	public MethodHandles.Lookup retrieve(Class<?> cls) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		MethodHandles.Lookup caller = null;
		if (!Virtual.class.isAssignableFrom(cls)) {
			caller = MethodHandles.lookup();
		} else {
			Method mth = memberFinder.findOne(
				MethodCriteria.forName(
					(name) -> name.equals(CALLER_RETRIEVER_METHOD_NAME)
				),
				cls
			);
			
			caller = (MethodHandles.Lookup)mth.invoke(null);
		}
		return caller;
	}
}
