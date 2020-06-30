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

import static org.burningwave.core.assembler.StaticComponentContainer.Paths;
import static org.burningwave.core.assembler.StaticComponentContainer.Streams;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import org.burningwave.core.concurrent.Mutex;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.IterableZipContainer;

public class Cache implements Component {
	public final PathForResources<ByteBuffer> pathForContents;
	public final PathForResources<FileSystemItem> pathForFileSystemItems;
	public final PathForResources<IterableZipContainer> pathForZipFiles;
	public final ObjectAndPathForResources<ClassLoader, Field[]> classLoaderForFields;
	public final ObjectAndPathForResources<ClassLoader, Method[]> classLoaderForMethods;
	public final ObjectAndPathForResources<ClassLoader, Constructor<?>[]> classLoaderForConstructors;
	public final ObjectAndPathForResources<ClassLoader, Field> uniqueKeyForField;
	public final ObjectAndPathForResources<ClassLoader, Collection<Method>> uniqueKeyForMethods;
	public final ObjectForObject<Method, Object> bindedFunctionalInterfaces;
	public final ObjectForObject<Method, MethodHandle> uniqueKeyForMethodHandle;
	
	private Cache() {
		logInfo("Building cache");
		pathForContents = new PathForResources<>(1L, Streams::shareContent);
		pathForFileSystemItems = new PathForResources<>(1L, fileSystemItem -> fileSystemItem);
		pathForZipFiles = new PathForResources<>(1L, zipFileContainer -> zipFileContainer);
		classLoaderForFields = new ObjectAndPathForResources<>(1L, fields -> fields);
		classLoaderForMethods = new ObjectAndPathForResources<>(1L, methods -> methods);
		classLoaderForConstructors = new ObjectAndPathForResources<>(1L, constructors -> constructors);
		bindedFunctionalInterfaces = new ObjectForObject<>();
		uniqueKeyForField = new ObjectAndPathForResources<>(1L, field -> field);
		uniqueKeyForMethods = new ObjectAndPathForResources<>(1L, methods -> methods);
		uniqueKeyForMethodHandle = new ObjectForObject<>();
	}
	
	public static Cache create() {
		return new Cache();
	}
	
	public static class ObjectForObject<T, R> implements Component {
		
		Map<T, R> resources;
		Mutex.Manager mutexManagerForResources;
		
		public ObjectForObject() {
			this.resources = new HashMap<>();
			mutexManagerForResources = Mutex.Manager.create(this);
		}
		
		public R get(T object) {
			return resources.get(object);
		}
		
		public R getOrUploadIfAbsent(T object, Supplier<R> resourceSupplier) {
			R resource = resources.get(object);
			if (resource == null) {
				synchronized(mutexManagerForResources.getMutex(object.toString())) {
					resource = resources.get(object);
					if (resource == null) {
						resources.put(object, (resource = resourceSupplier.get()));
					}
				}
			}
			return resource;
		}
		
		public R upload(T object, R resource) {
			synchronized(resources) {
				return resources.put(object, resource);
			}				
		}
		
		public void clear() {
			resources.clear();
			mutexManagerForResources.clear();
		}
	}

	
	public static class ObjectAndPathForResources<T, R> implements Component  {
		
		Map<T, PathForResources<R>> resources;
		Supplier<PathForResources<R>> pathForResourcesSupplier;
		Mutex.Manager mutexManagerForResources;
		
		public ObjectAndPathForResources(Long partitionStartLevel, Function<R, R> sharer) {
			this.resources = new HashMap<>();
			this.pathForResourcesSupplier = () -> new PathForResources<>(partitionStartLevel, sharer);
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
		
		public PathForResources<R> remove(T object) {
			return resources.remove(object);
		}

		public R removePath(T object, String path) {
			PathForResources<R> pathForResources = resources.get(object);
			if (pathForResources != null) {
				return pathForResources.remove(path);
			}
			return null;
		}
		
		public void clear() {
			resources.clear();
			mutexManagerForResources.clear();
		}
	}
	
	public static class PathForResources<R> implements Component  {

		Map<Long, Map<String, Map<String, R>>> resources;	
		Long partitionStartLevel;
		Function<R, R> sharer;
		Mutex.Manager mutexManagerForPartitions;
		Mutex.Manager mutexManagerForLoadedResources;
		Mutex.Manager mutexManagerForPartitionedResources;
		
		private PathForResources(Long partitionStartLevel, Function<R, R> sharer) {
			this.partitionStartLevel = partitionStartLevel;
			this.sharer = sharer;
			resources = new HashMap<>();
			mutexManagerForPartitions = Mutex.Manager.create(this);
			mutexManagerForLoadedResources = Mutex.Manager.create(this);
			mutexManagerForPartitionedResources = Mutex.Manager.create(this);
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
		
		public R remove(String path) {
			path = Paths.clean(path);
			Long occurences = path.chars().filter(ch -> ch == '/').count();
			Long partitionIndex = occurences > partitionStartLevel? occurences : partitionStartLevel;
			Map<String, Map<String, R>> partion = retrievePartition(resources, partitionIndex);
			Map<String, R> nestedPartition = retrievePartition(partion, partitionIndex, path);
			return nestedPartition.remove(path);
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
		
		public void clear() {
			resources.clear();
			mutexManagerForPartitions.clear();         
			mutexManagerForLoadedResources.clear();    
			mutexManagerForPartitionedResources.clear(); 
		}
	}
	
	public void clear() {
		pathForContents.clear();
		pathForFileSystemItems.clear();
		pathForZipFiles.clear();
		classLoaderForFields.clear();
		classLoaderForMethods.clear();
		classLoaderForConstructors.clear();
		bindedFunctionalInterfaces.clear();
		uniqueKeyForField.clear();
		uniqueKeyForMethods.clear();
		uniqueKeyForMethodHandle.clear();
	}
	
	@Override
	public void close() {
		clear();
	}
}
