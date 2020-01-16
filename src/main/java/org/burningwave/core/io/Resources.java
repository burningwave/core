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
import java.util.function.Supplier;

import org.burningwave.core.common.Strings;

public class Resources {
	private final static Map<Long, Map<String, Map<String, ByteBuffer>>> LOADED_RESOURCES = new ConcurrentHashMap<>();
	private final static String MUTEX_PREFIX_NAME = Resources.class.getName();	
	private final static Long PARTITION_START_LEVEL = 1L;

	public static ByteBuffer getOrDefault(String path, Supplier<ByteBuffer> resourceSupplier) {
		path = Strings.Paths.clean(path);
		Long occurences = path.chars().filter(ch -> ch == '/').count();
		Long partitionIndex = occurences > PARTITION_START_LEVEL? occurences : PARTITION_START_LEVEL;
		Map<String, Map<String, ByteBuffer>> partion = retrievePartition(LOADED_RESOURCES, partitionIndex);
		Map<String, ByteBuffer> nestedPartition = retrievePartition(partion, partitionIndex, path);
		return getOrDefault(nestedPartition, path, resourceSupplier);
	}

	private static Map<String, ByteBuffer> retrievePartition(Map<String, Map<String, ByteBuffer>> partion, Long partitionIndex, String path) {
		String partitionKey = path.substring(0, path.lastIndexOf("/"));
		partitionKey = partitionKey.substring(partitionKey.lastIndexOf("/") + 1);
		Map<String, ByteBuffer> innerPartion = partion.get(partitionKey);
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

	public static ByteBuffer getOrDefault(Map<String, ByteBuffer> loadedResources, String path, Supplier<ByteBuffer> resourceSupplier) {
		ByteBuffer resource = loadedResources.get(path);
		if (resource == null) {
			synchronized (MUTEX_PREFIX_NAME + "_" + path) {
				resource = loadedResources.get(path);
				if (resource == null && resourceSupplier != null) {
					resource = resourceSupplier.get();
					if (resource != null) {
						loadedResources.put(path, resource = Streams.shareContent(resource));
					}
				}
			}
		}
		return resource != null? 
			Streams.shareContent(resource) :
			resource;
	}
	
	private static Map<String, Map<String, ByteBuffer>> retrievePartition(Map<Long, Map<String, Map<String, ByteBuffer>>> resourcesPartitioned, Long partitionIndex) {
		Map<String, Map<String, ByteBuffer>> resources = resourcesPartitioned.get(partitionIndex);
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
	
	public static int getLoadedResourcesCount() {
		return getLoadedResourcesCount(LOADED_RESOURCES);
	}
	
	private static int getLoadedResourcesCount(Map<Long, Map<String, Map<String, ByteBuffer>>> resources) {
		int count = 0;
		for (Map.Entry<Long, Map<String, Map<String, ByteBuffer>>> partition : resources.entrySet()) {
			for (Map.Entry<String, Map<String, ByteBuffer>> innerPartition : partition.getValue().entrySet()) {
				count += innerPartition.getValue().size();
			}
		}
		return count;
	}
	
}
