/*
 * This file is part of Burningwave Core.
 *
 * Author: Roberto Gentli
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

import java.lang.reflect.Member;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.burningwave.Throwables;
import org.burningwave.core.Component;
import org.burningwave.core.Criteria;
import org.burningwave.core.common.Streams;
import org.burningwave.core.function.PentaPredicate;

@SuppressWarnings("unchecked")
public class ClassCriteria extends CriteriaWithClassElementsSupplyingSupport<Class<?>, ClassCriteria, ClassCriteria.TestContext> implements Component {
	MemberFinder memberFinder;
	Map<String, MemberCriteria<?, ?, ?>> memberCriterias;
	PentaPredicate<ClassCriteria, TestContext, MemberCriteria<?, ?, ?>, String, Class<?>> membersPredicate;
	private boolean collectMembers;
	
	private ClassCriteria() {
		super();
		memberCriterias = new ConcurrentHashMap<>();
	}
	
	public static ClassCriteria create() {
		return new ClassCriteria();
	}
	
	public void init(ClassHelper classHelper, ClassLoader classSupplier, MemberFinder memberFinder) {
		this.classSupplier = cls -> {
			try {
				return classHelper.loadOrUploadClass(cls, classSupplier);
			} catch (ClassNotFoundException exc) {
				throw Throwables.toRuntimeException(exc);
			}
		};
		this.memberFinder = memberFinder;
		this.byteCodeSupplier = classHelper::getByteCode;
		for (MemberCriteria<?, ?, ?> memberCriteria : memberCriterias.values()) {
			memberCriteria.init(this.classSupplier, this.byteCodeSupplier);
			if (this.classesToBeUploaded != null) {
				memberCriteria.useClasses(this.classesToBeUploaded);
			}
		}
		if (!collectMembers) {
			membersPredicate = this::testMembers;
		} else {
			membersPredicate = this::testAndCollectMembers;
		}
	}

	@Override
	public ClassCriteria logicOperation(
		ClassCriteria leftCriteria, ClassCriteria rightCriteria,
		Function<BiPredicate<TestContext, Class<?>>, Function<BiPredicate<? super TestContext, ? super Class<?>>, BiPredicate<TestContext, Class<?>>>> binaryOperator,
		ClassCriteria targetCriteria
	) {
		leftCriteria.memberCriterias.entrySet().stream().collect(
			Collectors.toMap(
				Map.Entry::getKey, (entry) -> entry.getValue().createCopy(),
				(o1, o2) -> o1, () -> targetCriteria.memberCriterias
			)
		);
		rightCriteria.memberCriterias.entrySet().stream().collect(
			Collectors.toMap(
				Map.Entry::getKey, (entry) -> entry.getValue().createCopy(),
				(o1, o2) -> o1, () -> targetCriteria.memberCriterias
			)
		);
		targetCriteria.collectMembers = leftCriteria.collectMembers || rightCriteria.collectMembers;
		return super.logicOperation((ClassCriteria)leftCriteria, rightCriteria, binaryOperator, targetCriteria);
	}	

	
	public Class<?> retrieveClass(Class<?> cls) {
		if (classSupplier != null) {
			return classSupplier.apply(cls);
		}
		return cls;
	}	
	
	public ClassCriteria packageName(final Predicate<String> predicate) {
		this.predicate = concat(
			this.predicate,
			(testContext, cls) -> predicate.test(cls.getPackage().getName())
		);
		return this;
	}
	

	public ClassCriteria className(final Predicate<String> predicate) {
		this.predicate = concat(
			this.predicate,
			(testContext, cls) -> predicate.test(cls.getName())
		);
		return this;
	}

	
	public ClassCriteria byBytecode(Predicate<byte[]> predicate) {
		this.predicate = concat(
			this.predicate,
			(context, cls) -> {
				ClassCriteria criteria = context.getCriteria();
				return predicate.test(Streams.toByteArray(criteria.byteCodeSupplier.apply(cls)));
			}
		);
		return this;
	}
	
	public ClassCriteria byBytecode(BiPredicate<Map<Class<?>, byte[]>, byte[]> predicate) {
		this.predicate = concat(
			this.predicate,
			(context, cls) -> {
				ClassCriteria criteria = context.getCriteria();
				return predicate.test(
					criteria.getLoadedBytecode(), 
					Streams.toByteArray(criteria.byteCodeSupplier.apply(cls))
				);
			}
		);
		return this;
	}
	
	public ClassCriteria byClasses(BiPredicate<Map<Class<?>, Class<?>>, Class<?>> predicate) {
		this.predicate = concat(
			this.predicate,
			(context, cls) -> {
				return predicate.test(context.getCriteria().getUploadedClasses(), cls);
			}
		);
		return this;
	}
	
	public <M extends Member> ClassCriteria byMembers(MemberCriteria<?, ?, ?> memberCriteria) {
		final String key = UUID.randomUUID().toString();
		this.memberCriterias.put(key, memberCriteria);		
		this.predicate = concat(
			this.predicate,
			(context, cls) -> {
				ClassCriteria criteria = context.getCriteria();
				return criteria.membersPredicate.test(criteria, context, memberCriteria, key, cls);
			}
		);
		return this;
	}
	
	public ClassCriteria collectMembers(boolean collectMembers) {
		this.collectMembers =collectMembers;
		return this;
	}
	
	private boolean testMembers(
		ClassCriteria criteria,
		TestContext context,
		MemberCriteria<?, ?, ?> memberCriteria,
		String key, 
		Class<?> cls
	) {
		return criteria.memberFinder.match(criteria.memberCriterias.get(key), cls);
	}
	
	private boolean testAndCollectMembers( 
		ClassCriteria criteria,
		TestContext context,
		MemberCriteria<?, ?, ?> memberCriteria,
		String key, 
		Class<?> cls
	) {
		Collection<Member> members = (Collection<Member>)criteria.memberFinder.findAll(criteria.memberCriterias.get(key), cls);
		context.addMembersFound(memberCriteria, members);
		return !members.isEmpty();
	}
	
	public ClassCriteria createCopy() {
		ClassCriteria copy = super.createCopy();
		this.memberCriterias.entrySet().stream().collect(
			Collectors.toMap(
				Map.Entry::getKey, (entry) -> entry.getValue().createCopy(),
				(o1, o2) -> o1, () -> copy.memberCriterias
			)
		);
		copy.collectMembers = this.collectMembers;
		return copy;
	}
	
	
	@Override
	public TestContext createTestContext() {
		return TestContext.create(this);
	}
	
	
	@Override
	public void close() {
		memberFinder = null;
		super.close();
	}


	public static class TestContext extends Criteria.TestContext<Class<?>, ClassCriteria> {
		private enum Elements {
			MEMBERS_FOUND
		}
		
		protected TestContext(ClassCriteria criteria) {
			super(criteria);
			if (criteria.collectMembers) {
				put(Elements.MEMBERS_FOUND, new ConcurrentHashMap<MemberCriteria<?, ?, ?>, Collection<Member>>());
			}
		}
		
		public static TestContext create(ClassCriteria criteria) {
			return new TestContext(criteria);
		}
		
		public Map<MemberCriteria<?, ?, ?>, Collection<Member>> getMembersFound() {
			return get(Elements.MEMBERS_FOUND);
		}
		
		void addMembersFound(MemberCriteria<?, ?, ?> criteria, Collection<Member> members) {
			getMembersFound().put(criteria, members);
		}
	}
}
