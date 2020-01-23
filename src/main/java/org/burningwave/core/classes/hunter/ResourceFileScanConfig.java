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
package org.burningwave.core.classes.hunter;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.burningwave.core.common.Strings;
import org.burningwave.core.io.FileCriteria;
import org.burningwave.core.io.FileInputStream;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.io.ZipEntryCriteria;
import org.burningwave.core.io.ZipInputStream;
import org.burningwave.core.io.FileSystemHelper.Scan;
import org.burningwave.core.io.FileSystemHelper.Scan.Configuration;

public class ResourceFileScanConfig {
	private final static Predicate<String> ARCHIVE_PREDICATE = name -> 
		name.endsWith(".jar") ||
		name.endsWith(".war") ||
		name.endsWith(".ear") ||
		name.endsWith(".zip");
	private final static Predicate<String> RESOURCE_PREDICATE = ARCHIVE_PREDICATE.negate().and(name -> !name.endsWith(".class"));
	private final static Predicate<String> RESOURCE_PREDICATE_FOR_ZIP_ENTRY = RESOURCE_PREDICATE.and(name -> !name.endsWith("/"));
	PathHelper pathHelper;
	Collection<String> paths;
	FileCriteria directoryCriteriaForFileSystem;
	FileCriteria resourceCriteriaForFileSystem;
	FileCriteria libraryCriteriaForFileSystem;
	ZipEntryCriteria resourceCriteriaForZipEntry;
	ZipEntryCriteria libraryCriteriaForZipEntry;
	int maxParallelTasksForUnit;
	boolean recursiveOnDirectoryOfFileSystem;
	boolean recursiveOnLibraryOfZipEntry;
	
	private ResourceFileScanConfig() {
		paths = ConcurrentHashMap.newKeySet();
		maxParallelTasksForUnit = Runtime.getRuntime().availableProcessors();
		resourceCriteriaForFileSystem = FileCriteria.create().name(RESOURCE_PREDICATE);
		libraryCriteriaForFileSystem = FileCriteria.create().name(ARCHIVE_PREDICATE);
		resourceCriteriaForZipEntry = ZipEntryCriteria.create().name(RESOURCE_PREDICATE_FOR_ZIP_ENTRY);
		libraryCriteriaForZipEntry = ZipEntryCriteria.create().name(ARCHIVE_PREDICATE);
		recursiveOnDirectoryOfFileSystem = true;
		recursiveOnLibraryOfZipEntry = true;
	}
	
	void init() {
		Set<String> temp = new LinkedHashSet<String>(paths);
		paths.clear();
		for(String path : temp) {
			paths.add(Strings.Paths.clean(path));
		}
		temp.clear();
	}
	

	static ResourceFileScanConfig create() {
		return new ResourceFileScanConfig();
	}
	
	public static ResourceFileScanConfig forPaths(Collection<String> paths) {
		ResourceFileScanConfig criteria = create();
		criteria.paths.addAll(paths);
		return criteria;
	}			
	
	public static ResourceFileScanConfig forPaths(String... paths) {
		return forPaths(Stream.of(paths).collect(Collectors.toCollection(ConcurrentHashMap::newKeySet)));
	}
	

	ResourceFileScanConfig setPaths(Collection<String> newPaths) {
		this.paths.clear();
		this.paths.addAll(newPaths);
		return this;
	}
	
	public ResourceFileScanConfig maxParallelTasksForUnit(int value) {
		this.maxParallelTasksForUnit = value;
		return this;
	}
	
	public ResourceFileScanConfig recursiveOnDirectoryOfFileSystem(boolean recursiveOnDirectoryOfFileSystem) {
		this.recursiveOnDirectoryOfFileSystem = recursiveOnDirectoryOfFileSystem;
		return this;
	}

	public ResourceFileScanConfig recursiveOnLibraryOfZipEntry(boolean recursiveOnLibraryOfZipEntry) {
		this.recursiveOnLibraryOfZipEntry = recursiveOnLibraryOfZipEntry;
		return this;
	}
	
	public ResourceFileScanConfig addPaths(Collection<String> paths) {
		this.paths.addAll(paths);
		return this;
	}
	
	Collection<String> getPaths() {
		return paths;
	}
	
