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
import java.util.zip.ZipException;

import org.burningwave.Throwables;
import org.burningwave.core.Component;
import org.burningwave.core.common.Streams;
import org.burningwave.core.function.ThrowingRunnable;
import org.burningwave.core.io.ZipInputStream.Entry.Detached;

public class ZipInputStream extends java.util.zip.ZipInputStream implements Serializable, Component {

	private static final long serialVersionUID = -33538562818485472L;
		
	private ZipInputStream parent;
	private Entry currentZipEntry;
	private String name;
	private String path;
	private ByteBufferInputStream byteBufferInputStream;
	
	private ZipInputStream(String name, InputStream inputStream) {
		super(inputStream);
		this.name = name;
	}
	
	private ZipInputStream(File file) {
		this(file.getAbsolutePath(), FileInputStream.create(file));
	}
	
	public static ZipInputStream create(String name, InputStream inputStream) {
		ByteBufferInputStream iS = null;
		if (inputStream instanceof ByteBufferInputStream) {
			iS = new ByteBufferInputStream(((ByteBufferInputStream)inputStream).toByteBuffer());
		} else {
			iS = new ByteBufferInputStream(Streams.toByteBuffer(inputStream));
		}
		ZipInputStream zipInputStream = new ZipInputStream(name, iS);
		zipInputStream.byteBufferInputStream = iS;
		zipInputStream.init();
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
		zipInputStream.init();
		return zipInputStream;
	}
	
	public static ZipInputStream create(ZipInputStream.Entry.Detached zipEntry) {
		ZipInputStream zipInputStream = create(zipEntry.getName(), zipEntry.toInputStream());
		zipInputStream.parent = zipEntry.getZipInputStream();
		zipInputStream.init();
		return zipInputStream;
	}
	
	
	public static ZipInputStream create(ZipInputStream.Entry zipEntry) {
		ZipInputStream zipInputStream = create(zipEntry.getName(), zipEntry.toInputStream());
		zipInputStream.parent = zipEntry.getZipInputStream();
		zipInputStream.init();
		return zipInputStream;
	}
	
	private void init() {
		path = name.replace("\\", "/");
		if (parent != null) {
			path = parent.getAbsolutePath() + "/" + path;
		}
	}
	
	public ZipInputStream duplicate() {
		ZipInputStream zipInputStream = create(getName(), toByteBuffer());
		if (parent != null) {
			zipInputStream.parent = parent.duplicate();
		}
		return zipInputStream;
	}
	
	public String getName() {
		return this.name;
	}
	
	public String getAbsolutePath() {
		return path;
	}
	
	public ByteBuffer toByteBuffer() {
		return byteBufferInputStream.toByteBuffer();
	}

	public byte[] toByteArray() {
		return Streams.toByteArray(toByteBuffer());
	}

	@Override
    protected Entry createZipEntry(String name) {
    	return new Entry(name, this);
    }
	
	public Entry getNextEntry() {
		return getNextEntry(false);
	}
	
	public Entry getNextEntry(boolean loadZipEntryData) {
		ThrowingRunnable.run(() -> {
				try {
					currentZipEntry = (Entry)super.getNextEntry();
				} catch (ZipException exc) {
					String message = exc.getMessage();
					logWarn("Could not open zipEntry of {}: {}", getName(), message);
				}
			}
		);
		if (loadZipEntryData) {
			currentZipEntry.loadContent();
		}
		return currentZipEntry;
	}		
	
	public <T> Set<T> findAllAndConvert(
		Predicate<Entry> zipEntryPredicate, 
		Function<Entry, T> tSupplier,
		boolean loadZipEntryData
	) {
		Set<T> collection = ConcurrentHashMap.newKeySet();
		if (currentZipEntry != null && zipEntryPredicate.test(currentZipEntry)) {
			if (loadZipEntryData && !currentZipEntry.contentHasBeenLoaded()) {
				currentZipEntry.loadContent();
			}
			collection.add(tSupplier.apply(currentZipEntry));
		}
		while(getNextEntry(false) != null) {
			if (zipEntryPredicate.test(currentZipEntry)) {
				if (loadZipEntryData && !currentZipEntry.contentHasBeenLoaded()) {
					currentZipEntry.loadContent();
				}
				collection.add(tSupplier.apply(currentZipEntry));
			}
		}
		return collection;
	}
	
