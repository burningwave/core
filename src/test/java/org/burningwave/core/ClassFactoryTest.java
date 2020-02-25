package org.burningwave.core;

import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.bean.Complex;
import org.burningwave.core.bean.PojoInterface;
import org.burningwave.core.io.FileSystemItem;
import org.junit.jupiter.api.Test;

public class ClassFactoryTest extends BaseTest {
	
	@Test
	public void getOrBuildFunctionClassTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotNull(() -> componentSupplier.getClassFactory().getOrBuildFunctionSubType(getComponentSupplier().getMemoryClassLoader(), 10));
	}	
	
	@Test
	public void getOrBuildConsumerClassTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotNull(() -> componentSupplier.getClassFactory().getOrBuildConsumerSubType(getComponentSupplier().getMemoryClassLoader(), 2));
	}
	
	@Test
	public void getOrBuildPredicateClassTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotNull(() -> componentSupplier.getClassFactory().getOrBuildPredicateSubType(getComponentSupplier().getMemoryClassLoader(), 10));
	}
	
	
	@Test
	public void getOrBuildPojoClassTestOne() throws Exception {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotNull(() -> 
			componentSupplier.getClassFactory().getOrBuildPojoSubType(
				getComponentSupplier().getMemoryClassLoader(), this.getClass().getPackage().getName() + ".SimpleVirtual"
			)
		);
	}
	
	
	@Test
	public void getOrBuildPojoClassTestTwo() throws Exception {
		ComponentSupplier componentSupplier = getComponentSupplier();
		Class<?> cls = componentSupplier.getClassFactory().getOrBuildPojoSubType(
			getComponentSupplier().getMemoryClassLoader(), this.getClass().getPackage().getName() + ".PojoImpl",
			Complex.Data.Item.class,
			PojoInterface.class
		);
		testNotNull(() -> 
			componentSupplier.getClassFactory().getOrBuildPojoSubType(
				getComponentSupplier().getMemoryClassLoader(), cls.getPackage().getName() + ".ExtendedPojoImpl", cls
			)			
		);
	}
	
	@Test
	public void getOrBuildClassTestOne() throws ClassNotFoundException {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotNull(() -> componentSupplier.getClassFactory().getOrBuild(
			"package prova;\n" +
			"public class RiProva {\n" +
				"\tpublic static class RiRiprova {\n\n" +
						 
				"\t}\n"+
			 "}", getComponentSupplier().getMemoryClassLoader()
		));
		testNotNull(() -> 
			componentSupplier.getMemoryClassLoader().loadClass("prova.RiProva$RiRiprova")
		);
	}

	@Test
	public void getOrBuildPojoClassTestThree() throws Exception {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotNull(() -> 
			componentSupplier.getClassFactory().getOrBuildPojoSubType(
				getComponentSupplier().getMemoryClassLoader(), this.getClass().getPackage().getName() + ".PojoImpl", 
				FileSystemItem.class,
				PojoInterface.class
			)
		);
	}
}
