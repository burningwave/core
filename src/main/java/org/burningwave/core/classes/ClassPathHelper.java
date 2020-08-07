package org.burningwave.core.classes;

import static org.burningwave.core.assembler.StaticComponentContainer.FileSystemHelper;
import static org.burningwave.core.assembler.StaticComponentContainer.HighPriorityTasksExecutor;
import static org.burningwave.core.assembler.StaticComponentContainer.Paths;
import static org.burningwave.core.assembler.StaticComponentContainer.SourceCodeHandler;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

import org.burningwave.core.Component;
import org.burningwave.core.classes.ClassPathHunter.SearchResult;
import org.burningwave.core.concurrent.QueuedTasksExecutor;
import org.burningwave.core.function.ThrowingRunnable;
import org.burningwave.core.io.FileSystemItem;
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
	
	
	@Override
	public String getTemporaryFolderPrefix() {
		return getClass().getName() + "@" + instanceId;
	}
	
	public static ClassPathHelper create(ClassPathHunter classPathHunter, Properties config) {
		return new ClassPathHelper(classPathHunter, config);
	}
	
	public Supplier<Collection<String>> computeByClassesSearching(Collection<String> classRepositories) {
		return computeByClassesSearching(classRepositories, ClassCriteria.create());
	}
	
	public Supplier<Collection<String>> computeByClassesSearching(
		Collection<String> classRepositories,
		ClassCriteria classCriteria
	) {
		return compute(classRepositories, (toBeAdjuested) -> {
			FileSystemItem.CheckingOption checkFileOption = 
				FileSystemItem.CheckingOption.forLabel(config.resolveStringValue(
					Configuration.Key.CLASS_PATH_HUNTER_SEARCH_CONFIG_CHECK_FILE_OPTIONS,
					Configuration.DEFAULT_VALUES
				)
			);
			try(SearchResult result = classPathHunter.loadInCache(
					SearchConfig.forPaths(toBeAdjuested).withScanFileCriteria(
						FileSystemItem.Criteria.forClassTypeFiles(checkFileOption)
					).by(
						classCriteria
					).optimizePaths(
						true
					)
				).find()
			) {	
				return result.getClassPaths();
			}
		});
	}
	
	private Supplier<Collection<String>> compute(
		Collection<String> classRepositories,
		Function<Collection<String>, Collection<FileSystemItem>> adjustedClassPathsSupplier
	) {
			Collection<String> classPaths = new HashSet<>();
			Collection<FileSystemItem> effectiveClassPaths = adjustedClassPathsSupplier.apply(classRepositories);
			
			Collection<QueuedTasksExecutor.ProducerTask<String>> pathsCreationTasks = new HashSet<>(); 
			
			if (!effectiveClassPaths.isEmpty()) {
				for (FileSystemItem fsObject : effectiveClassPaths) {
					if (fsObject.isCompressed()) {					
						ThrowingRunnable.run(() -> {
							synchronized (this) {
								FileSystemItem classPath = FileSystemItem.ofPath(
									classPathsBasePath.getAbsolutePath() + "/" + Paths.toSquaredPath(fsObject.getAbsolutePath(), fsObject.isFolder())
								);
								if (!classPath.refresh().exists()) {
									pathsCreationTasks.add(
										HighPriorityTasksExecutor.addWithCurrentThreadPriority(() -> {
											FileSystemItem copy = fsObject.copyTo(classPathsBasePath.getAbsolutePath());
											File target = new File(classPath.getAbsolutePath());
											new File(copy.getAbsolutePath()).renameTo(target);
											return Paths.clean(target.getAbsolutePath());
										})
									);
								}
								classPaths.add(
									classPath.getAbsolutePath()
								);
								//Free memory
								//classPath.reset();
							}
						});
					} else {
						classPaths.add(fsObject.getAbsolutePath());
					}
				}
			}
			return () -> {
				pathsCreationTasks.stream().forEach(pathsCreationTask -> pathsCreationTask.join());
				return classPaths;
			};
		}
	
	public Supplier<Collection<String>> computeFromSources(
		Collection<String> sources,
		Collection<String> classRepositories
	) {
		return computeFromSources(sources, classRepositories, null);
	}
	
	public Supplier<Collection<String>> computeFromSources(
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
			FileSystemItem.CheckingOption.forLabel(config.resolveStringValue(
				Configuration.Key.CLASS_PATH_HUNTER_SEARCH_CONFIG_CHECK_FILE_OPTIONS,
				Configuration.DEFAULT_VALUES
			)
		);
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
	
	@Override
	public void close() {
		closeResources(() -> classPathsBasePath == null,  () -> {
			unregister(config);
			FileSystemHelper.deleteOnExit(classPathsBasePath.getAbsolutePath());
			classPathsBasePath.destroy();
			classPathsBasePath = null;
			classPathHunter = null;
			config = null;
			instanceId = null;				
		});
	}
	
}
