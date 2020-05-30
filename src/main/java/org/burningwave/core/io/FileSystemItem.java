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
import static org.burningwave.core.assembler.StaticComponentContainer.Paths;
import static org.burningwave.core.assembler.StaticComponentContainer.Streams;
import static org.burningwave.core.assembler.StaticComponentContainer.Strings;
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.burningwave.core.ManagedLogger;
import org.burningwave.core.function.ThrowingSupplier;
import org.burningwave.core.io.FileSystemScanner.Scan;

public class FileSystemItem implements ManagedLogger {
	private Map.Entry<String, String> absolutePath;
	private FileSystemItem parent;
	private FileSystemItem parentContainer;
	private Set<FileSystemItem> children;
	private Set<FileSystemItem> allChildren;
	private Boolean exists;
	
	private FileSystemItem(String realAbsolutePath) {
		realAbsolutePath = Paths.clean(realAbsolutePath);
		this.absolutePath = new AbstractMap.SimpleEntry<>(realAbsolutePath, null);
	}
	
	private FileSystemItem(String realAbsolutePath, String conventionedAbsolutePath) {
		realAbsolutePath = Paths.clean(realAbsolutePath);
		this.absolutePath = new AbstractMap.SimpleEntry<>(realAbsolutePath, conventionedAbsolutePath);
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
		FileSystemItem fileSystemItem = Cache.pathForFileSystemItems.getOrUploadIfAbsent(
			realAbsolutePathCleaned, () -> {
				if (Strings.isNotEmpty(realAbsolutePathCleaned)) {
					return new FileSystemItem(realAbsolutePathCleaned, conventionedAbsolutePath);
				}
				return null;
			}
		);
		if (fileSystemItem.absolutePath.getValue() == null && conventionedAbsolutePath != null) {
			fileSystemItem.absolutePath.setValue(conventionedAbsolutePath);
			fileSystemItem.exists = true;
		}
		return fileSystemItem;
	}
	
	private synchronized String computeConventionedAbsolutePath() {
		if ((absolutePath.getValue() == null && exists != Boolean.FALSE) || parentContainer == null) {
			if (parentContainer != null && parentContainer.isArchive()) {
				ByteBuffer par = parentContainer.toByteBuffer();
				String relativePath = absolutePath.getKey().replace(parentContainer.getAbsolutePath() + "/", "");
				String conventionedAbsolutePath = parentContainer.computeConventionedAbsolutePath() + retrieveConventionedRelativePath(par, parentContainer.getAbsolutePath(), relativePath);
				absolutePath.setValue(conventionedAbsolutePath);
				exists = true;
			} else {
				absolutePath.setValue(retrieveConventionedAbsolutePath(absolutePath.getKey(), ""));
			}
		}
		if (!exists) {
			clear(true);
			exists = false;
		}
		return absolutePath.getValue();
	}
	
	private String retrieveConventionedAbsolutePath(String realAbsolutePath, String relativePath) {
		File file = new File(realAbsolutePath);
		if (file.exists()) {
			exists = true;
			if (relativePath.isEmpty()) {
				if (file.isDirectory()) {
					return realAbsolutePath + (realAbsolutePath.endsWith("/")? "" : "/");
				} else {
					try {
						if (Streams.isArchive(file)) {
							return realAbsolutePath + IterableZipContainer.ZIP_PATH_SEPARATOR;
						} else {
							return realAbsolutePath;
						}
					} catch (IOException exc) {
						logWarn("Exception occurred while calling isArchive on file {}: {}", file.getAbsolutePath(), exc.getMessage());
						return realAbsolutePath;
					}
				}
			} else {
				try (FileInputStream fileInputStream = FileInputStream.create(file)) {
					exists = true;
					return fileInputStream.getAbsolutePath() + IterableZipContainer.ZIP_PATH_SEPARATOR + retrieveConventionedRelativePath(
						fileInputStream.toByteBuffer(), fileInputStream.getAbsolutePath(), relativePath
					);
				} catch (Exception exc) {
					exists = false;
					logWarn("File {}/{} does not exists", realAbsolutePath, relativePath);
					return null;
				} 
			}		
		} else if (realAbsolutePath.chars().filter(ch -> ch == '/').count() > 1) {
			String pathToTest = realAbsolutePath.substring(0, realAbsolutePath.lastIndexOf("/"));
			relativePath = realAbsolutePath.replace(pathToTest + "/", "") + (relativePath.isEmpty()? "" : "/") + relativePath;
			return retrieveConventionedAbsolutePath(pathToTest, relativePath);
		} else {
			exists = false;
			return null;
		}
	}


