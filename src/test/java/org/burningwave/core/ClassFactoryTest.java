package org.burningwave.core;

import static org.burningwave.core.assembler.StaticComponentContainer.Classes;
import static org.burningwave.core.assembler.StaticComponentContainer.Constructors;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;

import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.assembler.StaticComponentContainer;
import org.burningwave.core.bean.Complex;
import org.burningwave.core.bean.PojoInterface;
import org.burningwave.core.classes.ClassFactory;
import org.burningwave.core.classes.ClassSourceGenerator;
import org.burningwave.core.classes.FunctionSourceGenerator;
import org.burningwave.core.classes.LoadOrBuildAndDefineConfig;
import org.burningwave.core.classes.PojoSourceGenerator;
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
		testNotNull(() -> componentSupplier.getClassFactory().loadOrBuildAndDefineFunctionSubType(Thread.currentThread().getContextClassLoader(), 10));
	}	
	
	@Test
	public void getOrBuildConsumerClassTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotNull(() -> componentSupplier.getClassFactory().loadOrBuildAndDefineConsumerSubType(Thread.currentThread().getContextClassLoader(), 2));
	}
	
	@Test
	public void getOrBuildPredicateClassTestOne() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotNull(() -> componentSupplier.getClassFactory().loadOrBuildAndDefinePredicateSubType(Thread.currentThread().getContextClassLoader(), 10));
	}
	
	
	@Test
	public void getOrBuildPojoClassTestOne() throws Exception {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotNull(() -> 
			componentSupplier.getClassFactory().loadOrBuildAndDefinePojoSubType(
				Thread.currentThread().getContextClassLoader(), this.getClass().getPackage().getName() + ".SimpleVirtual"
			)
		);
	}
	
	
	@Test
	public void getOrBuildPojoClassTestTwo() throws Exception {
		ComponentSupplier componentSupplier = getComponentSupplier();
		java.lang.Class<?> cls = componentSupplier.getClassFactory().loadOrBuildAndDefinePojoSubType(
			Thread.currentThread().getContextClassLoader(),
			this.getClass().getPackage().getName() + ".TestTwoPojoImpl",
			PojoSourceGenerator.BUILDING_METHODS_CREATION_ENABLED,
			Complex.Data.Item.class,
			PojoInterface.class
		);
		testNotNull(() -> {
			Class<?> reloadedCls = componentSupplier.getClassFactory().loadOrBuildAndDefinePojoSubType(
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
		).addStaticImport(StaticComponentContainer.class, "Streams", "Classes");
		testNotNull(() -> {
			return componentSupplier.getClassFactory().loadOrBuildAndDefine(
				unitSG
			).get(
				"tryyy.ReTry"
			);
		});
		testNotNull(() -> 
			componentSupplier.getClassFactory().loadOrBuildAndDefine(unitSG).get("tryyy.ReTry$ReReTry")
		);
	}
	
	
	@Test
	public void getOrBuildClassWithExternalClassOneParallelized() {
		testDoesNotThrow(() -> {
			Thread thr_01 = new Thread( () -> getOrBuildClassWithExternalClassOne(false));
			Thread thr_02 = new Thread( () -> getOrBuildClassWithExternalClassOne(false));
			thr_01.start();
			thr_02.start();
			thr_01.join();
			thr_02.join();
		});
	}
	
	@Test
	public void getOrBuildClassWithExternalClassOne() {
		getOrBuildClassWithExternalClassOne(true);
	}
	
	public void getOrBuildClassWithExternalClassOne(boolean clearCache) {
		ComponentSupplier componentSupplier = getComponentSupplier();
		PathHelper pathHelper = componentSupplier.getPathHelper();
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
		UnitSourceGenerator unitSG2= UnitSourceGenerator.create("packagename").addClass(
			ClassSourceGenerator.create(
				TypeDeclarationSourceGenerator.create("ComplexExampleTwo")
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
			ClassFactory.ClassRetriever classRetriever =  componentSupplier.getClassFactory().loadOrBuildAndDefine(
				LoadOrBuildAndDefineConfig.forUnitSourceGenerator(unitSG)
				.addCompilationClassPaths(
					pathHelper.getPaths(PathHelper.MAIN_CLASS_PATHS, PathHelper.MAIN_CLASS_PATHS_EXTENSION)
				).addClassPathsWhereToSearchNotFoundClasses(
					pathHelper.getAbsolutePathOfResource("../../src/test/external-resources/libs-for-test.zip")
				)
			);
			classRetriever.get("packagename.ComplexExample");
			if (clearCache) {
				ComponentContainer.clearAllCaches();
			}
			classRetriever = componentSupplier.getClassFactory().loadOrBuildAndDefine(
				LoadOrBuildAndDefineConfig.forUnitSourceGenerator(unitSG2).addCompilationClassPaths(
					pathHelper.getPaths(PathHelper.MAIN_CLASS_PATHS, PathHelper.MAIN_CLASS_PATHS_EXTENSION)
				).addClassPathsWhereToSearchNotFoundClasses(
					pathHelper.getAbsolutePathOfResource("../../src/test/external-resources/libs-for-test.zip")
				)
			);
			return classRetriever.get("packagename.ComplexExampleTwo");
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
			ClassFactory.ClassRetriever classRetriever = componentSupplier.getClassFactory().loadOrBuildAndDefine(
				LoadOrBuildAndDefineConfig.forUnitSourceGenerator(unitSG).addCompilationClassPaths(
					pathHelper.getPaths(PathHelper.MAIN_CLASS_PATHS, PathHelper.MAIN_CLASS_PATHS_EXTENSION)
				).addClassPathsWhereToSearchNotFoundClasses(
						pathHelper.getAbsolutePathOfResource("../../src/test/external-resources/spring-core-4.3.4.RELEASE.jar")
				)
			);
			return classRetriever.get("packagename.ExternalClassReferenceTest");
		});
	}

	@Test
	public void getOrBuildPojoClassTestThree() throws Exception {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotNull(() -> {
			java.lang.Class<?> virtualClass = componentSupplier.getClassFactory().loadOrBuildAndDefinePojoSubType(
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
	
}
