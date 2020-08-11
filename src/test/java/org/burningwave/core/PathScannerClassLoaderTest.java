package org.burningwave.core;

import static org.burningwave.core.assembler.StaticComponentContainer.Paths;

import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;

import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.PathScannerClassLoader;
import org.junit.jupiter.api.Test;

public class PathScannerClassLoaderTest extends BaseTest {
	
	@Test
	public void getResourcesTestOne() throws ClassNotFoundException {
		testNotEmpty(() -> {
			ComponentSupplier componentSupplier = getComponentSupplier();
			PathScannerClassLoader classLoader = componentSupplier.getPathScannerClassLoader();
			classLoader.scanPathsAndAddAllByteCodesFound(
				Arrays.asList(
					componentSupplier.getPathHelper().getAbsolutePathOfResource("../../src/test/external-resources")
				)
			);
			return Collections.list(classLoader.getResources("META-INF/MANIFEST.MF")).stream().map(url ->
				Paths.convertURLPathToAbsolutePath(url.getPath())).collect(Collectors.toSet()
			);			
		}, true);
	}
	
	@Test
	public void getResourcesTestTwo() throws ClassNotFoundException {
		testNotEmpty(() -> {
			ComponentSupplier componentSupplier = getComponentSupplier();
			PathScannerClassLoader classLoader = componentSupplier.getPathScannerClassLoader();
			classLoader.scanPathsAndAddAllByteCodesFound(
				Arrays.asList(
					componentSupplier.getPathHelper().getAbsolutePathOfResource("../../src/test/external-resources")
				)
			);
			return Collections.list(classLoader.getResources("org/burningwave/core/Component.class"));			
		}, true);
	}
	
	@Test
	public void getResourceTestOne() throws ClassNotFoundException {
		testNotNull(() -> {
			ComponentSupplier componentSupplier = getComponentSupplier();
			PathScannerClassLoader classLoader = componentSupplier.getPathScannerClassLoader();
			classLoader.scanPathsAndAddAllByteCodesFound(
				Arrays.asList(
					componentSupplier.getPathHelper().getAbsolutePathOfResource("../../src/test/external-resources")
				)
			);
			return classLoader.getResource("org/burningwave/core/Component.class");			
		});
	}
	
	@Test
	public void getResourceTestTwo() throws ClassNotFoundException {
		testNotNull(() -> {
			ComponentSupplier componentSupplier = getComponentSupplier();
			PathScannerClassLoader classLoader = componentSupplier.getPathScannerClassLoader();
			classLoader.scanPathsAndAddAllByteCodesFound(
				Arrays.asList(
					componentSupplier.getPathHelper().getAbsolutePathOfResource("../../src/test/external-resources")
				)
			);
			return classLoader.getResource("META-INF/MANIFEST.MF");			
		});
	}
	
	@Test
	public void getResourceTestThree() throws ClassNotFoundException {
		testNotNull(() -> {
			ComponentSupplier componentSupplier = getComponentSupplier();
			PathScannerClassLoader classLoader = componentSupplier.getPathScannerClassLoader();
			classLoader.scanPathsAndAddAllByteCodesFound(
				Arrays.asList(
					componentSupplier.getPathHelper().getAbsolutePathOfResource("../../src/test/external-resources")
				)
			);
			return classLoader.getResource("burningwave.properties");			
		});
	}
}