	private String retrieveConventionedRelativePath(ByteBuffer zipInputStreamAsBytes, String zipInputStreamName, String relativePath1) {
		try (IterableZipContainer zIS = IterableZipContainer.create(zipInputStreamName, zipInputStreamAsBytes)){
			if (zIS == null) {
				throw new FileSystemItemNotFoundException("Absolute path \"" + absolutePath.getKey() + "\" not exists");
			}
			Predicate<IterableZipContainer.Entry> zipEntryPredicate = zEntry -> zEntry.getName().equals(relativePath1) || zEntry.getName().equals(relativePath1 + "/");
			String temp = relativePath1;
			while (temp != null) {
				int lastIndexOfSlash = temp.lastIndexOf("/");
				final String temp2 = lastIndexOfSlash != -1? temp.substring(0, lastIndexOfSlash) : temp;
				zipEntryPredicate = zipEntryPredicate.or(zEntry -> zEntry.getName().equals(temp2) || zEntry.getName().equals(temp2 + "/"));
				if (lastIndexOfSlash == -1) {
					temp = null;
				} else {
					temp = temp2;
				}
			}
			Set<IterableZipContainer.Entry> zipEntryWrappers = zIS.findAll(
				zipEntryPredicate, zEntry -> false
			);
			if (!zipEntryWrappers.isEmpty()) {
				IterableZipContainer.Entry zipEntryWrapper = Collections.max(
					zipEntryWrappers, Comparator.comparing(zipEntryW -> zipEntryW.getName().split("/").length)
				);
				return retrieveConventionedRelativePath(this, zipEntryWrapper, relativePath1);			
			} else {
				throw new FileSystemItemNotFoundException("Absolute path \"" + absolutePath.getKey() + "\" not exists");
			}
		}
	}

	protected String retrieveConventionedRelativePath(
		FileSystemItem fileSystemItem,
		IterableZipContainer.Entry zipEntry,
		String relativePath1
	) {
		String relativePath2 = zipEntry.getName();
		if (relativePath2.endsWith("/")) {
			relativePath2 = relativePath2.substring(0, relativePath2.length() - 1);
		}
		relativePath2 = relativePath1.substring(relativePath2.length());
		if (relativePath2.startsWith("/")) {
			relativePath2 = relativePath2.replaceFirst("\\/", "");
		}
		if (relativePath2.isEmpty()) {
			if (fileSystemItem.parentContainer == null) {
				fileSystemItem.parentContainer = FileSystemItem.ofPath(zipEntry.getParentContainer().getAbsolutePath());
			}
			return zipEntry.getName() + (!zipEntry.isDirectory() && zipEntry.isArchive() ? IterableZipContainer.ZIP_PATH_SEPARATOR : "");
		} else {
			return zipEntry.getName() + IterableZipContainer.ZIP_PATH_SEPARATOR + retrieveConventionedRelativePath(
				zipEntry.toByteBuffer(), zipEntry.getAbsolutePath(), relativePath2
			);
		}
	}
	
	public URL getURL() {
		try {
			return new URL(toURL());
		} catch (MalformedURLException exc) {
			throw Throwables.toRuntimeException(exc);
		}
	}
	
