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
import static org.burningwave.core.assembler.StaticComponentContainer.FileSystemHelper;
import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggersRepository;
import static org.burningwave.core.assembler.StaticComponentContainer.Paths;
import static org.burningwave.core.assembler.StaticComponentContainer.Streams;
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;

class ZipFile implements IterableZipContainer {
	String absolutePath;
	String conventionedAbsolutePath;
	IterableZipContainer parent;
	IterableZipContainer.Entry currentZipEntry;
	Iterator<Entry> entriesIterator;
	Collection<Entry> entries;
	Runnable temporaryFileDeleter;
	java.util.zip.ZipFile originalZipFile;
	
	ZipFile(String absolutePath, ByteBuffer content) {
		this.absolutePath = Paths.clean(absolutePath);
		entries = ConcurrentHashMap.newKeySet();
		try (java.util.zip.ZipFile zipFile = retrieveFile(absolutePath, content)) {
			Enumeration<? extends ZipEntry> entriesIterator = zipFile.entries();
			while (entriesIterator.hasMoreElements()) {
				ZipEntry zipEntry = entriesIterator.nextElement();
				entries.add(
					new Entry(
						this, 
						zipEntry.getName(), () -> {
							try (
								InputStream zipEntryIS = retrieveFile(absolutePath, content).getInputStream(zipEntry);
								ByteBufferOutputStream bBOS = new ByteBufferOutputStream()
							){
								 Streams.copy(zipEntryIS, bBOS);
								 return bBOS.toByteBuffer();
							} catch (Throwable exc) {
								ManagedLoggersRepository.logError(this.getClass(), "Could not load content of " + zipEntry.getName() + " of " + getAbsolutePath(), exc);
								return null;
							}
						}
					)
				);
			}
			originalZipFile = null;
		} catch (IOException exc) {
			throw Throwables.toRuntimeException(exc);
		}
		entriesIterator = entries.iterator();
	}

	private java.util.zip.ZipFile retrieveFile(String absolutePath, ByteBuffer content) {
		java.util.zip.ZipFile originalZipFile = this.originalZipFile;
		if (originalZipFile == null) {
			synchronized (this) {
				if ((originalZipFile = this.originalZipFile) == null) {
					File file = new File(absolutePath);
					if (!file.exists()) {
						File temporaryFolder = FileSystemHelper.getOrCreateTemporaryFolder(FileSystemHelper.toString());
						String fileAbsolutePath = null;
						if (absolutePath.chars().filter(ch -> ch == '/').count() > 1) {
							fileAbsolutePath = absolutePath.substring(absolutePath.indexOf("/")).replaceFirst("\\/", "\\[").replace("/", "][");
							fileAbsolutePath = fileAbsolutePath.substring(0, fileAbsolutePath.lastIndexOf("][") + 1) + fileAbsolutePath.substring(fileAbsolutePath.lastIndexOf("][") +2);
						} else {
							fileAbsolutePath = absolutePath.substring(absolutePath.indexOf("/") + 1);
						}
						fileAbsolutePath = Paths.clean(temporaryFolder.getAbsolutePath()) + "/" + fileAbsolutePath;
						file = new File(fileAbsolutePath);
						if (!file.exists()) {
							FileSystemItem fileSystemItem = Streams.store(fileAbsolutePath, content);
							temporaryFileDeleter = () -> {
								//String temporaryFileAbsolutePath = fileSystemItem.getAbsolutePath();
								FileSystemHelper.delete(fileSystemItem.getAbsolutePath());
								fileSystemItem.destroy();
							};
						}
					}
					try {
						originalZipFile = this.originalZipFile = new java.util.zip.ZipFile(file);
					} catch (IOException exc) {
						throw Throwables.toRuntimeException(exc);
					}
				}
			}
		}
		return originalZipFile;
	}
	
	private ZipFile(String absolutePath, Collection<Entry> entries) {
		this.absolutePath = absolutePath;
		this.entries = entries;
		this.entriesIterator = entries.iterator();
	}
	
	@Override
	public IterableZipContainer duplicate() {
		return new ZipFile(absolutePath, entries);
	}
	
