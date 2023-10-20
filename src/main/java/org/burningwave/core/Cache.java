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
package org.burningwave.core;

import static org.burningwave.core.assembler.StaticComponentContainer.BackgroundExecutor;
import static org.burningwave.core.assembler.StaticComponentContainer.BufferHandler;
import static org.burningwave.core.assembler.StaticComponentContainer.IterableObjectHelper;
import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository;
import static org.burningwave.core.assembler.StaticComponentContainer.Objects;
import static org.burningwave.core.assembler.StaticComponentContainer.Synchronizer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.burningwave.core.classes.Members;
import org.burningwave.core.concurrent.QueuedTaskExecutor;
import org.burningwave.core.function.TriConsumer;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.IterableZipContainer;
import org.burningwave.core.iterable.IterableObjectHelper.IterationConfig;


public class Cache {
	public final PathForResources<ByteBuffer> pathForContents;
	public final PathForResources<FileSystemItem> pathForFileSystemItems;
	public final PathForResources<IterableZipContainer> pathForIterableZipContainers;
	public final ObjectAndPathForResources<ClassLoader, Field[]> classLoaderForFields;
	public final ObjectAndPathForResources<ClassLoader, Method[]> classLoaderForMethods;
	public final ObjectAndPathForResources<ClassLoader, Constructor<?>[]> classLoaderForConstructors;
	public final ObjectAndPathForResources<ClassLoader, Collection<Field>> uniqueKeyForFields;
	public final ObjectAndPathForResources<ClassLoader, Collection<Constructor<?>>> uniqueKeyForConstructors;
	public final ObjectAndPathForResources<ClassLoader, Collection<Method>> uniqueKeyForMethods;
	public final ObjectAndPathForResources<ClassLoader, Object> bindedFunctionalInterfaces;
	public final ObjectAndPathForResources<ClassLoader, Members.Handler.OfExecutable.Box<?>> uniqueKeyForExecutableAndMethodHandle;

	private Cache() {
		ManagedLoggerRepository.logInfo(getClass()::getName, "Building cache");
		pathForContents = new PathForResources<>(BufferHandler::shareContent);
		pathForFileSystemItems = new PathForResources<>(
			(path, fileSystemItem) ->
				fileSystemItem.destroy()
		);
		pathForIterableZipContainers = new PathForResources<>(
			(path, zipFileContainer) ->
				zipFileContainer.destroy()
		);
		classLoaderForFields = new ObjectAndPathForResources<>();
		classLoaderForMethods = new ObjectAndPathForResources<>();
		uniqueKeyForFields = new ObjectAndPathForResources<>();
		uniqueKeyForMethods = new ObjectAndPathForResources<>();
		uniqueKeyForConstructors = new ObjectAndPathForResources<>();
		classLoaderForConstructors = new ObjectAndPathForResources<>();
		bindedFunctionalInterfaces = new ObjectAndPathForResources<>();
		uniqueKeyForExecutableAndMethodHandle = new ObjectAndPathForResources<>();
	}

	public static Cache create() {
		return new Cache();
	}

	public static class ObjectAndPathForResources<T, R> {
		Map<T, PathForResources<R>> resources;
		Supplier<PathForResources<R>> pathForResourcesSupplier;
		String instanceId;

		public ObjectAndPathForResources() {
			this(1L, item -> item, null );
		}

		public ObjectAndPathForResources(Long partitionStartLevel) {
			this(partitionStartLevel, item -> item, null);
		}

		public ObjectAndPathForResources(Long partitionStartLevel, Function<R, R> sharer) {
			this(partitionStartLevel, sharer, null);
		}

		public ObjectAndPathForResources(Long partitionStartLevel, Function<R, R> sharer, BiConsumer<String, R> itemDestroyer) {
			this.resources = new ConcurrentHashMap<>();
			this.pathForResourcesSupplier = () -> new PathForResources<>(partitionStartLevel, sharer, itemDestroyer);
			this.instanceId = Objects.getId(this);
		}

		public R getOrUploadIfAbsent(T object, String path, Supplier<R> resourceSupplier) {
			PathForResources<R> pathForResources = resources.get(object);
			if (pathForResources == null) {
				pathForResources = Synchronizer.execute(instanceId + "_" + Objects.getId(object), () -> getPathForResource(object));
			}
			return pathForResources.getOrUploadIfAbsent(path, resourceSupplier);
		}

		public R get(T object, String path) {
			PathForResources<R> pathForResources = resources.get(object);
			if (pathForResources == null) {
				pathForResources = Synchronizer.execute(instanceId + "_mutexManagerForResources_" + Objects.getId(object), () -> getPathForResource(object));
			}
			return pathForResources.get(path);
		}

