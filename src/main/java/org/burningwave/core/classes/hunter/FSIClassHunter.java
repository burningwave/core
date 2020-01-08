/*
 * This file is part of Burningwave Core.
 *
 * Author: Roberto Gentli
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