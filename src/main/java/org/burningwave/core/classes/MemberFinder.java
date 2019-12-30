package org.burningwave.core.classes;

import java.lang.reflect.Member;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.burningwave.Throwables;
import org.burningwave.core.Component;
import org.burningwave.core.common.Classes;

public class MemberFinder implements Component {
	
	private MemberFinder() {}
	
	public static MemberFinder create() {
		return new MemberFinder();
	}
	
	public <M extends Member> M findOne(MemberCriteria<M, ?, ?> criteria, Object objectOrClass) {
		return findOne(criteria, Classes.retrieveFrom(objectOrClass));
	}
	
	public <M extends Member> M findOne(MemberCriteria<M, ?, ?> criteria, Class<?> classFrom) {
		Collection<M> members = findAll(criteria, classFrom);
		if (members.size() > 1) {
			throw Throwables.toRuntimeException("More than one member found for class " + classFrom.getName());
		}
		return members.stream().findFirst().orElse(null);
	}
	
	public <M extends Member> Collection<M> findAll(MemberCriteria<M, ?, ?> criteria, Object objectOrClass) {
		return findAll(criteria, Classes.retrieveFrom(objectOrClass));
	}
	
	public <M extends Member> Collection<M> findAll(MemberCriteria<M, ?, ?> criteria, Class<?> classFrom) {
		Collection<M> result = findAll(
			classFrom,
			classFrom,
			criteria.getScanUpToPredicate(), 
			criteria.getMembersSupplier(),
			criteria.getPredicateOrTruePredicateIfNull()
		);
		Predicate<Collection<M>> resultPredicate = criteria.getResultPredicate();
		return resultPredicate == null?
				result :
				resultPredicate.test(result)?
					result :
					new LinkedHashSet<M>();
	}
	
	@SuppressWarnings("unchecked")
	private <M extends Member> Collection<M> findAll(
		Class<?> initialClsFrom, 
		Class<?> clsFrom, 
		BiPredicate<Class<?>, Class<?>> clsPredicate, 
		BiFunction<Class<?>, Class<?>, M[]> memberSupplier, 
		Predicate<M> predicate
	) {
		return Stream.of(
			Optional.ofNullable(
				memberSupplier.apply(initialClsFrom, clsFrom)
			).orElseGet(() -> (M[])new Member[0])
		).filter(
			predicate
		).collect(
			(Collector<M, ?, Collection<M>>)(clsPredicate.test(initialClsFrom, clsFrom) || clsFrom.getSuperclass() == null ? 
				Collectors.toCollection(LinkedHashSet::new):
				Collectors.toCollection(() -> 
					findAll((Class<?>) initialClsFrom, clsFrom.getSuperclass(), clsPredicate, memberSupplier, predicate)
				)
			)
		);
	}
	
	public <M extends Member> boolean match(MemberCriteria<M, ?, ?> criteria, Object objectOrClass) {
		return match(criteria, Classes.retrieveFrom(objectOrClass));
	}
	
	public <M extends Member> boolean match(MemberCriteria<M, ?, ?> criteria, Class<?> classFrom) {
		Predicate<Collection<M>> resultPredicate = criteria.getResultPredicate();
		return resultPredicate == null?
			 match(
				classFrom,
				classFrom,
				criteria.getScanUpToPredicate(), 
				criteria.getMembersSupplier(),
				criteria.getPredicateOrTruePredicateIfNull()
			) :
			resultPredicate.test(
				findAll(
					classFrom,
					classFrom,
					criteria.getScanUpToPredicate(), 
					criteria.getMembersSupplier(),
					criteria.getPredicateOrTruePredicateIfNull()
				)
			);
	}	
	
	private <M extends Member> boolean match(
			Class<?> initialClsFrom,
			Class<?> clsFrom,			
			BiPredicate<Class<?>, Class<?>> clsPredicate,
			BiFunction<Class<?>, Class<?>, M[]> 
			memberSupplier, Predicate<M> predicate) {
		M member = Stream.of(
			memberSupplier.apply(initialClsFrom, clsFrom)
		).filter(
			predicate
		).findFirst().orElse(null);
		return member != null? true :
			(clsPredicate.test(initialClsFrom, clsFrom) || clsFrom.getSuperclass() == null) ?
				false :
				match(initialClsFrom, clsFrom.getSuperclass(), clsPredicate, memberSupplier, predicate);
	}
	
}
