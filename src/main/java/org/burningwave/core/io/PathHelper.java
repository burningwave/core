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


import java.io.File;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.burningwave.Throwables;
import org.burningwave.core.Component;
import org.burningwave.core.Strings;
import org.burningwave.core.iterable.IterableObjectHelper;
import org.burningwave.core.iterable.Properties;
import org.burningwave.core.iterable.Properties.Event;


public class PathHelper implements Component {
	public static final Object MAIN_CLASS_PATHS_EXTENSION_DEFAULT_VALUE = "//${system.properties:java.home}/lib//children.*\\.jar|.*\\.jmod;//${system.properties:java.home}/jmods//children.*\\.jar|.*\\.jmod;";
	public static String PATHS_KEY_PREFIX = "paths.";
	public static String MAIN_CLASS_PATHS = "main-class-paths";
	public static String MAIN_CLASS_PATHS_EXTENSION = MAIN_CLASS_PATHS + ".extension";
	private static Pattern PATH_REGEX = Pattern.compile("\\/\\/(.*)\\/\\/(children|allChildren)(.*)");
	private IterableObjectHelper iterableObjectHelper;
	private Supplier<FileSystemHelper> fileSystemHelperSupplier;
	private FileSystemHelper fileSystemHelper;
	private Map<String, Collection<String>> pathGroups;
	private Collection<String> allPaths;
	private Properties config;
		
	private PathHelper(Supplier<FileSystemHelper> fileSystemHelperSupplier, IterableObjectHelper iterableObjectHelper, Properties config) {
		this.iterableObjectHelper = iterableObjectHelper;
		this.fileSystemHelperSupplier = fileSystemHelperSupplier;
		pathGroups = new ConcurrentHashMap<>();
		allPaths = ConcurrentHashMap.newKeySet();
		loadMainClassPaths();
		this.config = config;
		loadAllPaths();	
		listenTo(config);
	}
	
