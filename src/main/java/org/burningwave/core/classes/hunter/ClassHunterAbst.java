package org.burningwave.core.classes.hunter;

import java.lang.reflect.Member;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.burningwave.core.Criteria;
import org.burningwave.core.classes.ClassCriteria;
import org.burningwave.core.classes.ClassHelper;
import org.burningwave.core.classes.JavaClass;
import org.burningwave.core.classes.MemberCriteria;
import org.burningwave.core.classes.MemberFinder;
import org.burningwave.core.io.FileInputStream;
import org.burningwave.core.io.FileSystemHelper;
import org.burningwave.core.io.FileSystemHelper.Scan;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.io.StreamHelper;
import org.burningwave.core.io.ZipInputStream;


public abstract class ClassHunterAbst<K, R extends ClassHunterAbst.SearchResult<K>> extends CacherHunter<K, Class<?>, ClassHunterAbst.SearchContext<K>, R> {
	PathMemoryClassLoader pathMemoryClassLoader;
	
	ClassHunterAbst(
		Supplier<ByteCodeHunter> byteCodeHunterSupplier,
		Supplier<ClassHunter> classHunterSupplier,
		FileSystemHelper fileSystemHelper, 
		PathHelper pathHelper,
		StreamHelper streamHelper,
		ClassHelper classHelper,
		MemberFinder memberFinder,
		ClassLoader parentClassLoader,
		Function<SearchContext<K>, R> resultSupplier
	) {
		super(
			byteCodeHunterSupplier,
			classHunterSupplier,
			fileSystemHelper,
			pathHelper,
			streamHelper,
			classHelper,
			memberFinder,
			(variableInitObjects) -> ClassHunterAbst.SearchContext._create(
				fileSystemHelper, streamHelper, variableInitObjects
			),
			resultSupplier
		);
		this.pathMemoryClassLoader = PathMemoryClassLoader.create(
			parentClassLoader, pathHelper, classHelper, byteCodeHunterSupplier
		);
	}
	
	@Override
	public R findBy(ClassFileScanConfig scanConfig, SearchConfig searchConfig) {
		searchConfig.getClassCriteria().collectMembers(true);
		return super.findBy(scanConfig, searchConfig);
	}
	
	@Override
	public SearchResult<K> findBy(CacheableSearchConfig searchConfig) {
		searchConfig.getClassCriteria().collectMembers(true);
		return (SearchResult<K>)super.findBy(searchConfig);
	}
	
	@Override
	<S extends SearchConfigAbst<S>> ClassCriteria.TestContext testCachedItem(ClassHunterAbst.SearchContext<K> context, String path, K key, Class<?> cls) {
		return context.testCriteria(cls);
	}
	
	@Override
	<S extends SearchConfigAbst<S>> void addCachedItemToContext(
		ClassHunterAbst.SearchContext<K> context, ClassCriteria.TestContext testContext, String path, Entry<K, Class<?>> cachedItemAsEntry
	) {
		context.addItemFound(path, cachedItemAsEntry.getKey(), cachedItemAsEntry.getValue(), testContext.getMembersFound());
	}
	
	@Override
	void retrieveItemFromFileInputStream(
		ClassHunterAbst.SearchContext<K> context, 
		ClassCriteria.TestContext criteriaTestContext,
		Scan.ItemContext<FileInputStream> scanItemContext, 
		JavaClass javaClass
	) {
		context.addItemFound(
			scanItemContext.getBasePathAsString(),
			buildKey(scanItemContext.getInput().getAbsolutePath()),
			criteriaTestContext.getEntity(),
			criteriaTestContext.getMembersFound()
		);
	}
	
	abstract K buildKey(String absolutePath) ;
	
	@Override
	void retrieveItemFromZipEntry(ClassHunterAbst.SearchContext<K> context, ClassCriteria.TestContext criteriaTestContext, Scan.ItemContext<ZipInputStream.Entry> scanItemContext, JavaClass javaClass) {
		context.addItemFound(
			scanItemContext.getBasePathAsString(),
			buildKey(scanItemContext.getInput().getAbsolutePath()),
			criteriaTestContext.getEntity(),
			criteriaTestContext.getMembersFound()
		);
	}
	
	
	@Override
	public void close() {
		pathMemoryClassLoader.close();
		pathMemoryClassLoader = null;
		super.close();
	}
	
