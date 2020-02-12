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

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.Streams;
import org.burningwave.core.io.ZipFile;
import org.burningwave.core.iterable.Properties;

public class Cache {
	private static final String TYPE_CONFIG_KEY = "cache.type";
	public final static PathForResources<ByteBuffer> PATH_FOR_CONTENTS ;
	public final static PathForResources<FileSystemItem> PATH_FOR_FILE_SYSTEM_ITEMS;
	public final static PathForResources<ZipFile> PATH_FOR_ZIP_FILES;
	public final static PathForResources<Field[]> CLASS_RELATIVE_PATH_FOR_FIELDS;
	public final static PathForResources<Method[]> CLASS_RELATIVE_PATH_FOR_METHODS;
	public final static PathForResources<Constructor<?>[]> CLASS_RELATIVE_PATH_FOR_CONSTRUCTORS;
	
	static {
		if ("sync".equalsIgnoreCase((String)Properties.getGlobalProperty(TYPE_CONFIG_KEY))) {
			PATH_FOR_CONTENTS = new SyncPathForResources<>(1L, Streams::shareContent);
			PATH_FOR_FILE_SYSTEM_ITEMS = new SyncPathForResources<>(1L, fileSystemItem -> fileSystemItem);
			PATH_FOR_ZIP_FILES = new SyncPathForResources<>(1L, zipFileContainer -> zipFileContainer);
			CLASS_RELATIVE_PATH_FOR_FIELDS = new SyncPathForResources<>(1L, fields -> fields);
			CLASS_RELATIVE_PATH_FOR_METHODS = new SyncPathForResources<>(1L, fields -> fields);
			CLASS_RELATIVE_PATH_FOR_CONSTRUCTORS = new SyncPathForResources<>(1L, fields -> fields);
		} else {
			PATH_FOR_CONTENTS = new AsyncPathForResources<>(1L, Streams::shareContent);
			PATH_FOR_FILE_SYSTEM_ITEMS = new AsyncPathForResources<>(1L, fileSystemItem -> fileSystemItem);
			PATH_FOR_ZIP_FILES = new AsyncPathForResources<>(1L, zipFileContainer -> zipFileContainer);	
			CLASS_RELATIVE_PATH_FOR_FIELDS = new AsyncPathForResources<>(1L, fields -> fields);
			CLASS_RELATIVE_PATH_FOR_METHODS = new AsyncPathForResources<>(1L, methods -> methods);
			CLASS_RELATIVE_PATH_FOR_CONSTRUCTORS = new AsyncPathForResources<>(1L, constructors -> constructors);
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
			Map<Long, Map<String, Map<String, R>>> loadedResources;	
			Long partitionStartLevel;
			Function<R, R> sharer;
			
			private Abst(Long partitionStartLevel, Function<R, R> sharer) {
				this.partitionStartLevel = partitionStartLevel;
				this.sharer = sharer;
			}
			
			@Override
			public R upload(String path, Supplier<R> resourceSupplier) {
				path = Strings.Paths.clean(path);
				Long occurences = path.chars().filter(ch -> ch == '/').count();
				Long partitionIndex = occurences > partitionStartLevel? occurences : partitionStartLevel;
				Map<String, Map<String, R>> partion = retrievePartition(loadedResources, partitionIndex);
				Map<String, R> nestedPartition = retrievePartition(partion, partitionIndex, path);
				return upload(nestedPartition, path, resourceSupplier);
			}
			
			@Override
			public R getOrDefault(String path, Supplier<R> resourceSupplier) {
				path = Strings.Paths.clean(path);
				Long occurences = path.chars().filter(ch -> ch == '/').count();
				Long partitionIndex = occurences > partitionStartLevel? occurences : partitionStartLevel;
				Map<String, Map<String, R>> partion = retrievePartition(loadedResources, partitionIndex);
				Map<String, R> nestedPartition = retrievePartition(partion, partitionIndex, path);
				return getOrDefault(nestedPartition, path, resourceSupplier);
			}
			
			@Override
			public R get(String path) {
				return getOrDefault(path, null);
			}
			
			@Override
			public R remove(String path) {
				path = Strings.Paths.clean(path);
				Long occurences = path.chars().filter(ch -> ch == '/').count();
				Long partitionIndex = occurences > partitionStartLevel? occurences : partitionStartLevel;
				Map<String, Map<String, R>> partion = retrievePartition(loadedResources, partitionIndex);
				Map<String, R> nestedPartition = retrievePartition(partion, partitionIndex, path);
				return nestedPartition.remove(path);
			}
			
			@Override
			public int getLoadedResourcesCount() {
				return getLoadedResourcesCount(loadedResources);
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
			loadedResources = new LinkedHashMap<>();
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
				synchronized (loadedResources) {
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
			synchronized (loadedResources) {
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
			loadedResources = new ConcurrentHashMap<>();
			mutexPrefixName = loadedResources.toString();
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
				synchronized (mutexPrefixName + partitionIndex + "_" + partitionKey) {
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
