package org.burningwave.core.classes;

import static org.burningwave.core.assembler.StaticComponentContainer.BackgroundExecutor;
import static org.burningwave.core.assembler.StaticComponentContainer.ClassLoaders;
import static org.burningwave.core.assembler.StaticComponentContainer.FileSystemHelper;
import static org.burningwave.core.assembler.StaticComponentContainer.Paths;
import static org.burningwave.core.assembler.StaticComponentContainer.SourceCodeHandler;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.burningwave.core.Component;
import org.burningwave.core.classes.ClassPathHunter.SearchResult;
import org.burningwave.core.concurrent.QueuedTasksExecutor;
import org.burningwave.core.function.ThrowingRunnable;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.FileSystemItem.CheckingOption;
import org.burningwave.core.iterable.Properties;

public class ClassPathHelper  implements Component {
	
public static class Configuration {
		
		public static class Key {
			
			public static final String CLASS_PATH_HUNTER_SEARCH_CONFIG_CHECK_FILE_OPTIONS = "class-path-helper.class-path-hunter.search-config.check-file-option";

		}
		
		public final static Map<String, Object> DEFAULT_VALUES;
		
		static {
			DEFAULT_VALUES = new HashMap<>();
			DEFAULT_VALUES.put(
				Key.CLASS_PATH_HUNTER_SEARCH_CONFIG_CHECK_FILE_OPTIONS,
				"${" + ClassPathScannerAbst.Configuration.Key.DEFAULT_CHECK_FILE_OPTIONS + "}"
			);
		}
	}
	
	private String instanceId;
	private ClassPathHunter classPathHunter;
	private FileSystemItem classPathsBasePath;
	private Properties config;	
	
	
	public ClassPathHelper(
		ClassPathHunter classPathHunter, Properties config
	) {	
		this.instanceId = UUID.randomUUID().toString();
		this.classPathHunter = classPathHunter;
		this.classPathsBasePath = FileSystemItem.of(getOrCreateTemporaryFolder("classPaths"));
		this.config = config;
		listenTo(config);
	}
	
	
	public static ClassPathHelper create(ClassPathHunter classPathHunter, Properties config) {
		return new ClassPathHelper(classPathHunter, config);
	}
	
	@Override
	public String getTemporaryFolderPrefix() {
		return getClass().getName() + "@" + instanceId;
	}
	
	CheckingOption getClassFileCheckingOption() {
		return FileSystemItem.CheckingOption.forLabel(
			config.resolveStringValue(
				Configuration.Key.CLASS_PATH_HUNTER_SEARCH_CONFIG_CHECK_FILE_OPTIONS,
				Configuration.DEFAULT_VALUES
			)
		);
	}
	
	public Supplier<Map<String, String>> computeByClassesSearching(Collection<String> classRepositories) {
		return computeByClassesSearching(classRepositories, null, ClassCriteria.create());
	}
	
	public Supplier<Map<String, String>> computeByClassesSearching(
		Collection<String> classRepositories,
		ClassCriteria classCriteria
	) {
		return computeByClassesSearching(classRepositories, null, classCriteria);
	}
	
	public Supplier<Map<String, String>> computeByClassesSearching(CacheableSearchConfig searchConfig) {
		return compute(searchConfig.getPaths(), (toBeAdjuested) -> {
			if (searchConfig.scanFileCriteria.hasNoPredicate()) {
				searchConfig.withScanFileCriteria(
					FileSystemItem.Criteria.forClassTypeFiles(
						getClassFileCheckingOption()
					)
				);
			}
			try(SearchResult result = classPathHunter.loadInCache(
					searchConfig
				).find()
			) {	
				return result.getClassPaths();
			}
		});
	}
			
	
	public Supplier<Map<String, String>> computeByClassesSearching(
		Collection<String> classRepositories,
		Collection<String> pathsToBeRefreshed,
		ClassCriteria classCriteria
	) {	
		CacheableSearchConfig searchConfig = SearchConfig.forPaths(classRepositories).by(
			classCriteria
		).optimizePaths(
			true
		);
		if (pathsToBeRefreshed != null) {
			searchConfig.checkForAddedClassesForAllPathThat(pathsToBeRefreshed::contains);
		}
		return computeByClassesSearching(searchConfig);
	}
	
	
	public Supplier<Map<String, String>> computeFromSources(
		Collection<String> sources,
		Collection<String> classRepositories
	) {
		return computeFromSources(sources, classRepositories, null, null);
	}
	
