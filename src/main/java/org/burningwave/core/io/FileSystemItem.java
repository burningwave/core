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

import static org.burningwave.core.assembler.StaticComponentContainer.Cache;
import static org.burningwave.core.assembler.StaticComponentContainer.IterableObjectHelper;
import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggersRepository;
import static org.burningwave.core.assembler.StaticComponentContainer.Objects;
import static org.burningwave.core.assembler.StaticComponentContainer.Paths;
import static org.burningwave.core.assembler.StaticComponentContainer.Streams;
import static org.burningwave.core.assembler.StaticComponentContainer.Strings;
import static org.burningwave.core.assembler.StaticComponentContainer.Synchronizer;
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.burningwave.core.function.Executor;

@SuppressWarnings("resource")
public class FileSystemItem {
	private final static String instanceIdPrefix;
	
	private Map.Entry<String, String> absolutePath;
	private FileSystemItem parent;
	private FileSystemItem parentContainer;
	private Set<FileSystemItem> children;
	private Set<FileSystemItem> allChildren;
	private String instanceId;
	
	static {
		instanceIdPrefix = FileSystemItem.class.getName();
	}
	
	public static FileSystemItem of(File file) {
		return ofPath(file.getAbsolutePath());
	}

	public static FileSystemItem of(URL realAbsolutePath) {
		return ofPath(Paths.convertURLPathToAbsolutePath(realAbsolutePath.getPath()));
	}

	public static FileSystemItem ofPath(String realAbsolutePath) {
		return ofPath(realAbsolutePath, null);
	}

	static FileSystemItem ofPath(String realAbsolutePath, String conventionedAbsolutePath) {
		final String realAbsolutePathCleaned = Paths.normalizeAndClean(realAbsolutePath);
		FileSystemItem fileSystemItem = Cache.pathForFileSystemItems.getOrUploadIfAbsent(realAbsolutePathCleaned,
				() -> {
					if (Strings.isNotEmpty(realAbsolutePathCleaned)) {
						return new FileSystemItem(realAbsolutePathCleaned, conventionedAbsolutePath);
					}
					return null;
				});
		if (fileSystemItem.absolutePath.getValue() == null && conventionedAbsolutePath != null) {
			fileSystemItem.absolutePath.setValue(conventionedAbsolutePath);
		}
		return fileSystemItem;
	}

	private FileSystemItem(String realAbsolutePath, String conventionedAbsolutePath) {
		realAbsolutePath = Paths.clean(realAbsolutePath);
		this.absolutePath = new AbstractMap.SimpleEntry<>(realAbsolutePath, conventionedAbsolutePath);
		instanceId = instanceIdPrefix + "_" + System.currentTimeMillis() + "_" + realAbsolutePath;
	}

	private String computeConventionedAbsolutePath() {
		String conventionedAbsolutePath = absolutePath.getValue();
		FileSystemItem parentContainer = this.parentContainer;
		String absolutePath = this.absolutePath.getKey();
		if ((conventionedAbsolutePath == null) || parentContainer == null) {
			conventionedAbsolutePath = Synchronizer.execute(absolutePath, () -> {
				FileSystemItem parentContainerTemp = this.parentContainer;
				String conventionedAbsolutePathTemp = this.absolutePath.getValue();
				if (conventionedAbsolutePathTemp == null || parentContainerTemp == null) {
					if (parentContainerTemp != null && parentContainerTemp.isArchive()) {
						ByteBuffer parentContainerContent = parentContainerTemp.toByteBuffer();
						String relativePath = absolutePath.replace(parentContainer.getAbsolutePath() + "/", "");
						conventionedAbsolutePathTemp = parentContainer.computeConventionedAbsolutePath()
								+ retrieveConventionedRelativePath(parentContainerContent,
										parentContainer.getAbsolutePath(), relativePath);
						this.absolutePath.setValue(conventionedAbsolutePathTemp);
					} else {
						conventionedAbsolutePathTemp = retrieveConventionedAbsolutePath(absolutePath, "");
						this.absolutePath.setValue(conventionedAbsolutePathTemp);
					}
				}
				return conventionedAbsolutePathTemp;
			});
		}
		if (conventionedAbsolutePath == null) {
			destroy();
		}
		return conventionedAbsolutePath;
	}

	public FileSystemItem copyAllChildrenTo(String folder) {
		return copyAllChildrenTo(folder, null);
	}

	public FileSystemItem copyAllChildrenTo(String folder, FileSystemItem.Criteria filter) {
		FileSystemItem.Criteria finalFilter = FileSystemItem.Criteria
				.forAllFileThat(fileSystemItem -> !fileSystemItem.isArchive());
		finalFilter = filter != null ? finalFilter.and(filter) : finalFilter;
		Collection<FileSystemItem> allChildren = findInAllChildren(finalFilter);
		for (FileSystemItem child : allChildren) {
			FileSystemItem destFile = FileSystemItem
					.ofPath(folder + child.getAbsolutePath().replaceFirst(this.getAbsolutePath(), ""));
			if (child.isFolder()) {
				File file = new File(destFile.getAbsolutePath());
				if (!file.exists()) {
					file.mkdirs();
				}
			} else {
				Streams.store(destFile.getParent().getAbsolutePath() + "/" + child.getName(), child.toByteBuffer());
			}
		}
		return FileSystemItem.ofPath(folder).refresh();
	}

	public FileSystemItem copyTo(String folder) {
		return copyTo(folder, null);
	}

	public FileSystemItem copyTo(String folder, FileSystemItem.Criteria filter) {
		FileSystemItem destination = null;
		if (isFile()) {
			if (filter == null || filter.testWithFalseResultForNullEntityOrTrueResultForNullPredicate(
					new FileSystemItem[] { this, this })) {
				destination = Streams.store(folder + "/" + getName(), toByteBuffer());
			}
		} else {
			File file = new File(folder + "/" + getName());
			file.mkdirs();
			for (FileSystemItem fileSystemItem : (filter == null ? getChildren() : findInChildren(filter))) {
				fileSystemItem.copyTo(file.getAbsolutePath(), filter);
			}
			destination = FileSystemItem.ofPath(file.getAbsolutePath());
		}
		return destination;
	}
	
