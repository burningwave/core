package org.burningwave.core.io;

import java.nio.ByteBuffer;
import java.util.function.Function;
import java.util.function.Predicate;

public class ZipMemoryContainer implements IterableZipContainer {

	@Override
	public String getAbsolutePath() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IterableZipContainer getParent() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setParent(IterableZipContainer parent) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public ByteBuffer toByteBuffer() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <Z extends IterableZipContainer.Entry> Z getNextEntry() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Entry getNextEntry(Predicate<IterableZipContainer.Entry> loadZipEntryData) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IterableZipContainer duplicate() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IterableZipContainer.Entry getCurrentZipEntry() {
		// TODO Auto-generated method stub
		return null;
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
