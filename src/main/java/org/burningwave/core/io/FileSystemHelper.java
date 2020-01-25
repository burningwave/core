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
package org.burningwave.core.io;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.burningwave.Throwables;
import org.burningwave.core.Component;
import org.burningwave.core.common.Strings;
import org.burningwave.core.concurrent.ParallelTasksManager;
import org.burningwave.core.function.ThrowingRunnable;
import org.burningwave.core.function.ThrowingSupplier;
import org.burningwave.core.io.FileSystemHelper.Scan.Configuration;
import org.burningwave.core.io.FileSystemHelper.Scan.Directive;
import org.burningwave.core.io.FileSystemHelper.Scan.ItemContext;


public class FileSystemHelper implements Component {
	private Supplier<PathHelper> pathHelperSupplier; 
	private PathHelper pathHelper;
	private Collection<File> temporaryFiles;
	private File baseTempFolder;
	
	private FileSystemHelper(Supplier<PathHelper> pathHelperSupplier) {
		ThrowingRunnable.run(() ->{
			File toDelete = File.createTempFile("_BW_TEMP_", "_temp");
			File tempFolder = toDelete.getParentFile();
			baseTempFolder = new File(tempFolder.getAbsolutePath() + "/Burningwave");
			if (!baseTempFolder.exists()) {
				baseTempFolder.mkdirs();
			}
			toDelete.delete();
		});
		this.pathHelperSupplier = pathHelperSupplier;
		this.temporaryFiles = new CopyOnWriteArrayList<File>();
	}
	
	public static FileSystemHelper create(Supplier<PathHelper> pathHelperSupplier) {
		return new FileSystemHelper(pathHelperSupplier);
	}
	
	private PathHelper getPathHelper() {
		return pathHelper != null ?
			pathHelper :
			(pathHelper = pathHelperSupplier.get());
	}
	
	public Collection<FileSystemItem> getResources(String... resourcesRelativePaths) {
		return getResources((coll, file) -> coll.add(file), resourcesRelativePaths);
	}
	
	public FileSystemItem getResource(String resourceRelativePath) {
		return getResource(
				(coll, file) -> 
					coll.add(file), resourceRelativePath);
	}
	
	
	public <T> Collection<T> getResources(
		BiConsumer<Collection<T>, FileSystemItem> fileConsumer,
		String... resourcesRelativePaths
	) {
		Collection<T> files = new ArrayList<>();
		if (resourcesRelativePaths != null && resourcesRelativePaths.length > 0) {
			PathHelper pathHelper = pathHelperSupplier.get();
			FileSystemItem.disableLog();
			for (String resourceRelativePath : resourcesRelativePaths) {
				pathHelper.getAllClassPaths().stream().forEach((path) -> {
					FileSystemItem fileSystemItem = FileSystemItem.ofPath(path + "/" + resourceRelativePath);
					if (fileSystemItem.exists()) {
						fileConsumer.accept(files, fileSystemItem);
					}
				});
			}
			FileSystemItem.enableLog();
		}
		return files;
	}
	
	
	public <T> T getResource(BiConsumer<Collection<T>, FileSystemItem> fileConsumer, String resourceRelativePath) {
		Collection<T> files = getResources(fileConsumer, resourceRelativePath);
		if (files.size() > 1) {
			throw Throwables.toRuntimeException("Found more than one resource under relative path " + resourceRelativePath);
		}
		return files.stream().findFirst().orElse(null);
	}
	
	public File createTemporaryFolder(String folderName) {
		return ThrowingSupplier.get(() -> {
			File tempFolder = new File(baseTempFolder.getAbsolutePath() + "/" + folderName);
			if (!tempFolder.exists()) {
				tempFolder.mkdirs();
			}
			temporaryFiles.add(tempFolder);
			return tempFolder;
		});
	}
	

	public void deleteTempraryFiles(Collection<File> temporaryFiles) {
		deleteFiles(temporaryFiles);
		temporaryFiles.removeAll(temporaryFiles);
	}
	
