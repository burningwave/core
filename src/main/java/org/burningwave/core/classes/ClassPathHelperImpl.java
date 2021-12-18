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
 * Copyright (c) 2019-2021 Roberto Gentili
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

import static org.burningwave.core.assembler.StaticComponentContainer.BackgroundExecutor;
import static org.burningwave.core.assembler.StaticComponentContainer.ClassLoaders;
import static org.burningwave.core.assembler.StaticComponentContainer.FileSystemHelper;
import static org.burningwave.core.assembler.StaticComponentContainer.IterableObjectHelper;
import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository;
import static org.burningwave.core.assembler.StaticComponentContainer.Paths;
import static org.burningwave.core.assembler.StaticComponentContainer.SourceCodeHandler;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.burningwave.core.Component;
import org.burningwave.core.classes.ClassPathHunter.SearchResult;
import org.burningwave.core.concurrent.QueuedTaskExecutor;
import org.burningwave.core.function.Executor;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.FileSystemItem.CheckingOption;
import org.burningwave.core.iterable.IterableObjectHelper.ResolveConfig;

class ClassPathHelperImpl implements ClassPathHelper, Component {
	private String instanceId;
	private ClassPathHunter classPathHunter;
	private FileSystemItem classPathsBasePath;
	private Map<?, ?> config;


	ClassPathHelperImpl(
		ClassPathHunter classPathHunter, Map<?, ?> config
	) {
		this.instanceId = UUID.randomUUID().toString();
		this.classPathHunter = classPathHunter;
		this.classPathsBasePath = FileSystemItem.of(getOrCreateTemporaryFolder("classPaths"));
		this.config = config;
		checkAndListenTo(config);
	}

	@Override
	public String getTemporaryFolderPrefix() {
		return getClass().getName() + "@" + instanceId;
	}

	CheckingOption getClassFileCheckingOption() {
		return FileSystemItem.CheckingOption.forLabel(
			IterableObjectHelper.resolveStringValue(
				ResolveConfig.forNamedKey(Configuration.Key.CLASS_PATH_HUNTER_SEARCH_CONFIG_CHECK_FILE_OPTIONS)
				.on(config)
				.withDefaultValues(Configuration.DEFAULT_VALUES)
			)
		);
	}

	@Override
	public Supplier<Map<String, String>> compute(SearchConfig searchConfig) {
		SearchConfig searchConfigCopy = searchConfig.createCopy();
		searchConfigCopy.init((ClassPathHunterImpl)classPathHunter);
		return compute0(
			searchConfig.getPathsToBeScanned().stream().map(FileSystemItem::getAbsolutePath).collect(Collectors.toSet()),
			null,
			(toBeAdjuested) -> {
				searchConfigCopy.setFileFilter(
					FileSystemItem.Criteria.forClassTypeFiles(
						getClassFileCheckingOption()
					)
				);
				try(SearchResult result = classPathHunter.findBy(
					searchConfigCopy
				)) {
					return result.getClassPaths();
				}
			}
		);
	}


	@Override
	public Supplier<Map<String, String>> compute(
		ComputeConfig.ByClassesSearching input
	) {
		if (input.classRepositories == null) {
			throw new IllegalArgumentException("No class repository has been provided");
		}
		SearchConfig searchConfig = SearchConfig.forPaths(input.classRepositories).by(
			input.classCriteria
		).optimizePaths(
			true
		);
		if (input.pathsToBeRefreshed != null) {
			searchConfig.checkForAddedClassesForAllPathThat(fileSystemItem-> {
				return input.pathsToBeRefreshed.contains(fileSystemItem.getAbsolutePath());
			});
		}
		return compute(searchConfig);
	}

	@Override
	public Supplier<Map<String, String>> compute(
		ComputeConfig.ByClassesSearching.FromImportsIntoSources input
	) {
		if (input.sources == null) {
			throw new IllegalArgumentException("No source has been provided");
		}
		Collection<String> imports = new HashSet<>();
		for (String sourceCode : input.sources) {
			imports.addAll(SourceCodeHandler.extractImports(sourceCode));
		}
		ClassCriteria classCriteria = ClassCriteria.create().className(className ->
			imports.contains(className)
		);
		if (input.additionalClassCriteria != null) {
			classCriteria = classCriteria.or(input.additionalClassCriteria);
		}
		return compute(
			ComputeConfig.byClassesSearching(input.classRepositories).withClassFilter(classCriteria)
		);
	}

	@Override
	public Supplier<Map<String, String>> compute(
		ComputeConfig.FromImportsIntoSources input
	) {
		Collection<String> imports = new HashSet<>();
		for (String sourceCode : input.sources) {
			imports.addAll(SourceCodeHandler.extractImports(sourceCode));
		}
		Predicate<FileSystemItem> javaClassFilter = (classFile) ->
			imports.contains(classFile.toJavaClass().getName());

		if (input.additionalFileFilter != null) {
			javaClassFilter = javaClassFilter.or(input.additionalFileFilter);
		}

		return compute(
			ComputeConfig.forClassRepositories(input.classRepositories)
			.refreshAllPathsThat(input.pathsToBeRefreshedPredicate)
			.withFileFilter(javaClassFilter)
		);
	}

