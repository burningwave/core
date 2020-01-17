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


import java.io.File;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.burningwave.core.classes.ClassCriteria;
import org.burningwave.core.classes.ClassHelper;
import org.burningwave.core.classes.JavaClass;
import org.burningwave.core.classes.MemberFinder;
import org.burningwave.core.common.Strings;
import org.burningwave.core.concurrent.ParallelTasksManager;
import org.burningwave.core.function.ThrowingRunnable;
import org.burningwave.core.io.ByteBufferInputStream;
import org.burningwave.core.io.FileInputStream;
import org.burningwave.core.io.FileOutputStream;
import org.burningwave.core.io.FileSystemHelper;
import org.burningwave.core.io.FileSystemHelper.Scan;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.io.StreamHelper;
import org.burningwave.core.io.Streams;
import org.burningwave.core.io.ZipInputStream;

public class ClassPathHunter extends ClassPathScannerWithCachingSupport<File, Collection<Class<?>>, ClassPathHunter.SearchContext, ClassPathHunter.SearchResult> {
	
	private ClassPathHunter(
		Supplier<ByteCodeHunter> byteCodeHunterSupplier,
		Supplier<ClassHunter> classHunterSupplier,
		FileSystemHelper fileSystemHelper, 
		PathHelper pathHelper,
		StreamHelper streamHelper,
		ClassHelper classHelper,
		MemberFinder memberFinder
	) {
		super(
			byteCodeHunterSupplier,
			classHunterSupplier,
			fileSystemHelper,
			pathHelper,
			streamHelper,
			classHelper,
			memberFinder,
			(initContext) -> SearchContext._create(fileSystemHelper, streamHelper, initContext),
			(context) -> new ClassPathHunter.SearchResult(context)
		);
	}
	
	public static ClassPathHunter create(Supplier<ByteCodeHunter> byteCodeHunterSupplier, Supplier<ClassHunter> classHunterSupplier, FileSystemHelper fileSystemHelper, PathHelper pathHelper, StreamHelper streamHelper,
		ClassHelper classHelper, MemberFinder memberFinder
	) {
		return new ClassPathHunter(byteCodeHunterSupplier, classHunterSupplier, fileSystemHelper, pathHelper, streamHelper, classHelper, memberFinder);
	}
	
	@Override
	void loadCache(SearchContext context, Collection<String> paths) {
		context.deleteTemporaryFiles(false);
		super.loadCache(context, paths);		
	}
	
	@Override
	<S extends SearchConfigAbst<S>> void iterateAndTestItemsForPath(SearchContext context, String path, Map<File, Collection<Class<?>>> itemsForPath) {
		for (Entry<Class<?>, File> cachedItemAsEntry : itemsForPath.entrySet()) {
			ClassCriteria.TestContext testContext = testCachedItem(context, path, cachedItemAsEntry.getKey(), cachedItemAsEntry.getValue());
			if(testContext.getResult()) {
				addCachedItemToContext(context, testContext, path, cachedItemAsEntry);
				break;
			}
		}
	}
	
	@Override
	<S extends SearchConfigAbst<S>> ClassCriteria.TestContext testCachedItem(SearchContext context, String path, File file, Collection<Class<?>> cls) {
		return context.testCriteria(context.retrieveClass(cls));
	}
	
	@Override
	void retrieveItemFromFileInputStream(
		SearchContext context, 
		ClassCriteria.TestContext criteriaTestContext,
		Scan.ItemContext<FileInputStream> scanItemContext,
		JavaClass javaClass
	) {
		String classPath = Strings.Paths.uniform(scanItemContext.getInput().getFile().getAbsolutePath());
		classPath = classPath.substring(
			0, classPath.lastIndexOf(
				javaClass.getPath(), classPath.length() -1
			)
		-1);	
		File classPathAsFile = new File(classPath);
		context.addItemFound(
			scanItemContext.getBasePathAsString(),
			classPathAsFile,
			context.loadClass(javaClass.getName())
		);
	}

	@Override
	void retrieveItemFromZipEntry(
		SearchContext context,
		ClassCriteria.TestContext criteriaTestContext,
		Scan.ItemContext<ZipInputStream.Entry> scanItemContext,
		JavaClass javaClass
	) {
		File fsObject = null;
		ZipInputStream.Entry zipEntry = scanItemContext.getInput();
		if (zipEntry.getName().equals(javaClass.getPath())) {
			fsObject = new File(zipEntry.getZipInputStream().getAbsolutePath());
			if (!fsObject.exists()) {
				fsObject = extractLibrary(context, zipEntry);
			}
			if (!context.getSearchConfig().getClassCriteria().hasNoPredicate()) {
				scanItemContext.getParent().setDirective(Scan.Directive.STOP_ITERATION);
			}
		} else {
			fsObject = extractClass(context, zipEntry, javaClass);
		}
		context.addItemFound(scanItemContext.getBasePathAsString(), fsObject, context.loadClass(javaClass.getName()));
	}
	
