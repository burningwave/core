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

import java.lang.reflect.Member;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.burningwave.core.Criteria;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.ClassCriteria.TestContext;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.iterable.Properties;

@SuppressWarnings("unchecked")
public class ClassHunter extends ClassPathScannerWithCachingSupport<Class<?>, ClassHunter.SearchContext, ClassHunter.SearchResult> {
	
	public static class Configuration {
		
		public static class Key {
			
			public final static String DEFAULT_PATH_SCANNER_CLASS_LOADER = "class-hunter.default-path-scanner-class-loader";
			public final static String PATH_SCANNER_CLASS_LOADER_SEARCH_CONFIG_CHECK_FILE_OPTIONS = "class-hunter.new-isolated-path-scanner-class-loader.search-config.check-file-option";
			
		}
		
		public final static Map<String, Object> DEFAULT_VALUES;
		
		static {
			DEFAULT_VALUES = new HashMap<>();
			DEFAULT_VALUES.put(Configuration.Key.DEFAULT_PATH_SCANNER_CLASS_LOADER + CodeExecutor.PROPERTIES_FILE_CODE_EXECUTOR_IMPORTS_KEY_SUFFIX,
				"${"+ Configuration.Key.DEFAULT_PATH_SCANNER_CLASS_LOADER + ".additional-imports}" +  ";" +
				ComponentSupplier.class.getName() + ";" +
				FileSystemItem.class.getName() + ";" + 
				PathScannerClassLoader.class.getName() + ";" +
				Supplier.class.getName() + ";"
			);
			DEFAULT_VALUES.put(Configuration.Key.DEFAULT_PATH_SCANNER_CLASS_LOADER + CodeExecutor.PROPERTIES_FILE_CODE_EXECUTOR_NAME_KEY_SUFFIX, ClassHunter.class.getPackage().getName() + ".DefaultPathScannerClassLoaderRetrieverForClassHunter");
			//DEFAULT_VALUES.put(Key.PARENT_CLASS_LOADER_FOR_PATH_SCANNER_CLASS_LOADER, "Thread.currentThread().getContextClassLoader()");
			DEFAULT_VALUES.put(
				Key.DEFAULT_PATH_SCANNER_CLASS_LOADER, 
				(Function<ComponentSupplier, ClassLoader>)(componentSupplier) -> 
					componentSupplier.getPathScannerClassLoader()
			);
			DEFAULT_VALUES.put(
				Key.PATH_SCANNER_CLASS_LOADER_SEARCH_CONFIG_CHECK_FILE_OPTIONS,
				"${" + ClassPathScannerAbst.Configuration.Key.DEFAULT_CHECK_FILE_OPTIONS + "}"
			);
		}
	}
	
	private Supplier<PathScannerClassLoader> defaultPathScannerClassLoaderSupplier;
	private Object defaultPathScannerClassLoaderOrDefaultPathScannerClassLoaderSupplier;
	private PathScannerClassLoader defaultPathScannerClassLoader;
	
	ClassHunter(
		Supplier<ClassHunter> classHunterSupplier,
		PathHelper pathHelper,
		Object defaultPathScannerClassLoaderOrDefaultPathScannerClassLoaderSupplier,
		Properties config
	) {
		super(
			classHunterSupplier,
			pathHelper,
			(initContext) -> ClassHunter.SearchContext._create(
				initContext
			),
			(context) -> new ClassHunter.SearchResult(context),
			config
		);
		this.defaultPathScannerClassLoaderOrDefaultPathScannerClassLoaderSupplier = defaultPathScannerClassLoaderOrDefaultPathScannerClassLoaderSupplier;
	}
	
	
	
	public static ClassHunter create(
		Supplier<ClassHunter> classHunterSupplier, 
		PathHelper pathHelper,
		Object defaultPathScannerClassLoaderOrDefaultClassLoaderSupplier,
		Properties config
	) {
		return new ClassHunter(
			classHunterSupplier, pathHelper, defaultPathScannerClassLoaderOrDefaultClassLoaderSupplier, config
		);
	}
	
