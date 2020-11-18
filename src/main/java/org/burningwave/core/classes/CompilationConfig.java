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
import java.util.UUID;

public class CompilationConfig {
	private Collection<String> sources;
	
	private Collection<String> classPaths;
	private Collection<String> additionalClassPaths;
	
	private Collection<String> classRepositories;
	private Collection<String> additionalClassRepositories;
	
	private String compiledClassesStorage;
	
	private CompilationConfig() {
		this.sources = new HashSet<>();
		compiledClassesStorage = "/common";
	}
	
	@SafeVarargs
	public final static CompilationConfig withSources(Collection<String>... sourceCollections) {
		CompilationConfig compileConfig = new CompilationConfig();
		for (Collection<String> sourceCollection : sourceCollections) {
			compileConfig.sources.addAll(sourceCollection);
		}
		return compileConfig;
	}
	
	@SafeVarargs
	public final static CompilationConfig withSource(String... sources) {
		return withSources(Arrays.asList(sources));
	}
	
	public CompilationConfig storeCompiledClasses(boolean flag) {
		if (flag) {
			compiledClassesStorage = "common";
		} else {
			compiledClassesStorage = null;
		}
		return this;
	}
	
	public CompilationConfig storeCompiledClassesTo(String folderName) {
		compiledClassesStorage = folderName;
		return this;
	}
	
	public CompilationConfig storeCompiledClassesToNewFolder() {
		compiledClassesStorage = UUID.randomUUID().toString();
		return this;
	}


////////////////////	
	
	@SafeVarargs
	public final CompilationConfig setClassPaths(Collection<String>... classPathCollections) {
		if (classPaths == null) {
			classPaths = new HashSet<>();
		}
		for (Collection<String> classPathCollection : classPathCollections) {
			classPaths.addAll(classPathCollection);
		}
		return this;
	}
	
	@SafeVarargs
	public final CompilationConfig setClassPaths(String... classPaths) {
		return setClassPaths(Arrays.asList(classPaths));
	}

////////////////////	
	
	@SafeVarargs
	public final CompilationConfig addClassPaths(Collection<String>... classPathCollections) {
		if (additionalClassPaths == null) {
			additionalClassPaths = new HashSet<>();
		}
		for (Collection<String> classPathCollection : classPathCollections) {
			additionalClassPaths.addAll(classPathCollection);
		}
		return this;
	}
	
	@SafeVarargs
	public final CompilationConfig addClassPaths(String... classPaths) {
		return addClassPaths(Arrays.asList(classPaths));
	}

////////////////////	
	
	@SafeVarargs
	public final CompilationConfig setClassRepositories(Collection<String>... classPathCollections) {
		if (classRepositories == null) {
			classRepositories = new HashSet<>();
		}
		for (Collection<String> classPathCollection : classPathCollections) {
			classRepositories.addAll(classPathCollection);
		}
		return this;
	}
	
	@SafeVarargs
	public final CompilationConfig setClassRepository(String... classPaths) {
		return setClassRepositories(Arrays.asList(classPaths));
	}

////////////////////	
	
	@SafeVarargs
	public final CompilationConfig addClassRepositories(Collection<String>... classPathCollections) {
		if (additionalClassRepositories == null) {
			additionalClassRepositories = new HashSet<>();
		}
		for (Collection<String> classPathCollection : classPathCollections) {
			additionalClassRepositories.addAll(classPathCollection);
		}
		return this;
	}
	
	@SafeVarargs
	public final CompilationConfig addClassRepository(String... classPaths) {
		return addClassRepositories(Arrays.asList(classPaths));
	}

////////////////////	
	
	Collection<String> getSources() {
		return sources;
	}

	Collection<String> getClassPaths() {
		return classPaths;
	}

	Collection<String> getAdditionalClassPaths() {
		return additionalClassPaths;
	}

	Collection<String> getClassRepositories() {
		return classRepositories;
	}

	Collection<String> getAdditionalClassRepositories() {
		return additionalClassRepositories;
	}
	
	boolean isStoringCompiledClassesEnabled() {
		return compiledClassesStorage != null;
	}
	
	String getCompiledClassesStorage() {
		return compiledClassesStorage;
	}
}

