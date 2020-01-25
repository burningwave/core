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

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

import org.burningwave.core.common.Strings;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.Streams;

public class Cache {
	public final static PathForResources<ByteBuffer> PATH_FOR_CONTENTS = new PathForResources<>(Streams::shareContent);
	public final static PathForResources<FileSystemItem> PATH_FOR_FILE_SYSTEM_ITEMS = new PathForResources<>(fileSystemItem -> fileSystemItem);
	
	public static class PathForResources<R> {
		private Map<Long, Map<String, Map<String, R>>> LOADED_RESOURCES = new ConcurrentHashMap<>();
		private String MUTEX_PREFIX_NAME = PathForResources.class.getName();	
		private Long PARTITION_START_LEVEL = 1L;
		private Function<R, R> sharer;
		
		private PathForResources(Function<R, R> sharer) {
			this.sharer = sharer;
		}
		
		public R upload(String path, Supplier<R> resourceSupplier) {
			path = Strings.Paths.clean(path);
			Long occurences = path.chars().filter(ch -> ch == '/').count();
			Long partitionIndex = occurences > PARTITION_START_LEVEL? occurences : PARTITION_START_LEVEL;
			Map<String, Map<String, R>> partion = retrievePartition(LOADED_RESOURCES, partitionIndex);
			Map<String, R> nestedPartition = retrievePartition(partion, partitionIndex, path);
			return upload(nestedPartition, path, resourceSupplier);
		}
		
		public R getOrDefault(String path, Supplier<R> resourceSupplier) {
			path = Strings.Paths.clean(path);
			Long occurences = path.chars().filter(ch -> ch == '/').count();
			Long partitionIndex = occurences > PARTITION_START_LEVEL? occurences : PARTITION_START_LEVEL;
			Map<String, Map<String, R>> partion = retrievePartition(LOADED_RESOURCES, partitionIndex);
			Map<String, R> nestedPartition = retrievePartition(partion, partitionIndex, path);
			return getOrDefault(nestedPartition, path, resourceSupplier);
		}

		private Map<String, R> retrievePartition(Map<String, Map<String, R>> partion, Long partitionIndex, String path) {
			String partitionKey = "/";
			if (partitionIndex > 1) {
				partitionKey = path.substring(0, path.lastIndexOf("/"));
				partitionKey = partitionKey.substring(partitionKey.lastIndexOf("/") + 1);
			}
			Map<String, R> innerPartion = partion.get(partitionKey);
			if (innerPartion == null) {
				synchronized (MUTEX_PREFIX_NAME + partitionIndex + "_" + partitionKey) {
					innerPartion = partion.get(partitionKey);
					if (innerPartion == null) {
						partion.put(partitionKey, innerPartion = new ConcurrentHashMap<>());
					}
				}
			}
			return innerPartion;
		}
		
		public R getOrDefault(Map<String, R> loadedResources, String path, Supplier<R> resourceSupplier) {
			R resource = loadedResources.get(path);
			if (resource == null) {
				synchronized (MUTEX_PREFIX_NAME + "_" + path) {
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
			synchronized (MUTEX_PREFIX_NAME + "_" + path) {
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
				synchronized (MUTEX_PREFIX_NAME + "_" + partitionIndex) {
					resources = resourcesPartitioned.get(partitionIndex);
					if (resources == null) {
						resourcesPartitioned.put(partitionIndex, resources = new ConcurrentHashMap<>());
					}
				}
			}
			return resources;
		}
		
		public int getLoadedResourcesCount() {
			return getLoadedResourcesCount(LOADED_RESOURCES);
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
