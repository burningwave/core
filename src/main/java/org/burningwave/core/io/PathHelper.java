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

import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggersRepository;
import static org.burningwave.core.assembler.StaticComponentContainer.Paths;
import static org.burningwave.core.assembler.StaticComponentContainer.Strings;
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.burningwave.core.Component;
import org.burningwave.core.function.ThrowingSupplier;
import org.burningwave.core.iterable.IterableObjectHelper;
import org.burningwave.core.iterable.Properties;
import org.burningwave.core.iterable.Properties.Event;


public class PathHelper implements Component {
	public static final Object MAIN_CLASS_PATHS_EXTENSION_DEFAULT_VALUE = "//${system.properties:java.home}/lib//children:.*\\.jar|.*\\.jmod;//${system.properties:java.home}/lib/ext//children:.*\\.jar|.*\\.jmod;//${system.properties:java.home}/jmods//children:.*\\.jar|.*\\.jmod;";
	public static String PATHS_KEY_PREFIX = "paths.";
	public static String MAIN_CLASS_PATHS = "main-class-paths";
	public static String MAIN_CLASS_PATHS_EXTENSION = MAIN_CLASS_PATHS + ".extension";
	private static Pattern PATH_REGEX = Pattern.compile("\\/\\/(.*)\\/\\/(children|allChildren):(.*)");
	private IterableObjectHelper iterableObjectHelper;
	private Map<String, Collection<String>> pathGroups;
	private Collection<String> allPaths;
	private Properties config;
		