	@Override
	public boolean equals(Object obj) {
		return this == obj || obj instanceof FileSystemItem
				&& ((FileSystemItem) obj).getAbsolutePath().equals(this.getAbsolutePath());
	}

	public boolean exists() {
		String conventionedAbsolutePath = absolutePath.getValue();
		if (conventionedAbsolutePath == null) {
			conventionedAbsolutePath = computeConventionedAbsolutePath();
		}
		return conventionedAbsolutePath != null;
	}

	private void extractAndAddAllFoldersName(Set<String> folderRelPaths, String path) {
		int lastIndexOfSlash = path.lastIndexOf("/");
		if (lastIndexOfSlash != 1 && lastIndexOfSlash > 0) {
			String folder = path.substring(0, lastIndexOfSlash);
			if (!folderRelPaths.contains(folder)) {
				folderRelPaths.add(folder);
				extractAndAddAllFoldersName(folderRelPaths, folder);
			}
		}
	}

	public Collection<FileSystemItem> findInAllChildren(FileSystemItem.Criteria filter) {
		return findIn(this::getAllChildren0, filter, HashSet::new);
	}

	public Collection<FileSystemItem> findInAllChildren(FileSystemItem.Criteria filter,
			Supplier<Collection<FileSystemItem>> setSupplier) {
		return findIn(this::getAllChildren0, filter, setSupplier);
	}

	public Collection<FileSystemItem> findInChildren(FileSystemItem.Criteria filter) {
		return findIn(this::getChildren0, filter, HashSet::new);
	}

	public Collection<FileSystemItem> findInChildren(
		FileSystemItem.Criteria filter,
		Supplier<Collection<FileSystemItem>> setSupplier
	) {
		return findIn(this::getChildren0, filter, setSupplier);
	}
	
	private Collection<FileSystemItem> findIn(
		Supplier<Set<FileSystemItem>> childrenSupplier,
		FileSystemItem.Criteria filter,
		Supplier<Collection<FileSystemItem>> outputCollectionSupplier
	) {	
		Set<FileSystemItem> children;
		try {
			children = childrenSupplier.get();
		} catch (Throwable exc) {
			ManagedLoggersRepository.logWarn(this.getClass()::getName, "Exception occurred while retrieving children of {}: ", getAbsolutePath(), Strings.formatMessage(exc));
			ManagedLoggersRepository.logInfo(this.getClass()::getName, "Trying to reset {} and reload children/all children", getAbsolutePath());
			reset();
			children = childrenSupplier.get();
		}
		if (children == null) {
			return null;
		}
		Predicate<FileSystemItem[]> nativePredicate = filter.getOriginalPredicateOrTruePredicateIfPredicateIsNull();
		Collection<FileSystemItem> iteratedFISWithErrors = ConcurrentHashMap.newKeySet();
		BiFunction<Throwable, FileSystemItem[], Boolean> customExceptionHandler = filter.exceptionHandler;
		Predicate<FileSystemItem> filterPredicate = child -> {
			try {
				return nativePredicate.test(new FileSystemItem[] { child, this });
			} catch (ArrayIndexOutOfBoundsException | NullPointerException exc) {
				iteratedFISWithErrors.add(child);
				return false;
			} catch (Throwable exc) {
				if (customExceptionHandler == null) {
					throw exc;
				}
				iteratedFISWithErrors.add(child);
				return false;
			}
		};
		final Collection<FileSystemItem> result = IterableObjectHelper.iterateParallelIf(
			children,
			(child, collector) -> {
				if (filterPredicate.test(child)) {
					collector.accept(child);
				}
			},
			outputCollectionSupplier.get(),
			items -> items.size() > 1
		);		
		if (!iteratedFISWithErrors.isEmpty()) {
			Predicate<FileSystemItem[]> nativePredicateWithExceptionManaging = filter.getPredicateOrTruePredicateIfPredicateIsNull();
			for (FileSystemItem child : iteratedFISWithErrors) {
				FileSystemItem[] childAndThis = new FileSystemItem[] { child, this };
				if (nativePredicateWithExceptionManaging.test(childAndThis)) {
					result.add(child);
				}
			}
			iteratedFISWithErrors.clear();
		}
		return result;
	}

	public FileSystemItem findFirstInAllChildren() {
		return findFirstInAllChildren(FileSystemItem.Criteria.create());
	}

	public FileSystemItem findFirstInAllChildren(FileSystemItem.Criteria filter) {
		return findFirstInChildren(this::getAllChildren0, filter);
	}

	public FileSystemItem findFirstInChildren() {
		return findFirstInAllChildren(FileSystemItem.Criteria.create());
	}

	public FileSystemItem findFirstInChildren(FileSystemItem.Criteria filter) {
		return findFirstInChildren(this::getChildren0, filter);
	}

	private FileSystemItem findFirstInChildren(Supplier<Set<FileSystemItem>> childrenSupplier,
			FileSystemItem.Criteria filter) {
		Predicate<FileSystemItem[]> filterPredicate = filter.getPredicateOrTruePredicateIfPredicateIsNull();
		FileSystemItem[] childAndThis = new FileSystemItem[] { null, this };
		for (FileSystemItem fileSystemItem : childrenSupplier.get()) {
			childAndThis[0] = fileSystemItem;
			if (filterPredicate.test(childAndThis)) {
				return fileSystemItem;
			}
		}
		return null;
	}

	public String getAbsolutePath() {
		return absolutePath.getKey();
	}

	public Set<FileSystemItem> getAllChildren() {
		return Optional.ofNullable(getAllChildren0()).map(children ->  Collections.unmodifiableSet(children)).orElseGet(() -> null);
	}