	private String toURL() {
		String url = computeConventionedAbsolutePath();
		String prefix = "file:";
		if (!url.startsWith("/")) {
			prefix = prefix + "/";
		}
		if (isCompressed()) {
			prefix = getParentContainer().getExtension() + ":" + prefix;
		}
		String uRLToRet = url.replace(IterableZipContainer.ZIP_PATH_SEPARATOR, "!/");
		url = ThrowingSupplier.get(() -> URLEncoder.encode(uRLToRet, StandardCharsets.UTF_8.name())).replace("%3A", ":").replace("%21", "!").replace("%2F", "/");
		url = prefix + url;
		return url;
	}
	
	public String getAbsolutePath() {
		return absolutePath.getKey();
	}
	
	public String getExtension() {
		String extension = null;
		String name = getName();
		if (name.contains(".")) {
			extension = name.substring(name.lastIndexOf(".") + 1);
		}
		return extension;
	}
	
	public String getName() {
		FileSystemItem parent = getParent();
		return getAbsolutePath().replace(parent.getAbsolutePath() + "/", "");
	}
	
	public InputStream toInputStream() {
		return new ByteBufferInputStream(toByteBuffer());
	}
	
	public boolean isRoot() {
		String absolutePathStr = getAbsolutePath();
		return absolutePathStr.chars().filter(ch -> ch == '/').count() == 0 || absolutePathStr.equals("/");
	}
	
	public FileSystemItem getParent() {
		if (parent != null) {
			return parent;
		} else if (isRoot()) {
			return null;
		} else {			
			String conventionedPath = absolutePath.getValue();
			if (conventionedPath != null) {
				if (conventionedPath.endsWith("/")) {
					int offset = -1;
					if (conventionedPath.endsWith("//")) {
						offset = -IterableZipContainer.ZIP_PATH_SEPARATOR.length();
					}
					conventionedPath = conventionedPath.substring(0, conventionedPath.length() + offset);	
				}
				conventionedPath = conventionedPath.substring(0, conventionedPath.lastIndexOf("/")) + "/";
				return FileSystemItem.ofPath(
					absolutePath.getKey().substring(0, absolutePath.getKey().lastIndexOf("/")),
					conventionedPath
				);
			} else {
				return parent = FileSystemItem.ofPath(
					absolutePath.getKey().substring(0, absolutePath.getKey().lastIndexOf("/"))
				);
			}
		}
	}
	
	public FileSystemItem getParentContainer() {
		if (parentContainer != null) {
			return parentContainer;
		} else {
			computeConventionedAbsolutePath();
			if (parentContainer == null) {
				parentContainer = getParent();
			}
		}
		return parentContainer;
	}
	
	public synchronized FileSystemItem refresh() {
		return refresh(false);
	}
	
	public synchronized FileSystemItem reset() {
		return reset(true);
	}
	
	private synchronized FileSystemItem reset(boolean removeFromCache) {
		return clear(removeFromCache);
	}
	
	public synchronized FileSystemItem refresh(boolean removeFromCache) {
		reset(removeFromCache);
		computeConventionedAbsolutePath();
		return this;
	}
	
	private synchronized FileSystemItem clear(boolean removeFromCache) {
		if (allChildren != null) {
			for (FileSystemItem child : allChildren) {
				child.clear(removeFromCache);
			}
			allChildren.clear();
			allChildren = null;
			if (children != null) {
				children.clear();
				children = null;
			}			
		} else if (children != null) {
			for (FileSystemItem child : children) {
				child.clear(removeFromCache);
			}
			children.clear();
			children = null;
		}
		exists = null;
		parentContainer = null;
		absolutePath.setValue(null);
		parent = null;
		if (removeFromCache) {
			removeFromCache(this);
		}
		return this;
	}
	
