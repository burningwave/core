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

import static org.burningwave.core.assembler.StaticComponentContainer.Constructors;
import static org.burningwave.core.assembler.StaticComponentContainer.GlobalProperties;
import static org.burningwave.core.assembler.StaticComponentContainer.Paths;
import static org.burningwave.core.assembler.StaticComponentContainer.Streams;

import java.io.File;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.burningwave.core.function.ThrowingSupplier;
import org.burningwave.core.io.FileSystemScanner.Scan;
import org.burningwave.core.io.FileSystemScanner.Scan.Configuration;
import org.burningwave.core.io.IterableZipContainer.Entry;

@SuppressWarnings({"unchecked"})
public abstract class FileScanConfigAbst<F extends FileScanConfigAbst<F>> {
	private final static String DEFAULT_CHECK_FILE_OPTIONS_CONFIG_KEY = "file-system-scanner.default-scan-config.check-file-options";
	
	public final static Integer CHECK_FILE_NAME = 0b00000001;
	public final static Integer CHECK_FILE_SIGNATURE = 0b00000100;
	public final static Integer CHECK_FILE_NAME_AND_SIGNATURE = 0b00000111;
	public final static Integer CHECK_FILE_NAME_OR_SIGNATURE = 0b00000101;
	public final static Integer CHECK_FILE_OPTIONS_DEFAULT_VALUE = parseCheckFileOptionsValue(
		GlobalProperties.getProperty(DEFAULT_CHECK_FILE_OPTIONS_CONFIG_KEY),
		CHECK_FILE_NAME
	);
		
	PathHelper pathHelper;
	Collection<String> paths;
	FileCriteria directoryCriteriaForFileSystemEntry;
	FileCriteria fileCriteriaForFileSystemEntry;
	FileCriteria archiveCriteriaForFileSystemEntry;
	ZipContainerEntryCriteria fileCriteriaForZipEntry;
	ZipContainerEntryCriteria archiveCriteriaForZipEntry;
	int maxParallelTasksForUnit;
	boolean recursiveOnDirectoryOfFileSystemEntry;
	boolean recursiveOnArchiveOfZipEntry;
	Integer checkFileOptions;
	boolean optimizePaths;
	
	FileScanConfigAbst() {
		paths = ConcurrentHashMap.newKeySet();
		maxParallelTasksForUnit = Runtime.getRuntime().availableProcessors();
		fileCriteriaForFileSystemEntry = FileCriteria.create();
		archiveCriteriaForFileSystemEntry = FileCriteria.create();
		fileCriteriaForZipEntry = ZipContainerEntryCriteria.create();
		archiveCriteriaForZipEntry = ZipContainerEntryCriteria.create();
		recursiveOnDirectoryOfFileSystemEntry = true;
		recursiveOnArchiveOfZipEntry = true;
		optimizePaths = false;
		checkFileOptions = CHECK_FILE_OPTIONS_DEFAULT_VALUE;
	}
	
	void init() {
		fileCriteriaForFileSystemEntry = FileCriteria.create().allThat(getFilePredicateForFileSystemEntry()).and(fileCriteriaForFileSystemEntry);
		archiveCriteriaForFileSystemEntry = FileCriteria.create().allThat(getArchivePredicateForFileSystemEntry()).and(archiveCriteriaForFileSystemEntry);
		fileCriteriaForZipEntry = ZipContainerEntryCriteria.create().allThat(getFilePredicateForZipEntry()).and(fileCriteriaForZipEntry);
		archiveCriteriaForZipEntry = ZipContainerEntryCriteria.create().allThat(getArchivePredicateForZipEntry()).and(archiveCriteriaForZipEntry);
	}
	
	public static final Integer parseCheckFileOptionsValue(String property, Integer defaultValue) {
		if (property != null) {
			if (property.contains("checkFileName") && property.contains("checkFileSignature") && property.contains("|")) {
				return FileScanConfigAbst.CHECK_FILE_NAME_OR_SIGNATURE;
			} else if (property.contains("checkFileName") && property.contains("checkFileSignature") && property.contains("&")) {
				return FileScanConfigAbst.CHECK_FILE_NAME_AND_SIGNATURE;
			} else if (property.contains("checkFileName")) {
				return FileScanConfigAbst.CHECK_FILE_NAME;
			} else if (property.contains("checkFileSignature")) {
				return FileScanConfigAbst.CHECK_FILE_SIGNATURE;
			}
		}
		return defaultValue;
	}
	
	Predicate<File> getArchivePredicateForFileSystemEntry(){
		Predicate<File> checkFileName = entry -> {
			String name = entry.getName();
			return name.endsWith(".jar") ||
				name.endsWith(".war") ||
				name.endsWith(".ear") ||
				name.endsWith(".zip") ||
				name.endsWith(".jmod");
		};		
		Predicate<File> checkFileSignature = entry -> ThrowingSupplier.get(() -> Streams.isArchive(entry));
		if (checkFileOptions == CHECK_FILE_SIGNATURE) {
			return checkFileSignature;
		} else if (checkFileOptions == CHECK_FILE_NAME_AND_SIGNATURE) {
			return checkFileName.and(checkFileSignature);
		} else if (checkFileOptions == CHECK_FILE_NAME_OR_SIGNATURE) {
			return checkFileName.or(checkFileSignature);
		} else if (checkFileOptions == CHECK_FILE_NAME) {
			return checkFileName;
		}
		return null;
	}
	