	private Set<FileSystemItem> getAllChildren0() {
		Set<FileSystemItem> allChildren = this.allChildren;
		if (allChildren == null) {
			allChildren = Synchronizer.execute(instanceId, () -> {
				Set<FileSystemItem> allChildrenTemp = this.allChildren;
				if (allChildrenTemp == null) {
					allChildrenTemp = this.allChildren = loadAllChildren();
				}
				return allChildrenTemp;
			});
		}
		return allChildren;
	}

	public Set<FileSystemItem> getChildren() {
		return Optional.ofNullable(getChildren0()).map(children -> Collections.unmodifiableSet(children)).orElseGet(() -> null);
	}

	private Set<FileSystemItem> getChildren0() {
		Set<FileSystemItem> children = this.children;
		if (children == null) {
			children = Synchronizer.execute(instanceId, () -> {
				Set<FileSystemItem> childrenTemp = this.children;
				if (childrenTemp == null) {
					childrenTemp = this.children = loadChildren();
				}
				return childrenTemp;
			});
		}
		return children;
	}

	public String getExtension() {
		String extension = null;
		String name = getName();
		if (!isFolder() && name.contains(".")) {
			extension = name.substring(name.lastIndexOf(".") + 1);
		}
		return extension;
	}

	public String getName() {
		String absolutePath = getAbsolutePath();
		if (!isRoot()) {
			return absolutePath.substring(absolutePath.lastIndexOf("/") + 1);
		} else {
			return absolutePath.substring(0, absolutePath.lastIndexOf("/"));
		}
	}

	public FileSystemItem getParent() {
		FileSystemItem parent = this.parent;
		if (parent != null) {
			return parent;
		} else if (isRoot()) {
			return null;
		} else {
			String conventionedPath = absolutePath.getValue();
			if (conventionedPath != null) {
				if (conventionedPath.endsWith("/")) {
					int offset = -1;
					if (conventionedPath.endsWith(IterableZipContainer.PATH_SUFFIX)) {
						offset = -IterableZipContainer.PATH_SUFFIX.length();
					}
					conventionedPath = conventionedPath.substring(0, conventionedPath.length() + offset);
				}
				conventionedPath = conventionedPath.substring(0, conventionedPath.lastIndexOf("/")) + "/";
				return FileSystemItem.ofPath(absolutePath.getKey().substring(0, absolutePath.getKey().lastIndexOf("/")),
						conventionedPath);
			} else {
				String absolutePath = getAbsolutePath();
				String parentAbsolutePath = absolutePath.substring(0, absolutePath.lastIndexOf("/"));
				if (isRoot(parentAbsolutePath)) {
					parentAbsolutePath = parentAbsolutePath.length() > 0 ? parentAbsolutePath : "/";
				}
				return this.parent = FileSystemItem.ofPath(parentAbsolutePath);
			}
		}
	}

	public FileSystemItem getParentContainer() {
		FileSystemItem parentContainer = this.parentContainer;
		if (parentContainer != null) {
			return parentContainer;
		} else {
			computeConventionedAbsolutePath();
			parentContainer = this.parentContainer;
			if (parentContainer == null) {
				parentContainer = getParent();
			}
		}
		return parentContainer;
	}

	public URL getURL() {
		try {
			return new URL(toURL());
		} catch (MalformedURLException exc) {
			return Throwables.throwException(exc);
		}
	}

	public boolean isArchive() {
		String conventionedAbsolutePath = computeConventionedAbsolutePath();
		return conventionedAbsolutePath.endsWith(IterableZipContainer.PATH_SUFFIX);
	}

	public boolean isChildOf(FileSystemItem fileSystemItem) {
		String conventionedAbsolutePath_01 = this.computeConventionedAbsolutePath();
		String conventionedAbsolutePath_02 = fileSystemItem.computeConventionedAbsolutePath();
		if (fileSystemItem.isContainer() && this.isContainer()) {
			return conventionedAbsolutePath_01.startsWith(conventionedAbsolutePath_02)
					&& !conventionedAbsolutePath_02.equals(conventionedAbsolutePath_01);
		} else if (fileSystemItem.isContainer() && !this.isContainer()) {
			return conventionedAbsolutePath_01.startsWith(conventionedAbsolutePath_02);
		}
		return false;
	}

	public boolean isCompressed() {
		String conventionedAbsolutePath = computeConventionedAbsolutePath();
		return (conventionedAbsolutePath.contains(IterableZipContainer.PATH_SUFFIX)
				&& !conventionedAbsolutePath.endsWith(IterableZipContainer.PATH_SUFFIX))
				|| (conventionedAbsolutePath.contains(IterableZipContainer.PATH_SUFFIX)
						&& conventionedAbsolutePath.endsWith(IterableZipContainer.PATH_SUFFIX)
						&& conventionedAbsolutePath
								.indexOf(IterableZipContainer.PATH_SUFFIX) != conventionedAbsolutePath
										.lastIndexOf(IterableZipContainer.PATH_SUFFIX));
	}

	public boolean isContainer() {
		String conventionedAbsolutePath = computeConventionedAbsolutePath();
		return conventionedAbsolutePath.endsWith("/");
	}

	public boolean isFile() {
		return !isFolder();
	}

	public boolean isFolder() {
		String conventionedAbsolutePath = computeConventionedAbsolutePath();
		return conventionedAbsolutePath.endsWith("/")
				&& !conventionedAbsolutePath.endsWith(IterableZipContainer.PATH_SUFFIX);
	}

	public boolean isParentOf(FileSystemItem fileSystemItem) {
		String conventionedAbsolutePath_01 = this.computeConventionedAbsolutePath();
		String conventionedAbsolutePath_02 = fileSystemItem.computeConventionedAbsolutePath();
		if (fileSystemItem.isContainer() && this.isContainer()) {
			return conventionedAbsolutePath_02.startsWith(conventionedAbsolutePath_01)
					&& !conventionedAbsolutePath_02.equals(conventionedAbsolutePath_01);
		} else if (!fileSystemItem.isContainer() && this.isContainer()) {
			return conventionedAbsolutePath_02.startsWith(conventionedAbsolutePath_01);
		}
		return false;
	}

