package org.burningwave.core.io;


import java.io.File;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.burningwave.Throwables;
import org.burningwave.core.Component;
import org.burningwave.core.common.Strings;
import org.burningwave.core.iterable.IterableObjectHelper;


public class PathHelper implements Component {
	public static String MAIN_CLASS_PATHS = "Main";
	private IterableObjectHelper iterableObjectHelper;
	private Supplier<FileSystemHelper> fileSystemHelperSupplier;
	private FileSystemHelper fileSystemHelper;
	private Map<String, Collection<String>> classPaths;
	private Collection<String> allClassPaths;
	private Properties config;
		
	private PathHelper(Supplier<FileSystemHelper> fileSystemHelperSupplier, IterableObjectHelper iterableObjectHelper, Properties config) {
		this.iterableObjectHelper = iterableObjectHelper;
		this.fileSystemHelperSupplier = fileSystemHelperSupplier;
		classPaths = new ConcurrentHashMap<>();
		allClassPaths = ConcurrentHashMap.newKeySet();
		loadMainClassPaths();
		this.config = config;
	}
	
	public static PathHelper create(Supplier<FileSystemHelper> fileSystemHelperSupplier, IterableObjectHelper iterableObjectHelper, Properties config) {
		return new PathHelper(fileSystemHelperSupplier, iterableObjectHelper, config);
	}
	
	private FileSystemHelper getFileSystemHelper() {
		return fileSystemHelper != null ?
			fileSystemHelper	:
			(fileSystemHelper = fileSystemHelperSupplier.get());
	}
	
	private void loadMainClassPaths() {
		String classPaths = System.getProperty("java.class.path");
		if (Strings.isNotEmpty(classPaths)) {
			addClassPaths(
				MAIN_CLASS_PATHS,
				Stream.of(
					classPaths.split(System.getProperty("path.separator"))
				).map(path ->
					Strings.Paths.clean(path)
				).collect(
					Collectors.toCollection(
						LinkedHashSet::new
					)
				)
			);
		}
		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		while (classLoader != null) {
			if (classLoader instanceof URLClassLoader) {
				addClassPaths(
					MAIN_CLASS_PATHS,
					Stream.of(
						((URLClassLoader)classLoader).getURLs()
					).map(uRL -> 
						Strings.Paths.clean(uRL.getFile())
					).collect(
						Collectors.toCollection(
							LinkedHashSet::new
						)
					)
				);
			}
			classLoader = classLoader.getParent();
		}
	}
	
	@SuppressWarnings("unused")
	private void loadClassPaths() {
		config.forEach((key, value) -> {
			if (((String)key).startsWith("classPaths.")) {
				String classPathsName = ((String)key).substring("classPaths.".length());
				loadClassPaths(classPathsName);
			}
		});
	}
	
	private void loadClassPaths(String classPathsName) {
		String classPathsNamePropertyName = "classPaths." + classPathsName;
		String classPaths = config.getProperty(classPathsNamePropertyName);
		if (classPaths != null) {
			Collection<String> mainClassPaths = getClassPaths(MAIN_CLASS_PATHS);
			if (classPaths.contains("${classPaths}")) {
				for (String mainClassPath : mainClassPaths) {
					Map<String, String> defaultValues = new LinkedHashMap<>();
					defaultValues.put("classPaths", mainClassPath);
					classPaths = Strings.Paths.clean(iterableObjectHelper.get(config, classPathsNamePropertyName, defaultValues));
					for (String classPath : classPaths.split(";")) {
						if (new File(classPath).exists()) {
							addClassPath(classPathsName, classPath);
						}
					}
				}	
			} else {
				for (String classPath : classPaths.split(";")) {
					addClassPath(classPathsName, iterableObjectHelper.get(config, (String)classPath, null));
				}
			}
		}
	}
	
	public Collection<String> getMainClassPaths() {
		return getClassPaths(MAIN_CLASS_PATHS);
	}
	
	public Collection<String> getAllClassPaths() {
		return allClassPaths;
	}
	
