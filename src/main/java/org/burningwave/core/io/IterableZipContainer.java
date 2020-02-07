package org.burningwave.core.io;

import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.burningwave.Throwables;
import org.burningwave.core.Component;

public interface IterableZipContainer extends Component {
	
	public static IterableZipContainer create(FileInputStream file) {
		return create(file.getAbsolutePath(), file);
	}
	
	public static IterableZipContainer create(File file) {
		return create(file.getAbsolutePath(), FileInputStream.create(file));
	}
	
	public static IterableZipContainer create(String absolutePath, InputStream inputStream) {
		ByteBufferInputStream iS = null;
		if (inputStream instanceof ByteBufferInputStream) {
			iS = new ByteBufferInputStream(((ByteBufferInputStream)inputStream).toByteBuffer());
		} else if (inputStream instanceof FileInputStream) {
			FileInputStream fileInputStream = (FileInputStream)inputStream;
			iS = new ByteBufferInputStream(fileInputStream.toByteBuffer());
		} else {
			iS = new ByteBufferInputStream(Streams.toByteBuffer(inputStream));
		}
		return new ZipInputStream(absolutePath, iS);
	}
	

	public static IterableZipContainer create(Entry zipEntry) {
		IterableZipContainer zipInputStream = create(zipEntry.getAbsolutePath(), zipEntry.toByteBuffer());
		IterableZipContainer parentContainer = zipEntry.getParentContainer();
		zipInputStream.setParent(parentContainer);
		return zipInputStream;
	}

	public static IterableZipContainer create(String absolutePath, ByteBuffer zipInputStreamAsBytes) {
		return new ZipInputStream(absolutePath, new ByteBufferInputStream(zipInputStreamAsBytes));
	}
	
	public default <T> Set<T> findAllAndConvert(
		Predicate<IterableZipContainer.Entry> zipEntryPredicate, 
		Function<IterableZipContainer.Entry, T> tSupplier,
		Predicate<IterableZipContainer.Entry> loadZipEntryData
	) {
		return findAllAndConvert(ConcurrentHashMap::newKeySet, zipEntryPredicate, tSupplier, loadZipEntryData);
	}
	
	public default <T> Set<T> findAllAndConvert(
		Supplier<Set<T>> supplier, 
		Predicate<IterableZipContainer.Entry> zipEntryPredicate, 
		Function<IterableZipContainer.Entry, T> tSupplier,
		Predicate<IterableZipContainer.Entry> loadZipEntryData
	) {
		Set<T> collection = supplier.get();
		if (getCurrentZipEntry() != null && zipEntryPredicate.test(getCurrentZipEntry())) {
			if (loadZipEntryData.test(getCurrentZipEntry())) {
				getCurrentZipEntry().toByteBuffer();
			}
			collection.add(tSupplier.apply(getCurrentZipEntry()));
		}
		while(getNextEntry((zEntry) -> false) != null) {
			if (zipEntryPredicate.test(getCurrentZipEntry())) {
				if (loadZipEntryData.test(getCurrentZipEntry())) {
					getCurrentZipEntry().toByteBuffer();
				}
				collection.add(tSupplier.apply(getCurrentZipEntry()));
			}
		}
		return collection;
	}
	
	public String getAbsolutePath();
	
	public IterableZipContainer getParent();
	
	public void setParent(IterableZipContainer parent);
	
	public ByteBuffer toByteBuffer();
	
	public <Z extends Entry> Z getNextEntry();
	
	public IterableZipContainer.Entry getNextEntry(Predicate<IterableZipContainer.Entry> loadZipEntryData);
	
	public IterableZipContainer duplicate();
	
	public Entry getCurrentZipEntry();
	
	public Function<Entry, Entry> getEntrySupplier();
	
	public void closeEntry();
	
	public default <T> T findFirstAndConvert(
		Predicate<IterableZipContainer.Entry> zipEntryPredicate, 
		Function<IterableZipContainer.Entry, T> tSupplier,
		Predicate<IterableZipContainer.Entry> loadZipEntryData
	) {
		if (getCurrentZipEntry() != null && zipEntryPredicate.test(getCurrentZipEntry())) {
			if (loadZipEntryData.test(getCurrentZipEntry())) {
				getCurrentZipEntry().toByteBuffer();
			}
			return tSupplier.apply(getCurrentZipEntry());
		}
		while(getNextEntry(zEntry -> false) != null) {
			if (zipEntryPredicate.test(getCurrentZipEntry())) {
				if (loadZipEntryData.test(getCurrentZipEntry())) {
					getCurrentZipEntry().toByteBuffer();
				}
				
				T toRet = tSupplier.apply(getCurrentZipEntry());
				closeEntry();
				return toRet;
			}
		}
		return null;
	}

	public default <T> T findOneAndConvert(Predicate<IterableZipContainer.Entry> zipEntryPredicate, Function<IterableZipContainer.Entry, T> tSupplier, Predicate<IterableZipContainer.Entry> loadZipEntryData) {
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
	
	public default IterableZipContainer.Entry findOne(Predicate<IterableZipContainer.Entry> zipEntryPredicate, Predicate<IterableZipContainer.Entry> loadZipEntryData) {
		return findOneAndConvert(
			zipEntryPredicate,
			getEntrySupplier(),
			loadZipEntryData		
		);
	}
	
	public default IterableZipContainer.Entry findFirst(Predicate<IterableZipContainer.Entry> zipEntryPredicate, Predicate<IterableZipContainer.Entry> loadZipEntryData) {
		return findFirstAndConvert(
			zipEntryPredicate,
			getEntrySupplier(),
			loadZipEntryData		
		);
	}
	
	public default Set<IterableZipContainer.Entry> findAll(Predicate<IterableZipContainer.Entry> zipEntryPredicate, Predicate<IterableZipContainer.Entry> loadZipEntryData) {
		return findAll(ConcurrentHashMap::newKeySet, zipEntryPredicate, loadZipEntryData);
	}
	
	public default Set<IterableZipContainer.Entry> findAll(
		Supplier<Set<IterableZipContainer.Entry>> setSupplier, 
		Predicate<IterableZipContainer.Entry> zipEntryPredicate, 
		Predicate<IterableZipContainer.Entry> loadZipEntryData) {
		return findAllAndConvert(
			setSupplier,
			zipEntryPredicate,
			getEntrySupplier(),
			loadZipEntryData		
		);
	}

	public static interface Entry extends Component{
		
		public <C extends IterableZipContainer> C getParentContainer();
		
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
		
	}
}


