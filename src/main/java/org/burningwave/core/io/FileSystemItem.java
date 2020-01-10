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
import java.util.stream.Collectors;

import org.burningwave.Throwables;
import org.burningwave.core.Component;
import org.burningwave.core.common.Streams;
import org.burningwave.core.common.Strings;
import org.burningwave.core.io.ZipInputStream.Entry;

public class FileSystemItem implements Component {
	private final static String ZIP_PATH_SEPARATOR = "//"; 
	private final static Map<String, FileSystemItem> FILE_SYSTEM_ITEMS = new ConcurrentHashMap<>(); 
	
	private Map.Entry<String, String> absolutePath;
	private FileSystemItem parent;
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
		realAbsolutePath = Strings.Paths.clean(realAbsolutePath);
		FileSystemItem fileSystemItemReader = FILE_SYSTEM_ITEMS.get(realAbsolutePath);
		if (fileSystemItemReader == null) {
			synchronized(realAbsolutePath) {
				if ((fileSystemItemReader = FILE_SYSTEM_ITEMS.get(realAbsolutePath)) == null) {
					fileSystemItemReader = new FileSystemItem(realAbsolutePath, conventionedAbsolutePath);
					if (Strings.isNotEmpty(realAbsolutePath)) {
						FILE_SYSTEM_ITEMS.put(realAbsolutePath, fileSystemItemReader);
					}
				}
			}
		}
		return fileSystemItemReader;
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
					logWarn("Exception occurred while calling isArchive on file {}", fileName);
					return fileName;
				} 
			}		
		} else {
			String pathToTest = realAbsolutePath.substring(0, realAbsolutePath.lastIndexOf("/"));
			relativePath = realAbsolutePath.replace(pathToTest + "/", "") + (relativePath.isEmpty()? "" : "/") + relativePath;
			return retrieveConventionedAbsolutePath(pathToTest, relativePath);
		}
	}


	private String retrieveConventionedRelativePath(ByteBuffer zipInputStreamAsBytes, String zipInputStreamName, String relativePath1) {
		try (ZipInputStream zIS = new ZipInputStream(zipInputStreamName, new ByteBufferInputStream(zipInputStreamAsBytes))){
			Predicate<Entry> zipEntryPredicate = zEntry -> zEntry.getName().equals(relativePath1) || zEntry.getName().equals(relativePath1 + "/");
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
			Set<ZipInputStream.Entry.Wrapper> zipEntryWrappers = zIS.findAllAndConvert(
				zipEntryPredicate, true
			);
			if (!zipEntryWrappers.isEmpty()) {
				ZipInputStream.Entry.Wrapper zipEntryWrapper = Collections.max(
					zipEntryWrappers, Comparator.comparing(zipEntryW -> zipEntryW.getName().split("/").length)
				);
				String relativePath2 = zipEntryWrapper.getName();
				if (relativePath2.endsWith("/")) {
					relativePath2 = relativePath2.substring(0, relativePath2.length() - 1);
				}
				relativePath2 = relativePath1.substring(relativePath2.length());
				if (relativePath2.startsWith("/")) {
					relativePath2 = relativePath2.replaceFirst("\\/", "");
				}
				if (relativePath2.isEmpty()) {
					return zipEntryWrapper.getName() + (!zipEntryWrapper.isDirectory() && Streams.isArchive(zipEntryWrapper.toByteBuffer()) ? ZIP_PATH_SEPARATOR : "");
				} else {
					return zipEntryWrapper.getName() + ZIP_PATH_SEPARATOR + retrieveConventionedRelativePath(
						zipEntryWrapper.toByteBuffer(), zipEntryWrapper.getAbsolutePath(), relativePath2
					);
				}			
			} else {
				throw new FileSystemItemNotFoundException("Absolute path \"" + absolutePath.getKey() + "\" not exists");
			}
		}
	}
	
	public String getAbsolutePath() {
		return absolutePath.getKey();
	}
	
	public String getName() {
		FileSystemItem parent = getParent();
		return getAbsolutePath().replace(parent.getAbsolutePath() + "/", "");
	}
	
	public InputStream toInputStream() {
		return new ByteBufferInputStream(toByteBuffer());
	}
	
	public FileSystemItem getParent() {
		if (parent != null) {
			return parent;
		} else {
			String conventionedPath = absolutePath.getValue();
			if (conventionedPath != null) {
				if (conventionedPath.endsWith("/")) {
					int offset = -1;
					if (conventionedPath.endsWith("//")) {
						offset = -2;
					}
					conventionedPath = conventionedPath.substring(0, conventionedPath.length() + offset);	
				}
				conventionedPath = conventionedPath.substring(0, conventionedPath.lastIndexOf("/")) + "/";
				return FileSystemItem.ofPath(
					absolutePath.getKey().substring(0, absolutePath.getKey().lastIndexOf("/")),
					conventionedPath
				);
			} else {
				return FileSystemItem.ofPath(
					absolutePath.getKey().substring(0, absolutePath.getKey().lastIndexOf("/"))
				);
			}
		}
	}
	
	public Set<FileSystemItem> getChildren() {
		if (children != null) {
			return children;
		} else {
			String conventionedAbsolutePath = getConventionedAbsolutePath();
			if (isContainer()) {
				if (isCompressed() || isArchive()) {
					String zipFilePath = conventionedAbsolutePath.substring(0, conventionedAbsolutePath.indexOf(ZIP_PATH_SEPARATOR));
					File file = new File(zipFilePath);
					if (file.exists()) {
						try (FileInputStream fIS = FileInputStream.create(file)) {
							children = getChildren(zipFilePath, fIS, conventionedAbsolutePath.replaceFirst(zipFilePath + ZIP_PATH_SEPARATOR, ""));
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
	
	public Set<FileSystemItem> getAllChildren() {
		if (allChildren != null) {
			return allChildren;
		} else if (isContainer()) {
			logDebug("Retrieving all children of " + absolutePath.getKey());
			Set<FileSystemItem> children = getChildren();
			if (children != null) {
				Set<FileSystemItem> allChildrenTemp = ConcurrentHashMap.newKeySet();
				allChildrenTemp.addAll(children);
				children.forEach(
					child -> Optional.ofNullable(child.getAllChildren()).map(allChildrenOfChild -> allChildrenTemp.addAll(allChildrenOfChild))
				);
				allChildren = allChildrenTemp;
			}
			
		}
		return allChildren;
	}
	
	private Set<FileSystemItem> getChildren(String zipFilePath, InputStream inputStream, String itemToSearch) {
		try (ZipInputStream zipInputStream = new ZipInputStream(zipFilePath, inputStream)) {
			if (itemToSearch.contains(ZIP_PATH_SEPARATOR)) {
				String zipEntryNameOfNestedZipFile = itemToSearch.substring(0, itemToSearch.indexOf(ZIP_PATH_SEPARATOR));
				ZipInputStream.Entry.Wrapper zipEntryWrapper = zipInputStream.findFirstAndConvert(
					zEntry -> zEntry.getName().equals(zipEntryNameOfNestedZipFile),
					true
				);
				if (zipEntryWrapper == null) {
					return null;
				}
				try (InputStream iss = zipEntryWrapper.toInputStream()) {
					return getChildren(
						zipEntryWrapper.getAbsolutePath(),
						zipEntryWrapper.toInputStream(), 
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
						return FileSystemItem.ofPath(zEntry.getAbsolutePath());
					},
					false
				);
				return toRet;
			}
		}
	}

	private String getConventionedAbsolutePath() {
		if (absolutePath.getValue() == null && exists == null) {
			absolutePath.setValue(retrieveConventionedAbsolutePath(absolutePath.getKey(), ""));
			if (!exists) {
				FILE_SYSTEM_ITEMS.remove(absolutePath.getKey());
			}
		}
		return absolutePath.getValue();
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
		String conventionedAbsolutePath = getConventionedAbsolutePath();
		if (!isFolder()) {
			if (isCompressed()) {
				String zipFilePath = conventionedAbsolutePath.substring(0, conventionedAbsolutePath.indexOf(ZIP_PATH_SEPARATOR));
				File file = new File(zipFilePath);
				if (file.exists()) {
					try (FileInputStream fIS = FileInputStream.create(file)) {
						return retrieveBytes(zipFilePath, fIS, conventionedAbsolutePath.replaceFirst(zipFilePath + ZIP_PATH_SEPARATOR, ""));
					}
				}
			} else {
				try (FileInputStream fIS = FileInputStream.create(conventionedAbsolutePath)) {
					return fIS.toByteBuffer();
				}
			}
		}
		return null;
	}

	private ByteBuffer retrieveBytes(String zipFilePath, InputStream inputStream, String itemToSearch) {
		try (ZipInputStream zipInputStream = new ZipInputStream(zipFilePath, inputStream)) {
			if (itemToSearch.contains(ZIP_PATH_SEPARATOR)) {
				String zipEntryNameOfNestedZipFile = itemToSearch.substring(0, itemToSearch.indexOf(ZIP_PATH_SEPARATOR));
				ZipInputStream.Entry.Wrapper zipEntry = zipInputStream.findFirstAndConvert(
					zEntry -> zEntry.getName().equals(zipEntryNameOfNestedZipFile),
					true
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
				ZipInputStream.Entry.Wrapper zipEntry = zipInputStream.findFirstAndConvert(
					zEntry -> zEntry.getName().equals(iTS),
					true
				);
				return zipEntry.toByteBuffer();
			}
		}
	}
	
	public FileSystemItem copyToFolder(String folder) throws IOException {
		FileSystemItem destinationFolder = null;
		File file = new File(folder);
		if (!file.exists()) {
			file.mkdirs();
		} else if (!file.isDirectory()) {
			file.setWritable(true);
			file.delete();
			file.mkdirs();
		}
		if (isFile()) {
			file = new File(folder, getName());
			if (file.exists()) {
				file.delete();
			}
			try(InputStream inputStream = toInputStream(); FileOutputStream fileOutputStream = FileOutputStream.create(file, true)) {
				Streams.copy(toInputStream(), fileOutputStream);
			}
			logDebug("Copied file to " + file.getAbsolutePath());
			destinationFolder = FileSystemItem.ofPath(file.getAbsolutePath());
		} else {
			file = new File(folder + "/" + getName());
			if (file.exists()) {
				file.delete();
			}
			file.mkdirs();
			for (FileSystemItem fileSystemItem : getChildren()) {
				fileSystemItem.copyToFolder(file.getAbsolutePath());
			}
			logDebug("Copied folder to " + file.getAbsolutePath());
			destinationFolder = FileSystemItem.ofPath(file.getAbsolutePath());
		}
		return destinationFolder;
	}
	
	public static void enableLog() {
		FileSystemItem.ofPath("/").enableLogging();
	}
	
	public static void disableLog() {
		FileSystemItem.ofPath("/").disableLogging();
	}
	
	@Override
	public String toString() {
		return absolutePath.getKey();
	}
}