	public boolean isRoot() {
		return isRoot(getAbsolutePath());
	}

	private boolean isRoot(String absolutePathStr) {
		return absolutePathStr.chars().filter(ch -> ch == '/').count() == 0 || absolutePathStr.equals("/");
	}

	Set<FileSystemItem> loadAllChildren() {
		if (isContainer()) {
			if (isCompressed() || isArchive()) {
				Predicate<IterableZipContainer.Entry> zipEntryPredicate = null;
				FileSystemItem parentContainerTemp = this;
				if (isArchive()) {
					zipEntryPredicate = zEntry -> !zEntry.getName().equals("/");
				} else if (isFolder()) {
					parentContainerTemp = parentContainer;
					zipEntryPredicate = zEntry -> zEntry.getAbsolutePath().startsWith(getAbsolutePath() + "/");
				}
				final FileSystemItem parentContainer = parentContainerTemp;
				boolean isJModArchive = Streams.isJModArchive(parentContainer.toByteBuffer());
				try (IterableZipContainer zipInputStream = IterableZipContainer
						.create(parentContainer.getAbsolutePath(), parentContainer.toByteBuffer())) {
					Set<String> folderRelPaths = new HashSet<>();
					Set<FileSystemItem> allChildren = ConcurrentHashMap.newKeySet();
					zipInputStream.findAllAndConvert(() -> allChildren, zipEntryPredicate, zEntry -> {
						FileSystemItem fileSystemItem = FileSystemItem
								.ofPath(parentContainer.getAbsolutePath() + "/" + zEntry.getName());
						fileSystemItem.absolutePath.setValue(
							parentContainer.computeConventionedAbsolutePath() + retrieveConventionedRelativePath(
								fileSystemItem, zipInputStream, zEntry, zEntry.getCleanedName()
							)
						);
						if (fileSystemItem.isArchive()) {
							Optional.ofNullable(fileSystemItem.getAllChildren())
									.ifPresent(fileSystemItemChildrens -> allChildren.addAll(fileSystemItemChildrens));
						}
						if (isJModArchive) {
							extractAndAddAllFoldersName(folderRelPaths, zEntry.getName());
						}
						return fileSystemItem;
					}, zEntry -> true);
					for (String folderRelPath : folderRelPaths) {
						FileSystemItem fileSystemItem = FileSystemItem
								.ofPath(zipInputStream.getAbsolutePath() + "/" + folderRelPath);
						if (fileSystemItem.parentContainer == null) {
							fileSystemItem.parentContainer = FileSystemItem.ofPath(zipInputStream.getAbsolutePath());
						}
						if (this.isParentOf(fileSystemItem)) {
							allChildren.add(fileSystemItem);
						}
					}
					return allChildren;
				}
			} else if (isFolder()) {
				Set<FileSystemItem> children = getChildren();
				if (children != null) {
					Set<FileSystemItem> allChildren = ConcurrentHashMap.newKeySet();
					allChildren.addAll(children);
					children.forEach(child -> {
						Optional.ofNullable(child.getAllChildren())
								.map(allChildrenOfChild -> allChildren.addAll(allChildrenOfChild));
					});
					return allChildren;
				}
			}
		}
		return null;
	}

	Set<FileSystemItem> loadChildren() {
		String conventionedAbsolutePath = computeConventionedAbsolutePath();
		if (isContainer()) {
			if (isCompressed()) {
				if (isArchive()) {
					Supplier<IterableZipContainer> zipInputStreamSupplier = () -> IterableZipContainer
							.create(getAbsolutePath(), toByteBuffer());
					return retrieveChildren(zipInputStreamSupplier, "");
				} else if (isFolder()) {
					Supplier<IterableZipContainer> zipInputStreamSupplier = () -> IterableZipContainer
							.create(parentContainer.getAbsolutePath(), parentContainer.toByteBuffer());
					return retrieveChildren(zipInputStreamSupplier,
							conventionedAbsolutePath.substring(
								conventionedAbsolutePath.lastIndexOf(IterableZipContainer.PATH_SUFFIX)
									+ IterableZipContainer.PATH_SUFFIX.length()
							)
					);
				}
			} else if (isArchive()) {
				String zipFilePath = conventionedAbsolutePath.substring(0,
						conventionedAbsolutePath.indexOf(IterableZipContainer.PATH_SUFFIX));
				File file = new File(zipFilePath);
				if (file.exists()) {
					try (FileInputStream fIS = FileInputStream.create(file)) {
						return retrieveChildren(() -> IterableZipContainer.create(fIS), conventionedAbsolutePath
							.replaceFirst(zipFilePath + IterableZipContainer.PATH_SUFFIX, ""));
					}
				}
			} else {
				File file = new File(conventionedAbsolutePath);
				if (file.exists()) {
					return Optional.ofNullable(file.listFiles())
							.map((childrenFiles) -> Arrays.stream(childrenFiles)
									.map(fl -> FileSystemItem.ofPath(fl.getAbsolutePath()))
									.collect(Collectors.toCollection(ConcurrentHashMap::newKeySet)))
							.orElseGet(ConcurrentHashMap::newKeySet);
				}
			}
		}
		return null;
	}

	public FileSystemItem refresh() {
		return refresh(true);
	}

	public FileSystemItem refresh(boolean removeLinkedResourcesFromCache) {
		reset(removeLinkedResourcesFromCache);
		computeConventionedAbsolutePath();
		return this;
	}

	private void removeFromCache(FileSystemItem fileSystemItem, boolean removeFromCache) {
		Cache.pathForContents.remove(fileSystemItem.getAbsolutePath(), true);
		IterableZipContainer zipContainer = Cache.pathForIterableZipContainers.get(fileSystemItem.getAbsolutePath());
		if (zipContainer != null) {
			zipContainer.destroy();
		}
		if (removeFromCache) {
			Cache.pathForFileSystemItems.remove(fileSystemItem.getAbsolutePath(), true);
		}
	}