	@Override
	public Map<String, ClassLoader> compute(
		ComputeConfig.AddAllToClassLoader input
	) {
		Predicate<FileSystemItem> pathsToBeRefreshedPredicate = null;
		if (input.pathsToBeRefreshed != null) {
			pathsToBeRefreshedPredicate =  fileSystemItem -> input.pathsToBeRefreshed.contains(fileSystemItem.getAbsolutePath());
		}

		Collection<String> classPathsOfClassToBeLoaded = new HashSet<>();
		Predicate<FileSystemItem> criteria = (fileSystemItemCls) -> {
			JavaClass javaClass = fileSystemItemCls.toJavaClass();
			if (javaClass.getName().equals(input.nameOfTheClassToBeLoaded)) {
				String classAbsolutePath = fileSystemItemCls.getAbsolutePath();
				classPathsOfClassToBeLoaded.add(classAbsolutePath.substring(0, classAbsolutePath.lastIndexOf("/" + javaClass.getPath())));
				return true;
			}
			return false;
		};

		Collection<String> classPathsOfClassesRequiredByTheClassToBeLoaded = new HashSet<>();
		if (!(input.nameOfTheClassesRequiredByTheClassToBeLoaded == null || input.nameOfTheClassesRequiredByTheClassToBeLoaded.isEmpty())) {
			criteria = criteria.or((fileSystemItemCls) -> {
				JavaClass javaClass = fileSystemItemCls.toJavaClass();
				for (String className : input.nameOfTheClassesRequiredByTheClassToBeLoaded) {
					if (javaClass.getName().equals(className)) {
						String classAbsolutePath = fileSystemItemCls.getAbsolutePath();
						classPathsOfClassesRequiredByTheClassToBeLoaded.add(classAbsolutePath.substring(0, classAbsolutePath.lastIndexOf("/" + javaClass.getPath())));
						return true;
					}
				}
				return false;
			});
		}

		Map<String, String> classPaths = compute(
			ComputeConfig.forClassRepositories(input.classRepositories)
			.refreshAllPathsThat(pathsToBeRefreshedPredicate)
			.withFileFilter(criteria)
		).get();

		ClassLoader targetClassLoader = input.classLoader;
		Collection<String> classPathsToLoad = new HashSet<>();
		if (!classPathsOfClassToBeLoaded.isEmpty()) {
			String classToFindClassClassPath = classPathsOfClassToBeLoaded.stream().findFirst().get();
			targetClassLoader = ClassLoaders.getClassLoaderOfPath(input.classLoader, classToFindClassClassPath);
			if (targetClassLoader == null) {
				String notFoundComputedClassClassPath = classPaths.get(classToFindClassClassPath);
				if (!notFoundComputedClassClassPath.equals(classToFindClassClassPath)) {
					targetClassLoader = ClassLoaders.getClassLoaderOfPath(input.classLoader, notFoundComputedClassClassPath);
					if (targetClassLoader == null) {
						classPathsToLoad.add(notFoundComputedClassClassPath);
					}
				} else {
					classPathsToLoad.add(notFoundComputedClassClassPath);
				}
			}
		}
		if (targetClassLoader == null) {
			targetClassLoader = input.classLoader;
		}

		Map<String, ClassLoader> addedClassPathsForClassLoader = new HashMap<>();

		if (!(targetClassLoader instanceof PathScannerClassLoader)) {
			for (String classPath : classPathsOfClassesRequiredByTheClassToBeLoaded) {
				classPathsToLoad.add(classPaths.get(classPath));
			}

			for (String classPath :  classPathsToLoad) {
				if (!ClassLoaders.addClassPath(
					targetClassLoader,
					absolutePath ->
						input.pathsToBeRefreshed != null && input.pathsToBeRefreshed.contains(absolutePath),
					classPath
				).isEmpty()) {
					ManagedLoggerRepository.logInfo(getClass()::getName, "Added class path {} to {}", classPath, targetClassLoader.toString());
					addedClassPathsForClassLoader.put(classPath, targetClassLoader);
				} else {
					ManagedLoggerRepository.logInfo(getClass()::getName, "Class path {} already present in {}", classPath, targetClassLoader.toString());
				}
			}
		} else {
			PathScannerClassLoader pathScannerClassLoader = (PathScannerClassLoader)targetClassLoader;
			classPathsToLoad = new HashSet<>();
			if (!classPathsOfClassToBeLoaded.isEmpty()) {
				classPathsToLoad.addAll(classPathsOfClassToBeLoaded);
			}
			if (!classPathsOfClassesRequiredByTheClassToBeLoaded.isEmpty()) {
				classPathsToLoad.addAll(classPathsOfClassesRequiredByTheClassToBeLoaded);
			}
			for (String addedClassPath : pathScannerClassLoader.scanPathsAndAddAllByteCodesFound(
				classPathsToLoad,
				absolutePath ->
					input.pathsToBeRefreshed != null && input.pathsToBeRefreshed.contains(absolutePath)
			)) {
				addedClassPathsForClassLoader.put(addedClassPath, pathScannerClassLoader);
			}
		}
		return addedClassPathsForClassLoader;
	}

