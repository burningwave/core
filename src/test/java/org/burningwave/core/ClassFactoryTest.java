package org.burningwave.core;

import static org.burningwave.core.assembler.StaticComponentContainer.Classes;
import static org.burningwave.core.assembler.StaticComponentContainer.Constructors;
import static org.burningwave.core.assembler.StaticComponentContainer.Methods;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.assembler.StaticComponentContainer;
import org.burningwave.core.bean.Complex;
import org.burningwave.core.bean.PojoInterface;
import org.burningwave.core.classes.ClassCriteria;
import org.burningwave.core.classes.ClassFactory;
import org.burningwave.core.classes.ClassPathHunter;
import org.burningwave.core.classes.ClassSourceGenerator;
import org.burningwave.core.classes.FunctionSourceGenerator;
import org.burningwave.core.classes.LoadOrBuildAndDefineConfig;
import org.burningwave.core.classes.PojoSourceGenerator;
import org.burningwave.core.classes.SearchConfig;
import org.burningwave.core.classes.TypeDeclarationSourceGenerator;
import org.burningwave.core.classes.UnitSourceGenerator;
import org.burningwave.core.classes.VariableSourceGenerator;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.service.Service;
import org.junit.jupiter.api.Test;

public class ClassFactoryTest extends BaseTest {
	
	
	@Test
	public void getOrBuildPojoClassTestOne() throws Exception {
		testNotNull(() -> {
			String className = this.getClass().getPackage().getName() + ".SimpleVirtual";
			ComponentSupplier componentSupplier = getComponentSupplier();
			return componentSupplier.getClassFactory().loadOrBuildAndDefine(
				LoadOrBuildAndDefineConfig.forUnitSourceGenerator(
					UnitSourceGenerator.create(Classes.retrievePackageName(className)).
					addClass(
						PojoSourceGenerator.create().generate(
							className					
						)
					)
				)
			).get(className);
		});
	}
	
	
	@Test
	public void getOrBuildPojoClassTestTwo() throws Exception {
		String className = this.getClass().getPackage().getName() + ".TestTwoPojoImpl";
		ComponentSupplier componentSupplier = getComponentSupplier();
		java.lang.Class<?> cls = componentSupplier.getClassFactory().loadOrBuildAndDefine(
			LoadOrBuildAndDefineConfig.forUnitSourceGenerator(
				UnitSourceGenerator.create(Classes.retrievePackageName(className)).
				addClass(PojoSourceGenerator.create().generate(
					className,
					PojoSourceGenerator.BUILDING_METHODS_CREATION_ENABLED,
					Complex.Data.Item.class,
					PojoInterface.class
				))
			).useClassLoader(Thread.currentThread().getContextClassLoader())
		).get(className);
		testNotNull(() -> {
			String classNameTwo = cls.getPackage().getName() + ".ExtendedPojoImpl";
			Class<?> reloadedCls = componentSupplier.getClassFactory().loadOrBuildAndDefine(
				LoadOrBuildAndDefineConfig.forUnitSourceGenerator(
					UnitSourceGenerator.create(Classes.retrievePackageName(classNameTwo)).
					addClass(PojoSourceGenerator.create().generate(
						classNameTwo,
						PojoSourceGenerator.BUILDING_METHODS_CREATION_ENABLED,
						cls
					))
				).useClassLoader(Thread.currentThread().getContextClassLoader())
			).get(classNameTwo);
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
	public void getOrBuildClassWithExternalClassOneParallelizedTest() {
		testDoesNotThrow(() -> {
			int threadCount = 6;
			Collection<Thread> threads = new ArrayList<>();
			for (int i = 0; i < threadCount; i++) {
				threads.add(new Thread( () -> getOrBuildClassWithExternalClassTestOne(true, "ComplexExample", "ComplexExampleTwo", null)));
			}
			for (Thread thread : threads) {
				thread.start();
			}
			for (Thread thread : threads) {
				thread.join();
			}
		});
	}
	
	@Test
	public void getOrBuildClassWithExternalClassTestOne() {
		getOrBuildClassWithExternalClassTestOne(true, "ComplexExample", "ComplexExampleTwo", null);
	}
	
	@Test
	//@Tag("Heavy")
	public void getOrBuildClassWithExternalClassTestFive() {
		getOrBuildClassWithExternalClassTestOne(true, "ComplexExampleFour", "ComplexExampleFive", null);
	}
	
	@Test
	public void getOrBuildClassWithExternalClassTestSix() {
		getOrBuildClassWithExternalClassTestOne(true, "ComplexExample", "ComplexExampleTwo", Thread.currentThread().getContextClassLoader());
	}
	
	@Test
	public void getOrBuildClassWithExternalClassTestNine() {
		getOrBuildClassWithExternalClassTestOne(true, "ComplexExample", "ComplexExampleTwo", new ClassLoader(null){});
	}
	
	@Test
	public void getOrBuildClassWithExternalClassTestSeven() {
		testNotNull(() -> {
			_getOrBuildClassWithExternalClassTestTwo().getAllCompiledClasses();
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
			AtomicReference<ClassFactory.ClassRetriever> classRetrieverWrapper = new AtomicReference<ClassFactory.ClassRetriever>();
			testNotNull(() -> {
				try (ClassPathHunter.SearchResult searchResult = componentSupplier.getClassPathHunter().findBy(
					SearchConfig.byCriteria(
						ClassCriteria.create().className(Virtual.class.getName()::equals)
					)
				)) {
					classRetrieverWrapper.set(
						componentSupplier.getClassFactory().loadOrBuildAndDefine(
							LoadOrBuildAndDefineConfig.forUnitSourceGenerator(unitSG).setClassRepository(
								pathHelper.getAbsolutePathOfResource("../../src/test/external-resources/libs-for-test.zip"),
								pathHelper.getAbsolutePathOfResource("../../src/test/external-resources/spring-core-4.3.4.RELEASE.jar")
							)
						)
					);
					return classRetrieverWrapper.get().get("freemarker.core.AndExpression");
				}
			});
			return classRetrieverWrapper.get();
		});
	}
	
	public void getOrBuildClassWithExternalClassTestOne(
		boolean clearCache,
		String classNameOne,
		String classNameTwo,
		ClassLoader classLoader
	) {
		ComponentSupplier componentSupplier = getComponentSupplier();
		PathHelper pathHelper = componentSupplier.getPathHelper();
		UnitSourceGenerator unitSG = UnitSourceGenerator.create("packagename").addClass(
			ClassSourceGenerator.create(
				TypeDeclarationSourceGenerator.create(classNameOne)
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
				).addBodyCodeLine("super(parentSoapMsg, inputStream);")
			)
		).addImport(
			"org.apache.axis2.saaj.SOAPPartImpl",
			"org.apache.axis2.saaj.SOAPMessageImpl",
			"javax.xml.soap.SOAPException"
		).addStaticImport(
			"org.burningwave.core.assembler.StaticComponentContainer.Classes"
		);
		UnitSourceGenerator unitSG2= UnitSourceGenerator.create("packagename").addClass(
			ClassSourceGenerator.create(
				TypeDeclarationSourceGenerator.create(classNameTwo)
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
				).addBodyCodeLine("super(parentSoapMsg, inputStream);")
			)
		).addImport(
			"org.apache.axis2.saaj.SOAPPartImpl",
			"org.apache.axis2.saaj.SOAPMessageImpl",
			"javax.xml.soap.SOAPException"
		).addStaticImport(
			"org.burningwave.core.assembler.StaticComponentContainer.Classes"
		);
		testNotNull(() -> {
			LoadOrBuildAndDefineConfig config = LoadOrBuildAndDefineConfig.forUnitSourceGenerator(unitSG).useClassLoader(
				classLoader
			);
			config.setClassRepository(
				pathHelper.getAbsolutePathOfResource("../../src/test/external-resources/libs-for-test.zip")
			);
			ClassFactory.ClassRetriever classRetriever = componentSupplier.getClassFactory().loadOrBuildAndDefine(
				config			
			);
			classRetriever.get("packagename." + classNameOne);
			if (clearCache) {
				ComponentContainer.clearAllCaches(true, true, false);
			}
			config = LoadOrBuildAndDefineConfig.forUnitSourceGenerator(unitSG2).useClassLoader(
				classLoader
			);
			config.setClassRepository(
				pathHelper.getAbsolutePathOfResource("../../src/test/external-resources/libs-for-test.zip")
			);
			classRetriever = componentSupplier.getClassFactory().loadOrBuildAndDefine(
				config			
			);
			return classRetriever.get("packagename." + classNameTwo);
		});
	}
	
	@Test
	public void getOrBuildClassWithExternalClassTestTwo() {
		_getOrBuildClassWithExternalClassTestTwo();
	}
	
	
	public ClassFactory.ClassRetriever _getOrBuildClassWithExternalClassTestTwo() {
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
		AtomicReference<ClassFactory.ClassRetriever> classRetrieverWrapper = new AtomicReference<ClassFactory.ClassRetriever>();
		testNotNull(() -> {
			try (ClassPathHunter.SearchResult searchResult = componentSupplier.getClassPathHunter().findBy(
				SearchConfig.byCriteria(
					ClassCriteria.create().className(Virtual.class.getName()::equals)
				)
			)) {
				classRetrieverWrapper.set(
					componentSupplier.getClassFactory().loadOrBuildAndDefine(
						LoadOrBuildAndDefineConfig.forUnitSourceGenerator(unitSG).modifyCompilationConfig(compileConfig -> 
							compileConfig.addClassPaths(
								pathHelper.getAbsolutePathOfResource("../../src/test/external-resources/spring-core-4.3.4.RELEASE.jar")
							)							
						)
					)
				);
				return classRetrieverWrapper.get().get("packagename.ExternalClassReferenceTest");
			}
		});
		return classRetrieverWrapper.get();
	}
	
	//@Test
	public void getOrBuildClassWithExternalClassTestEight() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		UnitSourceGenerator unitSG = UnitSourceGenerator.create("jdk.internal.loader").addClass(
			ClassSourceGenerator.create(
				TypeDeclarationSourceGenerator.create("ExtendedBuiltinClassLoader")
			).addModifier(
				Modifier.PUBLIC
			).expands(
				TypeDeclarationSourceGenerator.create("BuiltinClassLoader")
			).addConstructor(
				FunctionSourceGenerator.create().addParameter(
					VariableSourceGenerator.create(TypeDeclarationSourceGenerator.create("BuiltinClassLoader"), "parent")
				).addBodyCodeLine("super(null, parent, null);")
			)
		).addImport(
			"jdk.internal.loader.BuiltinClassLoader"
		);
		
			testNotNull(() -> {
				try (ClassPathHunter.SearchResult searchResult = componentSupplier.getClassPathHunter().findBy(
					SearchConfig.byCriteria(
						ClassCriteria.create().className(Virtual.class.getName()::equals)
					)
				)) {
					ClassFactory.ClassRetriever classRetriever = componentSupplier.getClassFactory().loadOrBuildAndDefine(
						LoadOrBuildAndDefineConfig.forUnitSourceGenerator(unitSG)
					);
					return classRetriever.get("jdk.internal.loader.ExtendedBuiltinClassLoader");
				}
			});
	}
	