		private PathForResources<R> getPathForResource(T object) {
			PathForResources<R> pathForResources = resources.get(object);
			if (pathForResources == null) {
				pathForResources = pathForResourcesSupplier.get();
				resources.put(object, pathForResources);
			}
			return pathForResources;
		}

		public PathForResources<R> remove(T object, boolean destroyItems) {
			PathForResources<R> pathForResources = resources.remove(object);
			if ((pathForResources != null) && destroyItems) {
				pathForResources.clearInBackground(destroyItems).waitForFinish();
			}
			return pathForResources;
		}

		public R removePath(T object, String path) {
			return removePath(object, path, false);
		}

		public R removePath(T object, String path, boolean destroyItem) {
			PathForResources<R> pathForResources = resources.get(object);
			if (pathForResources != null) {
				return pathForResources.remove(path, destroyItem);
			}
			return null;
		}

		QueuedTaskExecutor.Task clearInBackground(boolean destroyItems) {
			Map<T, PathForResources<R>> resources;
			synchronized (this.resources) {
				resources = this.resources;
				this.resources = new ConcurrentHashMap<>();
			}
			return BackgroundExecutor.createTask(task -> {
				for (Entry<T, PathForResources<R>> item : resources.entrySet()) {
					item.getValue().clearInBackground(destroyItems).waitForFinish();
				}
				resources.clear();
			}).submit();
		}

		public void iterate(TriConsumer<T, String, R> itemConsumer) {
			iterate(false, itemConsumer, new AtomicReference<>());
		}

		public void iterateParallel(TriConsumer<T, String, R> itemConsumer) {
			iterate(true, itemConsumer, new AtomicReference<>());
		}

		void iterate(
			boolean parallel,
			TriConsumer<T, String, R> itemConsumer,
			AtomicReference<org.burningwave.core.iterable.IterableObjectHelper.TerminateIteration> terminateExceptionWrapper
		) {

			IterableObjectHelper.iterate(
				IterationConfig.of(
					this.resources.entrySet()
				).withAction(entry -> {
					PathForResources<R> pathForResources = entry.getValue();
					pathForResources.checkAndThrow(terminateExceptionWrapper);
					pathForResources.iterate(
						parallel,
						(path, item) ->
							itemConsumer.accept(entry.getKey(), path, item),
						terminateExceptionWrapper
					);
				}).parallelIf(coll -> parallel)
			);
		}
	}

	public static class PathForResources<R> {
		Map<Long, Map<String, Map<String, R>>> resources;
		Long partitionStartLevel;
		Function<R, R> sharer;
		BiConsumer<String, R> itemDestroyer;
		String instanceId;

		private PathForResources() {
			this(1L, item -> item, null);
		}

		private PathForResources(Long partitionStartLevel) {
			this(partitionStartLevel, item -> item, null);
		}

		private PathForResources(Function<R, R> sharer) {
			this(1L, sharer, null);
		}

		private PathForResources(BiConsumer<String, R> itemDestroyer) {
			this(1L, item -> item, itemDestroyer);
		}

		private PathForResources(Long partitionStartLevel, Function<R, R> sharer) {
			this(partitionStartLevel, sharer, null);
		}

		private PathForResources(Function<R, R> sharer, BiConsumer<String, R> itemDestroyer) {
			this(1L, item -> item, itemDestroyer);
		}

		private PathForResources(Long partitionStartLevel, BiConsumer<String, R> itemDestroyer) {
			this(partitionStartLevel, item -> item, itemDestroyer);
		}

		private PathForResources(Long partitionStartLevel, Function<R, R> sharer, BiConsumer<String, R> itemDestroyer) {
			this.partitionStartLevel = partitionStartLevel;
			this.sharer = sharer;
			this.resources = new ConcurrentHashMap<>();
			this.itemDestroyer = itemDestroyer;
			this.instanceId = this.toString();
		}

		Map<String, R> retrievePartition(Map<String, Map<String, R>> partion, Long partitionIndex, String path) {
			String partitionKey = "/";
			if (partitionIndex > 1) {
				partitionKey = path.substring(0, path.lastIndexOf("/"));
				partitionKey = partitionKey.substring(partitionKey.lastIndexOf("/") + 1);
			}
			Map<String, R> innerPartion = partion.get(partitionKey);
			if (innerPartion == null) {
				String finalPartitionKey = partitionKey;
				innerPartion = Synchronizer.execute(instanceId + "_mutexManagerForPartitions_" + finalPartitionKey, () -> {
					Map<String, R> innerPartionTemp = partion.get(finalPartitionKey);
					if (innerPartionTemp == null) {
						partion.put(finalPartitionKey, innerPartionTemp = new ConcurrentHashMap<>());
					}
					return innerPartionTemp;
				});
			}
			return innerPartion;
		}

