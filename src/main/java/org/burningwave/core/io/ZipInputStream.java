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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.zip.ZipException;

import org.burningwave.Throwables;
import org.burningwave.core.Cache;
import org.burningwave.core.Component;
import org.burningwave.core.function.ThrowingRunnable;
import org.burningwave.core.io.ZipInputStream.Entry.Detached;
import org.burningwave.core.jvm.LowLevelObjectsHandler.ByteBufferDelegate;

public class ZipInputStream extends java.util.zip.ZipInputStream implements Serializable, Component {

	private static final long serialVersionUID = -33538562818485472L;
		
	private ZipInputStream parent;
	private Entry.Attached currentZipEntry;
	private String absolutePath;
	private ByteBufferInputStream byteBufferInputStream;
	
	private ZipInputStream(String absolutePath, InputStream inputStream) {
		super(inputStream);
		this.absolutePath = absolutePath;
	}
	
	private ZipInputStream(File file) {
		this(file.getAbsolutePath(), FileInputStream.create(file));
	}
	
	public static ZipInputStream create(String absolutePath, InputStream inputStream) {
		ByteBufferInputStream iS = null;
		if (inputStream instanceof ByteBufferInputStream) {
			iS = new ByteBufferInputStream(((ByteBufferInputStream)inputStream).toByteBuffer());
		} else if (inputStream instanceof FileInputStream) {
			FileInputStream fileInputStream = (FileInputStream)inputStream;
			iS = new ByteBufferInputStream(fileInputStream.toByteBuffer());
		} else {
			iS = new ByteBufferInputStream(Streams.toByteBuffer(inputStream));
		}
		ZipInputStream zipInputStream = new ZipInputStream(absolutePath, iS);
		zipInputStream.byteBufferInputStream = iS;
		return zipInputStream;
	}
	
	public static ZipInputStream create(FileInputStream file) {
		return create(file.getAbsolutePath(), file);
	}
	
	public static ZipInputStream create(File file) {
		return create(file.getAbsolutePath(), FileInputStream.create(file));
	}	
	
	public static ZipInputStream create(String name, ByteBuffer zipInputStreamAsBytes) {
		ByteBufferInputStream iS = new ByteBufferInputStream(zipInputStreamAsBytes);
		ZipInputStream zipInputStream = new ZipInputStream(name, iS);
		zipInputStream.byteBufferInputStream = iS;
		return zipInputStream;
	}
	
	public static ZipInputStream create(ZipInputStream.Entry.Detached zipEntry) {
		ZipInputStream zipInputStream = create(zipEntry.getAbsolutePath(), zipEntry.toByteBuffer());
		zipInputStream.parent = zipEntry.getZipInputStream();
		return zipInputStream;
	}
	
	
	public static ZipInputStream create(ZipInputStream.Entry zipEntry) {
		ZipInputStream zipInputStream = create(zipEntry.getAbsolutePath(), zipEntry.toByteBuffer());
		zipInputStream.parent = zipEntry.getZipInputStream();
		return zipInputStream;
	}
	
	public ZipInputStream duplicate() {
		ZipInputStream zipInputStream = create(absolutePath, toByteBuffer());
		if (parent != null) {
			zipInputStream.parent = parent.duplicate();
		}
		return zipInputStream;
	}
	
	public String getAbsolutePath() {
		return absolutePath;
	}
	
	public ByteBuffer toByteBuffer() {
		return byteBufferInputStream.toByteBuffer();
	}

	public byte[] toByteArray() {
		
		return Streams.toByteArray(toByteBuffer());
	}

	@Override
    protected Entry.Attached createZipEntry(String name) {
    	return new Entry.Attached(name, this);
    }
	
	public Entry.Attached getNextEntry() {
		return getNextEntry((zEntry) -> false);
	}
	
	public Entry.Attached getNextEntry(Predicate<Entry.Attached> loadZipEntryData) {
		ThrowingRunnable.run(() -> {
			try {
				currentZipEntry = (Entry.Attached)super.getNextEntry();
			} catch (ZipException exc) {
				String message = exc.getMessage();
				logWarn("Could not open zipEntry of {}: {}", absolutePath, message);
			}
		});
		if (currentZipEntry != null && loadZipEntryData.test(currentZipEntry)) {
			currentZipEntry.loadContent();
		}
		return currentZipEntry;
	}		
	
	public Detached getNextEntryAsDetached() {
		return getNextEntryAsDetached(zEntry -> false);
	}
	
	public Detached getNextEntryAsDetached(Predicate<Entry.Attached> loadZipEntryData) {
		return Optional.ofNullable(
			getNextEntry(loadZipEntryData)).map(zipEntry ->	zipEntry.convert()
		).orElseGet(
			() -> null
		);
	}
	
