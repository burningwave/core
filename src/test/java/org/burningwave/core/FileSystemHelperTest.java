package org.burningwave.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.io.FileSystemHelper.Scan.Configuration;
import org.junit.jupiter.api.Test;

public class FileSystemHelperTest extends BaseTest {
	
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
			componentSupplier.getPathHelper().getAllClassPaths()
		).scanRecursivelyAllDirectory(
		).whenFindFileTestAndApply(
			file -> file.getName().endsWith(".class"), 
			scanItemContext -> {
				String fileName = scanItemContext.getInput().getAbsolutePath();
				classFileFounds.add(fileName);
				allClassesByteCodeFounds.add(fileName);
			}
		).scanAllZipFileThat(file -> 
			file.getName().endsWith(".jar") ||
			file.getName().endsWith(".war") ||
			file.getName().endsWith(".ear") ||
			file.getName().endsWith(".zip")
		).scanRecursivelyAllZipEntryThat(zipEntry -> 
			zipEntry.getName().endsWith(".jar") ||
			zipEntry.getName().endsWith(".war") ||
			zipEntry.getName().endsWith(".ear") ||
			zipEntry.getName().endsWith(".zip")
		).whenFindZipEntryTestAndApply(
			zipEntry -> zipEntry.getName().endsWith(".class"),  
			scanItemContext -> {
				String fileName = scanItemContext.getInput().getAbsolutePath();
				classZipEntryFounds.add(fileName);
				allClassesByteCodeFounds.add(fileName);
			}
		).setMaxParallelTasks(8);
		
		testNotEmpty(() -> {
			componentSupplier.getFileSystemHelper().scan(config);
			return allClassesByteCodeFounds;
		});
		logInfo("class file founds: " + classFileFounds.size());
		logInfo("class zip entry founds: " + classZipEntryFounds.size());
		
		return allClassesByteCodeFounds;
	}
	
	
	@Test
	public void findAllDirectoryWithRecursionTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		Collection<String> allFilesFound = ConcurrentHashMap.newKeySet();
		Configuration config = Configuration.forPaths(
			componentSupplier.getPathHelper().getAllClassPaths()
		).scanRecursivelyAllDirectoryAndApplyBefore(
			scanItemContext -> {
				String fileName = scanItemContext.getInput().getAbsolutePath();
				allFilesFound.add(fileName);
			}
		).setMaxParallelTasks(8);
		
		testNotEmpty(() -> {
			componentSupplier.getFileSystemHelper().scan(config);
			return allFilesFound;
		});
		
	}
		
	
	@Test
	public void findAllDirectoryWithNoRecursionTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		Collection<String> allFilesFound = ConcurrentHashMap.newKeySet();
		Configuration config = Configuration.forPaths(
			componentSupplier.getPathHelper().getAllClassPaths()
		).scanStrictlyDirectoryAndApplyBefore(
			scanItemContext -> {
				String fileName = scanItemContext.getInput().getAbsolutePath();
				allFilesFound.add(fileName);
			}
		).setMaxParallelTasks(8);
		
		testNotEmpty(() -> {
			componentSupplier.getFileSystemHelper().scan(config);
			return allFilesFound;
		});
	}
	
	
	@Test
	public void findAllDirectoryWithNoRecursionTestTwo() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		Collection<String> allFilesFound = ConcurrentHashMap.newKeySet();
		Configuration config = Configuration.forPaths(
			componentSupplier.getPathHelper().getAllClassPaths()
		).scanStrictlyDirectoryAndApplyBefore(
			scanItemContext -> {
				String fileName = scanItemContext.getInput().getAbsolutePath();
				allFilesFound.add(fileName);
			}
		).optimizePaths(
			true
		).setMaxParallelTasks(8);
		
		testNotEmpty(() -> {
			componentSupplier.getFileSystemHelper().scan(config);
			return allFilesFound;
		});
	}
	
	
	@Test
	public void findAllInZipWithNoRecursionTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		Collection<String> allFilesFound = ConcurrentHashMap.newKeySet();
		Configuration config = Configuration.forPaths(
			componentSupplier.getPathHelper().getAbsolutePath("libs-for-test.zip")
		).scanAllZipFileThat(file ->
			file.getName().endsWith(".zip")
		).whenFindZipEntryApply(  
			scanItemContext -> {
				String fileName = scanItemContext.getInput().getAbsolutePath();
				allFilesFound.add(fileName);
			}
		).setMaxParallelTasks(8);
		
		testNotEmpty(() -> {
			componentSupplier.getFileSystemHelper().scan(config);
			return allFilesFound;
		});
		
	}
	
	@Test
	public void findAllInZipWithRecursionTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		Collection<String> allFilesFound = ConcurrentHashMap.newKeySet();
		Configuration config = Configuration.forPaths(
			componentSupplier.getPathHelper().getAbsolutePath("libs-for-test.zip")
		).scanAllZipFileThat(file ->
			file.getName().endsWith(".zip")
		).whenFindZipEntryApply(  
			scanItemContext -> {
				String fileName = scanItemContext.getInput().getAbsolutePath();
				allFilesFound.add(fileName);
			}
		).scanRecursivelyAllZipEntryThat(zipEntry -> 
			zipEntry.getName().endsWith(".jar") ||
			zipEntry.getName().endsWith(".war") ||
			zipEntry.getName().endsWith(".ear") ||
			zipEntry.getName().endsWith(".zip")
		).setMaxParallelTasks(8);
		
		testNotEmpty(() -> {
			componentSupplier.getFileSystemHelper().scan(config);
			return allFilesFound;
		});
		
	}
}
