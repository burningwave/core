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
	
	ZipFile(String absolutePath, ByteBuffer content) {
		this.absolutePath = Paths.clean(absolutePath);
		File file = new File(absolutePath);
		if (!file.exists()) {
			File temporaryFolder = FileSystemHelper.getOrCreateTemporaryFolder(toString());
			FileSystemItem fileSystemItem = Streams.store(temporaryFolder.getAbsolutePath() + "/" + file.getName(), content);
			Cache.pathForContents.getOrUploadIfAbsent(absolutePath, () -> fileSystemItem.toByteBuffer());
			file = new File(fileSystemItem.getAbsolutePath());
		}
		final File fileRef = file;
		entries = ConcurrentHashMap.newKeySet();
		try (java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(fileRef)) {
			Enumeration<? extends ZipEntry> entriesIterator = zipFile.entries();
			while (entriesIterator.hasMoreElements()) {
				ZipEntry zipEntry = entriesIterator.nextElement();
				entries.add(
					new Entry(
						this, 
						zipEntry.getName(), () -> {
							try (java.util.zip.ZipFile zipFileRef = new java.util.zip.ZipFile(fileRef); InputStream zipEntryIS = zipFileRef.getInputStream(zipEntry); ByteBufferOutputStream bBOS = new ByteBufferOutputStream()){
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
		} catch (IOException exc) {
			Throwables.toRuntimeException(exc);
		}
		entriesIterator = entries.iterator();
	}
	
	private ZipFile(String absolutePath, Collection<Entry> entries) {
		this.absolutePath = absolutePath;
		this.entries = entries;
		this.entriesIterator = entries.iterator();
	}
	
	@Override
	public IterableZipContainer duplicate() {
		IterableZipContainer zipContainer = new ZipFile(absolutePath, entries);
		if (getParent() != null) {
			zipContainer.setParent(getParent().duplicate());
		}
		return zipContainer;
	}
	
	@Override
	public String getAbsolutePath() {
		return absolutePath;
	}
	

	@Override
	public String getConventionedAbsolutePath() {
		if (conventionedAbsolutePath == null) {
			if (parent != null) {
				conventionedAbsolutePath = parent.getConventionedAbsolutePath() + absolutePath.replace(parent.getAbsolutePath() + "/", "");
			} else {
				conventionedAbsolutePath = absolutePath;
			}
			conventionedAbsolutePath += IterableZipContainer.ZIP_PATH_SEPARATOR;
		}
		if (conventionedAbsolutePath == null) {
			conventionedAbsolutePath = null;
		}
		return conventionedAbsolutePath;
	}
	
	@Override
	public IterableZipContainer getParent() {
		return parent;
	}

	@Override
	public void setParent(IterableZipContainer parent) {
		this.parent = parent;		
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
		this.absolutePath = null;
		this.entriesIterator = null;
		this.entries = null;
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
	}
}