	public <T> Set<T> findAllAndConvert(
		Predicate<Entry.Attached> zipEntryPredicate, 
		Function<Entry.Attached, T> tSupplier,
		Predicate<Entry.Attached> loadZipEntryData
	) {
		return findAllAndConvert(ConcurrentHashMap::newKeySet, zipEntryPredicate, tSupplier, loadZipEntryData);
	}
	
	public <T> Set<T> findAllAndConvert(
		Supplier<Set<T>> supplier, 
		Predicate<Entry.Attached> zipEntryPredicate, 
		Function<Entry.Attached, T> tSupplier,
		Predicate<Entry.Attached> loadZipEntryData
	) {
		Set<T> collection = supplier.get();
		if (currentZipEntry != null && zipEntryPredicate.test(currentZipEntry)) {
			if (loadZipEntryData.test(currentZipEntry)) {
				currentZipEntry.loadContent();
			}
			collection.add(tSupplier.apply(currentZipEntry));
		}
		while(getNextEntry((zEntry) -> false) != null) {
			if (zipEntryPredicate.test(currentZipEntry)) {
				if (loadZipEntryData.test(currentZipEntry)) {
					currentZipEntry.loadContent();
				}
				collection.add(tSupplier.apply(currentZipEntry));
			}
		}
		return collection;
	}
	
	public <T> T findFirstAndConvert(
		Predicate<Entry.Attached> zipEntryPredicate, 
		Function<Entry.Attached, T> tSupplier,
		Predicate<Entry.Attached> loadZipEntryData
	) {
		if (currentZipEntry != null && zipEntryPredicate.test(currentZipEntry)) {
			if (loadZipEntryData.test(currentZipEntry)) {
				currentZipEntry.loadContent();
			}
			return tSupplier.apply(currentZipEntry);
		}
		while(getNextEntry(zEntry -> false) != null) {
			if (zipEntryPredicate.test(currentZipEntry)) {
				if (loadZipEntryData.test(currentZipEntry)) {
					currentZipEntry.loadContent();
				}
				
				T toRet = tSupplier.apply(currentZipEntry);
				closeEntry();
				return toRet;
			}
		}
		return null;
	}
	
	public <T> T findOneAndConvert(Predicate<Entry.Attached> zipEntryPredicate, Function<Entry.Attached, T> tSupplier, Predicate<Entry.Attached> loadZipEntryData) {
		Set<T> entriesFound = findAllAndConvert(
			zipEntryPredicate,
			tSupplier, 
			loadZipEntryData
		);
		if (entriesFound.size() > 1) {
			throw Throwables.toRuntimeException("Found more than one zip entry for predicate " + zipEntryPredicate);
		}
		return entriesFound.stream().findFirst().orElseGet(() -> null);
	}
	
	public Entry.Detached findOneAndConvert(Predicate<Entry.Attached> zipEntryPredicate, Predicate<Entry.Attached> loadZipEntryData) {
		return findOneAndConvert(
			zipEntryPredicate,
			zEntry -> new Entry.Detached(
				zEntry
			),
			loadZipEntryData		
		);
	}
	
	public Entry.Detached findFirstAndConvert(Predicate<Entry.Attached> zipEntryPredicate, Predicate<Entry.Attached> loadZipEntryData) {
		return findFirstAndConvert(
			zipEntryPredicate,
			zEntry -> new Entry.Detached(
				zEntry
			),
			loadZipEntryData		
		);
	}
	
	public Set<Entry.Detached> findAllAndConvert(Predicate<Entry.Attached> zipEntryPredicate, Predicate<Entry.Attached> loadZipEntryData) {
		return findAllAndConvert(ConcurrentHashMap::newKeySet, zipEntryPredicate, loadZipEntryData);
	}
	
	public Set<Entry.Detached> findAllAndConvert(Supplier<Set<Entry.Detached>> setSupplier, Predicate<Entry.Attached> zipEntryPredicate, Predicate<Entry.Attached> loadZipEntryData) {
		return findAllAndConvert(
			setSupplier,
			zipEntryPredicate,
			zEntry -> new Entry.Attached.Detached(
				zEntry
			),
			loadZipEntryData		
		);
	}
	
	public Entry.Attached getCurrentZipEntry() {
		return currentZipEntry;
	}
	
	public Detached convertCurrentZipEntry() {
		return currentZipEntry.convert();
	}
	
	
	@Override
	public void closeEntry() {
		try {
			super.closeEntry();
		} catch (IOException exc) {
			logWarn("Exception occurred while closing zipEntry {}: {}", Optional.ofNullable(currentZipEntry).map((zipEntry) -> zipEntry.getAbsolutePath()).orElseGet(() -> "null"), exc.getMessage());
		}
		if (currentZipEntry != null) {
			currentZipEntry.close();
			currentZipEntry = null;
		}
	}
	
	@Override
	public void close() {
		closeEntry();
		parent = null;
		absolutePath = null;
		ThrowingRunnable.run(() -> super.close());
		this.byteBufferInputStream = null;
	}
		
