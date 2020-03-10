package org.burningwave.core;

import static org.burningwave.core.assembler.StaticComponentContainer.Classes;
import static org.burningwave.core.assembler.StaticComponentContainer.ConstructorHelper;

import java.lang.reflect.Modifier;
import java.util.ArrayList;

import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.bean.Complex;
import org.burningwave.core.bean.PojoInterface;
import org.burningwave.core.classes.ClassFactory.PojoSubTypeRetriever;
import org.burningwave.core.classes.ClassSourceGenerator;
import org.burningwave.core.classes.TypeDeclarationSourceGenerator;
import org.burningwave.core.classes.UnitSourceGenerator;
import org.burningwave.core.service.Service;
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
		java.lang.Class<?> cls = componentSupplier.getClassFactory().getOrBuildPojoSubType(
			getComponentSupplier().getMemoryClassLoader(),
			this.getClass().getPackage().getName() + ".TestTwoPojoImpl",
			PojoSubTypeRetriever.SourceGenerator.BUILDING_METHODS_CREATION_ENABLED,
			Complex.Data.Item.class,
			PojoInterface.class
		);
		testNotNull(() -> {
			java.lang.Class<?> reloadedCls = componentSupplier.getClassFactory().getOrBuildPojoSubType(
				getComponentSupplier().getMemoryClassLoader(), cls.getPackage().getName() + ".ExtendedPojoImpl",
				PojoSubTypeRetriever.SourceGenerator.BUILDING_METHODS_CREATION_ENABLED, cls
			);
			java.lang.reflect.Method createMethod = Classes.getDeclaredMethods(reloadedCls, method -> 
				method.getName().equals("create") &&
				method.getParameterTypes()[0].equals(String.class)).stream().findFirst().orElse(null);
			PojoInterface pojoObject = (PojoInterface)createMethod.invoke(null, "try");
			return pojoObject;
		});
	}
	
	@Test
	public void getOrBuildClassTestOne() throws ClassNotFoundException {
		ComponentSupplier componentSupplier = getComponentSupplier();

		testNotNull(() -> {
			ClassSourceGenerator ClassSG = ClassSourceGenerator.create(
				TypeDeclarationSourceGenerator.create("ReTry")
			).addModifier(
				Modifier.PUBLIC
			).addInnerClass(
				ClassSourceGenerator.create(
					TypeDeclarationSourceGenerator.create("ReReTry")
				).addModifier(
					Modifier.PUBLIC | Modifier.STATIC
				)
			);
			UnitSourceGenerator unitSG = UnitSourceGenerator.create("tryyy").addClass(
				ClassSG
			);
			return componentSupplier.getClassFactory().getOrBuild(
				getComponentSupplier().getMemoryClassLoader(),
				"tryyy.ReTry", () -> unitSG
			);
		});
		testNotNull(() -> 
			componentSupplier.getMemoryClassLoader().loadClass("tryyy.ReTry$ReReTry")
		);
	}

	@Test
	public void getOrBuildPojoClassTestThree() throws Exception {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotNull(() -> {
			java.lang.Class<?> virtualClass = componentSupplier.getClassFactory().getOrBuildPojoSubType(
				getComponentSupplier().getMemoryClassLoader(), this.getClass().getPackage().getName() + ".TestThreePojoImpl", 
				Service.class,
				PojoInterface.class
			);
			Virtual virtual = (Virtual)ConstructorHelper.newInstanceOf(virtualClass);
			virtual.invokeDirect("setList", new ArrayList<>());
			virtual.invoke("setList", new ArrayList<>());
			virtual.invokeDirect("setList", new ArrayList<>());
			virtual.invoke("consume", new Integer(1));
			virtual.invokeDirect("consume", new Integer(1));
			return virtual;
			}
		);
	}
}