	private void removeFromCache(FileSystemItem fileSystemItem) {
		Cache.pathForContents.remove(fileSystemItem.getAbsolutePath());
		Cache.pathForFileSystemItems.remove(fileSystemItem.getAbsolutePath());
		Cache.pathForZipFiles.remove(fileSystemItem.getAbsolutePath());
	}
	
	public <C extends Set<FileSystemItem>> Set<FileSystemItem> getChildren(Predicate<FileSystemItem> filter) {
		return getChildren(filter, HashSet::new);
	}
	
	public <C extends Set<FileSystemItem>> Set<FileSystemItem> getChildren(Predicate<FileSystemItem> filter, Supplier<C> setSupplier) {
		return Optional.ofNullable(getChildren0()).map(children -> children.stream().filter(filter).collect(Collectors.toCollection(setSupplier))).orElseGet(() -> null);
	}
	
	public Set<FileSystemItem> getChildren() {
		return Optional.ofNullable(getChildren0()).map(children -> new HashSet<>(children)).orElseGet(() -> null);
	}
	
	private Set<FileSystemItem> getChildren0() {
		if (children == null) {
			synchronized (this) {
				if (children == null) {
					children = loadChildren();
				}
			}
		}
		return children;
	}

	protected Set<FileSystemItem> loadChildren() {
		String conventionedAbsolutePath = computeConventionedAbsolutePath();
		if (isContainer()) {
			if (isCompressed()) {
				if (isArchive()) {
					Supplier<IterableZipContainer> zipInputStreamSupplier = () -> 
						IterableZipContainer.create(
							getAbsolutePath(), toByteBuffer()
						)
					;
					return getChildren(zipInputStreamSupplier, "");
				} else if (isFolder()) {
					Supplier<IterableZipContainer> zipInputStreamSupplier = () -> 
						IterableZipContainer.create(
							parentContainer.getAbsolutePath(), parentContainer.toByteBuffer()
						)
					;
					return getChildren(
						zipInputStreamSupplier, 
						conventionedAbsolutePath.substring(conventionedAbsolutePath.lastIndexOf(IterableZipContainer.ZIP_PATH_SEPARATOR) + IterableZipContainer.ZIP_PATH_SEPARATOR.length())
					);
				}
			} else if (isArchive()) {
				String zipFilePath = conventionedAbsolutePath.substring(0, conventionedAbsolutePath.indexOf(IterableZipContainer.ZIP_PATH_SEPARATOR));
				File file = new File(zipFilePath);
				if (file.exists()) {
					try (FileInputStream fIS = FileInputStream.create(file)) {
						return getChildren(() -> 
							IterableZipContainer.create(fIS), 
							conventionedAbsolutePath.replaceFirst(zipFilePath + IterableZipContainer.ZIP_PATH_SEPARATOR, "")
						);
					}
				}
			} else {
				File file = new File(conventionedAbsolutePath);
				if (file.exists()) {
					return Optional.ofNullable(
						file.listFiles()
					).map((childrenFiles) -> 
						Arrays.stream(childrenFiles
					).map(fl -> 
						FileSystemItem.ofPath(fl.getAbsolutePath())
					).collect(
						Collectors.toCollection(
							HashSet::new
						)
					)).orElseGet(HashSet::new);
				}
			}
		}
		return null;
	}
	
	
	public <C extends Set<FileSystemItem>> Set<FileSystemItem> getAllChildren(Predicate<FileSystemItem> filter) {
		return getAllChildren(filter, HashSet::new);
	}
	
	public <C extends Set<FileSystemItem>> Set<FileSystemItem> getAllChildren(Predicate<FileSystemItem> filter, Supplier<C> setSupplier) {
		return Optional.ofNullable(getAllChildren0()).map(children -> children.stream().filter(filter).collect(Collectors.toCollection(setSupplier))).orElseGet(() -> null);
	}
	
	public Set<FileSystemItem> getAllChildren() {
		return Optional.ofNullable(getAllChildren0()).map(children -> new HashSet<>(children)).orElseGet(() -> null);
	}
	
