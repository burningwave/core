package org.burningwave.core;

import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.IterableZipContainer;
import org.junit.jupiter.api.Test;


public class IterableZipContainerTest extends BaseTest {

	@Test
	public void getConventionedAbsolutePathTestOne() {
		testNotNull(() ->{
			ComponentSupplier componentSupplier = getComponentSupplier();
			FileSystemItem fIS = componentSupplier.getPathHelper().getResource(
				"/../../src/test/external-resources/libs-for-test.zip/java.desktop.jmod"
			);
			fIS.reset();
			IterableZipContainer zip = IterableZipContainer.create(fIS.getAbsolutePath());
			return zip.getConventionedAbsolutePath();
		});
	}
	
	@Test
	public void getConventionedAbsolutePathTestTwo() {
		testNotNull(() ->{
			ComponentSupplier componentSupplier = getComponentSupplier();
			FileSystemItem fIS = componentSupplier.getPathHelper().getResource(
				"/../../src/test/external-resources/libs-for-test.zip/ESC-Lib.ear/APP-INF/lib/bcel-5.1.jar"
			);
			fIS.reset();
			IterableZipContainer zip = IterableZipContainer.create(fIS.getAbsolutePath());
			return zip.getConventionedAbsolutePath();
		});
	}
	
}
