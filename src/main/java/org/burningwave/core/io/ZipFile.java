package org.burningwave.core.io;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.zip.ZipException;

import org.burningwave.core.Strings;
import org.burningwave.core.function.ThrowingSupplier;
;

public class ZipFile extends java.util.zip.ZipFile implements ZipContainer {
	private String absolutePath;
	private Iterator<Entry> entriesIterator;
	
	public ZipFile(String absolutePath) throws IOException {
		this(new File(Strings.Paths.clean(absolutePath)));
	}
	
	public ZipFile(File file) throws ZipException, IOException {
		super(file);
		absolutePath = Strings.Paths.clean(file.getAbsolutePath());
	}

	@Override
	public String getAbsolutePath() {
		return absolutePath;
	}

	@Override
	public ByteBuffer toByteBuffer() {
		return Cache.PATH_FOR_CONTENTS.getOrDefault(
			getAbsolutePath(), () -> {
				try (FileInputStream fileInputStream = FileInputStream.create(new File(getAbsolutePath()));
					ByteBufferOutputStream bBOS = new ByteBufferOutputStream()
				) {
					Streams.copy(fileInputStream, bBOS);
				    return bBOS.toByteBuffer();
				}
			}
		);
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public <Z extends ZipContainer.Entry> Z getNextEntry() {
		if (entriesIterator == null) {
			entries();
		}
		if (entriesIterator.hasNext()) {
			return (Z) entriesIterator.next();
		}
		return null;
	}
	
	@Override
	public java.util.zip.ZipEntry getEntry(String name) {
		return new Entry(super.getEntry(name), this);
	}
	
	@Override
	public InputStream getInputStream(java.util.zip.ZipEntry entry) throws IOException {
		return new ByteBufferInputStream(Cache.PATH_FOR_CONTENTS.getOrDefault(
			((Entry)entry).getAbsolutePath(), () -> {
				try (ByteBufferOutputStream bBOS = new ByteBufferOutputStream()) {
					Streams.copy(ThrowingSupplier.get(() -> super.getInputStream(entry)), bBOS);
				    return bBOS.toByteBuffer();
				}
			}
		));
	}
	
	@Override
	public Enumeration<? extends java.util.zip.ZipEntry> entries() {
		Enumeration<? extends java.util.zip.ZipEntry> zipEntries = super.entries();
		Collection<Entry> entries = new LinkedHashSet<>();
		while (zipEntries.hasMoreElements()) {
			entries.add(new Entry(zipEntries.nextElement(), this));
		}
		if (entriesIterator == null) {
			entriesIterator = entries.iterator();
		}
		return Collections.enumeration(entries);
	}
	
	public static class Entry extends java.util.zip.ZipEntry implements ZipContainer.Entry {
		private ZipFile parentContainer;
		public Entry(java.util.zip.ZipEntry e, ZipFile parentContainer) {
			super(e);
			this.parentContainer = parentContainer;
		}

		@Override
		@SuppressWarnings("unchecked")
		public ZipFile getParentContainer() {
			return parentContainer;
		}

		@Override
		public String getAbsolutePath() {
			String name = getName();
			return parentContainer.getAbsolutePath() + "/" + (name.endsWith("/") ? name.substring(0, name.length() -1) : name);
		}		
		
		@Override
		public ByteBuffer toByteBuffer() {
			return Cache.PATH_FOR_CONTENTS.getOrDefault(
				getAbsolutePath(), () -> {
					try (ByteBufferOutputStream bBOS = new ByteBufferOutputStream()) {
						Streams.copy(ThrowingSupplier.get(() -> parentContainer.getInputStream(this)), bBOS);
					    return bBOS.toByteBuffer();
					}
				}
			);
		}
		
	}
}
