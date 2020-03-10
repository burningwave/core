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
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.burningwave.core.io.IterableZipContainer.Entry;

public class ClassFileScanConfig extends FileScanConfigAbst<ClassFileScanConfig> {
	private final static Predicate<File> CLASS_PREDICATE_FOR_FILE_SYSTEM_ENTRY = entry -> {
		String name = entry.getName();
		return name.endsWith(".class") && 
			!name.endsWith("module-info.class") &&
			!name.endsWith("package-info.class");
	};
	
	private static final Predicate<Entry> CLASS_PREDICATE_FOR_ZIP_ENTRY = entry -> {
		String name = entry.getName();
		return name.endsWith(".class") && 
			!name.endsWith("module-info.class") &&
			!name.endsWith("package-info.class");
	};
	
	private final static Predicate<File> ARCHIVE_PREDICATE_FOR_FILE_SYSTEM_ENTRY = entry -> {
		String name = entry.getName();
		return name.endsWith(".jar") ||
			name.endsWith(".war") ||
			name.endsWith(".ear") ||
			name.endsWith(".zip") ||
			name.endsWith(".jmod");
	};
	
	private static final Predicate<Entry> ARCHIVE_PREDICATE_FOR_ZIP_ENTRY = entry -> {
		String name = entry.getName();
		return name.endsWith(".jar") ||
			name.endsWith(".war") ||
			name.endsWith(".ear") ||
			name.endsWith(".zip") ||
			name.endsWith(".jmod");
	};
	

	private static ClassFileScanConfig create() {
		return new ClassFileScanConfig();
	}	

	@Override
	ClassFileScanConfig _create() {
		return create();
	}
	
	public static ClassFileScanConfig forPaths(Collection<String> paths) {
		ClassFileScanConfig criteria = create();
		criteria.paths.addAll(paths);
		return criteria;
	}			
	
	public static ClassFileScanConfig forPaths(String... paths) {
		return forPaths(Stream.of(paths).collect(Collectors.toCollection(ConcurrentHashMap::newKeySet)));
	}
	@Override
	Predicate<File> getFilePredicateForFileSystemEntry() {
		return CLASS_PREDICATE_FOR_FILE_SYSTEM_ENTRY;
	}

	@Override
	Predicate<File> getArchivePredicateForFileSystemEntry() {
		return ARCHIVE_PREDICATE_FOR_FILE_SYSTEM_ENTRY;
	}

	@Override
	Predicate<Entry> getFilePredicateForZipEntry() {
		return CLASS_PREDICATE_FOR_ZIP_ENTRY;
	}

	@Override
	Predicate<Entry> getArchivePredicateForZipEntry() {
		return ARCHIVE_PREDICATE_FOR_ZIP_ENTRY;
	}
}