	public FileSystemItem reset() {
		return reset(true);
	}

	public FileSystemItem reset(boolean removeLinkedResourcesFromCache) {
		return clear(removeLinkedResourcesFromCache, false);
	}

	public void destroy() {
		clear(true, true);
	}

	FileSystemItem clear(boolean removeLinkedResourcesFromCache, boolean removeFromCache) {
		return Synchronizer.execute(instanceId, () -> {
			Collection<FileSystemItem> allChildren = this.allChildren;
			Collection<FileSystemItem> children = this.children;
			this.allChildren = null;
			this.children = null;
			if (allChildren != null) {
				for (FileSystemItem child : allChildren) {
					Synchronizer.execute(child.instanceId, () -> {
						child.absolutePath.setValue(null);
						child.parentContainer = null;
						child.parent = null;
						child.allChildren = null;
						child.children = null;
						if (removeLinkedResourcesFromCache) {
							removeFromCache(child, removeFromCache);
						}
					});
				}
			} else if (children != null) {
				for (FileSystemItem child : children) {
					child.clear(removeLinkedResourcesFromCache, removeFromCache);
				}
			}
			absolutePath.setValue(null);
			parentContainer = null;
			parent = null;
			if (removeLinkedResourcesFromCache) {
				removeFromCache(this, removeFromCache);
			}
			return removeFromCache ? null : this;
		});
	}

	private Set<FileSystemItem> retrieveChildren(Supplier<IterableZipContainer> zipInputStreamSupplier,
			String itemToSearch) {
		try (IterableZipContainer zipInputStream = zipInputStreamSupplier.get()) {
			final String itemToSearchRegEx = itemToSearch.replace("/", "\\/") + "(.*?)\\/";
			Pattern itemToSearchRegExPattern = Pattern.compile(itemToSearchRegEx);
			boolean isJModArchive = Streams.isJModArchive(zipInputStream.toByteBuffer());
			Set<String> folderRelPaths = new HashSet<>();
			Set<FileSystemItem> children = ConcurrentHashMap.newKeySet();
			zipInputStream.findAllAndConvert(() -> children, (zEntry) -> {
				String nameToTest = zEntry.getName();
				nameToTest += nameToTest.endsWith("/") ? "" : "/";
				if (isJModArchive && nameToTest.matches(itemToSearchRegEx)) {
					String childRelPath = itemToSearch
							+ Strings.extractAllGroups(itemToSearchRegExPattern, nameToTest).get(1).get(0) + "/";
					if (!folderRelPaths.contains(childRelPath)) {
						folderRelPaths.add(childRelPath);
						FileSystemItem fileSystemItem = FileSystemItem
								.ofPath(zEntry.getParentContainer().getAbsolutePath() + "/" + childRelPath);
						if (fileSystemItem.parentContainer == null) {
							fileSystemItem.parentContainer = FileSystemItem
									.ofPath(zEntry.getParentContainer().getAbsolutePath());
						}
						if (this.isParentOf(fileSystemItem)) {
							children.add(fileSystemItem);
						}
					}
				}
				return nameToTest.matches(itemToSearchRegEx)
						&& nameToTest.replaceFirst(itemToSearchRegEx, "").length() == 0;
			}, (zEntry) -> {
				FileSystemItem fileSystemItem = FileSystemItem.ofPath(zEntry.getAbsolutePath());
				if (fileSystemItem.parentContainer == null) {
					fileSystemItem.parentContainer = FileSystemItem
							.ofPath(zEntry.getParentContainer().getAbsolutePath());
				}
				return fileSystemItem;
			}, zEntry -> false);
			return children;
		}
	}

	private String retrieveConventionedAbsolutePath(String realAbsolutePath, String relativePath) {
		File file = new File(realAbsolutePath);
		if (file.exists()) {
			if (relativePath.isEmpty()) {
				if (file.isDirectory()) {
					return realAbsolutePath + (realAbsolutePath.endsWith("/") ? "" : "/");
				} else {
					try {
						if (Streams.isArchive(file)) {
							return realAbsolutePath + IterableZipContainer.PATH_SUFFIX;
						} else {
							return realAbsolutePath;
						}
					} catch (IOException exc) {
						ManagedLoggersRepository.logWarn(this.getClass()::getName, "Exception occurred while calling isArchive on file {}: {}", file.getAbsolutePath(),
								exc.getMessage());
						return realAbsolutePath;
					}
				}
			} else {
				try (FileInputStream fileInputStream = FileInputStream.create(file)) {
					return fileInputStream.getAbsolutePath() + IterableZipContainer.PATH_SUFFIX
							+ retrieveConventionedRelativePath(fileInputStream.toByteBuffer(),
									fileInputStream.getAbsolutePath(), relativePath);
				} catch (Throwable exc) {
					return null;
				}
			}
		} else if (realAbsolutePath.chars().filter(ch -> ch == '/').count() > 1) {
			String pathToTest = realAbsolutePath.substring(0, realAbsolutePath.lastIndexOf("/"));
			relativePath = realAbsolutePath.replace(pathToTest + "/", "") + (relativePath.isEmpty() ? "" : "/")
					+ relativePath;
			return retrieveConventionedAbsolutePath(pathToTest, relativePath);
		} else {
			return null;
		}
	}