	public void deleteFiles(Collection<File> files) {
		if (files != null) {
			Iterator<File> itr = files.iterator();
			while(itr.hasNext()) {
				File tempFile = (File)itr.next();
				if (tempFile.exists()) {
					delete(tempFile);
					itr.remove();
				}
			}
		}
	}
	
	public boolean delete(File file) {
		if (file.isDirectory()) {
			return deleteFolder(file);
		} else {
			return file.delete();
		}
	}

	public boolean deleteFolder(File folder) {
	    File[] files = folder.listFiles();
	    if(files!=null) { //some JVMs return null for empty dirs
	        for(File f: files) {
	            if(f.isDirectory()) {
	                deleteFolder(f);
	            } else {
	                f.delete();
	            }
	        }
	    }
	    return folder.delete();
	}
	
	
	public void scan(Configuration configuration) {
		try (Scan.MainContext context = Scan.MainContext.create(this, configuration.createCopy())) {
			if (context.configuration.optimizePaths) {
				getPathHelper().optimize(context.configuration.paths);
			}
			Optional.ofNullable(configuration.beforeScan).ifPresent(consumer -> consumer.accept(context));
			for (String path : context.configuration.paths) {
				Optional.ofNullable(configuration.beforeScanPath).ifPresent(consumer -> consumer.accept(context, path));
				scan(
					new ItemContext<File>(
						context, path
					)
				);
				Optional.ofNullable(configuration.afterScanPath).ifPresent(consumer -> consumer.accept(context, path));
				if (context.directive == Directive.STOP_ITERATION) {
	        		break;
	        	}
			}
			Optional.ofNullable(configuration.afterScan).ifPresent(consumer -> consumer.accept(context));
		}
	}
	

	void scan(ItemContext<File> scannedItemContext) {
		File basePath = scannedItemContext.basePath;
		File currentPath = scannedItemContext.input;
		Scan.MainContext mainContext = scannedItemContext.mainContext;
		Configuration configuration = mainContext.configuration;
		if (currentPath.isDirectory()) {
			for (Entry<BiPredicate<File, File>, Consumer<ItemContext<File>>> entry : configuration.filterAndMapperForDirectory.entrySet()) {
				if (entry.getKey().test(basePath, currentPath)) {
					entry.getValue().accept(
						new ItemContext<File>(
							scannedItemContext, currentPath
						)
					);	
				}
			}
	    } else {
	    	mainContext.tasksManager.addTask(() -> {
	    		for (Entry<Predicate<File>, Consumer<ItemContext<FileInputStream>>> entry : configuration.filterAndMapperForFile.entrySet()) {
	    			if (entry.getKey().test(currentPath)) {
	    				try (FileInputStream fileInputStream = FileInputStream.create(currentPath)) {	    						
	    					entry.getValue().accept(
	    						new ItemContext<FileInputStream>(
	    							scannedItemContext, fileInputStream
	    						)
	    					);    		
    					} 
	    			}
	    		}
	    	});		
	    }    
	}
	
	<K, T> void scanDirectory(ItemContext<File> scanItemContext){
		File currentPath = scanItemContext.input;
		File[] files = currentPath.listFiles();
		if (files != null) {
			for (File fsObj : files) { 
	        	logDebug("scanning file system item " + fsObj.getAbsolutePath());
				scan(
					new ItemContext<File>(
						scanItemContext, fsObj
					)
				);
	        	if (scanItemContext.directive == Directive.STOP_ITERATION) {
	        		break;
	        	}
	        }
		}	
	}
	
	<K, T> void scanZipFile(ItemContext<FileInputStream> scanItemContext){
		File currentFile = scanItemContext.input.getFile();
		try (ZipInputStream zipInputStream = ZipInputStream.create(scanItemContext.input)) {
			logDebug("scanning zip file " + zipInputStream.getAbsolutePath());      
			scanZipInputStream(new ItemContext<ZipInputStream>(scanItemContext, zipInputStream));
		} catch (Throwable exc) {
			logError("Could not scan zip file " + Strings.Paths.clean(currentFile.getAbsolutePath()), exc);
		}
	}
	
