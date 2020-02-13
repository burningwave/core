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
import java.util.function.Supplier;

import org.burningwave.core.classes.ClassCriteria;
import org.burningwave.core.classes.ClassHelper;
import org.burningwave.core.classes.JavaClass;
import org.burningwave.core.classes.MemberFinder;
import org.burningwave.core.io.FileSystemHelper;
import org.burningwave.core.io.FileSystemScanner;
import org.burningwave.core.io.FileSystemScanner.Scan;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.io.StreamHelper;

public class ByteCodeHunter extends ClassPathScannerWithCachingSupport<JavaClass, SearchContext<JavaClass>, ByteCodeHunter.SearchResult> {
	
	private ByteCodeHunter(
		Supplier<ByteCodeHunter> byteCodeHunterSupplier,
		Supplier<ClassHunter> classHunterSupplier,
		FileSystemHelper fileSystemHelper,
		FileSystemScanner fileSystemScanner,
		PathHelper pathHelper,
		StreamHelper streamHelper,
		ClassHelper classHelper,
		MemberFinder memberFinder
	) {
		super(
			byteCodeHunterSupplier,
			classHunterSupplier,
			fileSystemHelper,
			fileSystemScanner,
			pathHelper,
			streamHelper, 
			classHelper,
			memberFinder,
			(initContext) -> SearchContext.<JavaClass>create(
				fileSystemHelper, streamHelper, initContext
			),
			(context) -> new ByteCodeHunter.SearchResult(context)
		);
	}
	
	public static ByteCodeHunter create(
		Supplier<ByteCodeHunter> byteCodeHunterSupplier,
		Supplier<ClassHunter> classHunterSupplier, 
		FileSystemHelper fileSystemHelper,
		FileSystemScanner fileSystemScanner,
		PathHelper pathHelper,
		StreamHelper streamHelper,
		ClassHelper classHelper,
		MemberFinder memberFinder
	) {
		return new ByteCodeHunter(byteCodeHunterSupplier, classHunterSupplier, fileSystemHelper, fileSystemScanner,pathHelper, streamHelper, classHelper, memberFinder);
	}
	
	@Override
	<S extends SearchConfigAbst<S>> ClassCriteria.TestContext testCriteria(SearchContext<JavaClass> context, JavaClass javaClass) {
		return context.getSearchConfig().getClassCriteria().hasNoPredicate() ?
			context.getSearchConfig().getClassCriteria().testAndReturnTrueIfNullOrTrueByDefault(null) :
			super.testCriteria(context, javaClass);
	}
	
	@Override
	<S extends SearchConfigAbst<S>> ClassCriteria.TestContext testCachedItem(SearchContext<JavaClass> context, String path, String key, JavaClass javaClass) {
		return context.getSearchConfig().getClassCriteria().hasNoPredicate() ?
			context.getSearchConfig().getClassCriteria().testAndReturnTrueIfNullOrTrueByDefault(null) :				
			super.testCriteria(context, javaClass);
	}
	
	@Override
	void retrieveItemFromFileInputStream(
		SearchContext<JavaClass> context, 
		ClassCriteria.TestContext criteriaTestContext,
		Scan.ItemContext scanItemContext,
		JavaClass javaClass
	) {
		context.addItemFound(scanItemContext.getBasePathAsString(), scanItemContext.getScannedItem().getAbsolutePath(), javaClass);
	}

	
	@Override
	void retrieveItemFromZipEntry(
		SearchContext<JavaClass> context,
		ClassCriteria.TestContext criteriaTestContext,
		Scan.ItemContext scanItemContext,
		JavaClass javaClass
	) {
		context.addItemFound(scanItemContext.getBasePathAsString(), scanItemContext.getScannedItem().getAbsolutePath(), javaClass);
	}
		
	public static class SearchResult extends org.burningwave.core.classes.hunter.SearchResult<JavaClass> {

		public SearchResult(SearchContext<JavaClass> context) {
			super(context);
		}
		
		public Collection<JavaClass> getClasses() {
			return context.getItemsFound();
		}
		
		public Map<String, JavaClass> getClassesFlatMap() {
			return context.getItemsFoundFlatMap();
		}
	}
}
