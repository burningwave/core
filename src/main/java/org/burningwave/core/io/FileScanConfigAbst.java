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

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.burningwave.core.common.Strings;
import org.burningwave.core.io.FileSystemHelper.Scan;
import org.burningwave.core.io.FileSystemHelper.Scan.Configuration;

@SuppressWarnings({"unchecked"})
public abstract class FileScanConfigAbst<F extends FileScanConfigAbst<F>> {
	
	PathHelper pathHelper;
	Collection<String> paths;
	FileCriteria directoryCriteriaForFileSystem;
	FileCriteria classCriteriaForFileSystem;
	FileCriteria libraryCriteriaForFileSystem;
	ZipEntryCriteria classCriteriaForZipEntry;
	ZipEntryCriteria libraryCriteriaForZipEntry;
	int maxParallelTasksForUnit;
	boolean recursiveOnDirectoryOfFileSystem;
	boolean recursiveOnLibraryOfZipEntry;
	
	FileScanConfigAbst() {
		paths = ConcurrentHashMap.newKeySet();
		maxParallelTasksForUnit = Runtime.getRuntime().availableProcessors();
		classCriteriaForFileSystem = FileCriteria.create().name(getClassPredicateForFileSystem());
		libraryCriteriaForFileSystem = FileCriteria.create().name(getArchivePredicateForFileSystem());
		classCriteriaForZipEntry = ZipEntryCriteria.create().name(getClassPredicateForZipEntry());
		libraryCriteriaForZipEntry = ZipEntryCriteria.create().name(getArchivePredicateForZipEntry());
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
	
	abstract Predicate<String> getClassPredicateForFileSystem();
	
	abstract Predicate<String> getArchivePredicateForFileSystem();
	
	abstract Predicate<String> getClassPredicateForZipEntry();
	
	abstract Predicate<String> getArchivePredicateForZipEntry();
	

	public F setPaths(Collection<String> newPaths) {
		this.paths.clear();
		this.paths.addAll(newPaths);
		return (F)this;
	}
	
	public F maxParallelTasksForUnit(int value) {
		this.maxParallelTasksForUnit = value;
		return (F)this;
	}
	
	public F recursiveOnDirectoryOfFileSystem(boolean recursiveOnDirectoryOfFileSystem) {
		this.recursiveOnDirectoryOfFileSystem = recursiveOnDirectoryOfFileSystem;
		return (F)this;
	}

	public F recursiveOnLibraryOfZipEntry(boolean recursiveOnLibraryOfZipEntry) {
		this.recursiveOnLibraryOfZipEntry = recursiveOnLibraryOfZipEntry;
		return (F)this;
	}
	
	public F addPaths(Collection<String> paths) {
		this.paths.addAll(paths);
		return (F)this;
	}
	
	public Collection<String> getPaths() {
		return paths;
	}
	
	public int getMaxParallelTasksForUnit() {
		return this.maxParallelTasksForUnit;
	}
	
	public F scanStrictlyDirectory() {
		recursiveOnDirectoryOfFileSystem = false;
		directoryCriteriaForFileSystem = null;
		return (F)this;
	}
	
	public F scanRecursivelyAllDirectoryThat(Predicate<File> predicate) {
		if (this.directoryCriteriaForFileSystem == null) {
			directoryCriteriaForFileSystem = FileCriteria.create();
		}
		this.directoryCriteriaForFileSystem.and().allThat(predicate);
		return (F)this;
	}
	
	public F scanAllClassFileThat(Predicate<File> predicate) {
		this.classCriteriaForFileSystem.and().allThat(predicate);
		return (F)this;
	}
	
	public F scanAllLibraryFileThat(Predicate<File> predicate) {
		this.libraryCriteriaForFileSystem.and().allThat(predicate);
		return (F)this;
	}
	
	
	public F loadAllClassFileThat(Predicate<File> predicate) {
		this.classCriteriaForFileSystem.and().allThat(predicate);
		return (F)this;
	}
	
	public F loadAllClassZipEntryThat(Predicate<ZipInputStream.Entry> predicate) {
		this.classCriteriaForZipEntry.and().allThat(predicate);
		return (F)this;
	}
	

	public Configuration toScanConfiguration(
		Consumer<Scan.ItemContext<FileInputStream>> fileConsumer,
		Consumer<Scan.ItemContext<ZipInputStream.Entry>> zipEntryConsumer
	) {
		init();
		Configuration config = Configuration.forPaths(
			getPaths()
		).whenFindFileTestAndApply(
			classCriteriaForFileSystem.getPredicateOrTruePredicateIfNull(), 
			fileConsumer
		).scanAllZipFileThat(
			libraryCriteriaForFileSystem.getPredicateOrTruePredicateIfNull()
		).whenFindZipEntryTestAndApply(
			classCriteriaForZipEntry.getPredicateOrTruePredicateIfNull(),
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
	
	abstract F _create();
	
	public  F createCopy() {
		F copy = _create();
		copy.directoryCriteriaForFileSystem = 
			this.directoryCriteriaForFileSystem != null?	
				this.directoryCriteriaForFileSystem.createCopy()
				:null;
		copy.classCriteriaForFileSystem = this.classCriteriaForFileSystem.createCopy();
		copy.classCriteriaForZipEntry = this.classCriteriaForZipEntry.createCopy();
		copy.libraryCriteriaForFileSystem = this.libraryCriteriaForFileSystem.createCopy();
		copy.libraryCriteriaForZipEntry = this.libraryCriteriaForZipEntry.createCopy();
		copy.paths.addAll(this.getPaths());
		copy.recursiveOnDirectoryOfFileSystem = this.recursiveOnDirectoryOfFileSystem;
		copy.recursiveOnLibraryOfZipEntry = this.recursiveOnLibraryOfZipEntry;
		copy.maxParallelTasksForUnit = this.maxParallelTasksForUnit;
		return copy;
	}
}