/*
 * This file is part of Burningwave Core.
 *
 * Author: Roberto Gentili
 *
 * Hosted at: https://github.com/burningwave/core
 *
 * --
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 Roberto Gentili
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without
 * limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
 * EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.burningwave.core.classes;

import static org.burningwave.core.assembler.StaticComponentContainer.Classes;

import java.lang.reflect.Member;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;
import org.burningwave.core.Component;

public class Members implements Component {

	private Members() {}
	
	public static Members create() {
		return new Members();
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
			memberSupplier.apply(initialClsFrom, clsFrom)
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
