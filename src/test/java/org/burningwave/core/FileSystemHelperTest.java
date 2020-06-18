package org.burningwave.core;

import static org.burningwave.core.assembler.StaticComponentContainer.FileSystemHelper;
import static org.junit.Assert.assertTrue;

import org.burningwave.core.io.FileSystemItem;
import org.junit.jupiter.api.Test;

public class FileSystemHelperTest extends BaseTest {
	
	@Test
	public void createFolderTest() {
		assertTrue(FileSystemItem.of(FileSystemHelper.createTemporaryFolder("FolderForTest")).exists());
	}
	
}
