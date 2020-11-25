package org.burningwave.core.classes;

import static org.burningwave.core.assembler.StaticComponentContainer.BackgroundExecutor;
import static org.burningwave.core.assembler.StaticComponentContainer.IterableObjectHelper;

import java.util.Map;

import org.burningwave.core.classes.ClassCriteria.TestContext;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.iterable.Properties;

public class ByteCodeHunterImpl extends ClassPathScannerWithCachingSupport.Abst<JavaClass, SearchContext<JavaClass>, ByteCodeHunter.SearchResult> implements ByteCodeHunter {
	ByteCodeHunterImpl(
		PathHelper pathHelper,
		Object defaultPathScannerClassLoaderOrDefaultPathScannerClassLoaderSupplier,
		Properties config
	) {
		super(
			pathHelper,
			(initContext) -> SearchContext.<JavaClass>create(
				initContext
			),
			(context) -> new ByteCodeHunter.SearchResult(context),
			defaultPathScannerClassLoaderOrDefaultPathScannerClassLoaderSupplier,
			config
		);
	}
	
	@Override
	String getNameInConfigProperties() {
		return ByteCodeHunter.Configuration.Key.NAME_IN_CONFIG_PROPERTIES;
	}
	
	@Override
	String getDefaultPathScannerClassLoaderNameInConfigProperties() {
		return ByteCodeHunter.Configuration.Key.DEFAULT_PATH_SCANNER_CLASS_LOADER;
	}
	
	@Override
	String getDefaultPathScannerClassLoaderCheckFileOptionsNameInConfigProperties() {
		return ByteCodeHunter.Configuration.Key.PATH_SCANNER_CLASS_LOADER_SEARCH_CONFIG_CHECK_FILE_OPTIONS;
	}
	
	@Override
	<S extends SearchConfigAbst<S>> ClassCriteria.TestContext testClassCriteria(SearchContext<JavaClass> context, JavaClass javaClass) {
		return context.getSearchConfig().getClassCriteria().hasNoPredicate() ?
			context.getSearchConfig().getClassCriteria().testWithTrueResultForNullEntityOrTrueResultForNullPredicate(null) :
			super.testClassCriteria(context, javaClass);
	}
	
	@Override
	<S extends SearchConfigAbst<S>> ClassCriteria.TestContext testCachedItem(SearchContext<JavaClass> context, String path, String key, JavaClass javaClass) {
		return context.getSearchConfig().getClassCriteria().hasNoPredicate() ?
			context.getSearchConfig().getClassCriteria().testWithTrueResultForNullEntityOrTrueResultForNullPredicate(null) :				
			super.testClassCriteria(context, javaClass);
	}
	
	@Override
	void addToContext(SearchContext<JavaClass> context, TestContext criteriaTestContext,
		String basePath, FileSystemItem fileSystemItem, JavaClass javaClass
	) {
		context.addItemFound(basePath, fileSystemItem.getAbsolutePath(), javaClass.duplicate());		
	}
	
	@Override
	void clearItemsForPath(Map<String, JavaClass> items) {
		BackgroundExecutor.createTask(() -> {
			IterableObjectHelper.deepClear(items, (path, javaClass) -> javaClass.close());
		}, Thread.MIN_PRIORITY).submit();
	}
	
	@Override
	public void close() {
		closeResources(() -> this.cache == null, () -> {
			super.close();
		});
	}

}