	@Override
	public String getAbsolutePath() {
		return absolutePath;
	}
	

	@Override
	public String getConventionedAbsolutePath() {
		if (conventionedAbsolutePath == null) {
			synchronized (this) {
				if (parent != null) {
					conventionedAbsolutePath = parent.getConventionedAbsolutePath() + absolutePath.replace(parent.getAbsolutePath() + "/", "");
				} else {
					FileSystemItem zipFis = FileSystemItem.ofPath(absolutePath);
					if (zipFis.getParentContainer().isArchive()) {
						parent = IterableZipContainer.create(zipFis.getParentContainer().getAbsolutePath());
						return getConventionedAbsolutePath();
					} else {
						conventionedAbsolutePath = absolutePath;
					}
				}
				conventionedAbsolutePath += IterableZipContainer.ZIP_PATH_SEPARATOR;
			}
		}
		return conventionedAbsolutePath;
	}
	
	@Override
	public IterableZipContainer getParent() {
		if (conventionedAbsolutePath == null) {
			getConventionedAbsolutePath();
		}
		return parent;
	}

	@Override
	public ByteBuffer toByteBuffer() {
		return Cache.pathForContents.get(absolutePath);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <Z extends IterableZipContainer.Entry> Z getNextEntry() {
		return (Z) (currentZipEntry = entriesIterator.hasNext()? entriesIterator.next() : null);
	}

	@Override
	public Entry getNextEntry(Predicate<IterableZipContainer.Entry> loadZipEntryData) {
		return (Entry)(currentZipEntry = entriesIterator.hasNext()? entriesIterator.next() : null);
	}

	@Override
	public IterableZipContainer.Entry getCurrentZipEntry() {
		return currentZipEntry;
	}

	@Override
	public Function<IterableZipContainer.Entry, IterableZipContainer.Entry> getEntrySupplier() {
		return (entry) -> entry;
	}

	@Override
	public void closeEntry() {
		currentZipEntry = null;
	}
	
	@Override
	public void close() {
		closeEntry();
		java.util.zip.ZipFile originalZipFile = this.originalZipFile;
		if (originalZipFile != null) {
			try {
				originalZipFile.close();
			} catch (IOException exc) {
				logError("Exception while closing " + getAbsolutePath(), exc);
			}
		}
		this.absolutePath = null;
		this.entriesIterator = null;
		this.entries = null;
	}
	
	@Override
	public void destroy(boolean removeFromCache) {
		if (removeFromCache) {
			IterableZipContainer.super.destroy(removeFromCache);
		}		
		for (Entry entry : entries) {
			entry.destroy();
		}
		entries.clear();
		close();
		Runnable temporaryFileDeleter = this.temporaryFileDeleter;
		if (temporaryFileDeleter != null) {
			this.temporaryFileDeleter = null;
			temporaryFileDeleter.run();			
		}
	}
	
	public static class Entry implements IterableZipContainer.Entry {
		private ZipFile zipMemoryContainer;
		private String name;
		private String absolutePath;
		private Supplier<ByteBuffer> zipEntryContentSupplier;

		public Entry(ZipFile zipMemoryContainer, String entryName, Supplier<ByteBuffer> zipEntryContentSupplier) {
			this.zipMemoryContainer = zipMemoryContainer;
			this.name = entryName;
			this.absolutePath = Paths.clean(zipMemoryContainer.getAbsolutePath() + "/" + entryName);
			this.zipEntryContentSupplier = zipEntryContentSupplier;
		}

		@Override
		@SuppressWarnings("unchecked")
		public <C extends IterableZipContainer> C getParentContainer() {
			return (C) zipMemoryContainer;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public String getAbsolutePath() {
			return absolutePath;
		}

		@Override
		public boolean isDirectory() {
			return name.endsWith("/");
		}

		@Override
		public ByteBuffer toByteBuffer() {
			return Cache.pathForContents.getOrUploadIfAbsent(getAbsolutePath(), zipEntryContentSupplier);
		}
		
		public void destroy() {
			this.absolutePath = null;
			this.name = null;
			this.zipEntryContentSupplier = null;
			this.zipMemoryContainer = null;
		}
	}
}
