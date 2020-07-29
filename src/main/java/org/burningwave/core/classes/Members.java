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
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.lang.reflect.Array;
import java.lang.reflect.Executable;
import java.lang.reflect.Member;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.burningwave.core.Component;

public class Members implements Component {
	
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
			criteria.getPredicateOrTruePredicateIfPredicateIsNull(),
			new LinkedHashSet<>()
		);
		Predicate<Collection<M>> resultPredicate = criteria.getResultPredicate();
		return resultPredicate == null?
				result :
				resultPredicate.test(result)?
					result :
					new LinkedHashSet<M>();
	}
	
	private <M extends Member> Collection<M> findAll(
		Class<?> initialClsFrom, 
		Class<?> clsFrom, 
		BiPredicate<Class<?>, Class<?>> clsPredicate, 
		BiFunction<Class<?>, Class<?>, M[]> memberSupplier, 
		Predicate<M> predicate,
		Collection<M> collection
	) {	
		Stream.of(
			memberSupplier.apply(initialClsFrom, clsFrom)
		).filter(
			predicate
		).collect(
			Collectors.toCollection(() -> collection)
		);
		return clsFrom.getSuperclass() == null || clsPredicate.test(initialClsFrom, clsFrom) ?
			collection :
			findAll((Class<?>) initialClsFrom, clsFrom.getSuperclass(), clsPredicate, memberSupplier, predicate, collection);
	}
	
	public <M extends Member> boolean match(MemberCriteria<M, ?, ?> criteria, Object objectOrClass) {
		return match(criteria, Classes.retrieveFrom(objectOrClass));
	}
	
	public <M extends Member> boolean match(MemberCriteria<M, ?, ?> criteria, Class<?> classFrom) {
		return findFirst(criteria, classFrom) != null;
	}	
	
	public <M extends Member> M findFirst(MemberCriteria<M, ?, ?> criteria, Class<?> classFrom) {
		Predicate<Collection<M>> resultPredicate = criteria.getResultPredicate();
		if (resultPredicate == null) {
			return findFirst(
				classFrom,
				classFrom,
				criteria.getScanUpToPredicate(), 
				criteria.getMembersSupplier(),
				criteria.getPredicateOrTruePredicateIfPredicateIsNull()
			);
		} else {
			return findAll(
				classFrom,
				classFrom,
				criteria.getScanUpToPredicate(), 
				criteria.getMembersSupplier(),
				criteria.getPredicateOrTruePredicateIfPredicateIsNull(),
				new LinkedHashSet<>()
			).stream().findFirst().orElseGet(() -> null);
		}
	}
	
	private <M extends Member> M findFirst(
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
		return member != null? member :
			(clsPredicate.test(initialClsFrom, clsFrom) || clsFrom.getSuperclass() == null) ?
				null :
				findFirst(initialClsFrom, clsFrom.getSuperclass(), clsPredicate, memberSupplier, predicate);
	}
	
	static abstract class Handler<M extends Member> extends Members {	
		
		@SuppressWarnings("unchecked")
		<C extends MemberCriteria<M, C, ?>> Collection<M> findAllAndApply(C criteria, Class<?> targetClass, Consumer<M>... consumers) {
			Collection<M> members = findAll(criteria, targetClass);
			Optional.ofNullable(consumers).ifPresent(cnsms -> 
				members.stream().forEach(member -> 
					Stream.of(cnsms).filter(consumer -> 
						consumer != null
					).forEach(consumer -> {
							consumer.accept(member);
						}
					)
				)
			);
			return members;
		}
		
		@SuppressWarnings("unchecked")
		<C extends MemberCriteria<M, C, ?>> M findOneAndApply(C criteria, Class<?> targetClass, Consumer<M>... consumers) {
			M member = findOne(criteria, targetClass);
			Optional.ofNullable(consumers).ifPresent(cnsms -> 
				Optional.ofNullable(member).ifPresent(mmb -> 
					Stream.of(cnsms).filter(consumer -> 
						consumer != null
					).forEach(consumer ->{
							consumer.accept(mmb);
						}
					)
				)
			);
			return member;
		}
		
		String getCacheKey(Class<?> targetClass, String groupName, Class<?>... arguments) {
			if (arguments == null) {
				arguments = new Class<?>[] {null};
			}
			String argumentsKey = "";
			if (arguments != null && arguments.length > 0) {
				StringBuffer argumentsKeyStringBuffer = new StringBuffer();
				Stream.of(arguments).forEach(cls ->
					argumentsKeyStringBuffer.append("/" + Optional.ofNullable(cls).map(Class::getName).orElseGet(() ->"null"))
				);
				argumentsKey = argumentsKeyStringBuffer.toString();
			}
			String cacheKey = "/" + targetClass.getName() + "@" + targetClass.hashCode() +
				"/" + groupName +
				argumentsKey;
			return cacheKey;		
		}
		
		static abstract class OfExecutable<E extends Executable> extends Members.Handler<E> {
			
			List<Object> getArgumentList(E member, Object... arguments) {
				Parameter[] parameters = member.getParameters();
				List<Object> argumentList = new ArrayList<>();
				if (arguments != null) {
					if (parameters.length == 1 && parameters[0].isVarArgs()) {
						Object array = Array.newInstance(parameters[0].getType().getComponentType(), arguments.length);
						for (int i=0; i< arguments.length; i++) {
							Array.set(array, i, arguments[i]);
						}
						argumentList.add(array);
					} else {
						for (Object arg : arguments) {
							argumentList.add(arg);
						}
						if (parameters.length > 0 && parameters[parameters.length - 1].isVarArgs() && arguments.length < parameters.length) {
							argumentList.add(null);
						}
					}
				} else {
					argumentList.add(null);
				}
				return argumentList;
			}
			
			Object[] getArgumentArray(E member, Object... arguments) {
				List<Object> argumentList = getArgumentList(member, arguments);
				return argumentList.toArray(new Object[argumentList.size()]);
			}
			
			Class<?>[] retrieveParameterTypes(Executable member, List<Class<?>> argumentsClassesAsList) {
				Parameter[] memberParameter = member.getParameters();
				Class<?>[] memberParameterTypes = member.getParameterTypes();
				if (memberParameter.length > 0 && memberParameter[memberParameter.length - 1].isVarArgs()) {
					Class<?> varArgsType = 
						argumentsClassesAsList.size() > 0 && 
						argumentsClassesAsList.get(argumentsClassesAsList.size()-1) != null &&
						argumentsClassesAsList.get(argumentsClassesAsList.size()-1).isArray()?
						memberParameter[memberParameter.length - 1].getType():
						memberParameter[memberParameter.length - 1].getType().getComponentType();
					if (memberParameter.length == 1) {
						memberParameterTypes = new Class<?>[argumentsClassesAsList.size()];
						for (int j = 0; j < memberParameterTypes.length; j++) {
							memberParameterTypes[j] = varArgsType;
						}
					} else if (memberParameter.length - 1 <= argumentsClassesAsList.size()) {
						memberParameterTypes = new Class<?>[argumentsClassesAsList.size()];
						for (int j = 0; j < memberParameterTypes.length; j++) {
							if (j < (memberParameter.length - 1)) {
								memberParameterTypes[j] = memberParameter[j].getType();
							} else {
								memberParameterTypes[j] = varArgsType;
							}
						}
					}
				}
				return memberParameterTypes;
			}
			

			Collection<E> searchForExactMatch(Collection<E> members, Class<?>... arguments) {
				Collection<E> membersThatMatch = new LinkedHashSet<>();
				for (E executable : members) {
					List<Class<?>> argumentsClassesAsList = Arrays.asList(arguments);
					Class<?>[] parameterTypes = retrieveParameterTypes(executable, argumentsClassesAsList);
					boolean exactMatch = true;
					for (int i = 0; i < parameterTypes.length; i++) {
						if (argumentsClassesAsList.get(i) != null && 
							!Classes.getClassOrWrapper(argumentsClassesAsList.get(i)).equals(Classes.getClassOrWrapper(parameterTypes[i]))
						) {
							exactMatch = false;
						}
					}
					if (exactMatch) {
						membersThatMatch.add(executable);
					}
				}
				return membersThatMatch;
			}
		}
	}

}
