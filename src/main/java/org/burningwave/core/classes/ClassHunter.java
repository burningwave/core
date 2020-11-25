package org.burningwave.core.classes;

import java.lang.reflect.Member;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.burningwave.core.Criteria;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.ClassCriteria.TestContext;
import org.burningwave.core.classes.ClassPathScannerWithCachingSupport.CacheScanner;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.iterable.Properties;


@SuppressWarnings("unchecked")
public interface ClassHunter {
	
	public static class Configuration {
		
		public static class Key {
			public final static String NAME_IN_CONFIG_PROPERTIES = "class-hunter";
			public final static String DEFAULT_PATH_SCANNER_CLASS_LOADER = NAME_IN_CONFIG_PROPERTIES + ".default-path-scanner-class-loader";
			public final static String PATH_SCANNER_CLASS_LOADER_SEARCH_CONFIG_CHECK_FILE_OPTIONS = NAME_IN_CONFIG_PROPERTIES + ".new-isolated-path-scanner-class-loader.search-config.check-file-option";
			
		}
		
		public final static Map<String, Object> DEFAULT_VALUES;
		
		static {
			Map<String, Object> defaultValues = new HashMap<>();
			
			defaultValues.put(Configuration.Key.DEFAULT_PATH_SCANNER_CLASS_LOADER + CodeExecutor.Configuration.Key.PROPERTIES_FILE_IMPORTS_SUFFIX,
				"${"+ CodeExecutor.Configuration.Key.COMMON_IMPORTS + "}" + CodeExecutor.Configuration.Value.CODE_LINE_SEPARATOR + 
				"${"+ Configuration.Key.DEFAULT_PATH_SCANNER_CLASS_LOADER + ".additional-imports}" + CodeExecutor.Configuration.Value.CODE_LINE_SEPARATOR +
				PathScannerClassLoader.class.getName() + ";"
			);
			defaultValues.put(Configuration.Key.DEFAULT_PATH_SCANNER_CLASS_LOADER + CodeExecutor.Configuration.Key.PROPERTIES_FILE_SUPPLIER_NAME_SUFFIX, ClassHunter.class.getPackage().getName() + ".DefaultPathScannerClassLoaderRetrieverForClassHunter");
			//DEFAULT_VALUES.put(Key.PARENT_CLASS_LOADER_FOR_PATH_SCANNER_CLASS_LOADER, "Thread.currentThread().getContextClassLoader()");
			defaultValues.put(
				Key.DEFAULT_PATH_SCANNER_CLASS_LOADER, 
				(Function<ComponentSupplier, ClassLoader>)(componentSupplier) -> 
					componentSupplier.getPathScannerClassLoader()
			);
			defaultValues.put(
				Key.PATH_SCANNER_CLASS_LOADER_SEARCH_CONFIG_CHECK_FILE_OPTIONS,
				"${" + ClassPathScanner.Configuration.Key.DEFAULT_CHECK_FILE_OPTIONS + "}"
			);
			
			DEFAULT_VALUES = Collections.unmodifiableMap(defaultValues);
		}
	}
	
	public static ClassHunter create(
		PathHelper pathHelper,
		Object defaultPathScannerClassLoaderOrDefaultClassLoaderSupplier,
		Properties config
	) {
		return new Impl(
			pathHelper, defaultPathScannerClassLoaderOrDefaultClassLoaderSupplier, config
		);		
	}	
	
	//Not cached search
	public CacheScanner<Class<?>, SearchResult> loadInCache(CacheableSearchConfig searchConfig);

	public SearchResult findBy(SearchConfig searchConfig);

	public SearchResult findBy(CacheableSearchConfig searchConfig);
	
	public SearchResult find();

	public SearchResult findAndCache();
	
	public void closeSearchResults();

	public void clearCache();

	public void clearCache(boolean closeSearchResults);
	
	static class Impl extends ClassPathScannerWithCachingSupport.Abst<Class<?>, SearchContext, ClassHunter.SearchResult> implements ClassHunter {
		
		Impl(
			PathHelper pathHelper,
			Object defaultPathScannerClassLoaderOrDefaultPathScannerClassLoaderSupplier,
			Properties config
		) {
			super(
				pathHelper,
				(initContext) -> SearchContext._create(
					initContext
				),
				(context) -> new ClassHunter.SearchResult(context),
				defaultPathScannerClassLoaderOrDefaultPathScannerClassLoaderSupplier,
				config
			);
		}
		
		@Override
		String getNameInConfigProperties() {
			return ClassHunter.Configuration.Key.NAME_IN_CONFIG_PROPERTIES;
		}
		
		@Override
		String getDefaultPathScannerClassLoaderNameInConfigProperties() {
			return ClassHunter.Configuration.Key.DEFAULT_PATH_SCANNER_CLASS_LOADER;
		}
		
		@Override
		String getDefaultPathScannerClassLoaderCheckFileOptionsNameInConfigProperties() {
			return ClassHunter.Configuration.Key.PATH_SCANNER_CLASS_LOADER_SEARCH_CONFIG_CHECK_FILE_OPTIONS;
		}
		
		@Override
		<S extends SearchConfigAbst<S>> ClassCriteria.TestContext testCachedItem(Impl.SearchContext context, String path, String key, Class<?> cls) {
			return context.test(context.retrieveClass(cls));
		}
		
		@Override
		void addToContext(SearchContext context, TestContext criteriaTestContext,
			String basePath, FileSystemItem fileSystemItem, JavaClass javaClass
		) {
			context.addItemFound(
				basePath,
				fileSystemItem.getAbsolutePath(),
				criteriaTestContext.getEntity()
			);
		}
		