	<K, T> void scanZipEntry(ItemContext<ZipInputStream.Entry> scanItemContext){
		ZipInputStream.Entry zipEntry = scanItemContext.input;
		try (ZipInputStream zipInputStream = ZipInputStream.create(zipEntry)) {
			scanZipInputStream(new ItemContext<ZipInputStream>(scanItemContext, zipInputStream));
		}
	}
	
	<K, T> void scanZipInputStream(ItemContext<ZipInputStream> currentScannedItemContext){
		ZipInputStream currentZip = currentScannedItemContext.input;
		Scan.MainContext mainContext = currentScannedItemContext.mainContext;
		Configuration configuration = mainContext.configuration;
		while(currentZip.getNextEntry() != null) {
			ZipInputStream.Entry zipEntry = currentZip.getCurrentZipEntry();
			for (Entry<Predicate<org.burningwave.core.io.ZipInputStream.Entry>, Consumer<ItemContext<ZipInputStream.Entry>>> entry : configuration.filterAndMapperForZipEntry.entrySet()) {
				if (entry.getKey().test(zipEntry)) {
					try {
						logDebug("scanning zip entry " + zipEntry.getAbsolutePath());
						entry.getValue().accept(new ItemContext<ZipInputStream.Entry>(currentScannedItemContext, zipEntry));
					} catch (Throwable exc) {
						logError("Could not scan zip entry " + Strings.Paths.clean(zipEntry.getAbsolutePath()), exc);
					}
				}				
			}
			if (currentScannedItemContext.directive == Directive.STOP_ITERATION) {
        		break;
        	}
		}
	}
	
	
	public static class Scan {
		
		public static enum Directive {
			CONTINUE, STOP_ITERATION
		}
		
		public static class ItemContext<I> {
			final FileSystemHelper fileSystemHelper;
			final MainContext mainContext;
			final ItemContext<?> parent;
			final String basePathAsString;
			final File basePath;
			final I input;		
			Directive directive;			
			
			@SuppressWarnings("unchecked")
			public ItemContext(MainContext context, String path) {
				this.parent = null;
				this.mainContext = context;
				this.fileSystemHelper = mainContext.fileSystemHelper;
				this.basePathAsString = path;
				this.basePath = new File(this.basePathAsString);
				this.input = (I)basePath;
				directive = Directive.CONTINUE;
			}
			
			ItemContext(ItemContext<?> parent, I input) {
				this.parent = parent;
				this.mainContext = this.parent.mainContext;
				this.fileSystemHelper = mainContext.fileSystemHelper;
				this.input = input;
				this.basePathAsString = parent.basePathAsString;
				this.basePath = new File(this.basePathAsString);
				directive = Directive.CONTINUE;
			}
			
			public String getBasePathAsString() {
				return basePathAsString;
			}

			public File getBasePath() {
				return basePath;
			}
			
			public I getInput() {
				return input;
			}
			
			@SuppressWarnings({"unchecked" })
			public <C extends ItemContext<?>> C getParent() {
				return (C)parent;
			}
			
			public MainContext getMainContext() {
				return mainContext;
			}
			
			public Directive getDirective() {
				return directive;
			}
			
			public void setDirective(Directive directive) {
				this.directive = directive;
			}
		}
		
		public static class MainContext implements Component {
			final FileSystemHelper fileSystemHelper;
			final ParallelTasksManager tasksManager;
			final Configuration configuration;
			Directive directive;
			
			private MainContext(FileSystemHelper fileSystemHelper, Configuration configuration) {
				this.configuration = configuration;
				this.fileSystemHelper = fileSystemHelper;
				tasksManager = ParallelTasksManager.create(configuration.maxParallelTasks);
				directive = Directive.CONTINUE;
			}
			
			static MainContext create(FileSystemHelper fileSystemHelper, Configuration configuration) {
				return new MainContext(fileSystemHelper, configuration);
			}		
			
			public FileSystemHelper getFileSystemHelper() {
				return this.fileSystemHelper;
			}
			
			public Directive getDirective() {
				return directive;
			}
			
			public void setDirective(Directive directive) {
				this.directive = directive;
			}
			
			public void waitForTasksEnding() {
				tasksManager.waitForTasksEnding();
			}
			
