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
package org.burningwave.core;

import static org.burningwave.core.assembler.StaticComponentContainer.BackgroundExecutor;
import static org.burningwave.core.assembler.StaticComponentContainer.IterableObjectHelper;
import static org.burningwave.core.assembler.StaticComponentContainer.Paths;
import static org.burningwave.core.assembler.StaticComponentContainer.Streams;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.burningwave.core.concurrent.Mutex;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.IterableZipContainer;

@SuppressWarnings("unchecked")
public class Cache implements Component {
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
	public final ObjectAndPathForResources<ClassLoader, Map.Entry<java.lang.reflect.Executable, MethodHandle>> uniqueKeyForExecutableAndMethodHandle;
	
	private Cache() {
		logInfo("Building cache");
		pathForContents = new PathForResources<ByteBuffer>(1L, Streams::shareContent);
		pathForFileSystemItems = new PathForResources<FileSystemItem>(
			1L,
			fileSystemItem -> 
				fileSystemItem, 
			(path, fileSystemItem) -> 
				fileSystemItem.destroy()
		);
		pathForIterableZipContainers = new PathForResources<IterableZipContainer>(
			1L, 
			zipFileContainer ->
				zipFileContainer, 
			(path, zipFileContainer) -> 
				zipFileContainer.destroy()
		);
		classLoaderForFields = new ObjectAndPathForResources<>(1L, fields -> fields);
		classLoaderForMethods = new ObjectAndPathForResources<>(1L, methods -> methods);
		uniqueKeyForFields = new ObjectAndPathForResources<>(1L, field -> field);
		uniqueKeyForMethods = new ObjectAndPathForResources<>(1L, constructors -> constructors);
		uniqueKeyForConstructors = new ObjectAndPathForResources<>(1L, methods -> methods);
		classLoaderForConstructors = new ObjectAndPathForResources<>(1L, constructors -> constructors);
		bindedFunctionalInterfaces = new ObjectAndPathForResources<>(1L, functionalInterface -> functionalInterface);	
		uniqueKeyForExecutableAndMethodHandle = new ObjectAndPathForResources<>(1L, methodHandle -> methodHandle);
	}
	
	public static Cache create() {
		return new Cache();
	}
	
	public static class ObjectAndPathForResources<T, R> implements Component {
		
		Map<T, PathForResources<R>> resources;
		Supplier<PathForResources<R>> pathForResourcesSupplier;
		Mutex.Manager mutexManagerForResources;
		
		public ObjectAndPathForResources(Long partitionStartLevel, Function<R, R> sharer) {
			this(partitionStartLevel, sharer, null);
		}
		
		public ObjectAndPathForResources(Long partitionStartLevel, Function<R, R> sharer, BiConsumer<String, R> itemDestroyer) {
			this.resources = new HashMap<>();
			this.pathForResourcesSupplier = () -> new PathForResources<R>(partitionStartLevel, sharer, itemDestroyer);
			mutexManagerForResources = Mutex.Manager.create(this);
		}

		public R getOrUploadIfAbsent(T object, String path, Supplier<R> resourceSupplier) {
			PathForResources<R> pathForResources = resources.get(object);
			if (pathForResources == null) {
				synchronized (mutexManagerForResources.getMutex(object.toString())) {
					pathForResources = resources.get(object);
					if (pathForResources == null) {
						pathForResources = pathForResourcesSupplier.get();
						resources.put(object, pathForResources);
					}					
				}
			}
			return pathForResources.getOrUploadIfAbsent(path, resourceSupplier);
		}
		
		public R get(T object, String path) {
			PathForResources<R> pathForResources = resources.get(object);
			if (pathForResources == null) {
				synchronized (mutexManagerForResources.getMutex(object.toString())) {
					pathForResources = resources.get(object);
					if (pathForResources == null) {
						pathForResources = pathForResourcesSupplier.get();
						resources.put(object, pathForResources);
					}					
				}
			}
			return pathForResources.get(path);
		}
		
