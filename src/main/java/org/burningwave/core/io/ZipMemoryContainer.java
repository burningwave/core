package org.burningwave.core.io;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.burningwave.Throwables;
import org.burningwave.core.Strings;

public class ZipMemoryContainer implements IterableZipContainer {
	private String absolutePath;
	private IterableZipContainer parent;
	private IterableZipContainer.Entry currentZipEntry;
	private Iterator<Entry> entriesIterator;
	private Collection<Entry> entries;
	
	public ZipMemoryContainer(String absolutePath, ByteBuffer content) {
		File file = new File(Strings.Paths.clean(absolutePath));
		if (!file.exists()) {
			File temporaryFolder = FileSystemHelper.getOrCreateTemporaryFolder(toString());
			file = new File(Streams.store(temporaryFolder.getAbsolutePath() + "/" + file.getName(), content).getAbsolutePath());
		}
		try {
			ZipFile zipFile = new ZipFile(file);
			//Enumeration<? extends ZipEntry> e = zipFile.entries();
		} catch (IOException exc) {
			Throwables.toRuntimeException(exc);
		}
		entries = ConcurrentHashMap.newKeySet();
	}
	
	@Override
	public IterableZipContainer duplicate() {
		ZipMemoryContainer zipContainer = (ZipMemoryContainer)IterableZipContainer.super.duplicate();
		zipContainer.entries = this.entries;
		zipContainer.entriesIterator = entries.iterator();
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
		// TODO Auto-generated method stub
		return null;
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
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void closeEntry() {
		// TODO Auto-generated method stub
		
	}
	
	public static class Entry implements IterableZipContainer.Entry {

		@Override
		public <C extends IterableZipContainer> C getParentContainer() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public String getName() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public String getAbsolutePath() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public boolean isDirectory() {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public ByteBuffer toByteBuffer() {
			// TODO Auto-generated method stub
			return null;
		}
		
		
	}
	
}
