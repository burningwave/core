package org.burningwave.core.classes.hunter;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.burningwave.core.common.Strings;
import org.burningwave.core.io.FileCriteria;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.io.ZipEntryCriteria;
import org.burningwave.core.io.ZipInputStream;
import org.burningwave.core.io.FileSystemHelper.Scan.Configuration;

public class ClassFileScanConfiguration {
	private final static Predicate<String> CLASS_PREDICATE = name -> name.endsWith(".class");
	private final static Predicate<String> ARCHIVE_PREDICATE = name -> 
		name.endsWith(".jar") ||
		name.endsWith(".war") ||
		name.endsWith(".ear") ||
		name.endsWith(".zip");
	
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
	
	private ClassFileScanConfiguration() {
		paths = ConcurrentHashMap.newKeySet();
		maxParallelTasksForUnit = Runtime.getRuntime().availableProcessors();
		classCriteriaForFileSystem = FileCriteria.create().name(CLASS_PREDICATE);
		libraryCriteriaForFileSystem = FileCriteria.create().name(ARCHIVE_PREDICATE);
		classCriteriaForZipEntry = ZipEntryCriteria.create().name(CLASS_PREDICATE);
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
	

	static ClassFileScanConfiguration create() {
		return new ClassFileScanConfiguration();
	}
	
	public static ClassFileScanConfiguration forPaths(Collection<String> paths) {
		ClassFileScanConfiguration criteria = create();
		criteria.paths.addAll(paths);
		return criteria;
	}			
	
	public static ClassFileScanConfiguration forPaths(String... paths) {
		return forPaths(Stream.of(paths).collect(Collectors.toCollection(ConcurrentHashMap::newKeySet)));
	}
	

	ClassFileScanConfiguration setPaths(Collection<String> newPaths) {
		this.paths.clear();
		this.paths.addAll(newPaths);
		return this;
	}
	
	public ClassFileScanConfiguration maxParallelTasksForUnit(int value) {
		this.maxParallelTasksForUnit = value;
		return this;
	}
	
	public ClassFileScanConfiguration recursiveOnDirectoryOfFileSystem(boolean recursiveOnDirectoryOfFileSystem) {
		this.recursiveOnDirectoryOfFileSystem = recursiveOnDirectoryOfFileSystem;
		return this;
	}

	public ClassFileScanConfiguration recursiveOnLibraryOfZipEntry(boolean recursiveOnLibraryOfZipEntry) {
		this.recursiveOnLibraryOfZipEntry = recursiveOnLibraryOfZipEntry;
		return this;
	}
	
	public ClassFileScanConfiguration addPaths(Collection<String> paths) {
		this.paths.addAll(paths);
		return this;
	}
	
	Collection<String> getPaths() {
		return paths;
	}
	
	public ClassFileScanConfiguration scanStrictlyDirectory() {
		recursiveOnDirectoryOfFileSystem = false;
		directoryCriteriaForFileSystem = null;
		return this;
	}
	
	public ClassFileScanConfiguration scanRecursivelyAllDirectoryThat(Predicate<File> predicate) {
		if (this.directoryCriteriaForFileSystem == null) {
			directoryCriteriaForFileSystem = FileCriteria.create();
		}
		this.directoryCriteriaForFileSystem.and().allThat(predicate);
		return this;
	}
	
	public ClassFileScanConfiguration scanAllClassFileThat(Predicate<File> predicate) {
		this.classCriteriaForFileSystem.and().allThat(predicate);
		return this;
	}
	
	public ClassFileScanConfiguration scanAllLibraryFileThat(Predicate<File> predicate) {
		this.libraryCriteriaForFileSystem.and().allThat(predicate);
		return this;
	}
	
	
	public ClassFileScanConfiguration loadAllClassFileThat(Predicate<File> predicate) {
		this.classCriteriaForFileSystem.and().allThat(predicate);
		return this;
	}
	
	public ClassFileScanConfiguration loadAllClassZipEntryThat(Predicate<ZipInputStream.Entry> predicate) {
		this.classCriteriaForZipEntry.and().allThat(predicate);
		return this;
	}
	

	<K, I, C extends SearchContext<K, I>, R extends SearchResult<K, I>>Configuration toScanConfiguration(C context, Hunter<K, I, C, R> hunter) {
		Configuration config = Configuration.forPaths(
			getPaths()
		).whenFindFileTestAndApply(
			classCriteriaForFileSystem.getPredicateOrTruePredicateIfNull(), 
			hunter.getFileSystemEntryTransformer(context)
		).scanAllZipFileThat(
			libraryCriteriaForFileSystem.getPredicateOrTruePredicateIfNull()
		).whenFindZipEntryTestAndApply(
			classCriteriaForZipEntry.getPredicateOrTruePredicateIfNull(),
			hunter.getZipEntryTransformer(context)
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
	
	public ClassFileScanConfiguration createCopy() {
		ClassFileScanConfiguration copy = new ClassFileScanConfiguration();
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