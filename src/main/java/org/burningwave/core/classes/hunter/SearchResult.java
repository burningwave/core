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

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.burningwave.Throwables;
import org.burningwave.core.Component;
import org.burningwave.core.Criteria;

public class SearchResult<E> implements Component {
	protected SearchContext<E> context;
	
	SearchResult(SearchContext<E> context) {
		this.context = context;
	}
	
	protected Collection<E> getItemsFound() {
		return context.getItemsFound();
	}
	
	protected Map<String, E> getItemsFoundFlatMap() {
		return context.getItemsFoundFlatMap();
	}
	
	public <C extends Criteria<E, C, T>, T extends Criteria.TestContext<E, C>> Map<String, E> getClasses(C criteria) {
		Map<String, E> itemsFound = new ConcurrentHashMap<>();
		final C criteriaCopy = createCriteriaCopy(criteria);
		getItemsFoundFlatMap().forEach((path, javaClass) -> {
			if (criteriaCopy.testAndReturnFalseIfNullOrTrueByDefault(javaClass).getResult()) {
				itemsFound.put(path, javaClass);
			}
		});
		return itemsFound;
	}
	
	public <C extends Criteria<E, C, T>, T extends Criteria.TestContext<E, C>> Map.Entry<String, E> getUnique(C criteria) {
		Map<String, E> itemsFound = new ConcurrentHashMap<>();
		final C criteriaCopy = createCriteriaCopy(criteria);
		getItemsFoundFlatMap().forEach((path, javaClass) -> {
			if (criteriaCopy.testAndReturnFalseIfNullOrTrueByDefault(javaClass).getResult()) {
				itemsFound.put(path, javaClass);
			}
		});
		if (itemsFound.size() > 1) {
			throw Throwables.toRuntimeException("Found more than one element");
		}
		return itemsFound.entrySet().stream().findFirst().orElseGet(() -> null);
	}
	
	protected <C extends Criteria<E, C, T>, T extends Criteria.TestContext<E, C>> C createCriteriaCopy(C criteria) {
		return criteria.createCopy();
	}
	
	@SuppressWarnings("unchecked")
	<C extends ClassLoader> C getUsedClassLoader() {
		return (C) context.pathMemoryClassLoader;
	}
	
	public void waitForSearchEnding() {
		context.waitForSearchEnding();
	}
	
	@Override
	public void close() {
		context.close();
		context = null;
	}
}