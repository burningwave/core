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

public class LoadOrBuildAndDefineConfig {
	private Collection<String> compilationClassPaths;
	private Collection<String> classPathsWhereToSearchNotFoundClassesDuringCompilation;
	private Collection<String> classPathsWhereToSearchNotFoundClassesDuringLoading;
	private Collection<UnitSourceGenerator> unitSourceGenerators;
	private ClassLoader classLoader;
	private boolean useOneShotJavaCompiler;
	
	@SafeVarargs
	private LoadOrBuildAndDefineConfig(UnitSourceGenerator... unitsCode) {
		this(Arrays.asList(unitsCode));
	}
	
	@SafeVarargs
	private LoadOrBuildAndDefineConfig(Collection<UnitSourceGenerator>... unitCodeCollections) {
		unitSourceGenerators = new HashSet<>();
		for (Collection<UnitSourceGenerator> unitsCode : unitCodeCollections) {
			unitSourceGenerators.addAll(unitsCode);
		}
	}
	
	@SafeVarargs
	public final static LoadOrBuildAndDefineConfig forUnitSourceGenerator(UnitSourceGenerator... unitsCode) {
		return new LoadOrBuildAndDefineConfig(unitsCode);
	}
	
	@SafeVarargs
	public final static LoadOrBuildAndDefineConfig forUnitSourceGenerator(Collection<UnitSourceGenerator>... unitsCode) {
		return new LoadOrBuildAndDefineConfig(unitsCode);
	}
	
	@SafeVarargs
	public final LoadOrBuildAndDefineConfig add(UnitSourceGenerator... unitsCode) {
		unitSourceGenerators.addAll(Arrays.asList(unitsCode));
		return this;
	}
	
	@SafeVarargs
	public final LoadOrBuildAndDefineConfig addCompilationClassPaths(Collection<String>... classPathCollections) {
		if (compilationClassPaths == null) {
			compilationClassPaths = new HashSet<>();
		}
		for (Collection<String> classPathCollection : classPathCollections) {
			compilationClassPaths.addAll(classPathCollection);
		}
		return this;
	}
	
	@SafeVarargs
	public final LoadOrBuildAndDefineConfig addCompilationClassPaths(String... classPaths) {
		return addCompilationClassPaths(Arrays.asList(classPaths));
	}
	
	@SafeVarargs
	public final LoadOrBuildAndDefineConfig addClassPathsWhereToSearchNotFoundClassesDuringCompilation(Collection<String>... classPathCollections) {
		if (classPathsWhereToSearchNotFoundClassesDuringCompilation == null) {
			classPathsWhereToSearchNotFoundClassesDuringCompilation = new HashSet<>();
		}
		for (Collection<String> classPathCollection : classPathCollections) {
			classPathsWhereToSearchNotFoundClassesDuringCompilation.addAll(classPathCollection);
		}
		return this;
	}
	
	@SafeVarargs
	public final LoadOrBuildAndDefineConfig addClassPathsWhereToSearchNotFoundClassesDuringCompilation(String... classPaths) {
		return addClassPathsWhereToSearchNotFoundClassesDuringCompilation(Arrays.asList(classPaths));
	}
	
	@SafeVarargs
	public final LoadOrBuildAndDefineConfig addClassPathsWhereToSearchNotFoundClasses(String... classPaths) {
		return addClassPathsWhereToSearchNotFoundClassesDuringCompilation(classPaths)
			.addClassPathsWhereToSearchNotFoundClassesDuringLoading(classPaths);		
	}
	
	@SafeVarargs
	public final LoadOrBuildAndDefineConfig addClassPathsWhereToSearchNotFoundClasses(Collection<String>... classPathCollections) {
		return addClassPathsWhereToSearchNotFoundClassesDuringCompilation(classPathCollections)
			.addClassPathsWhereToSearchNotFoundClassesDuringLoading(classPathCollections);		
	}
	
	@SafeVarargs
	public final LoadOrBuildAndDefineConfig addClassPathsWhereToSearchNotFoundClassesDuringLoading(Collection<String>... classPathCollections) {
		if (classPathsWhereToSearchNotFoundClassesDuringLoading == null) {
			classPathsWhereToSearchNotFoundClassesDuringLoading = new HashSet<>();
		}
		for (Collection<String> classPathCollection : classPathCollections) {
			classPathsWhereToSearchNotFoundClassesDuringLoading.addAll(classPathCollection);
		}
		return this;
	}
	
	@SafeVarargs
	public final LoadOrBuildAndDefineConfig addClassPathsWhereToSearchNotFoundClassesDuringLoading(String... classPaths) {
		return addClassPathsWhereToSearchNotFoundClassesDuringLoading(Arrays.asList(classPaths));
	}
	
	public LoadOrBuildAndDefineConfig useClassLoader(ClassLoader classLoader) {
		this.classLoader = classLoader;
		return this;
	}
	
	public LoadOrBuildAndDefineConfig useOneShotJavaCompiler(boolean flag) {
		this.useOneShotJavaCompiler = flag;
		return this;
	}

	Collection<String> getCompilationClassPaths() {
		return compilationClassPaths;
	}

	Collection<String> getClassPathsWhereToSearchNotFoundClassesDuringCompilation() {
		return classPathsWhereToSearchNotFoundClassesDuringCompilation;
	}

	Collection<String> getClassPathsWhereToSearchNotFoundClassesDuringLoading() {
		return classPathsWhereToSearchNotFoundClassesDuringLoading;
	}
	
	Collection<UnitSourceGenerator> getUnitSourceGenerators() {
		return unitSourceGenerators;
	}

	ClassLoader getClassLoader() {
		return classLoader;
	}

	boolean isUseOneShotJavaCompiler() {
		return useOneShotJavaCompiler;
	}
	
}