package org.burningwave.core;

import static org.burningwave.core.assembler.StaticComponentContainer.FileSystemHelper;

import java.io.File;

public interface Memorizer {

	default public String getTemporaryFolderPrefix() {
		return getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(this));
	}
	
	default public File getOrCreateTemporaryFolder(String folderName) {
		//Register main temporary folder for deleting on FileSystemHelper closing 
		FileSystemHelper.getOrCreateTemporaryFolder(getTemporaryFolderPrefix());
		return FileSystemHelper.getOrCreateTemporaryFolder(getTemporaryFolderPrefix() + "/" + folderName);
	}
	
	default public File getOrCreateTemporaryFolder() {
		//Register main temporary folder for deleting on FileSystemHelper closing 
		return FileSystemHelper.getOrCreateTemporaryFolder(getTemporaryFolderPrefix());
	}

}