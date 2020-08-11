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

import static org.burningwave.core.assembler.StaticComponentContainer.BackgroundExecutor;
import static org.burningwave.core.assembler.StaticComponentContainer.ClassLoaders;
import static org.burningwave.core.assembler.StaticComponentContainer.Paths;
import static org.burningwave.core.assembler.StaticComponentContainer.Resources;
import static org.burningwave.core.assembler.StaticComponentContainer.Strings;
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
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

import org.burningwave.core.Component;
import org.burningwave.core.concurrent.QueuedTasksExecutor;
import org.burningwave.core.function.ThrowingSupplier;
import org.burningwave.core.iterable.Properties;
import org.burningwave.core.iterable.Properties.Event;


@SuppressWarnings("unchecked")
public class PathHelper implements Component {
	
	public static class Configuration {
		
		public static class Key {
			public static String PATHS_PREFIX = "paths.";
			public static String PATHS_SEPARATOR = ";";
			public static String MAIN_CLASS_PATHS = PATHS_PREFIX + "main-class-paths";
			public static String MAIN_CLASS_PATHS_PLACE_HOLDER = "${" + MAIN_CLASS_PATHS + "}";
			public static String MAIN_CLASS_PATHS_EXTENSION = MAIN_CLASS_PATHS + ".extension";
			public static String MAIN_CLASS_REPOSITORIES = PATHS_PREFIX + "main-class-repositories";
		}
		
		public final static Map<String, Object> DEFAULT_VALUES;
		