		@Override
		public CacheScanner<Class<?>, SearchResult> loadInCache(CacheableSearchConfig searchConfig) {
			searchConfig.getClassCriteria().collectMembers(true);
			return super.loadInCache(searchConfig);
		}
		
		@Override
		public ClassHunter.SearchResult findBy(SearchConfig searchConfig) {
			searchConfig.getClassCriteria().collectMembers(true);
			return super.findBy(searchConfig);
		}
		
		@Override
		public ClassHunter.SearchResult findBy(CacheableSearchConfig searchConfig) {
			searchConfig.getClassCriteria().collectMembers(true);
			return super.findBy(searchConfig);
		}
		
		@Override
		public void clearCache(boolean closeSearchResults) {
			this.defaultPathScannerClassLoaderManager.reset();
			super.clearCache(closeSearchResults);
		}
		
		@Override
		public void close() {
			closeResources(() -> isClosed(), () -> {
				this.defaultPathScannerClassLoaderManager.close();
				super.close();
				this.defaultPathScannerClassLoaderManager = null;
			});
		}

	}
	
	static class SearchContext extends org.burningwave.core.classes.SearchContext<Class<?>> {
		Map<Class<?>, Map<MemberCriteria<?, ?, ?>, Collection<Member>>> membersFound;
		Map<MemberCriteria<?, ?, ?>, Collection<Member>> membersFoundFlatMap;
		
		static SearchContext _create(InitContext initContext) {
			return new SearchContext(initContext);
		}
		
		SearchContext(InitContext initContext) {
			super(initContext);
		}
		
		void addAllMembersFound(Class<?> cls, Map<MemberCriteria<?, ?, ?>, Collection<Member>> membersFound) {
			this.membersFound.put(cls, membersFound);
			this.membersFoundFlatMap.putAll(membersFound);
		}
		
		
		Map<Class<?>, Map<MemberCriteria<?, ?, ?>, Collection<Member>>> getMembersFound() {
			if (membersFound == null) {
				loadMemberMaps();
			}
			return membersFound;
		}
		
		public Map<MemberCriteria<?, ?, ?>, Collection<Member>> getMembersFoundFlatMap() {
			if (membersFoundFlatMap == null) {
				loadMemberMaps();
			}
			return membersFoundFlatMap;
		}

		private void loadMemberMaps() {
			ClassCriteria classCriteria = searchConfig.getClassCriteria();
			if (!classCriteria.memberCriterias.isEmpty() && (membersFound == null || membersFoundFlatMap == null)) {
				synchronized(this) {
					if (membersFound == null || membersFoundFlatMap == null) {
						Map<Class<?>, Map<MemberCriteria<?, ?, ?>, Collection<Member>>> membersFound = new ConcurrentHashMap<>();
						Map<MemberCriteria<?, ?, ?>, Collection<Member>> membersFoundFlatMap = new ConcurrentHashMap<>();
						for (Entry<String, Class<?>> pathAndItem : itemsFoundFlatMap.entrySet()) {
							ClassCriteria.TestContext testContext = test(pathAndItem.getValue());
							testContext.getMembersFound();
							Map<MemberCriteria<?, ?, ?>, Collection<Member>> membersForCriteria = testContext.getMembersFound();
							membersFound.put(testContext.getEntity(), membersForCriteria);
							membersForCriteria.forEach((criteria, memberList) -> {
								Collection<Member> coll = membersFoundFlatMap.get(criteria);
								if (coll == null) {								
									coll = new CopyOnWriteArrayList<>();
									membersFoundFlatMap.put(criteria, coll);
								}
								coll.addAll(memberList);
							});
						}
						this.membersFound = membersFound;
						this.membersFoundFlatMap = membersFoundFlatMap;
					}
				}
			}
		}
		
		@Override
		public void close() {
			membersFound = null;
			membersFoundFlatMap = null;		
			super.close();
		}
	}
	
	public static class SearchResult extends org.burningwave.core.classes.SearchResult<Class<?>> {
		SearchResult(Impl.SearchContext context) {
			super(context);
		}
		
		public Map<Class<?>, Map<MemberCriteria<?, ?, ?>, Collection<Member>>> getMembers() {
			return ((Impl.SearchContext)this.context).getMembersFound();
		}
		
		public Map<MemberCriteria<?, ?, ?>, Collection<Member>> getMembersFlatMap() {
			return ((Impl.SearchContext)this.context).getMembersFoundFlatMap();
		}
		
		public Collection<Class<?>> getClasses() {
			return context.getItemsFound();
		}
		
		public Map<String, Class<?>> getClassesFlatMap() {
			return context.getItemsFoundFlatMap();
		}
		
		public <M extends Member, C extends MemberCriteria<M, C, T>, T extends Criteria.TestContext<M, C>> Collection<Member> getMembersBy(C criteria) {
			Collection<Member> membersFoundByCriteria = getMembersFlatMap().get(criteria);
			if (membersFoundByCriteria != null && membersFoundByCriteria.size() > 0) {
				return membersFoundByCriteria;
			} else {
				try (C criteriaCopy = createCriteriaCopy(criteria)) {
					final Collection<Member> membersFoundByCriteriaFinal = new HashSet<>();
					((Impl.SearchContext)this.context).getMembersFoundFlatMap().values().forEach((membersCollection) -> {
						membersCollection.stream().filter(
							(member) ->
								criteriaCopy.testWithFalseResultForNullEntityOrTrueResultForNullPredicate((M)member).getResult()
						).collect(
							Collectors.toCollection(() -> membersFoundByCriteriaFinal)
						);
					});
					return membersFoundByCriteriaFinal;
				}
			}
		}
	}
}