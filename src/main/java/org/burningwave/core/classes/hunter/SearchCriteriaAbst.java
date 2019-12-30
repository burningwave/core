package org.burningwave.core.classes.hunter;

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
import org.burningwave.core.classes.ClassHelper;
import org.burningwave.core.classes.CriteriaWithClassElementsSupplyingSupport;
import org.burningwave.core.classes.MemberCriteria;
import org.burningwave.core.classes.MemberFinder;
import org.burningwave.core.common.Streams;
import org.burningwave.core.function.PentaPredicate;

@SuppressWarnings("unchecked")
abstract class SearchCriteriaAbst<S extends SearchCriteriaAbst<S>> extends CriteriaWithClassElementsSupplyingSupport<Class<?>, S, SearchCriteriaAbst.TestContext<S>> implements Component {
	MemberFinder memberFinder;
	ClassLoader parentClassLoaderForMainClassLoader;
	Map<String, MemberCriteria<?, ?, ?>> memberCriterias;
	PentaPredicate<S, TestContext<S>, MemberCriteria<?, ?, ?>, String, Class<?>> membersPredicate;
	boolean useSharedClassLoaderAsMain;
	boolean deleteFoundItemsOnClose;
	boolean collectMembers;
	boolean useSharedClassLoaderAsParent;
	boolean considerURLClassLoaderPathsAsScanned;
	boolean waitForSearchEnding;
	

	SearchCriteriaAbst() {
		byteCodeForClasses = new ConcurrentHashMap<>();
		memberCriterias = new ConcurrentHashMap<>();
		useSharedClassLoaderAsMain(true);
		deleteFoundItemsOnClose = true;
		waitForSearchEnding = true;
	}
	