	@Test
	public void getOrBuildClassWithExternalClassTestFour() {
		ComponentSupplier componentSupplier = getComponentSupplier();
		PathHelper pathHelper = componentSupplier.getPathHelper();
		UnitSourceGenerator unitSG = UnitSourceGenerator.create("packagename").addClass(
			ClassSourceGenerator.create(
				TypeDeclarationSourceGenerator.create("ExternalClassReferenceTestFour")
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
				LoadOrBuildAndDefineConfig.forUnitSourceGenerator(unitSG).setClassPaths(
					pathHelper.getAbsolutePathOfResource("../../src/test/external-resources/spring-core-4.3.4.RELEASE.jar")
				).useOneShotJavaCompiler(true)
			);
			return classRetriever.get("packagename.ExternalClassReferenceTestFour");
		});
	}
	
	@Test
	public void getOrBuildClassWithExternalClassTestThree() {
		getOrBuildClassWithExternalClassTestTwo();
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
				LoadOrBuildAndDefineConfig.forUnitSourceGenerator(unitSG).setClassRepository(
					pathHelper.getAbsolutePathOfResource("../../src/test/external-resources/spring-core-4.3.4.RELEASE.jar"),
					pathHelper.getAbsolutePathOfResource("../../src/test/external-resources/libs-for-test.zip")
				)
			);
			classRetriever.get(
				"org.apache.commons.lang.ArrayUtils",
				"org.springframework.util.TypeUtils"
			);
			return classRetriever.get("packagename.ExternalClassReferenceTest");
		});
	}

	@Test
	public void getOrBuildPojoClassTestThree() throws Exception {
		ComponentSupplier componentSupplier = getComponentSupplier();
		testNotNull(() -> {
			String classNameTwo = this.getClass().getPackage().getName() + ".TestThreePojoImpl";
			Class<?> virtualClass = componentSupplier.getClassFactory().loadOrBuildAndDefine(
				LoadOrBuildAndDefineConfig.forUnitSourceGenerator(
					UnitSourceGenerator.create(Classes.retrievePackageName(classNameTwo)).
					addClass(PojoSourceGenerator.create().generate(
						classNameTwo,
						PojoSourceGenerator.BUILDING_METHODS_CREATION_ENABLED,
						Service.class,
						PojoInterface.class
					))
				).useClassLoader(Thread.currentThread().getContextClassLoader())
			).get(classNameTwo);
			Virtual virtual = (Virtual)Constructors.newInstanceDirectOf(virtualClass);
			virtual.invokeDirect("setList", new ArrayList<>());
			virtual.invoke("setList", new ArrayList<>());
			virtual.invokeDirect("setList", new ArrayList<>());
			virtual.invoke("consume", Integer.valueOf(1));
			Methods.invokeStaticDirect(virtual.getClass(), "consume", Integer.valueOf(1));
			List<?> list = virtual.getValueOf("list");
			list = virtual.getDirectValueOf("list");
			return list;
			}
		);
	}
	
	public static class Repeat extends ClassFactoryTest {
		
	}
	
}
