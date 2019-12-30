package org.burningwave.core.classes;


import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

import org.burningwave.core.Criteria;
import org.burningwave.core.function.TriPredicate;


public class ConstructorCriteria extends ExecutableMemberCriteria<
	Constructor<?>, ConstructorCriteria, Criteria.TestContext<Constructor<?>, ConstructorCriteria>
> {
	
	private ConstructorCriteria() {
		super();
	}
	
	public static ConstructorCriteria create() {
		return new ConstructorCriteria();
	}
	
	@Override
	public Function<Class<?>, Constructor<?>[]> getMembersSupplierFunction() {
		return (currentClass) -> currentClass.getDeclaredConstructors();
	}
	
	public static ConstructorCriteria forName(Predicate<String> predicate) {
		ConstructorCriteria criteria = ConstructorCriteria.create();
		criteria.predicate = (context, member) -> predicate.test(member.getName());
		return criteria;
	}
	
	
	public static ConstructorCriteria byScanUpTo(BiPredicate<Class<?>, Class<?>> predicate) {
		return ConstructorCriteria.create().scanUpTo(predicate);
	}
	
	public static ConstructorCriteria byScanUpTo(TriPredicate<Map<Class<?>, Class<?>>, Class<?>, Class<?>> predicate) {
		return ConstructorCriteria.create().scanUpTo(predicate);
	}

	public static ConstructorCriteria byScanUpTo(Object obj) {
		ConstructorCriteria criteria = new ConstructorCriteria();
		criteria.scanUpToPredicate = (crit, initialClassFrom, currentClass) -> crit.retrieveClass(currentClass).equals(crit.retrieveClass(initialClassFrom));
		return criteria;
	}
}