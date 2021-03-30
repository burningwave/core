package org.burningwave.core;

import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggersRepository;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import org.burningwave.core.classes.AnnotationSourceGenerator;
import org.burningwave.core.classes.BodySourceGenerator;
import org.burningwave.core.classes.ClassSourceGenerator;
import org.burningwave.core.classes.FunctionSourceGenerator;
import org.burningwave.core.classes.GenericSourceGenerator;
import org.burningwave.core.classes.SourceGenerator;
import org.burningwave.core.classes.TypeDeclarationSourceGenerator;
import org.burningwave.core.classes.UnitSourceGenerator;
import org.burningwave.core.classes.VariableSourceGenerator;
import org.junit.jupiter.api.Test;

public class UnitSourceGeneratorTest extends BaseTest {

	@Test
	public void generateUnitTestOne() throws Throwable {
		testDoesNotThrow(() -> {
			UnitSourceGenerator unit = UnitSourceGenerator.create("code.generator.try");
			unit.addClass(ClassSourceGenerator.createEnum(TypeDeclarationSourceGenerator.create("MyEnum"))
				.addEnumConstant(VariableSourceGenerator.create("FIRST_VALUE"))
				.addEnumConstant(VariableSourceGenerator.create("SECOND_VALUE")));
			FunctionSourceGenerator method = FunctionSourceGenerator.create("find").addModifier(Modifier.PUBLIC)
					.setTypeDeclaration(TypeDeclarationSourceGenerator.create(GenericSourceGenerator.create("F"), GenericSourceGenerator.create("G")))
					.setReturnType(TypeDeclarationSourceGenerator.create(Long.class))
					.addParameter(VariableSourceGenerator.create(TypeDeclarationSourceGenerator.create(Long.class), "parameter1")
							.addOuterCodeLine("@Parameter"))
					.addParameter(VariableSourceGenerator.create(TypeDeclarationSourceGenerator.create(String.class), "parameter2"))
					.addParameter(VariableSourceGenerator.create(TypeDeclarationSourceGenerator.create(Long.class), "parameter3"))
					.addBodyCodeLine("System.out.println(\"Hello world!\");")
					.addBodyCodeLine("System.out.println(\"How are you!\");").addBodyCodeLine("return new Long(1);")
					.addOuterCodeLine("@MethodAnnotation");

			FunctionSourceGenerator method2 = FunctionSourceGenerator.create("find2").addModifier(Modifier.PUBLIC)
					.setTypeDeclaration(TypeDeclarationSourceGenerator.create(GenericSourceGenerator.create("F"), GenericSourceGenerator.create("G")))
					.setReturnType(TypeDeclarationSourceGenerator.create(Long.class))
					.addParameter(VariableSourceGenerator.create(TypeDeclarationSourceGenerator.create(Long.class), "parameter1"))
					.addParameter(VariableSourceGenerator.create(TypeDeclarationSourceGenerator.create(String.class), "parameter2"))
					.addParameter(VariableSourceGenerator.create(TypeDeclarationSourceGenerator.create(Long.class), "parameter3"))
					.addBodyCodeLine("System.out.println(\"Hello world!\");")
					.addBodyCodeLine("System.out.println(\"How are you!\");").addBodyCodeLine("return new Long(1);");

			FunctionSourceGenerator constructor = FunctionSourceGenerator.create().addModifier(Modifier.PUBLIC).addBodyCodeLine("this.index1 = 1;");

			ClassSourceGenerator cls = ClassSourceGenerator
					.create(TypeDeclarationSourceGenerator.create("Generated0")
							.addGeneric(GenericSourceGenerator.create("T").expands(TypeDeclarationSourceGenerator.create("Class")
									.addGeneric(GenericSourceGenerator.create("F").expands(
											TypeDeclarationSourceGenerator.create("ClassTwo").addGeneric(GenericSourceGenerator.create("H"))))))
							.addGeneric(GenericSourceGenerator.create("?")
									.parentOf(TypeDeclarationSourceGenerator.create("Free").addGeneric(GenericSourceGenerator.create("S"))
											.addGeneric(GenericSourceGenerator.create("Y")))))
					.addModifier(Modifier.PUBLIC).expands(Object.class)
					.setStaticInitializer(BodySourceGenerator.create().addCodeLine("System.out.println(\"Hello World\");"))
					.addField(VariableSourceGenerator.create(TypeDeclarationSourceGenerator.create(Integer.class), "index1").setValue("new Integer(1)")
							.addModifier(Modifier.PRIVATE).addOuterCodeLine("@Field").addOuterCodeLine("@Annotation2"))
					.addField(VariableSourceGenerator.create(TypeDeclarationSourceGenerator.create(Collection.class).addGeneric(GenericSourceGenerator.create("String").addOuterCode("@NotNull")), "collection")
							.addModifier(Modifier.PRIVATE))
					.addConstructor(constructor).addMethod(method).addMethod(method2);
			cls.addInnerClass(ClassSourceGenerator
					.create(TypeDeclarationSourceGenerator.create("Generated1")
							.addGeneric(GenericSourceGenerator.create("T").expands(TypeDeclarationSourceGenerator.create("Class")
									.addGeneric(GenericSourceGenerator.create("F").expands(
											TypeDeclarationSourceGenerator.create("ClassTwo").addGeneric(GenericSourceGenerator.create("H"))))))
							.addGeneric(GenericSourceGenerator.create("?")
									.parentOf(TypeDeclarationSourceGenerator.create("Free").addGeneric(GenericSourceGenerator.create("S"))
											.addGeneric(GenericSourceGenerator.create("Y")))))
					.addModifier(Modifier.PUBLIC).expands(Object.class)
					.addField(VariableSourceGenerator.create(TypeDeclarationSourceGenerator.create(Integer.class), "index1")
							.addModifier(Modifier.PRIVATE).addOuterCodeLine("@Field"))
					.addField(VariableSourceGenerator.create(TypeDeclarationSourceGenerator.create(Integer.class), "index2")
							.addModifier(Modifier.PRIVATE))
					.addOuterCodeLine("@Annotation").addOuterCodeLine("@Annotation2")
					.addInnerClass(ClassSourceGenerator
							.create(TypeDeclarationSourceGenerator.create("Generated2")
									.addGeneric(GenericSourceGenerator.create("T")
											.expands(TypeDeclarationSourceGenerator.create("Class")
													.addGeneric(GenericSourceGenerator.create("F")
															.expands(TypeDeclarationSourceGenerator.create("ClassTwo")
																	.addGeneric(GenericSourceGenerator.create("H"))))))
									.addGeneric(GenericSourceGenerator.create("?")
											.parentOf(TypeDeclarationSourceGenerator.create("Free").addGeneric(GenericSourceGenerator.create("S"))
													.addGeneric(GenericSourceGenerator.create("Y")))))
							.addModifier(Modifier.PUBLIC).expands(Object.class)
							.addField(VariableSourceGenerator.create(TypeDeclarationSourceGenerator.create(Integer.class), "index1")
									.addModifier(Modifier.PRIVATE).addAnnotation(AnnotationSourceGenerator.create(Field.class)))
							.addField(VariableSourceGenerator.create(TypeDeclarationSourceGenerator.create(Integer.class), "index2")
									.addModifier(Modifier.PRIVATE))
							.addAnnotation(
									AnnotationSourceGenerator.create("NotEmpty.List").useType(NotEmpty.class).addParameter(
										AnnotationSourceGenerator.create(NotEmpty.class).addParameter(
											VariableSourceGenerator.create("message").setValue("\"Person name should not be empty\"")
										).addParameter(
											VariableSourceGenerator.create("groups").setValue(this.getClass().getSimpleName() + ".class").useType(this.getClass())
										),
										AnnotationSourceGenerator.create(NotEmpty.class).addParameter(
											VariableSourceGenerator.create("message").setValue("\"Company name should not be empty\"")
										)
									)
								)
							)
					.addMethod(method)).addOuterCodeLine("@Annotation").addOuterCodeLine("@Annotation2");
			unit.addClass(cls);
			unit.addClass(cls);
			ManagedLoggersRepository.logDebug(getClass()::getName, unit.make());
		});
	}
	
