package org.burningwave.core;

import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.ClassHelper.Dependencies;
import org.burningwave.core.io.FileSystemItem;
import org.junit.jupiter.api.Test;

public class ClassHelperTest extends BaseTest {
	
	@Test
	public void storeDependenciesTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotEmpty(() -> {
			Dependencies dependencies = componentSupplier.getClassHelper().storeDependencies(
				ClassHelperTest.class, System.getProperty("user.home") + "/Desktop/bw-tests"
			);
			dependencies.waitForTaskEnding();
			return dependencies.getStore().getChildren();
		});
	}	

	
	public static void main(String[] args) {
		FileSystemItem.ofPath(System.getProperty("user.home")).getChildren();
	}
}
