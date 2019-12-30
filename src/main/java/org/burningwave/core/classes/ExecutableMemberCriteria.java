package org.burningwave.core.classes;

import java.lang.reflect.Executable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import org.burningwave.core.Criteria;
import org.burningwave.core.common.Classes;
import org.burningwave.core.function.TriPredicate;

public abstract class ExecutableMemberCriteria<
	E extends Executable, 
	C extends ExecutableMemberCriteria<E, C, T>, 
	T extends Criteria.TestContext<E, C>
> extends MemberCriteria<E, C, T> {
	

	@SuppressWarnings("unchecked")
	public C parameterTypes(final Predicate<Class<?>[]> predicate) {
		this.predicate = concat(
			this.predicate,
			(context, member) -> predicate.test(member.getParameterTypes())
		);
		return (C)this;
	}
	
	@SuppressWarnings("unchecked")
	public C parameterTypesAreAssignableFrom(Object... arguments) {
		Class<?>[] argumentsClasses = Classes.retrieveFrom(arguments);
		if (argumentsClasses != null && argumentsClasses.length > 0) {
			List<Class<?>> argumentsClassesAsList = Arrays.asList(argumentsClasses);
			for (int i = 0; i < argumentsClasses.length; i++) {
				final int index = i;
				this.predicate = concat(
					this.predicate,
					(context, member) -> {
						Class<?>[] memberParameters = member.getParameterTypes();
						if (argumentsClassesAsList.size() == memberParameters.length) {							
							TriPredicate<List<Class<?>>, Class<?>[], Integer> predicate = (argClasses, paramTypes, innerIdx) -> 
								(argClasses.get(innerIdx) == null || paramTypes[innerIdx].isAssignableFrom(argClasses.get(innerIdx)));
							if (this.classSupplier == null) {
								return predicate.test(argumentsClassesAsList, memberParameters, index);
							} else {
								return predicate.test(context.getCriteria().retrieveUploadedClasses(argumentsClasses), memberParameters, index);
							}
						} else {
							return false;
						}
					}
				);
				if (index < arguments.length - 1) {
					and();
				}
			}				
		} else {
			parameterTypes(
				context ->
				context.length == 0
			);
		}
		return (C)this;
	}
	
	@SuppressWarnings("unchecked")
	public C parameterType(final BiPredicate<Class<?>[], Integer> predicate) {
		this.predicate = concat(
			this.predicate,
			getPredicateWrapper(
				(criteria, member) -> member.getParameterTypes(), 				
				(criteria, array, index) -> predicate.test(array, index)
			)
		);
		return (C)this;
	}
	
	
	@SuppressWarnings("unchecked")
	public C parameterType(final TriPredicate<Map<Class<?>, Class<?>>, Class<?>[], Integer> predicate) {
		this.predicate = concat(
			this.predicate,
			getPredicateWrapper(
				(context, member) -> member.getParameterTypes(), 				
				(context, array, index) -> predicate.test(context.getCriteria().getUploadedClasses(), array, index)
			)
		);
		return (C)this;
	}
}