		R getOrUploadIfAbsent(Map<String, R> loadedResources, String path, Supplier<R> resourceSupplier) {
			R resource = loadedResources.get(path);
			if (resource == null) {
				resource = Synchronizer.execute(instanceId + "_mutexManagerForLoadedResources_" + path, () -> {
					R resourceTemp = loadedResources.get(path);
					if ((resourceTemp == null) && (resourceSupplier != null)) {
						resourceTemp = resourceSupplier.get();
						if (resourceTemp != null) {
							loadedResources.put(path, resourceTemp = sharer.apply(resourceTemp));
						}
					}
					return resourceTemp;
				});
			}
			return resource != null?
				sharer.apply(resource) :
				resource;
		}

		public R upload(Map<String, R> loadedResources, String path, Supplier<R> resourceSupplier, boolean destroy) {
			R oldResource = remove(path, destroy);
			Synchronizer.execute(instanceId + "_mutexManagerForLoadedResources_" + path, () -> {
				R resourceTemp = resourceSupplier.get();
				if (resourceTemp != null) {
					loadedResources.put(path, resourceTemp = sharer.apply(resourceTemp));
				}
			});
			return oldResource;
		}

		Map<String, Map<String, R>> retrievePartition(Map<Long, Map<String, Map<String, R>>> partitionedResources, Long partitionIndex) {
			Map<String, Map<String, R>> resources = partitionedResources.get(partitionIndex);
			if (resources == null) {
				resources = Synchronizer.execute(instanceId + "_mutexManagerForPartitionedResources_" + partitionIndex.toString(), () -> {
					Map<String, Map<String, R>> resourcesTemp = partitionedResources.get(partitionIndex);
					if (resourcesTemp == null) {
						partitionedResources.put(partitionIndex, resourcesTemp = new ConcurrentHashMap<>());
					}
					return resourcesTemp;
				});
			}
			return resources;
		}

		public R upload(String path, Supplier<R> resourceSupplier, boolean destroy) {
			return upload(getNestedPartition(path), path, resourceSupplier, destroy);
		}

		private Map<String, R> getNestedPartition(String path) {
			Long occurences = path.chars().filter(ch -> ch == '/').count();
			Long partitionIndex = occurences > partitionStartLevel? occurences : partitionStartLevel;
			Map<String, Map<String, R>> partion = retrievePartition(resources, partitionIndex);
			return retrievePartition(partion, partitionIndex, path);
		}

		public R getOrUploadIfAbsent(String path, Supplier<R> resourceSupplier) {
			Map<String, R> nestedPartition = getNestedPartition(path);
			return getOrUploadIfAbsent(nestedPartition, path, resourceSupplier);
		}

		public R get(String path) {
			return getOrUploadIfAbsent(path, null);
		}

		public R remove(String path, boolean destroy) {
			Map<String, R> nestedPartition = getNestedPartition(path);
			R item = Synchronizer.execute(instanceId + "_mutexManagerForLoadedResources_" + path, () -> {
				return nestedPartition.remove(path);
			});
			if ((itemDestroyer != null) && destroy && (item != null)) {
				String finalPath = path;
				itemDestroyer.accept(finalPath, item);
			}
			return item;
		}

		public int getLoadedResourcesCount() {
			return getLoadedResourcesCount(resources);
		}

		private int getLoadedResourcesCount(Map<Long, Map<String, Map<String, R>>> resources) {
			int count = 0;
			for (Map.Entry<Long, Map<String, Map<String, R>>> partition : resources.entrySet()) {
				for (Map.Entry<String, Map<String, R>> innerPartition : partition.getValue().entrySet()) {
					count += innerPartition.getValue().size();
				}
			}
			return count;
		}

		private QueuedTaskExecutor.Task clearInBackground(boolean destroyItems) {
			Map<Long, Map<String, Map<String, R>>> partitions;
			synchronized (this.resources) {
				partitions = this.resources;
				this.resources = new ConcurrentHashMap<>();
			}
			return BackgroundExecutor.createTask(task -> {
				clearResources(partitions, destroyItems);
			}).submit();
		}

