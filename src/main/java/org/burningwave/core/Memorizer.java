package org.burningwave.core;

import static org.burningwave.core.assembler.StaticComponentContainer.FileSystemHelper;

import java.io.File;

public interface Memorizer {

	default public String getTemporaryFolderPrefix() {
		return getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(this));
	}
	
	default public File getOrCreateTemporaryFolder() {
		//Register main temporary folder for deleting on FileSystemHelper closing 
		return FileSystemHelper.getOrCreateTemporaryFolder(getTemporaryFolderPrefix());
	}
	
	default public File getOrCreateTemporaryFolder(String folderName) {
		return FileSystemHelper.getOrCreateTemporaryFolder(getOrCreateTemporaryFolder().getName() + "/" + folderName);
	}

}