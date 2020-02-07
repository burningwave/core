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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.burningwave.Throwables;
import org.burningwave.core.ManagedLogger;
import org.burningwave.core.Strings;

public class FileSystemItem implements ManagedLogger {
	private final static String ZIP_PATH_SEPARATOR = "//"; 
		
	private Map.Entry<String, String> absolutePath;
	private FileSystemItem parent;
	private FileSystemItem parentContainer;
	private Set<FileSystemItem> children;
	private Set<FileSystemItem> allChildren;
	private Boolean exists;
	
	private FileSystemItem(String realAbsolutePath) {
		realAbsolutePath = Strings.Paths.clean(realAbsolutePath);
		this.absolutePath = new AbstractMap.SimpleEntry<>(realAbsolutePath, null);
	}
	
	private FileSystemItem(String realAbsolutePath, String conventionedAbsolutePath) {
		realAbsolutePath = Strings.Paths.clean(realAbsolutePath);
		this.absolutePath = new AbstractMap.SimpleEntry<>(realAbsolutePath, conventionedAbsolutePath);
	}
	
	public static FileSystemItem ofPath(URL realAbsolutePath) {
		return ofPath(Strings.Paths.convertFromURLPath(realAbsolutePath.getPath()));
	}
	
	public static FileSystemItem ofPath(String realAbsolutePath) {
		return ofPath(realAbsolutePath, null);
	}
	
	private static FileSystemItem ofPath(String realAbsolutePath, String conventionedAbsolutePath) {
		if (realAbsolutePath.contains("..") ||
			realAbsolutePath.contains(".\\") ||
			realAbsolutePath.contains(".//")
		) {
			realAbsolutePath = Paths.get(realAbsolutePath).normalize().toString();
		}
		final String realAbsolutePathCleaned = Strings.Paths.clean(realAbsolutePath);
		FileSystemItem fileSystemItem = Cache.PATH_FOR_FILE_SYSTEM_ITEMS.getOrDefault(
			realAbsolutePath, () -> {
				if (Strings.isNotEmpty(realAbsolutePathCleaned)) {
					return new FileSystemItem(realAbsolutePathCleaned, conventionedAbsolutePath);
				}
				return null;
			}
		);
		return fileSystemItem;
	}
	
