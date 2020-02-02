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
	FileCriteria directoryCriteriaForFileSystemEntry;
	FileCriteria fileCriteriaForFileSystemEntry;
	FileCriteria archiveCriteriaForFileSystemEntry;
	ZipEntryCriteria fileCriteriaForZipEntry;
	ZipEntryCriteria archiveCriteriaForZipEntry;
	int maxParallelTasksForUnit;
	boolean recursiveOnDirectoryOfFileSystemEntry;
	boolean recursiveOnArchiveOfZipEntry;
	
	FileScanConfigAbst() {
		paths = ConcurrentHashMap.newKeySet();
		maxParallelTasksForUnit = Runtime.getRuntime().availableProcessors();
		fileCriteriaForFileSystemEntry = FileCriteria.create().name(getFilePredicateForFileSystemEntry());
		archiveCriteriaForFileSystemEntry = FileCriteria.create().name(getArchivePredicateForFileSystemEntry());
		fileCriteriaForZipEntry = ZipEntryCriteria.create().name(getFilePredicateForZipEntry());
		archiveCriteriaForZipEntry = ZipEntryCriteria.create().name(getArchivePredicateForZipEntry());
		recursiveOnDirectoryOfFileSystemEntry = true;
		recursiveOnArchiveOfZipEntry = true;
	}
	
	void init() {
		Set<String> temp = new LinkedHashSet<String>(paths);
		paths.clear();
		for(String path : temp) {
			paths.add(Strings.Paths.clean(path));
		}
		temp.clear();
	}
	
	abstract Predicate<String> getFilePredicateForFileSystemEntry();
	
	abstract Predicate<String> getArchivePredicateForFileSystemEntry();
	
	abstract Predicate<String> getFilePredicateForZipEntry();
	
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
	
	public F recursiveOnDirectoryOfFileSystemEntry(boolean recursiveOnDirectoryOfFileSystem) {
		this.recursiveOnDirectoryOfFileSystemEntry = recursiveOnDirectoryOfFileSystem;
		return (F)this;
	}

	public F recursiveOnArchiveOfZipEntry(boolean recursiveOnArchiveOfZipEntry) {
		this.recursiveOnArchiveOfZipEntry = recursiveOnArchiveOfZipEntry;
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
		recursiveOnDirectoryOfFileSystemEntry = false;
		directoryCriteriaForFileSystemEntry = null;
		return (F)this;
	}
	
	public F scanRecursivelyAllDirectoryThat(Predicate<File> predicate) {
		if (this.directoryCriteriaForFileSystemEntry == null) {
			directoryCriteriaForFileSystemEntry = FileCriteria.create();
		}
		this.directoryCriteriaForFileSystemEntry.and().allThat(predicate);
		return (F)this;
	}
	
	public F scanAllFileThat(Predicate<File> predicate) {
		this.fileCriteriaForFileSystemEntry.and().allThat(predicate);
		return (F)this;
	}
	
	public F scanAllArchiveFileThat(Predicate<File> predicate) {
		this.archiveCriteriaForFileSystemEntry.and().allThat(predicate);
		return (F)this;
	}
	
	
	public F loadAllFileThat(Predicate<File> predicate) {
		this.fileCriteriaForFileSystemEntry.and().allThat(predicate);
		return (F)this;
	}
	
	public F loadAllZipEntryThat(Predicate<ZipInputStream.Entry> predicate) {
		this.fileCriteriaForZipEntry.and().allThat(predicate);
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
			fileCriteriaForFileSystemEntry.getPredicateOrTruePredicateIfNull(), 
			fileConsumer
		).scanAllZipFileThat(
			archiveCriteriaForFileSystemEntry.getPredicateOrTruePredicateIfNull()
		).whenFindZipEntryTestAndApply(
			fileCriteriaForZipEntry.getPredicateOrTruePredicateIfNull(),
			zipEntryConsumer
		).setMaxParallelTasks(
			maxParallelTasksForUnit
		);
		if (recursiveOnDirectoryOfFileSystemEntry && directoryCriteriaForFileSystemEntry == null) {
			config.scanRecursivelyAllDirectory();
		} else if (recursiveOnDirectoryOfFileSystemEntry && directoryCriteriaForFileSystemEntry != null) {
			config.scanRecursivelyAllDirectoryThat((basePath, currentPath) -> directoryCriteriaForFileSystemEntry.getPredicateOrTruePredicateIfNull().test(currentPath));
		} else if (!recursiveOnDirectoryOfFileSystemEntry && directoryCriteriaForFileSystemEntry != null) {
			config.scanRecursivelyAllDirectoryThat((basePath, currentPath) -> basePath.equals(currentPath) && directoryCriteriaForFileSystemEntry.getPredicateOrTruePredicateIfNull().test(currentPath));
		} else {
			config.scanStrictlyDirectory();
		}
		if (recursiveOnArchiveOfZipEntry) {
			config.scanRecursivelyAllZipEntryThat(
				archiveCriteriaForZipEntry.getPredicateOrTruePredicateIfNull()
			);
		}
		return config;
	}
	
	abstract F _create();
	
	public  F createCopy() {
		F copy = _create();
		copy.directoryCriteriaForFileSystemEntry = 
			this.directoryCriteriaForFileSystemEntry != null?	
				this.directoryCriteriaForFileSystemEntry.createCopy()
				:null;
		copy.fileCriteriaForFileSystemEntry = this.fileCriteriaForFileSystemEntry.createCopy();
		copy.fileCriteriaForZipEntry = this.fileCriteriaForZipEntry.createCopy();
		copy.archiveCriteriaForFileSystemEntry = this.archiveCriteriaForFileSystemEntry.createCopy();
		copy.archiveCriteriaForZipEntry = this.archiveCriteriaForZipEntry.createCopy();
		copy.paths.addAll(this.getPaths());
		copy.recursiveOnDirectoryOfFileSystemEntry = this.recursiveOnDirectoryOfFileSystemEntry;
		copy.recursiveOnArchiveOfZipEntry = this.recursiveOnArchiveOfZipEntry;
		copy.maxParallelTasksForUnit = this.maxParallelTasksForUnit;
		return copy;
	}
}