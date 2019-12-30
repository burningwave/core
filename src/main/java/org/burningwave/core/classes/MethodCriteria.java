package org.burningwave.core.classes;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

import org.burningwave.core.Criteria;
import org.burningwave.core.function.TriPredicate;

public class MethodCriteria extends ExecutableMemberCriteria<
	Method, MethodCriteria, Criteria.TestContext<Method, MethodCriteria>
> {
	
	private MethodCriteria() {
		super();
	}
	
	public static MethodCriteria create() {
		return new MethodCriteria();
	}
	
	@Override
	public Function<Class<?>, Method[]> getMembersSupplierFunction() {
		return (currentClass) -> currentClass.getDeclaredMethods();
	}
	
	public static MethodCriteria forName(Predicate<String> predicate) {
		MethodCriteria criteria = MethodCriteria.create();
		criteria.predicate = (context, member) -> predicate.test(member.getName());
		return criteria;
	}
	
	public static MethodCriteria byScanUpTo(TriPredicate<Map<Class<?>, Class<?>>, Class<?>, Class<?>> predicate) {
		return MethodCriteria.create().scanUpTo(predicate);
	}
	
	public static MethodCriteria byScanUpTo(BiPredicate<Class<?>, Class<?>> predicate) {
		return MethodCriteria.create().scanUpTo(predicate);
	}
	
	public static MethodCriteria byScanUpTo(Predicate<Class<?>> predicate) {
		return MethodCriteria.create().scanUpTo(predicate);
	}
	
	public static MethodCriteria on(Object obj) {
		MethodCriteria criteria = MethodCriteria.create();
		criteria.scanUpToPredicate = (crit, initialClassFrom, currentClass) -> crit.retrieveClass(currentClass).equals(crit.retrieveClass(initialClassFrom));
		return criteria;
	}

	public MethodCriteria returnType(final Predicate<Class<?>> predicate) {
		this.predicate = concat(
			this.predicate,
			(context, member) -> predicate.test(member.getReturnType())
		);
		return this;
	}
}