	void init(ClassHelper classHelper, PathMemoryClassLoader classSupplier, MemberFinder memberFinder) {
		super.init(t -> {
				try {
					return classSupplier.loadOrUploadClass(t);
				} catch (ClassNotFoundException exc) {
					throw Throwables.toRuntimeException(exc);
				}
			},
			classHelper::getByteCode
		);
		this.memberFinder = memberFinder;
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
	public S logicOperation(
		S leftCriteria, S rightCriteria,
		Function<BiPredicate<TestContext<S>, Class<?>>, Function<BiPredicate<? super TestContext<S>, ? super Class<?>>, BiPredicate<TestContext<S>, Class<?>>>> binaryOperator,
		S targetCriteria
	) {
		if (leftCriteria.classesToBeUploaded != null) {
			targetCriteria.useClasses(leftCriteria.classesToBeUploaded);
		}
		if (rightCriteria.classesToBeUploaded != null) {
			targetCriteria.useClasses(rightCriteria.classesToBeUploaded);
		}
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
		
		if (leftCriteria.useSharedClassLoaderAsMain || rightCriteria.useSharedClassLoaderAsMain) {
			targetCriteria.useSharedClassLoaderAsMain(true);
		} else if (leftCriteria.useSharedClassLoaderAsParent || rightCriteria.useSharedClassLoaderAsParent) {
			targetCriteria.useSharedClassLoaderAsParent(true);
		} else if (leftCriteria.parentClassLoaderForMainClassLoader != null || rightCriteria.parentClassLoaderForMainClassLoader != null) {
			if (leftCriteria.parentClassLoaderForMainClassLoader != null && rightCriteria.parentClassLoaderForMainClassLoader != null) {
				if (leftCriteria.parentClassLoaderForMainClassLoader == rightCriteria.parentClassLoaderForMainClassLoader) {
					targetCriteria.useAsParentClassLoader(leftCriteria.parentClassLoaderForMainClassLoader);
				} else {
					throw Throwables.toRuntimeException("parentClassLoaderForMainClassLoaders are not the same instance");
				}
			} else if (leftCriteria.parentClassLoaderForMainClassLoader != null) {
				targetCriteria.useAsParentClassLoader(leftCriteria.parentClassLoaderForMainClassLoader);
			} else if (rightCriteria.parentClassLoaderForMainClassLoader != null) {
				targetCriteria.useAsParentClassLoader(rightCriteria.parentClassLoaderForMainClassLoader);
			}
		}
		targetCriteria.deleteFoundItemsOnClose = leftCriteria.deleteFoundItemsOnClose || rightCriteria.deleteFoundItemsOnClose;
		targetCriteria.collectMembers = leftCriteria.collectMembers || rightCriteria.collectMembers;
		targetCriteria.considerURLClassLoaderPathsAsScanned = leftCriteria.considerURLClassLoaderPathsAsScanned && rightCriteria.considerURLClassLoaderPathsAsScanned;
		targetCriteria.waitForSearchEnding = leftCriteria.waitForSearchEnding || rightCriteria.waitForSearchEnding;
		return super.logicOperation((S)leftCriteria, rightCriteria, binaryOperator, targetCriteria);
	}	

	public S deleteFoundItemsOnClose(boolean flag) {
		this.deleteFoundItemsOnClose = flag;
		return (S)this;
	}	

	public S useSharedClassLoaderAsMain(boolean value) {
		useSharedClassLoaderAsMain = value;
		useSharedClassLoaderAsParent = !useSharedClassLoaderAsMain;
		parentClassLoaderForMainClassLoader = null;
		return (S)this;
	}
	
	public S useAsParentClassLoader(ClassLoader classLoader) {
		if (classLoader == null)  {
			throw Throwables.toRuntimeException("Parent class loader could not be null");
		}
		useSharedClassLoaderAsMain = false;
		useSharedClassLoaderAsParent = false;
		parentClassLoaderForMainClassLoader = classLoader;
		return (S)this;
	}
	
	public S useSharedClassLoaderAsParent(boolean value) {
		useSharedClassLoaderAsParent = value;
		useSharedClassLoaderAsMain = !useSharedClassLoaderAsParent;		
		parentClassLoaderForMainClassLoader = null;
		return (S)this;
	}
	
	public S waitForSearchEnding(boolean waitForSearchEnding) {
		this.waitForSearchEnding = waitForSearchEnding;
		return (S)this;
	}

	public S considerURLClassLoaderPathsAsScanned(
		boolean value
	) {
		this.considerURLClassLoaderPathsAsScanned = value;
		return (S)this;
	}
	
	public S packageName(final Predicate<String> predicate) {
		this.predicate = concat(
			this.predicate,
			(context, cls) -> predicate.test(cls.getPackage().getName())
		);
		return (S)this;
	}
	

	public S className(final Predicate<String> predicate) {
		this.predicate = concat(
			this.predicate,
			(context, cls) -> predicate.test(cls.getName())
		);
		return (S)this;
	}

	
	public S byBytecode(Predicate<byte[]> predicate) {
		this.predicate = concat(
			this.predicate,
			(context, cls) -> {
				S criteria = context.getCriteria();
				return predicate.test(Streams.toByteArray(criteria.byteCodeSupplier.apply(cls)));
			}
		);
		return (S)this;
	}
	
	public S byBytecode(BiPredicate<Map<Class<?>, byte[]>, byte[]> predicate) {
		this.predicate = concat(
			this.predicate,
			(context, cls) -> {
				S criteria = context.getCriteria();
				return predicate.test(
					criteria.getLoadedBytecode(), 
					Streams.toByteArray(criteria.byteCodeSupplier.apply(cls))
				);
			}
		);
		return (S)this;
	}
	
	public S byClasses(BiPredicate<Map<Class<?>, Class<?>>, Class<?>> predicate) {
		this.predicate = concat(
			this.predicate,
			(context, cls) -> {
				return predicate.test(context.getCriteria().getUploadedClasses(), cls);
			}
		);
		return (S)this;
	}
	
	public S byClasses(Predicate<Class<?>> predicate) {
		this.predicate = concat(
			this.predicate,
			(context, cls) -> {
				return predicate.test(cls);
			}
		);
		return (S)this;
	}
	
	public <M extends Member> S byMembers(MemberCriteria<?, ?, ?> memberCriteria) {
		final String key = UUID.randomUUID().toString();
		this.memberCriterias.put(key, memberCriteria);		
		this.predicate = concat(
			this.predicate,
			(context, cls) -> {
				S criteria = context.getCriteria();
				return criteria.membersPredicate.test(criteria, context, memberCriteria, key, cls);
			}
		);
		return (S)this;
	}

	private boolean testMembers(
		S criteria,
		TestContext<S> context,
		MemberCriteria<?, ?, ?> memberCriteria,
		String key, 
		Class<?> cls
	) {
		return criteria.memberFinder.match(criteria.memberCriterias.get(key), cls);
	}
	
	private boolean testAndCollectMembers( 
		S criteria,
		TestContext<S> context,
		MemberCriteria<?, ?, ?> memberCriteria,
		String key, 
		Class<?> cls
	) {
		Collection<Member> members = (Collection<Member>)criteria.memberFinder.findAll(criteria.memberCriterias.get(key), cls);
		context.addMembersFound(memberCriteria, members);
		return !members.isEmpty();
	}
	
	public S createCopy() {
		S copy = super.createCopy();
		this.memberCriterias.entrySet().stream().collect(
			Collectors.toMap(
				Map.Entry::getKey, (entry) -> entry.getValue().createCopy(),
				(o1, o2) -> o1, () -> copy.memberCriterias
			)
		);
		copy.useSharedClassLoaderAsMain = this.useSharedClassLoaderAsMain;
		copy.parentClassLoaderForMainClassLoader = this.parentClassLoaderForMainClassLoader;
		copy.useSharedClassLoaderAsParent = this.useSharedClassLoaderAsParent;
		copy.deleteFoundItemsOnClose = this.deleteFoundItemsOnClose;
		copy.collectMembers = this.collectMembers;
		copy.considerURLClassLoaderPathsAsScanned = this.considerURLClassLoaderPathsAsScanned;
		copy.waitForSearchEnding = this.waitForSearchEnding;
		return copy;
	}
	
	
	@Override
	public TestContext<S> createTestContext() {
		return TestContext.<S>create((S)this);
	}
	
	
	@Override
	public void close() {
		memberFinder = null;
		super.close();
	}


	public static class TestContext<S extends SearchCriteriaAbst<S>> extends Criteria.TestContext<Class<?>, S> {
		private enum Elements {
			MEMBERS_FOUND
		}
		
		protected TestContext(S criteria) {
			super(criteria);
			if (criteria.collectMembers) {
				put(Elements.MEMBERS_FOUND, new ConcurrentHashMap<MemberCriteria<?, ?, ?>, Collection<Member>>());
			}
		}
		
		public static <S extends SearchCriteriaAbst<S>> TestContext<S> create(S criteria) {
			return (TestContext<S>)new TestContext<>(criteria);
		}
		
		public Map<MemberCriteria<?, ?, ?>, Collection<Member>> getMembersFound() {
			return get(Elements.MEMBERS_FOUND);
		}
		
		void addMembersFound(MemberCriteria<?, ?, ?> criteria, Collection<Member> members) {
			getMembersFound().put(criteria, members);
		}
	}
}
