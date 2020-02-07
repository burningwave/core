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

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

import org.burningwave.core.Strings;

public class Cache {
	public final static PathForResources<ByteBuffer> PATH_FOR_CONTENTS = new PathForResources<>(1L, Streams::shareContent);
	public final static PathForResources<FileSystemItem> PATH_FOR_FILE_SYSTEM_ITEMS = new PathForResources<>(1L, fileSystemItem -> fileSystemItem);
	public final static PathForResources<ZipFileContainer> PATH_FOR_ZIP_FILE_CONTAINERS = new PathForResources<>(1L, zipFileContainer -> zipFileContainer);	
	
	public static class PathForResources<R> {
		private Map<Long, Map<String, Map<String, R>>> loadedResources = new ConcurrentHashMap<>();
		private String mutexPrefixName = loadedResources.toString();	
		private Long partitionStartLevel;
		private Function<R, R> sharer;
		
		private PathForResources(Long partitionStartLevel, Function<R, R> sharer) {
			this.partitionStartLevel = partitionStartLevel;
			this.sharer = sharer;
		}
		
		public R upload(String path, Supplier<R> resourceSupplier) {
			path = Strings.Paths.clean(path);
			Long occurences = path.chars().filter(ch -> ch == '/').count();
			Long partitionIndex = occurences > partitionStartLevel? occurences : partitionStartLevel;
			Map<String, Map<String, R>> partion = retrievePartition(loadedResources, partitionIndex);
			Map<String, R> nestedPartition = retrievePartition(partion, partitionIndex, path);
			return upload(nestedPartition, path, resourceSupplier);
		}
		
		public R getOrDefault(String path, Supplier<R> resourceSupplier) {
			path = Strings.Paths.clean(path);
			Long occurences = path.chars().filter(ch -> ch == '/').count();
			Long partitionIndex = occurences > partitionStartLevel? occurences : partitionStartLevel;
			Map<String, Map<String, R>> partion = retrievePartition(loadedResources, partitionIndex);
			Map<String, R> nestedPartition = retrievePartition(partion, partitionIndex, path);
			return getOrDefault(nestedPartition, path, resourceSupplier);
		}
		
		public R get(String path) {
			return getOrDefault(path, null);
		}
		
		public R remove(String path) {
			path = Strings.Paths.clean(path);
			Long occurences = path.chars().filter(ch -> ch == '/').count();
			Long partitionIndex = occurences > partitionStartLevel? occurences : partitionStartLevel;
			Map<String, Map<String, R>> partion = retrievePartition(loadedResources, partitionIndex);
			Map<String, R> nestedPartition = retrievePartition(partion, partitionIndex, path);
			return nestedPartition.remove(path);
		}
		
		private Map<String, R> retrievePartition(Map<String, Map<String, R>> partion, Long partitionIndex, String path) {
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
		
		private R getOrDefault(Map<String, R> loadedResources, String path, Supplier<R> resourceSupplier) {
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
		
		private Map<String, Map<String, R>> retrievePartition(Map<Long, Map<String, Map<String, R>>> resourcesPartitioned, Long partitionIndex) {
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
		
	}

}