	@Test
	public void generateUnitAndStoreTestOne() throws Throwable {
		UnitSourceGenerator.create(
			"code.generator.test"
		).addClass(
			ClassSourceGenerator.createInterface(
				TypeDeclarationSourceGenerator.create(
					"Function"
				).addGeneric(
					GenericSourceGenerator.create("T")
				).addGeneric(
					GenericSourceGenerator.create("R")
				)
			).addModifier(
				Modifier.PUBLIC
			).addMethod(
				FunctionSourceGenerator.create(
					"apply"
				).addParameter(
					VariableSourceGenerator.create(
						TypeDeclarationSourceGenerator.create("T"), "t"
					)
				).setReturnType("R").addModifier(Modifier.PUBLIC | Modifier.ABSTRACT)
			).addAnnotation(AnnotationSourceGenerator.create(FunctionalInterface.class))
		).addImport(
			FunctionalInterface.class
		).storeToClassPath(
			System.getProperty("user.home") + "/Desktop/bw-tests"
		);
	}
	
	
	@Test
	public void generateUnitTwo() throws Throwable {
		UnitSourceGenerator unitSG = UnitSourceGenerator.create("org.burningwave.core.examples.classfactory").addClass(
			createClass("MyExtendedClass")
			.addInnerClass(
				createClass("MyExtendedInnerClass")
				.addModifier(Modifier.PUBLIC | Modifier.STATIC)
				.addInnerClass(
					createEnum().addModifier(Modifier.PUBLIC | Modifier.STATIC)
				)
			)
		);
		unitSG.storeToClassPath(System.getProperty("user.home") + "/Desktop/bw-tests");
		System.out.println("\nGenerated code:\n" + unitSG.make());
		unitSG.serializeToPath(System.getProperty("user.home") + "/Desktop/bw-tests/GenerateUnitTwo.UnitSourceGenerator.ser");
		unitSG = SourceGenerator.deserializeFromPath(System.getProperty("user.home") + "/Desktop/bw-tests/GenerateUnitTwo.UnitSourceGenerator.ser");
		System.out.println("\nGenerated code:\n" + unitSG.make());
	}

