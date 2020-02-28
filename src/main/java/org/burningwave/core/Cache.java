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

import static org.burningwave.core.assembler.StaticComponentsContainer.Classes;
import static org.burningwave.core.assembler.StaticComponentsContainer.Paths;
import static org.burningwave.core.assembler.StaticComponentsContainer.Streams;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.IterableZipContainer;
import org.burningwave.core.iterable.Properties;

public class Cache {
	private static final String TYPE_CONFIG_KEY = "cache.type";
	
	public final PathForResources<ByteBuffer> pathForContents;
	public final PathForResources<FileSystemItem> pathForFileSystemItems;
	public final PathForResources<IterableZipContainer> pathForZipFiles;
	public final ObjectAndPathForResources<ClassLoader, Field[]> classLoaderForFields;
	public final ObjectAndPathForResources<ClassLoader, Method[]> classLoaderForMethods;
	public final ObjectAndPathForResources<ClassLoader, Constructor<?>[]> classLoaderForConstructors;
	public final ObjectForObject<Method, Object> bindedFunctionalInterfaces;
	public final PathForResources<Field> uniqueKeyForField;
	public final PathForResources<Collection<Method>> uniqueKeyForMethods;
	public final ObjectForObject<Method, MethodHandle> uniqueKeyForMethodHandle;
	
	private Cache(Properties properties) {
		if ("sync".equalsIgnoreCase((String)properties.getProperty(TYPE_CONFIG_KEY))) {
			pathForContents = new SyncPathForResources<>(1L, Streams::shareContent);
			pathForFileSystemItems = new SyncPathForResources<>(1L, fileSystemItem -> fileSystemItem);
			pathForZipFiles = new SyncPathForResources<>(1L, zipFileContainer -> zipFileContainer);
			classLoaderForFields = new SyncObjectAndPathForResources<>(1L, fields -> fields);
			classLoaderForMethods = new SyncObjectAndPathForResources<>(1L, methods -> methods);
			classLoaderForConstructors = new SyncObjectAndPathForResources<>(1L, constructors -> constructors);
			bindedFunctionalInterfaces = new SyncObjectForObject<>();
			uniqueKeyForField = new SyncPathForResources<>(1L, field -> field);
			uniqueKeyForMethods = new SyncPathForResources<>(1L, methods -> methods);
			uniqueKeyForMethodHandle = new SyncObjectForObject<>();
		} else {
			pathForContents = new AsyncPathForResources<>(1L, Streams::shareContent);
			pathForFileSystemItems = new AsyncPathForResources<>(1L, fileSystemItem -> fileSystemItem);
			pathForZipFiles = new AsyncPathForResources<>(1L, zipFileContainer -> zipFileContainer);
			classLoaderForFields = new AsyncObjectAndPathForResources<>(1L, fields -> fields);
			classLoaderForMethods = new AsyncObjectAndPathForResources<>(1L, methods -> methods);
			classLoaderForConstructors = new AsyncObjectAndPathForResources<>(1L, constructors -> constructors);
			bindedFunctionalInterfaces = new AsyncObjectForObject<>();
			uniqueKeyForField = new AsyncPathForResources<>(1L, fields -> fields);
			uniqueKeyForMethods = new AsyncPathForResources<>(1L, methods -> methods);
			uniqueKeyForMethodHandle = new AsyncObjectForObject<>();
		}	
	}
	
	public static Cache create(Properties properties) {
		return new Cache(properties);
	}
	
	public static interface ObjectForObject<T, R> {
		
		public R getOrDefault(T object, Supplier<R> resourceSupplier);
		
		public R get(T object);
		
		public R upload(T object, R resource);
		
		public abstract class Abst<T, R> implements ObjectForObject<T, R> {
			Map<T, R> resources;
			
			public Abst(Map<T, R> resources) {
				this.resources = resources;
			}
			
			@Override
			public R get(T object) {
				return resources.get(object);
			}
			
			@Override
			public R getOrDefault(T object, Supplier<R> resourceSupplier) {
				R resource = resources.get(object);
				if (resource == null) {
					synchronized(Classes.getId(resources,object)) {
						resource = resources.get(object);
						if (resource == null) {
							resources.put(object, (resource = resourceSupplier.get()));
						}
					}
				}
				return resource;
			}
			
			@Override
			public R upload(T object, R resource) {
				synchronized(Classes.getId(resources, object)) {
					return resources.put(object, resource);
				}				
			}
		}
	}
	
	public static class AsyncObjectForObject<T, R> extends ObjectForObject.Abst<T, R> {

		public AsyncObjectForObject() {
			super(new ConcurrentHashMap<>());
		}
	}
	
	public static class SyncObjectForObject<T, R> extends ObjectForObject.Abst<T, R> {