	Predicate<File> getFilePredicateForFileSystemEntry() {
		Predicate<File> checkFileName = getFileNameChecker();
		Predicate<File> checkFileSignature = getFileContentChecker();
		if (checkFileOptions == CHECK_FILE_SIGNATURE) {
			return checkFileSignature;
		} else if (checkFileOptions == CHECK_FILE_NAME_AND_SIGNATURE) {
			return checkFileName.and(checkFileSignature);
		} else if (checkFileOptions == CHECK_FILE_NAME_OR_SIGNATURE) {
			return checkFileName.or(checkFileSignature);
		} else if (checkFileOptions == CHECK_FILE_NAME) {
			return checkFileName;
		}
		return null;
	}

	protected abstract Predicate<File> getFileContentChecker();

	protected abstract Predicate<File> getFileNameChecker();

	Predicate<Entry> getArchivePredicateForZipEntry() {
		Predicate<Entry> checkFileName = entry -> {
			String name = entry.getName();
			return name.endsWith(".jar") ||
				name.endsWith(".war") ||
				name.endsWith(".ear") ||
				name.endsWith(".zip") ||
				name.endsWith(".jmod");
		};
		Predicate<Entry> checkFileSignature = entry -> ThrowingSupplier.get(() -> Streams.isArchive(entry.toByteBuffer()));
		if (checkFileOptions == CHECK_FILE_SIGNATURE) {
			return checkFileSignature;
		} else if (checkFileOptions == CHECK_FILE_NAME_AND_SIGNATURE) {
			return checkFileName.and(checkFileSignature);
		} else if (checkFileOptions == CHECK_FILE_NAME_OR_SIGNATURE) {
			return checkFileName.or(checkFileSignature);
		} else if (checkFileOptions == CHECK_FILE_NAME) {
			return checkFileName;
		}
		return null;
	}

	Predicate<Entry> getFilePredicateForZipEntry() {
		Predicate<Entry> checkFileName = getZipEntryNameChecker();
		Predicate<Entry> checkFileSignature = getZipEntryContentChecker();
		if (checkFileOptions == CHECK_FILE_SIGNATURE) {
			return checkFileSignature;
		} else if (checkFileOptions == CHECK_FILE_NAME_AND_SIGNATURE) {
			return checkFileName.and(checkFileSignature);
		} else if (checkFileOptions == CHECK_FILE_NAME_OR_SIGNATURE) {
			return checkFileName.or(checkFileSignature);
		} else if (checkFileOptions == CHECK_FILE_NAME) {
			return checkFileName;
		}
		return null;
	}
	
	protected abstract Predicate<Entry> getZipEntryContentChecker();

	protected abstract Predicate<Entry> getZipEntryNameChecker();
	
	public F checkFileOptions(Integer options) {
		this.checkFileOptions = options;
		return (F)this;
	}
	
	public Integer getCheckFileOptions() {
		return checkFileOptions;
	}
	
	public F addPaths(Collection<String>... pathColls) {
		for(Collection<String> pathColl : pathColls) {
			for (String path : pathColl) {
				paths.add(Paths.normalizeAndClean(path));
			}
		}
		return (F)this;
	}
	
	public F setPaths(Collection<String>... newPaths) {
		this.paths.clear();
		return addPaths(newPaths);
	}
	
	public F optimizePaths(boolean flag) {
		this.optimizePaths = flag;
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
	
	public F loadAllZipEntryThat(Predicate<IterableZipContainer.Entry> predicate) {
		this.fileCriteriaForZipEntry.and().allThat(predicate);
		return (F)this;
	}
	
	public Configuration toScanConfiguration(
		Consumer<Scan.ItemContext> itemConsumer
	) {
		return toScanConfiguration(itemConsumer, itemConsumer);
	}
	
	public Configuration toScanConfiguration(
		Consumer<Scan.ItemContext> fileConsumer,
		Consumer<Scan.ItemContext> zipEntryConsumer
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
		).optimizePaths(
			optimizePaths
		);
		if (recursiveOnDirectoryOfFileSystemEntry && directoryCriteriaForFileSystemEntry == null) {
			config.scanRecursivelyAllDirectory();
		} else if (recursiveOnDirectoryOfFileSystemEntry && directoryCriteriaForFileSystemEntry != null) {
			config.scanRecursivelyAllDirectoryThat((basePath, currentPath) ->
				directoryCriteriaForFileSystemEntry.getPredicateOrTruePredicateIfNull().test(currentPath));
		} else if (!recursiveOnDirectoryOfFileSystemEntry && directoryCriteriaForFileSystemEntry != null) {
			config.scanRecursivelyAllDirectoryThat((basePath, currentPath) ->
				basePath.equals(currentPath) && directoryCriteriaForFileSystemEntry.getPredicateOrTruePredicateIfNull().test(currentPath));
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
	
	private final F createNew() {
		return Constructors.newInstanceOf(this);
	}
	
	public  F createCopy() {
		F copy = createNew();
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
		copy.optimizePaths = this.optimizePaths;
		copy.checkFileOptions = this.checkFileOptions;
		return copy;
	}
}