	private ClassSourceGenerator createClass(String className) {
		return ClassSourceGenerator.create(
			TypeDeclarationSourceGenerator.create(className)
		).addOuterCodeLine("//Comment").addAnnotation(
			AnnotationSourceGenerator.create("NotEmpty.List").useType(NotEmpty.class).addParameter("value", true, 
				AnnotationSourceGenerator.create(NotEmpty.class).addParameter(
					VariableSourceGenerator.create("message").setValue("\"Person name should not be empty\"")
				).addParameter(
					VariableSourceGenerator.create("groups").setValue(this.getClass().getSimpleName() + ".class").useType(this.getClass())
				),
				AnnotationSourceGenerator.create(NotEmpty.class).addParameter(
					VariableSourceGenerator.create("message").setValue("\"Company name should not be empty\"")
				)
			)
		).addAnnotation(
				AnnotationSourceGenerator.create(NotNull.class)
		).addAnnotation(
				AnnotationSourceGenerator.create(SuppressWarnings.class).addParameter(VariableSourceGenerator.create("\"unchecked\""))
		).addModifier(
			Modifier.PUBLIC
		)
		.addField(VariableSourceGenerator.create(TypeDeclarationSourceGenerator.create(Date[][].class).useFullyQualifiedName(true), "multiTimesZero").addModifier(Modifier.PRIVATE))
		.addField(VariableSourceGenerator.create(Date[][][].class, "multiTimesOne").addModifier(Modifier.PRIVATE))		
		.addField(VariableSourceGenerator.create(TypeDeclarationSourceGenerator.create(Date[][][][].class), "multiTimesTwo").addModifier(Modifier.PRIVATE))
		.addField(VariableSourceGenerator.create(TypeDeclarationSourceGenerator.create("java.util.Date[][][][][]", "Date[][][][][]"), "multiTimesThree").addModifier(Modifier.PRIVATE))
		.addField(VariableSourceGenerator.create(TypeDeclarationSourceGenerator.create("java.util.Date[][][][][][]", "Date[][][][][][]").useFullyQualifiedName(true), "multiTimesFour").addModifier(Modifier.PRIVATE))
		.addField(VariableSourceGenerator.create(long.class, "serialVersionUID").addModifier(Modifier.PRIVATE | Modifier.STATIC | Modifier.FINAL))
		.addField(
			VariableSourceGenerator.create(
				TypeDeclarationSourceGenerator.create(
					Map.class
				).addGeneric(
					GenericSourceGenerator.create(String.class).addAnnotation(
						AnnotationSourceGenerator.create(NotEmpty.class)
					)
				).addGeneric(
					GenericSourceGenerator.create(String.class)
				),
				"mapOne"
			).addAnnotation(AnnotationSourceGenerator.create(NotEmpty.class))
			.setValue(
				BodySourceGenerator.createSimple().addCode("new").addElement(
					ClassSourceGenerator.create(
						TypeDeclarationSourceGenerator.create(HashMap.class).addGeneric(
							GenericSourceGenerator.create(String.class),
							GenericSourceGenerator.create(String.class)
					).setAsParameterizable(true)
					).addMethod(
						FunctionSourceGenerator.create("get")
						.setReturnType(TypeDeclarationSourceGenerator.create(String.class))
						.addModifier(Modifier.PUBLIC)
						.addParameter(VariableSourceGenerator.create(TypeDeclarationSourceGenerator.create(Object.class), "key"))
						.addOuterCodeLine("@Override")
						.addBodyCodeLine("return super.get(key);")
					)
				)
			)
		)
		.addField(
			VariableSourceGenerator.create(
				TypeDeclarationSourceGenerator.create(
					Map.class
				).addGeneric(
					GenericSourceGenerator.create(String.class)
				).addGeneric(
					GenericSourceGenerator.create(String.class)
				),
				"mapTwo"
			)
		)
		.addConstructor(
			FunctionSourceGenerator.create().addParameter(
					VariableSourceGenerator.create(TypeDeclarationSourceGenerator.create(String.class).setAsVarArgs(true), "values")
				).addBodyCodeLine("mapTwo = ").addBodyElement(
				BodySourceGenerator.createSimple().addCode("new").addElement(
					ClassSourceGenerator.create(
						TypeDeclarationSourceGenerator.create(HashMap.class).addGeneric(
							GenericSourceGenerator.create(String.class),
							GenericSourceGenerator.create(String.class)
					).setAsParameterizable(true)
					).addMethod(
						FunctionSourceGenerator.create("get")
						.setReturnType(TypeDeclarationSourceGenerator.create(String.class))
						.addModifier(Modifier.PUBLIC)
						.addParameter(VariableSourceGenerator.create(TypeDeclarationSourceGenerator.create(Object.class), "key"))
						.addOuterCodeLine("@Override")
						.addBodyCodeLine("return super.get(key);")
					)
				)		
			).addBodyCode(";")
			
		)
		.setStaticInitializer(BodySourceGenerator.create().addCodeLine("serialVersionUID = 1L;"))
		.addMethod(
			FunctionSourceGenerator.create("now")
			.setTypeDeclaration(
				TypeDeclarationSourceGenerator.create(
					GenericSourceGenerator.create("T").expands(
						TypeDeclarationSourceGenerator.create(Cloneable.class),
						TypeDeclarationSourceGenerator.create(Serializable.class)
					)
				)
			)
			.setReturnType(TypeDeclarationSourceGenerator.create(Comparable.class).addGeneric(GenericSourceGenerator.create("T")))
			.addModifier(Modifier.PUBLIC)
			.addOuterCodeLine("//Comment")
			.addBodyCodeLine()
			.addBodyElement(
				VariableSourceGenerator.create(
					TypeDeclarationSourceGenerator.create(Supplier.class).addGeneric(GenericSourceGenerator.create(Date.class)).useFullyQualifiedName(true),
					"dateSupplier"
				).setValue(
					BodySourceGenerator.createSimple().addCodeLine("new").addElement(
						ClassSourceGenerator.create(
							TypeDeclarationSourceGenerator.create(Supplier.class).addGeneric(GenericSourceGenerator.create(Date.class)).useFullyQualifiedName(true)
							.setAsParameterizable(true)
						).addMethod(
							FunctionSourceGenerator.create("get")
							.setReturnType(TypeDeclarationSourceGenerator.create(Date.class))
							.addModifier(Modifier.PUBLIC)
							.addOuterCodeLine("@Override")
							.addBodyCodeLine("return new Date();")
						)
					)
				
				)
			).addBodyCodeLine("return (Comparable<T>)dateSupplier.get();")
		).addConcretizedType(
			Serializable.class
		).expands(Object.class);
	}
	