	public static interface Entry {
		
		public ZipInputStream getZipInputStream();
				
		public String getName();
		
		public String getAbsolutePath();
		
		public boolean isDirectory();
		
		public ByteBuffer toByteBuffer();
		
		default public byte[] toByteArray() {
			return Streams.toByteArray(toByteBuffer());
		}
		
		default public boolean isArchive() {
			return Streams.isArchive(toByteBuffer());
		}
		
		default public InputStream toInputStream() {
			return new ByteBufferInputStream(toByteBuffer());
		}		
	
		static class Attached extends java.util.zip.ZipEntry implements Serializable, Component, Entry {
			
			private static final long serialVersionUID = -3679843114872023810L;
			private ZipInputStream zipInputStream;
	
			public Attached(Entry.Attached e, ZipInputStream zIS) {
				super(e);
				this.zipInputStream = zIS;
			}
			
			public Attached(String name, ZipInputStream zIS) {
				super(name);
				this.zipInputStream = zIS;
			}
			
			public ZipInputStream getZipInputStream() {
				return zipInputStream;
			}
			
			public String getAbsolutePath() {
				String name = getName();
				return zipInputStream.getAbsolutePath() + "/" + (name.endsWith("/") ? name.substring(0, name.length() -1) : name);
			}
			
			private ByteBufferOutputStream createDataBytesContainer() {
				int currEntrySize = (int)super.getSize();
				if (currEntrySize != -1) {
					return new ByteBufferOutputStream(currEntrySize);
				} else {
					return new ByteBufferOutputStream();
				}
			}
			
			@Override
			public long getSize() {
				long size = super.getSize();
				if (size < 0) {
					size = ByteBufferDelegate.limit(toByteBuffer());
				}
				return size;
			}		
			
			
			private ByteBuffer loadContent() {
				return Cache.PATH_FOR_CONTENTS.getOrDefault(
					getAbsolutePath(), () -> {
						if (zipInputStream.currentZipEntry != this) {
							throw Throwables.toRuntimeException("Entry.Impl and his ZipInputStream are not aligned");
						}
						try (ByteBufferOutputStream bBOS = createDataBytesContainer()) {
							Streams.copy(zipInputStream, bBOS);
						    return bBOS.toByteBuffer();
						}
					}
				);
				
			}		
	
			public ByteBuffer toByteBuffer() {
				return loadContent();
			}
			
			public Detached convert() {
				return new Entry.Detached(
					this
				);
			}		
			
			public void unzipToFolder(File folder) {
				File destinationFilePath = new File(folder.getAbsolutePath(), this.getName());
				destinationFilePath.getParentFile().mkdirs();
				if (!this.isDirectory()) {
					ThrowingRunnable.run(() -> {
						try (BufferedInputStream bis = new BufferedInputStream(this.toInputStream())) {
							int byteTransferred = 0;
							byte buffer[] = new byte[Streams.DEFAULT_BUFFER_SIZE];
							try (
								FileOutputStream fos = FileOutputStream.create(destinationFilePath);
								BufferedOutputStream bos = new BufferedOutputStream(fos, Streams.DEFAULT_BUFFER_SIZE)
							) {
								while ((byteTransferred = bis.read(buffer, 0, Streams.DEFAULT_BUFFER_SIZE)) != -1) {
									bos.write(buffer, 0, byteTransferred);
								}
								bos.flush();
							}
						}
					});
				}
			}
			
			@Override
			public void close() {
				zipInputStream = null;
			}
		}
	
		public static class Detached implements Component, Entry {
			private String name;
			private String absolutePath;
			private Boolean isDirectory;
			private ZipInputStream zipInputStream;
			
			Detached(Entry.Attached zipEntry) {
				this.name = zipEntry.getName();
				this.absolutePath = zipEntry.getAbsolutePath();
				this.isDirectory = zipEntry.isDirectory();
				this.zipInputStream = zipEntry.getZipInputStream().duplicate();
				
			}
			
			public ZipInputStream getZipInputStream() {
				return zipInputStream.duplicate();
			}
	
			public ByteBuffer toByteBuffer() {
				return Cache.PATH_FOR_CONTENTS.getOrDefault(absolutePath, () -> {
					try (ZipInputStream zipInputStream = getZipInputStream()) {
						ByteBuffer content = zipInputStream.findFirstAndConvert((entry) -> 
							entry.getName().equals(getName()), zEntry -> 
							zEntry.toByteBuffer(), zEntry -> true
						);
						return Streams.shareContent(content);
					}
				});			
			}
			
			public String getName() {
				return name;
			}
			public String getAbsolutePath() {
				return absolutePath;
			}
			
			public boolean isDirectory() {
				return isDirectory;
			}
			
			@Override
			public void close() {
				name = null;
				absolutePath = null;
				isDirectory = null;
				zipInputStream.close();
				zipInputStream = null;
			}
		}
	}	
}