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

import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.lang.reflect.Member;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.burningwave.core.Closeable;
import org.burningwave.core.Criteria;
import org.burningwave.core.ManagedLogger;

public class SearchResult<E> implements Closeable, ManagedLogger {
	SearchContext<E> context;
	ClassPathScanner.Abst<E, ?, ?> classPathScanner;
	
	SearchResult(SearchContext<E> context) {
		this.context = context;
	}
	
	void setClassPathScanner(ClassPathScanner.Abst<E, ?, ?> classPathScanner) {
		this.classPathScanner = classPathScanner;
		classPathScanner.register(this);
	}
	
	Collection<E> getItemsFound() {
		return context.getItemsFound();
	}
	
	Map<String, E> getItemsFoundFlatMap() {
		return context.getItemsFoundFlatMap();
	}
	
	public <C extends CriteriaWithClassElementsSupplyingSupport<E, C, T>, T extends CriteriaWithClassElementsSupplyingSupport.TestContext<E, C>> Map<String, E> getClasses(C criteria) {
		try (C criteriaCopy = createCriteriaCopy(criteria)) {
			Map<String, E> itemsFound = new HashMap<>();
			getItemsFoundFlatMap().forEach((path, javaClass) -> {
				if (criteriaCopy.testWithFalseResultForNullEntityOrTrueResultForNullPredicate(javaClass).getResult()) {
					itemsFound.put(path, javaClass);
				}
			});
			return itemsFound;
		}
	}
	
	public <C extends CriteriaWithClassElementsSupplyingSupport<E, C, T>, T extends Criteria.TestContext<E, C>> Map.Entry<String, E> getUnique(C criteria) {
		Map<String, E> itemsFound = getClasses(criteria);
		if (itemsFound.size() > 1) {
			Throwables.throwException("Found more than one element");
		}
		return itemsFound.entrySet().stream().findFirst().orElseGet(() -> null);
	}
	
	<C extends CriteriaWithClassElementsSupplyingSupport<E, C, T>, T extends Criteria.TestContext<E, C>> C createCriteriaCopy(C criteria) {
		C criteriaCopy = criteria.createCopy().init(
			context.getSearchConfig().getClassCriteria().getClassSupplier(),
			context.getSearchConfig().getClassCriteria().getByteCodeSupplier()
		);
		Optional.ofNullable(context.getSearchConfig().getClassCriteria().getClassesToBeUploaded()).ifPresent(classesToBeUploaded -> criteriaCopy.useClasses(classesToBeUploaded));
		return criteriaCopy;
	}
	
	<M extends Member, C extends MemberCriteria<M, C, T>, T extends Criteria.TestContext<M, C>> C createCriteriaCopy(C criteria) {
		C criteriaCopy = criteria.createCopy().init(
			context.getSearchConfig().getClassCriteria().getClassSupplier(),
			context.getSearchConfig().getClassCriteria().getByteCodeSupplier()
		);
		Optional.ofNullable(context.getSearchConfig().getClassCriteria().getClassesToBeUploaded()).ifPresent(classesToBeUploaded -> criteriaCopy.useClasses(classesToBeUploaded));
		return criteriaCopy;
	}
	
	@SuppressWarnings("unchecked")
	<C extends ClassLoader> C getUsedClassLoader() {
		return (C) context.pathScannerClassLoader;
	}
	
	public void waitForSearchEnding() {
		context.waitForSearchEnding();
	}
	
	public Collection<String> getSkippedClassNames() {
		return context.getSkippedClassNames();
	}
	
	@Override
	public void close() {
		context.close();
		context = null;
		classPathScanner.unregister(this);
		classPathScanner = null;
	}
}