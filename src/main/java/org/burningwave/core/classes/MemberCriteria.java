package org.burningwave.core.classes;


import java.lang.reflect.Member;
import java.util.Collection;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

import org.burningwave.core.Criteria;
import org.burningwave.core.function.TriPredicate;


public abstract class MemberCriteria<M extends Member, C extends MemberCriteria<M, C, T>, T extends Criteria.TestContext<M, C>> extends CriteriaWithClassElementsSupplyingSupport<M, C, T> {
	TriPredicate<C, Class<?>, Class<?>> scanUpToPredicate;	
	TriPredicate<C, Class<?>, Class<?>> skipClassPredicate;
	Predicate<Collection<M>> resultPredicate;	
		
	@SuppressWarnings("unchecked")
	public BiPredicate<Class<?>, Class<?>> getScanUpToPredicate() {
		return scanUpToPredicate != null?
			(initialClassFrom, currentClass) -> this.scanUpToPredicate.test((C)this, initialClassFrom, currentClass):
			(initialClassFrom, currentClass) -> currentClass.getName().equals(Object.class.getName()
		);
	}	
	
	@SuppressWarnings("unchecked")
	public C scanUpTo(Predicate<Class<?>> predicate) {
		this.scanUpToPredicate = (criteria, initialClassFrom, currentClass) -> predicate.test(currentClass);
		return (C)this;
	}	
	
	@SuppressWarnings("unchecked")
	public C scanUpTo(BiPredicate<Class<?>, Class<?>> predicate) {
		this.scanUpToPredicate = (criteria, initialClassFrom, currentClass) -> predicate.test(initialClassFrom, currentClass);
		return (C)this;
	}	
	
	@SuppressWarnings("unchecked")
	public C scanUpTo(TriPredicate<Map<Class<?>, Class<?>>, Class<?>, Class<?>> predicate) {
		this.scanUpToPredicate = (criteria, initialClassFrom, currentClass) -> predicate.test(criteria.getUploadedClasses(), initialClassFrom, currentClass);
		return (C)this;
	}	
	
	@SuppressWarnings("unchecked")
	public C skip(TriPredicate<Map<Class<?>, Class<?>>, Class<?>, Class<?>> predicate) {
		if (skipClassPredicate != null) {
			skipClassPredicate = skipClassPredicate.or((criteria, initialClassFrom, currentClass) -> 
				predicate.test(criteria.getUploadedClasses(), initialClassFrom, currentClass)
			);
		} else {
			skipClassPredicate = (criteria, initialClassFrom, currentClass) -> predicate.test(criteria.getUploadedClasses(), initialClassFrom, currentClass);
		}	
		return (C)this;
	}	
	
	@SuppressWarnings("unchecked")
	public C skip(BiPredicate<Class<?>, Class<?>> predicate) {
		if (skipClassPredicate != null) {
			skipClassPredicate = skipClassPredicate.or((criteria, initialClassFrom, currentClass) ->
				predicate.test(initialClassFrom, currentClass)
			);
		} else {
			skipClassPredicate = (criteria, initialClassFrom, currentClass) -> 
				predicate.test(initialClassFrom, currentClass);
		}
		return (C)this;
	}
	
	@SuppressWarnings("unchecked")
	public C result(Predicate<Collection<M>> resultPredicate) {
		this.resultPredicate = resultPredicate;
		return (C)this;
	}
	
	protected Predicate<Collection<M>> getResultPredicate() {
		return this.resultPredicate;
	}
	
	@Override
	protected C logicOperation(C leftCriteria, C rightCriteria,
			Function<BiPredicate<T, M>, Function<BiPredicate<? super T, ? super M>, BiPredicate<T, M>>> binaryOperator,
			C targetCriteria) {
		C newCriteria = super.logicOperation(leftCriteria, rightCriteria, binaryOperator, targetCriteria);
		newCriteria.scanUpToPredicate =
			leftCriteria.scanUpToPredicate != null?
				rightCriteria.scanUpToPredicate != null?
					leftCriteria.scanUpToPredicate.or(rightCriteria.scanUpToPredicate) :
					null :
				null;
		newCriteria.skipClassPredicate =
			leftCriteria.skipClassPredicate != null?
				rightCriteria.skipClassPredicate != null?
					leftCriteria.skipClassPredicate.or(rightCriteria.skipClassPredicate) :
					leftCriteria.skipClassPredicate :
				rightCriteria.skipClassPredicate;
//		newCriteria.resultPredicate =
//			leftCriteria.resultPredicate != null?
//				rightCriteria.resultPredicate != null?
//					leftCriteria.resultPredicate.or(rightCriteria.resultPredicate) :
//					leftCriteria.resultPredicate :
//				rightCriteria.resultPredicate;
		return newCriteria;
	}

	
	@SuppressWarnings("unchecked")
	public C member(final Predicate<M> predicate) {
		this.predicate = concat(
			this.predicate,
			(criteria, member) -> predicate.test(member)
		);
		return (C)this;
	}	
	
	@SuppressWarnings("unchecked")
	public C name(final Predicate<String> predicate) {
		this.predicate = concat(
			this.predicate,
			(context, member) -> predicate.test(member.getName())
		);
		return (C)this;
	}	
	
	@Override
	public C createCopy() {
		C copy = super.createCopy();
		copy.scanUpToPredicate = this.scanUpToPredicate;
		copy.skipClassPredicate = this.skipClassPredicate;
		copy.resultPredicate = this.resultPredicate;
		return copy;
	}
	
	public abstract Function<Class<?>, M[]> getMembersSupplierFunction();
	
	@SuppressWarnings("unchecked")
	public BiFunction<Class<?>, Class<?>, M[]> getMembersSupplier() {
		return (initialClassFrom, currentClass) -> 
			!(skipClassPredicate != null && skipClassPredicate.test((C)this, initialClassFrom, currentClass)) ?
				getMembersSupplierFunction().apply(currentClass) : 
				null;
	}
}
