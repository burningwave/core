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
import static org.burningwave.core.assembler.StaticComponentContainer.Members;
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.lang.reflect.Array;
import java.lang.reflect.Executable;
import java.lang.reflect.Member;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.burningwave.core.Component;
import org.burningwave.core.function.TriFunction;

@SuppressWarnings("unchecked")
public class Members implements Component {
	
	public static Members create() {
		return new Members();
	}
	
	public <M extends Member> M findOne(MemberCriteria<M, ?, ?> criteria, Class<?> classFrom) {
		Collection<M> members = findAll(criteria, classFrom);
		if (members.size() > 1) {
			throw Throwables.toRuntimeException("More than one member found for class " + classFrom.getName());
		}
		return members.stream().findFirst().orElse(null);
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
	
	static abstract class Handler<M extends Member, C extends MemberCriteria<M, C, ?>> {	

		public M findOne(C criteria, Class<?> classFrom) {
			return Members.findOne(criteria, classFrom);
		}

		public Collection<M> findAll(C criteria, Class<?> classFrom) {
			return Members.findAll(criteria, classFrom);
		}

		public boolean match(C criteria, Class<?> classFrom) {
			return Members.match(criteria, classFrom);
		}

		public M findFirst(C criteria, Class<?> classFrom) {
			return Members.findFirst(criteria, classFrom);
		}

		Collection<M> findAllAndApply(C criteria, Class<?> targetClass, Consumer<M>... consumers) {
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
	
		M findOneAndApply(C criteria, Class<?> targetClass, Consumer<M>... consumers) {
			M member = findOne(criteria, targetClass);
			Optional.ofNullable(consumers).ifPresent(cnsms -> 
				Optional.ofNullable(member).ifPresent(mmb -> 
					Stream.of(cnsms).filter(consumer -> 
						consumer != null
					).forEach(consumer -> {
						consumer.accept(mmb);
					})
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
		
		static abstract class OfExecutable<E extends Executable, C extends ExecutableMemberCriteria<E, C, ?>> extends Members.Handler<E, C> {
			private Collection<String> classNamesToIgnoreToDetectTheCallingMethod;
			
			public OfExecutable() {
				classNamesToIgnoreToDetectTheCallingMethod = new HashSet<>();
				Class<?> cls = this.getClass();
				while (!cls.getName().equals(OfExecutable.class.getSuperclass().getName())) {
					classNamesToIgnoreToDetectTheCallingMethod.add(cls.getName());
					cls = cls.getSuperclass();
				}
			}
			
			Object[] getArgumentArray(
				E member,
				TriFunction<E, Supplier<List<Object>>, Object[], List<Object>> argumentListSupplier,
				Supplier<List<Object>> listSupplier,
				Object... arguments
			) {
				List<Object> argumentList = argumentListSupplier.apply(member, listSupplier, arguments);
				return argumentList.toArray(new Object[argumentList.size()]);
			}
			
			List<Object> getFlatArgumentList(E member, Supplier<List<Object>> argumentListSupplier, Object... arguments) {
				Parameter[] parameters = member.getParameters();
				List<Object> argumentList = argumentListSupplier.get();
				if (arguments != null) {
					if (parameters.length > 0 && parameters[parameters.length - 1].isVarArgs()) {
						for (int i = 0; i < arguments.length && i < parameters.length - 1; i++) {
							argumentList.add(arguments[i]);
						}
						if (arguments.length == parameters.length) {
							Parameter lastParameter = parameters[parameters.length -1];
							Object lastArgument = arguments[arguments.length -1];
							if (lastArgument != null && 
								lastArgument.getClass().isArray() && 
								lastArgument.getClass().equals(lastParameter.getType())) {
								for (int i = 0; i < Array.getLength(lastArgument); i++) {
									argumentList.add(Array.get(lastArgument, i));
								}
							} else {
								argumentList.add(lastArgument);
							}
						} else if (arguments.length > parameters.length) {
							for (int i = parameters.length - 1; i < arguments.length; i++) {
								argumentList.add(arguments[i]);
							}
						} else if (arguments.length < parameters.length) {
							argumentList.add(null);
						}						
					} else if (arguments.length > 0) {
						for (int i = 0; i < arguments.length; i++) {
							argumentList.add(arguments[i]);
						}
					}
				} else {
					argumentList.add(null);
				}
				return argumentList;
			}

			List<Object> getArgumentListWithArrayForVarArgs(E member, Supplier<List<Object>> argumentListSupplier, Object... arguments) {
				Parameter[] parameters = member.getParameters();
				List<Object> argumentList = argumentListSupplier.get();
				if (arguments != null) {
					if (parameters.length > 0 && parameters[parameters.length - 1].isVarArgs()) {
						for (int i = 0; i < arguments.length && i < parameters.length - 1; i++) {
							argumentList.add(arguments[i]);
						}
						Parameter lastParameter = parameters[parameters.length -1];
						if (arguments.length == parameters.length) {
							Object lastArgument = arguments[arguments.length -1];
							if (lastArgument != null && 
								lastArgument.getClass().isArray() && 
								lastArgument.getClass().equals(lastParameter.getType())) {
								argumentList.add(lastArgument);
							} else {
								Object array = Array.newInstance(lastParameter.getType().getComponentType(), 1);
								Array.set(array, 0, lastArgument);
								argumentList.add(array);
							}
						} else if (arguments.length > parameters.length) {
							Object array = Array.newInstance(lastParameter.getType().getComponentType(), arguments.length - (parameters.length - 1));
							for (int i = parameters.length - 1, j = 0; i < arguments.length; i++, j++) {
								Array.set(array, j, arguments[i]);
							}
							argumentList.add(array);
						} else if (arguments.length < parameters.length) {
							argumentList.add(Array.newInstance(lastParameter.getType().getComponentType(),0));
						}						
					} else if (arguments.length > 0) {
						for (int i = 0; i < arguments.length; i++) {
							argumentList.add(arguments[i]);
						}
					}
				} else {
					argumentList.add(null);
				}
				return argumentList;
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
			
			public StackTraceElement getCaller() {
				return getCaller(Thread.currentThread().getStackTrace(), 1);
			}
			
			public StackTraceElement getCaller(StackTraceElement[] stackTrace) {
				return getCaller(stackTrace, 1);
			}
			public StackTraceElement getCaller(StackTraceElement[] stackTrace, int level) {
				return getCaller(stackTrace, (clientMethodSTE, currentIteratedSTE) -> !clientMethodSTE.getClassName().equals(currentIteratedSTE.getClassName()), level);
			}
			
			public StackTraceElement getCaller(BiPredicate<StackTraceElement, StackTraceElement> filter, int level) {
				return getCaller(Thread.currentThread().getStackTrace(), filter, 1);
			}
			
			public StackTraceElement getCaller(StackTraceElement[] stackTrace, BiPredicate<StackTraceElement, StackTraceElement> filter, int level) {
				return getCallers(stackTrace, filter, level).get(level);
			}
			
			public List<StackTraceElement> getCallers() {
				return getCallers(Thread.currentThread().getStackTrace(), 1);
			}
			
			public List<StackTraceElement> getCallers(StackTraceElement[] stackTrace, int level) {
				return getCallers(stackTrace, (clientMethodSTE, currentIteratedSTE) -> !clientMethodSTE.getClassName().equals(currentIteratedSTE.getClassName()), level);
			}
			
			public List<StackTraceElement> getCallers(StackTraceElement[] stackTrace, BiPredicate<StackTraceElement, StackTraceElement> filter, int level) {
				StackTraceElement clientMethodSTE = null;
				StackTraceElement clientMethodCallerSTE = null;
				List<StackTraceElement> clientMethodCallersSTE = new ArrayList<>();
				int reachedLevel = 0;
				for (int i = 1; i < stackTrace.length; i ++) {
					if (clientMethodSTE == null && !classNamesToIgnoreToDetectTheCallingMethod.contains(stackTrace[i].getClassName())) {
						clientMethodSTE = stackTrace[i];
						continue;
					}
					if (clientMethodSTE != null && filter.test(clientMethodSTE, stackTrace[i])) {
						clientMethodCallerSTE = stackTrace[i];
					}
					if (clientMethodCallerSTE != null) {
						clientMethodCallersSTE.add(stackTrace[i]);
						if (level > 0 && ++reachedLevel == level) {
							break;
						}
					}
				}		
				return clientMethodCallersSTE;
			}	
		}
	}
	
}
