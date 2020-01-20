package org.burningwave.core;

import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.io.FileSystemItem;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

public class FileSystemItemTest extends BaseTest {
	
	@Test
	public void readTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		String basePath = componentSupplier.getPathHelper().getClassPath((path) -> path.endsWith("target/test-classes"));
		testNotNull(() -> FileSystemItem.ofPath(
			basePath + "/libs-for-test.zip/ESC-Lib.ear/APP-INF/lib/bcel-5.1.jar/org/apache/bcel/generic/MethodGen$BranchTarget.class"
		).toByteBuffer());
	}
	
	@Test
	public void readTestTwo() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		String basePath = componentSupplier.getPathHelper().getClassPath((path) -> path.endsWith("target/test-classes"));
		testNotNull(() -> FileSystemItem.ofPath(
			basePath + "/libs-for-test.zip/ESC-Lib.ear/APP-INF/lib/bcel-5.1.jar/org/apache/bcel/generic/MethodGen$BranchTarget.class"
		).toInputStream());
	}
	
	
	@Test
	public void readTestThree() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		String basePath = componentSupplier.getPathHelper().getClassPath((path) -> path.endsWith("target/test-classes"));
		testNotNull(() -> FileSystemItem.ofPath(
			basePath + "/libs-for-test.zip/ESC-Lib.ear/APP-INF/lib/bcel-5.1.jar/org/apache/bcel/generic/MethodGen$BranchTarget.class"
		).getParent());
	}
	
	@Test
	public void readTestFour() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		String basePath = componentSupplier.getPathHelper().getClassPath((path) -> path.endsWith("target/test-classes"));
		testNotEmpty(() -> FileSystemItem.ofPath(
			basePath + "/libs-for-test.zip/ESC-Lib.ear/APP-INF/lib/bcel-5.1.jar/org/apache/bcel/generic/MethodGen$BranchTarget.class"
		).getParent().getParent().getChildren());
	}
	
	@Test
	public void readTestFive() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		String basePath = componentSupplier.getPathHelper().getClassPath((path) -> path.endsWith("target/test-classes"));
		testNotEmpty(() -> FileSystemItem.ofPath(
			basePath + "/libs-for-test.zip"
		).getChildren());
	}
	
	
	@Test
	public void readTestSix() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		String basePath = componentSupplier.getPathHelper().getClassPath((path) -> path.endsWith("target/test-classes"));
		testNotEmpty(() -> FileSystemItem.ofPath(
			basePath + "/libs-for-test.zip/ESC-Lib.ear/APP-INF/lib/bcel-5.1.jar"
		).getChildren());
	}
	
	@Test
	public void readTestSeven() {
		testNotEmpty(() -> 
			FileSystemItem.ofPath(
				System.getProperty("os.name").toLowerCase().contains("windows")?
					"C:" : "/"
			).getChildren(), 
			true
		);
	}
		
	@Test
	public void readTestEight() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		String basePath = componentSupplier.getPathHelper().getClassPath((path) -> path.endsWith("target/test-classes"));
		testNotEmpty(() -> FileSystemItem.ofPath(
			basePath + "/libs-for-test.zip/ESC-Lib.ear/APP-INF/lib/bcel-5.1.jar/org/apache/bcel/verifier"
		).getAllChildren());
	}
	
	@Test
	@Tag("Heavy")
	public void readTestNine() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		String basePath = componentSupplier.getPathHelper().getClassPath((path) -> path.endsWith("target/test-classes"));
		testNotEmpty(() -> FileSystemItem.ofPath(
			basePath + "/libs-for-test.zip/ESC-Lib.ear/APP-INF/lib/bcel-5.1.jar"
		).getAllChildren());
	}
	
	@Test
	public void readTestTen() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotEmpty(() -> FileSystemItem.ofPath(
			componentSupplier.getPathHelper().getClassPath((path) -> path.endsWith("target/classes"))
		).getAllChildren());
	}
	
	@Test
	public void readTestEleven() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		String basePath = componentSupplier.getPathHelper().getClassPath((path) -> path.endsWith("target/test-classes"));
		testNotEmpty(() -> FileSystemItem.ofPath(
			basePath + "/libs-for-test.zip/ESC-Lib.ear/APP-INF/lib/jaxb-xjc-2.1.7.jar/1.0"
		).getAllChildren());
	}
	
	@Test
	public void readTestTwelve() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		String basePath = componentSupplier.getPathHelper().getClassPath((path) -> path.endsWith("target/test-classes"));
		testNotEmpty(() -> FileSystemItem.ofPath(
			basePath + "/libs-for-test.zip/ESC-Lib.ear/APP-INF/lib"
		).getChildren());
	}
	
	
	@Test
	@Tag("Heavy")
	public void readTestTestThirteen() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		String basePath = componentSupplier.getPathHelper().getClassPath((path) -> path.endsWith("target/test-classes"));
		testNotEmpty(() -> FileSystemItem.ofPath(
			basePath + "/libs-for-test.zip"
		).getAllChildren((fileSystemItem) -> fileSystemItem.getName().endsWith(".class")),
		true);
	}
	
	@Test
	public void copyFileTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		String basePath = componentSupplier.getPathHelper().getClassPath((path) -> path.endsWith("target/test-classes"));
		testNotEmpty(() -> FileSystemItem.ofPath(
			basePath + "/libs-for-test.zip"
		).copyTo(System.getProperty("user.home") + "/Desktop/bw-tests").getChildren());
	}
	
	@Test
	public void copyFileTestTwo() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		String basePath = componentSupplier.getPathHelper().getClassPath((path) -> path.endsWith("target/test-classes"));
		testNotEmpty(() -> FileSystemItem.ofPath(
			basePath + "/libs-for-test.zip/ESC-Lib.ear/APP-INF/lib/jaxb-xjc-2.1.7.jar"
		).copyTo(System.getProperty("user.home") + "/Desktop/bw-tests").getChildren());
	}
	
	@Test
	public void copyFolderTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		String basePath = componentSupplier.getPathHelper().getClassPath((path) -> path.endsWith("target/test-classes"));
		testNotEmpty(() -> FileSystemItem.ofPath(
			basePath + "/libs-for-test.zip/META-INF"
		).copyTo(System.getProperty("user.home") + "/Desktop/bw-tests").getChildren());
	}
	
	@Test
	@Tag("Heavy")
	public void copyFolderTestTwo() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		String basePath = componentSupplier.getPathHelper().getClassPath((path) -> path.endsWith("target/test-classes"));
		testNotEmpty(() -> FileSystemItem.ofPath(
			basePath + "/libs-for-test.zip/ESC-Lib.ear/APP-INF/lib/jaxb-xjc-2.1.7.jar/1.0"
		).copyTo(System.getProperty("user.home") + "/Desktop/bw-tests").getChildren());
	}
}
