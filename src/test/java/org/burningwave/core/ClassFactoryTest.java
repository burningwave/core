package org.burningwave.core;

import static org.burningwave.core.assembler.StaticComponentContainer.Classes;
import static org.burningwave.core.assembler.StaticComponentContainer.Constructors;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.bean.Complex;
import org.burningwave.core.bean.PojoInterface;
import org.burningwave.core.classes.ClassFactory;
import org.burningwave.core.classes.ClassSourceGenerator;
import org.burningwave.core.classes.FunctionSourceGenerator;
import org.burningwave.core.classes.PojoSourceGenerator;
import org.burningwave.core.classes.StatementSourceGenerator;
import org.burningwave.core.classes.TypeDeclarationSourceGenerator;
import org.burningwave.core.classes.UnitSourceGenerator;
import org.burningwave.core.classes.VariableSourceGenerator;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.service.Service;
import org.junit.jupiter.api.Test;

public class ClassFactoryTest extends BaseTest {
	
	@Test
	public void getOrBuildFunctionClassTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotNull(() -> componentSupplier.getClassFactory().buildFunctionSubTypeAndLoadOrUploadTo(Thread.currentThread().getContextClassLoader(), 10));
	}	
	
	@Test
	public void getOrBuildConsumerClassTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotNull(() -> componentSupplier.getClassFactory().buildConsumerSubTypeAndLoadOrUploadTo(Thread.currentThread().getContextClassLoader(), 2));
	}
	
	@Test
	public void getOrBuildPredicateClassTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotNull(() -> componentSupplier.getClassFactory().buildPredicateSubTypeAndLoadOrUploadTo(Thread.currentThread().getContextClassLoader(), 10));
	}
	
	
	@Test
	public void getOrBuildPojoClassTestOne() throws Exception {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotNull(() -> 
			componentSupplier.getClassFactory().buildPojoSubTypeAndLoadOrUploadTo(
				Thread.currentThread().getContextClassLoader(), this.getClass().getPackage().getName() + ".SimpleVirtual"
			)
		);
	}
	
	
	@Test
	public void getOrBuildPojoClassTestTwo() throws Exception {
		ComponentSupplier componentSupplier = getComponentSupplier();
		java.lang.Class<?> cls = componentSupplier.getClassFactory().buildPojoSubTypeAndLoadOrUploadTo(
			Thread.currentThread().getContextClassLoader(),
			this.getClass().getPackage().getName() + ".TestTwoPojoImpl",
			PojoSourceGenerator.BUILDING_METHODS_CREATION_ENABLED,
			Complex.Data.Item.class,
			PojoInterface.class
		);
		testNotNull(() -> {
			Class<?> reloadedCls = componentSupplier.getClassFactory().buildPojoSubTypeAndLoadOrUploadTo(
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
	public void getOrBuildClassTestOne() {
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
			)
		);
		UnitSourceGenerator unitSG = UnitSourceGenerator.create("tryyy").addClass(
			ClassSG
		);
		testNotNull(() -> {
			return componentSupplier.getClassFactory().buildAndLoadOrUpload(
				unitSG
			).get(
				"tryyy.ReTry"
			);
		});
		testNotNull(() -> 
			componentSupplier.getClassFactory().buildAndLoadOrUpload(unitSG).get("tryyy.ReTry$ReReTry")
		);
	}
	
	
	@Test
	public void getOrBuildClassWithExternalClassOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		UnitSourceGenerator unitSG = UnitSourceGenerator.create("packagename").addClass(
			ClassSourceGenerator.create(
				TypeDeclarationSourceGenerator.create("ComplexExample")
			).addModifier(
				Modifier.PUBLIC
			).expands(
				TypeDeclarationSourceGenerator.create("SOAPPartImpl")
			).addConstructor(
				FunctionSourceGenerator.create().addParameter(
					VariableSourceGenerator.create(TypeDeclarationSourceGenerator.create("SOAPMessageImpl"), "parentSoapMsg"),
					VariableSourceGenerator.create(TypeDeclarationSourceGenerator.create(InputStream.class), "inputStream")
				).addThrowable(
					TypeDeclarationSourceGenerator.create("SOAPException")				
				).addBodyCodeRow("super(parentSoapMsg, inputStream);")
			)
		).addImport(
			"org.apache.axis2.saaj.SOAPPartImpl",
			"org.apache.axis2.saaj.SOAPMessageImpl",
			"javax.xml.soap.SOAPException"
		);
		testNotNull(() -> {
			ClassFactory.ClassRetriever classRetriever = componentSupplier.getClassFactory()
			.buildAndLoadOrUpload(unitSG);
			return classRetriever.get("packagename.ComplexExample");
		});
	}
	
	@Test
	public void getOrBuildClassWithExternalClassTwo() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		PathHelper pathHelper = componentSupplier.getPathHelper();
		UnitSourceGenerator unitSG = UnitSourceGenerator.create("packagename").addClass(
			ClassSourceGenerator.create(
				TypeDeclarationSourceGenerator.create("ExternalClassReferenceTest")
			).addModifier(
				Modifier.PUBLIC
			).expands(
				TypeDeclarationSourceGenerator.create("DefaultSerializer")
			)
		).addImport(
			"org.springframework.core.serializer.DefaultSerializer"
		);
		testNotNull(() -> {
			ClassFactory.ClassRetriever classRetriever = componentSupplier.getClassFactory()
			.buildAndLoadOrUpload(
				pathHelper.getPaths(PathHelper.MAIN_CLASS_PATHS, PathHelper.MAIN_CLASS_PATHS_EXTENSION),
				Arrays.asList("F:/Shared/Programmi/Apache/Maven/Repositories/BurningWave/org/springframework/spring-core/4.3.4.RELEASE"),
				Arrays.asList("F:/Shared/Programmi/Apache/Maven/Repositories/BurningWave/org/springframework/spring-core/4.3.4.RELEASE"),
				unitSG
			);
			return classRetriever.get("packagename.ExternalClassReferenceTest");
		});
	}

	@Test
	public void getOrBuildPojoClassTestThree() throws Exception {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotNull(() -> {
			java.lang.Class<?> virtualClass = componentSupplier.getClassFactory().buildPojoSubTypeAndLoadOrUploadTo(
				Thread.currentThread().getContextClassLoader(), this.getClass().getPackage().getName() + ".TestThreePojoImpl", 
				Service.class,
				PojoInterface.class
			);
			Virtual virtual = (Virtual)Constructors.newInstanceOf(virtualClass);
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