		void clearResources(Map<Long, Map<String, Map<String, R>>> partitions, boolean destroyItems) {
			for (Entry<Long, Map<String, Map<String, R>>> partition : partitions.entrySet()) {
				for (Entry<String, Map<String, R>> nestedPartition : partition.getValue().entrySet()) {
					if ((itemDestroyer != null) && destroyItems) {
						IterableObjectHelper.deepClear(nestedPartition.getValue(), (path, resource) -> {
							this.itemDestroyer.accept(path, resource);
						});
					} else {
						nestedPartition.getValue().clear();
					}
				}
				partition.getValue().clear();
			}
			partitions.clear();
		}

		public void iterate(BiConsumer<String, R> itemConsumer) {
			iterate(false, itemConsumer, new AtomicReference<>());
		}

		public void iterateParallel(BiConsumer<String, R> itemConsumer) {
			iterate(true, itemConsumer, new AtomicReference<>());
		}

		void iterate(
			boolean parallel,
			BiConsumer<String, R> itemConsumer,
			AtomicReference<org.burningwave.core.iterable.IterableObjectHelper.TerminateIteration> terminateExceptionWrapper
		) {
			IterableObjectHelper.iterate(
				IterationConfig.of(
					this.resources.entrySet()
				).withAction(entryOne -> {
					checkAndThrow(terminateExceptionWrapper);
					IterableObjectHelper.iterate(
						IterationConfig.of(
							entryOne.getValue().entrySet()
						).withAction(entryTwo -> {
							checkAndThrow(terminateExceptionWrapper);
							IterableObjectHelper.iterate(
								IterationConfig.of(
									entryTwo.getValue().entrySet()
								).withAction(entryThree -> {
									try {
										itemConsumer.accept(entryThree.getKey(), entryThree.getValue());
									} catch (org.burningwave.core.iterable.IterableObjectHelper.TerminateIteration exception) {
										if (exception == org.burningwave.core.iterable.IterableObjectHelper.TerminateIteration.NOTIFICATION) {
											terminateExceptionWrapper.set(exception);
										}
										throw exception;
									}
								}).parallelIf(coll -> parallel)
							);
						}).parallelIf(coll -> false)
					);
				}).parallelIf(coll -> parallel)
			);
		}

		void checkAndThrow(AtomicReference<org.burningwave.core.iterable.IterableObjectHelper.TerminateIteration> terminateExceptionWrapper) {
			org.burningwave.core.iterable.IterableObjectHelper.TerminateIteration terminateException = terminateExceptionWrapper.get();
			if (terminateException != null) {
				throw terminateException;
			}
		}

	}


	public void clear(boolean destroyItems, Object... excluded) {
		Set<Object> toBeExcluded = (excluded != null) && (excluded.length > 0) ?
			new HashSet<>(Arrays.asList(excluded)) :
			null;
		Set<QueuedTaskExecutor.Task> tasks = new HashSet<>();
		addCleaningTask(tasks, clear(pathForContents, toBeExcluded, destroyItems));
		addCleaningTask(tasks, clear(pathForFileSystemItems, toBeExcluded, destroyItems));
		addCleaningTask(tasks, clear(pathForIterableZipContainers, toBeExcluded, destroyItems));
		addCleaningTask(tasks, clear(classLoaderForFields, toBeExcluded, destroyItems));
		addCleaningTask(tasks, clear(classLoaderForMethods, toBeExcluded, destroyItems));
		addCleaningTask(tasks, clear(classLoaderForConstructors, toBeExcluded, destroyItems));
		addCleaningTask(tasks, clear(bindedFunctionalInterfaces, toBeExcluded, destroyItems));
		addCleaningTask(tasks, clear(uniqueKeyForFields, toBeExcluded, destroyItems));
		addCleaningTask(tasks, clear(uniqueKeyForConstructors, toBeExcluded, destroyItems));
		addCleaningTask(tasks, clear(uniqueKeyForMethods, toBeExcluded, destroyItems));
		addCleaningTask(tasks, clear(uniqueKeyForExecutableAndMethodHandle, toBeExcluded, destroyItems));
		for (QueuedTaskExecutor.Task task : tasks) {
			task.join();
		}
	}

	private boolean addCleaningTask(Set<QueuedTaskExecutor.Task> tasks, QueuedTaskExecutor.Task task) {
		if (task != null) {
			return tasks.add(task);
		}
		return false;
	}

	private QueuedTaskExecutor.Task clear(Object cache, Set<Object> excluded, boolean destroyItems) {
		if ((excluded == null) || !excluded.contains(cache)) {
			if (cache instanceof ObjectAndPathForResources) {
				return ((ObjectAndPathForResources<?,?>)cache).clearInBackground(destroyItems);
			}  else if (cache instanceof PathForResources) {
				return ((PathForResources<?>)cache).clearInBackground(destroyItems);
			}
		}
		return null;
	}
}
