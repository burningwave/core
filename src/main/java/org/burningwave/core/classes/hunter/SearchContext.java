package org.burningwave.core.classes.hunter;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.burningwave.Throwables;
import org.burningwave.core.Component;
import org.burningwave.core.Context;
import org.burningwave.core.classes.hunter.SearchCriteriaAbst.TestContext;
import org.burningwave.core.function.ThrowingSupplier;
import org.burningwave.core.io.FileSystemHelper;
import org.burningwave.core.io.StreamHelper;

public class SearchContext<K, T> implements Component {

	FileSystemHelper fileSystemHelper;
	ClassFileScanConfiguration classFileScanConfiguration;
	SearchCriteriaAbst<?> criteria;
	Map<K, T> itemsFoundFlatMap;
	Map<String, Map<K, T>> itemsFoundMap;
	PathMemoryClassLoader sharedPathMemoryClassLoader;
	PathMemoryClassLoader pathMemoryClassLoader;
	Collection<String> skippedClassNames;
	Boolean classLoaderHaveBeenUploadedWithCriteriaPaths;
	CompletableFuture<Void> searchTask;
	boolean searchTaskFinished;
	
	Collection<String> getSkippedClassNames() {
		return skippedClassNames;
	}
	

	SearchContext(
		FileSystemHelper fileSystemHelper,
		StreamHelper streamHelper,
		InitContext initContext
	) {
		this.fileSystemHelper = fileSystemHelper;
		this.itemsFoundFlatMap = new ConcurrentHashMap<>();
		this.itemsFoundMap = new ConcurrentHashMap<>();
		this.skippedClassNames = ConcurrentHashMap.newKeySet();
		this.sharedPathMemoryClassLoader = initContext.getSharedPathMemoryClassLoader();
		this.pathMemoryClassLoader = initContext.getPathMemoryClassLoader();
		this.classFileScanConfiguration = initContext.getClassFileScanConfiguration();
		this.criteria = initContext.getSearchCriteria();
		this.classLoaderHaveBeenUploadedWithCriteriaPaths = pathMemoryClassLoader.checkPaths(
			classFileScanConfiguration.getPaths(), criteria.considerURLClassLoaderPathsAsScanned
		).getNotContainedPaths().isEmpty();
	}
	
	public static <K, T> SearchContext<K, T> create(
		FileSystemHelper fileSystemHelper,
		StreamHelper streamHelper,
		InitContext initContext
	) {
		return new SearchContext<>(fileSystemHelper, streamHelper, initContext);
	}
	
	void executeSearch(Runnable searcher) {
		if (criteria.waitForSearchEnding) {
			searcher.run();
			searchTaskFinished = true;
		} else {
			searchTask = CompletableFuture.runAsync(() -> {
				searcher.run();
				searchTaskFinished = true;
			});
		}
	}
	
	void waitForSearchEnding() {
		try {
			searchTask.get();
		} catch (Throwable exc) {
			throw Throwables.toRuntimeException(exc);
		}
	}
	
	void addItemFound(String path, K key, T item) {
		retrieveCollectionForPath(
			itemsFoundMap,
			ConcurrentHashMap::new, path
		).put(key, item);
		itemsFoundFlatMap.put(key, item);
	}
	
	void addAllItemsFound(String path, Map<K, T> items) {
		retrieveCollectionForPath(
			itemsFoundMap,
			ConcurrentHashMap::new, path
		).putAll(items);
		itemsFoundFlatMap.putAll(items);
	}
	
	 Map<K, T> retrieveCollectionForPath(Map<String, Map<K, T>> allItems, Supplier<Map<K, T>> mapForPathSupplier, String path) {
		Map<K, T> items = null;
		if (mapForPathSupplier != null) {
			if (allItems != null) {
				items = allItems.get(path);
				if (items == null) {
					synchronized(allItems) {
						items = allItems.get(path);
						if (items == null) {
							items = mapForPathSupplier.get();
							allItems.put(path, items);
						}
					}
				}
			} else {
				items = mapForPathSupplier.get();
			}
		}
		return items;
	}
	
	Collection<String> getPathsToBeScanned() {
		return classFileScanConfiguration.getPaths();
	}
	
	@SuppressWarnings("unchecked")
	<C extends SearchCriteriaAbst<C>> C getCriteria() {
		return (C)criteria;
	}
	
	Map<K, T> getItemsFoundFlatMap() {
		return this.itemsFoundFlatMap;
	}
	
	Collection<T> getItemsFound() {
		return this.itemsFoundFlatMap.values();
	}
	
	Map<K, T> getItemsFound(String path) {
		return this.itemsFoundMap.get(path);
	}
			
	
	public void addByteCodeClassesToClassLoader(String className, ByteBuffer byteCode) {
		pathMemoryClassLoader.addCompiledClass(className, byteCode);			
	}
	