	public Supplier<Map<String, String>> computeFromSources(
		Collection<String> sources,
		Collection<String> classRepositories,
		ClassCriteria otherClassCriteria
	) {
		Collection<String> imports = new HashSet<>();
		for (String sourceCode : sources) {
			imports.addAll(SourceCodeHandler.extractImports(sourceCode));
		}
		ClassCriteria classCriteria = ClassCriteria.create().className(
			className -> 
				imports.contains(className)
		);
		if (otherClassCriteria != null) {
			classCriteria = classCriteria.or(otherClassCriteria);
		}
		return computeByClassesSearching(classRepositories, classCriteria);
	}
	
	@SafeVarargs
	public final Collection<String> searchWithoutTheUseOfCache(ClassCriteria classCriteria, Collection<String>... pathColls) {
		FileSystemItem.CheckingOption checkFileOption = 
			getClassFileCheckingOption();
		Collection<String> classPaths = new HashSet<>();
		try (SearchResult result = classPathHunter.findBy(
				SearchConfig.withoutUsingCache().addPaths(pathColls).by(
					classCriteria
				).withScanFileCriteria(
					FileSystemItem.Criteria.forClassTypeFiles(checkFileOption)
				).optimizePaths(
					true
				)
			)
		) {	
			for (FileSystemItem classPath : result.getClassPaths()) {
				classPaths.add(classPath.getAbsolutePath());
				classPath.reset();
			}
		}
		return classPaths;
	}
	
	public Collection<String> searchWithoutTheUseOfCache(ClassCriteria classCriteria, String... path) {
		return searchWithoutTheUseOfCache(classCriteria, Arrays.asList(path));
	}
	
	public Supplier<Map<String, String>> computeFromSources(
		Collection<String> sources,
		Collection<String> classRepositories,
		Predicate<FileSystemItem> pathsToBeRefreshedPredicate,
		BiPredicate<FileSystemItem, JavaClass> javaClassFilterAdditionalFilter
	) {
		Collection<String> imports = new HashSet<>();
		for (String sourceCode : sources) {
			imports.addAll(SourceCodeHandler.extractImports(sourceCode));
		}
		BiPredicate<FileSystemItem, JavaClass> javaClassFilter = (classFile, javaClass) -> 
			imports.contains(javaClass.getName())
		;
		
		if (javaClassFilterAdditionalFilter != null) {
			javaClassFilter = javaClassFilter.or(javaClassFilterAdditionalFilter);
		}
		return compute(classRepositories, pathsToBeRefreshedPredicate, javaClassFilter);
	}
	
	public Supplier<Map<String, String>> compute(
		Collection<String> classRepositories,
		BiPredicate<FileSystemItem, JavaClass> javaClassProcessor
	) {
		return compute(classRepositories, null, javaClassProcessor);
	}
	
	Map<String, ClassLoader> computeClassPathsAndAddThemToClassLoader(
		ClassLoader classLoader,
		Collection<String> classRepositories,
		String className,
		Collection<String> notFoundClasses
	) {
		return computeClassPathsAndAddThemToClassLoader(classLoader, classRepositories, null, className, notFoundClasses);
	}
	
