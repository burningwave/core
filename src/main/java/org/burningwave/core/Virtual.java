package org.burningwave.core;


import org.burningwave.core.classes.MemberFinder;
import org.burningwave.core.classes.MethodHelper;


public interface Virtual extends Component {
	public final static MethodHelper methodHelper = MethodHelper.create(MemberFinder.create());

	
	default <T> T invoke(String methodName, Object... parameters) {
		return methodHelper.invoke(this, methodName, parameters);
	}
	
	default <T> T invokeWithoutCachingMethod(String methodName, Object... parameters) {
		return methodHelper.invoke(this, methodName, false, parameters);
	}

}