	public Set<FileSystemItem> getAllChildren0() {
		if (allChildren == null) {
			synchronized (this) {
				if (allChildren == null) {
					allChildren = loadAllChildren();
				}
			}
		}
		return allChildren;
	}

	protected Set<FileSystemItem>  loadAllChildren() {
		if (isCompressed() || isArchive()) {
			Predicate<IterableZipContainer.Entry> zipEntryPredicate = null;
			FileSystemItem parentContainerTemp = this;
			if (isArchive()) {
				zipEntryPredicate = zEntry -> !zEntry.getName().equals("/");
			} else if (isFolder()) {
				parentContainerTemp = parentContainer;
				zipEntryPredicate = zEntry ->
					zEntry.getAbsolutePath().startsWith(getAbsolutePath() + "/");
			}
			final FileSystemItem parentContainer = parentContainerTemp;
			try (IterableZipContainer zipInputStream = IterableZipContainer.create(parentContainer.getAbsolutePath(), parentContainer.toByteBuffer())) {					
				Set<FileSystemItem> allChildren = new HashSet<>();
				zipInputStream.findAllAndConvert(
					() -> allChildren,
					zipEntryPredicate,
					zEntry -> {
						FileSystemItem fileSystemItem = FileSystemItem.ofPath(
							parentContainer.getAbsolutePath() + "/" +zEntry.getName()
						);
						fileSystemItem.absolutePath.setValue(
							parentContainer.computeConventionedAbsolutePath() + retrieveConventionedRelativePath(
								fileSystemItem, zEntry, zEntry.getName()
							)
						);
						fileSystemItem.exists = true;
						logDebug(fileSystemItem.getAbsolutePath());
						if (fileSystemItem.isArchive()) {
							Optional.ofNullable(
								fileSystemItem.getAllChildren()
							).ifPresent(fileSystemItemChildrens ->
								allChildren.addAll(fileSystemItemChildrens)
							);
						}
						return fileSystemItem;
					},
					zEntry -> true
				);
				return allChildren;
			}
		} else if (isFolder()) {
			logDebug("Retrieving all children of " + absolutePath.getKey());
			Set<FileSystemItem> children = getChildren();
			if (children != null) {
				Set<FileSystemItem> allChildren = new HashSet<>();
				allChildren.addAll(children);
				children.forEach(
					child -> {
						Optional.ofNullable(child.getAllChildren()).map(allChildrenOfChild -> allChildren.addAll(allChildrenOfChild));
					}
				);
				return allChildren;
			}
		}
		return null;
	}
	
	private Set<FileSystemItem> getChildren(Supplier<IterableZipContainer> zipInputStreamSupplier, String itemToSearch) {
		try (IterableZipContainer zipInputStream = zipInputStreamSupplier.get()) {
			if (itemToSearch.contains(IterableZipContainer.ZIP_PATH_SEPARATOR)) {
				String zipEntryNameOfNestedZipFile = itemToSearch.substring(0, itemToSearch.indexOf(IterableZipContainer.ZIP_PATH_SEPARATOR));
				IterableZipContainer.Entry zipEntryWrapper = zipInputStream.findFirst(
					zEntry -> zEntry.getName().equals(zipEntryNameOfNestedZipFile),
					zEntry -> false
				);
				if (zipEntryWrapper == null) {
					return null;
				}
				try (InputStream iss = zipEntryWrapper.toInputStream()) {
					return getChildren(
						() -> IterableZipContainer.create(zipEntryWrapper.getAbsolutePath(), zipEntryWrapper.toInputStream()), 
						itemToSearch.replaceFirst(zipEntryNameOfNestedZipFile + IterableZipContainer.ZIP_PATH_SEPARATOR, "")
					);
				} catch (IOException exc) {
					logWarn("Exception occurred while opening input stream from zipEntry {}: {}", zipEntryWrapper.getAbsolutePath(), exc.getMessage());
					return null;
				}
			} else {
				final String iTS = itemToSearch.replace("/", "\\/") + ".*?\\/";
				Set<FileSystemItem> toRet = zipInputStream.findAllAndConvert(
					(zEntry) -> {
						String nameToTest = zEntry.getName();
						nameToTest += nameToTest.endsWith("/") ? "" : "/";
						//logDebug(nameToTest + " = " + nameToTest.matches(iTS) + " " + (nameToTest.replaceFirst(iTS, "").length() == 0) + " " + nameToTest.replaceFirst(iTS, ""));
						return nameToTest.matches(iTS) && nameToTest.replaceFirst(iTS, "").length() == 0;
					},
					(zEntry) -> {
						FileSystemItem fileSystemItem = FileSystemItem.ofPath(zEntry.getAbsolutePath());
						if (fileSystemItem.parentContainer == null) {
							fileSystemItem.parentContainer = FileSystemItem.ofPath(zEntry.getParentContainer().getAbsolutePath());
						}
						return fileSystemItem;
					},
					zEntry -> false
				);
				return toRet;
			}
		}
	}
	
