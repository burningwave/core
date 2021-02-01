package org.burningwave.core;

import static org.junit.Assert.assertTrue;

import java.lang.reflect.Modifier;

import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.assembler.StaticComponentContainer;
import org.burningwave.core.classes.ClassSourceGenerator;
import org.burningwave.core.classes.JavaMemoryCompiler;
import org.burningwave.core.classes.MemoryClassLoader;
import org.burningwave.core.classes.PropertyAccessor;
import org.burningwave.core.classes.TypeDeclarationSourceGenerator;
import org.burningwave.core.classes.UnitSourceGenerator;
import org.junit.jupiter.api.Test;

public class MemoryClassLoaderTest extends BaseTest {
	
	protected MemoryClassLoader getMemoryClassLoader(ClassLoader parent) {
		return MemoryClassLoader.create(
				parent
		);
	}
	
	UnitSourceGenerator generateSources() {
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
		return unitSG;
	}
	
	@Test
	public void loadClassTestOne() {
		testNotNull(() -> {
			try(MemoryClassLoader memoryClassLoader = getMemoryClassLoader(null);) {
				return memoryClassLoader.loadOrDefineClass(PropertyAccessor.class);
			}			
		});
	}
	
	@Test
	public void getResourceAsStreamTestOne() throws ClassNotFoundException {
		testNotNull(() -> {
			try(MemoryClassLoader memoryClassLoader = getMemoryClassLoader(null);) {
				memoryClassLoader.loadOrDefineClass(PropertyAccessor.class);
				return memoryClassLoader.getResourceAsStream(PropertyAccessor.class.getName().replace(".", "/") + ".class");
			}			
		});
	}
	
	@Test
	public void getResourceAsStreamTestTwo() throws ClassNotFoundException {
		try(MemoryClassLoader memoryClassLoader = getMemoryClassLoader(null);) {
			memoryClassLoader.loadOrDefineClass(PropertyAccessor.class);
			assertTrue(memoryClassLoader.hasPackageBeenDefined(PropertyAccessor.class.getPackage().getName()));
		}
	}
	
	@Test
	public void loadClassTestTwo() throws ClassNotFoundException {
		testNotNull(() -> {
			try(MemoryClassLoader memoryClassLoader = getMemoryClassLoader(null);) {
				ComponentSupplier componentSupplier = getComponentSupplier();
				JavaMemoryCompiler jMC = componentSupplier.getJavaMemoryCompiler();
				memoryClassLoader.addByteCodes(
					jMC.compile(
						JavaMemoryCompiler.Compilation.Config.forUnitSourceGenerator(generateSources())
					).join().getCompiledFiles().entrySet()
				);
				return memoryClassLoader.loadClass("tryyy.ReTry$ReReTry");
			}
		});
	}
	
	@Test
	public void forceCompiledClassesLoadingTestOne() throws ClassNotFoundException {
		testNotEmpty(() -> {
			try(MemoryClassLoader memoryClassLoader = getMemoryClassLoader(null);) {
				ComponentSupplier componentSupplier = getComponentSupplier();
				JavaMemoryCompiler jMC = componentSupplier.getJavaMemoryCompiler();
				memoryClassLoader.addByteCodes(
					jMC.compile(
						JavaMemoryCompiler.Compilation.Config.forUnitSourceGenerator(generateSources())
					).join().getCompiledFiles().entrySet()
				);
				return memoryClassLoader.forceBytecodesLoading();
			}
		});
	}
	
	@Test
	public void getNotLoadedByteCodeTestOne() throws ClassNotFoundException {
		testNotNull(() -> {
			try(MemoryClassLoader memoryClassLoader = getMemoryClassLoader(null);) {
				ComponentSupplier componentSupplier = getComponentSupplier();
				JavaMemoryCompiler jMC = componentSupplier.getJavaMemoryCompiler();
				memoryClassLoader.addByteCodes(
					jMC.compile(
						JavaMemoryCompiler.Compilation.Config.forUnitSourceGenerator(generateSources())
					).join().getCompiledFiles().entrySet()
				);
				return memoryClassLoader.getNotLoadedByteCode("tryyy.ReTry$ReReTry");
			}
		});
	}
	
	@Test
	public void getByteCodeOfTestOne() throws ClassNotFoundException {
		testNotNull(() -> {
			try(MemoryClassLoader memoryClassLoader = getMemoryClassLoader(null);) {
				ComponentSupplier componentSupplier = getComponentSupplier();
				JavaMemoryCompiler jMC = componentSupplier.getJavaMemoryCompiler();
				memoryClassLoader.addByteCodes(
					jMC.compile(
						JavaMemoryCompiler.Compilation.Config.forUnitSourceGenerator(generateSources())
					).join().getCompiledFiles().entrySet()
				);
				return memoryClassLoader.getByteCodeOf("tryyy.ReTry$ReReTry");
			}
		});
	}
}
