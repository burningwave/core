package org.burningwave.core.classes;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

import org.burningwave.core.Criteria;
import org.burningwave.core.function.TriPredicate;


public class FieldCriteria extends MemberCriteria<
	Field, FieldCriteria, Criteria.TestContext<Field, FieldCriteria>
> {
	
	private FieldCriteria() {
		super();
	}
	
	public static FieldCriteria create() {
		return new FieldCriteria();
	}
	
	@Override
	public Function<Class<?>, Field[]> getMembersSupplierFunction() {
		return (currentClass) -> currentClass.getDeclaredFields();
	}
	
	public static FieldCriteria forName(Predicate<String> predicate) {
		FieldCriteria criteria = FieldCriteria.create();
		criteria.predicate = (context, member) -> predicate.test(member.getName());
		return criteria;
	}
	
	public static FieldCriteria byScanUpTo(BiPredicate<Class<?>, Class<?>> predicate) {
		return FieldCriteria.create().scanUpTo(predicate);
	}
	
	public static FieldCriteria byScanUpTo(TriPredicate<Map<Class<?>, Class<?>>, Class<?>, Class<?>> predicate) {
		return FieldCriteria.create().scanUpTo(predicate);
	}
	
	public static FieldCriteria byScanUpTo(Object obj) {
		FieldCriteria criteria = FieldCriteria.create();
		criteria.scanUpToPredicate = (crit, initialClassFrom, currentClass) -> crit.retrieveClass(currentClass).equals(crit.retrieveClass(initialClassFrom));
		return criteria;
	}
	
	public FieldCriteria type(final Predicate<Class<?>> predicate) {
		this.predicate = concat(
			this.predicate,
			(context, member) -> predicate.test(member.getType())
		);
		return this;
	}
}