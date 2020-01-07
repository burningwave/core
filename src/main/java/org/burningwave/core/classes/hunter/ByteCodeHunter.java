package org.burningwave.core.classes.hunter;

import java.util.function.Supplier;

import org.burningwave.core.classes.ClassCriteria;
import org.burningwave.core.classes.ClassHelper;
import org.burningwave.core.classes.JavaClass;
import org.burningwave.core.classes.MemberFinder;
import org.burningwave.core.io.FileInputStream;
import org.burningwave.core.io.FileSystemHelper;
import org.burningwave.core.io.FileSystemHelper.Scan;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.io.StreamHelper;
import org.burningwave.core.io.ZipInputStream;

public class ByteCodeHunter extends CacherHunter<String, JavaClass, SearchContext<String, JavaClass>, ByteCodeHunter.SearchResult> {
	
	private ByteCodeHunter(
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
			(initContext) -> SearchContext.<String, JavaClass>create(
				fileSystemHelper, streamHelper, initContext
			),
			(context) -> new ByteCodeHunter.SearchResult(context)
		);
	}
	
	public static ByteCodeHunter create(Supplier<ByteCodeHunter> byteCodeHunterSupplier, Supplier<ClassHunter> classHunterSupplier, 
		FileSystemHelper fileSystemHelper, PathHelper pathHelper, StreamHelper streamHelper,
		ClassHelper classHelper, MemberFinder memberFinder
	) {
		return new ByteCodeHunter(byteCodeHunterSupplier, classHunterSupplier, fileSystemHelper, pathHelper, streamHelper, classHelper, memberFinder);
	}
	
	@Override
	<S extends SearchConfigAbst<S>> ClassCriteria.TestContext testCriteria(SearchContext<String, JavaClass> context, JavaClass javaClass) {
		return context.getScanConfig().getClassCriteria().hasNoPredicate() ?
			context.getScanConfig().getClassCriteria().testAndReturnTrueIfNullOrTrueByDefault(null) :
			super.testCriteria(context, javaClass);
	}
	
	@Override
	<S extends SearchConfigAbst<S>> ClassCriteria.TestContext testCachedItem(SearchContext<String, JavaClass> context, String path, String key, JavaClass javaClass) {
		return super.testCriteria(context, javaClass);
	}
	
	@Override
	void retrieveItemFromFileInputStream(
		SearchContext<String, JavaClass> context, 
		ClassCriteria.TestContext criteriaTestContext,
		Scan.ItemContext<FileInputStream> scanItemContext,
		JavaClass javaClass
	) {
		context.addItemFound(scanItemContext.getBasePathAsString(), scanItemContext.getInput().getAbsolutePath(), javaClass);
	}

	
	@Override
	void retrieveItemFromZipEntry(
		SearchContext<String, JavaClass> context,
		ClassCriteria.TestContext criteriaTestContext,
		Scan.ItemContext<ZipInputStream.Entry> scanItemContext,
		JavaClass javaClass) {
		context.addItemFound(scanItemContext.getBasePathAsString(), scanItemContext.getInput().getAbsolutePath(), javaClass);
	}
		
	public static class SearchResult extends org.burningwave.core.classes.hunter.SearchResult<String, JavaClass> {

		public SearchResult(SearchContext<String, JavaClass> context) {
			super(context);
		}
		
	}
}