		public PathForResources<R> remove(T object, boolean destroyItems) {
			PathForResources<R> pathForResources = resources.remove(object);
			if (pathForResources != null && destroyItems) {
				pathForResources.clear(destroyItems);
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
		
		@Override
		public ObjectAndPathForResources<T, R> clear() {
			return clear(false);
		}
		
		public ObjectAndPathForResources<T, R> clear(boolean destroyItems) {
			Map<T, PathForResources<R>> resources;
			synchronized (this.resources) {	
				resources = this.resources;
				this.resources = new HashMap<>();
				mutexManagerForResources.clear();
			}
			BackgroundExecutor.createTask(() -> {
				for (Entry<T, PathForResources<R>> item : resources.entrySet()) {
					item.getValue().clear(destroyItems);
				}
				resources.clear();
			}, Thread.MIN_PRIORITY).submit();		
			return this;
		}
	}
	
	public static class PathForResources<R> implements Component  {

		Map<Long, Map<String, Map<String, R>>> resources;	
		Long partitionStartLevel;
		Function<R, R> sharer;
		Mutex.Manager mutexManagerForPartitions;
		Mutex.Manager mutexManagerForLoadedResources;
		Mutex.Manager mutexManagerForPartitionedResources;
		BiConsumer<String, R> itemDestroyer;
		
		private PathForResources(Long partitionStartLevel, Function<R, R> sharer) {
			this(partitionStartLevel, sharer, null);
		}
		
		private PathForResources(Long partitionStartLevel, Function<R, R> sharer, BiConsumer<String, R> itemDestroyer) {
			this.partitionStartLevel = partitionStartLevel;
			this.sharer = sharer;
			resources = new HashMap<>();
			mutexManagerForPartitions = Mutex.Manager.create(this);
			mutexManagerForLoadedResources = Mutex.Manager.create(this);
			mutexManagerForPartitionedResources = Mutex.Manager.create(this);
			this.itemDestroyer = itemDestroyer;
		}
		
		Map<String, R> retrievePartition(Map<String, Map<String, R>> partion, Long partitionIndex, String path) {
			String partitionKey = "/";
			if (partitionIndex > 1) {
				partitionKey = path.substring(0, path.lastIndexOf("/"));
				partitionKey = partitionKey.substring(partitionKey.lastIndexOf("/") + 1);
			}
			Map<String, R> innerPartion = partion.get(partitionKey);
			if (innerPartion == null) {
				synchronized (mutexManagerForPartitions.getMutex(partitionKey)) {
					innerPartion = partion.get(partitionKey);
					if (innerPartion == null) {
						partion.put(partitionKey, innerPartion = new HashMap<>());
					}
				}
			}
			return innerPartion;
		}
		
		R getOrUploadIfAbsent(Map<String, R> loadedResources, String path, Supplier<R> resourceSupplier) {
			R resource = loadedResources.get(path);
			if (resource == null) {
				synchronized (mutexManagerForLoadedResources.getMutex(path)) {
					resource = loadedResources.get(path);
					if (resource == null && resourceSupplier != null) {
						resource = resourceSupplier.get();
						if (resource != null) {
							loadedResources.put(path, resource = sharer.apply(resource));
						}
					}
				}
			}
			return resource != null? 
				sharer.apply(resource) :
				resource;
		}
		
		public R upload(Map<String, R> loadedResources, String path, Supplier<R> resourceSupplier) {
			R resource = null;
			synchronized (mutexManagerForLoadedResources.getMutex(path)) {
				if (resourceSupplier != null) {
					resource = resourceSupplier.get();
					if (resource != null) {
						loadedResources.put(path, resource = sharer.apply(resource));
					}
				}
			}
			return resource != null? 
				sharer.apply(resource) :
				resource;
		}
		
		Map<String, Map<String, R>> retrievePartition(Map<Long, Map<String, Map<String, R>>> partitionedResources, Long partitionIndex) {
			Map<String, Map<String, R>> resources = partitionedResources.get(partitionIndex);
			if (resources == null) {
				synchronized (mutexManagerForPartitionedResources.getMutex(partitionIndex.toString())) {
					resources = partitionedResources.get(partitionIndex);
					if (resources == null) {
						partitionedResources.put(partitionIndex, resources = new HashMap<>());
					}
				}
			}
			return resources;
		}
		
		public R upload(String path, Supplier<R> resourceSupplier) {
			path = Paths.clean(path);
			Long occurences = path.chars().filter(ch -> ch == '/').count();
			Long partitionIndex = occurences > partitionStartLevel? occurences : partitionStartLevel;
			Map<String, Map<String, R>> partion = retrievePartition(resources, partitionIndex);
			Map<String, R> nestedPartition = retrievePartition(partion, partitionIndex, path);
			return upload(nestedPartition, path, resourceSupplier);
		}
		
		public R getOrUploadIfAbsent(String path, Supplier<R> resourceSupplier) {
			path = Paths.clean(path);
			Long occurences = path.chars().filter(ch -> ch == '/').count();
			Long partitionIndex = occurences > partitionStartLevel? occurences : partitionStartLevel;
			Map<String, Map<String, R>> partion = retrievePartition(resources, partitionIndex);
			Map<String, R> nestedPartition = retrievePartition(partion, partitionIndex, path);
			return getOrUploadIfAbsent(nestedPartition, path, resourceSupplier);
		}
		
		public R get(String path) {
			return getOrUploadIfAbsent(path, null);
		}
		
		public R remove(String path, boolean destroy) {
			path = Paths.clean(path);
			Long occurences = path.chars().filter(ch -> ch == '/').count();
			Long partitionIndex = occurences > partitionStartLevel? occurences : partitionStartLevel;
			Map<String, Map<String, R>> partion = retrievePartition(resources, partitionIndex);
			Map<String, R> nestedPartition = retrievePartition(partion, partitionIndex, path);
			R item = nestedPartition.remove(path);
			if (itemDestroyer != null && destroy && item != null) {
				String finalPath = path;
				BackgroundExecutor.createTask(() -> 
					itemDestroyer.accept(finalPath, item)
				);
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
		
		@Override
		public PathForResources<R> clear() {
			return clear(false);
		}
		
		public PathForResources<R> clear(boolean destroyItems) {
			Map<Long, Map<String, Map<String, R>>> partitions;
			synchronized (this.resources) {	
				partitions = this.resources;
				this.resources = new HashMap<>();
				mutexManagerForPartitions.clear();         
				mutexManagerForLoadedResources.clear();    
				mutexManagerForPartitionedResources.clear();
			}
			BackgroundExecutor.createTask(() -> {
				clearResources(partitions, destroyItems);
			}, Thread.MIN_PRIORITY).submit();
			return this;
		}

		void clearResources(Map<Long, Map<String, Map<String, R>>> partitions, boolean destroyItems) {
			for (Entry<Long, Map<String, Map<String, R>>> partition : partitions.entrySet()) {
				for (Entry<String, Map<String, R>> nestedPartition : partition.getValue().entrySet()) {
					if (itemDestroyer != null && destroyItems) {
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
		
	}
	
	public void clear(Cleanable... excluded) {
		clear(false, excluded);
	}
	
	public void clear(boolean destroyItems, Cleanable... excluded) {
		Set<Cleanable> toBeExcluded = excluded != null && excluded.length > 0 ?
			new HashSet<>(Arrays.asList(excluded)) :
			null;
		clear(pathForContents, toBeExcluded, destroyItems);
		clear(pathForFileSystemItems, toBeExcluded, destroyItems);
		clear(pathForIterableZipContainers, toBeExcluded, destroyItems);
		clear(classLoaderForFields, toBeExcluded, destroyItems);
		clear(classLoaderForMethods, toBeExcluded, destroyItems);
		clear(classLoaderForConstructors, toBeExcluded, destroyItems);
		clear(bindedFunctionalInterfaces, toBeExcluded, destroyItems);
		clear(uniqueKeyForFields, toBeExcluded, destroyItems);
		clear(uniqueKeyForConstructors, toBeExcluded, destroyItems);
		clear(uniqueKeyForMethods, toBeExcluded, destroyItems);
		clear(uniqueKeyForExecutableAndMethodHandle, toBeExcluded, destroyItems);
	}

	private void clear(Cleanable cache, Set<Cleanable> excluded, boolean destroyItems) {
		if (excluded == null || !excluded.contains(cache)) {
			if (!destroyItems) {
				cache.clear();
			} else if (cache instanceof ObjectAndPathForResources) {
				((ObjectAndPathForResources<?,?>)cache).clear(destroyItems);
			}  else if (cache instanceof PathForResources) {
				((PathForResources<?>)cache).clear(destroyItems);
			}
		}
	}
	
	@Override
	public void close() {
		close(false);
	}
	
	public void close(boolean destroyItem) {
		clear(destroyItem);
	}
}
