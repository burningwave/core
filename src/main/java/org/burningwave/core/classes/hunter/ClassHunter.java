package org.burningwave.core.classes.hunter;

import java.lang.reflect.Member;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.burningwave.core.Criteria;
import org.burningwave.core.classes.ClassFactory;
import org.burningwave.core.classes.ClassHelper;
import org.burningwave.core.classes.JavaClass;
import org.burningwave.core.classes.MemberCriteria;
import org.burningwave.core.classes.MemberFinder;
import org.burningwave.core.classes.hunter.SearchCriteriaAbst.TestContext;
import org.burningwave.core.common.Strings;
import org.burningwave.core.io.FileInputStream;
import org.burningwave.core.io.FileSystemHelper;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.io.StreamHelper;
import org.burningwave.core.io.ZipInputStream;
import org.burningwave.core.io.FileSystemHelper.Scan;
import org.burningwave.core.reflection.ObjectRetriever;


public class ClassHunter extends CacherHunter<String, Class<?>, ClassHunter.SearchContext, ClassHunter.SearchResult> {
	PathMemoryClassLoader pathMemoryClassLoader;
	public final static String PARENT_CLASS_LOADER_SUPPLIER_IMPORTS_FOR_PATH_MEMORY_CLASS_LOADER_CONFIG_KEY = "classHunter.pathMemoryClassLoader.parent.supplier.imports";
	public final static String PARENT_CLASS_LOADER_SUPPLIER_FOR_PATH_MEMORY_CLASS_LOADER_CONFIG_KEY = "classHunter.pathMemoryClassLoader.parent";
	public final static Map<String, String> DEFAULT_CONFIG_VALUES = new LinkedHashMap<>();
	
	private ClassHunter(
		Supplier<ByteCodeHunter> byteCodeHunterSupplier,
		Supplier<ClassHunter> classHunterSupplier,
		FileSystemHelper fileSystemHelper, 
		PathHelper pathHelper,
		StreamHelper streamHelper,
		ClassFactory classFactory,
		ClassHelper classHelper,
		MemberFinder memberFinder,
		ObjectRetriever objectRetriever,
		ClassLoader parentClassLoader
	) {
		super(
			byteCodeHunterSupplier,
			classHunterSupplier,
			fileSystemHelper,
			pathHelper,
			streamHelper,
			classHelper,
			memberFinder,
			objectRetriever, 
			(variableInitObjects) -> ClassHunter.SearchContext._create(
				fileSystemHelper, streamHelper, variableInitObjects, objectRetriever
			),
			(context) -> new SearchResult(context)
		);
		this.pathMemoryClassLoader = PathMemoryClassLoader.create(
			parentClassLoader, pathHelper, classHelper, objectRetriever, byteCodeHunterSupplier
		);
	}
	
	static {
		DEFAULT_CONFIG_VALUES.put(ClassHunter.PARENT_CLASS_LOADER_SUPPLIER_IMPORTS_FOR_PATH_MEMORY_CLASS_LOADER_CONFIG_KEY, "");
		DEFAULT_CONFIG_VALUES.put(ClassHunter.PARENT_CLASS_LOADER_SUPPLIER_FOR_PATH_MEMORY_CLASS_LOADER_CONFIG_KEY, "null");
	}
	
	public static ClassHunter create(
			Supplier<ByteCodeHunter> byteCodeHunterSupplier, 
			Supplier<ClassHunter> classHunterSupplier, 
			FileSystemHelper fileSystemHelper, 
			PathHelper pathHelper, 
			StreamHelper streamHelper,
			ClassFactory classFactory,
			ClassHelper classHelper,
			MemberFinder memberFinder,
			ObjectRetriever objectRetriever,
			ClassLoader parentClassLoader
	) {
		return new ClassHunter(
			byteCodeHunterSupplier, classHunterSupplier, fileSystemHelper, pathHelper, streamHelper, classFactory, classHelper, memberFinder, objectRetriever, parentClassLoader
		);
	}	
	
	@Override
	public SearchResult findBy(ClassFileScanConfiguration scanConfig, SearchCriteria criteria) {
		criteria.collectMembers = true;
		return super.findBy(scanConfig, criteria);
	}
	
	@Override
	public SearchResult findBy(SearchForPathCriteria criteria) {
		criteria.collectMembers = true;
		return (SearchResult)super.findBy(criteria);
	}
	
