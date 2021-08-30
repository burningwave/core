package org.burningwave.core;

import java.util.Set;
import java.util.TreeSet;

import org.burningwave.core.io.FileSystemItem;
import org.junit.jupiter.api.Test;

public class JavaHomeTreeTest extends BaseTest {
	
	@Test
	public void readTestOne() {
		testNotEmpty(() -> {
				Set<String> files = new TreeSet<>();
				FileSystemItem.ofPath(System.getProperty("java.home")).findInAllChildren(
					FileSystemItem.Criteria.forAllFileThat(fileSystemItem -> {
						if ("class".equals(fileSystemItem.getExtension())) {
							return false;
						} else {
							files.add(fileSystemItem.getAbsolutePath());
							return true;
						}
					})
				);
				return files;
			}, true
		);
	}

}
