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
package org.burningwave.core.classes;

import static org.burningwave.core.assembler.StaticComponentContainer.Paths;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.burningwave.core.io.FileScanConfigAbst;

public class CacheableSearchConfig extends SearchConfigAbst<CacheableSearchConfig> {
	
	int maxParallelTasksForUnit;
	Integer checkFileOptions;
	Collection<String> paths;
	private boolean optimizePaths;
	
	CacheableSearchConfig() {
		super();
		paths = ConcurrentHashMap.newKeySet();
		maxParallelTasksForUnit = Runtime.getRuntime().availableProcessors();
		this.checkFileOptions = FileScanConfigAbst.CHECK_FILE_OPTIONS_DEFAULT_VALUE;
		this.optimizePaths = false;
	}
	
	void init(PathMemoryClassLoader classSupplier) {
		super.init(classSupplier);
		Set<String> temp = new LinkedHashSet<String>(paths);
		paths.clear();
		for(String path : temp) {
			paths.add(Paths.clean(path));
		}
		temp.clear();
	}
	
	public CacheableSearchConfig maxParallelTasksForUnit(int value) {
		this.maxParallelTasksForUnit = value;
		return this;
	}
	
	public CacheableSearchConfig addPaths(Collection<String> paths) {
		for (String path : paths) {
			this.paths.add(Paths.normalizeAndClean(path));
		}
		return this;
	}
	
	public CacheableSearchConfig optimizePaths(boolean flag) {
		this.optimizePaths = flag;
		return this;
	}
	
	public boolean isOptimizePathsEnabled() {
		return optimizePaths;
	}
	
	public Collection<String> getPaths() {
		return paths;
	}	
	
	public CacheableSearchConfig checkFileOptions(Integer value) {
		this.checkFileOptions = value;
		return this;
	}
	
	public Integer getCheckFileOptions() {
		return checkFileOptions;
	}
	
	@Override
	public CacheableSearchConfig createCopy() {
		CacheableSearchConfig copy = super.createCopy();
		copy.checkFileOptions = this.checkFileOptions;
		copy.paths.addAll(this.getPaths());
		copy.maxParallelTasksForUnit = this.maxParallelTasksForUnit;
		return copy;
	}
	
	@Override
	public void close() {
		paths.clear();
		paths = null;
		super.close();
	}
}