package org.burningwave.core.classes;


import java.lang.reflect.Constructor;
import java.util.Optional;

import org.burningwave.Throwables;
import org.burningwave.core.common.Classes;
import org.burningwave.core.function.ThrowingSupplier;


public class ConstructorHelper extends MemberHelper<Constructor<?>>  {

	private ConstructorHelper(MemberFinder memberFinder) {
		super(memberFinder);
	}
	
	public static ConstructorHelper create(MemberFinder memberFinder) {
		return new ConstructorHelper(memberFinder);
	}
	
	
	@SuppressWarnings("unchecked")
	public <T> T newInstanceOf(
			Object object,
			Object... arguments) {
		return ThrowingSupplier.get(() -> 
			(T)findOneAndMakeItAccessible(object, arguments).newInstance(arguments)
		);
	}

	@SuppressWarnings("unchecked")
	public Constructor<?> findOneAndMakeItAccessible(Object object, Object... arguments) {
		ConstructorCriteria criteria = ConstructorCriteria.byScanUpTo(object).parameterTypesAreAssignableFrom(
			arguments
		);
		Constructor<?> member = findOneAndApply(
			criteria, object, (mmb) ->	mmb.setAccessible(true)
		);
		Optional.ofNullable(member).orElseThrow(() ->
			Throwables.toRuntimeException("Constructor not found for class " + Classes.retrieveFrom(object))
		);
		return member;
	}

}
