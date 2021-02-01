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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.burningwave.core.Virtual;
import org.burningwave.core.classes.JavaMemoryCompiler.Compilation;

@SuppressWarnings("unchecked")
class LoadOrBuildAndDefineConfigAbst<L extends LoadOrBuildAndDefineConfigAbst<L>> {
	
	Collection<UnitSourceGenerator> unitSourceGenerators;
	private Function<Compilation.Config, Compilation.Config> compilationConfigSupplier;
	private Collection<String> classRepositoriesWhereToSearchNotFoundClassesDuringLoading;
	private Collection<String> additionalClassRepositoriesWhereToSearchNotFoundClassesDuringLoading;
	
	private ClassLoader classLoader;
	private boolean useOneShotJavaCompiler;
	private boolean virtualizeClasses;
		
	@SafeVarargs LoadOrBuildAndDefineConfigAbst(UnitSourceGenerator... unitsCode) {
		this(Arrays.asList(unitsCode));
	}
	
	@SafeVarargs
	LoadOrBuildAndDefineConfigAbst(Collection<UnitSourceGenerator>... unitCodeCollections) {
		virtualizeClasses = true;
		unitSourceGenerators = new HashSet<>();
		for (Collection<UnitSourceGenerator> unitsCode : unitCodeCollections) {
			unitSourceGenerators.addAll(unitsCode);
		}
		compilationConfigSupplier = (compileConfig) -> {
			for (UnitSourceGenerator unitCode : this.unitSourceGenerators) {
				unitCode.getAllClasses().entrySet().forEach(entry -> {
					if (virtualizeClasses) {
						entry.getValue().addConcretizedType(TypeDeclarationSourceGenerator.create(Virtual.class));
					}
				});
			}
			return Compilation.Config.forUnitSourceGenerators(unitSourceGenerators);
		};
	}
	
	public L virtualizeClasses(boolean flag) {
		this.virtualizeClasses = flag;
		return (L)this;
	}
	
	public L modifyCompilationConfig(Consumer<Compilation.Config> compilationConfigModifier) {
		compilationConfigSupplier = compilationConfigSupplier.andThen((compileConfig) -> {
			compilationConfigModifier.accept(compileConfig);
			return compileConfig;
		});
		return (L)this;
	}

////////////////////	
	
	@SafeVarargs
	public final L setClassRepository(String... classPaths) {
		return setClassRepositories(Arrays.asList(classPaths));
	}
	
	@SafeVarargs
	public final L setClassRepositories(Collection<String>... classPathCollections) {
		modifyCompilationConfig(compileConfig ->
			compileConfig.setClassRepositories(classPathCollections)
		);
		return setClassRepositoriesWhereToSearchNotFoundClasses(classPathCollections);
	}
////////////////////	
	
	@SafeVarargs
	public final L addClassRepository(String... classPaths) {
		return addClassRepositories(Arrays.asList(classPaths));
	}
	
	@SafeVarargs
	public final L addClassRepositories(Collection<String>... classPathCollections) {
		modifyCompilationConfig(compileConfig ->
			compileConfig.addClassRepositories(classPathCollections)
		);
		return addClassRepositoriesWhereToSearchNotFoundClasses(classPathCollections);
	}	
	
////////////////////
	
	@SafeVarargs
	public final L setClassPaths(String... classPaths) {
		return setClassPaths(Arrays.asList(classPaths));
	}
	
	@SafeVarargs
	public final L setClassPaths(Collection<String>... classPathCollections) {
		modifyCompilationConfig(compileConfig ->
			compileConfig.setClassPaths(classPathCollections)
		);
		return setClassRepositoriesWhereToSearchNotFoundClasses(classPathCollections);
	}
////////////////////	
	
	@SafeVarargs
	public final L addClassPaths(String... classPaths) {
		return addClassPaths(Arrays.asList(classPaths));
	}
	
	@SafeVarargs
	public final L addClassPaths(Collection<String>... classPathCollections) {
		modifyCompilationConfig(compileConfig ->
			compileConfig.addClassPaths(classPathCollections)
		);
		return addClassRepositoriesWhereToSearchNotFoundClasses(classPathCollections);
	}

////////////////////	
	
