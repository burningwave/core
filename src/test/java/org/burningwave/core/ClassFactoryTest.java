package org.burningwave.core;

import static org.burningwave.core.assembler.StaticComponentContainer.Classes;
import static org.burningwave.core.assembler.StaticComponentContainer.ConstructorHelper;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.bean.Complex;
import org.burningwave.core.bean.PojoInterface;
import org.burningwave.core.classes.ClassSourceGenerator;
import org.burningwave.core.classes.PojoSourceGenerator;
import org.burningwave.core.classes.StatementSourceGenerator;
import org.burningwave.core.classes.TypeDeclarationSourceGenerator;
import org.burningwave.core.classes.UnitSourceGenerator;
import org.burningwave.core.service.Service;
import org.junit.jupiter.api.Test;

public class ClassFactoryTest extends BaseTest {
	
	@Test
	public void getOrBuildFunctionClassTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotNull(() -> componentSupplier.getClassFactory().getOrBuildFunctionSubType(Thread.currentThread().getContextClassLoader(), 10));
	}	
	
	@Test
	public void getOrBuildConsumerClassTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotNull(() -> componentSupplier.getClassFactory().getOrBuildConsumerSubType(Thread.currentThread().getContextClassLoader(), 2));
	}
	
	@Test
	public void getOrBuildPredicateClassTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotNull(() -> componentSupplier.getClassFactory().getOrBuildPredicateSubType(Thread.currentThread().getContextClassLoader(), 10));
	}
	
	
	@Test
	public void getOrBuildPojoClassTestOne() throws Exception {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotNull(() -> 
			componentSupplier.getClassFactory().getOrBuildPojoSubType(
				Thread.currentThread().getContextClassLoader(), this.getClass().getPackage().getName() + ".SimpleVirtual"
			)
		);
	}
	
	
	@Test
	public void getOrBuildPojoClassTestTwo() throws Exception {
		ComponentSupplier componentSupplier = getComponentSupplier();
		java.lang.Class<?> cls = componentSupplier.getClassFactory().getOrBuildPojoSubType(
			Thread.currentThread().getContextClassLoader(),
			this.getClass().getPackage().getName() + ".TestTwoPojoImpl",
			PojoSourceGenerator.BUILDING_METHODS_CREATION_ENABLED,
			Complex.Data.Item.class,
			PojoInterface.class
		);
		testNotNull(() -> {
			Class<?> reloadedCls = componentSupplier.getClassFactory().getOrBuildPojoSubType(
				Thread.currentThread().getContextClassLoader(), cls.getPackage().getName() + ".ExtendedPojoImpl",
				PojoSourceGenerator.BUILDING_METHODS_CREATION_ENABLED, cls
			);
			Method createMethod = Classes.getDeclaredMethods(reloadedCls, method -> 
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
				"tryyy.ReTry", unitSG
			);
		});
		testNotNull(() -> 
			componentSupplier.getClassFactory().getOrBuild("tryyy.ReTry$ReReTry", null)
		);
	}

	@Test
	public void getOrBuildPojoClassTestThree() throws Exception {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotNull(() -> {
			java.lang.Class<?> virtualClass = componentSupplier.getClassFactory().getOrBuildPojoSubType(
				Thread.currentThread().getContextClassLoader(), this.getClass().getPackage().getName() + ".TestThreePojoImpl", 
				Service.class,
				PojoInterface.class
			);
			Virtual virtual = (Virtual)ConstructorHelper.newInstanceOf(virtualClass);
			virtual.invokeDirect("setList", new ArrayList<>());
			virtual.invoke("setList", new ArrayList<>());
			virtual.invokeDirect("setList", new ArrayList<>());
			virtual.invoke("consume", Integer.valueOf(1));
			virtual.invokeDirect("consume", Integer.valueOf(1));
			return virtual;
			}
		);
	}
	
	@Test
	public void executeCodeTest() throws Exception {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotNull(() -> {
			StatementSourceGenerator statementSG = StatementSourceGenerator.createSimple().setElementPrefix("\t");
			statementSG.useType(ArrayList.class);
			statementSG.useType(List.class);
			statementSG.addCodeRow("System.out.println(\"number to add: \" + parameter[0]);");
			statementSG.addCodeRow("List<Integer> numbers = new ArrayList<>();");
			statementSG.addCodeRow("numbers.add((Integer)parameter[0]);");
			statementSG.addCodeRow("System.out.println(\"number list size: \" + numbers.size());");
			statementSG.addCodeRow("System.out.println(\"number in the list: \" + numbers.get(0));");
			statementSG.addCodeRow("Integer inputNumber = (Integer)parameter[0];");
			statementSG.addCodeRow("return (T)inputNumber++;");
			return componentSupplier.getClassFactory().execute(statementSG, Integer.valueOf(5));
		});
	}
}