	Map<String, ClassLoader> computeClassPathsAndAddThemToClassLoader(
		ClassLoader classLoader,
		Collection<String> classRepositories,
		Collection<String> pathsToBeRefreshed,
		String className,
		Collection<String> notFoundClasses
	) {	
		Predicate<FileSystemItem> pathsToBeRefreshedPredicate = null;
		if (pathsToBeRefreshed != null) {
			pathsToBeRefreshedPredicate =  fileSystemItem -> pathsToBeRefreshed.contains(fileSystemItem.getAbsolutePath());
		}
		
		Collection<String> notFoundClassClassPaths = new HashSet<>();
		BiPredicate<FileSystemItem, JavaClass> criteriaOne = (fileSystemItemCls, javaClass) -> {
			if (javaClass.getName().equals(className)) {
				String classAbsolutePath = fileSystemItemCls.getAbsolutePath();
				notFoundClassClassPaths.add(classAbsolutePath.substring(0, classAbsolutePath.lastIndexOf("/" + javaClass.getPath())));
				return true;
			}				
			return false;
		};
		
		Collection<String> notFoundClassesClassPaths = new HashSet<>();
		BiPredicate<FileSystemItem, JavaClass> criteriaTwo = (fileSystemItemCls, javaClass) -> {
			for (String notFoundClass : notFoundClasses) {
				if (javaClass.getName().equals(notFoundClass)) {
					String classAbsolutePath = fileSystemItemCls.getAbsolutePath();
					notFoundClassesClassPaths.add(classAbsolutePath.substring(0, classAbsolutePath.lastIndexOf("/" + javaClass.getPath())));
					return true;
				}
			}			
			return false;
		};
		
		Map<String, String> classPaths = compute(
			classRepositories,
			pathsToBeRefreshedPredicate,
			criteriaOne.or(criteriaTwo)
		).get();
		
		ClassLoader targetClassLoader = classLoader;
		Collection<String> classPathsToLoad = new HashSet<>();
		if (!notFoundClassClassPaths.isEmpty()) {
			String notFoundClassClassPath = notFoundClassClassPaths.stream().findFirst().get();
			targetClassLoader = ClassLoaders.getClassLoaderOfPath(classLoader, notFoundClassClassPath);
			if (targetClassLoader == null) {
				String notFoundComputedClassClassPath = classPaths.get(notFoundClassClassPath);
				if (!notFoundComputedClassClassPath.equals(notFoundClassClassPath)) {
					targetClassLoader = ClassLoaders.getClassLoaderOfPath(classLoader, notFoundComputedClassClassPath);
					if (targetClassLoader == null) {
						classPathsToLoad.add(notFoundComputedClassClassPath);
					}
				} else {
					classPathsToLoad.add(notFoundComputedClassClassPath);
				}
			}
		}
		if (targetClassLoader == null) {
			targetClassLoader = classLoader;
		}
		
		Map<String, ClassLoader> addedClassPathsForClassLoader = new HashMap<>();
		
		if (!(targetClassLoader instanceof PathScannerClassLoader)) {
			for (String classPath : notFoundClassesClassPaths) {
				classPathsToLoad.add(classPaths.get(classPath));
			}
			
			for (String classPath :  classPathsToLoad) {
				if (!ClassLoaders.addClassPath(
					targetClassLoader,
					absolutePath -> 
						pathsToBeRefreshed != null && pathsToBeRefreshed.contains(absolutePath),
					classPath
				).isEmpty()) {
					logInfo("Added class path {} to {}", classPath, targetClassLoader.toString());
					addedClassPathsForClassLoader.put(classPath, targetClassLoader);
				} else {
					logInfo("Class path {} already present in {}", classPath, targetClassLoader.toString());
				}
			}
		} else {
			PathScannerClassLoader pathScannerClassLoader = (PathScannerClassLoader)targetClassLoader;
			classPathsToLoad = new HashSet<>();
			if (!notFoundClassClassPaths.isEmpty()) {
				classPathsToLoad.addAll(notFoundClassClassPaths);
			}
			if (!notFoundClassesClassPaths.isEmpty()) {
				classPathsToLoad.addAll(notFoundClassesClassPaths);
			}
			for (String addedClassPath : pathScannerClassLoader.scanPathsAndAddAllByteCodesFound(
				classPathsToLoad,
				absolutePath -> 
					pathsToBeRefreshed != null && pathsToBeRefreshed.contains(absolutePath)
			)) {
				addedClassPathsForClassLoader.put(addedClassPath, pathScannerClassLoader);
			}
		}
		return addedClassPathsForClassLoader;
	}
	
