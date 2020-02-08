package org.burningwave.core.io;

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


import org.burningwave.Throwables;
import org.burningwave.core.Strings;

class ZipFile implements IterableZipContainer {
	String absolutePath;
	IterableZipContainer parent;
	IterableZipContainer.Entry currentZipEntry;
	Iterator<Entry> entriesIterator;
	Collection<Entry> entries;
	
	ZipFile(String absolutePath, ByteBuffer content) {
		this.absolutePath = Strings.Paths.clean(absolutePath);
		File file = new File(absolutePath);
		if (!file.exists()) {
			File temporaryFolder = FileSystemHelper.getOrCreateTemporaryFolder(toString());
			FileSystemItem fileSystemItem = Streams.store(temporaryFolder.getAbsolutePath() + "/" + file.getName(), content);
			Cache.PATH_FOR_CONTENTS.getOrDefault(absolutePath, () -> fileSystemItem.toByteBuffer());
			file = new File(fileSystemItem.getAbsolutePath());
		}
		entries = ConcurrentHashMap.newKeySet();
		try (java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(file)){
			Enumeration<? extends ZipEntry> entriesIterator = zipFile.entries();
			while (entriesIterator.hasMoreElements()) {
				ZipEntry zipEntry = entriesIterator.nextElement();
				entries.add(
					new Entry(
						this, 
						zipEntry.getName(), () -> {
							try (InputStream zipEntryIS = zipFile.getInputStream(zipEntry); ByteBufferOutputStream bBOS = new ByteBufferOutputStream()){
								Streams.copy(zipEntryIS, bBOS);
								 return bBOS.toByteBuffer();
							} catch (IOException exc) {
								throw Throwables.toRuntimeException(exc);
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
	public IterableZipContainer getParent() {
		return parent;
	}

	@Override
	public void setParent(IterableZipContainer parent) {
		this.parent = parent;		
	}

	@Override
	public ByteBuffer toByteBuffer() {
		return Cache.PATH_FOR_CONTENTS.get(absolutePath);
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

		public Entry(ZipFile zipMemoryContainer, String entryName, Supplier<ByteBuffer> zipEntryContentSupplier) {
			this.zipMemoryContainer = zipMemoryContainer;
			this.name = entryName;
			this.absolutePath = Strings.Paths.clean(zipMemoryContainer.getAbsolutePath() + "/" + entryName);
			Cache.PATH_FOR_CONTENTS.getOrDefault(getAbsolutePath(), zipEntryContentSupplier);
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
			return Cache.PATH_FOR_CONTENTS.get(getAbsolutePath());
		}	
	}
	
}