	@Override
	<S extends SearchCriteriaAbst<S>> TestContext<S>  testCachedItem(ClassHunter.SearchContext context, String path, String key, Class<?> cls) {
		return context.testCriteria(cls);
	}
	
	@Override
	<S extends SearchCriteriaAbst<S>> void addCachedItemToContext(
		ClassHunter.SearchContext context, TestContext<S> testContext, String path, Entry<String, Class<?>> cachedItemAsEntry
	) {
		context.addItemFound(path, cachedItemAsEntry.getKey(), cachedItemAsEntry.getValue(), testContext.getMembersFound());
	}
	
	@Override
	void retrieveItemFromFileInputStream(
		ClassHunter.SearchContext context, 
		TestContext<SearchCriteria> criteriaTestContext,
		Scan.ItemContext<FileInputStream> scanItemContext, 
		JavaClass javaClass
	) {
		context.addItemFound(
			scanItemContext.getBasePathAsString(),
			Strings.Paths.clean(scanItemContext.getInput().getAbsolutePath()),
			criteriaTestContext.getEntity(),
			criteriaTestContext.getMembersFound()
		);
	}

	@Override
	void retrieveItemFromZipEntry(ClassHunter.SearchContext context, TestContext<SearchCriteria> criteriaTestContext, Scan.ItemContext<ZipInputStream.Entry> scanItemContext, JavaClass javaClass) {
		context.addItemFound(
			scanItemContext.getBasePathAsString(),
			Strings.Paths.clean(scanItemContext.getInput().getAbsolutePath()),
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
	
	public static class SearchContext extends org.burningwave.core.classes.hunter.SearchContext<String, Class<?>> {
		Map<Class<?>, Map<MemberCriteria<?, ?, ?>, Collection<Member>>> membersFound;
		private Map<MemberCriteria<?, ?, ?>, Collection<Member>> membersFoundFlatMap;
		
		static SearchContext _create(FileSystemHelper fileSystemHelper, StreamHelper streamHelper, InitContext initContext, ObjectRetriever objectRetriever) {
			return new SearchContext(fileSystemHelper, streamHelper,  initContext, objectRetriever);
		}
		
		SearchContext(FileSystemHelper fileSystemHelper, StreamHelper streamHelper, InitContext initContext,
				ObjectRetriever objectRetriever) {
			super(fileSystemHelper, streamHelper, initContext, objectRetriever);
			membersFound = new ConcurrentHashMap<>();
			membersFoundFlatMap = new ConcurrentHashMap<>();
		}
		
		void addItemFound(String path, String key, Class<?> item, Map<MemberCriteria<?, ?, ?>, Collection<Member>> membersForCriteria) {
			super.addItemFound(path, key, item);
			this.membersFound.put(item, membersForCriteria);
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

	public static class SearchResult extends org.burningwave.core.classes.hunter.SearchResult<String, Class<?>> {
		SearchResult(SearchContext context) {
			super(context);
		}
		
		public Map<Class<?>, Map<MemberCriteria<?, ?, ?>, Collection<Member>>> getMembersFound() {
			return ((SearchContext)this.context).getMembersFound();
		}
		
		public Map<MemberCriteria<?, ?, ?>, Collection<Member>> getMembersFoundFlatMap() {
			return ((SearchContext)this.context).getMembersFoundFlatMap();
		}
		
		@SuppressWarnings("unchecked")
		public <M extends Member, C extends MemberCriteria<M, C, T>, T extends Criteria.TestContext<M, C>> Collection<Member> getMembersFoundBy(C criteria) {
			Collection<Member> membersFoundByCriteria = getMembersFoundFlatMap().get(criteria);
			if (membersFoundByCriteria != null && membersFoundByCriteria.size() > 0) {
				return membersFoundByCriteria;
			} else {
				C criteriaCopy = criteria.createCopy();
				criteriaCopy.init(context.criteria.getClassSupplier(), context.criteria.getByteCodeSupplier());
				criteriaCopy.useClasses(context.criteria.getClassesToBeUploaded());
				final Collection<Member> membersFoundByCriteriaFinal = new CopyOnWriteArrayList<>();
				((SearchContext)this.context).getMembersFoundFlatMap().values().forEach((membersCollection) -> {
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
