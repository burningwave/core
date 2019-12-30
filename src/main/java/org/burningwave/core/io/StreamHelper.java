 package org.burningwave.core.io;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;

import org.burningwave.core.Component;
import org.burningwave.core.function.ThrowingRunnable;
import org.burningwave.core.function.ThrowingSupplier;

public class StreamHelper implements Component {
	private FileSystemHelper fileSystemHelper;
	
	private StreamHelper(FileSystemHelper fileSystemHelper) {
		this.fileSystemHelper = fileSystemHelper;	
	}
	
	public static StreamHelper create(FileSystemHelper fileSystemHelper) {
		return new StreamHelper(fileSystemHelper);
	}
	
	public Collection<InputStream> getResourcesAsStreams(String... resourcesRelativePaths) {
		return fileSystemHelper.getResources((coll, fileSystemItem) -> coll.add(fileSystemItem.toInputStream()), resourcesRelativePaths);
	}
	
	public InputStream getResourceAsStream(String resourceRelativePath) {
		return fileSystemHelper.getResource((coll, fileSystemItem) ->
			coll.add(fileSystemItem.toInputStream()), 
			resourceRelativePath
		);
	}
	
	public StringBuffer getResourceAsStringBuffer(String resourceRelativePath) {
		return ThrowingSupplier.get(() -> {
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(getResourceAsStream(resourceRelativePath)))) {
				StringBuffer result = new StringBuffer();
				String sCurrentLine;
				while ((sCurrentLine = reader.readLine()) != null) {
					result.append(sCurrentLine + "\n");
				}
				return result;
			}
		});
	}
		
	
	public void close(Collection<InputStream> inputStreams) {
		for (InputStream inputStream : inputStreams) {
			ThrowingRunnable.run(() -> inputStream.close());
		}
	}
}