			@Override
			public void close() {
				tasksManager.close();
				configuration.close();
				directive = null;
			}
		}

		
		public static class Configuration implements Component {
			private Collection<String> paths;
			private Consumer<MainContext> beforeScan;
			private Consumer<MainContext> afterScan;
			private BiConsumer<MainContext, String> beforeScanPath;
			private BiConsumer<MainContext, String> afterScanPath;
			private Map<BiPredicate<File, File>, Consumer<ItemContext<File>>> filterAndMapperForDirectory;
			private Map<Predicate<File>, Consumer<ItemContext<FileInputStream>>> filterAndMapperForFile;
			private Map<Predicate<ZipInputStream.Entry>, Consumer<ItemContext<ZipInputStream.Entry>>> filterAndMapperForZipEntry;
			private boolean optimizePaths;
			private int maxParallelTasks;

			private Configuration() {
				maxParallelTasks = Runtime.getRuntime().availableProcessors();
				filterAndMapperForDirectory = new ConcurrentHashMap<>(); 
				filterAndMapperForFile = new ConcurrentHashMap<>();
				filterAndMapperForZipEntry = new ConcurrentHashMap<>();
				paths = new CopyOnWriteArrayList<>();
			}

			public static Configuration create() {
				return new Configuration();	
			}
			
			@SuppressWarnings("resource")
			public static Configuration forPaths(Collection<String> paths) {
				return new Configuration().addPaths(paths);
			}
			
			@SuppressWarnings("resource")
			public static Configuration forPaths(String... paths) {
				return new Configuration().addPaths(Arrays.asList(paths));
			}
			
			public Collection<String> getPaths() {
				return paths;
			}
			
			public Configuration addPaths(Collection<String> paths) {
				this.paths.addAll(paths);
				return this;
			}
			
			@SafeVarargs
			public final Configuration afterScan(Consumer<MainContext>... consumers) {
				for (Consumer<MainContext> consumer : consumers) {
					if (afterScan != null) {
						afterScan.andThen(consumer);
					} else {
						afterScan = consumer;
					}
				}
				return this;
			}
			
			@SafeVarargs
			public final Configuration beforeScan(Consumer<MainContext>... consumers) {
				for (Consumer<MainContext> consumer : consumers) {
					if (beforeScan != null) {
						beforeScan.andThen(consumer);
					} else {
						beforeScan = consumer;
					}
				}
				return this;
			}
			
			@SafeVarargs
			public final Configuration afterScanPath(BiConsumer<MainContext, String>... consumers) {
				for (BiConsumer<MainContext, String> consumer : consumers) {
					if (afterScanPath != null) {
						afterScanPath.andThen(consumer);
					} else {
						afterScanPath = consumer;
					}
				}
				return this;
			}

			@SafeVarargs
			public final Configuration beforeScanPath(BiConsumer<MainContext, String>... consumers) {
				for (BiConsumer<MainContext, String> consumer : consumers) {
					if (beforeScanPath != null) {
						beforeScanPath.andThen(consumer);
					} else {
						beforeScanPath = consumer;
					}
				}
				return this;
			}
			
			public Configuration scanRecursivelyAllDirectoryAndApplyBefore(Consumer<ItemContext<File>> before) {
				return putInFilterAndConsumerMap(filterAndMapperForDirectory, (basePath, currentPath) -> true,
					before.andThen(
						scanItemContext -> {
							scanItemContext.fileSystemHelper.scanDirectory(scanItemContext);
						}
					)
				);
			}
			
			public Configuration scanRecursivelyAllDirectoryThatAndApplyBefore(
				BiPredicate<File, File> predicate,
				Consumer<ItemContext<File>> before
			) {
				return putInFilterAndConsumerMap(filterAndMapperForDirectory, predicate,
					before.andThen(
						scanItemContext -> {
							scanItemContext.fileSystemHelper.scanDirectory(scanItemContext);
						}
					)
				);				
			}
			