	private synchronized String retrieveConventionedRelativePath(ByteBuffer zipInputStreamAsBytes,
			String zipInputStreamName, String relativePath1) {
		IterableZipContainer zIS = IterableZipContainer.create(zipInputStreamName, zipInputStreamAsBytes);
		try {
			if (zIS == null) {
				return Throwables.throwException(
					new FileSystemItemNotFoundException("Absolute path \"" + absolutePath.getKey() + "\" not exists")
				);
			}
			Predicate<IterableZipContainer.Entry> zipEntryPredicate = zEntry -> {
				return zEntry.getName().equals(relativePath1) || zEntry.getName().equals(relativePath1 + "/");
			};
			String temp = relativePath1;
			while (temp != null) {
				int lastIndexOfSlash = temp.lastIndexOf("/");
				final String temp2 = lastIndexOfSlash != -1 ? temp.substring(0, lastIndexOfSlash) : temp;
				zipEntryPredicate = zipEntryPredicate
						.or(zEntry -> zEntry.getName().equals(temp2) || zEntry.getName().equals(temp2 + "/"));
				if (lastIndexOfSlash == -1) {
					temp = null;
				} else {
					temp = temp2;
				}
			}
			Set<IterableZipContainer.Entry> zipEntries = zIS.findAll(zipEntryPredicate, zEntry -> false);
			if (!zipEntries.isEmpty()) {
				IterableZipContainer.Entry zipEntry = Collections.max(
					zipEntries,
					Comparator.comparing(zipEntryW -> 
						zipEntryW.getName().split("/").length
					)
				);
				return retrieveConventionedRelativePath(this, zIS, zipEntry, relativePath1);
			} else if (Streams.isJModArchive(zipInputStreamAsBytes)) {
				try (IterableZipContainer zIS2 = IterableZipContainer.create(zipInputStreamName,
						zipInputStreamAsBytes)) {
					if (zIS2.findFirst(zipEntry -> zipEntry.getName().startsWith(relativePath1 + "/"),
							zipEntry -> false) != null) {
						// in case of JMod files folder
						return retrieveConventionedRelativePath(this, zIS2, null, relativePath1);
					}
					;
					return Throwables.throwException(new FileSystemItemNotFoundException(
							"Absolute path \"" + absolutePath.getKey() + "\" not exists"));
				}
			} else {
				return Throwables.throwException(
					new FileSystemItemNotFoundException("Absolute path \"" + absolutePath.getKey() + "\" not exists")
				);
			}
		} finally {
			if (zIS != null) {
				zIS.close();
			}
		}
	}

	synchronized String retrieveConventionedRelativePath(FileSystemItem fileSystemItem, IterableZipContainer iZC,
			IterableZipContainer.Entry zipEntry, String relativePath1) {
		if (zipEntry != null) {
			String zipEntryCleanedName = zipEntry.getCleanedName();
			String relativePath2 = zipEntryCleanedName;
			if (relativePath2.endsWith("/")) {
				relativePath2 = relativePath2.substring(0, relativePath2.length() - 1);
			}
			relativePath2 = relativePath1.substring(relativePath2.length());
			if (relativePath2.startsWith("/")) {
				relativePath2 = relativePath2.replaceFirst("\\/", "");
			}
			if (relativePath2.isEmpty()) {
				if (fileSystemItem.parentContainer == null) {
					fileSystemItem.parentContainer = FileSystemItem
							.ofPath(zipEntry.getParentContainer().getAbsolutePath());
				}
				return zipEntryCleanedName
					+ (zipEntry.isArchive() ? IterableZipContainer.PATH_SUFFIX
								: "");
			} else {
				return zipEntryCleanedName + IterableZipContainer.PATH_SUFFIX + retrieveConventionedRelativePath(
					zipEntry.toByteBuffer(), zipEntry.getAbsolutePath(), relativePath2);
			}
			// in case of JMod files folder
		} else {
			if (fileSystemItem.parentContainer == null) {
				fileSystemItem.parentContainer = FileSystemItem.ofPath(iZC.getAbsolutePath());
			}
			return iZC.getAbsolutePath() + IterableZipContainer.PATH_SUFFIX + relativePath1 + "/";
		}
	}
	
	public ByteBuffer toByteBuffer() {
		return Executor.get(this::toByteBuffer0, 2);
	}
	
	private ByteBuffer toByteBuffer0() {
		String absolutePath = getAbsolutePath();
		ByteBuffer resource = Cache.pathForContents.get(absolutePath); 
		if (resource != null) {
			return resource;
		}
		if (exists() && !isFolder()) {
			if (isCompressed()) {
				FileSystemItem parentContainer = getParentContainer();
				FileSystemItem superParentContainer = parentContainer;
				while (superParentContainer.getParentContainer() != null && superParentContainer.getParentContainer().isArchive()) {
					superParentContainer = superParentContainer.getParentContainer();
				}
				Collection<FileSystemItem> superParentAllChildren = superParentContainer.getAllChildren();
				FileSystemItem randomFIS = IterableObjectHelper.getRandom(superParentAllChildren);
				while (randomFIS.getAbsolutePath() == this.getAbsolutePath() && superParentAllChildren.size() > 1) {
					randomFIS = IterableObjectHelper.getRandom(superParentAllChildren);
				}
				if ((Cache.pathForContents.get(randomFIS.getAbsolutePath())) == null) {
					FileSystemItem finalRandomFIS = randomFIS;
					FileSystemItem superParentContainerFinal = superParentContainer;
					Synchronizer.execute(superParentContainer.instanceId, () -> {
						if ((Cache.pathForContents.get(finalRandomFIS.getAbsolutePath()) == null)) {
							superParentContainerFinal.refresh().getAllChildren();
						}
					});
				}
				if (Cache.pathForContents.get(absolutePath) == null) {
					reloadContent(false);
				}
				return Cache.pathForContents.get(absolutePath);		
			} else {
				return Cache.pathForContents.getOrUploadIfAbsent(
					absolutePath, () -> {
						try (FileInputStream fIS = FileInputStream.create(getAbsolutePath())) {
							return fIS.toByteBuffer();
						}						
					}
				);				
			}
		}
		return null;
	}
	
	public <S extends Serializable> S toObject() {
		try (InputStream inputStream = toInputStream()) {
			return Objects.deserialize(inputStream);
		} catch (Throwable exc) {
			return Throwables.throwException(exc);
		}
	}	

	public FileSystemItem reloadContent() {
		return reloadContent(false);
	}
	
