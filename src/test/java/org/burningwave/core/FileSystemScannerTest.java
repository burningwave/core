package org.burningwave.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.io.FileSystemScanner.Scan.Configuration;
import org.junit.jupiter.api.Test;

public class FileSystemScannerTest extends BaseTest {
	
	@Test
	public void findAllClassesTestMultipleCallTestOne() {
		int size = findAllClassesTest().size();
		assertEquals(findAllClassesTest().size(), size);
		assertEquals(findAllClassesTest().size(), size);
		assertEquals(findAllClassesTest().size(), size);
		assertEquals(findAllClassesTest().size(), size);
	}
	
	@Test
	public void findAllClassesTestOne() {
		findAllClassesTest();
	}
	
	public Collection<String> findAllClassesTest() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		Collection<String> classFileFounds = ConcurrentHashMap.newKeySet();
		Collection<String> classZipEntryFounds = ConcurrentHashMap.newKeySet();
		Collection<String> allClassesByteCodeFounds = ConcurrentHashMap.newKeySet();
		Configuration config = Configuration.forPaths(
			componentSupplier.getPathHelper().getAllPaths()
		).scanRecursivelyAllDirectory(
		).whenFindFileTestAndApply(
			file -> file.getName().endsWith(".class"), 
			scanItemContext -> {
				String fileName = scanItemContext.getScannedItem().getAbsolutePath();
				classFileFounds.add(fileName);
				allClassesByteCodeFounds.add(fileName);
			}
		).scanAllZipFileThat(file -> 
			file.getName().endsWith(".jar") ||
			file.getName().endsWith(".war") ||
			file.getName().endsWith(".ear") ||
			file.getName().endsWith(".zip") ||
			file.getName().endsWith(".jmod")
		).scanRecursivelyAllZipEntryThat(zipEntry -> 
			zipEntry.getName().endsWith(".jar") ||
			zipEntry.getName().endsWith(".war") ||
			zipEntry.getName().endsWith(".ear") ||
			zipEntry.getName().endsWith(".zip") ||
			zipEntry.getName().endsWith(".jmod")
		).whenFindZipEntryTestAndApply(
			zipEntry -> zipEntry.getName().endsWith(".class"),  
			scanItemContext -> {
				String fileName = scanItemContext.getScannedItem().getAbsolutePath();
				classZipEntryFounds.add(fileName);
				allClassesByteCodeFounds.add(fileName);
			}
		).setMaxParallelTasks(8);
		
		testNotEmpty(() -> {
			componentSupplier.getFileSystemScanner().scan(config);
			return allClassesByteCodeFounds;
		}, true);
		logInfo("class file founds: " + classFileFounds.size());
		logInfo("class zip entry founds: " + classZipEntryFounds.size());
		
		return allClassesByteCodeFounds;
	}
	
	
	@Test
	public void findAllDirectoryWithRecursionTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		Collection<String> allFilesFound = ConcurrentHashMap.newKeySet();
		Configuration config = Configuration.forPaths(
			componentSupplier.getPathHelper().getAllPaths()
		).scanRecursivelyAllDirectoryAndApplyBefore(
			scanItemContext -> {
				String fileName = scanItemContext.getScannedItem().getAbsolutePath();
				allFilesFound.add(fileName);
			}
		).setMaxParallelTasks(8);
		
		testNotEmpty(() -> {
			componentSupplier.getFileSystemScanner().scan(config);
			return allFilesFound;
		});
		
	}
		
	
	@Test
	public void findAllDirectoryWithNoRecursionTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		Collection<String> allFilesFound = ConcurrentHashMap.newKeySet();
		Configuration config = Configuration.forPaths(
			componentSupplier.getPathHelper().getAllPaths()
		).scanStrictlyDirectoryAndApplyBefore(
			scanItemContext -> {
				String fileName = scanItemContext.getScannedItem().getAbsolutePath();
				allFilesFound.add(fileName);
			}
		).setMaxParallelTasks(8);
		
		testNotEmpty(() -> {
			componentSupplier.getFileSystemScanner().scan(config);
			return allFilesFound;
		});
	}
	
	
	@Test
	public void findAllDirectoryWithNoRecursionTestTwo() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		Collection<String> allFilesFound = ConcurrentHashMap.newKeySet();
		Configuration config = Configuration.forPaths(
			componentSupplier.getPathHelper().getAllPaths()
		).scanStrictlyDirectoryAndApplyBefore(
			scanItemContext -> {
				String fileName = scanItemContext.getScannedItem().getAbsolutePath();
				allFilesFound.add(fileName);
			}
		).optimizePaths(
			true
		).setMaxParallelTasks(8);
		
		testNotEmpty(() -> {
			componentSupplier.getFileSystemScanner().scan(config);
			return allFilesFound;
		});
	}
	
	
	@Test
	public void findAllInZipWithNoRecursionTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		Collection<String> allFilesFound = ConcurrentHashMap.newKeySet();
		Configuration config = Configuration.forPaths(
			componentSupplier.getPathHelper().getAbsolutePathOfResource("../../src/test/external-resources/libs-for-test.zip")
		).scanAllZipFileThat(file ->
			file.getName().endsWith(".zip")
		).whenFindZipEntryApply(  
			scanItemContext -> {
				String fileName = scanItemContext.getScannedItem().getAbsolutePath();
				allFilesFound.add(fileName);
			}
		).setMaxParallelTasks(8);
		
		testNotEmpty(() -> {
			componentSupplier.getFileSystemScanner().scan(config);
			return allFilesFound;
		});
		
	}
	
	@Test
	public void findAllInZipWithRecursionTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		Collection<String> allFilesFound = ConcurrentHashMap.newKeySet();
		Configuration config = Configuration.forPaths(
			componentSupplier.getPathHelper().getAbsolutePathOfResource("../../src/test/external-resources/libs-for-test.zip")
		).scanAllZipFileThat(file ->
			file.getName().endsWith(".zip")
		).whenFindZipEntryApply(  
			scanItemContext -> {
				String fileName = scanItemContext.getScannedItem().getAbsolutePath();
				allFilesFound.add(fileName);
			}
		).scanRecursivelyAllZipEntryThat(zipEntry -> 
			zipEntry.getName().endsWith(".jar") ||
			zipEntry.getName().endsWith(".war") ||
			zipEntry.getName().endsWith(".ear") ||
			zipEntry.getName().endsWith(".zip")
		).setMaxParallelTasks(8);
		
		testNotEmpty(() -> {
			componentSupplier.getFileSystemScanner().scan(config);
			return allFilesFound;
		});
		
	}
}