	public static class SearchContext<K> extends org.burningwave.core.classes.hunter.SearchContext<K, Class<?>> {
		Map<Class<?>, Map<MemberCriteria<?, ?, ?>, Collection<Member>>> membersFound;
		private Map<MemberCriteria<?, ?, ?>, Collection<Member>> membersFoundFlatMap;
		
		static <K> SearchContext<K> _create(FileSystemHelper fileSystemHelper, StreamHelper streamHelper, InitContext initContext) {
			return new SearchContext<K>(fileSystemHelper, streamHelper,  initContext);
		}
		
		SearchContext(FileSystemHelper fileSystemHelper, StreamHelper streamHelper, InitContext initContext) {
			super(fileSystemHelper, streamHelper, initContext);
			membersFound = new ConcurrentHashMap<>();
			membersFoundFlatMap = new ConcurrentHashMap<>();
		}
		
		void addItemFound(String path, K key, Class<?> item, Map<MemberCriteria<?, ?, ?>, Collection<Member>> membersForCriteria) {
			super.addItemFound(path, key, item);
			try {
				this.membersFound.put(item, membersForCriteria);
			} catch (NullPointerException exc) {
				// TODO Auto-generated catch block
				exc.printStackTrace();
			}
			membersForCriteria.forEach((criteria, memberList) -> {
				Collection<Member> coll = membersFoundFlatMap.get(criteria);
				if (coll == null) {								
					coll = new CopyOnWriteArrayList<>();
					membersFoundFlatMap.put(criteria, coll);
				}
				coll.addAll(memberList);
			});	
		}
		
		void addAllMembersFound(Class<?> cls, Map<MemberCriteria<?, ?, ?>, Collection<Member>> membersFound) {
			this.membersFound.put(cls, membersFound);
			this.membersFoundFlatMap.putAll(membersFound);
		}
		
		Map<Class<?>, Map<MemberCriteria<?, ?, ?>, Collection<Member>>> getMembersFound() {
			return membersFound;
		}
		
		public Map<MemberCriteria<?, ?, ?>, Collection<Member>> getMembersFoundFlatMap() {
			return membersFoundFlatMap;
		}
		
		@Override
		public void close() {
			membersFound.clear();
			membersFound = null;
			membersFoundFlatMap.clear();
			membersFoundFlatMap = null;
			super.close();
		}
	}

	public static class SearchResult<K> extends org.burningwave.core.classes.hunter.SearchResult<K, Class<?>> {
		SearchResult(SearchContext<K> context) {
			super(context);
		}
		
		public Map<Class<?>, Map<MemberCriteria<?, ?, ?>, Collection<Member>>> getMembersFound() {
			return ((SearchContext<K>)this.context).getMembersFound();
		}
		
		public Map<MemberCriteria<?, ?, ?>, Collection<Member>> getMembersFoundFlatMap() {
			return ((SearchContext<K>)this.context).getMembersFoundFlatMap();
		}
		
		@SuppressWarnings("unchecked")
		public <M extends Member, C extends MemberCriteria<M, C, T>, T extends Criteria.TestContext<M, C>> Collection<Member> getMembersFoundBy(C criteria) {
			Collection<Member> membersFoundByCriteria = getMembersFoundFlatMap().get(criteria);
			if (membersFoundByCriteria != null && membersFoundByCriteria.size() > 0) {
				return membersFoundByCriteria;
			} else {
				C criteriaCopy = criteria.createCopy();
				criteriaCopy.init(context.getSearchConfig().getClassCriteria().getClassSupplier(), context.getSearchConfig().getClassCriteria().getByteCodeSupplier());
				criteriaCopy.useClasses(context.getSearchConfig().getClassCriteria().getClassesToBeUploaded());
				final Collection<Member> membersFoundByCriteriaFinal = new CopyOnWriteArrayList<>();
				((SearchContext<K>)this.context).getMembersFoundFlatMap().values().forEach((membersCollection) -> {
					membersCollection.stream().filter(
						(member) -> criteriaCopy.testAndReturnFalseIfNullOrTrueByDefault((M)member).getResult()
					).collect(
						Collectors.toCollection(() -> membersFoundByCriteriaFinal)
					);
				});
				return membersFoundByCriteriaFinal;
			}
		}
	}
}