	public synchronized boolean exists() {
		if (exists == null) {
			computeConventionedAbsolutePath();
		}
		return exists;
	}
	
	public synchronized boolean isContainer() {
		String conventionedAbsolutePath = computeConventionedAbsolutePath();
		return conventionedAbsolutePath.endsWith("/");
	}
	
	public synchronized boolean isCompressed() {
		String conventionedAbsolutePath = computeConventionedAbsolutePath();
		return 
			(conventionedAbsolutePath.contains(IterableZipContainer.ZIP_PATH_SEPARATOR) && 
				!conventionedAbsolutePath.endsWith(IterableZipContainer.ZIP_PATH_SEPARATOR)) ||
			(conventionedAbsolutePath.contains(IterableZipContainer.ZIP_PATH_SEPARATOR) && 
				conventionedAbsolutePath.endsWith(IterableZipContainer.ZIP_PATH_SEPARATOR) && 
					conventionedAbsolutePath.indexOf(IterableZipContainer.ZIP_PATH_SEPARATOR) != conventionedAbsolutePath.lastIndexOf(IterableZipContainer.ZIP_PATH_SEPARATOR))
		;
	}
	
	public synchronized boolean isFile() {
		return !isContainer() || isArchive();
	}
	
	public synchronized boolean isArchive() {
		String conventionedAbsolutePath = computeConventionedAbsolutePath();
		return conventionedAbsolutePath.endsWith(IterableZipContainer.ZIP_PATH_SEPARATOR);
	}
	
	public synchronized boolean isFolder() {
		String conventionedAbsolutePath = computeConventionedAbsolutePath();
		return conventionedAbsolutePath.endsWith("/") && !conventionedAbsolutePath.endsWith(IterableZipContainer.ZIP_PATH_SEPARATOR);
	}
	
	public ByteBuffer toByteBuffer() {
		String absolutePath = getAbsolutePath();
		ByteBuffer resource = Cache.pathForContents.getOrUploadIfAbsent(absolutePath, null); 
		if (resource != null) {
			return resource;
		}
		computeConventionedAbsolutePath();
		if (exists && !isFolder()) {
			if (isCompressed()) {
				return Cache.pathForContents.get(absolutePath);
			} else {
				try (FileInputStream fIS = FileInputStream.create(absolutePath)) {
					return Cache.pathForContents.getOrUploadIfAbsent(
						absolutePath, () ->
						fIS.toByteBuffer()
					);
				}
			}
		}
		return null;
	}
	
	public FileSystemItem copyTo(String folder) {
		return copyTo(folder, null);
	}
	
	public FileSystemItem copyAllChildrenTo(String folder) {
		return copyAllChildrenTo(folder, null);
	}
	
