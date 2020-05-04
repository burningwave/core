package org.burningwave.core.io;

import static org.burningwave.core.assembler.StaticComponentContainer.Cache;
import static org.burningwave.core.assembler.StaticComponentContainer.Paths;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.burningwave.core.Component;
import org.burningwave.core.concurrent.ParallelTasksManager;



public class FileSystemScanner implements Component {
	private Consumer<Collection<String>> pathsOptimizer;
	
	private FileSystemScanner(Consumer<Collection<String>> pathsOptimizer) {
		this.pathsOptimizer = pathsOptimizer;
	}
	
	public static FileSystemScanner create(Consumer<Collection<String>> pathsOptimizer) {
		return new FileSystemScanner(pathsOptimizer);
	}
	
	public void scan(Scan.Configuration configuration) {
		try (Scan.MainContext context = Scan.MainContext.create(this, configuration.createCopy())) {
			if (pathsOptimizer != null && context.configuration.optimizePaths) {
				pathsOptimizer.accept(context.configuration.paths);
			}
			Optional.ofNullable(configuration.beforeScan).ifPresent(consumer -> consumer.accept(context));
			for (String path : context.configuration.paths) {
				Optional.ofNullable(configuration.beforeScanPath).ifPresent(consumer -> consumer.accept(context, path));
				scan(
					new Scan.ItemContext(
						context, path
					)
				);
				Optional.ofNullable(configuration.afterScanPath).ifPresent(consumer -> consumer.accept(context, path));
				if (context.directive == Scan.Directive.STOP_ITERATION) {
	        		break;
	        	}
			}
			Optional.ofNullable(configuration.afterScan).ifPresent(consumer -> consumer.accept(context));
			context.waitForTasksEnding();
		}
	}
	

	void scan(Scan.ItemContext scanItemContext) {
		File basePath = scanItemContext.basePath;
		File currentPath = scanItemContext.item.getWrappedItem();
		Scan.MainContext mainContext = scanItemContext.mainContext;
		Scan.Configuration configuration = mainContext.configuration;
		if (currentPath.isDirectory()) {
			for (Entry<BiPredicate<File, File>, Consumer<Scan.ItemContext>> entry : configuration.filterAndMapperForDirectory.entrySet()) {
				if (entry.getKey().test(basePath, currentPath)) {
					entry.getValue().accept(
						new Scan.ItemContext(
							scanItemContext, new Scan.FileWrapper(currentPath)
						)
					);	
				}
			}
	    } else {
	    	mainContext.tasksManager.addTask(() -> {
	    		for (Entry<Predicate<File>, Consumer<Scan.ItemContext>> entry : configuration.filterAndMapperForFile.entrySet()) {
	    			if (entry.getKey().test(currentPath)) {
	    				try (FileInputStream fileInputStream = FileInputStream.create(currentPath)) {	    						
	    					entry.getValue().accept(
	    						new Scan.ItemContext(
	    							scanItemContext, new Scan.FileInputStreamWrapper(fileInputStream)
	    						)
	    					);    		
    					} 
	    			}
	    		}
	    	});		
	    }    
	}
	
	void scanDirectory(Scan.ItemContext scanItemContext){
		File currentPath = scanItemContext.item.getWrappedItem();
		File[] files = currentPath.listFiles();
		if (files != null) {
			for (File fsObj : files) { 
	        	logDebug("scanning file system item " + fsObj.getAbsolutePath());
				scan(
					new Scan.ItemContext(
						scanItemContext, new Scan.FileWrapper(fsObj)
					)
				);
	        	if (scanItemContext.directive == Scan.Directive.STOP_ITERATION) {
	        		break;
	        	}
	        }
		}	
	}
	
	void scanZipFile(Scan.ItemContext scanItemContext){
		FileInputStream fileInputStream = scanItemContext.item.getWrappedItem();
		File currentFile = fileInputStream.getFile();
		try (IterableZipContainer zipContainer = IterableZipContainer.create(fileInputStream)) {
			logDebug("scanning zip file " + zipContainer.getAbsolutePath());      
			scanZipContainer(new Scan.ItemContext(scanItemContext, new Scan.ZipContainerWrapper(zipContainer)));
		} catch (Throwable exc) {
			logError("Could not scan zip file " + Paths.clean(currentFile.getAbsolutePath()), exc);
		}
	}
	
	void scanZipContainerEntry(Scan.ItemContext scanItemContext){
		IterableZipContainer.Entry zipEntry = scanItemContext.item.getWrappedItem();
		try (IterableZipContainer zipInputStream = IterableZipContainer.create(zipEntry)) {
			scanZipContainer(new Scan.ItemContext(scanItemContext, new Scan.ZipContainerWrapper(zipInputStream)));
		}
	}
	
