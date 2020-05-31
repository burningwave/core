package org.burningwave.core;

import static org.junit.Assert.assertTrue;

import java.lang.reflect.Modifier;
import java.util.Arrays;

import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.assembler.StaticComponentContainer;
import org.burningwave.core.classes.ClassSourceGenerator;
import org.burningwave.core.classes.JavaMemoryCompiler;
import org.burningwave.core.classes.MemoryClassLoader;
import org.burningwave.core.classes.TypeDeclarationSourceGenerator;
import org.burningwave.core.classes.UnitSourceGenerator;
import org.burningwave.core.reflection.PropertyAccessor;
import org.junit.jupiter.api.Test;

public class MemoryClassLoaderTest extends BaseTest {
	
	protected MemoryClassLoader getMemoryClassLoader(ClassLoader parent) {
		return MemoryClassLoader.create(
				parent
		);
	}
	
	@Test
	public void loadClassTestOne() {
		testNotNull(() ->
			getMemoryClassLoader(null).loadOrUploadClass(PropertyAccessor.class)
		);
	}
	
	@Test
	public void getResourceAsStreamTestOne() throws ClassNotFoundException {
		MemoryClassLoader memoryClassLoader = getMemoryClassLoader(null);
		memoryClassLoader.loadOrUploadClass(PropertyAccessor.class);
		testNotNull(() ->
			memoryClassLoader.getResourceAsStream(PropertyAccessor.class.getName().replace(".", "/") + ".class")
		);
	}
	
	@Test
	public void getResourceAsStreamTestTwo() throws ClassNotFoundException {
		MemoryClassLoader memoryClassLoader = getMemoryClassLoader(null);
		memoryClassLoader.loadOrUploadClass(PropertyAccessor.class);
		assertTrue(memoryClassLoader.hasPackageBeenDefined(PropertyAccessor.class.getPackage().getName()));
	}
	
	@Test
	public void loadClassTestTwo() throws ClassNotFoundException {
		testNotNull(() -> {
			ComponentSupplier componentSupplier = getComponentSupplier();
			ClassSourceGenerator ClassSG = ClassSourceGenerator.create(
				TypeDeclarationSourceGenerator.create("ReTry")
			).addModifier(
				Modifier.PUBLIC
			).addInnerClass(
				ClassSourceGenerator.create(
					TypeDeclarationSourceGenerator.create("ReReTry")
				).addModifier(
					Modifier.PUBLIC | Modifier.STATIC
				).expands(TypeDeclarationSourceGenerator.create("ReTry"))
			);
			UnitSourceGenerator unitSG = UnitSourceGenerator.create("tryyy").addClass(
				ClassSG
			).addStaticImport(StaticComponentContainer.class, "Streams", "Classes");
			JavaMemoryCompiler jMC = componentSupplier.getJavaMemoryCompiler();
			MemoryClassLoader memoryClassLoader = getMemoryClassLoader(null);
			memoryClassLoader.addByteCodes(jMC.compile(Arrays.asList(unitSG.make())).entrySet());
			return memoryClassLoader.loadClass("tryyy.ReTry$ReReTry");
		});
	}
	
}
