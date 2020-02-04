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
import org.burningwave.core.Criteria.TestContext;
import org.burningwave.core.classes.ClassCriteria;
import org.burningwave.core.classes.ClassHelper;
import org.burningwave.core.classes.JavaClass;
import org.burningwave.core.classes.MemberCriteria;
import org.burningwave.core.classes.MemberFinder;
import org.burningwave.core.io.ClassFileScanConfig;
import org.burningwave.core.io.FileSystemHelper;
import org.burningwave.core.io.FileSystemHelper.Scan;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.io.StreamHelper;
import org.burningwave.core.jvm.LowLevelObjectsHandler;


public class ClassHunter extends ClassPathScannerWithCachingSupport<Class<?>, ClassHunter.SearchContext, ClassHunter.SearchResult> {
	public final static String PARENT_CLASS_LOADER_SUPPLIER_FOR_PATH_MEMORY_CLASS_LOADER_CONFIG_KEY = "class-hunter.path-memory-class-loader.parent";
	public final static Map<String, String> DEFAULT_CONFIG_VALUES = new LinkedHashMap<>();

	PathMemoryClassLoader pathMemoryClassLoader;
	
	ClassHunter(
		Supplier<ByteCodeHunter> byteCodeHunterSupplier,
		Supplier<ClassHunter> classHunterSupplier,
		FileSystemHelper fileSystemHelper, 
		PathHelper pathHelper,
		StreamHelper streamHelper,
		ClassHelper classHelper,
		MemberFinder memberFinder,
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
			(variableInitObjects) -> ClassHunter.SearchContext._create(
				fileSystemHelper, streamHelper, variableInitObjects
			),
			(context) -> new ClassHunter.SearchResult(context)
		);
		this.pathMemoryClassLoader = PathMemoryClassLoader.create(
			parentClassLoader, pathHelper, classHelper, byteCodeHunterSupplier
		);
	}
	
	static {
		DEFAULT_CONFIG_VALUES.put(PARENT_CLASS_LOADER_SUPPLIER_FOR_PATH_MEMORY_CLASS_LOADER_CONFIG_KEY + LowLevelObjectsHandler.SUPPLIER_IMPORTS_KEY_SUFFIX, "");
		DEFAULT_CONFIG_VALUES.put(PARENT_CLASS_LOADER_SUPPLIER_FOR_PATH_MEMORY_CLASS_LOADER_CONFIG_KEY, "null");
	}
	
	public static ClassHunter create(
		Supplier<ByteCodeHunter> byteCodeHunterSupplier, 
		Supplier<ClassHunter> classHunterSupplier, 
		FileSystemHelper fileSystemHelper, 
		PathHelper pathHelper, 
		StreamHelper streamHelper,
		ClassHelper classHelper,
		MemberFinder memberFinder,
		ClassLoader parentClassLoader
	) {
		return new ClassHunter(
			byteCodeHunterSupplier, classHunterSupplier, fileSystemHelper, pathHelper, streamHelper, classHelper, memberFinder, parentClassLoader
		);
	}
	
	@Override
	public ClassHunter.SearchResult findBy(ClassFileScanConfig scanConfig, SearchConfig searchConfig) {
		searchConfig.getClassCriteria().collectMembers(true);
		return super.findBy(scanConfig, searchConfig);
	}
	
	@Override
	public ClassHunter.SearchResult findBy(CacheableSearchConfig searchConfig) {
		searchConfig.getClassCriteria().collectMembers(true);
		return super.findBy(searchConfig);
	}
	
	@Override
	<S extends SearchConfigAbst<S>> ClassCriteria.TestContext testCachedItem(ClassHunter.SearchContext context, String path, String key, Class<?> cls) {
		return context.testCriteria(context.retrieveClass(cls));
	}
	
	@Override
	<S extends SearchConfigAbst<S>> void addCachedItemToContext(
		ClassHunter.SearchContext context, ClassCriteria.TestContext testContext, String path, Entry<String, Class<?>> cachedItemAsEntry
	) {
		context.addItemFound(path, cachedItemAsEntry.getKey(), cachedItemAsEntry.getValue(), testContext.getMembersFound());
	}
	
	@Override
	void retrieveItemFromFileInputStream(
		ClassHunter.SearchContext context, 
		ClassCriteria.TestContext criteriaTestContext,
		Scan.ItemContext scanItemContext, 
		JavaClass javaClass
	) {
		context.addItemFound(
			scanItemContext.getBasePathAsString(),
			scanItemContext.getInput().getAbsolutePath(),
			criteriaTestContext.getEntity(),
			criteriaTestContext.getMembersFound()
		);
	}
	
	@Override
	void retrieveItemFromZipEntry(ClassHunter.SearchContext context, ClassCriteria.TestContext criteriaTestContext, Scan.ItemContext scanItemContext, JavaClass javaClass) {
		context.addItemFound(
			scanItemContext.getBasePathAsString(),
			scanItemContext.getInput().getAbsolutePath(),
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
	
	public static class SearchContext extends org.burningwave.core.classes.hunter.SearchContext<Class<?>> {
		Map<Class<?>, Map<MemberCriteria<?, ?, ?>, Collection<Member>>> membersFound;
		private Map<MemberCriteria<?, ?, ?>, Collection<Member>> membersFoundFlatMap;
		
		static SearchContext _create(FileSystemHelper fileSystemHelper, StreamHelper streamHelper, InitContext initContext) {
			return new SearchContext(fileSystemHelper, streamHelper,  initContext);
		}
		
		SearchContext(FileSystemHelper fileSystemHelper, StreamHelper streamHelper, InitContext initContext) {
			super(fileSystemHelper, streamHelper, initContext);
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

	public static class SearchResult extends org.burningwave.core.classes.hunter.SearchResult<Class<?>> {
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
		
		@Override
		@SuppressWarnings("unchecked")
		protected <C extends Criteria<Class<?>, C, T>, T extends TestContext<Class<?>, C>> C createCriteriaCopy(C criteria) {
			if (criteria instanceof ClassCriteria) {
				ClassCriteria criteriaCopy = ((ClassCriteria)criteria).createCopy();
				criteriaCopy.init(
					context.getSearchConfig().getClassCriteria().getClassSupplier(),
					context.getSearchConfig().getClassCriteria().getByteCodeSupplier()
				);
				return (C)criteriaCopy;
			} else {
				return super.createCriteriaCopy(criteria);
			}
		}		
		@SuppressWarnings("unchecked")
		public <M extends Member, C extends MemberCriteria<M, C, T>, T extends Criteria.TestContext<M, C>> Collection<Member> getMembersBy(C criteria) {
			Collection<Member> membersFoundByCriteria = getMembersFlatMap().get(criteria);
			if (membersFoundByCriteria != null && membersFoundByCriteria.size() > 0) {
				return membersFoundByCriteria;
			} else {
				C criteriaCopy = criteria.createCopy();
				criteriaCopy.init(context.getSearchConfig().getClassCriteria().getClassSupplier(), context.getSearchConfig().getClassCriteria().getByteCodeSupplier());
				criteriaCopy.useClasses(context.getSearchConfig().getClassCriteria().getClassesToBeUploaded());
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