	void scanZipContainer(Scan.ItemContext currentScannedItemContext){
		IterableZipContainer currentZip = currentScannedItemContext.item.getWrappedItem();
		Scan.MainContext mainContext = currentScannedItemContext.mainContext;
		Scan.Configuration configuration = mainContext.configuration;
		IterableZipContainer.Entry zipEntry = null;
		while((zipEntry = currentZip.getNextEntry()) != null) {
			for (Entry<Predicate<IterableZipContainer.Entry>, Consumer<Scan.ItemContext>> entry : configuration.filterAndMapperForZipEntry.entrySet()) {
				if (entry.getKey().test(zipEntry)) {
					try {
						logDebug("scanning zip entry " + zipEntry.getAbsolutePath());
						entry.getValue().accept(new Scan.ItemContext(currentScannedItemContext, new Scan.ZipEntryWrapper(zipEntry)));
					} catch (Throwable exc) {
						logError("Could not scan zip entry " + Paths.clean(zipEntry.getAbsolutePath()), exc);
					}
				}				
			}
			if (currentScannedItemContext.directive == Scan.Directive.STOP_ITERATION) {
        		break;
        	}
		}
	}
	
	
	public static class Scan {
		
		public static enum Directive {
			CONTINUE, STOP_ITERATION
		}
		
		public static interface ItemWrapper {
			
			public ByteBuffer toByteBuffer();
			
			public String getAbsolutePath();
			
			public <W> W getWrappedItem();
		}
		
		private static class FileInputStreamWrapper implements ItemWrapper {
			private FileInputStream fileInputStream;
			private FileInputStreamWrapper(FileInputStream fileInputStream) {
				this.fileInputStream = fileInputStream;
			}
			@Override
			public ByteBuffer toByteBuffer() {
				return fileInputStream.toByteBuffer();
			}
			@Override
			public String getAbsolutePath() {
				return fileInputStream.getAbsolutePath();
			}
			@SuppressWarnings("unchecked")
			@Override
			public <F> F getWrappedItem() {
				return (F)fileInputStream;
			}
		}
		
		private static class FileWrapper implements ItemWrapper {
			private File file;
			
			private FileWrapper(File file) {
				this.file = file;
			}
			
			@Override
			public ByteBuffer toByteBuffer() {
				return Cache.pathForContents.getOrUploadIfAbsent(
					Paths.clean(file.getAbsolutePath()), () -> {
					try (FileInputStream fileInputStream = FileInputStream.create(file)) {
						return fileInputStream.toByteBuffer();
					}					
				});
			}
			@Override
			public String getAbsolutePath() {
				return Paths.clean(file.getAbsolutePath());
			}

			@Override
			@SuppressWarnings("unchecked")
			public <W> W getWrappedItem() {
				return (W)file;
			}
		}
		
		private static class ZipEntryWrapper implements ItemWrapper {
			private IterableZipContainer.Entry zipEntry;
			
			private ZipEntryWrapper(IterableZipContainer.Entry zipEntry) {
				this.zipEntry = zipEntry;
			}

			@Override
			public ByteBuffer toByteBuffer() {
				return zipEntry.toByteBuffer();
			}

			@Override
			public String getAbsolutePath() {
				return zipEntry.getAbsolutePath();
			}

			@Override
			@SuppressWarnings("unchecked")
			public <W> W getWrappedItem() {
				return (W)zipEntry;
			}
		}
		
		private static class ZipContainerWrapper implements ItemWrapper {
			private IterableZipContainer zipContainer;
			
			private ZipContainerWrapper(IterableZipContainer zipContainer) {
				this.zipContainer = zipContainer;
			}

			@Override
			public ByteBuffer toByteBuffer() {
				return zipContainer.toByteBuffer();
			}

			@Override
			public String getAbsolutePath() {
				return zipContainer.getAbsolutePath();
			}

			@Override
			@SuppressWarnings("unchecked")
			public <W> W getWrappedItem() {
				return (W)zipContainer;
			}
		}
		
		public static class ItemContext {
			final FileSystemScanner fileSystemScanner;
			final MainContext mainContext;
			final ItemContext parent;
			final String basePathAsString;
			final File basePath;
			final ItemWrapper item;		
			Directive directive;			
			
			public ItemContext(MainContext context, String path) {
				this.parent = null;
				this.mainContext = context;
				this.fileSystemScanner = mainContext.fileSystemScanner;
				this.basePathAsString = path;
				this.basePath = new File(this.basePathAsString);
				this.item = new FileWrapper(basePath);
				directive = Directive.CONTINUE;
			}
			
