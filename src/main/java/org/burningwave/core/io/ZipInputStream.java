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

import static org.burningwave.core.assembler.StaticComponentContainer.ByteBufferHandler;
import static org.burningwave.core.assembler.StaticComponentContainer.Cache;
import static org.burningwave.core.assembler.StaticComponentContainer.Streams;
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.zip.ZipException;

import org.burningwave.core.Component;
import org.burningwave.core.function.ThrowingRunnable;
import org.burningwave.core.io.ZipInputStream.Entry.Attached;

class ZipInputStream extends java.util.zip.ZipInputStream implements IterableZipContainer, Component {
	String absolutePath;
	String conventionedAbsolutePath;
	IterableZipContainer parent;
	IterableZipContainer.Entry currentZipEntry;
	ByteBufferInputStream byteBufferInputStream;
	
	ZipInputStream(String absolutePath, InputStream inputStream) {
		super(inputStream);
		this.absolutePath = absolutePath;
	}
	
	ZipInputStream(String absolutePath, ByteBufferInputStream inputStream) {
		super(inputStream);
		this.absolutePath = absolutePath;
		this.byteBufferInputStream = inputStream;
	}
	
	@Override
	public Function<IterableZipContainer.Entry, org.burningwave.core.io.IterableZipContainer.Entry> getEntrySupplier() {
		return Entry.Detached::new;
	}	
	
	@Override
	public IterableZipContainer getParent() {
		if (conventionedAbsolutePath == null) {
			getConventionedAbsolutePath();
		}
		return parent;
	}
	
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
	
	
	@Override
	@SuppressWarnings("unchecked")
	public Entry.Attached getNextEntry() {
		return (Attached)getNextEntry((zEntry) -> false);
	}
	
	public IterableZipContainer.Entry getNextEntry(Predicate<IterableZipContainer.Entry> loadZipEntryData) {
		ThrowingRunnable.run(() -> {
			try {
				currentZipEntry = (Entry.Attached)super.getNextEntry();
			} catch (ZipException exc) {
				String message = exc.getMessage();
				logWarn("Could not open zipEntry of {}: {}", absolutePath, message);
			}
		});
		if (currentZipEntry != null && loadZipEntryData.test(currentZipEntry)) {
			currentZipEntry.toByteBuffer();
		}
		return currentZipEntry;
	}		
	
	public IterableZipContainer.Entry getNextEntryAsDetached() {
		return getNextEntryAsDetached(zEntry -> false);
	}
	
	public IterableZipContainer.Entry getNextEntryAsDetached(Predicate<IterableZipContainer.Entry> loadZipEntryData) {
		return Optional.ofNullable(
			getNextEntry(loadZipEntryData)).map(zipEntry ->	((Attached) zipEntry).convert()
		).orElseGet(
			() -> null
		);
	}
	
	public IterableZipContainer.Entry getCurrentZipEntry() {
		return currentZipEntry;
	}
	
	public IterableZipContainer.Entry convertCurrentZipEntry() {
		return ((Attached)getCurrentZipEntry()).convert();
	}
	
	
	@Override
	public void closeEntry() {
		try {
			//This synchronization is necessary to avoid a jdk bug on
			//java.util.zip.Inflater.inflateBytes method
			synchronized (this.inf) {
				super.closeEntry();
			}
		} catch (IOException exc) {
			logWarn("Exception occurred while closing zipEntry {}: {}", Optional.ofNullable(getCurrentZipEntry()).map((zipEntry) -> zipEntry.getAbsolutePath()).orElseGet(() -> "null"), exc.getMessage());
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
		
	public static interface Entry extends IterableZipContainer.Entry {		
	
		static class Attached extends java.util.zip.ZipEntry implements Entry {
			private ZipInputStream zipInputStream;
	
			public Attached(Entry.Attached e, ZipInputStream zIS) {
				super(e);
				this.zipInputStream = zIS;
			}
			
			public Attached(String name, ZipInputStream zIS) {
				super(name);
				this.zipInputStream = zIS;
			}
			
			@SuppressWarnings("unchecked")
			public ZipInputStream getParentContainer() {
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
					size = ByteBufferHandler.limit(toByteBuffer());
				}
				return size;
			}		
			
			
			private ByteBuffer loadContent() {
				return Cache.pathForContents.getOrUploadIfAbsent(
					getAbsolutePath(), () -> {
						if (zipInputStream.getCurrentZipEntry() != this) {
							throw Throwables.toRuntimeException("{} and his ZipInputStream are not aligned", Attached.class.getSimpleName());
						}
						try (ByteBufferOutputStream bBOS = createDataBytesContainer()) {
							Streams.copy(zipInputStream, bBOS);
						    return bBOS.toByteBuffer();
						} catch (Throwable exc) {
							logError("Could not load content of {} of {}", exc, getName(), zipInputStream.getAbsolutePath());
							return null;
						}
					}
				);
				
			}		
	
			public ByteBuffer toByteBuffer() {
				return loadContent();
			}
			
			public IterableZipContainer.Entry convert() {
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
							byte buffer[] = new byte[Streams.defaultBufferSize];
							try (
								FileOutputStream fos = FileOutputStream.create(destinationFilePath);
								BufferedOutputStream bos = new BufferedOutputStream(fos, Streams.defaultBufferSize)
							) {
								while ((byteTransferred = bis.read(buffer, 0, Streams.defaultBufferSize)) != -1) {
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
	
		public static class Detached implements Entry {
			private String name;
			private String absolutePath;
			private Boolean isDirectory;
			private IterableZipContainer zipInputStream;
			
			Detached(IterableZipContainer.Entry zipEntry) {
				this.name = zipEntry.getName();
				this.absolutePath = zipEntry.getAbsolutePath();
				this.isDirectory = zipEntry.isDirectory();
				this.zipInputStream = zipEntry.getParentContainer().duplicate();
				
			}
			
			@SuppressWarnings("unchecked")
			public IterableZipContainer getParentContainer() {
				return zipInputStream.duplicate();
			}
	
			public ByteBuffer toByteBuffer() {
				return Cache.pathForContents.getOrUploadIfAbsent(absolutePath, () -> {
					try (IterableZipContainer zipInputStream = getParentContainer()) {
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