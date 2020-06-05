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
import static org.burningwave.core.assembler.StaticComponentContainer.Streams;
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.burningwave.core.Component;

public interface IterableZipContainer extends Component {
	
	public static final String ZIP_PATH_SEPARATOR = "///";
	
	public static IterableZipContainer create(FileInputStream file) {
		return create(file.getAbsolutePath(), file);
	}
	
	public static IterableZipContainer create(File file) {
		return create(file.getAbsolutePath(), FileInputStream.create(file));
	}
	
	public static IterableZipContainer create(Entry zipEntry) {
		IterableZipContainer zipContainer = create(zipEntry.getAbsolutePath(), zipEntry.toByteBuffer());
		IterableZipContainer parentContainer = zipEntry.getParentContainer().duplicate();
		zipContainer.setParent(parentContainer);
		return zipContainer;
	}

	@SuppressWarnings("resource")
	public static IterableZipContainer create(String absolutePath, ByteBuffer bytes) {
		if (Streams.isJModArchive(bytes)) {
			return Cache.pathForZipFiles.getOrUploadIfAbsent(
				absolutePath, () -> new ZipFile(absolutePath, bytes)
			).duplicate();
		} else if (Streams.isArchive(bytes)) {
			return new ZipInputStream(absolutePath, new ByteBufferInputStream(bytes));
		}
		return null;
	}
	
	@SuppressWarnings("resource")
	public static IterableZipContainer create(String absolutePath, InputStream inputStream) {
		ByteBufferInputStream iS;
		if (inputStream instanceof ByteBufferInputStream) {
			iS = new ByteBufferInputStream(((ByteBufferInputStream)inputStream).toByteBuffer());
		} else if (inputStream instanceof FileInputStream) {
			FileInputStream fileInputStream = (FileInputStream)inputStream;
			iS = new ByteBufferInputStream(fileInputStream.toByteBuffer());
		} else {
			iS = new ByteBufferInputStream(Streams.toByteBuffer(inputStream));
		}
		if (Streams.isJModArchive(iS.toByteBuffer())) {
			return Cache.pathForZipFiles.getOrUploadIfAbsent(
				absolutePath, () -> new ZipFile(absolutePath, iS.toByteBuffer())
			).duplicate();
		} else if (Streams.isArchive(iS.toByteBuffer())) {
			return new ZipInputStream(absolutePath, new ByteBufferInputStream(iS.toByteBuffer()));
		}
		return null;
	}
	
	public default <T> Set<T> findAllAndConvert(
		Predicate<IterableZipContainer.Entry> zipEntryPredicate, 
		Function<IterableZipContainer.Entry, T> tSupplier,
		Predicate<IterableZipContainer.Entry> loadZipEntryData
	) {
		return findAllAndConvert(HashSet::new, zipEntryPredicate, tSupplier, loadZipEntryData);
	}
	
	public default <T> Set<T> findAllAndConvert(
		Supplier<Set<T>> supplier, 
		Predicate<IterableZipContainer.Entry> zipEntryPredicate, 
		Function<IterableZipContainer.Entry, T> tSupplier,
		Predicate<IterableZipContainer.Entry> loadZipEntryData
	) {
		Set<T> collection = supplier.get();
		Entry zipEntry = getCurrentZipEntry();
		if (zipEntry != null && zipEntryPredicate.test(zipEntry)) {
			if (loadZipEntryData.test(zipEntry)) {
				zipEntry.toByteBuffer();
			}
			collection.add(tSupplier.apply(zipEntry));
			closeEntry();
		}
		while((zipEntry = getNextEntry((zEntry) -> false)) != null) {
			if (zipEntryPredicate.test(zipEntry)) {
				if (loadZipEntryData.test(zipEntry)) {
					zipEntry.toByteBuffer();
				}
				collection.add(tSupplier.apply(zipEntry));
			}
			closeEntry();
		}
		return collection;
	}
	
	public String getConventionedAbsolutePath();
	
	public String getAbsolutePath();
	
	public IterableZipContainer getParent();
	
	public void setParent(IterableZipContainer parent);
	
	public ByteBuffer toByteBuffer();
	
	public <Z extends Entry> Z getNextEntry();
	
	public IterableZipContainer.Entry getNextEntry(Predicate<IterableZipContainer.Entry> loadZipEntryData);
	
	public default IterableZipContainer duplicate() {
		IterableZipContainer zipContainer = IterableZipContainer.create(getAbsolutePath(), toByteBuffer());
		if (getParent() != null) {
			zipContainer.setParent(getParent().duplicate());
		}
		return zipContainer;
	}
	
	public Entry getCurrentZipEntry();
	
	public Function<Entry, Entry> getEntrySupplier();
	
	public void closeEntry();
	
	public default <T> T findFirstAndConvert(
		Predicate<IterableZipContainer.Entry> zipEntryPredicate, 
		Function<IterableZipContainer.Entry, T> tSupplier,
		Predicate<IterableZipContainer.Entry> loadZipEntryData
	) {
		Entry zipEntry = getCurrentZipEntry();
		if (zipEntry != null && zipEntryPredicate.test(zipEntry)) {
			if (loadZipEntryData.test(zipEntry)) {
				zipEntry.toByteBuffer();
			}
			closeEntry();
			return tSupplier.apply(zipEntry);
		}
		while((zipEntry = getNextEntry(zEntry -> false)) != null) {
			if (zipEntryPredicate.test(zipEntry)) {
				if (loadZipEntryData.test(zipEntry)) {
					zipEntry.toByteBuffer();
				}
				
				T toRet = tSupplier.apply(zipEntry);
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
		return findAll(HashSet::new, zipEntryPredicate, loadZipEntryData);
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
		
		public default String getConventionedAbsolutePath() {
			return getParentContainer().getConventionedAbsolutePath() + getName();
		}
		
		public String getName();
		
		public String getAbsolutePath();
		
		public boolean isDirectory();
		
		public ByteBuffer toByteBuffer();
		
		default public byte[] toByteArray() {
			return Streams.toByteArray(toByteBuffer());
		}
		
		default public boolean isArchive() {
			ByteBuffer content = toByteBuffer();
			return content != null ? Streams.isArchive(content) : false;
		}
		
		default public InputStream toInputStream() {
			return new ByteBufferInputStream(toByteBuffer());
		}
		
	}
}


