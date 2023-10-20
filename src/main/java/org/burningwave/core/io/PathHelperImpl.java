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
 * Copyright (c) 2019-2023 Roberto Gentili
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
import static org.burningwave.core.assembler.StaticComponentContainer.Classes;
import static org.burningwave.core.assembler.StaticComponentContainer.IterableObjectHelper;
import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository;
import static org.burningwave.core.assembler.StaticComponentContainer.Paths;
import static org.burningwave.core.assembler.StaticComponentContainer.Resources;
import static org.burningwave.core.assembler.StaticComponentContainer.Strings;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.burningwave.core.Component;
import org.burningwave.core.assembler.StaticComponentContainer;
import org.burningwave.core.concurrent.QueuedTaskExecutor;
import org.burningwave.core.function.Executor;
import org.burningwave.core.iterable.IterableObjectHelper.ResolveConfig;
import org.burningwave.core.iterable.Properties;
import org.burningwave.core.iterable.Properties.Event;


@SuppressWarnings("unchecked")
class PathHelperImpl implements Component, PathHelper {
	private static Pattern PATH_REGEX = Pattern.compile("\\/\\/(.*)\\/\\/(children|allChildren):(.*)");
	private Map<String, Collection<String>> pathGroups;
	private Collection<String> allPaths;
	private Map<?, ?> config;
	private QueuedTaskExecutor.Task initializerTask;

	PathHelperImpl(Map<?, ?> config) {
		this.config = config;
		checkAndListenTo(config);
		launchAllPathsLoadingTask();
	}

	private void launchAllPathsLoadingTask() {
		initializerTask = BackgroundExecutor.createTask(task -> {
			try {
				pathGroups = new ConcurrentHashMap<>();
				allPaths = ConcurrentHashMap.newKeySet();
				loadMainClassPaths();
				loadAllPaths();
			} finally {
				initializerTask = null;
			}
		}, Thread.MAX_PRIORITY);
		initializerTask.submit();
	}

	@Override
	public <K, V>void processChangeNotification(Properties properties, Event event, K key, V newValue, V oldValue) {
		if (key instanceof String) {
			String propertyKey = (String)key;
			if (propertyKey.startsWith(Configuration.Key.PATHS_PREFIX)) {
				if (event.name().equals(Event.PUT.name())) {
					loadAndMapPaths(propertyKey, null);
				} else if (event.name().equals(Event.REMOVE.name())) {
					launchAllPathsLoadingTask();
				}
			}
		}

	}

	private void loadMainClassPaths() {
		Collection<String> placeHolders = IterableObjectHelper.getAllPlaceHolders(config, Configuration.Key.MAIN_CLASS_PATHS);
		if (placeHolders.contains("${system.properties:java.class.path}")) {
			loadAndMapPaths(Configuration.Key.MAIN_CLASS_PATHS, null);
			addPaths(
				Configuration.Key.MAIN_CLASS_PATHS,
				ClassLoaders.getAllLoadedPaths(Classes.getClassLoader(this.getClass()))
			);
		}
	}

	private void loadAllPaths() {
		for (Object pathGroupNameObject : config.keySet()) {
			String pathGroupName = (String) pathGroupNameObject;
			if ((pathGroupName.startsWith(Configuration.Key.PATHS_PREFIX))) {
				loadAndMapPaths(pathGroupName, null);
			}
		}
	}

	@Override
	public String getBurningwaveRuntimeClassPath() {
		return Resources.getClassPath(this.getClass()).getAbsolutePath();
	}

	@Override
	public Collection<String> getMainClassPaths() {
		return getPaths(Configuration.Key.MAIN_CLASS_PATHS);
	}

	@Override
	public Collection<String> getAllMainClassPaths() {
		return getPaths(Configuration.Key.MAIN_CLASS_PATHS, Configuration.Key.MAIN_CLASS_PATHS_EXTENSION);
	}