		public SyncObjectForObject() {
			super(new LinkedHashMap<>());
		}
	}
	
	public static interface ObjectAndPathForResources<T, R> {
		
		public R getOrDefault(T object, String path, Supplier<R> resourceSupplier);

		public PathForResources<R> remove(T object);
		
		public R removePath(T object, String path);
		
		public abstract static class Abst<T, R> implements ObjectAndPathForResources<T, R> {
			private Map<T, PathForResources<R>> resources;
			private Supplier<PathForResources<R>> pathForResourcesSupplier;
			
			Abst(
				Supplier<Map<T, PathForResources<R>>> resourcesSupplier,
				Supplier<PathForResources<R>> pathForResourcesSupplier
			) {
				this.resources = resourcesSupplier.get();
				this.pathForResourcesSupplier = pathForResourcesSupplier;
			}

			@Override
			public R getOrDefault(T object, String path, Supplier<R> resourceSupplier) {
				PathForResources<R> pathForResources = resources.get(object);
				if (pathForResources == null) {
					synchronized (Classes.getId(resources, object)) {
						pathForResources = resources.get(object);
						if (pathForResources == null) {
							pathForResources = pathForResourcesSupplier.get();
							resources.put(object, pathForResources);
						}					
					}
				}
				return pathForResources.getOrDefault(path, resourceSupplier);
			}
			
			@Override
			public PathForResources<R> remove(T object) {
				return resources.remove(object);
			}
			
			@Override
			public R removePath(T object, String path) {
				PathForResources<R> pathForResources = resources.get(object);
				if (pathForResources != null) {
					return pathForResources.remove(path);
				}
				return null;
			}
		}		
	}
	
	public static class AsyncObjectAndPathForResources<T, R> extends ObjectAndPathForResources.Abst<T, R> {

		public AsyncObjectAndPathForResources(Long partitionStartLevel, Function<R, R> sharer) {
			super(ConcurrentHashMap::new, () -> new AsyncPathForResources<>(partitionStartLevel, sharer));
		}
		
	}
	
	public static class SyncObjectAndPathForResources<T, R> extends ObjectAndPathForResources.Abst<T, R> {

		public SyncObjectAndPathForResources(Long partitionStartLevel, Function<R, R> sharer) {
			super(LinkedHashMap::new, () -> new SyncPathForResources<>(partitionStartLevel, sharer));
		}
		
	}
	
	public static interface PathForResources<R> {

		R upload(String path, Supplier<R> resourceSupplier);

		R getOrDefault(String path, Supplier<R> resourceSupplier);

		R get(String path);

		R remove(String path);

		R upload(Map<String, R> loadedResources, String path, Supplier<R> resourceSupplier);

		int getLoadedResourcesCount();
		
		public static abstract class Abst<R> implements PathForResources<R> {
			Map<Long, Map<String, Map<String, R>>> resources;	
			Long partitionStartLevel;
			Function<R, R> sharer;
			
			private Abst(Long partitionStartLevel, Function<R, R> sharer) {
				this.partitionStartLevel = partitionStartLevel;
				this.sharer = sharer;
			}
			
			@Override
			public R upload(String path, Supplier<R> resourceSupplier) {
				path = Paths.clean(path);
				Long occurences = path.chars().filter(ch -> ch == '/').count();
				Long partitionIndex = occurences > partitionStartLevel? occurences : partitionStartLevel;
				Map<String, Map<String, R>> partion = retrievePartition(resources, partitionIndex);
				Map<String, R> nestedPartition = retrievePartition(partion, partitionIndex, path);
				return upload(nestedPartition, path, resourceSupplier);
			}
			
			@Override
			public R getOrDefault(String path, Supplier<R> resourceSupplier) {
				path = Paths.clean(path);
				Long occurences = path.chars().filter(ch -> ch == '/').count();
				Long partitionIndex = occurences > partitionStartLevel? occurences : partitionStartLevel;
				Map<String, Map<String, R>> partion = retrievePartition(resources, partitionIndex);
				Map<String, R> nestedPartition = retrievePartition(partion, partitionIndex, path);
				return getOrDefault(nestedPartition, path, resourceSupplier);
			}
			
			@Override
			public R get(String path) {
				return getOrDefault(path, null);
			}
			
			@Override
			public R remove(String path) {
				path = Paths.clean(path);
				Long occurences = path.chars().filter(ch -> ch == '/').count();
				Long partitionIndex = occurences > partitionStartLevel? occurences : partitionStartLevel;
				Map<String, Map<String, R>> partion = retrievePartition(resources, partitionIndex);
				Map<String, R> nestedPartition = retrievePartition(partion, partitionIndex, path);
				return nestedPartition.remove(path);
			}
			