	public Collection<String> getClassPaths(String... names) {
		Collection<String> classPaths = new LinkedHashSet<>();
		if (names != null && names.length > 0) {
			for (String name : names) {
				Collection<String> classPathsFound = this.classPaths.get(name);
				if (classPathsFound != null) {
					classPaths.addAll(classPathsFound);
				} else {
					loadClassPaths(name);
					classPathsFound = this.classPaths.get(name);
					if (classPathsFound != null) {
						classPaths.addAll(classPathsFound);
					} else {
						logWarn("classPaths named " + name + " is not defined");
					}
				}
				
			}
		}
		return classPaths;
	}
	
	public Collection<String> getOrCreateClassPaths(String name) {
		Collection<String> classPathsGroup = null;
		if ((classPathsGroup = this.classPaths.get(name)) == null) {
			synchronized (this.classPaths) {
				if ((classPathsGroup = this.classPaths.get(name)) == null) {
					classPathsGroup = ConcurrentHashMap.newKeySet();
					this.classPaths.put(name, classPathsGroup);
				}
			}
		}
		return classPathsGroup;
	}
	
	public Collection<String> addClassPaths(String name, Collection<String> classPaths) {
		if (classPaths != null) {
			Collection<String> classPathsFound = getOrCreateClassPaths(name);
			classPathsFound.addAll(classPaths);
			allClassPaths.addAll(classPaths);
			return classPathsFound;
		} else {
			throw Throwables.toRuntimeException("classPaths parameter is null");
		}
	}
	
	public void addClassPath(String name, String classPaths) {
		addClassPaths(name, Arrays.asList(classPaths));
	}
	
	
	public String getAbsolutePath(String resourceRelativePath) {
		String path = Objects.requireNonNull(
			getFileSystemHelper().getResource(resourceRelativePath), 
			"Could not find file " + resourceRelativePath
		).getAbsolutePath();
		if (System.getProperty("os.name").toLowerCase().contains("windows")) {
			if (path.startsWith("/")) {
				path = path.replaceFirst("/", "");
			}
		} else {
			path = path.replace("\\", "/");
		}
		return path;
	}
	
	
	public CheckResult check(Collection<String> pathCollection1, Collection<String> pathCollection2) {
		CheckResult checkPathsResult = new CheckResult();
		for (String path2 : pathCollection2) {
			File path2AsFile = new File(path2);
			String path2Normalized = Strings.Paths.clean(path2AsFile.getAbsolutePath());
			int result = -1;
			for (String path1 : pathCollection1) {
				File pathAsFile1 = new File(path1);
				String path1Normalized = Strings.Paths.clean(pathAsFile1.getAbsolutePath());
				if (
					(!pathAsFile1.isDirectory() && !path2AsFile.isDirectory() && 
						path1Normalized.equals(path2Normalized)) ||
					(pathAsFile1.isDirectory() && !path2AsFile.isDirectory() &&
						path2Normalized.startsWith(path1Normalized + "/"))
				) {	
					checkPathsResult.addContainedPath(path1);
					result = 0;
				} else if (
					!pathAsFile1.isDirectory() && path2AsFile.isDirectory() && 
						path1Normalized.startsWith(path2Normalized + "/")) {
					checkPathsResult.addPartialContainedFile(path2, path1);
					result = 1;
				} else if (pathAsFile1.isDirectory() && path2AsFile.isDirectory()) {
					if ((path2Normalized + "/").startsWith(path1Normalized + "/")) {
						checkPathsResult.addContainedPath(path1);
						result = 0;
					} else if ((path1Normalized + "/").startsWith(path2Normalized + "/")) {
						checkPathsResult.addPartialContainedDirectory(path2, path1);
						result = 1;
					}
				}
				if (result != -1) {
					break;
				}				
			}
			if (result != 0) {
				checkPathsResult.addNotContainedPath(path2);
			}
		}
		return checkPathsResult;
	}
	