	@Override
	public void receiveNotification(Properties properties, Event event, Object key, Object value) {
		if (event == Event.PUT) {
			String propertyKey = (String)key;
			if (propertyKey.startsWith(PATHS_KEY_PREFIX)) {
				loadPaths(((String)key).replaceFirst(PATHS_KEY_PREFIX, ""));	
			}
		}
		Component.super.receiveNotification(properties, event, key, value);
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
			addPaths(
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
				addPaths(
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
	
	public void loadAllPaths() {
		config.forEach((key, value) -> {
			if (((String)key).startsWith(PATHS_KEY_PREFIX)) {
				String pathGroupName = ((String)key).substring(PATHS_KEY_PREFIX.length());
				loadPaths(pathGroupName);
			}
		});
	}
	
	private void loadPaths(String pathGroupName) {
		String pathGroupPropertyName = PATHS_KEY_PREFIX + pathGroupName;
		String paths = config.getProperty(pathGroupPropertyName);
		if (paths != null) {
			Collection<String> mainClassPaths = getPaths(MAIN_CLASS_PATHS);
			if (paths.contains("${classPaths}")) {
				for (String mainClassPath : mainClassPaths) {
					Map<String, String> defaultValues = new LinkedHashMap<>();
					defaultValues.put("classPaths", mainClassPath);
					paths = Strings.Paths.clean(iterableObjectHelper.get(config, pathGroupPropertyName, defaultValues));
					for (String path : paths.split(";")) {
						addPath(pathGroupName, path);
					}
				}	
			} else {
				for (String classPath : iterableObjectHelper.get(config, pathGroupPropertyName, null).split(";")) {
					addPath(pathGroupName, classPath);
				}
			}
		}
	}
	
	public Collection<String> getMainClassPaths() {
		return getPaths(MAIN_CLASS_PATHS);
	}
	
	public Collection<String> getAllPaths() {
		return allPaths;
	}
	
	public Collection<String> getPaths(String... names) {
		Collection<String> pathGroup = ConcurrentHashMap.newKeySet();
		if (names != null && names.length > 0) {
			for (String name : names) {
				Collection<String> pathsFound = this.pathGroups.get(name);
				if (pathsFound != null) {
					pathGroup.addAll(pathsFound);
				} else {
					loadPaths(name);
					pathsFound = this.pathGroups.get(name);
					if (pathsFound != null) {
						pathGroup.addAll(pathsFound);
					} else {
						logWarn("path group named " + name + " is not defined");
					}
				}
				
			}
		}
		return pathGroup;
	}
	
	public Collection<String> getOrCreatePathGroup(String name) {
		Collection<String> classPathsGroup = null;
		if ((classPathsGroup = this.pathGroups.get(name)) == null) {
			synchronized (this.pathGroups) {
				if ((classPathsGroup = this.pathGroups.get(name)) == null) {
					classPathsGroup = ConcurrentHashMap.newKeySet();
					this.pathGroups.put(name, classPathsGroup);
				}
			}
		}
		return classPathsGroup;
	}
	
	public Collection<String> addPaths(String groupName, Collection<String> paths) {
		if (paths != null) {
			Collection<String> pathGroup = getOrCreatePathGroup(groupName);
			FileSystemItem.disableLog();
			paths.forEach((path) -> {
				if (path.matches(PATH_REGEX.pattern())) {
					Map<Integer, List<String>> groupMap = Strings.extractAllGroups(PATH_REGEX, path);
					FileSystemItem fileSystemItemParent = FileSystemItem.ofPath(groupMap.get(1).get(0));
					if (fileSystemItemParent.exists()) {
						String childrenSet = groupMap.get(2).get(0);
						String childrenSetRegEx = groupMap.get(3).get(0);
						Function<Predicate<FileSystemItem>, Set<FileSystemItem>> childrenSupplier =
							childrenSet.equalsIgnoreCase("children") ?
								fileSystemItemParent::getChildren :
								childrenSet.equalsIgnoreCase("allChildren") ?
									fileSystemItemParent::getAllChildren : null;
						if (childrenSupplier != null) {
							Set<FileSystemItem> childrenFound = childrenSupplier.apply(fileSystemItem -> fileSystemItem.getAbsolutePath().matches(childrenSetRegEx));
							for (FileSystemItem fileSystemItem : childrenFound) {
								pathGroup.add(fileSystemItem.getAbsolutePath());
								allPaths.add(fileSystemItem.getAbsolutePath());
							}
						}
					}
				} else {
					FileSystemItem fileSystemItem = FileSystemItem.ofPath(path);
					if (fileSystemItem.exists()) {
						pathGroup.add(fileSystemItem.getAbsolutePath());
						allPaths.add(fileSystemItem.getAbsolutePath());
					}
				}
			});
			FileSystemItem.enableLog();
			return pathGroup;
		} else {
			throw Throwables.toRuntimeException("classPaths parameter is null");
		}
	}
	
	public void addPath(String name, String classPaths) {
		addPaths(name, Arrays.asList(classPaths));
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
				if (//If path 1 and path 2 are the same file or path 2 is contained in path 1
					(!pathAsFile1.isDirectory() && !path2AsFile.isDirectory() && 
						path1Normalized.equals(path2Normalized)) ||
					(pathAsFile1.isDirectory() && !path2AsFile.isDirectory() &&
						path2Normalized.startsWith(path1Normalized + "/"))
				) {	
					checkPathsResult.addContainedPath(path1, path2);
					result = 0;
				} else if (
					//If path 1 is a file contained in path 2 that is a directory
					!pathAsFile1.isDirectory() && path2AsFile.isDirectory() && 
						path1Normalized.startsWith(path2Normalized + "/")) {
					checkPathsResult.addPartialContainedFile(path2, path1);
					result = 1;
					//If path 1 and path 2 are directories
				} else if (pathAsFile1.isDirectory() && path2AsFile.isDirectory()) {
					//If path 2 is contained in path 1
					if ((path2Normalized + "/").startsWith(path1Normalized + "/")) {
						checkPathsResult.addContainedPath(path1, path2);
						result = 0;
					//If path 1 is contained in path 2
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
		return getAllPaths().stream().filter(pathPredicate).collect(Collectors.toSet());
	}
	
	public String getPath(Predicate<String> pathPredicate) {
		Collection<String> classPathsFound = getAllClassPathThat(pathPredicate);
		if (classPathsFound.size() > 1) {
			throw Throwables.toRuntimeException("Found more than one class path for predicate " + pathPredicate);
		}
		return classPathsFound.stream().findFirst().orElseGet(() -> null);
	}
	
	@Override
	public void close() {
		iterableObjectHelper = null;
		pathGroups.forEach((key, value) -> {
			value.clear();
			pathGroups.remove(key);
		});
		pathGroups.clear();
		pathGroups = null;
		allPaths.clear();
		allPaths = null;
		config = null;
	}
	
	
	public static class CheckResult {
		private final Collection<String> notContainedPaths;
		private final Map<String, Collection<String>> partialContainedDirectories;
		private final Map<String, Collection<String>> partialContainedFiles;
		private final Map<String, Collection<String>> containedPaths;

		private CheckResult() {
			notContainedPaths = new LinkedHashSet<>();
			containedPaths = new LinkedHashMap<>();
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
		
		void addContainedPath(String path1, String path2) {
			Collection<String> paths = containedPaths.get(path1);
			if (paths == null) {
				paths = new LinkedHashSet<>();
				containedPaths.put(path1, paths);
			}
			paths.add(path2);
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


		public Map<String, Collection<String>> getContainedPaths() {
			return containedPaths;
		}
	}
}