	PathScannerClassLoader getDefaultPathScannerClassLoader(Object client) {
		if (defaultPathScannerClassLoaderSupplier != null) {
			PathScannerClassLoader classLoader = defaultPathScannerClassLoaderSupplier.get();
			if (defaultPathScannerClassLoader != classLoader) {
				synchronized(classLoader) {
					if (defaultPathScannerClassLoader != classLoader) {
						PathScannerClassLoader oldClassLoader = this.defaultPathScannerClassLoader;
						if (oldClassLoader != null) {
							oldClassLoader.unregister(this, true);
						}
						if (!classLoader.register(this)) {
							classLoader = getDefaultPathScannerClassLoader(client);
						} else {
							classLoader.register(client);
						}
						this.defaultPathScannerClassLoader = classLoader;
					}
				}
			}
			return classLoader;
		}
		if (defaultPathScannerClassLoader == null) {
			synchronized (this) {
				if (defaultPathScannerClassLoader == null) {
					if (defaultPathScannerClassLoaderOrDefaultPathScannerClassLoaderSupplier instanceof Supplier) {
						Object defaultPathScannerClassLoaderOrDefaultPathScannerClassLoaderSupplier =
							((Supplier<?>)this.defaultPathScannerClassLoaderOrDefaultPathScannerClassLoaderSupplier).get();
						if (defaultPathScannerClassLoaderOrDefaultPathScannerClassLoaderSupplier instanceof PathScannerClassLoader) {
							this.defaultPathScannerClassLoader = (PathScannerClassLoader)defaultPathScannerClassLoaderOrDefaultPathScannerClassLoaderSupplier;
							((MemoryClassLoader)defaultPathScannerClassLoader).register(this);
							((MemoryClassLoader)defaultPathScannerClassLoader).register(client);
							return defaultPathScannerClassLoader;
						} else if (defaultPathScannerClassLoaderOrDefaultPathScannerClassLoaderSupplier instanceof Supplier) {
							this.defaultPathScannerClassLoaderSupplier = (Supplier<PathScannerClassLoader>) defaultPathScannerClassLoaderOrDefaultPathScannerClassLoaderSupplier;
							return getDefaultPathScannerClassLoader(client);
						}
					}
				} else { 
					return defaultPathScannerClassLoader;
				}
			}
		}
		return defaultPathScannerClassLoader;
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
	<S extends SearchConfigAbst<S>> ClassCriteria.TestContext testCachedItem(ClassHunter.SearchContext context, String path, String key, Class<?> cls) {
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
	
	@SuppressWarnings("resource")
	public static class SearchContext extends org.burningwave.core.classes.SearchContext<Class<?>> {
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
			if (membersFound != null) {
				membersFound.clear();
				membersFound = null;
			}
			if (membersFoundFlatMap != null) {
				membersFoundFlatMap.clear();
				membersFoundFlatMap = null;
			}			
			super.close();
		}
	}

	public static class SearchResult extends org.burningwave.core.classes.SearchResult<Class<?>> {
		SearchResult(SearchContext context) {
			super(context);
		}
		
		public Map<Class<?>, Map<MemberCriteria<?, ?, ?>, Collection<Member>>> getMembers() {
			return ((SearchContext)this.context).getMembersFound();
		}
		
		public Map<MemberCriteria<?, ?, ?>, Collection<Member>> getMembersFlatMap() {
			return ((SearchContext)this.context).getMembersFoundFlatMap();
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
					((SearchContext)this.context).getMembersFoundFlatMap().values().forEach((membersCollection) -> {
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
	
	@Override
	public void clearCache() {
		super.clearCache();
		PathScannerClassLoader pathScannerClassLoader = this.defaultPathScannerClassLoader;
		if (pathScannerClassLoader != null) {
			pathScannerClassLoader.unregister(this, true);
			this.defaultPathScannerClassLoader = null;
		}
	}
	
	@Override
	public void close() {
		super.close();
		defaultPathScannerClassLoader = null;
	}

}
