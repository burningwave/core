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


import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.burningwave.core.Component;
import org.burningwave.core.classes.SearchContext.InitContext;
import org.burningwave.core.io.ClassFileScanConfig;
import org.burningwave.core.io.FileSystemHelper;
import org.burningwave.core.io.FileSystemScanner;
import org.burningwave.core.io.FileSystemScanner.Scan;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.io.StreamHelper;


abstract class ClassPathScannerAbst<I, C extends SearchContext<I>, R extends SearchResult<I>> implements Component {
	
	Supplier<ByteCodeHunter> byteCodeHunterSupplier;
	ByteCodeHunter byteCodeHunter;
	Supplier<ClassHunter> classHunterSupplier;
	ClassHunter classHunter;
	Classes.Loaders classesLoaders;
	FileSystemHelper fileSystemHelper;
	FileSystemScanner fileSystemScanner;
	StreamHelper streamHelper;
	PathHelper pathHelper;
	Function<InitContext, C> contextSupplier;
	Function<C, R> resultSupplier;

	ClassPathScannerAbst(
		Supplier<ByteCodeHunter> byteCodeHunterSupplier,
		Supplier<ClassHunter> classHunterSupplier,
		FileSystemHelper fileSystemHelper,
		FileSystemScanner fileSystemScanner,
		PathHelper pathHelper,
		StreamHelper streamHelper,
		Classes.Loaders classesLoaders,
		Function<InitContext, C> contextSupplier,
		Function<C, R> resultSupplier
	) {
		this.fileSystemHelper = fileSystemHelper;
		this.fileSystemScanner = fileSystemScanner;
		this.pathHelper = pathHelper;
		this.streamHelper = streamHelper;
		this.classesLoaders = classesLoaders;
		this.byteCodeHunterSupplier = byteCodeHunterSupplier;
		this.classHunterSupplier = classHunterSupplier;
		this.contextSupplier = contextSupplier;
		this.resultSupplier = resultSupplier;
	}
	
	ClassHunter getClassHunter() {
		return classHunter != null ?
			classHunter	:
			(classHunter = classHunterSupplier.get());
	}
	
	
	ByteCodeHunter getByteCodeHunter() {
		return byteCodeHunter != null ?
			byteCodeHunter :
			(byteCodeHunter = byteCodeHunterSupplier.get());	
	}
	
	//Not cached search
	public R findBy(ClassFileScanConfig scanConfig, SearchConfig searchConfig) {
		final ClassFileScanConfig scanConfigCopy = scanConfig.createCopy();
		searchConfig = searchConfig.createCopy();
		C context = createContext(scanConfigCopy, searchConfig);
		searchConfig.init(this.classesLoaders, context.pathMemoryClassLoader);
		context.executeSearch(() -> {
			fileSystemScanner.scan(
				scanConfigCopy.toScanConfiguration(
					getFileSystemEntryTransformer(context),
					getZipEntryTransformer(context)
				)
			);
		});
		Collection<String> skippedClassesNames = context.getSkippedClassNames();
		if (!skippedClassesNames.isEmpty()) {
			logWarn("Skipped classes count: {}", skippedClassesNames.size());
		}
		return resultSupplier.apply(context);
	}
	
	C createContext(ClassFileScanConfig scanConfig, SearchConfigAbst<?> searchConfig) {
		PathMemoryClassLoader sharedClassLoader = getClassHunter().pathMemoryClassLoader;
		if (searchConfig.useSharedClassLoaderAsParent) {
			searchConfig.parentClassLoaderForMainClassLoader = sharedClassLoader;
		}
		C context = contextSupplier.apply(
			InitContext.create(
				sharedClassLoader,
				searchConfig.useSharedClassLoaderAsMain ?
					sharedClassLoader :
					PathMemoryClassLoader.create(
						searchConfig.parentClassLoaderForMainClassLoader, 
						pathHelper, classesLoaders, byteCodeHunterSupplier
					),
				scanConfig,
				searchConfig
			)		
		);
		return context;
	}

	
	Consumer<Scan.ItemContext> getFileSystemEntryTransformer(
		C context
	) {
		return (scannedItemContext) -> {
			JavaClass javaClass = JavaClass.create(scannedItemContext.getScannedItem().toByteBuffer());
			ClassCriteria.TestContext criteriaTestContext = testCriteria(context, javaClass);
			if (criteriaTestContext.getResult()) {
				retrieveItemFromFileInputStream(
					context, criteriaTestContext, scannedItemContext, javaClass
				);
			}
		};
	}
	
	
	Consumer<Scan.ItemContext> getZipEntryTransformer(
		C context
	) {
		return (scannedItemContext) -> {
			JavaClass javaClass = JavaClass.create(scannedItemContext.getScannedItem().toByteBuffer());
			ClassCriteria.TestContext criteriaTestContext = testCriteria(context, javaClass);
			if (criteriaTestContext.getResult()) {
				retrieveItemFromZipEntry(
					context, criteriaTestContext, scannedItemContext, javaClass
				);
			}
		};
	}
	
	<S extends SearchConfigAbst<S>> ClassCriteria.TestContext testCriteria(C context, JavaClass javaClass) {
		return context.testCriteria(context.loadClass(javaClass.getName()));
	}
		
	abstract void retrieveItemFromFileInputStream(C Context,ClassCriteria.TestContext criteriaTestContext, Scan.ItemContext scannedItem, JavaClass javaClass);
	
	
	abstract void retrieveItemFromZipEntry(C Context, ClassCriteria.TestContext criteriaTestContext, Scan.ItemContext zipEntry, JavaClass javaClass);
	
	
	@Override
	public void close() {
		byteCodeHunterSupplier = null;
		classesLoaders = null;
		fileSystemHelper = null;
		streamHelper = null;
		pathHelper = null;
		contextSupplier = null;
	}
}