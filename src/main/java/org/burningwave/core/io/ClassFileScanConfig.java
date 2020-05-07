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

import static org.burningwave.core.assembler.StaticComponentContainer.Streams;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.burningwave.core.function.ThrowingSupplier;
import org.burningwave.core.io.IterableZipContainer.Entry;

public class ClassFileScanConfig extends FileScanConfigAbst<ClassFileScanConfig> {

	@Override
	ClassFileScanConfig create() {
		return new ClassFileScanConfig();
	}
	
	@SafeVarargs
	public static ClassFileScanConfig forPaths(Collection<String>... pathsColl) {
		return new ClassFileScanConfig().addPaths(pathsColl);
	}	
	
	public static ClassFileScanConfig forPaths(String... paths) {
		return forPaths((Collection<String>)Stream.of(paths).collect(Collectors.toCollection(HashSet::new)));
	}
	
	@Override
	Predicate<File> getFilePredicateForFileSystemEntry() {
		Predicate<File> checkFileExtension = entry -> {
			String name = entry.getName();
			return name.endsWith(".class") && 
				!name.endsWith("module-info.class") &&
				!name.endsWith("package-info.class");
		};
		Predicate<File> checkFileSignature = entry -> ThrowingSupplier.get(() -> Streams.isClass(entry));
		if (checkFileOptions == CHECK_FILE_SIGNATURE) {
			return checkFileSignature;
		} else if (checkFileOptions == CHECK_FILE_EXTENSION_AND_SIGNATURE) {
			return checkFileExtension.and(checkFileSignature);
		} else if (checkFileOptions == CHECK_FILE_EXTENSION_OR_SIGNATURE) {
			return checkFileExtension.or(checkFileSignature);
		} else if (checkFileOptions == CHECK_FILE_EXTENSION) {
			return checkFileExtension;
		}
		return null;
	}

	@Override
	Predicate<Entry> getFilePredicateForZipEntry() {
		Predicate<Entry> checkFileExtension = entry -> {
			String name = entry.getName();
			return name.endsWith(".class") && 
				!name.endsWith("module-info.class") &&
				!name.endsWith("package-info.class");
		};
		Predicate<Entry> checkFileSignature = entry -> ThrowingSupplier.get(() -> Streams.isClass(entry.toByteBuffer()));
		if (checkFileOptions == CHECK_FILE_SIGNATURE) {
			return checkFileSignature;
		} else if (checkFileOptions == CHECK_FILE_EXTENSION_AND_SIGNATURE) {
			return checkFileExtension.and(checkFileSignature);
		} else if (checkFileOptions == CHECK_FILE_EXTENSION_OR_SIGNATURE) {
			return checkFileExtension.or(checkFileSignature);
		} else if (checkFileOptions == CHECK_FILE_EXTENSION) {
			return checkFileExtension;
		}
		return null;
	}
}