		static {
			DEFAULT_VALUES = new HashMap<>();
			DEFAULT_VALUES.put(Key.MAIN_CLASS_PATHS, "${system.properties:java.class.path}");
			DEFAULT_VALUES.put(
				Key.MAIN_CLASS_PATHS_EXTENSION, 
				"//${system.properties:java.home}/lib//children:.*?\\.jar" + PathHelper.Configuration.Key.PATHS_SEPARATOR +
				"//${system.properties:java.home}/lib/ext//children:.*?\\.jar" + PathHelper.Configuration.Key.PATHS_SEPARATOR
			);
			DEFAULT_VALUES.put(
				Key.MAIN_CLASS_REPOSITORIES, 
				"//${system.properties:java.home}/jmods//children:.*?\\.jmod" + PathHelper.Configuration.Key.PATHS_SEPARATOR
			);
		}
	}	
	
	
	private static Pattern PATH_REGEX = Pattern.compile("\\/\\/(.*)\\/\\/(children|allChildren):(.*)");
	private Map<String, Collection<String>> pathGroups;
	private Collection<String> allPaths;
	private Properties config;
	private QueuedTasksExecutor.Task initializerTask;
	private BiConsumer<String, Collection<String>> pathAdder = (path, pathGroup) -> {
		if (path.matches(PATH_REGEX.pattern())) {
			Map<Integer, List<String>> groupMap = Strings.extractAllGroups(PATH_REGEX, path);
			FileSystemItem fileSystemItemParent = FileSystemItem.ofPath(groupMap.get(1).get(0));
			if (fileSystemItemParent.exists()) {
				String childrenSet = groupMap.get(2).get(0);
				String childrenSetRegEx = groupMap.get(3).get(0);
				Function<FileSystemItem.Criteria, Set<FileSystemItem>> childrenSupplier =
					childrenSet.equalsIgnoreCase("children") ?
						fileSystemItemParent::findInChildren :
						childrenSet.equalsIgnoreCase("allChildren") ?
							fileSystemItemParent::findInAllChildren : null;
				if (childrenSupplier != null) {
					Set<FileSystemItem> childrenFound = childrenSupplier.apply(
						FileSystemItem.Criteria.forAllFileThat(
							fileSystemItem -> fileSystemItem.getAbsolutePath().matches(childrenSetRegEx)
						)
					);
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
	};
	
	private PathHelper(Properties config) {
		pathGroups = new ConcurrentHashMap<>();
		allPaths = ConcurrentHashMap.newKeySet();
		this.config = config;
		initializerTask = BackgroundExecutor.createTask(() -> {
			loadMainClassPaths();	
			loadAllPaths();
			initializerTask = null;
		}, Thread.MAX_PRIORITY).async();
		initializerTask.submit();
		listenTo(config);
	}
	
	@Override
	public <K, V>void receiveNotification(Properties properties, Event event, K key, V value) {
		if (event == Event.PUT) {
			if (key instanceof String) {
				String propertyKey = (String)key;
				if (propertyKey.startsWith(Configuration.Key.PATHS_PREFIX)) {
					loadPaths(propertyKey);	
				}
			}
		}
	}
	
	public static PathHelper create(Properties config) {
		return new PathHelper(config);
	}
	
	private void loadMainClassPaths() {
		Collection<String> placeHolders = config.getAllPlaceHolders(Configuration.Key.MAIN_CLASS_PATHS);
		if (placeHolders.contains("${system.properties:java.class.path}")) {
			loadPaths(Configuration.Key.MAIN_CLASS_PATHS);
			addPaths(
				Configuration.Key.MAIN_CLASS_PATHS,
				ClassLoaders.getAllLoadedPaths(this.getClass().getClassLoader())
			);
		}
	}
	
	private void loadAllPaths() {
		config.forEach((pathGroupName, value) -> {
			if (((String)pathGroupName).startsWith(Configuration.Key.PATHS_PREFIX)) {
				loadPaths((String)pathGroupName);
			}
		});
	}
	
	public String getBurningwaveRuntimeClassPath() {
		return Resources.getClassPath(this.getClass()).getAbsolutePath();
	}
	
	public Collection<String> getMainClassPaths() {
		return getPaths(Configuration.Key.MAIN_CLASS_PATHS);
	}
	
	public Collection<String> getAllMainClassPaths() {
		return getPaths(Configuration.Key.MAIN_CLASS_PATHS, Configuration.Key.MAIN_CLASS_PATHS_EXTENSION);
	}
	
	private void waitForInitialization(boolean ignoreThread) {
		QueuedTasksExecutor.Task initializerTask = this.initializerTask;
		if (initializerTask != null) {
			initializerTask.join(ignoreThread);
		}
	}
	
	public Collection<String> getAllPaths() {
		waitForInitialization(false);
		Collection<String> allPaths = ConcurrentHashMap.newKeySet();
		allPaths.addAll(this.allPaths);
		return allPaths;
	}
	
	public Collection<String> getPaths(String... names) {
		waitForInitialization(false);
		Collection<String> pathGroup = new HashSet<>();
		if (names != null && names.length > 0) {
			for (String name : names) {
				if (name.startsWith(Configuration.Key.PATHS_PREFIX)) {
					name = name.replaceFirst(Configuration.Key.PATHS_PREFIX.replace(".", "\\."), "");
				}
				Collection<String> pathsFound = this.pathGroups.get(name);
				if (pathsFound != null) {
					pathGroup.addAll(pathsFound);
				} else {
					loadPaths(name);
					pathsFound = this.pathGroups.get(name);
					if (pathsFound != null) {
						pathGroup.addAll(pathsFound);
					} else {
						//logWarn("path group named " + name + " is not defined");
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
		String pathGroupPropertyName = pathGroupName.startsWith(Configuration.Key.PATHS_PREFIX) ?
			pathGroupName :
			Configuration.Key.PATHS_PREFIX + pathGroupName;
		Collection<String> groupPaths = ConcurrentHashMap.newKeySet();
		synchronized(this) {
			String currentPropertyPaths = config.getProperty(pathGroupPropertyName);
			if (Strings.isNotEmpty(currentPropertyPaths) && Strings.isNotEmpty(paths)) {
				if (!currentPropertyPaths.endsWith(Configuration.Key.PATHS_SEPARATOR)) {
					currentPropertyPaths += Configuration.Key.PATHS_SEPARATOR;
				}
				currentPropertyPaths += paths;
				config.put(pathGroupPropertyName, currentPropertyPaths);
			} else if (Strings.isNotEmpty(paths)) {
				currentPropertyPaths = paths;
				config.put(pathGroupPropertyName, currentPropertyPaths);
			}
			Collection<String> placeHolders = config.getAllPlaceHolders(pathGroupPropertyName);
			Map<String, String> defaultValues = new LinkedHashMap<>();
			if (!placeHolders.isEmpty()) {
				for (String placeHolder : placeHolders) {
					String placeHolderName = placeHolder.replaceAll("[\\$\\{\\}]",  "");
					Collection<String> placeHolderPaths;
					if (placeHolderName.contains("system.properties")) {
						placeHolderPaths = Arrays.asList(
							System.getProperty(
								placeHolderName.split(
									":"
								)[1]
							).split(System.getProperty("path.separator"))
						);
					} else {
						placeHolderPaths = getPaths(placeHolderName);
					}
					for (String placeHolderPath : placeHolderPaths) {
						defaultValues.put(placeHolderName,
						Optional.ofNullable(defaultValues.get(placeHolderName)).map(pHP -> 
							pHP + 
							(pHP.endsWith(Configuration.Key.PATHS_SEPARATOR)?
								"" : Configuration.Key.PATHS_SEPARATOR) +
							placeHolderPath + 
							(placeHolderPath.endsWith(Configuration.Key.PATHS_SEPARATOR)?
								"" : Configuration.Key.PATHS_SEPARATOR)
						).orElseGet(() -> {
							return placeHolderPath;
						}));
					}
				}

			}
			Properties configWithResolvedPaths = new Properties();
			configWithResolvedPaths.putAll(config);
			configWithResolvedPaths.putAll(defaultValues);
			Collection<String> computedPaths = configWithResolvedPaths.resolveStringValues(pathGroupPropertyName, Configuration.Key.PATHS_SEPARATOR, true);
			if (computedPaths != null) {
				for (String path : computedPaths) {
					if (Strings.isNotEmpty(path)) {
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
		groupName = groupName.startsWith(Configuration.Key.PATHS_PREFIX) ?
			groupName.substring(Configuration.Key.PATHS_PREFIX.length()):
			groupName;
		if (paths != null) {
			Collection<String> pathGroup = getOrCreatePathGroup(groupName);
			Collection<QueuedTasksExecutor.Task> tasks = new ArrayList<>();
			int pathSize = paths.size();
			if (pathSize > 1) {
				for (String path : paths) {
					pathAdder.accept(path, pathGroup);
					tasks.add(
						BackgroundExecutor.createTask(() -> {
							pathAdder.accept(path, pathGroup);
						}).async().submit()
					);
				}
				for (QueuedTasksExecutor.Task task : tasks) {
					task.join();
				}
			} else if (pathSize == 1){
				pathAdder.accept(paths.iterator().next(), pathGroup);
			}
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
			for (String resourceRelativePath : resourcesRelativePaths) {
				getAllPaths().stream().forEach((path) -> {
					FileSystemItem fileSystemItem = FileSystemItem.ofPath(path + "/" + resourceRelativePath);
					if (fileSystemItem.exists()) {
						fileConsumer.accept(files, fileSystemItem);
					}
				});
			}
		}
		return files;
	}
	
	
	public <T> T getResource(BiConsumer<Collection<T>, FileSystemItem> fileConsumer, String resourceRelativePath) {
		Collection<T> files = getResources(fileConsumer, resourceRelativePath);
		if (files.size() > 1) {
			Map<String, FileSystemItem> filesFound = new HashMap<>();
			StringBuffer filesInfo = new StringBuffer();
			for (T file : files) {
				if (file instanceof FileSystemItem) {
					FileSystemItem fileSystemItem = (FileSystemItem)file;
					filesFound.put(fileSystemItem.getAbsolutePath(), fileSystemItem);
					filesInfo.append("\t" + System.identityHashCode(file) + ": " + fileSystemItem.getAbsolutePath() + "\n");
				} else {
					throw Throwables.toRuntimeException("Found more than one resource under relative path " + resourceRelativePath);
				}
			}
			if (filesFound.size() > 1) {
				throw Throwables.toRuntimeException("Found more than one resource under relative path " + resourceRelativePath + ":\n" + filesInfo.toString());
			} else {
				FileSystemItem fileSystemItem = FileSystemItem.ofPath(filesFound.keySet().stream().findFirst().get());
				logWarn("Found more than one resource under relative path " + resourceRelativePath + ":\n" + filesInfo.toString() + "\t" +
					System.identityHashCode(fileSystemItem) + ": " + fileSystemItem.getAbsolutePath() + "\twill be assumed\n"
				);
				return (T)fileSystemItem;
			}			
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
	
	public Collection<String> optimize(String... paths) {
		return optimize(Arrays.asList(paths));
	}
	
	public Collection<String> optimize(Collection<String> paths) {
		Collection<String> copyOfPaths = new HashSet<>();
		for (String path : paths) {
			copyOfPaths.add(Paths.normalizeAndClean(path));
		}
		paths = new HashSet<>(copyOfPaths);
		Collection<String> toBeRemoved = new HashSet<>();
		Iterator<String> paths1Itr = copyOfPaths.iterator();
		while (paths1Itr.hasNext()) {
			String path1 = paths1Itr.next();
			FileSystemItem path1AsFile = FileSystemItem.ofPath(path1);
			Iterator<String> paths2Itr = copyOfPaths.iterator();
			while (paths2Itr.hasNext()) {
				String path2 = paths2Itr.next();
				FileSystemItem path2AsFile = FileSystemItem.ofPath(path2);
				if (path1AsFile.isChildOf(path2AsFile)) {
					toBeRemoved.add(path1);
				}
			}
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
		closeResources(() -> pathGroups == null, () -> {
			waitForInitialization(false);
			unregister(config);
			pathGroups.forEach((key, value) -> {
				value.clear();
				pathGroups.remove(key);
			});
			pathGroups.clear();
			pathGroups = null;
			allPaths.clear();
			allPaths = null;
			config = null;
		});		
	}
}