	protected <O> O execute(ThrowingSupplier<O> supplier, Supplier<O> defaultValueSupplier, Supplier<String> classNameSupplier) {
		return ThrowingSupplier.get(() -> {
			try {
				return supplier.get();
			} catch (ClassNotFoundException | NoClassDefFoundError exc) {
				String notFoundClassName = exc.getMessage().replace("/", ".");
				if (!skippedClassNames.contains(notFoundClassName)) {
					if (!this.classLoaderHaveBeenUploadedWithCriteriaPaths) {
						synchronized(this.classLoaderHaveBeenUploadedWithCriteriaPaths) {
							if (!this.classLoaderHaveBeenUploadedWithCriteriaPaths) {
								pathMemoryClassLoader.scanPathsAndLoadAllFoundClasses(
									getPathsToBeScanned(), criteria.considerURLClassLoaderPathsAsScanned, classFileScanConfiguration.maxParallelTasksForUnit
								);
								this.classLoaderHaveBeenUploadedWithCriteriaPaths = true;
							}
						}
						return execute(supplier, defaultValueSupplier, classNameSupplier);
					} else {
						skippedClassNames.add(classNameSupplier.get());
						skippedClassNames.add(notFoundClassName);
					}
				}
				return defaultValueSupplier.get();
			} catch (ClassFormatError | ClassCircularityError | IncompatibleClassChangeError | VerifyError exc) {
				logWarn("Could not load class {}: {}", classNameSupplier.get(), exc.toString());
			}
			return defaultValueSupplier.get();
		});
	}
	
	protected Class<?> loadClass(String className) {
		return execute(
			() -> pathMemoryClassLoader.loadClass(className), 
			() -> null, 
			() -> className
		);
	}
	
	
	@SuppressWarnings("unchecked")
	<C extends SearchCriteriaAbst<C>> TestContext<C> testCriteria(Class<?> cls) {
		return (TestContext<C>) execute(
			() -> criteria.testAndReturnFalseIfNullOrTrueByDefault(cls), 
			() -> criteria.testAndReturnFalseIfNullOrFalseByDefault(null), 
			() -> cls.getName()
		);
	}
	
	@Override
	public void close() {
		fileSystemHelper = null;
		if (criteria.deleteFoundItemsOnClose) {
			itemsFoundFlatMap.clear();
			itemsFoundMap.entrySet().stream().forEach(entry -> {
				entry.getValue().clear();
			});
		}
		itemsFoundFlatMap = null;
		itemsFoundMap = null;
		criteria = null;
		if (pathMemoryClassLoader != sharedPathMemoryClassLoader) {
			pathMemoryClassLoader.close();
		}
		pathMemoryClassLoader = null;
		sharedPathMemoryClassLoader = null;
		skippedClassNames.clear();
		skippedClassNames = null;
	}
	
	
	static class InitContext extends Context {
		enum Elements {
			SHARED_PATH_MEMORY_CLASS_LOADER,
			PATH_MEMORY_CLASS_LOADER,
			CLASS_FILE_SCAN_CONFIGURATION,
			SEARCH_CRITERIA;
		}
		
		InitContext(
			PathMemoryClassLoader sharedPathMemoryClassLoader, 
			PathMemoryClassLoader pathMemoryClassLoader,
			ClassFileScanConfiguration classFileScanConfiguration,
			SearchCriteriaAbst<?> criteria
		) {
			super();
			put(Elements.SHARED_PATH_MEMORY_CLASS_LOADER, sharedPathMemoryClassLoader);
			put(Elements.PATH_MEMORY_CLASS_LOADER, pathMemoryClassLoader);
			put(Elements.CLASS_FILE_SCAN_CONFIGURATION, classFileScanConfiguration);
			put(Elements.SEARCH_CRITERIA, criteria);			
		}
		
		static InitContext create(
			PathMemoryClassLoader sharedPathMemoryClassLoader, 
			PathMemoryClassLoader pathMemoryClassLoader,
			ClassFileScanConfiguration classFileScanConfiguration,
			SearchCriteriaAbst<?> criteria
		) {
			return new InitContext(sharedPathMemoryClassLoader, pathMemoryClassLoader, classFileScanConfiguration, criteria);
		}
		
		PathMemoryClassLoader getSharedPathMemoryClassLoader() {
			return get(Elements.SHARED_PATH_MEMORY_CLASS_LOADER);
		}
		
		PathMemoryClassLoader getPathMemoryClassLoader() {
			return get(Elements.PATH_MEMORY_CLASS_LOADER);
		}
		
		ClassFileScanConfiguration getClassFileScanConfiguration() {
			return get(Elements.CLASS_FILE_SCAN_CONFIGURATION);
		}
		
		<C extends SearchCriteriaAbst<C>> C getSearchCriteria() {
			return get(Elements.SEARCH_CRITERIA);
		}
	}
}