	public void optimize(Collection<String> paths) {
		Collection<String> copyOfPaths = new LinkedHashSet<>(paths);
		Collection<String> toBeRemoved = new LinkedHashSet<>();
		Iterator<String> pathsItr = copyOfPaths.iterator();
		int i = 0;
		while (pathsItr.hasNext()) {
			String path = pathsItr.next();
			File pathAsFile = new File(path);
			Iterator<String> pathsItrNested = copyOfPaths.iterator();
			int j = 0;
			while (pathsItrNested.hasNext()) {
				String nestedPath = pathsItrNested.next();
				if (i != j) {					
					File nestedPathAsFile = new File(nestedPath);
					if (!nestedPathAsFile.isDirectory() && !pathAsFile.isDirectory()) {
						if (Strings.Paths.clean(nestedPathAsFile.getAbsolutePath()).equals(Strings.Paths.clean(pathAsFile.getAbsolutePath()))) {
							toBeRemoved.add(path);
						}
					} else if (nestedPathAsFile.isDirectory() && !pathAsFile.isDirectory()) {
						if (Strings.Paths.clean(pathAsFile.getAbsolutePath()).startsWith(Strings.Paths.clean(nestedPathAsFile.getAbsolutePath()) + "/")) {
							toBeRemoved.add(path);
						}
					} else {
						if ((Strings.Paths.clean(pathAsFile.getAbsolutePath()) + "/").startsWith(Strings.Paths.clean(nestedPathAsFile.getAbsolutePath()) + "/")) {
							toBeRemoved.add(path);
						}
					}
				}
				j++;
			}
			i++;
		}
		if (!toBeRemoved.isEmpty()) {
			copyOfPaths.removeAll(toBeRemoved);
			paths.clear();
			paths.addAll(copyOfPaths);
		}
	}
	
	
	public Collection<String> getAllClassPathThat(Predicate<String> pathPredicate) {
		return getAllClassPaths().stream().filter(pathPredicate).collect(Collectors.toSet());
	}
	
	public String getClassPath(Predicate<String> pathPredicate) {
		Collection<String> classPathsFound = getAllClassPathThat(pathPredicate);
		if (classPathsFound.size() > 1) {
			throw Throwables.toRuntimeException("Found more than one class path for predicate " + pathPredicate);
		}
		return classPathsFound.stream().findFirst().orElseGet(() -> null);
	}
	
	@Override
	public void close() {
		iterableObjectHelper = null;
		classPaths.forEach((key, value) -> {
			value.clear();
			classPaths.remove(key);
		});
		classPaths.clear();
		classPaths = null;
		allClassPaths.clear();
		allClassPaths = null;
		config = null;
	}
	
	
	public static class CheckResult {
		private final Collection<String> notContainedPaths;
		private final Map<String, Collection<String>> partialContainedDirectories;
		private final Map<String, Collection<String>> partialContainedFiles;
		private final Collection<String> containedPaths;

		private CheckResult() {
			notContainedPaths = new LinkedHashSet<>();
			containedPaths = new LinkedHashSet<>();
			partialContainedDirectories = new LinkedHashMap<>();
			partialContainedFiles = new LinkedHashMap<>();
		}
		
		
		void addNotContainedPath(String path) {
			notContainedPaths.add(path);
		}
		
		void addPartialContainedDirectory(String path1, String path2) {
			Collection<String> paths = partialContainedDirectories.get(path1);
			if (paths == null) {
				paths = new LinkedHashSet<>();
				partialContainedDirectories.put(path1, paths);
			}
			paths.add(path2);
		}
		
		void addPartialContainedFile(String path1, String path2) {
			Collection<String> paths = partialContainedFiles.get(path1);
			if (paths == null) {
				paths = new LinkedHashSet<>();
				partialContainedFiles.put(path1, paths);
			}
			paths.add(path2);
		}
		
		void addContainedPath(String path) {
			containedPaths.add(path);
		}
		
		public Collection<String> getNotContainedPaths() {
			return notContainedPaths;
		}
		
		public Map<String, Collection<String>> getPartialContainedFiles() {
			return partialContainedFiles;
		}

		public Map<String, Collection<String>> getPartialContainedDirectories() {
			return partialContainedDirectories;
		}


		public Collection<String> getContainedPaths() {
			return containedPaths;
		}
	}
}
