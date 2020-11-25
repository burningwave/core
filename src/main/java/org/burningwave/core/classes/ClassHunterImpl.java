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
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.burningwave.core.classes.ClassCriteria.TestContext;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.iterable.Properties;

class ClassHunterImpl extends ClassPathScannerWithCachingSupport.Abst<Class<?>, ClassHunterImpl.SearchContext, ClassHunter.SearchResult> implements ClassHunter {
	
	ClassHunterImpl(
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
	<S extends SearchConfigAbst<S>> ClassCriteria.TestContext testCachedItem(ClassHunterImpl.SearchContext context, String path, String key, Class<?> cls) {
		return context.test(context.retrieveClass(cls));
	}
	
	@Override
	void addToContext(ClassHunterImpl.SearchContext context, TestContext criteriaTestContext,
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
	
	static class SearchContext extends org.burningwave.core.classes.SearchContext<Class<?>> {
		Map<Class<?>, Map<MemberCriteria<?, ?, ?>, Collection<Member>>> membersFound;
		Map<MemberCriteria<?, ?, ?>, Collection<Member>> membersFoundFlatMap;
		
		static ClassHunterImpl.SearchContext _create(InitContext initContext) {
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

}