	public FileSystemItem copyAllChildrenTo(String folder, Predicate<FileSystemItem> filter){
		Predicate<FileSystemItem> finalFilter = fileSystemItem -> !fileSystemItem.isArchive();
		finalFilter = filter != null ? finalFilter.and(filter) : finalFilter; 
		Set<FileSystemItem> allChildren = getAllChildren(finalFilter);
		for (FileSystemItem child : allChildren) {
			FileSystemItem destFile = FileSystemItem.ofPath(folder + child.getAbsolutePath().replaceFirst(this.getAbsolutePath(), ""));
			logDebug("Copying " + child.getAbsolutePath());
			if (child.isFolder()) {
				File file = new File(destFile.getAbsolutePath());
				if (!file.exists()) {
					file.mkdirs();
				}
			} else {
				Streams.store(destFile.getParent().getAbsolutePath() + "/" + child.getName(), child.toByteBuffer());
			}
		}
		return FileSystemItem.ofPath(folder);
	}
	
	public static Consumer<Scan.ItemContext> getFilteredConsumerForFileSystemScanner(
		Predicate<FileSystemItem> fileSystemItemFilter,
		Consumer<FileSystemItem> fileSystemItemConsumer
	) {
		return (scannedItemContext) -> {
			FileSystemItem fileSystemItem = null;
			Scan.ItemWrapper itemWrapper = scannedItemContext.getScannedItem();
			if (!(itemWrapper.getWrappedItem() instanceof FileInputStream || 
				itemWrapper.getWrappedItem() instanceof File)) {
				if (itemWrapper.getWrappedItem() instanceof IterableZipContainer.Entry) {
					IterableZipContainer.Entry zipEntry = (IterableZipContainer.Entry)itemWrapper.getWrappedItem();
					fileSystemItem = FileSystemItem.ofPath(
						zipEntry.getAbsolutePath(),
						zipEntry.getConventionedAbsolutePath()
					);
				} else {
					IterableZipContainer zipContainer = (IterableZipContainer)itemWrapper.getWrappedItem();
					fileSystemItem = FileSystemItem.ofPath(
						zipContainer.getAbsolutePath(),
						zipContainer.getConventionedAbsolutePath()
					);					
				}
			} else {
				File file = null;
				if (itemWrapper.getWrappedItem() instanceof FileInputStream) {
					file = ((FileInputStream)itemWrapper.getWrappedItem()).getFile();
				} else {
					file = ((File)itemWrapper.getWrappedItem());
				}
				String conventionedAbsolutePath = Paths.clean(file.getAbsolutePath());
				conventionedAbsolutePath +=	file.isDirectory()? "/" : "";
				fileSystemItem = FileSystemItem.ofPath(scannedItemContext.getScannedItem().getAbsolutePath(), conventionedAbsolutePath);
			}
			Cache.pathForContents.getOrUploadIfAbsent(fileSystemItem.getAbsolutePath(), () -> itemWrapper.toByteBuffer());
			if (fileSystemItemFilter.test(fileSystemItem)) {
				fileSystemItemConsumer.accept(fileSystemItem);
			}
		};
	}
	
	public FileSystemItem copyTo(String folder, Predicate<FileSystemItem> filter) {
		FileSystemItem destination = null;
		if (isFile()) {
			if (filter == null || filter.test(this)) {
				destination = Streams.store(folder + "/" + getName(), toByteBuffer());
			}
		} else {
			File file = new File(folder + "/" + getName());
			file.mkdirs();
			for (FileSystemItem fileSystemItem : (filter == null ? getChildren() : getChildren(filter))) {
				fileSystemItem.copyTo(file.getAbsolutePath(), filter);
			}
			logDebug("Copied folder to " + file.getAbsolutePath());
			destination = FileSystemItem.ofPath(file.getAbsolutePath());
		}
		return destination;
	}
	
	@Override
	public String toString() {
		return absolutePath.getKey();
	}

}