	@Test
	public void testEnum() {
		testDoesNotThrow(() -> {
			UnitSourceGenerator unitSG = UnitSourceGenerator.create("org.burningwave.core.examples.classfactory").addClass(
				createEnum()				
			);
			unitSG.storeToClassPath(System.getProperty("user.home") + "/Desktop/bw-tests");
			System.out.println("\nGenerated code:\n" + unitSG.make());
		});
	}

	private ClassSourceGenerator createEnum() {
		return ClassSourceGenerator.createEnum(TypeDeclarationSourceGenerator.create("MyEnum"))
		.addEnumConstant(
			VariableSourceGenerator.create(
				TypeDeclarationSourceGenerator.create("FIRST_VALUE").addParameter(
					BodySourceGenerator.createSimple().addCodeLine("new").addElement(
						ClassSourceGenerator.create(
							TypeDeclarationSourceGenerator.create(Supplier.class).addGeneric(GenericSourceGenerator.create(Date.class)).useFullyQualifiedName(true)
							.setAsParameterizable(true)
						).addMethod(
							FunctionSourceGenerator.create("get")
							.setReturnType(TypeDeclarationSourceGenerator.create(Date.class))
							.addModifier(Modifier.PUBLIC)
							.addOuterCodeLine("@Override")
							.addBodyCodeLine("return new Date();")
						)
					)
				)
			).setValue(
				BodySourceGenerator.create().addElement(
					FunctionSourceGenerator.create("toLowerCase")
					.addOuterCodeLine("@Override")
					.addModifier(Modifier.PUBLIC)
					.setReturnType(String.class)
					.addBodyCodeLine("return FIRST_VALUE.toString().toLowerCase();")
				)
			)
		).addEnumConstant(
			VariableSourceGenerator.create(
				TypeDeclarationSourceGenerator.create("SECOND_VALUE").addParameter(
					BodySourceGenerator.createSimple().addCodeLine("new").addElement(
						ClassSourceGenerator.create(
							TypeDeclarationSourceGenerator.create(Supplier.class).addGeneric(GenericSourceGenerator.create(Date.class)).useFullyQualifiedName(true)
							.setAsParameterizable(true)
						).addMethod(
							FunctionSourceGenerator.create("get")
							.setReturnType(TypeDeclarationSourceGenerator.create(Date.class))
							.addModifier(Modifier.PUBLIC)
							.addOuterCodeLine("@Override")
							.addBodyCodeLine("return new Date();")
						)
					)
				)
			).setValue(
				BodySourceGenerator.create().addElement(
					FunctionSourceGenerator.create("toLowerCase")
					.addOuterCodeLine("@Override")
					.addModifier(Modifier.PUBLIC)
					.setReturnType(String.class)
					.addBodyCodeLine("return SECOND_VALUE.toString().toLowerCase();")
				)
			)
		).addEnumConstant(
			VariableSourceGenerator.create(
				TypeDeclarationSourceGenerator.create("THIRD_VALUE").addParameter(
					BodySourceGenerator.createSimple().addCodeLine("new").addElement(
						ClassSourceGenerator.create(
							TypeDeclarationSourceGenerator.create(Supplier.class).addGeneric(GenericSourceGenerator.create(Date.class)).useFullyQualifiedName(true)
							.setAsParameterizable(true)
						).addMethod(
							FunctionSourceGenerator.create("get")
							.setReturnType(TypeDeclarationSourceGenerator.create(Date.class))
							.addModifier(Modifier.PUBLIC)
							.addOuterCodeLine("@Override")
							.addBodyCodeLine("return new Date();")
						)
					),
					BodySourceGenerator.createSimple().addCodeLine("new").addElement(
						ClassSourceGenerator.create(
							TypeDeclarationSourceGenerator.create(Supplier.class).addGeneric(GenericSourceGenerator.create(Date.class)).useFullyQualifiedName(true)
							.setAsParameterizable(true)
						).addMethod(
							FunctionSourceGenerator.create("get")
							.setReturnType(TypeDeclarationSourceGenerator.create(Date.class))
							.addModifier(Modifier.PUBLIC)
							.addOuterCodeLine("@Override")
							.addBodyCodeLine("return new Date();")
						)
					)
				)
			).setValue(
				BodySourceGenerator.create().addElement(
					FunctionSourceGenerator.create("toLowerCase")
					.addOuterCodeLine("@Override")
					.addModifier(Modifier.PUBLIC)
					.setReturnType(String.class)
					.addBodyCodeLine("return SECOND_VALUE.toString().toLowerCase();")
				)
			)
		).addField(
			VariableSourceGenerator.create(TypeDeclarationSourceGenerator.create(Supplier[].class).useFullyQualifiedName(true)
			.addGeneric(GenericSourceGenerator.create(Date.class)), "dateSupplier")
			.addModifier(Modifier.PRIVATE)
		).addConstructor(FunctionSourceGenerator.create().addParameter(
			VariableSourceGenerator.create(TypeDeclarationSourceGenerator.create(Supplier.class).setAsVarArgs(true).useFullyQualifiedName(true)
			.addGeneric(GenericSourceGenerator.create(Date.class)), "dateSupplier")).addBodyCodeLine("this.dateSupplier = dateSupplier;")
		).addMethod(FunctionSourceGenerator.create("toLowerCase").addModifier(Modifier.ABSTRACT | Modifier.PUBLIC).setReturnType(String.class));
	}
}