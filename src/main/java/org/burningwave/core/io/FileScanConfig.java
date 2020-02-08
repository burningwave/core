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

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FileScanConfig extends FileScanConfigAbst<FileScanConfig> {
	private final static Predicate<String> ARCHIVE_PREDICATE = name -> 
		name.endsWith(".jar") ||
		name.endsWith(".war") ||
		name.endsWith(".ear") ||
		name.endsWith(".zip") ||
		name.endsWith(".jmod");	
	private final static Predicate<String> ARCHIVE_PREDICATE_FOR_ZIP_ENTRY = name -> 
		name.endsWith(".jar") ||
		name.endsWith(".war") ||
		name.endsWith(".ear") ||
		name.endsWith(".zip") ||
		name.endsWith(".jmod");	
	private final static Predicate<String> FILE_PREDICATE = ARCHIVE_PREDICATE.negate();
	private final static Predicate<String> FILE_PREDICATE_FOR_ZIP_ENTRY = FILE_PREDICATE.and(name -> !name.endsWith("/"));
	
	private static FileScanConfig create() {
		return new FileScanConfig();
	}	

	@Override
	FileScanConfig _create() {
		return create();
	}
	
	public static FileScanConfig forPaths(Collection<String> paths) {
		FileScanConfig criteria = create();
		criteria.paths.addAll(paths);
		return criteria;
	}			
	
	public static FileScanConfig forPaths(String... paths) {
		return forPaths(Stream.of(paths).collect(Collectors.toCollection(ConcurrentHashMap::newKeySet)));
	}
	@Override
	Predicate<String> getFilePredicateForFileSystemEntry() {
		return FILE_PREDICATE;
	}

	@Override
	Predicate<String> getArchivePredicateForFileSystemEntry() {
		return ARCHIVE_PREDICATE;
	}

	@Override
	Predicate<String> getFilePredicateForZipEntry() {
		return FILE_PREDICATE_FOR_ZIP_ENTRY;
	}

	@Override
	Predicate<String> getArchivePredicateForZipEntry() {
		return ARCHIVE_PREDICATE_FOR_ZIP_ENTRY;
	}
}