			@Override
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
			
			abstract R getOrDefault(
				Map<String, R> nestedPartition,
				String path,
				Supplier<R> resourceSupplier
			);

			abstract Map<String, R> retrievePartition(
				Map<String, Map<String, R>> partion,
				Long partitionIndex, String path
			);

			abstract Map<String, Map<String, R>> retrievePartition(
				Map<Long, Map<String, Map<String, R>>>
				loadedResources2, Long partitionIndex
			);
		}
	}
	
	public static class SyncPathForResources<R> extends PathForResources.Abst <R> {
		private SyncPathForResources(Long partitionStartLevel, Function<R, R> sharer) {
			super(partitionStartLevel, sharer);
			resources = new LinkedHashMap<>();
		}		
		
		@Override
		Map<String, R> retrievePartition(Map<String, Map<String, R>> partion, Long partitionIndex, String path) {
			String partitionKey = "/";
			if (partitionIndex > 1) {
				partitionKey = path.substring(0, path.lastIndexOf("/"));
				partitionKey = partitionKey.substring(partitionKey.lastIndexOf("/") + 1);
			}
			Map<String, R> innerPartion = partion.get(partitionKey);
			if (innerPartion == null) {
				synchronized (partion) {
					innerPartion = partion.get(partitionKey);
					if (innerPartion == null) {
						partion.put(partitionKey, innerPartion = new ConcurrentHashMap<>());
					}
				}
			}
			return innerPartion;
		}
		
		@Override
		R getOrDefault(Map<String, R> loadedResources, String path, Supplier<R> resourceSupplier) {
			R resource = loadedResources.get(path);
			if (resource == null) {
				synchronized (Classes.getId(loadedResources, path)) {
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
		
		@Override
		public R upload(Map<String, R> loadedResources, String path, Supplier<R> resourceSupplier) {
			R resource = null;
			synchronized (Classes.getId(loadedResources,path)) {
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
		
		@Override
		Map<String, Map<String, R>> retrievePartition(Map<Long, Map<String, Map<String, R>>> resourcesPartitioned, Long partitionIndex) {
			Map<String, Map<String, R>> resources = resourcesPartitioned.get(partitionIndex);
			if (resources == null) {
				synchronized (resourcesPartitioned) {
					resources = resourcesPartitioned.get(partitionIndex);
					if (resources == null) {
						resourcesPartitioned.put(partitionIndex, resources = new ConcurrentHashMap<>());
					}
				}
			}
			return resources;
		}
	}
	
	public static class AsyncPathForResources<R> extends PathForResources.Abst <R> {
		
		String mutexPrefixName;
		
		 private AsyncPathForResources(Long partitionStartLevel, Function<R, R> sharer) {
			super(partitionStartLevel, sharer);
			resources = new ConcurrentHashMap<>();
			mutexPrefixName = Classes.getId(resources);
		}
		
		
		@Override
		Map<String, R> retrievePartition(Map<String, Map<String, R>> partion, Long partitionIndex, String path) {
			String partitionKey = "/";
			if (partitionIndex > 1) {
				partitionKey = path.substring(0, path.lastIndexOf("/"));
				partitionKey = partitionKey.substring(partitionKey.lastIndexOf("/") + 1);
			}
			Map<String, R> innerPartion = partion.get(partitionKey);
			if (innerPartion == null) {
				synchronized (Classes.getId(mutexPrefixName, partitionIndex, partitionKey)) {
					innerPartion = partion.get(partitionKey);
					if (innerPartion == null) {
						partion.put(partitionKey, innerPartion = new ConcurrentHashMap<>());
					}
				}
			}
			return innerPartion;
		}
		
		@Override
		R getOrDefault(Map<String, R> loadedResources, String path, Supplier<R> resourceSupplier) {
			R resource = loadedResources.get(path);
			if (resource == null) {
				synchronized (mutexPrefixName + "_" + path) {
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
		
		@Override
		public R upload(Map<String, R> loadedResources, String path, Supplier<R> resourceSupplier) {
			R resource = null;
			synchronized (mutexPrefixName + "_" + path) {
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
		
		@Override
		Map<String, Map<String, R>> retrievePartition(Map<Long, Map<String, Map<String, R>>> resourcesPartitioned, Long partitionIndex) {
			Map<String, Map<String, R>> resources = resourcesPartitioned.get(partitionIndex);
			if (resources == null) {
				synchronized (mutexPrefixName + "_" + partitionIndex) {
					resources = resourcesPartitioned.get(partitionIndex);
					if (resources == null) {
						resourcesPartitioned.put(partitionIndex, resources = new ConcurrentHashMap<>());
					}
				}
			}
			return resources;
		}		
	}
}
