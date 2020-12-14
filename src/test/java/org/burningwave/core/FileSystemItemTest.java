package org.burningwave.core;

import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggersRepository;

import java.net.URL;

import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.PathHelper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

public class FileSystemItemTest extends BaseTest {
	
	@Test
	public void readTestOne() {
		testNotNull(() -> {
				ComponentSupplier componentSupplier = getComponentSupplier();
				return componentSupplier.getPathHelper().getResource(
					"/../../src/test/external-resources/libs-for-test.zip/ESC-Lib.ear/APP-INF/lib/bcel-5.1.jar/org/apache/bcel/generic/MethodGen$BranchTarget.class"
				).toByteBuffer();
			}
		);
	}
	
	@Test
	public void resetTestOne() {
		testNotNull(() -> {
				ComponentSupplier componentSupplier = getComponentSupplier();
				FileSystemItem fIS = componentSupplier.getPathHelper().getResource(
					"/../../src/test/external-resources/libs-for-test.zip/java.desktop.jmod/classes/javax/swing/UIManager$1.class"
				);
				fIS.toByteBuffer();
				fIS.reset();
				return fIS.toByteBuffer();
			}
		);
	}
	
	@Test
	public void resetTestTwo() {
		testNotNull(() -> {
				ComponentSupplier componentSupplier = getComponentSupplier();
				
				FileSystemItem fIS = componentSupplier.getPathHelper().getResource(
					"/../../src/test/external-resources/libs-for-test.zip/java.desktop.jmod/classes/javax/swing/UIManager$1.class"
				);
				FileSystemItem parent = componentSupplier.getPathHelper().getResource(
					"/../../src/test/external-resources/libs-for-test.zip/java.desktop.jmod/"
				);
				fIS.reset();
				parent.reset();
				fIS.toByteBuffer();
				fIS.reset();
				return fIS.toByteBuffer();
			}
		);
	}	
	
	
	@Test
	public void readTestTwo() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		String basePath = componentSupplier.getPathHelper().getPath((path) -> path.endsWith("target/test-classes"));
		testNotNull(() -> FileSystemItem.ofPath(
			basePath + "/../../src/test/external-resources/libs-for-test.zip/ESC-Lib.ear/APP-INF/lib/bcel-5.1.jar/org/apache/bcel/generic/MethodGen$BranchTarget.class"
		).toInputStream());
	}
	
	
	@Test
	public void readTestThree() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		String basePath = componentSupplier.getPathHelper().getPath((path) -> path.endsWith("target/test-classes"));
		testNotNull(() -> FileSystemItem.ofPath(
			basePath + "/../../src/test/external-resources/libs-for-test.zip/ESC-Lib.ear/APP-INF/lib/bcel-5.1.jar/org/apache/bcel/generic/MethodGen$BranchTarget.class"
		).getParent());
	}
	
	@Test
	public void readTestFour() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		String basePath = componentSupplier.getPathHelper().getPath((path) -> path.endsWith("target/test-classes"));
		testNotEmpty(() -> FileSystemItem.ofPath(
			basePath + "/../../src/test/external-resources/libs-for-test.zip/ESC-Lib.ear/APP-INF/lib/bcel-5.1.jar/org/apache/bcel/generic/MethodGen$BranchTarget.class"
		).getParent().getParent().getChildren());
	}
	
	@Test
	public void readTestFive() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		String basePath = componentSupplier.getPathHelper().getPath((path) -> path.endsWith("target/test-classes"));
		testNotEmpty(() -> FileSystemItem.ofPath(
			basePath + "/../../src/test/external-resources/libs-for-test.zip"
		).getChildren());
	}	
	
	@Test
	public void readTestSix() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		String basePath = componentSupplier.getPathHelper().getPath((path) -> path.endsWith("target/test-classes"));
		testNotEmpty(() -> FileSystemItem.ofPath(
			basePath + "/../../src/test/external-resources/libs-for-test.zip/ESC-Lib.ear/APP-INF/lib/bcel-5.1.jar"
		).getChildren());
	}
	
	@Test
	public void readTestSeven() {
		testNotEmpty(() -> {
				return FileSystemItem.ofPath(
					System.getProperty("os.name").toLowerCase().contains("windows")?
						"C:" : "/"
				).getChildren();
			}, 
			true
		);
	}
	
	@Test
	public void readTestTwentyThree() {
		testNotEmpty(() -> {
				return FileSystemItem.ofPath(
					System.getProperty("os.name").toLowerCase().contains("windows")?
						"C:/Windows" : "/home"
				).getParent().getChildren();
			}, true
		);
	}
		
	@Test
	public void readTestEight() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		String basePath = componentSupplier.getPathHelper().getPath((path) -> path.endsWith("target/test-classes"));
		testNotEmpty(() -> FileSystemItem.ofPath(
			basePath + "/../../src/test/external-resources/libs-for-test.zip/ESC-Lib.ear/APP-INF/lib/bcel-5.1.jar/org/apache/bcel/verifier"
		).getAllChildren());
	}
	
	@Test
	public void readTestNine() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotEmpty(() -> componentSupplier.getPathHelper().getResource(
			"/../../src/test/external-resources/libs-for-test.zip/ESC-Lib.ear/APP-INF/lib/bcel-5.1.jar"
		).getAllChildren());
	}
	
	@Test
	public void readTestTen() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotEmpty(() -> FileSystemItem.ofPath(
			componentSupplier.getPathHelper().getPath((path) -> path.endsWith("target/classes"))
		).getAllChildren());
	}
	
	@Test
	public void readTestEleven() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		String basePath = componentSupplier.getPathHelper().getPath((path) -> path.endsWith("target/test-classes"));
		testNotEmpty(() -> FileSystemItem.ofPath(
			basePath + "/../../src/test/external-resources/libs-for-test.zip/ESC-Lib.ear/APP-INF/lib/jaxb-xjc-2.1.7.jar/1.0"
		).getAllChildren());
	}
	
	@Test
	public void readTestTwelve() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		String basePath = componentSupplier.getPathHelper().getPath((path) -> path.endsWith("target/test-classes"));
		testNotEmpty(() -> FileSystemItem.ofPath(
			basePath + "/../../src/test/external-resources/libs-for-test.zip/ESC-Lib.ear/APP-INF/lib"
		).getChildren());
	}
	
	@Test
	public void readTestThirteen() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		String basePath = componentSupplier.getPathHelper().getPath((path) -> path.endsWith("target/test-classes"));
		testNotEmpty(() -> FileSystemItem.ofPath(
			basePath + "/../../src/test/external-resources/libs-for-test.zip"
		).getAllChildren());
	}
	
	@Test
	public void readTestFourteen() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotEmpty(() -> componentSupplier.getPathHelper().getResource(
			"/../../src/test/external-resources/libs-for-test.zip/ESC-Lib.ear/APP-INF/lib/jaxb-xjc-2.1.7.jar/1.0"
		).getChildren(), true);
	}
	
	@Test
	public void readTestFifteen() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		String basePath = componentSupplier.getPathHelper().getPath((path) -> path.endsWith("target/test-classes"));
		testNotEmpty(() -> FileSystemItem.ofPath(
			basePath + "/../../src/test/external-resources/libs-for-test.zip"
		).findInAllChildren(FileSystemItem.Criteria.forAllFileThat((fileSystemItem) -> fileSystemItem.getName().endsWith(".class"))),
		false);
	}
	
	@Test
	public void readTestSixteen() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		PathHelper pathHelper = componentSupplier.getPathHelper();
		testNotEmpty(() -> 
			pathHelper.getResource("/../../src/test/external-resources/libs-for-test.zip/java.desktop.jmod/classes")
		.getAllChildren(),
		false);
	}
	
	@Test
	public void readTestSeventeen() {
		testNotEmpty(() -> {
				ComponentSupplier componentSupplier = getComponentSupplier();
				PathHelper pathHelper = componentSupplier.getPathHelper();
	
				return pathHelper.getResource("/../../src/test/external-resources/libs-for-test.zip/java.desktop.jmod/classes")
					.getChildren();
			},
			true
		);
	}
	
	
	@Test
	public void readTestEighteen() {
		testNotEmpty(() -> {
				ComponentSupplier componentSupplier = getComponentSupplier();
				PathHelper pathHelper = componentSupplier.getPathHelper();
	
				return pathHelper.getResource("/../../src/test/external-resources/libs-for-test.zip/java.desktop.jmod/classes")
					.getChildren().stream().filter(FileSystemItem::isFolder).findFirst().get().getChildren();
			},
			true
		);
	}
	
	@Test
	public void readTestNineteen() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		PathHelper pathHelper = componentSupplier.getPathHelper();
		testNotEmpty(() -> 
			pathHelper.getResource("/../../src/test/external-resources/libs-for-test.zip/java.desktop.jmod")
		.getChildren(),
		true);
	}
	
	@Test
	public void readTestTwenty() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotEmpty(() -> componentSupplier.getPathHelper().getResource(
			"/../../src/test/external-resources/libs-for-test.zip/ESC-Lib.ear/APP-INF/lib/jaxb-xjc-2.1.7.jar"
		).getChildren(), true);
	}
	
	@Test
	public void readTestTwentyOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		PathHelper pathHelper = componentSupplier.getPathHelper();
		testNotEmpty(() -> 
			pathHelper.getResource("/../../src/test/external-resources/libs-for-test.zip/java.desktop.jmod/classes")
		.findInAllChildren(FileSystemItem.Criteria.forAllFileThat(FileSystemItem::isFolder)),
		false);
	}
	
	@Test
	public void readTestTwentyTwo() {
		testNotEmpty(() -> {
				ComponentSupplier componentSupplier = getComponentSupplier();
				PathHelper pathHelper = componentSupplier.getPathHelper();
				return pathHelper.getResource("/../../src/test/external-resources/libs-for-test.zip")
					.findInAllChildren(FileSystemItem.Criteria.forArchiveTypeFiles(FileSystemItem.CheckingOption.FOR_NAME_AND_SIGNATURE));
			},
			true
		);
	}
	
	@Test
	public void readWithFilterTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		PathHelper pathHelper = componentSupplier.getPathHelper();
		testNotEmpty(() -> pathHelper.getResource(
			"/../../src/test/external-resources/libs-for-test.zip"
		).findInAllChildren(
			FileSystemItem.Criteria.forAllFileThat(
				fileSystemItem -> "class".equals(fileSystemItem.getExtension())
			)
		),
		false);
	}
	
	@Test
	public void refreshTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		String basePath = componentSupplier.getPathHelper().getPath((path) -> path.endsWith("target/test-classes"));
		testNotEmpty(() -> {
			FileSystemItem fileSysteItem = FileSystemItem.ofPath(
				basePath + "/../../src/test/external-resources/libs-for-test.zip/ESC-Lib.ear/APP-INF/lib/jaxb-xjc-2.1.7.jar/1.0"
			);
			fileSysteItem.getAllChildren();
			fileSysteItem.refresh();
			return fileSysteItem.getAllChildren();
		});
	}
	
	@Test
	public void refreshTestTwo() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		String basePath = componentSupplier.getPathHelper().getPath((path) -> path.endsWith("target/test-classes"));
		testNotEmpty(() -> {
			FileSystemItem fileSysteItem = FileSystemItem.ofPath(
				basePath + "/../../src/test/external-resources/libs-for-test.zip/ESC-Lib.ear/APP-INF/lib/jaxb-xjc-2.1.7.jar/1.0"
			);
			fileSysteItem.getChildren();
			fileSysteItem.refresh();
			return fileSysteItem.getChildren();
		});
	}
	
	@Test
	public void copyFileTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		String basePath = componentSupplier.getPathHelper().getPath((path) -> path.endsWith("target/test-classes"));
		testNotEmpty(() -> FileSystemItem.ofPath(
			basePath + "/../../src/test/external-resources/libs-for-test.zip"
		).copyTo(System.getProperty("user.home") + "/Desktop/bw-tests").getChildren());
	}
	
	@Test
	public void copyFileTestTwo() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		String basePath = componentSupplier.getPathHelper().getPath((path) -> path.endsWith("target/test-classes"));
		testNotEmpty(() -> FileSystemItem.ofPath(
			basePath + "/../../src/test/external-resources/libs-for-test.zip/ESC-Lib.ear/APP-INF/lib/jaxb-xjc-2.1.7.jar"
		).copyTo(System.getProperty("user.home") + "/Desktop/bw-tests").getChildren());
	}
	
	@Test
	public void copyFolderTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		String basePath = componentSupplier.getPathHelper().getPath((path) -> path.endsWith("target/test-classes"));
		testNotEmpty(() -> FileSystemItem.ofPath(
			basePath + "/../../src/test/external-resources/libs-for-test.zip/META-INF"
		).copyTo(System.getProperty("user.home") + "/Desktop/bw-tests").getChildren());
	}
	
	@Test
	public void copyFolderTestTwo() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotEmpty(() -> 
			componentSupplier.getPathHelper().getResource(
				"/../../src/test/external-resources/libs-for-test.zip/ESC-Lib.ear/APP-INF/lib/jaxb-xjc-2.1.7.jar/1.0"
			).copyTo(
				System.getProperty("user.home") + "/Desktop/bw-tests"
			).getChildren()
		);
	}
	
	@Test
	public void toByteBufferTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		String basePath = componentSupplier.getPathHelper().getPath((path) -> path.endsWith("target/test-classes"));
		testNotNull(() -> {
			FileSystemItem fIS = FileSystemItem.ofPath(basePath + "/../../src/test/external-resources/libs-for-test.zip/ESC-Lib.ear");
			fIS.toByteBuffer();
			fIS.reset();
			return fIS.toByteBuffer();
		});
	}
	
	@Test
	@Tag("Heavy")
	public void copyAllChildrenTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		PathHelper pathHelper = componentSupplier.getPathHelper();
		testNotEmpty(() -> 
			pathHelper.getResource(
				"/../../src/test/external-resources/libs-for-test.zip"
			).copyAllChildrenTo(System.getProperty("user.home") + "/Desktop/bw-tests").getAllChildren()
		);
	}
	
	@Test
	public void copyAllChildrenTestTwo() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotEmpty(() -> 
			componentSupplier.getPathHelper().getResource(
				"/../../src/test/external-resources/libs-for-test.zip/ESC-Lib.ear/APP-INF/lib/bcel-5.1.jar"
			).copyAllChildrenTo(System.getProperty("user.home") + "/Desktop/bw-tests").findFirstInAllChildren(
				FileSystemItem.Criteria.forAllFileThat(file -> "org".equals(file.getName()))
			).getAllChildren()
		);
	}
	
	@Test
	public void toUrlTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		String basePath = componentSupplier.getPathHelper().getPath((path) -> path.endsWith("target/test-classes"));
		testNotNull(() -> {
			URL url = FileSystemItem.ofPath(
				basePath + "/../../src/test/external-resources/libs-for-test.zip/ESC-Lib.ear/APP-INF/lib/jaxb-xjc-2.1.7.jar/1.0"
			).getURL();
			ManagedLoggersRepository.logDebug(getClass()::getName, url.toString());
			return url;
		});
	}
}