	public Supplier<Map<String, String>> compute(
			Collection<String> classRepositories,
			Predicate<FileSystemItem> pathsToBeRefreshedPredicate,
			BiPredicate<FileSystemItem, JavaClass> javaClassFilter
		) {		
			FileSystemItem.Criteria classFileFilter = FileSystemItem.Criteria.forClassTypeFiles(
				getClassFileCheckingOption()
			);
			return compute(
				classRepositories, 
				null,
				clsRepositories -> {
					Collection<FileSystemItem> classPaths = new HashSet<>();
					for (String classRepositoryPath : clsRepositories) {
						FileSystemItem classRepository = FileSystemItem.ofPath(classRepositoryPath);
						if (pathsToBeRefreshedPredicate != null && pathsToBeRefreshedPredicate.test(classRepository)) {
							classRepository.refresh();
						}					
						classRepository.findInAllChildren(classFileFilter.and().allFileThat(fileSystemItemCls -> 
								JavaClass.extractByUsing(fileSystemItemCls.toByteBuffer(), javaClass -> {
									if (javaClassFilter.test(fileSystemItemCls, javaClass)) {
										String classAbsolutePath = fileSystemItemCls.getAbsolutePath();
										classPaths.add(
											FileSystemItem.ofPath(
												classAbsolutePath.substring(0, classAbsolutePath.lastIndexOf("/" + javaClass.getPath()))
											)
										);
										return true;
									}
									return false;
								})
							)
						);
					}
					return classPaths;
				}
			);
		}
		
		private Supplier<Map<String, String>> compute(
			Collection<String> classRepositories,
			Function<Collection<String>, Collection<FileSystemItem>> adjustedClassPathsSupplier
		) {
			return compute(classRepositories, fileSystemItem -> false, adjustedClassPathsSupplier);
		}
		
		private Supplier<Map<String, String>> compute(
			Collection<String> classRepositories,
			Predicate<FileSystemItem> pathsToBeRefreshedPredicate,
			Function<Collection<String>, Collection<FileSystemItem>> callRepositoriesSupplier
		) {
			Map<String, String> classPaths = new HashMap<>();
			Collection<FileSystemItem> effectiveClassPaths = callRepositoriesSupplier.apply(classRepositories);
			
			Collection<QueuedTasksExecutor.ProducerTask<String>> pathsCreationTasks = new HashSet<>(); 
			
			if (pathsToBeRefreshedPredicate == null) {
				pathsToBeRefreshedPredicate = fileSystemItem -> false;
			}	
			
			if (!effectiveClassPaths.isEmpty()) {
				for (FileSystemItem fsObject : effectiveClassPaths) {
					if (pathsToBeRefreshedPredicate.test(fsObject)) {
						fsObject.refresh();
					}
					if (fsObject.isCompressed()) {					
						ThrowingRunnable.run(() -> {
							synchronized (this) {
								FileSystemItem classPath = FileSystemItem.ofPath(
									classPathsBasePath.getAbsolutePath() + "/" + Paths.toSquaredPath(fsObject.getAbsolutePath(), fsObject.isFolder())
								);
								if (!classPath.refresh().exists()) {
									pathsCreationTasks.add(
										BackgroundExecutor.createTask(() -> {
											FileSystemItem copy = fsObject.copyTo(classPathsBasePath.getAbsolutePath());
											File target = new File(classPath.getAbsolutePath());
											new File(copy.getAbsolutePath()).renameTo(target);
											return Paths.clean(target.getAbsolutePath());
										}).submit()
									);
								}
								classPaths.put(
									fsObject.getAbsolutePath(),
									classPath.getAbsolutePath()
								);
								//Free memory
								//classPath.reset();
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
		closeResources(() -> classPathsBasePath == null,  () -> {
			unregister(config);
			FileSystemHelper.deleteOnExit(getOrCreateTemporaryFolder());
			classPathsBasePath.destroy();
			classPathsBasePath = null;
			classPathHunter = null;
			config = null;
			instanceId = null;				
		});
	}
	
}