	@SafeVarargs
	public final L setClassRepositoryWhereToSearchNotFoundClasses(String... classPaths) {
		return setClassRepositoriesWhereToSearchNotFoundClasses(Arrays.asList(classPaths));		
	}
	
	@SafeVarargs
	public final L setClassRepositoriesWhereToSearchNotFoundClasses(Collection<String>... classRepositoryCollections) {
		return modifyCompilationConfig(compileConfig -> compileConfig.setClassRepositories(classRepositoryCollections))
			.setClassRepositoriesWhereToSearchNotFoundClassesDuringLoading(classRepositoryCollections);		
	}

////////////////////	
	
	@SafeVarargs
	public final L addClassRepositoryWhereToSearchNotFoundClasses(String... classPaths) {
		return addClassRepositoriesWhereToSearchNotFoundClasses(Arrays.asList(classPaths));
	}
	
	@SafeVarargs
	public final L addClassRepositoriesWhereToSearchNotFoundClasses(Collection<String>... classRepositoryCollections) {
		return modifyCompilationConfig(compileConfig -> compileConfig.addClassRepositories(classRepositoryCollections))
			.addClassRepositoriesWhereToSearchNotFoundClassesDuringLoading(classRepositoryCollections);		
	}

////////////////////
	
	@SafeVarargs
	public final L setClassRepositoryWhereToSearchNotFoundClassesDuringLoading(String... classPaths) {
		return setClassRepositoriesWhereToSearchNotFoundClassesDuringLoading(Arrays.asList(classPaths));
	}
	
	@SafeVarargs
	public final L setClassRepositoriesWhereToSearchNotFoundClassesDuringLoading(Collection<String>... classPathCollections) {
		if (classRepositoriesWhereToSearchNotFoundClassesDuringLoading == null) {
			classRepositoriesWhereToSearchNotFoundClassesDuringLoading = new HashSet<>();
		}
		for (Collection<String> classPathCollection : classPathCollections) {
			classRepositoriesWhereToSearchNotFoundClassesDuringLoading.addAll(classPathCollection);
		}
		return (L)this;
	}

////////////////////	
	
	@SafeVarargs
	public final L addClassRepositoryWhereToSearchNotFoundClassesDuringLoading(String... classPaths) {
		return addClassRepositoriesWhereToSearchNotFoundClassesDuringLoading(Arrays.asList(classPaths));
	}
	
	@SafeVarargs
	public final L addClassRepositoriesWhereToSearchNotFoundClassesDuringLoading(Collection<String>... classPathCollections) {
		if (additionalClassRepositoriesWhereToSearchNotFoundClassesDuringLoading == null) {
			additionalClassRepositoriesWhereToSearchNotFoundClassesDuringLoading = new HashSet<>();
		}
		for (Collection<String> classPathCollection : classPathCollections) {
			additionalClassRepositoriesWhereToSearchNotFoundClassesDuringLoading.addAll(classPathCollection);
		}
		return (L)this;
	}

////////////////////

	public L useClassLoader(ClassLoader classLoader) {
		this.classLoader = classLoader;
		return (L)this;
	}
	
	public L useOneShotJavaCompiler(boolean flag) {
		this.useOneShotJavaCompiler = flag;
		return (L)this;
	}

	Collection<String> getClassRepositoriesWhereToSearchNotFoundClassesDuringLoading() {
		return classRepositoriesWhereToSearchNotFoundClassesDuringLoading;
	}
	
	Collection<String> getAdditionalClassRepositoriesWhereToSearchNotFoundClassesDuringLoading() {
		return additionalClassRepositoriesWhereToSearchNotFoundClassesDuringLoading;
	}	
	
	ClassLoader getClassLoader() {
		return classLoader;
	}

	boolean isUseOneShotJavaCompilerEnabled() {
		return useOneShotJavaCompiler;
	}
	
	Collection<String> getClassesName() {
		Collection<String> classesName = new HashSet<>();
		unitSourceGenerators.stream().forEach(unitCode -> {
			unitCode.getAllClasses().entrySet().forEach(entry -> {
				classesName.add(entry.getKey());
			});
		});
		return classesName;
	}
	
	Supplier<Compilation.Config> getCompileConfigSupplier() {
		return () -> compilationConfigSupplier.apply(null);
	}
	
	boolean isVirtualizeClassesEnabled() {
		return virtualizeClasses;
	}
}