	public ResourceFileScanConfig scanStrictlyDirectory() {
		recursiveOnDirectoryOfFileSystem = false;
		directoryCriteriaForFileSystem = null;
		return this;
	}
	
	public ResourceFileScanConfig scanRecursivelyAllDirectoryThat(Predicate<File> predicate) {
		if (this.directoryCriteriaForFileSystem == null) {
			directoryCriteriaForFileSystem = FileCriteria.create();
		}
		this.directoryCriteriaForFileSystem.and().allThat(predicate);
		return this;
	}
	
	public ResourceFileScanConfig scanAllClassFileThat(Predicate<File> predicate) {
		this.resourceCriteriaForFileSystem.and().allThat(predicate);
		return this;
	}
	
	public ResourceFileScanConfig scanAllLibraryFileThat(Predicate<File> predicate) {
		this.libraryCriteriaForFileSystem.and().allThat(predicate);
		return this;
	}
	
	
	public ResourceFileScanConfig loadAllClassFileThat(Predicate<File> predicate) {
		this.resourceCriteriaForFileSystem.and().allThat(predicate);
		return this;
	}
	
	public ResourceFileScanConfig loadAllClassZipEntryThat(Predicate<ZipInputStream.Entry> predicate) {
		this.resourceCriteriaForZipEntry.and().allThat(predicate);
		return this;
	}
	

	public Configuration toScanConfiguration(
		Consumer<Scan.ItemContext<FileInputStream>> fileConsumer,
		Consumer<Scan.ItemContext<ZipInputStream.Entry>> zipEntryConsumer
	) {
		Configuration config = Configuration.forPaths(
			getPaths()
		).whenFindFileTestAndApply(
			resourceCriteriaForFileSystem.getPredicateOrTruePredicateIfNull(), 
			fileConsumer
		).scanAllZipFileThat(
			libraryCriteriaForFileSystem.getPredicateOrTruePredicateIfNull()
		).whenFindZipEntryTestAndApply(
			resourceCriteriaForZipEntry.getPredicateOrTruePredicateIfNull(),
			zipEntryConsumer
		).setMaxParallelTasks(
			maxParallelTasksForUnit
		);
		if (recursiveOnDirectoryOfFileSystem && directoryCriteriaForFileSystem == null) {
			config.scanRecursivelyAllDirectory();
		} else if (recursiveOnDirectoryOfFileSystem && directoryCriteriaForFileSystem != null) {
			config.scanRecursivelyAllDirectoryThat((basePath, currentPath) -> directoryCriteriaForFileSystem.getPredicateOrTruePredicateIfNull().test(currentPath));
		} else if (!recursiveOnDirectoryOfFileSystem && directoryCriteriaForFileSystem != null) {
			config.scanRecursivelyAllDirectoryThat((basePath, currentPath) -> basePath.equals(currentPath) && directoryCriteriaForFileSystem.getPredicateOrTruePredicateIfNull().test(currentPath));
		} else {
			config.scanStrictlyDirectory();
		}
		if (recursiveOnLibraryOfZipEntry) {
			config.scanRecursivelyAllZipEntryThat(
				libraryCriteriaForZipEntry.getPredicateOrTruePredicateIfNull()
			);
		}
		return config;
	}
	
	public ResourceFileScanConfig createCopy() {
		ResourceFileScanConfig copy = new ResourceFileScanConfig();
		copy.directoryCriteriaForFileSystem = 
			this.directoryCriteriaForFileSystem != null?	
				this.directoryCriteriaForFileSystem.createCopy()
				:null;
		copy.resourceCriteriaForFileSystem = this.resourceCriteriaForFileSystem.createCopy();
		copy.resourceCriteriaForZipEntry = this.resourceCriteriaForZipEntry.createCopy();
		copy.libraryCriteriaForFileSystem = this.libraryCriteriaForFileSystem.createCopy();
		copy.libraryCriteriaForZipEntry = this.libraryCriteriaForZipEntry.createCopy();
		copy.paths.addAll(this.getPaths());
		copy.recursiveOnDirectoryOfFileSystem = this.recursiveOnDirectoryOfFileSystem;
		copy.recursiveOnLibraryOfZipEntry = this.recursiveOnLibraryOfZipEntry;
		copy.maxParallelTasksForUnit = this.maxParallelTasksForUnit;
		return copy;
	}
}