	private String getConventionedAbsolutePath() {
		if ((absolutePath.getValue() == null && exists == null) || parentContainer == null) {
			if (parentContainer != null && parentContainer.isArchive()) {
				ByteBuffer par = parentContainer.toByteBuffer();
				String relativePath = absolutePath.getKey().replace(parentContainer.getAbsolutePath() + "/", "");
				String conventionedAbsolutePath = parentContainer.getConventionedAbsolutePath() + retrieveConventionedRelativePath(par, parentContainer.getAbsolutePath(), relativePath);
				absolutePath.setValue(conventionedAbsolutePath);
				exists = true;
			} else {
				absolutePath.setValue(retrieveConventionedAbsolutePath(absolutePath.getKey(), ""));
			}
			if (!exists) {
				Cache.PATH_FOR_FILE_SYSTEM_ITEMS.remove(absolutePath.getKey());
			}
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
							return realAbsolutePath + ZIP_PATH_SEPARATOR;
						} else {
							return realAbsolutePath;
						}
					} catch (FileNotFoundException exc) {
						logWarn("Exception occurred while calling isArchive on file {}: {}", file.getAbsolutePath(), exc.getMessage());
						return realAbsolutePath;
					} catch (IOException exc) {
						logWarn("Exception occurred while calling isArchive on file {}: {}", file.getAbsolutePath(), exc.getMessage());
						return realAbsolutePath;
					}
				}
			} else {
				try (FileInputStream fileInputStream = FileInputStream.create(file)) {
					exists = true;
					return fileInputStream.getAbsolutePath() + ZIP_PATH_SEPARATOR + retrieveConventionedRelativePath(
						fileInputStream.toByteBuffer(), fileInputStream.getAbsolutePath(), relativePath
					);
				} catch (FileSystemItemNotFoundException exc) {
					exists = false;
					String fileName = realAbsolutePath + (realAbsolutePath.endsWith("/")? "" : "/") + relativePath;
					logWarn("File {}/{} does not exists", realAbsolutePath, relativePath);
					return fileName;
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
			return zipEntry.getName() + (!zipEntry.isDirectory() && zipEntry.isArchive() ? ZIP_PATH_SEPARATOR : "");
		} else {
			return zipEntry.getName() + ZIP_PATH_SEPARATOR + retrieveConventionedRelativePath(
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
		String url = getConventionedAbsolutePath();
		String prefix = "file:";
		if (!url.startsWith("/")) {
			prefix = prefix + "/";
		}
		if (isCompressed()) {
			prefix = getParentContainer().getExtension() + ":" + prefix;
		}
		url = prefix + url.replace(ZIP_PATH_SEPARATOR, "!/");
		if (url.contains(" ")) {
			url = url.replace(" ", "%20");
		}
		if (url.contains("[")) {
			url = url.replace("[", "%5b");
		}
		if (url.contains("]")) {
			url = url.replace("]", "%5d");
		}
		return url;
	}
	
	public String getAbsolutePath() {
		return absolutePath.getKey();
	}
	
	public String getExtension() {
		String extension = getName();
		extension = extension.substring(extension.lastIndexOf(".") + 1);
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
						offset = -ZIP_PATH_SEPARATOR.length();
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
			getConventionedAbsolutePath();
			if (parentContainer == null) {
				parentContainer = getParent();
			}
		}
		return parentContainer;
	}
	
	public void refresh() {
		children = null;
		allChildren = null;
		exists = null;
		parentContainer = null;
		absolutePath.setValue(null);
		parent = null;
		getConventionedAbsolutePath();		
	}
	
	public <C extends Set<FileSystemItem>> Set<FileSystemItem> getChildren(Predicate<FileSystemItem> filter) {
		return getChildren(filter, ConcurrentHashMap::newKeySet);
	}
	
	public <C extends Set<FileSystemItem>> Set<FileSystemItem> getChildren(Predicate<FileSystemItem> filter, Supplier<C> setSupplier) {
		return getChildren().stream().filter(filter).collect(Collectors.toCollection(setSupplier));
	}
	
	public Set<FileSystemItem> getChildren() {
		if (children != null) {
			return children;
		} else {
			String conventionedAbsolutePath = getConventionedAbsolutePath();
			if (isContainer()) {
				if (isCompressed()) {
					if (isArchive()) {
						Supplier<IterableZipContainer> zipInputStreamSupplier = () -> 
							IterableZipContainer.create(
								getAbsolutePath(), toByteBuffer()
							)
						;
						children = getChildren(zipInputStreamSupplier, "");
					} else if (isFolder()) {
						Supplier<IterableZipContainer> zipInputStreamSupplier = () -> 
							IterableZipContainer.create(
								parentContainer.getAbsolutePath(), parentContainer.toByteBuffer()
							)
						;
						children = getChildren(
							zipInputStreamSupplier, 
							conventionedAbsolutePath.substring(conventionedAbsolutePath.lastIndexOf(ZIP_PATH_SEPARATOR) + ZIP_PATH_SEPARATOR.length())
						);
					}
				} else if (isArchive()) {
					String zipFilePath = conventionedAbsolutePath.substring(0, conventionedAbsolutePath.indexOf(ZIP_PATH_SEPARATOR));
					File file = new File(zipFilePath);
					if (file.exists()) {
						try (FileInputStream fIS = FileInputStream.create(file)) {
							children = getChildren(() -> 
								IterableZipContainer.create(fIS), 
								conventionedAbsolutePath.replaceFirst(zipFilePath + ZIP_PATH_SEPARATOR, "")
							);
						}
					}
				} else {
					File file = new File(conventionedAbsolutePath);
					if (file.exists()) {
						children = Optional.ofNullable(
							file.listFiles()
						).map((childrenFiles) -> 
							Arrays.stream(childrenFiles
						).map(fl -> 
							FileSystemItem.ofPath(fl.getAbsolutePath())
						).collect(
							Collectors.toCollection(
								ConcurrentHashMap::newKeySet
							)
						)).orElseGet(ConcurrentHashMap::newKeySet);
					}
				}
			}
		}
		return children;
	}
	
	public <C extends Set<FileSystemItem>> Set<FileSystemItem> getAllChildren(Predicate<FileSystemItem> filter, Supplier<C> setSupplier) {
		return getAllChildren().stream().filter(filter).collect(Collectors.toCollection(setSupplier));
	}
	
	public <C extends Set<FileSystemItem>> Set<FileSystemItem> getAllChildren(Predicate<FileSystemItem> filter) {
		return getAllChildren(filter, ConcurrentHashMap::newKeySet);
	}
	
	@SuppressWarnings("resource")
	public Set<FileSystemItem> getAllChildren() {
		if (allChildren != null) {
			return allChildren;
		} else if (isContainer()) {
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
					Set<FileSystemItem> allChildrenTemp = ConcurrentHashMap.newKeySet();
					zipInputStream.findAllAndConvert(
						() -> allChildrenTemp,
						zipEntryPredicate,
						zEntry -> {
							FileSystemItem fileSystemItem = FileSystemItem.ofPath(
								parentContainer.getAbsolutePath() + "/" +zEntry.getName()
							);
							fileSystemItem.absolutePath.setValue(
								parentContainer.getConventionedAbsolutePath() + retrieveConventionedRelativePath(
									fileSystemItem, zEntry, zEntry.getName()
								)
							);
							logDebug(fileSystemItem.getAbsolutePath());
							if (fileSystemItem.isArchive()) {
								Optional.ofNullable(
									fileSystemItem.getAllChildren()
								).ifPresent(allChildren ->
									allChildrenTemp.addAll(allChildren)
								);
							}
							return fileSystemItem;
						},
						zEntry -> true
					);
					allChildren = allChildrenTemp;
				};
			} else if (isFolder()) {
				logDebug("Retrieving all children of " + absolutePath.getKey());
				Set<FileSystemItem> children = getChildren();
				if (children != null) {
					Set<FileSystemItem> allChildrenTemp = ConcurrentHashMap.newKeySet();
					allChildrenTemp.addAll(children);
					children.forEach(
						child -> {
							Optional.ofNullable(child.getAllChildren()).map(allChildrenOfChild -> allChildrenTemp.addAll(allChildrenOfChild));
						}
					);
					allChildren = allChildrenTemp;
				}
			}
		}
		return allChildren;
	}
	
	private Set<FileSystemItem> getChildren(Supplier<IterableZipContainer> zipInputStreamSupplier, String itemToSearch) {
		try (IterableZipContainer zipInputStream = zipInputStreamSupplier.get()) {
			if (itemToSearch.contains(ZIP_PATH_SEPARATOR)) {
				String zipEntryNameOfNestedZipFile = itemToSearch.substring(0, itemToSearch.indexOf(ZIP_PATH_SEPARATOR));
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
						itemToSearch.replaceFirst(zipEntryNameOfNestedZipFile + ZIP_PATH_SEPARATOR, "")
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
	
	public boolean exists() {
		if (exists == null) {
			getConventionedAbsolutePath();
		}
		return exists;
	}
	
	public boolean isContainer() {
		String conventionedAbsolutePath = getConventionedAbsolutePath();
		return conventionedAbsolutePath.endsWith("/");
	}
	
	public boolean isCompressed() {
		String conventionedAbsolutePath = getConventionedAbsolutePath();
		return 
			(conventionedAbsolutePath.contains(ZIP_PATH_SEPARATOR) && 
				!conventionedAbsolutePath.endsWith(ZIP_PATH_SEPARATOR)) ||
			(conventionedAbsolutePath.contains(ZIP_PATH_SEPARATOR) && 
				conventionedAbsolutePath.endsWith(ZIP_PATH_SEPARATOR) && 
					conventionedAbsolutePath.indexOf(ZIP_PATH_SEPARATOR) != conventionedAbsolutePath.lastIndexOf(ZIP_PATH_SEPARATOR))
		;
	}
	
	public boolean isFile() {
		return !isContainer() || isArchive();
	}
	
	public boolean isArchive() {
		String conventionedAbsolutePath = getConventionedAbsolutePath();
		return conventionedAbsolutePath.endsWith(ZIP_PATH_SEPARATOR);
	}
	
	public boolean isFolder() {
		String conventionedAbsolutePath = getConventionedAbsolutePath();
		return conventionedAbsolutePath.endsWith("/") && !conventionedAbsolutePath.endsWith(ZIP_PATH_SEPARATOR);
	}
	
	public ByteBuffer toByteBuffer() {
		String absolutePath = getAbsolutePath();
		ByteBuffer resource = Cache.PATH_FOR_CONTENTS.getOrDefault(absolutePath, null); 
		if (resource != null) {
			return resource;
		}
		String conventionedAbsolutePath = getConventionedAbsolutePath();
		if (exists && !isFolder()) {
			if (isCompressed()) {
				String zipFilePath = conventionedAbsolutePath.substring(0, conventionedAbsolutePath.indexOf(ZIP_PATH_SEPARATOR));
				File file = new File(zipFilePath);
				if (file.exists()) {
					try (FileInputStream fIS = FileInputStream.create(file)) {
						return Cache.PATH_FOR_CONTENTS.getOrDefault(
							absolutePath,
							() ->
								retrieveBytes(zipFilePath, fIS, conventionedAbsolutePath.replaceFirst(zipFilePath + ZIP_PATH_SEPARATOR, ""))
						);
					}
				}
			} else {
				try (FileInputStream fIS = FileInputStream.create(conventionedAbsolutePath)) {
					return Cache.PATH_FOR_CONTENTS.getOrDefault(
						absolutePath, () ->
						fIS.toByteBuffer()
					);
				}
			}
		}
		return null;
	}
	
	
	private ByteBuffer retrieveBytes(String zipFilePath, InputStream inputStream, String itemToSearch) {
		try (IterableZipContainer zipInputStream = IterableZipContainer.create(zipFilePath, inputStream)) {
			if (itemToSearch.contains(ZIP_PATH_SEPARATOR)) {
				String zipEntryNameOfNestedZipFile = itemToSearch.substring(0, itemToSearch.indexOf(ZIP_PATH_SEPARATOR));
				IterableZipContainer.Entry zipEntry = zipInputStream.findFirst(
					zEntry -> zEntry.getName().equals(zipEntryNameOfNestedZipFile),
					zEntry -> false
				);
				itemToSearch = itemToSearch.replaceFirst(zipEntryNameOfNestedZipFile + ZIP_PATH_SEPARATOR, "");
				if (Strings.isNotEmpty(itemToSearch)) {
					try (InputStream iss = zipEntry.toInputStream()) {
						return retrieveBytes(zipEntry.getAbsolutePath(), zipEntry.toInputStream(), itemToSearch);
					} catch (IOException exc) {
						throw Throwables.toRuntimeException(exc);
					}
				} else {
					return zipEntry.toByteBuffer();
				}
			} else {
				final String iTS = itemToSearch;
				IterableZipContainer.Entry zipEntry = zipInputStream.findFirst(
					zEntry -> zEntry.getName().equals(iTS),
					zEntry -> false
				);
				return zipEntry.toByteBuffer();
			}
		}
	}
	
	public FileSystemItem copyTo(String folder) throws IOException {
		return copyTo(folder, null);
	}
	
	public FileSystemItem copyAllChildrenTo(String folder) throws IOException {
		return copyAllChildrenTo(folder, null);
	}
	
	public FileSystemItem copyAllChildrenTo(String folder, Predicate<FileSystemItem> filter) throws IOException {
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
	
	public FileSystemItem copyTo(String folder, Predicate<FileSystemItem> filter) throws IOException {
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
	
	public static void enableLog() {
		ManagedLogger.Repository.getInstance().enableLogging(FileSystemItem.class);
	}
	
	public static void disableLog() {
		ManagedLogger.Repository.getInstance().disableLogging(FileSystemItem.class);
	}
	
	@Override
	public String toString() {
		return absolutePath.getKey();
	}
}