	File extractClass(ClassPathHunter.SearchContext context, ZipInputStream.Entry zipEntry, JavaClass javaClass) {
		String libName = Strings.Paths.uniform(zipEntry.getZipInputStream().getAbsolutePath());
		libName = libName.substring(libName.lastIndexOf("/", libName.length()-2)+1, libName.lastIndexOf("/"));
		return copyToTemporaryFolder(
			context, zipEntry.toByteBuffer(),
			getTemporaryFolderPrefix() + "_classes", libName, javaClass.getPackagePath(), javaClass.getClassName() + ".class"
		);
	}

	
	File extractLibrary(ClassPathHunter.SearchContext context, ZipInputStream.Entry zipEntry) {
		String libName = Strings.Paths.uniform(zipEntry.getZipInputStream().getAbsolutePath());
		libName = libName.substring(libName.lastIndexOf("/", libName.length()-2)+1, libName.lastIndexOf("/"));
		return copyToTemporaryFolder(
			context, zipEntry.getZipInputStream().toByteBuffer(), 
			getTemporaryFolderPrefix() + "_lib", null, null, libName
		);
	}
	
	
	File getMainFolder(ClassPathHunter.SearchContext context, String folderName) {
		File mainFolder = context.temporaryFiles.stream().filter((file) -> 
			file.getName().contains(folderName)
		).findFirst().orElse(null);
		if (mainFolder == null) {
			mainFolder = fileSystemHelper.createTemporaryFolder(folderName);
			context.temporaryFiles.add(mainFolder);
		}
		return mainFolder;
	}
	
	File copyToTemporaryFolder(ClassPathHunter.SearchContext context, ByteBuffer buffer, String mainFolderName, String libName, String packageFolders, String fileName) {
		File toRet = getMainFolder(context, mainFolderName);
		if (libName != null && !libName.isEmpty()) {
			toRet = new File(toRet.getAbsolutePath(), libName);
			toRet.mkdirs();
		}
		File destinationFilePath = toRet;
		if (packageFolders != null && !packageFolders.isEmpty()) {
			destinationFilePath = new File(toRet.getAbsolutePath(), packageFolders + "/");
			destinationFilePath.mkdirs();
		}
		File destinationFile =  new File(destinationFilePath.getAbsolutePath() + "/" + fileName);
		if (!destinationFile.exists()) {
			ThrowingRunnable.run(() -> destinationFile.createNewFile());
			context.tasksManager.addTask(() -> {
				try (FileOutputStream output = FileOutputStream.create(destinationFile, true)){
					Streams.copy(new ByteBufferInputStream(buffer), output);
				}
			});
		}
		if (libName == null || libName.isEmpty()) {
			toRet = destinationFile;
		}
		return toRet;
	}
	
	@Override
	public void close() {
		fileSystemHelper = null;
		super.close();
	}
	
	public static class SearchContext extends org.burningwave.core.classes.hunter.SearchContext<File, Collection<Class<?>>> {
		ParallelTasksManager tasksManager;
		Collection<File> temporaryFiles;
		boolean deleteTemporaryFilesOnClose;
		
		SearchContext(FileSystemHelper fileSystemHelper, StreamHelper streamHelper, InitContext initContext) {
			super(fileSystemHelper, streamHelper, initContext);
			this.temporaryFiles = ConcurrentHashMap.newKeySet();
			ClassFileScanConfig scanConfig = initContext.getClassFileScanConfiguration();
			this.tasksManager = ParallelTasksManager.create(scanConfig.maxParallelTasksForUnit);
			deleteTemporaryFilesOnClose = getSearchConfig().deleteFoundItemsOnClose;
		}		
		
		public void addItemFound(String basePathAsString, File classPathAsFile, Class<?> testedClass) {
			Map<File, Collection<Class<?>>> testedClassesForClassPathMap = retrieveCollectionForPath(
				itemsFoundMap,
				ConcurrentHashMap::new, basePathAsString
			);
			Collection<Class<?>> testedClassesForClassPath = testedClassesForClassPathMap.get(classPathAsFile);
			if (testedClassesForClassPath == null) {
				synchronized (testedClassesForClassPathMap) {
					testedClassesForClassPath = testedClassesForClassPathMap.get(classPathAsFile);
					if (testedClassesForClassPath == null) {
						testedClassesForClassPathMap.put(classPathAsFile, testedClassesForClassPath = ConcurrentHashMap.newKeySet());
					}
				}
			}
			testedClassesForClassPath.add(testedClass);
		}

		void deleteTemporaryFiles(boolean value) {
			deleteTemporaryFilesOnClose = value;			
		}

		static SearchContext _create(FileSystemHelper fileSystemHelper, StreamHelper streamHelper, InitContext initContext) {
			return new SearchContext(fileSystemHelper, streamHelper,  initContext);
		}
		
		@Override
		public void close() {
			if (deleteTemporaryFilesOnClose) {
				itemsFoundFlatMap.values().removeAll(temporaryFiles);
				fileSystemHelper.deleteTempraryFiles(temporaryFiles);
			}
			temporaryFiles = null;
			tasksManager.close();
			super.close();
		}
	}
	
	public static class SearchResult extends org.burningwave.core.classes.hunter.SearchResult<File, Collection<Class<?>>> {

		public SearchResult(SearchContext context) {
			super(context);
		}
		
	}
}