			public Configuration scanRecursivelyAllDirectoryThatAndApply(
				BiPredicate<File, File> predicate,
				Consumer<ItemContext<File>> before,
				Consumer<ItemContext<File>> after
			) {
				return putInFilterAndConsumerMap(filterAndMapperForDirectory, predicate,
					before.andThen(
						scanItemContext -> {
							scanItemContext.fileSystemHelper.scanDirectory(scanItemContext);
						}
					).andThen(after)
				);					
			}
			
			public Configuration scanRecursivelyAllDirectoryThat(BiPredicate<File, File> predicate) {
				return putInFilterAndConsumerMap(
					filterAndMapperForDirectory, predicate, 
					scanItemContext -> scanItemContext.fileSystemHelper.scanDirectory(scanItemContext)
				);
			}
			
			public Configuration scanRecursivelyAllDirectory() {
				return putInFilterAndConsumerMap(
					filterAndMapperForDirectory, (basePath, currentPath) -> true, 
					scanItemContext -> scanItemContext.fileSystemHelper.scanDirectory(scanItemContext)
				);
			}
			
			public Configuration scanStrictlyDirectoryAndApplyBefore(Consumer<ItemContext<File>> before) {
				return putInFilterAndConsumerMap(
					filterAndMapperForDirectory,
					(basePath, currentPath) -> basePath.equals(currentPath), 
					before.andThen(
						scanItemContext -> {
							scanItemContext.fileSystemHelper.scanDirectory(scanItemContext);
						}
					)
				);
			}
			
			public Configuration scanStrictlyDirectoryAndApplyAfter(Consumer<ItemContext<File>> after) {
				return putInFilterAndConsumerMap(
					filterAndMapperForDirectory,
					(basePath, currentPath) -> basePath.equals(currentPath), 
					((Consumer<ItemContext<File>>)scanItemContext -> {
						scanItemContext.fileSystemHelper.scanDirectory(scanItemContext);
					}).andThen(after)
				);
			}
			
			public Configuration scanStrictlyDirectoryAndApply(Consumer<ItemContext<File>> before, Consumer<ItemContext<File>> after) {
				return putInFilterAndConsumerMap(
					filterAndMapperForDirectory,
					(basePath, currentPath) -> basePath.equals(currentPath), 
					before.andThen(
						scanItemContext -> {
							scanItemContext.fileSystemHelper.scanDirectory(scanItemContext);
						}
					).andThen(after)
				);
			}
			
			public Configuration scanStrictlyDirectory() {
				return putInFilterAndConsumerMap(
					filterAndMapperForDirectory, (basePath, currentPath) -> basePath.equals(currentPath), 
					scanItemContext -> scanItemContext.fileSystemHelper.scanDirectory(scanItemContext)
				);
			}
			
			
			public final Configuration whenFindFileTestAndApply(
				Predicate<File> predicate, 
				Consumer<ItemContext<FileInputStream>> fileSystemEntryAnalyzer
			) {
				return putInFilterAndConsumerMap(filterAndMapperForFile, predicate, fileSystemEntryAnalyzer);
			}
			
			public final Configuration scanAllZipFileThatAndApplyBefore(Predicate<File> predicate, Consumer<ItemContext<FileInputStream>> before) {
				return putInFilterAndConsumerMap(
					filterAndMapperForFile, predicate,
					before.andThen(
						scanItemContext -> {
							scanItemContext.fileSystemHelper.scanZipFile(scanItemContext);
						}
					)
				);
			}
			
			public final Configuration scanAllZipFileThatAndApply(
				Predicate<File> predicate,
				Consumer<ItemContext<FileInputStream>> before,
				Consumer<ItemContext<FileInputStream>> after
			) {
				return putInFilterAndConsumerMap(
					filterAndMapperForFile, predicate, 
					before.andThen(
						scanItemContext -> {
							scanItemContext.fileSystemHelper.scanZipFile(scanItemContext);
						}
					).andThen(after)
				);
			}

			
			public final Configuration scanAllZipFileThat(Predicate<File> predicate) {
				return putInFilterAndConsumerMap(
					filterAndMapperForFile, predicate, 
					scanItemContext -> {
						scanItemContext.fileSystemHelper.scanZipFile(scanItemContext);
					}
				);
			}
			
			
			public final Configuration whenFindZipEntryTestAndApply(
				Predicate<ZipInputStream.Entry> predicate,
				Consumer<ItemContext<ZipInputStream.Entry>> zipEntryAnalyzers
			) {
				return putInFilterAndConsumerMap(filterAndMapperForZipEntry, predicate, zipEntryAnalyzers);
			}
			
