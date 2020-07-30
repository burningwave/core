package org.burningwave.core.classes;

import static org.burningwave.core.assembler.StaticComponentContainer.Paths;
import static org.burningwave.core.assembler.StaticComponentContainer.SourceCodeHandler;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Function;

import org.burningwave.core.Component;
import org.burningwave.core.classes.ClassPathHunter.SearchResult;
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

	private ClassPathHunter classPathHunter;
	private FileSystemItem basePathForLibCopies;
	private FileSystemItem basePathForClassCopies;
	private Properties config;	
	
	public ClassPathHelper(
		ClassPathHunter classPathHunter, Properties config
	) {
		this.classPathHunter = classPathHunter;
		this.basePathForLibCopies = FileSystemItem.of(getOrCreateTemporaryFolder("lib"));
		this.basePathForClassCopies = FileSystemItem.of(getOrCreateTemporaryFolder("classes"));
		this.config = config;
		listenTo(config);
	}
	
	public static ClassPathHelper create(ClassPathHunter classPathHunter, Properties config) {
		return new ClassPathHelper(classPathHunter, config);
	}
	
	public Collection<String> computeByClassesSearching(Collection<String> classRepositories) {
		return computeByClassesSearching(classRepositories, ClassCriteria.create());
	}
	
	public Collection<String> computeByClassesSearching(
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
	
	private Collection<String> compute(
		Collection<String> classRepositories,
		Function<Collection<String>, Collection<FileSystemItem>> adjustedClassPathsSupplier
	) {
			Collection<String> classPaths = new HashSet<>();
			Collection<FileSystemItem> effectiveClassPaths = adjustedClassPathsSupplier.apply(classRepositories);

			if (!effectiveClassPaths.isEmpty()) {
				for (FileSystemItem fsObject : effectiveClassPaths) {
					if (fsObject.isCompressed()) {					
						ThrowingRunnable.run(() -> {
							synchronized (this) {
								FileSystemItem classPathBasePath = fsObject.isArchive() ?
									basePathForLibCopies :
									basePathForClassCopies
								;
								FileSystemItem classPath = FileSystemItem.ofPath(
									classPathBasePath.getAbsolutePath() + "/" + Paths.toSquaredPath(fsObject.getAbsolutePath(), fsObject.isFolder())
								);
								if (!classPath.refresh().exists()) {
									FileSystemItem copy = fsObject.copyTo(classPathBasePath.getAbsolutePath());
									new File(copy.getAbsolutePath()).renameTo(new File(classPath.getAbsolutePath()));
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
			return classPaths;
		}
	
	public Collection<String> computeFromSources(
		Collection<String> sources,
		Collection<String> classRepositories
	) {
		return computeFromSources(sources, classRepositories, null);
	}
	
	public Collection<String> computeFromSources(
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
		unregister(config);
		//FileSystemHelper.delete(basePathForLibCopies.getAbsolutePath());
		basePathForLibCopies.destroy();
		basePathForLibCopies = null;
		//FileSystemHelper.delete(basePathForClassCopies.getAbsolutePath());
		basePathForClassCopies.destroy();
		basePathForClassCopies = null;
		classPathHunter = null;
		config = null;
	}
	
}