			ItemContext(ItemContext parent, ItemWrapper input) {
				this.parent = parent;
				this.mainContext = this.parent.mainContext;
				this.fileSystemScanner = mainContext.fileSystemScanner;
				this.item = input;
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
			
			public ItemWrapper getScannedItem() {
				return item;
			}
			
			@SuppressWarnings({"unchecked" })
			public <C extends ItemContext> C getParent() {
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
			final FileSystemScanner fileSystemScanner;
			final ParallelTasksManager tasksManager;
			final Configuration configuration;
			Directive directive;
			
			private MainContext(FileSystemScanner fileSystemScanner, Configuration configuration) {
				this.configuration = configuration;
				this.fileSystemScanner = fileSystemScanner;
				tasksManager = ParallelTasksManager.create(configuration.maxParallelTasks);
				directive = Directive.CONTINUE;
			}
			
			static MainContext create(FileSystemScanner fileSystemScanner, Configuration configuration) {
				return new Scan.MainContext(fileSystemScanner, configuration);
			}		
			
			public FileSystemScanner getFileSystemHelper() {
				return this.fileSystemScanner;
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
			private Map<BiPredicate<File, File>, Consumer<ItemContext>> filterAndMapperForDirectory;
			private Map<Predicate<File>, Consumer<ItemContext>> filterAndMapperForFile;
			private Map<Predicate<IterableZipContainer.Entry>, Consumer<ItemContext>> filterAndMapperForZipEntry;
			private boolean optimizePaths;
			private int maxParallelTasks;

			private Configuration() {
				maxParallelTasks = Runtime.getRuntime().availableProcessors();
				filterAndMapperForDirectory = new HashMap<>(); 
				filterAndMapperForFile = new HashMap<>();
				filterAndMapperForZipEntry = new HashMap<>();
				optimizePaths = false;
				paths = new ArrayList<>();
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
			
			public Configuration scanRecursivelyAllDirectoryAndApplyBefore(Consumer<ItemContext> before) {
				return putInFilterAndConsumerMap(filterAndMapperForDirectory, (basePath, currentPath) -> true,
					before.andThen(
						scanItemContext -> {
							scanItemContext.fileSystemScanner.scanDirectory(scanItemContext);
						}
					)
				);
			}
			
			public Configuration scanRecursivelyAllDirectoryThatAndApplyBefore(
				BiPredicate<File, File> predicate,
				Consumer<ItemContext> before
			) {
				return putInFilterAndConsumerMap(filterAndMapperForDirectory, predicate,
					before.andThen(
						scanItemContext -> {
							scanItemContext.fileSystemScanner.scanDirectory(scanItemContext);
						}
					)
				);				
			}
			
			public Configuration scanRecursivelyAllDirectoryThatAndApply(
				BiPredicate<File, File> predicate,
				Consumer<ItemContext> before,
				Consumer<ItemContext> after
			) {
				return putInFilterAndConsumerMap(filterAndMapperForDirectory, predicate,
					before.andThen(
						scanItemContext -> {
							scanItemContext.fileSystemScanner.scanDirectory(scanItemContext);
						}
					).andThen(after)
				);					
			}
			
			public Configuration scanRecursivelyAllDirectoryThat(BiPredicate<File, File> predicate) {
				return putInFilterAndConsumerMap(
					filterAndMapperForDirectory, predicate, 
					scanItemContext -> scanItemContext.fileSystemScanner.scanDirectory(scanItemContext)
				);
			}
			
			public Configuration scanRecursivelyAllDirectory() {
				return putInFilterAndConsumerMap(
					filterAndMapperForDirectory, (basePath, currentPath) -> true, 
					scanItemContext -> scanItemContext.fileSystemScanner.scanDirectory(scanItemContext)
				);
			}
			
			public Configuration scanStrictlyDirectoryAndApplyBefore(Consumer<ItemContext> before) {
				return putInFilterAndConsumerMap(
					filterAndMapperForDirectory,
					(basePath, currentPath) -> basePath.equals(currentPath), 
					before.andThen(
						scanItemContext -> {
							scanItemContext.fileSystemScanner.scanDirectory(scanItemContext);
						}
					)
				);
			}
			
			public Configuration scanStrictlyDirectoryAndApplyAfter(Consumer<ItemContext> after) {
				return putInFilterAndConsumerMap(
					filterAndMapperForDirectory,
					(basePath, currentPath) -> basePath.equals(currentPath), 
					((Consumer<ItemContext>)scanItemContext -> {
						scanItemContext.fileSystemScanner.scanDirectory(scanItemContext);
					}).andThen(after)
				);
			}
			
			public Configuration scanStrictlyDirectoryAndApply(Consumer<ItemContext> before, Consumer<ItemContext> after) {
				return putInFilterAndConsumerMap(
					filterAndMapperForDirectory,
					(basePath, currentPath) -> basePath.equals(currentPath), 
					before.andThen(
						scanItemContext -> {
							scanItemContext.fileSystemScanner.scanDirectory(scanItemContext);
						}
					).andThen(after)
				);
			}
			
			public Configuration scanStrictlyDirectory() {
				return putInFilterAndConsumerMap(
					filterAndMapperForDirectory, (basePath, currentPath) -> basePath.equals(currentPath), 
					scanItemContext -> scanItemContext.fileSystemScanner.scanDirectory(scanItemContext)
				);
			}
			
			
			public final Configuration whenFindFileTestAndApply(
				Predicate<File> predicate, 
				Consumer<ItemContext> fileSystemEntryAnalyzer
			) {
				return putInFilterAndConsumerMap(filterAndMapperForFile, predicate, fileSystemEntryAnalyzer);
			}
			
			public final Configuration scanAllZipFileThatAndApplyBefore(Predicate<File> predicate, Consumer<ItemContext> before) {
				return putInFilterAndConsumerMap(
					filterAndMapperForFile, predicate,
					before.andThen(
						scanItemContext -> {
							scanItemContext.fileSystemScanner.scanZipFile(scanItemContext);
						}
					)
				);
			}
			
			public final Configuration scanAllZipFileThatAndApply(
				Predicate<File> predicate,
				Consumer<ItemContext> before,
				Consumer<ItemContext> after
			) {
				return putInFilterAndConsumerMap(
					filterAndMapperForFile, predicate, 
					before.andThen(
						scanItemContext -> {
							scanItemContext.fileSystemScanner.scanZipFile(scanItemContext);
						}
					).andThen(after)
				);
			}

			
			public final Configuration scanAllZipFileThat(Predicate<File> predicate) {
				return putInFilterAndConsumerMap(
					filterAndMapperForFile, predicate, 
					scanItemContext -> {
						scanItemContext.fileSystemScanner.scanZipFile(scanItemContext);
					}
				);
			}
			
			
			public final Configuration whenFindZipEntryTestAndApply(
				Predicate<IterableZipContainer.Entry> predicate,
				Consumer<ItemContext> zipEntryAnalyzers
			) {
				return putInFilterAndConsumerMap(filterAndMapperForZipEntry, predicate, zipEntryAnalyzers);
			}
			
			public final Configuration whenFindZipEntryApply(
				Consumer<ItemContext> zipEntryAnalyzers
			) {
				return putInFilterAndConsumerMap(filterAndMapperForZipEntry, file -> true, zipEntryAnalyzers);
			}
			
			public Configuration scanRecursivelyAllZipEntryThat(Predicate<IterableZipContainer.Entry> predicate) {
				return putInFilterAndConsumerMap(
					filterAndMapperForZipEntry, predicate, 
					scanItemContext -> {
						scanItemContext.fileSystemScanner.scanZipContainerEntry(scanItemContext);
					}
				);
			}
			
			public Configuration scanRecursivelyAllZipEntryThatAndApplyBefore(
				Predicate<IterableZipContainer.Entry> predicate,
				Consumer<ItemContext> before
			) {
				return putInFilterAndConsumerMap(
					filterAndMapperForZipEntry, predicate,
					before.andThen(
						scanItemContext -> {
							scanItemContext.fileSystemScanner.scanZipContainerEntry(scanItemContext);
						}
					)
				);
			}
			
			public Configuration scanRecursivelyAllZipEntryThatAndApply(
				Predicate<IterableZipContainer.Entry> predicate,
				Consumer<ItemContext> before,
				Consumer<ItemContext> after
			) {
				return putInFilterAndConsumerMap(
					filterAndMapperForZipEntry, predicate,
					before.andThen(
						scanItemContext -> {
							scanItemContext.fileSystemScanner.scanZipContainerEntry(scanItemContext);
						}
					).andThen(after)
				);
			}
		
			@SafeVarargs
			private final <O, P> Configuration putInFilterAndConsumerMap(
				Map<O, Consumer<ItemContext>> map,
				O predicate,
				Consumer<ItemContext>... analyzers
			) {
				synchronized (map) {
					Consumer<ItemContext> analyzer = map.get(predicate);
					for (Consumer<ItemContext> consumer : analyzers) {
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
	
}
