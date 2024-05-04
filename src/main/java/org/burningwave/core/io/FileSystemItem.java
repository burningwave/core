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


import static org.burningwave.core.assembler.StaticComponentContainer.BufferHandler;
import static org.burningwave.core.assembler.StaticComponentContainer.Cache;
import static org.burningwave.core.assembler.StaticComponentContainer.IterableObjectHelper;
import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository;
import static org.burningwave.core.assembler.StaticComponentContainer.Objects;
import static org.burningwave.core.assembler.StaticComponentContainer.Paths;
import static org.burningwave.core.assembler.StaticComponentContainer.Streams;
import static org.burningwave.core.assembler.StaticComponentContainer.Strings;
import static org.burningwave.core.assembler.StaticComponentContainer.Synchronizer;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.burningwave.core.classes.JavaClass;
import org.burningwave.core.function.Executor;
import org.burningwave.core.iterable.IterableObjectHelper.IterationConfig;

@SuppressWarnings("resource")
public class FileSystemItem implements Comparable<FileSystemItem> {

	private final static Function<String, Boolean> isContainer = conventionedAbsolutePath ->
		conventionedAbsolutePath.endsWith("/");

	private final static Function<String, Boolean> isFolder = conventionedAbsolutePath ->
		conventionedAbsolutePath.endsWith("/") && !conventionedAbsolutePath.endsWith(IterableZipContainer.PATH_SUFFIX);

	private final static Function<String, Boolean> isCompressed = conventionedAbsolutePath ->
		(conventionedAbsolutePath.contains(IterableZipContainer.PATH_SUFFIX)
			&& !conventionedAbsolutePath.endsWith(IterableZipContainer.PATH_SUFFIX))
			|| (conventionedAbsolutePath.contains(IterableZipContainer.PATH_SUFFIX)
					&& conventionedAbsolutePath.endsWith(IterableZipContainer.PATH_SUFFIX)
					&& conventionedAbsolutePath
							.indexOf(IterableZipContainer.PATH_SUFFIX) != conventionedAbsolutePath
									.lastIndexOf(IterableZipContainer.PATH_SUFFIX));

	private final static Function<String, Boolean> isArchive = conventionedAbsolutePath ->
		conventionedAbsolutePath.endsWith(IterableZipContainer.PATH_SUFFIX);

	private final static String instanceIdPrefix;
	private final static Supplier<Collection<FileSystemItem>> newCollectionSupplier;

	private Map.Entry<String, String> absolutePath;
	private FileSystemItem parent;
	private FileSystemItem parentContainer;
	private Collection<FileSystemItem> children;
	private Collection<FileSystemItem> allChildren;
	private String instanceId;
	private AtomicReference<JavaClass> javaClassWrapper;

	static {
		instanceIdPrefix = FileSystemItem.class.getName();
		newCollectionSupplier = ArrayList::new;
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
		final String realAbsolutePathCleaned = Paths.toNormalizedCleanedAbsolutePath(realAbsolutePath);
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
						String relativePath = absolutePath.replace(parentContainerTemp.getAbsolutePath() + "/", "");
						conventionedAbsolutePathTemp = parentContainerTemp.computeConventionedAbsolutePath()
								+ retrieveConventionedRelativePath(parentContainerContent,
										parentContainerTemp.getAbsolutePath(), relativePath);
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
		return findIn(this::getAllChildren0, filter, false, ConcurrentHashMap::newKeySet);
	}

	public Collection<FileSystemItem> findInAllChildren(
		FileSystemItem.Criteria filter,
		Supplier<Collection<FileSystemItem>> setSupplier
	) {
		return findIn(this::getAllChildren0, filter, false, setSupplier);
	}

	public Collection<FileSystemItem> findInChildren(FileSystemItem.Criteria filter) {
		return findIn(this::getChildren0, filter, false, ConcurrentHashMap::newKeySet);
	}

	public Collection<FileSystemItem> findInChildren(
		FileSystemItem.Criteria filter,
		Supplier<Collection<FileSystemItem>> setSupplier
	) {
		return findIn(this::getChildren0, filter, false, setSupplier);
	}

