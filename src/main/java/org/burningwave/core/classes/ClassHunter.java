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
 * Copyright (c) 2019-2022Roberto Gentili
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

import static org.burningwave.core.assembler.StaticComponentContainer.IterableObjectHelper;

import java.lang.reflect.Member;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.burningwave.core.Criteria;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.io.PathHelper;


@SuppressWarnings("unchecked")
public interface ClassHunter extends ClassPathScanner<Class<?>, ClassHunter.SearchResult> {

	public static class Configuration {

		public static class Key {
			public final static String NAME_IN_CONFIG_PROPERTIES = "class-hunter";
			public final static String DEFAULT_PATH_SCANNER_CLASS_LOADER = NAME_IN_CONFIG_PROPERTIES + ".default-path-scanner-class-loader";
			public final static String PATH_SCANNER_CLASS_LOADER_SEARCH_CONFIG_CHECK_FILE_OPTIONS = NAME_IN_CONFIG_PROPERTIES + ".new-isolated-path-scanner-class-loader.search-config.check-file-option";

		}

		public final static Map<String, Object> DEFAULT_VALUES;

		static {
			Map<String, Object> defaultValues = new HashMap<>();

			defaultValues.put(Configuration.Key.DEFAULT_PATH_SCANNER_CLASS_LOADER + CodeExecutor.Configuration.Key.PROPERTIES_FILE_SUPPLIER_IMPORTS_SUFFIX,
				"${"+ CodeExecutor.Configuration.Key.COMMON_IMPORTS + "}" + IterableObjectHelper.getDefaultValuesSeparator() +
				"${"+ Configuration.Key.DEFAULT_PATH_SCANNER_CLASS_LOADER + "." + CodeExecutor.Configuration.Key.PROPERTIES_FILE_SUPPLIER_KEY +".additional-imports}" + IterableObjectHelper.getDefaultValuesSeparator() +
				PathScannerClassLoader.class.getName() + IterableObjectHelper.getDefaultValuesSeparator()
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
		Map<?, ?> config
	) {
		return new ClassHunterImpl(
			pathHelper, defaultPathScannerClassLoaderOrDefaultClassLoaderSupplier, config
		);
	}

	public static class SearchResult extends org.burningwave.core.classes.SearchResult<Class<?>> {
		SearchResult(ClassHunterImpl.SearchContext context) {
			super(context);
		}

		public Map<Class<?>, Map<MemberCriteria<?, ?, ?>, Collection<Member>>> getMembers() {
			return ((ClassHunterImpl.SearchContext)this.context).getMembersFound();
		}

		public Map<MemberCriteria<?, ?, ?>, Collection<Member>> getMembersFlatMap() {
			return ((ClassHunterImpl.SearchContext)this.context).getMembersFoundFlatMap();
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
					((ClassHunterImpl.SearchContext)this.context).getMembersFoundFlatMap().values().forEach((membersCollection) -> {
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