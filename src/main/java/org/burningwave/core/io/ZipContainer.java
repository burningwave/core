package org.burningwave.core.io;

import java.io.InputStream;
import java.nio.ByteBuffer;

public interface ZipContainer {
	
	public String getAbsolutePath();
	
	public ByteBuffer toByteBuffer();
	
	public <Z extends Entry> Z getNextEntry();
	
	public static interface Entry {
		
		public <C extends ZipContainer> C getParentContainer();
		
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


