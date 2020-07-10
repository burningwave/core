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
package org.burningwave.core.classes;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

public class CompileConfig {
	private Collection<String> sources;
	
	private Collection<String> classPaths;
	private Collection<String> additionalClassPaths;
	
	private Collection<String> classRepositoriesWhereToSearchNotFoundClasses;
	private Collection<String> additionalClassRepositoriesWhereToSearchNotFoundClasses;
	
	private boolean storingCompiledClassesEnabled;
	private boolean adjustClassPaths;
	
	private CompileConfig() {
		this.sources = new HashSet<>();
		storingCompiledClassesEnabled = true;
	}
	
	@SafeVarargs
	public final static CompileConfig withSources(Collection<String>... sourceCollections) {
		CompileConfig compileConfig = new CompileConfig();
		for (Collection<String> sourceCollection : sourceCollections) {
			compileConfig.sources.addAll(sourceCollection);
		}
		return compileConfig;
	}
	
	@SafeVarargs
	public final static CompileConfig withSource(String... sources) {
		return withSources(Arrays.asList(sources));
	}
	
	public CompileConfig storeCompiledClasses(boolean flag) {
		this.storingCompiledClassesEnabled = flag;
		return this;
	}
	
	public CompileConfig adjustClassPaths(boolean flag) {
		this.adjustClassPaths = flag;
		return this;
	}	
	
	@SafeVarargs
	public final CompileConfig setClassPaths(Collection<String>... classPathCollections) {
		if (classPaths == null) {
			classPaths = new HashSet<>();
		}
		for (Collection<String> classPathCollection : classPathCollections) {
			classPaths.addAll(classPathCollection);
		}
		return this;
	}
	
	@SafeVarargs
	public final CompileConfig setClassPaths(String... classPaths) {
		return setClassPaths(Arrays.asList(classPaths));
	}
	
	@SafeVarargs
	public final CompileConfig addClassPaths(Collection<String>... classPathCollections) {
		if (additionalClassPaths == null) {
			additionalClassPaths = new HashSet<>();
		}
		for (Collection<String> classPathCollection : classPathCollections) {
			additionalClassPaths.addAll(classPathCollection);
		}
		return this;
	}
	
	@SafeVarargs
	public final CompileConfig addClassPaths(String... classPaths) {
		return addClassPaths(Arrays.asList(classPaths));
	}
	
	@SafeVarargs
	public final CompileConfig setClassRepositoriesWhereToSearchNotFoundClasses(Collection<String>... classPathCollections) {
		if (classRepositoriesWhereToSearchNotFoundClasses == null) {
			classRepositoriesWhereToSearchNotFoundClasses = new HashSet<>();
		}
		for (Collection<String> classPathCollection : classPathCollections) {
			classRepositoriesWhereToSearchNotFoundClasses.addAll(classPathCollection);
		}
		return this;
	}
	
	@SafeVarargs
	public final CompileConfig setClassRepositoryWhereToSearchNotFoundClasses(String... classPaths) {
		return setClassRepositoriesWhereToSearchNotFoundClasses(Arrays.asList(classPaths));
	}
	
	@SafeVarargs
	public final CompileConfig addClassRepositoriesWhereToSearchNotFoundClasses(Collection<String>... classPathCollections) {
		if (additionalClassRepositoriesWhereToSearchNotFoundClasses == null) {
			additionalClassRepositoriesWhereToSearchNotFoundClasses = new HashSet<>();
		}
		for (Collection<String> classPathCollection : classPathCollections) {
			additionalClassRepositoriesWhereToSearchNotFoundClasses.addAll(classPathCollection);
		}
		return this;
	}
	
	@SafeVarargs
	public final CompileConfig addClassRepositoryWhereToSearchNotFoundClasses(String... classPaths) {
		return addClassRepositoriesWhereToSearchNotFoundClasses(Arrays.asList(classPaths));
	}

	Collection<String> getSources() {
		return sources;
	}

	Collection<String> getClassPaths() {
		return classPaths;
	}

	Collection<String> getAdditionalClassPaths() {
		return additionalClassPaths;
	}

	Collection<String> getClassRepositoriesWhereToSearchNotFoundClasses() {
		return classRepositoriesWhereToSearchNotFoundClasses;
	}

	Collection<String> getAdditionalRepositoriesWhereToSearchNotFoundClasses() {
		return additionalClassRepositoriesWhereToSearchNotFoundClasses;
	}
	
	boolean isStoringCompiledClassesEnabled() {
		return storingCompiledClassesEnabled;
	}
	
	boolean isAdjustClassPathsEnabled() {
		return adjustClassPaths;
	}
}