	public FileSystemItem reloadContent(boolean recomputeConventionedAbsolutePath) {
		String absolutePath = getAbsolutePath();
		Synchronizer.execute(instanceId, () -> {
			Cache.pathForContents.remove(absolutePath, true);
			if (recomputeConventionedAbsolutePath) {
				this.absolutePath.setValue(null);
			}
		});
		if (exists() && !isFolder()) {
			if (isCompressed()) {
				try (IterableZipContainer iterableZipContainer = IterableZipContainer.create(
					getParentContainer().reloadContent(recomputeConventionedAbsolutePath).getAbsolutePath())
				) {
					iterableZipContainer.findFirst(
						iteratedZipEntry -> 
							iteratedZipEntry.getAbsolutePath().equals(absolutePath), 
						iteratedZipEntry -> 
							iteratedZipEntry.getAbsolutePath().equals(absolutePath)
					);
				}		
			} else {
				Cache.pathForContents.getOrUploadIfAbsent(
					absolutePath, () -> {
						try (FileInputStream fIS = FileInputStream.create(getAbsolutePath())) {
							return fIS.toByteBuffer();
						}						
					}
				);
			}
		}
		return this;
	}

	public InputStream toInputStream() {
		return new ByteBufferInputStream(toByteBuffer());
	}

	@Override
	public String toString() {
		return absolutePath.getKey();
	}

	private String toURL() {
		String url = computeConventionedAbsolutePath();
		String prefix = "file:";
		if (!url.startsWith("/")) {
			prefix = prefix + "/";
		}
		if (isCompressed()) {
			prefix = "jar" +
			// getParentContainer().getExtension() +
					":" + prefix;
		}
		url = url.endsWith(IterableZipContainer.PATH_SUFFIX)
				? url.substring(0, url.lastIndexOf(IterableZipContainer.PATH_SUFFIX))
				: url;
		String uRLToRet = url.replace(IterableZipContainer.PATH_SUFFIX, isCompressed() ? "!/" : "/");
		url = Executor.get(() -> URLEncoder.encode(uRLToRet, StandardCharsets.UTF_8.name())).replace("%3A", ":")
				.replace("%21", "!").replace("%2F", "/");
		url = prefix + url;
		return isFolder() ? url.endsWith("/") ? url : url + "/"
				: url.endsWith("/") ? url.substring(0, url.length() - 1) : url;

	}

	public static enum CheckingOption {
		FOR_NAME("checkFileName"), FOR_SIGNATURE("checkFileSignature"),
		FOR_NAME_AND_SIGNATURE(FOR_NAME.label + "&" + FOR_SIGNATURE.label),
		FOR_NAME_OR_SIGNATURE(FOR_NAME.label + "|" + FOR_SIGNATURE.label),
		FOR_SIGNATURE_OR_NAME(FOR_SIGNATURE.label + "|" + FOR_NAME.label),
		FOR_SIGNATURE_AND_NAME(FOR_SIGNATURE.label + "&" + FOR_NAME.label);

		public abstract static class ForFileOf {

			static class ArchiveType extends ForFileOf {

				ArchiveType() {
					super(file -> {
						String name = file.getName();
						return name.endsWith(".zip") || name.endsWith(".jar") || name.endsWith(".war")
								|| name.endsWith(".ear") || name.endsWith(".jmod");
					}, file -> Executor.get(() -> !file.isFolder() && Streams.isArchive(file.toByteBuffer())));
				}
			}

			static class ClassType extends ForFileOf {

				ClassType() {
					super(file -> {
						String name = file.getName();
						return name.endsWith(".class") && !name.endsWith("module-info.class")
								&& !name.endsWith("package-info.class");
					}, file -> Executor.get(() -> !file.isFolder() && Streams.isClass(file.toByteBuffer())));

				}

			}

			Predicate<FileSystemItem> fileNameChecker;

			Predicate<FileSystemItem> fileSignatureChecker;

			ForFileOf(Predicate<FileSystemItem> fileNameChecker, Predicate<FileSystemItem> fileSignatureChecker) {
				this.fileNameChecker = fileNameChecker;
				this.fileSignatureChecker = fileSignatureChecker;
			}

			FileSystemItem.Criteria toCriteria(CheckingOption checkFileOption) {
				if (checkFileOption.equals(CheckingOption.FOR_NAME)) {
					return FileSystemItem.Criteria.forAllFileThat(fileNameChecker);
				} else if (checkFileOption.equals(CheckingOption.FOR_SIGNATURE)) {
					return FileSystemItem.Criteria.forAllFileThat(fileSignatureChecker);
				} else if (checkFileOption.equals(CheckingOption.FOR_NAME_OR_SIGNATURE)) {
					return FileSystemItem.Criteria.forAllFileThat(fileNameChecker.or(fileSignatureChecker));
				} else if (checkFileOption.equals(CheckingOption.FOR_SIGNATURE_OR_NAME)) {
					return FileSystemItem.Criteria.forAllFileThat(fileSignatureChecker.or(fileNameChecker));
				} else if (checkFileOption.equals(CheckingOption.FOR_NAME_AND_SIGNATURE)) {
					return FileSystemItem.Criteria.forAllFileThat(fileNameChecker.and(fileSignatureChecker));
				}  else if (checkFileOption.equals(CheckingOption.FOR_SIGNATURE_AND_NAME)) {
					return FileSystemItem.Criteria.forAllFileThat(fileSignatureChecker.and(fileNameChecker));
				}
				return null;
			}

			FileSystemItem.Criteria toCriteria(String checkFileOptionLabel) {
				return toCriteria(CheckingOption.forLabel(checkFileOptionLabel));
			}
		}

		public static CheckingOption forLabel(String label) {
			for (CheckingOption item : CheckingOption.values()) {
				if (item.label.equals(label)) {
					return item;
				}
			}
			return null;
		}

		private String label;

		private CheckingOption(String label) {
			this.label = label;
		}

		public String getLabel() {
			return label;
		}

	}

	public static class Criteria extends org.burningwave.core.Criteria.Simple<FileSystemItem[], Criteria> {
		
