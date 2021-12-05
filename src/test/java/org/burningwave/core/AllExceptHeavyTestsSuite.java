package org.burningwave.core;

import org.junit.platform.runner.JUnitPlatform;
import org.junit.platform.suite.api.ExcludeTags;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.runner.RunWith;

@SuppressWarnings("unused")
@RunWith(JUnitPlatform.class)
//@SelectPackages("org.burningwave.core")
@SelectClasses({
	ModulesTest.class,
	BackgroundExecutorTest.class,
	ByteBufferHandlerTest.class,
	ByteCodeHunterTest.class,
    ClassesTest.class,
    ClassFactoryTest.class,
//    ComponentContainerTest.class,
    ClassHunterTest.class,
//    ClassLoadersTest.class,
    ClassPathHunterTest.class,
    CodeExecutorTest.class,
    ConstructorsTest.class,
    FieldsTest.class,
    FileSystemHelperTest.class,
    FileSystemItemTest.class,
    FunctionalInterfaceFactoryTest.class,
    IterableObjectHelperTest.class,
    IterableZipContainerTest.class,
    ManagedLoggerRepositoryTest.class,
    MembersTest.class,
    MemoryClassLoaderTest.class,
    MethodsTest.class,
    PathHelperTest.class,
    PathScannerClassLoaderTest.class,
    PropertyAccessorTest.class,
    PropertiesTest.class,
    SourceCodeHandlerTest.class,
    StringsTest.class,
    ThreadSupplierTest.class,
    UnitSourceGeneratorTest.class,
    RepeatedClassFactoryTest.class,
//    RepeatedComponentContainerTest.class
})
@ExcludeTags("Heavy")
public class AllExceptHeavyTestsSuite {

}