	private PathHelper(IterableObjectHelper iterableObjectHelper, Properties config) {
		this.iterableObjectHelper = iterableObjectHelper;
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
	
	public static PathHelper create(IterableObjectHelper iterableObjectHelper, Properties config) {
		return new PathHelper(iterableObjectHelper, config);
	}
	
	private void loadMainClassPaths() {
		String classPaths = System.getProperty("java.class.path");
		if (Strings.isNotEmpty(classPaths)) {
			addPaths(
				MAIN_CLASS_PATHS,
				Stream.of(
					classPaths.split(System.getProperty("path.separator"))
				).map(path ->
					Paths.clean(path)
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
						Paths.clean(uRL.getFile())
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
	
	private void loadAllPaths() {
		config.forEach((key, value) -> {
			if (((String)key).startsWith(PATHS_KEY_PREFIX)) {
				String pathGroupName = ((String)key).substring(PATHS_KEY_PREFIX.length());
				loadPaths(pathGroupName);
			}
		});
	}
	
	public Collection<String> getMainClassPaths() {
		return getPaths(MAIN_CLASS_PATHS);
	}
	
	public Collection<String> getAllPaths() {
		Collection<String> allPaths = ConcurrentHashMap.newKeySet();
		allPaths.addAll(this.allPaths);
		return allPaths;
	}
	
	public Collection<String> getPaths(String... names) {
		Collection<String> pathGroup = new HashSet<>();
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
	
	private Collection<String> getOrCreatePathGroup(String name) {
		Collection<String> classPathsGroup = null;
		if ((classPathsGroup = this.pathGroups.get(name)) == null) {
			synchronized(this.pathGroups) {
				if ((classPathsGroup = this.pathGroups.get(name)) == null) {
					classPathsGroup = ConcurrentHashMap.newKeySet();
					this.pathGroups.put(name, classPathsGroup);
				}
			}
		}
		return classPathsGroup;
	}
	
	private void loadPaths(String pathGroupName) {
		loadPaths(pathGroupName, null);
	}
	
	public Collection<String> loadPaths(String pathGroupName, String paths) {
		String pathGroupPropertyName = PATHS_KEY_PREFIX + pathGroupName;
		Collection<String> groupPaths = ConcurrentHashMap.newKeySet();
		synchronized(this) {
			String currentPropertyPaths = config.getProperty(pathGroupPropertyName);
			if (Strings.isNotEmpty(currentPropertyPaths) && Strings.isNotEmpty(paths)) {
				if (!currentPropertyPaths.endsWith(";")) {
					currentPropertyPaths += ";";
				}
				currentPropertyPaths += paths;
				config.put(pathGroupPropertyName, currentPropertyPaths);
			} else if (Strings.isNotEmpty(paths)) {
				currentPropertyPaths = paths;
				config.put(pathGroupPropertyName, currentPropertyPaths);
			}
			paths = currentPropertyPaths;
			
			if (paths != null) {
				if (iterableObjectHelper.containsValue(config, pathGroupPropertyName, null, "${classPaths}")) {
					Collection<String> mainClassPaths = getPaths(MAIN_CLASS_PATHS);
					for (String mainClassPath : mainClassPaths) {
						Map<String, String> defaultValues = new LinkedHashMap<>();
						defaultValues.put("classPaths", mainClassPath);
						paths = Paths.clean(iterableObjectHelper.get(config, pathGroupPropertyName, defaultValues)).replaceAll(";{2,}", ";");
						for (String path : paths.split(";")) {
							groupPaths.addAll(addPath(pathGroupName, path));
						}
					}	
				} else {
					for (String path : iterableObjectHelper.get(config, pathGroupPropertyName, null).split(";")) {
						groupPaths.addAll(addPath(pathGroupName, path));
					}
				}
			}
		}
		return groupPaths;
	}	
	
	private Collection<String> addPath(String name, String classPaths) {
		return addPaths(name, Arrays.asList(classPaths));
	}
	
	private Collection<String> addPaths(String groupName, Collection<String> paths) {
		if (paths != null) {
			Collection<String> pathGroup = getOrCreatePathGroup(groupName);
			Integer loggingLevelFlags = ManagedLoggersRepository.getLoggingLevelFlags(FileSystemItem.class);
			ManagedLoggersRepository.disableLogging(FileSystemItem.class);
			for (String path : paths) {
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
			}
			ManagedLoggersRepository.setLoggingLevelFlags(FileSystemItem.class, loggingLevelFlags);
			return pathGroup;
		} else {
			throw Throwables.toRuntimeException("classPaths parameter is null");
		}
	}
	
	public String getAbsolutePathOfResource(String resourceRelativePath) {
		return Optional.ofNullable(
			getResource(resourceRelativePath)
		).map(resource -> {
			return resource.getAbsolutePath();
		}).orElseGet(() -> {
			logInfo("Could not find file {}", resourceRelativePath);
			return null;
		});
	}
	
	public Collection<FileSystemItem> getResources(String... resourcesRelativePaths) {
		return getResources((coll, file) -> coll.add(file), resourcesRelativePaths);
	}
	
	public FileSystemItem getResource(String resourceRelativePath) {
		return getResource(
				(coll, file) -> 
					coll.add(file), resourceRelativePath);
	}
	
	
	public <T> Collection<T> getResources(
		BiConsumer<Collection<T>, FileSystemItem> fileConsumer,
		String... resourcesRelativePaths
	) {
		Collection<T> files = new HashSet<>();
		if (resourcesRelativePaths != null && resourcesRelativePaths.length > 0) {
			Integer loggingLevelFlags = ManagedLoggersRepository.getLoggingLevelFlags(FileSystemItem.class);
			ManagedLoggersRepository.disableLogging(FileSystemItem.class);
			for (String resourceRelativePath : resourcesRelativePaths) {
				getAllPaths().stream().forEach((path) -> {
					FileSystemItem fileSystemItem = FileSystemItem.ofPath(path + "/" + resourceRelativePath);
					if (fileSystemItem.exists()) {
						fileConsumer.accept(files, fileSystemItem);
					}
				});
			}
			ManagedLoggersRepository.setLoggingLevelFlags(FileSystemItem.class, loggingLevelFlags);
		}
		return files;
	}
	
	
	public <T> T getResource(BiConsumer<Collection<T>, FileSystemItem> fileConsumer, String resourceRelativePath) {
		Collection<T> files = getResources(fileConsumer, resourceRelativePath);
		if (files.size() > 1) {
			throw Throwables.toRuntimeException("Found more than one resource under relative path " + resourceRelativePath);
		}
		return files.stream().findFirst().orElse(null);
	}
	
	public Collection<InputStream> getResourcesAsStreams(String... resourcesRelativePaths) {
		return getResources((coll, fileSystemItem) -> coll.add(fileSystemItem.toInputStream()), resourcesRelativePaths);
	}
	
	public InputStream getResourceAsStream(String resourceRelativePath) {
		return getResource((coll, fileSystemItem) ->
			coll.add(fileSystemItem.toInputStream()), 
			resourceRelativePath
		);
	}
	
	public StringBuffer getResourceAsStringBuffer(String resourceRelativePath) {
		return ThrowingSupplier.get(() -> {
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(getResourceAsStream(resourceRelativePath)))) {
				StringBuffer result = new StringBuffer();
				String sCurrentLine;
				while ((sCurrentLine = reader.readLine()) != null) {
					result.append(sCurrentLine + "\n");
				}
				return result;
			}
		});
	}
	
	public ComparePathsResult comparePaths(Collection<String> pathCollection1, Collection<String> pathCollection2) {
		ComparePathsResult checkPathsResult = new ComparePathsResult();
		for (String path2 : pathCollection2) {
			FileSystemItem path2AsFile = FileSystemItem.ofPath(path2);
			String path2Normalized = Paths.normalizeAndClean(path2AsFile.getAbsolutePath());
			int result = -1;
			for (String path1 : pathCollection1) {
				FileSystemItem pathAsFile1 = FileSystemItem.ofPath(path1);
				String path1Normalized = Paths.normalizeAndClean(pathAsFile1.getAbsolutePath());
				if (//If path 1 and path 2 are the same file or path 2 is contained in path 1
					(!pathAsFile1.isFolder() && !path2AsFile.isFolder() && 
						path1Normalized.equals(path2Normalized)) ||
					(pathAsFile1.isFolder() && !path2AsFile.isFolder() &&
						path2Normalized.startsWith(path1Normalized + "/"))
				) {	
					checkPathsResult.addContainedPath(path1, path2);
					result = 0;
				} else if (
					//If path 1 is a file contained in path 2 that is a directory
					!pathAsFile1.isFolder() && path2AsFile.isFolder() && 
						path1Normalized.startsWith(path2Normalized + "/")) {
					checkPathsResult.addPartialContainedFile(path2, path1);
					result = 1;
					//If path 1 and path 2 are directories
				} else if (pathAsFile1.isFolder() && path2AsFile.isFolder()) {
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
	
	public Collection<String> optimize(Collection<String> paths) {
		Collection<String> copyOfPaths = new HashSet<>();
		for (String path : paths) {
			copyOfPaths.add(Paths.normalizeAndClean(path));
		}
		paths = new HashSet<>(copyOfPaths);
		Collection<String> toBeRemoved = new HashSet<>();
		Iterator<String> pathsItr = copyOfPaths.iterator();
		int i = 0;
		while (pathsItr.hasNext()) {
			String path = pathsItr.next();
			FileSystemItem pathAsFile = FileSystemItem.ofPath(path);
			Iterator<String> pathsItrNested = copyOfPaths.iterator();
			int j = 0;
			while (pathsItrNested.hasNext()) {
				String nestedPath = pathsItrNested.next();
				if (i != j) {					
					FileSystemItem nestedPathAsFile = FileSystemItem.ofPath(nestedPath);
					if (!nestedPathAsFile.isFolder() && !pathAsFile.isFolder()) {
						if (nestedPathAsFile.getAbsolutePath().equals(pathAsFile.getAbsolutePath())) {
							toBeRemoved.add(path);
						}
					} else if (nestedPathAsFile.isFolder() && !pathAsFile.isFolder()) {
						if (pathAsFile.getAbsolutePath().startsWith(nestedPathAsFile.getAbsolutePath() + "/")) {
							toBeRemoved.add(path);
						}
					} else {
						if ((pathAsFile.getAbsolutePath() + "/").startsWith((nestedPathAsFile.getAbsolutePath()) + "/")) {
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
		return paths;
	}
	
	
	public Collection<String> getPaths(Predicate<String> pathPredicate) {
		return getAllPaths().stream().filter(pathPredicate).collect(Collectors.toSet());
	}
	
	public String getPath(Predicate<String> pathPredicate) {
		Collection<String> classPathsFound = getPaths(pathPredicate);
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
	
	
	public static class ComparePathsResult {
		private final Collection<String> notContainedPaths;
		private final Map<String, Collection<String>> partialContainedDirectories;
		private final Map<String, Collection<String>> partialContainedFiles;
		private final Map<String, Collection<String>> containedPaths;

		private ComparePathsResult() {
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