		private BiFunction<Throwable, FileSystemItem[], Boolean> exceptionHandler;
		
		public static Criteria create() {
			return new Criteria();
		}

		public final static Criteria forAllFileThat(final BiPredicate<FileSystemItem, FileSystemItem> predicate) {
			return new Criteria()
					.allThoseThatMatch(childAndSuperParent -> predicate.test(childAndSuperParent[0], childAndSuperParent[1]));
		}

		public final static Criteria forAllFileThat(final Predicate<FileSystemItem> predicate) {
			return new Criteria().allThoseThatMatch(childAndSuperParent -> predicate.test(childAndSuperParent[0]));
		}

		public final static Criteria forArchiveTypeFiles(CheckingOption checkingOption) {
			return new CheckingOption.ForFileOf.ArchiveType().toCriteria(checkingOption);
		}

		public final static Criteria forArchiveTypeFiles(String checkingOption) {
			return new CheckingOption.ForFileOf.ArchiveType().toCriteria(checkingOption);
		}

		public final static Criteria forClassTypeFiles(CheckingOption checkingOption) {
			return new CheckingOption.ForFileOf.ClassType().toCriteria(checkingOption);
		}

		public final static Criteria forClassTypeFiles(String checkingOption) {
			return new CheckingOption.ForFileOf.ClassType().toCriteria(checkingOption);
		}

		public final Criteria allFileThat(final Predicate<FileSystemItem> predicate) {
			return this.allThoseThatMatch(childAndSuperParent -> predicate.test(childAndSuperParent[0]));
		}

		public final Criteria allFileThat(final BiPredicate<FileSystemItem, FileSystemItem> predicate) {
			return this.allThoseThatMatch(childAndSuperParent -> predicate.test(childAndSuperParent[0], childAndSuperParent[1]));
		}
		
		public Criteria excludePathsThatMatch(String regex) {
			return allFileThat(file -> {
				return !file.getAbsolutePath().matches(regex);
			});
		}
		
		public Criteria notRecursiveOnPath(String path, boolean isAbsolute) {
			path = Paths.clean(path);
			if (!isAbsolute) {
				path = "/" + path;
			}
			if (!path.endsWith("/")) {
				path += "/";
			}
			String slashedPath = path;
			String regex = isAbsolute ? "" :".*?" + path.replace("/", "\\/") + "[^\\/]*";
			return allFileThat(file -> {
				String absolutePath = file.getAbsolutePath();
				String slashedAbsolutePath = absolutePath + "/"; 
				boolean isContained = 
					isAbsolute?
						slashedAbsolutePath.startsWith(slashedPath) :
						slashedAbsolutePath.contains(slashedPath);
				return !isContained || absolutePath.matches(regex);
			});
		}
		
		public final Criteria setExceptionHandler(BiFunction<Throwable, FileSystemItem[], Boolean> exceptionHandler) {
			this.exceptionHandler = exceptionHandler;
			return this;
		}
		
		public final Criteria setDefaultExceptionHandler() {
			return setExceptionHandler((exception, childAndParent) -> {
				ManagedLoggersRepository.logError(this.getClass()::getName, "Could not scan " + childAndParent[0].getAbsolutePath(), exception);
				return false;
			});
		}		
		
		public boolean hasNoExceptionHandler() {
			return this.exceptionHandler == null;
		}
		
		public BiFunction<Throwable, FileSystemItem[], Boolean> getExceptionHandler() {
			return this.exceptionHandler;
		}
		
		@Override
		public Predicate<FileSystemItem[]> getPredicateOrFalsePredicateIfPredicateIsNull() {
			return nativePredicateToSomeExceptionManagedPredicate(super.getPredicateOrFalsePredicateIfPredicateIsNull());
		}
		
		@Override
		public Predicate<FileSystemItem[]> getPredicateOrTruePredicateIfPredicateIsNull() {
			return nativePredicateToSomeExceptionManagedPredicate(super.getPredicateOrTruePredicateIfPredicateIsNull());
		}
		
		public Predicate<FileSystemItem[]> getOriginalPredicateOrFalsePredicateIfPredicateIsNull() {
			return super.getPredicateOrFalsePredicateIfPredicateIsNull();
		}
		
		public Predicate<FileSystemItem[]> getOriginalPredicateOrTruePredicateIfPredicateIsNull() {
			return super.getPredicateOrTruePredicateIfPredicateIsNull();
		}
		
		
		private Predicate<FileSystemItem[]> nativePredicateToSomeExceptionManagedPredicate(
			Predicate<FileSystemItem[]> filterPredicate
		) {
			Predicate<FileSystemItem[]> finalFilterPredicate = childAndThis -> {
				try {
					try {
						return filterPredicate.test(childAndThis);
					} catch (ArrayIndexOutOfBoundsException | NullPointerException exc) {
						String childAbsolutePath = childAndThis[0].getAbsolutePath();
						ManagedLoggersRepository.logWarn(this.getClass()::getName, "Exception occurred while analyzing {}", childAbsolutePath);
						if (exc instanceof ArrayIndexOutOfBoundsException) {
							ManagedLoggersRepository.logInfo(this.getClass()::getName, "Trying to reload content of {} and test it again", childAbsolutePath);
							childAndThis[0].reloadContent();
						} else if (exc instanceof NullPointerException) {
							ManagedLoggersRepository.logInfo(this.getClass()::getName, "Trying to reload content and conventioned absolute path of {} and test it again", childAbsolutePath);
							childAndThis[0].reloadContent(true);
						}
						return filterPredicate.test(childAndThis);
					} 
				} catch (Throwable exc) {
					if (exceptionHandler != null) {
						return exceptionHandler.apply(exc, childAndThis);
					}
					throw exc;
				}
			};
			return finalFilterPredicate;
		}
		
		@Override
		public Criteria createCopy() {
			Criteria copy = super.createCopy();
			copy.exceptionHandler = this.exceptionHandler;
			return copy;
		}

	}
}