			public final Configuration whenFindZipEntryApply(
				Consumer<ItemContext<ZipInputStream.Entry>> zipEntryAnalyzers
			) {
				return putInFilterAndConsumerMap(filterAndMapperForZipEntry, file -> true, zipEntryAnalyzers);
			}
			
			public Configuration scanRecursivelyAllZipEntryThat(Predicate<ZipInputStream.Entry> predicate) {
				return putInFilterAndConsumerMap(
					filterAndMapperForZipEntry, predicate, 
					scanItemContext -> {
						scanItemContext.fileSystemHelper.scanZipEntry(scanItemContext);
					}
				);
			}
			
			public Configuration scanRecursivelyAllZipEntryThatAndApplyBefore(
				Predicate<ZipInputStream.Entry> predicate,
				Consumer<ItemContext<ZipInputStream.Entry>> before
			) {
				return putInFilterAndConsumerMap(
					filterAndMapperForZipEntry, predicate,
					before.andThen(
						scanItemContext -> {
							scanItemContext.fileSystemHelper.scanZipEntry(scanItemContext);
						}
					)
				);
			}
			
			public Configuration scanRecursivelyAllZipEntryThatAndApply(
				Predicate<ZipInputStream.Entry> predicate,
				Consumer<ItemContext<ZipInputStream.Entry>> before,
				Consumer<ItemContext<ZipInputStream.Entry>> after
			) {
				return putInFilterAndConsumerMap(
					filterAndMapperForZipEntry, predicate,
					before.andThen(
						scanItemContext -> {
							scanItemContext.fileSystemHelper.scanZipEntry(scanItemContext);
						}
					).andThen(after)
				);
			}
		
			@SafeVarargs
			private final <O, P> Configuration putInFilterAndConsumerMap(
				Map<O, Consumer<ItemContext<P>>> map,
				O predicate,
				Consumer<ItemContext<P>>... analyzers
			) {
				synchronized (map) {
					Consumer<ItemContext<P>> analyzer = map.get(predicate);
					for (Consumer<ItemContext<P>> consumer : analyzers) {
						if (analyzer != null) {
							analyzer = analyzer.andThen(consumer);
						} else {
							analyzer = consumer;
						}
					}
					map.put(predicate, analyzer);
				}	
				return this;
			}			
			
			public Configuration setMaxParallelTasks(int value) {
				this.maxParallelTasks = value;
				return this;
			}
			
			public Configuration optimizePaths(boolean flag) {
				this.optimizePaths = flag;
				return this;
			}
			
			public Configuration createCopy() {
				Configuration copy = Configuration.forPaths(this.paths);
				copy.beforeScan = this.beforeScan;
				copy.afterScan = this.afterScan;
				copy.beforeScanPath = this.beforeScanPath;
				copy.afterScanPath = this.afterScanPath;
				copy.filterAndMapperForDirectory.putAll(this.filterAndMapperForDirectory);
				copy.filterAndMapperForFile.putAll(this.filterAndMapperForFile);
				copy.filterAndMapperForZipEntry.putAll(this.filterAndMapperForZipEntry);			
				copy.maxParallelTasks = this.maxParallelTasks;
				copy.optimizePaths = this.optimizePaths;
				return copy;
			}
			
			@Override
			public void close() {
				filterAndMapperForDirectory.clear();
				filterAndMapperForDirectory = null;
				filterAndMapperForFile.clear();
				filterAndMapperForFile = null;
				filterAndMapperForZipEntry.clear();
				filterAndMapperForZipEntry = null;
				beforeScan = null;
				afterScan = null;
				beforeScanPath = null;
				afterScanPath = null;
				paths.clear();
				paths = null;
			}
		}
	}
	
	@Override
	public void close() {
		deleteFiles(temporaryFiles);
		temporaryFiles.clear();
	}

}