	private void waitForInitialization() {
		QueuedTaskExecutor.Task initializerTask = this.initializerTask;
		if (initializerTask != null) {
			initializerTask.waitForFinish();
		}
	}

	@Override
	public Collection<String> getAllPaths() {
		waitForInitialization();
		Collection<String> allPaths = ConcurrentHashMap.newKeySet();
		allPaths.addAll(this.allPaths);
		return allPaths;
	}

	@Override
	public Collection<String> getPaths(String... names) {
		waitForInitialization();
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
					loadAndMapPaths(name, null);
					pathsFound = this.pathGroups.get(name);
					if (pathsFound != null) {
						pathGroup.addAll(pathsFound);
					}
				}

			}
		}
		return pathGroup;
	}

	private Collection<String> getOrCreatePathGroup(String groupName) {
		groupName = groupName.startsWith(Configuration.Key.PATHS_PREFIX) ?
			groupName.substring(Configuration.Key.PATHS_PREFIX.length()):
			groupName;
		Collection<String> classPathsGroup = null;
		if ((classPathsGroup = this.pathGroups.get(groupName)) == null) {
			synchronized(this.pathGroups) {
				if ((classPathsGroup = this.pathGroups.get(groupName)) == null) {
					classPathsGroup = ConcurrentHashMap.newKeySet();
					this.pathGroups.put(groupName, classPathsGroup);
				}
			}
		}
		return classPathsGroup;
	}

	@Override
	public Collection<String> loadAndMapPaths(String pathGroupName, String paths) {
		waitForInitialization();
		String pathGroupPropertyName = pathGroupName.startsWith(Configuration.Key.PATHS_PREFIX) ?
			pathGroupName :
			Configuration.Key.PATHS_PREFIX + pathGroupName;
		Collection<String> groupPaths = ConcurrentHashMap.newKeySet();
		synchronized(this) {
			String currentPropertyPaths = (String)config.get(pathGroupPropertyName);
			if (Strings.isNotEmpty(currentPropertyPaths) && Strings.isNotEmpty(paths)) {
				if (!currentPropertyPaths.endsWith(IterableObjectHelper.getDefaultValuesSeparator())) {
					currentPropertyPaths += IterableObjectHelper.getDefaultValuesSeparator();
				}
				currentPropertyPaths += paths;
				((Map<Object, Object>)config).put(pathGroupPropertyName, currentPropertyPaths);
			} else if (Strings.isNotEmpty(paths)) {
				currentPropertyPaths = paths;
				((Map<Object, Object>)config).put(pathGroupPropertyName, currentPropertyPaths);
			}
			Collection<String> placeHolders = IterableObjectHelper.getAllPlaceHolders(config, pathGroupPropertyName);
			Map<String, String> defaultValues = new LinkedHashMap<>();
			if (!placeHolders.isEmpty()) {
				for (String placeHolder : placeHolders) {
					String placeHolderName = placeHolder.replaceAll("[\\$\\{\\}]",  "");
					Collection<String> placeHolderPaths;
					if (placeHolderName.contains("system.properties")) {
						placeHolderPaths = Arrays.asList(
							StaticComponentContainer.SystemProperties.get(
								placeHolderName.split(
									":"
								)[1]
							).split(File.pathSeparator)
						);
					} else {
						placeHolderPaths = getPaths(placeHolderName);
					}
					for (String placeHolderPath : placeHolderPaths) {
						defaultValues.put(placeHolderName,
						Optional.ofNullable(defaultValues.get(placeHolderName)).map(pHP ->
							pHP +
							(pHP.endsWith(IterableObjectHelper.getDefaultValuesSeparator())?
								"" : IterableObjectHelper.getDefaultValuesSeparator()) +
							placeHolderPath +
							(placeHolderPath.endsWith(IterableObjectHelper.getDefaultValuesSeparator())?
								"" : IterableObjectHelper.getDefaultValuesSeparator())
						).orElseGet(() -> {
							return placeHolderPath;
						}));
					}
				}

			}
			Map<Object, Object> configWithResolvedPaths = new HashMap<>();
			configWithResolvedPaths.putAll(config);
			configWithResolvedPaths.putAll(defaultValues);
			Collection<String> computedPaths = IterableObjectHelper.resolveStringValues(
				ResolveConfig.forNamedKey(pathGroupPropertyName)
				.on(configWithResolvedPaths)
				.withValuesSeparator(IterableObjectHelper.getDefaultValuesSeparator())
				.deleteUnresolvedPlaceHolder(true)
			);
			if (computedPaths != null) {
				groupPaths.addAll(addPaths(pathGroupName, computedPaths));
			}
		}
		return groupPaths;
	}

	private Collection<String> addPaths(String groupName, Collection<String> paths) {
		if (paths != null) {
			Collection<String> pathGroup = getOrCreatePathGroup(groupName);
			for (String path : paths) {
				if (path.matches(PATH_REGEX.pattern())) {
					Map<Integer, List<String>> groupMap = Strings.extractAllGroups(PATH_REGEX, path);
					String fileSystemItemParentPath = groupMap.get(1).get(0);
					FileSystemItem fileSystemItemParent = null;
					try {
						fileSystemItemParent = FileSystemItem.ofPath(fileSystemItemParentPath);
					} catch (Throwable exc) {
						ManagedLoggerRepository.logWarn(getClass()::getName, "could not add path '" + path + "': parent path '" + fileSystemItemParentPath + "' could not be loaded");
						continue;
					}
					if (fileSystemItemParent.exists()) {
						String childrenSet = groupMap.get(2).get(0);
						String childrenSetRegEx = groupMap.get(3).get(0);
						Function<FileSystemItem.Criteria, Collection<FileSystemItem>> childrenSupplier =
							childrenSet.equalsIgnoreCase("children") ?
								fileSystemItemParent::findInChildren :
								childrenSet.equalsIgnoreCase("allChildren") ?
									fileSystemItemParent::findInAllChildren : null;
						if (childrenSupplier != null) {
							Collection<FileSystemItem> childrenFound = childrenSupplier.apply(
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
			}
			return pathGroup;
		} else {
			return org.burningwave.core.assembler.StaticComponentContainer.Driver.throwException("classPaths parameter is null");
		}
	}

	@Override
	public Collection<FileSystemItem> findResources(Predicate<String> absolutePathPredicate) {
		Collection<FileSystemItem> resources = new HashSet<>();
		FileSystemItem.Criteria criteria = FileSystemItem.Criteria.forAllFileThat(fSI -> absolutePathPredicate.test(fSI.getAbsolutePath()));
		for (String path : getAllPaths()) {
			resources.addAll(FileSystemItem.ofPath(path).findInAllChildren(criteria));
		}
		return resources;
	}

	@Override
	public Collection<String> getAbsolutePathsOfResources(Predicate<String> absolutePathPredicate) {
		return findResources(absolutePathPredicate).stream().map(fSI -> fSI.getAbsolutePath()).collect(Collectors.toSet());
	}

	@Override
	public String getAbsolutePathOfResource(String resourceRelativePath) {
		return Optional.ofNullable(
			getResource(resourceRelativePath)
		).map(resource -> {
			return resource.getAbsolutePath();
		}).orElseGet(() -> {
			ManagedLoggerRepository.logInfo(getClass()::getName, "Could not find file {}", resourceRelativePath);
			return null;
		});
	}

	@Override
	public Collection<String> getAbsolutePathsOfResource(String... resourceRelativePath) {
		return getResources(resourceRelativePath).stream().map(fSI -> fSI.getAbsolutePath()).collect(Collectors.toSet());
	}

	@Override
	public Collection<FileSystemItem> getResources(String... resourcesRelativePaths) {
		return getResources((coll, file) -> coll.add(file), resourcesRelativePaths);
	}

	@Override
	public FileSystemItem getResource(String resourceRelativePath) {
		return getResource(
				(coll, file) ->
					coll.add(file), resourceRelativePath);
	}


	@Override
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


	@Override
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
					org.burningwave.core.assembler.StaticComponentContainer.Driver.throwException("Found more than one resource under relative path {}",  resourceRelativePath);
				}
			}
			if (filesFound.size() > 1) {
				org.burningwave.core.assembler.StaticComponentContainer.Driver.throwException("Found more than one resource under relative path {}:\n{}",  resourceRelativePath, filesInfo.toString());
			} else {
				FileSystemItem fileSystemItem = FileSystemItem.ofPath(filesFound.keySet().stream().findFirst().get());
				ManagedLoggerRepository.logWarn(getClass()::getName, "Found more than one resource under relative path {}:\n{}\t{}: {}\twill be assumed\n",
					resourceRelativePath,
					filesInfo.toString(),
					System.identityHashCode(fileSystemItem),
					fileSystemItem.getAbsolutePath()
				);
				return (T)fileSystemItem;
			}
		}
		return files.stream().findFirst().orElse(null);
	}

	@Override
	public Collection<InputStream> getResourcesAsStreams(String... resourcesRelativePaths) {
		return getResources((coll, fileSystemItem) -> coll.add(fileSystemItem.toInputStream()), resourcesRelativePaths);
	}

	@Override
	public InputStream getResourceAsStream(String resourceRelativePath) {
		return getResource((coll, fileSystemItem) ->
			coll.add(fileSystemItem.toInputStream()),
			resourceRelativePath
		);
	}

	@Override
	public StringBuffer getResourceAsStringBuffer(String resourceRelativePath) {
		return Executor.get(() -> {
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

	@Override
	public Collection<String> optimize(String... paths) {
		return optimize(Arrays.asList(paths));
	}

	@Override
	public Collection<String> optimize(Collection<String> paths) {
		Collection<String> copyOfPaths = new HashSet<>();
		for (String path : paths) {
			copyOfPaths.add(Paths.toNormalizedCleanedAbsolutePath(path));
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


	@Override
	public Collection<FileSystemItem> optimizeFileSystemItems(FileSystemItem... paths) {
		return optimizeFileSystemItems(Arrays.asList(paths));
	}


	@Override
	public Collection<FileSystemItem> optimizeFileSystemItems(Collection<FileSystemItem> paths) {
		Collection<FileSystemItem> copyOfPaths = new HashSet<>(paths);
		Collection<FileSystemItem> toBeRemoved = new HashSet<>();
		Iterator<FileSystemItem> paths1Itr = copyOfPaths.iterator();
		while (paths1Itr.hasNext()) {
			FileSystemItem path1AsFile = paths1Itr.next();
			Iterator<FileSystemItem> paths2Itr = copyOfPaths.iterator();
			while (paths2Itr.hasNext()) {
				FileSystemItem path2AsFile = paths2Itr.next();
				if (path1AsFile.isChildOf(path2AsFile)) {
					toBeRemoved.add(path1AsFile);
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


	@Override
	public Collection<String> getPaths(Predicate<String> pathPredicate) {
		return getAllPaths().stream().filter(pathPredicate).collect(Collectors.toSet());
	}

	@Override
	public String getPath(Predicate<String> pathPredicate) {
		Collection<String> classPathsFound = getPaths(pathPredicate);
		if (classPathsFound.size() > 1) {
			org.burningwave.core.assembler.StaticComponentContainer.Driver.throwException("Found more than one class path for predicate {}", pathPredicate);
		}
		return classPathsFound.stream().findFirst().orElseGet(() -> null);
	}

	@Override
	public void close() {
		closeResources(() -> pathGroups == null, task -> {
			QueuedTaskExecutor.Task initializerTask = this.initializerTask;
			if (initializerTask != null) {
				initializerTask.abortOrWaitForFinish();
			}
			checkAndUnregister(config);
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