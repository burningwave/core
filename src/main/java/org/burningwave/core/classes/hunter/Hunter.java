package org.burningwave.core.classes.hunter;


import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.burningwave.core.classes.ClassCriteria;
import org.burningwave.core.classes.ClassHelper;
import org.burningwave.core.classes.JavaClass;
import org.burningwave.core.classes.MemberFinder;
import org.burningwave.core.classes.hunter.SearchContext.InitContext;
import org.burningwave.core.io.FileInputStream;
import org.burningwave.core.io.FileSystemHelper;
import org.burningwave.core.io.FileSystemHelper.Scan;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.io.StreamHelper;
import org.burningwave.core.io.ZipInputStream;


public abstract class Hunter<K, I, C extends SearchContext<K, I>, R extends SearchResult<K, I>> implements org.burningwave.core.Component {
	Supplier<ByteCodeHunter> byteCodeHunterSupplier;
	ByteCodeHunter byteCodeHunter;
	Supplier<ClassHunter> classHunterSupplier;
	ClassHunter classHunter;
	ClassHelper classHelper;
	MemberFinder memberFinder;
	FileSystemHelper fileSystemHelper;
	StreamHelper streamHelper;
	PathHelper pathHelper;
	Function<InitContext, C> contextSupplier;
	Function<C, R> resultSupplier;


	Hunter(
		Supplier<ByteCodeHunter> byteCodeHunterSupplier,
		Supplier<ClassHunter> classHunterSupplier,
		FileSystemHelper fileSystemHelper,
		PathHelper pathHelper,
		StreamHelper streamHelper,
		ClassHelper classHelper,
		MemberFinder memberFinder,
		Function<InitContext, C> contextSupplier,
		Function<C, R> resultSupplier
	) {
		this.fileSystemHelper = fileSystemHelper;
		this.pathHelper = pathHelper;
		this.streamHelper = streamHelper;
		this.classHelper = classHelper;
		this.memberFinder = memberFinder;
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
	public R findBy(ClassFileScanConfiguration scanConfig, SearchConfig searchConfig) {
		final ClassFileScanConfiguration scanConfigCopy = scanConfig.createCopy();
		searchConfig = searchConfig.createCopy();
		C context = createContext(scanConfigCopy, searchConfig);
		searchConfig.init(this.classHelper, context.pathMemoryClassLoader, this.memberFinder);		
		scanConfigCopy.init();
		context.executeSearch(() -> {
				fileSystemHelper.scan(
					scanConfigCopy.toScanConfiguration(context, this)
				);
			}
		);
		Collection<String> skippedClassesNames = context.getSkippedClassNames();
		if (!skippedClassesNames.isEmpty()) {
			logWarn("Skipped classes count: {}", skippedClassesNames.size());
		}
		return resultSupplier.apply(context);
	}
	
	C createContext(ClassFileScanConfiguration scanConfig, SearchConfigAbst<?> searchConfig) {
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
						pathHelper, classHelper, byteCodeHunterSupplier
					),
				scanConfig,
				searchConfig
			)		
		);
		return context;
	}

	
	Consumer<Scan.ItemContext<FileInputStream>> getFileSystemEntryTransformer(
		C context
	) {
		return (scannedItemContext) -> {
			JavaClass javaClass = JavaClass.create(scannedItemContext.getInput().toByteBuffer());
			ClassCriteria.TestContext criteriaTestContext = testCriteria(context, javaClass);
			if (criteriaTestContext.getResult()) {
				retrieveItemFromFileInputStream(
					context, criteriaTestContext, scannedItemContext, javaClass
				);
			}
		};
	}
	
	
	Consumer<Scan.ItemContext<ZipInputStream.Entry>> getZipEntryTransformer(
		C context
	) {
		return (scannedItemContext) -> {
			JavaClass javaClass = JavaClass.create(scannedItemContext.getInput().toByteBuffer());
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
		
	abstract void retrieveItemFromFileInputStream(C Context,ClassCriteria.TestContext criteriaTestContext, Scan.ItemContext<FileInputStream> scannedItem, JavaClass javaClass);
	
	
	abstract void retrieveItemFromZipEntry(C Context, ClassCriteria.TestContext criteriaTestContext, Scan.ItemContext<ZipInputStream.Entry> zipEntry, JavaClass javaClass);
	
	
	@Override
	public void close() {
		byteCodeHunterSupplier = null;
		classHelper = null;
		fileSystemHelper = null;
		streamHelper = null;
		pathHelper = null;
		contextSupplier = null;
	}
}