package org.burningwave.core.classes.hunter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.burningwave.core.classes.ClassHelper;
import org.burningwave.core.classes.MemberFinder;
import org.burningwave.core.io.FileSystemHelper;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.io.StreamHelper;


public class FSIClassHunter extends ClassHunterAbst<FileSystemItem, FSIClassHunter.SearchResult> {
	public final static String PARENT_CLASS_LOADER_SUPPLIER_IMPORTS_FOR_PATH_MEMORY_CLASS_LOADER_CONFIG_KEY = "classHunter.pathMemoryClassLoader.parent.supplier.imports";
	public final static String PARENT_CLASS_LOADER_SUPPLIER_FOR_PATH_MEMORY_CLASS_LOADER_CONFIG_KEY = "classHunter.pathMemoryClassLoader.parent";
	public final static Map<String, String> DEFAULT_CONFIG_VALUES = new LinkedHashMap<>();
	
	private FSIClassHunter(
		Supplier<ByteCodeHunter> byteCodeHunterSupplier,
		Supplier<ClassHunter> classHunterSupplier,
		FileSystemHelper fileSystemHelper, 
		PathHelper pathHelper,
		StreamHelper streamHelper,
		ClassHelper classHelper,
		MemberFinder memberFinder,
		ClassLoader parentClassLoader
	) {
		super(
			byteCodeHunterSupplier,
			classHunterSupplier,
			fileSystemHelper,
			pathHelper,
			streamHelper,
			classHelper,
			memberFinder,
			parentClassLoader,
			(context) -> new SearchResult(context)
		);
	}
	
	static {
		DEFAULT_CONFIG_VALUES.put(ClassHunter.PARENT_CLASS_LOADER_SUPPLIER_IMPORTS_FOR_PATH_MEMORY_CLASS_LOADER_CONFIG_KEY, "");
		DEFAULT_CONFIG_VALUES.put(ClassHunter.PARENT_CLASS_LOADER_SUPPLIER_FOR_PATH_MEMORY_CLASS_LOADER_CONFIG_KEY, "null");
	}
	
	public static FSIClassHunter create(
		Supplier<ByteCodeHunter> byteCodeHunterSupplier, 
		Supplier<ClassHunter> classHunterSupplier, 
		FileSystemHelper fileSystemHelper, 
		PathHelper pathHelper, 
		StreamHelper streamHelper,
		ClassHelper classHelper,
		MemberFinder memberFinder,
		ClassLoader parentClassLoader
	) {
		return new FSIClassHunter(
			byteCodeHunterSupplier, classHunterSupplier, fileSystemHelper, pathHelper, streamHelper, classHelper, memberFinder, parentClassLoader
		);
	}	
	
	@Override
	FileSystemItem buildKey(String absolutePath) {
		return FileSystemItem.ofPath(absolutePath);
	}
	
	public static class SearchResult extends ClassHunterAbst.SearchResult<FileSystemItem> {

		SearchResult(SearchContext<FileSystemItem> context) {
			super(context);
		}
		
	}
}