	@Override
	public Supplier<Map<String, String>> compute(
		ComputeConfig input
	) {
		FileSystemItem.Criteria classFileFilter = FileSystemItem.Criteria.forClassTypeFiles(
			getClassFileCheckingOption()
		);
		Predicate<FileSystemItem> finalPathsToBeRefreshedPredicate = input.pathsToBeRefreshedPredicate != null? input.pathsToBeRefreshedPredicate :
			fileSystemItem -> false;

		Predicate<FileSystemItem> finalJavaClassFilter = input.javaClassFilter != null? input.javaClassFilter :
			(fileSystemItem) -> true;

		return compute0(
			input.classRepositories,
			null,
			clsRepositories -> {
				Collection<FileSystemItem> classPaths = ConcurrentHashMap.newKeySet();
				for (String classRepositoryPath : clsRepositories) {
					FileSystemItem classRepository = FileSystemItem.ofPath(classRepositoryPath);
					if (finalPathsToBeRefreshedPredicate.test(classRepository)) {
						classRepository.refresh();
					}
					classRepository.findInAllChildren(
						classFileFilter.and().allFileThat(fileSystemItemCls -> {
							JavaClass javaClass = fileSystemItemCls.toJavaClass();
							if (finalJavaClassFilter.test(fileSystemItemCls)) {
								String classAbsolutePath = fileSystemItemCls.getAbsolutePath();
								classPaths.add(
									FileSystemItem.ofPath(
										classAbsolutePath.substring(0, classAbsolutePath.lastIndexOf("/" + javaClass.getPath()))
									)
								);
								return true;
							}
							return false;
						}).enableDefaultExceptionHandler()
					);
				}
				return classPaths;
			}
		);
	}


	private Supplier<Map<String, String>> compute0(
		Collection<String> classRepositories,
		Predicate<FileSystemItem> pathsToBeRefreshedPredicate,
		Function<Collection<String>, Collection<FileSystemItem>> callRepositoriesSupplier
	) {
		if (classRepositories == null) {
			throw new IllegalArgumentException("No class repository has been provided");
		}
		Map<String, String> classPaths = new HashMap<>();
		Collection<FileSystemItem> effectiveClassPaths = callRepositoriesSupplier.apply(classRepositories);

		Collection<QueuedTaskExecutor.ProducerTask<String>> pathsCreationTasks = new HashSet<>();

		if (pathsToBeRefreshedPredicate == null) {
			pathsToBeRefreshedPredicate = fileSystemItem -> false;
		}

		if (!effectiveClassPaths.isEmpty()) {
			for (FileSystemItem fsObject : effectiveClassPaths) {
				if (pathsToBeRefreshedPredicate.test(fsObject)) {
					fsObject.refresh();
				}
				if (fsObject.isCompressed()) {
					Executor.run(() -> {
						synchronized (this) {
							FileSystemItem classPath = FileSystemItem.ofPath(
								classPathsBasePath.getAbsolutePath() + "/" + Paths.toSquaredPath(fsObject.getAbsolutePath(), fsObject.isFolder())
							);
							if (!classPath.refresh().exists()) {
								QueuedTaskExecutor.ProducerTask<String> tsk = BackgroundExecutor.createProducerTask(task -> {
									FileSystemItem copy = fsObject.copyTo(classPathsBasePath.getAbsolutePath());
									File target = new File(classPath.getAbsolutePath());
									new File(copy.getAbsolutePath()).renameTo(target);
									return Paths.clean(target.getAbsolutePath());
								});
								pathsCreationTasks.add(tsk.submit());
							}
							classPaths.put(
								fsObject.getAbsolutePath(),
								classPath.getAbsolutePath()
							);
							//Free memory
							classPath.destroy();
						}
					});
				} else {
					classPaths.put(fsObject.getAbsolutePath(), fsObject.getAbsolutePath());
				}
			}
		}
		return () -> {
			pathsCreationTasks.stream().forEach(pathsCreationTask -> pathsCreationTask.join());
			return classPaths;
		};
	}

	@Override
	public void close() {
		closeResources(() -> classPathsBasePath == null, task -> {
			checkAndUnregister(config);
			FileSystemHelper.deleteOnExit(getOrCreateTemporaryFolder());
			classPathsBasePath.destroy();
			classPathsBasePath = null;
			classPathHunter = null;
			config = null;
			instanceId = null;
		});
	}

}