	public Collection<FileSystemItem> findRecursiveInChildren(FileSystemItem.Criteria filter) {
		Collection<FileSystemItem> fileSystemItems = ConcurrentHashMap.newKeySet();
		findRecursiveInChildren(filter, () -> fileSystemItems);
		return fileSystemItems;
	}


	private Collection<FileSystemItem> findRecursiveInChildren(
		FileSystemItem.Criteria filter,
		Supplier<Collection<FileSystemItem>> outputCollectionSupplier
	) {
		Collection<FileSystemItem> outputCollection = outputCollectionSupplier.get();
		for (FileSystemItem filteredItem : findIn(this::getChildren0, filter, false, ConcurrentHashMap::newKeySet)) {
			outputCollection.add(filteredItem);
			if (filteredItem.isContainer()) {
				filteredItem.findRecursiveInChildren(filter, outputCollectionSupplier);
			}
		}
		return outputCollection;
	}

	private Collection<FileSystemItem> findIn(
		Supplier<Collection<FileSystemItem>> fileSystemItemSupplier,
		FileSystemItem.Criteria filter,
		boolean firstMatch,
		Supplier<Collection<FileSystemItem>> outputCollectionSupplier
	) {
		Collection<FileSystemItem> fileSystemItems;
		try {
			fileSystemItems = fileSystemItemSupplier.get();
		} catch (Throwable exc) {
			ManagedLoggerRepository.logWarn(this.getClass()::getName, "Exception occurred while retrieving items from {}: ", getAbsolutePath(), Strings.formatMessage(exc));
			ManagedLoggerRepository.logInfo(this.getClass()::getName, "Trying to reset {} and reload items of ", getAbsolutePath());
			reset();
			fileSystemItems = fileSystemItemSupplier.get();
		}
		if (fileSystemItems == null) {
			return null;
		}
		Predicate<FileSystemItem[]> nativePredicate = filter.getOriginalPredicateOrTruePredicateIfPredicateIsNull();
		Collection<FileSystemItem> iteratedFISWithErrors = ConcurrentHashMap.newKeySet();
		BiFunction<Throwable, FileSystemItem[], Boolean> customExceptionHandler = filter.exceptionHandler;
		Predicate<FileSystemItem> filterPredicate = iteratedFileSystemItem -> {
			try {
				return nativePredicate.test(new FileSystemItem[] { iteratedFileSystemItem, this });
			} catch (ArrayIndexOutOfBoundsException | NullPointerException exc) {
				iteratedFISWithErrors.add(iteratedFileSystemItem);
				return false;
			} catch (Throwable exc) {
				if (customExceptionHandler == null) {
					throw exc;
				}
				iteratedFISWithErrors.add(iteratedFileSystemItem);
				return false;
			}
		};
		BiConsumer<FileSystemItem, Consumer<Consumer<Collection<FileSystemItem>>>> action = !firstMatch ?
			(child, outputCollectionHandler) -> {
				if (filterPredicate.test(child)) {
					outputCollectionHandler.accept(
						(outputCollection) -> {
							outputCollection.add(child);
						}
					);
				}
			} :
			(iteratedFileSystemItem, outputCollectionHandler) -> {
				if (filterPredicate.test(iteratedFileSystemItem)) {
					outputCollectionHandler.accept(
						(outputCollection) -> {
							outputCollection.add(iteratedFileSystemItem);
						}
					);
					IterableObjectHelper.terminateIteration();
				}
			};

		final Collection<FileSystemItem> result = IterableObjectHelper.iterateAndGet(
			IterationConfig.of(fileSystemItems)
			.parallelIf(
				filter.minimumCollectionSizeForParallelIterationPredicate != null ?
					filter.minimumCollectionSizeForParallelIterationPredicate::test :
					null
			)
			.withPriority(filter.priority)
			.withOutput(outputCollectionSupplier.get())
			.withAction(action)
		);

		if (!iteratedFISWithErrors.isEmpty()) {
			Predicate<FileSystemItem[]> nativePredicateWithExceptionManaging = filter.getPredicateOrTruePredicateIfPredicateIsNull();
			for (FileSystemItem child : iteratedFISWithErrors) {
				FileSystemItem[] childAndThis = { child, this };
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
		return findIn(this::getAllChildren0, filter, true, ConcurrentHashMap::newKeySet).stream().findFirst().orElseGet(() -> null);
	}

	public FileSystemItem findFirstInChildren() {
		return findFirstInAllChildren(FileSystemItem.Criteria.create());
	}

	public FileSystemItem findFirstInChildren(FileSystemItem.Criteria filter) {
		return findIn(this::getChildren0, filter, true, ConcurrentHashMap::newKeySet).stream().findFirst().orElseGet(() -> null);
	}

	public String getAbsolutePath() {
		return absolutePath.getKey();
	}

	public FileSystemItem getRoot() {
		FileSystemItem parent = getParent();
		while (parent != null) {
			if (parent.isRoot()) {
				return parent;
			}
			parent = parent.getParent();
		}
		return null;
	}

	public Collection<FileSystemItem> getAllParents() {
		Collection<FileSystemItem> allParents = newCollectionSupplier.get();
		FileSystemItem parent = getParent();
		while (parent != null) {
			allParents.add(parent);
			parent = parent.getParent();
		}
		return Collections.unmodifiableCollection(allParents);
	}

	public Collection<FileSystemItem> getAllChildren() {
		return Optional.ofNullable(getAllChildren0()).map(children ->  Collections.unmodifiableCollection(children)).orElseGet(() -> null);
	}

	private Collection<FileSystemItem> getAllChildren0() {
		Collection<FileSystemItem> allChildren = this.allChildren;
		if (allChildren == null) {
			allChildren = Synchronizer.execute(instanceId, () -> {
				Collection<FileSystemItem> allChildrenTemp = this.allChildren;
				if (allChildrenTemp == null) {
					allChildrenTemp = this.allChildren = loadAllChildren();
				}
				return allChildrenTemp;
			});
		}
		return allChildren;
	}

	public Collection<FileSystemItem> getChildren() {
		return Optional.ofNullable(getChildren0()).map(children -> Collections.unmodifiableCollection(children)).orElseGet(() -> null);
	}

	private Collection<FileSystemItem> getChildren0() {
		Collection<FileSystemItem> children = this.children;
		if (children == null) {
			children = Synchronizer.execute(instanceId, () -> {
				Collection<FileSystemItem> childrenTemp = this.children;
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
			return absolutePath;
		}
	}

	public Collection<FileSystemItem> findInAllParents(FileSystemItem.Criteria filter) {
		return findIn(this::getAllParents, filter, false, ConcurrentHashMap::newKeySet);
	}

	public Collection<FileSystemItem> findInAllParents(
		FileSystemItem.Criteria filter,
		Supplier<Collection<FileSystemItem>> setSupplier
	) {
		return findIn(this::getAllParents, filter, false, setSupplier);
	}

	public FileSystemItem findFirstInAllParents(FileSystemItem.Criteria filter) {
		return findIn(this::getAllParents, filter, true, ConcurrentHashMap::newKeySet).stream().findFirst().orElseGet(() -> null);
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
				try {
					return FileSystemItem.ofPath(
						absolutePath.getKey().substring(0, absolutePath.getKey().lastIndexOf("/")),
						conventionedPath
					);
				} catch (NullPointerException exc) {
					if (conventionedPath.equals("/")) {
						return FileSystemItem.ofPath(
							conventionedPath,
							conventionedPath
						);
					}
					throw exc;
				}
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
			return org.burningwave.core.assembler.StaticComponentContainer.Driver.throwException(exc);
		}
	}

	public boolean isArchive() {
		return computeConventionedAbsolutePathAndExecute(isArchive);
	}

	public boolean isChildOf(FileSystemItem fileSystemItem) {
		String otherConventionedAbsolutePath = fileSystemItem.computeConventionedAbsolutePathAndExecute(conventionedAbsolutePath ->
			conventionedAbsolutePath.toString()
		);
		String thisConventionedAbsolutePath = computeConventionedAbsolutePathAndExecute(conventionedAbsolutePath ->
			conventionedAbsolutePath.toString()
		);
		if (isContainer.apply(otherConventionedAbsolutePath)) {
			if (isContainer.apply(thisConventionedAbsolutePath)) {
				return thisConventionedAbsolutePath.startsWith(otherConventionedAbsolutePath)
					&& !thisConventionedAbsolutePath.equals(otherConventionedAbsolutePath);
			}
			return thisConventionedAbsolutePath.startsWith(otherConventionedAbsolutePath);
		}
		return false;
	}

	public boolean isCompressed() {
		return computeConventionedAbsolutePathAndExecute(isCompressed);
	}

	public boolean isContainer() {
		return computeConventionedAbsolutePathAndExecute(isContainer);
	}

	public boolean isFile() {
		return !isFolder();
	}

	public boolean isFolder() {
		return computeConventionedAbsolutePathAndExecute(isFolder);
	}

	public boolean isParentOf(FileSystemItem fileSystemItem) {
		String otherConventionedAbsolutePath = fileSystemItem.computeConventionedAbsolutePathAndExecute(conventionedAbsolutePath ->
			conventionedAbsolutePath.toString()
		);
		String thisConventionedAbsolutePath = computeConventionedAbsolutePathAndExecute(conventionedAbsolutePath ->
			conventionedAbsolutePath.toString()
		);
		if (isContainer.apply(thisConventionedAbsolutePath)) {
			if (isContainer.apply(otherConventionedAbsolutePath)) {
				return otherConventionedAbsolutePath.startsWith(thisConventionedAbsolutePath)
					&& !otherConventionedAbsolutePath.equals(thisConventionedAbsolutePath);
			}
			return otherConventionedAbsolutePath.startsWith(thisConventionedAbsolutePath);
		}
		return false;
	}

	public boolean isRoot() {
		return isRoot(getAbsolutePath());
	}

	private boolean isRoot(String absolutePathStr) {
		return absolutePathStr.chars().filter(ch -> ch == '/').count() == 0 || absolutePathStr.equals("/");
	}

	private <T> T computeConventionedAbsolutePathAndExecute(Function<String, T> function) {
		return computeConventionedAbsolutePathAndExecute(function, null);
	}

	private <T> T computeConventionedAbsolutePathAndExecute(Function<String, T> function, NullPointerException exception){
		try {
			String conventionedAbsolutePath = computeConventionedAbsolutePath();
			return function.apply(conventionedAbsolutePath);
		} catch (NullPointerException exc) {
			if (exception == null) {
				ManagedLoggerRepository.logWarn(
					getClass()::getName,
					"Exception occurred while trying to compute conventioned absolute path of {}. Trying to repeat the operation.",
					absolutePath.getKey()
				);
				return computeConventionedAbsolutePathAndExecute(function, exc);
			} else {
				throw exception;
			}
		}
	}

	Collection<FileSystemItem> loadAllChildren() {
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
					Collection<FileSystemItem> allChildren = newCollectionSupplier.get();
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
				Collection<FileSystemItem> children = getChildren();
				if (children != null) {
					Collection<FileSystemItem> allChildren = newCollectionSupplier.get();
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

	Collection<FileSystemItem> loadChildren() {
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
					return IterableObjectHelper.iterateAndGet(
						IterationConfig.ofNullable(file.listFiles())
						.withOutput(newCollectionSupplier.get())
						.withAction((fl, outputCollectionHandler) -> {
							outputCollectionHandler.accept(
								(outputCollection) -> {
									outputCollection.add(FileSystemItem.ofPath(fl.getAbsolutePath()));
								}
							);
						})
					);
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
						clearJavaClassWrapper(child);
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
			clearJavaClassWrapper(this);
			if (removeLinkedResourcesFromCache) {
				removeFromCache(this, removeFromCache);
			}
			return removeFromCache ? null : this;
		});
	}

	private void clearJavaClassWrapper(FileSystemItem fileSystemItem) {
		AtomicReference<JavaClass> javaClassWrapper = fileSystemItem.javaClassWrapper;
		if (javaClassWrapper != null) {
			JavaClass javaClass = javaClassWrapper.get();
			if (javaClass != null) {
				javaClass.close();
			} else {
				Synchronizer.execute(instanceId + "_loadJavaClass", () -> {
					fileSystemItem.javaClassWrapper = null;
				});
			}
		}
	}

	private Collection<FileSystemItem> retrieveChildren(Supplier<IterableZipContainer> zipInputStreamSupplier,
			String itemToSearch) {
		try (IterableZipContainer zipInputStream = zipInputStreamSupplier.get()) {
			final String itemToSearchRegEx = itemToSearch.replace("/", "\\/") + "(.*?)\\/";
			Pattern itemToSearchRegExPattern = Pattern.compile(itemToSearchRegEx);
			boolean isJModArchive = Streams.isJModArchive(zipInputStream.toByteBuffer());
			Set<String> folderRelPaths = new HashSet<>();
			Collection<FileSystemItem> children = newCollectionSupplier.get();
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
						ManagedLoggerRepository.logWarn(this.getClass()::getName, "Exception occurred while calling isArchive on file {}: {}", file.getAbsolutePath(),
								exc.getMessage());
						return realAbsolutePath;
					}
				}
			} else {
				try (FileInputStream fileInputStream = FileInputStream.create(file)) {
					return fileInputStream.getAbsolutePath() + IterableZipContainer.PATH_SUFFIX + retrieveConventionedRelativePath(
						fileInputStream.toByteBuffer(),
						fileInputStream.getAbsolutePath(),
						relativePath
					);
				} catch (FileSystemItemNotFoundException exc) {
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

	private String retrieveConventionedRelativePath(
			ByteBuffer zipInputStreamAsBytes,
			String zipInputStreamName,
			String relativePath
    ) {
		return retrieveConventionedRelativePath(zipInputStreamAsBytes, zipInputStreamName, relativePath, null);
	}

	private synchronized String retrieveConventionedRelativePath(
		ByteBuffer zipInputStreamAsBytes,
		String zipInputStreamName,
		String relativePath,
		NullPointerException initialException
	) {
		Class<? extends IterableZipContainer> iterableZipContainerType = null;
		try {
			try (IterableZipContainer zIS = IterableZipContainer.create(zipInputStreamName, zipInputStreamAsBytes);) {
				if (zIS == null) {
					return org.burningwave.core.assembler.StaticComponentContainer.Driver.throwException(
						new FileSystemItemNotFoundException("Absolute path \"" + absolutePath.getKey() + "\" not exists")
					);
				}
				iterableZipContainerType = zIS.getClass();
				Predicate<IterableZipContainer.Entry> zipEntryPredicate = zEntry -> {
					return zEntry.getName().equals(relativePath) || zEntry.getName().equals(relativePath + "/");
				};
				String temp = relativePath;
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
				Collection<IterableZipContainer.Entry> zipEntries = zIS.findAll(
					zipEntryPredicate,
					zEntry -> false
				);
				if (!zipEntries.isEmpty()) {
					IterableZipContainer.Entry zipEntry = Collections.max(
						zipEntries,
						Comparator.comparing(zipEntryW ->
							zipEntryW.getName().split("/").length
						)
					);
					return retrieveConventionedRelativePath(this, zIS, zipEntry, relativePath);
				} else if (Streams.isJModArchive(zipInputStreamAsBytes)) {
					try (IterableZipContainer zIS2 = IterableZipContainer.create(zipInputStreamName, zipInputStreamAsBytes)) {
						iterableZipContainerType = zIS2.getClass();
						if (zIS2.findFirst(zipEntry -> zipEntry.getName().startsWith(relativePath + "/"),
								zipEntry -> false) != null) {
							// in case of JMod files folder
							return retrieveConventionedRelativePath(this, zIS2, null, relativePath);
						}
						return org.burningwave.core.assembler.StaticComponentContainer.Driver.throwException(
							new FileSystemItemNotFoundException(
								Strings.compile("Absolute path \"{}\" not exists", absolutePath.getKey())
							)
						);
					}
				} else {
					throw new FileSystemItemNotFoundException(Strings.compile("Absolute path \"{}\" not exists", absolutePath.getKey()));
				}
			}
		} catch (NullPointerException exc) {
			if (initialException != null) {
				return org.burningwave.core.assembler.StaticComponentContainer.Driver.throwException(initialException);
			} else {
				ManagedLoggerRepository.logWarn(
					getClass()::getName,
					"Exception occurred while trying to compute conventioned relative path on {} (IterableZipContainer path: {})(relative path: {})(IterableZipContainer type: {}). Trying to repeat the operation.",
					absolutePath.getKey(), zipInputStreamName, relativePath,
					Optional.ofNullable(iterableZipContainerType).map(Class::getName).orElseGet(() -> "null")
				);
				return retrieveConventionedRelativePath(zipInputStreamAsBytes, zipInputStreamName, relativePath, exc);
			}
		}
	}

	private synchronized String retrieveConventionedRelativePath(FileSystemItem fileSystemItem, IterableZipContainer iZC,
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
					zipEntry.toByteBuffer(), zipEntry.getAbsolutePath(), relativePath2
				);
			}
			// in case of JMod files folder
		} else {
			if (fileSystemItem.parentContainer == null) {
				fileSystemItem.parentContainer = FileSystemItem.ofPath(iZC.getAbsolutePath());
			}
			return iZC.getAbsolutePath() + IterableZipContainer.PATH_SUFFIX + relativePath1 + "/";
		}
	}

	public FileSystemItem reloadContent() {
		return reloadContent(false);
	}

	public FileSystemItem reloadContent(boolean recomputeConventionedAbsolutePath) {
		if (exists() && !isFolder()) {
			String absolutePath = getAbsolutePath();
			if (isCompressed()) {
				try (IterableZipContainer iterableZipContainer = IterableZipContainer.create(
					getParentContainer().reloadContent(recomputeConventionedAbsolutePath).getAbsolutePath())
				) {
					IterableZipContainer.Entry zipEntry = iterableZipContainer.findFirst(
						iteratedZipEntry ->
							iteratedZipEntry.getAbsolutePath().equals(absolutePath),
						iteratedZipEntry ->
							iteratedZipEntry.getAbsolutePath().equals(absolutePath)
					);
					Cache.pathForContents.upload(
						absolutePath, () -> {
							return zipEntry.toByteBuffer();
						}, true
					);
				}
			} else {
				Cache.pathForContents.upload(
					absolutePath, () -> {
						try (FileInputStream fIS = FileInputStream.create(getAbsolutePath())) {
							return fIS.toByteBuffer();
						}
					}, true
				);
			}
		}
		return this;
	}

	public ByteBuffer toByteBuffer() {
		return Executor.get(this::toByteBuffer0, 2);
	}

	public byte[] toByteArray() {
		return BufferHandler.toByteArray(toByteBuffer());
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

	public InputStream toInputStream() {
		return new ByteBufferInputStream(toByteBuffer());
	}

	public JavaClass toJavaClass() {
		AtomicReference<JavaClass> javaClassWrapper = this.javaClassWrapper;
		if (javaClassWrapper != null) {
			return javaClassWrapper.get();
		} else {
			return Synchronizer.execute(instanceId + "_loadJavaClass", () -> {
				AtomicReference<JavaClass> javaClassWrapperInternalRef = this.javaClassWrapper;
				if (javaClassWrapperInternalRef != null) {
					return javaClassWrapperInternalRef.get();
				}
				try {
					return (this.javaClassWrapper = new AtomicReference<>(new JavaClass(this.toByteBuffer()) {

						@Override
						protected ByteBuffer getByteCode0() {
							return FileSystemItem.this.toByteBuffer();
						}

						@Override
						protected void setByteCode0(ByteBuffer byteCode) {}

						@Override
						public void close() {
							Synchronizer.execute(instanceId + "_loadJavaClass", () -> {
								AtomicReference<JavaClass> javaClassWrapperRef =
									FileSystemItem.this.javaClassWrapper;
								FileSystemItem.this.javaClassWrapper = null;
								javaClassWrapperRef.set(null);
							});
						}

					})).get();
				} catch (Throwable exc) {
					return (this.javaClassWrapper = new AtomicReference<>(null)).get();
				}
			});
		}
	}

	public <S extends Serializable> S toObject() {
		try (InputStream inputStream = toInputStream()) {
			return Objects.deserialize(inputStream);
		} catch (Throwable exc) {
			return org.burningwave.core.assembler.StaticComponentContainer.Driver.throwException(exc);
		}
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


	@Override
	public int compareTo(FileSystemItem fileSystemItem) {
		if(fileSystemItem == null) {
			return -1;
		}
		return this.getAbsolutePath().compareTo(fileSystemItem.getAbsolutePath());
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

		private final static BiFunction<Throwable, FileSystemItem[], Boolean> defaultExceptionHandler;

		private BiFunction<Throwable, FileSystemItem[], Boolean> exceptionHandler;
		private Predicate<Collection<?>> minimumCollectionSizeForParallelIterationPredicate;

		private Long timeoutForTimedFindIn;
		private Integer priority;

		static {
			defaultExceptionHandler = (exception, childAndParent) -> {
				ManagedLoggerRepository.logError(FileSystemItem.Criteria.class::getName, "Could not scan " + childAndParent[0].getAbsolutePath(), exception);
				return false;
			};
		}

		private Criteria() {}

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

		public Criteria setMinimumCollectionSizeForParallelIteration(int value) {
			this.minimumCollectionSizeForParallelIterationPredicate = coll -> value >= 0 && coll.size() >= value;
			return this;
		}

		public Criteria setMinimumCollectionSizeForParallelIteration(Predicate<Collection<?>> predicate) {
			this.minimumCollectionSizeForParallelIterationPredicate = predicate;
			return this;
		}

		public Criteria withPriority(Integer priority) {
			this.priority = priority;
			return this;
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

		public final Criteria enableDefaultExceptionHandler() {
			return setExceptionHandler(defaultExceptionHandler);
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

		public Predicate<Collection<?>> getMinimumCollectionSizeForParallelIterationPredicate() {
			return minimumCollectionSizeForParallelIterationPredicate;
		}

		public Integer getPriority() {
			return priority;
		}

		@Override
		protected Criteria logicOperation(
			Criteria leftCriteria, Criteria rightCriteria,
			Function<Predicate<FileSystemItem[]>,
			Function<Predicate<? super FileSystemItem[]>, Predicate<FileSystemItem[]>>> binaryOperator,
			Criteria targetCriteria
		) {
			targetCriteria = super.logicOperation(leftCriteria, rightCriteria, binaryOperator, targetCriteria);
			targetCriteria.exceptionHandler = rightCriteria.exceptionHandler == null ?
					leftCriteria.exceptionHandler : rightCriteria.exceptionHandler;
			Predicate<Collection<?>> minimumCollectionSizeForParallelIterationPredicate =
				rightCriteria.minimumCollectionSizeForParallelIterationPredicate == null ?
						leftCriteria.minimumCollectionSizeForParallelIterationPredicate :
						rightCriteria.minimumCollectionSizeForParallelIterationPredicate;
			targetCriteria.setMinimumCollectionSizeForParallelIteration(minimumCollectionSizeForParallelIterationPredicate);
			targetCriteria.priority = rightCriteria.priority == null ?
				leftCriteria.priority : rightCriteria.priority;
			targetCriteria.timeoutForTimedFindIn = rightCriteria.timeoutForTimedFindIn == null ?
					leftCriteria.timeoutForTimedFindIn : rightCriteria.timeoutForTimedFindIn;
			return targetCriteria;
		}

		private Predicate<FileSystemItem[]> nativePredicateToSomeExceptionManagedPredicate(
			Predicate<FileSystemItem[]> filterPredicate
		) {
			Predicate<FileSystemItem[]> finalFilterPredicate = childAndThis -> {
				try {
					try {
						return filterPredicate.test(childAndThis);
					} catch (ArrayIndexOutOfBoundsException exc) {
						String childAbsolutePath = childAndThis[0].getAbsolutePath();
						ManagedLoggerRepository.logWarn(this.getClass()::getName, "Exception occurred while analyzing {}", childAbsolutePath);
						ManagedLoggerRepository.logInfo(this.getClass()::getName, "Trying to reload content of {} and test it again", childAbsolutePath);
						childAndThis[0].reloadContent();
						return filterPredicate.test(childAndThis);
					} catch (NullPointerException exc) {
						String childAbsolutePath = childAndThis[0].getAbsolutePath();
						ManagedLoggerRepository.logWarn(this.getClass()::getName, "Exception occurred while analyzing {}", childAbsolutePath);
						ManagedLoggerRepository.logInfo(this.getClass()::getName, "Trying to reload content and conventioned absolute path of {} and test it again", childAbsolutePath);
						childAndThis[0].reloadContent(true);
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
			copy.minimumCollectionSizeForParallelIterationPredicate = this.minimumCollectionSizeForParallelIterationPredicate;
			copy.priority = this.priority;
			copy.timeoutForTimedFindIn = this.timeoutForTimedFindIn;
			return copy;
		}

	}


	public static class NotFoundException extends RuntimeException {

		private static final long serialVersionUID = -6767561476829612304L;

		public NotFoundException(String message) {
	        super(message);
	    }

	}

	public static enum Find implements BiFunction<FileSystemItem, FileSystemItem.Criteria, Collection<FileSystemItem>> {
		IN_ALL_CHILDREN(FileSystemItem::findInAllChildren),
		RECURSIVE_IN_CHILDREN(FileSystemItem::findRecursiveInChildren),
		IN_CHILDREN(FileSystemItem::findInChildren),
		FIRST_IN_ALL_CHILDREN((fileSystemItem, filter) -> Arrays.asList(fileSystemItem.findFirstInAllChildren(filter))),
		FIRST_IN_CHILDREN((fileSystemItem, filter) -> Arrays.asList(fileSystemItem.findFirstInChildren(filter)));

		BiFunction<FileSystemItem, FileSystemItem.Criteria, Collection<FileSystemItem>> function;

		Find(BiFunction<FileSystemItem, FileSystemItem.Criteria, Collection<FileSystemItem>> function) {
			this.function = function;
		}

		@Override
		public Collection<FileSystemItem> apply(FileSystemItem fileSystemItem, FileSystemItem.Criteria criteria) {
			return function.apply(fileSystemItem, criteria);
		}

		public static enum FunctionSupplier implements Function<FileSystemItem, FileSystemItem.Find> {
			OF_IN_ALL_CHILDREN(fileSystemItem -> Find.IN_ALL_CHILDREN),
			OF_RECURSIVE_IN_CHILDREN(fileSystemItem -> Find.RECURSIVE_IN_CHILDREN),
			OF_IN_CHILDREN(fileSystemItem -> Find.IN_CHILDREN),
			OF_FIRST_IN_ALL_CHILDREN(fileSystemItem -> FIRST_IN_ALL_CHILDREN),
			OF_FIRST_IN_CHILDREN(fileSystemItem -> FIRST_IN_CHILDREN);


			Function<FileSystemItem, FileSystemItem.Find> supplier;

			FunctionSupplier(Function<FileSystemItem, FileSystemItem.Find> supplier) {
				this.supplier = supplier;
			}

			@Override
			public Find apply(FileSystemItem fileSystemItem) {
				return supplier.apply(fileSystemItem);
			}

		}
	}

}