	public <T> T findFirstAndConvert(
		Predicate<Entry> zipEntryPredicate, 
		Function<Entry, T> tSupplier,
		boolean loadZipEntryData
	) {
		if (currentZipEntry != null && zipEntryPredicate.test(currentZipEntry)) {
			if (loadZipEntryData && !currentZipEntry.contentHasBeenLoaded()) {
				currentZipEntry.loadContent();
			}
			return tSupplier.apply(currentZipEntry);
		}
		while(getNextEntry(false) != null) {
			if (zipEntryPredicate.test(currentZipEntry)) {
				if (loadZipEntryData && !currentZipEntry.contentHasBeenLoaded()) {
					currentZipEntry.loadContent();
				}
				
				T toRet = tSupplier.apply(currentZipEntry);
				closeEntry();
				return toRet;
			}
		}
		return null;
	}
	
	public <T> T findOneAndConvert(Predicate<Entry> zipEntryPredicate, Function<Entry, T> tSupplier, boolean loadZipEntryData) {
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
	
	public Entry.Detached findOneAndConvert(Predicate<Entry> zipEntryPredicate, boolean loadZipEntryData) {
		return findOneAndConvert(
			zipEntryPredicate,
			zEntry -> new Entry.Detached(
				zEntry
			),
			loadZipEntryData		
		);
	}
	
	public Entry.Detached findFirstAndConvert(Predicate<Entry> zipEntryPredicate, boolean loadZipEntryData) {
		return findFirstAndConvert(
			zipEntryPredicate,
			zEntry -> new Entry.Detached(
				zEntry
			),
			loadZipEntryData		
		);
	}
	
	public Set<Entry.Detached> findAllAndConvert(Predicate<Entry> zipEntryPredicate, boolean loadZipEntryData) {
		return findAllAndConvert(
			zipEntryPredicate,
			zEntry -> new Entry.Detached(
				zEntry
			),
			loadZipEntryData		
		);
	}
	
	public Entry getCurrentZipEntry() {
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
		name = null;
		path = null;
		ThrowingRunnable.run(() -> super.close());
	}
	
	public static class Entry extends java.util.zip.ZipEntry implements Serializable, Component {

		private static final long serialVersionUID = -3679843114872023810L;
		
		private ZipInputStream zipInputStream;
		private ByteBuffer content;
				

		public Entry(Entry e, ZipInputStream zIS) {
			super(e);
			this.zipInputStream = zIS;
		}
		
		public Entry(String name, ZipInputStream zIS) {
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
				size = toByteBuffer().limit();
			}
			return size;
		}		
		
		
		private void loadContent() {
			if (!contentHasBeenLoaded()) {
				if (zipInputStream.currentZipEntry != this) {
					throw Throwables.toRuntimeException("Entry and his ZipInputStream are not aligned");
				}
				try (ByteBufferOutputStream bBOS = createDataBytesContainer()) {
					Streams.copy(zipInputStream, bBOS);
				    this.content = bBOS.toByteBuffer();
				}
			}
		}
		
		
		public boolean contentHasBeenLoaded() {
			return content != null;
		}
		
		public byte[] toByteArray() {
			return Streams.toByteArray(toByteBuffer());
		}

		public ByteBuffer toByteBuffer() {
			if (content == null) {
				loadContent();
			}
			return Streams.shareContent(content);
		}
		
		public InputStream toInputStream() {
			return new ByteBufferInputStream(toByteBuffer());
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
			content = null;
			zipInputStream = null;
		}
		
		public static class Detached implements Component {
			private ByteBuffer content;
			private String name;
			private String absolutePath;
			private Boolean isDirectory;
			private ZipInputStream zipInputStream;
			
			Detached(Entry zipEntry) {
				this.content = zipEntry.content;
				this.name = zipEntry.getName();
				this.absolutePath = zipEntry.getAbsolutePath();
				this.isDirectory = zipEntry.isDirectory();
				this.zipInputStream = zipEntry.getZipInputStream().duplicate();
				
			}
			
			public ZipInputStream getZipInputStream() {
				return zipInputStream.duplicate();
			}
			
			public byte[] toByteArray() {
				return Streams.toByteArray(toByteBuffer());
			}

			public ByteBuffer toByteBuffer() {
				if (content == null) {
					synchronized (zipInputStream) {
						if (content == null) {
							try (ZipInputStream zipInputStream = getZipInputStream()) {
								ByteBuffer content = zipInputStream.findFirstAndConvert((entry) -> 
									entry.getName().equals(getName()), zEntry -> 
									zEntry.toByteBuffer(), true
								);
								this.content = Streams.shareContent(content);
							}
						}
					}
				}
				return Streams.shareContent(content);
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
			
			public InputStream toInputStream() {
				return new ByteBufferInputStream(toByteBuffer());
			}
			
			@Override
			public void close() {
				content = null;
				name = null;
				absolutePath = null;
				isDirectory = null;
				zipInputStream.close();
				zipInputStream = null;
			}
		}
	}	
}