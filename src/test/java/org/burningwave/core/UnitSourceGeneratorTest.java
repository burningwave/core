package org.burningwave.core;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import org.burningwave.core.classes.AnnotationSourceGenerator;
import org.burningwave.core.classes.ClassSourceGenerator;
import org.burningwave.core.classes.FunctionSourceGenerator;
import org.burningwave.core.classes.GenericSourceGenerator;
import org.burningwave.core.classes.TypeDeclarationSourceGenerator;
import org.burningwave.core.classes.UnitSourceGenerator;
import org.burningwave.core.classes.VariableSourceGenerator;
import org.burningwave.core.examples.classfactory.RuntimeClassExtenderTwo.MyInterface;
import org.burningwave.core.examples.classfactory.RuntimeClassExtenderTwo.ToBeExtended;
import org.junit.jupiter.api.Test;

public class UnitSourceGeneratorTest extends BaseTest {

	@Test
	public void generateUnitTestOne() throws Throwable {
		testDoesNotThrow(() -> {
			UnitSourceGenerator unit = UnitSourceGenerator.create("code.generator.try");
			unit.addClass(ClassSourceGenerator.createEnum(TypeDeclarationSourceGenerator.create("MyEnum")).addField(VariableSourceGenerator.create((TypeDeclarationSourceGenerator)null, "FIRST_VALUE")).addField(VariableSourceGenerator.create((TypeDeclarationSourceGenerator)null, "SECOND_VALUE")));
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
			logDebug(unit.make());
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
            ClassSourceGenerator.create(
                TypeDeclarationSourceGenerator.create("MyExtendedClass")
            ).addAnnotation(
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
            ).addAnnotation(
                	AnnotationSourceGenerator.create(NotNull.class)
            ).addAnnotation(
                	AnnotationSourceGenerator.create(SuppressWarnings.class).addParameter(VariableSourceGenerator.create("\"unchecked\""))
            ).addModifier(
                Modifier.PUBLIC
            //generating new method that override MyInterface.now()
            ).addField(
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
            		"map"
            	).addAnnotation(AnnotationSourceGenerator.create(NotEmpty.class))
            ).addMethod(
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
                .addOuterCodeLine("@Override")
                .addBodyCodeLine("return (Comparable<T>)new Date();")
                .useType(Date.class)
            ).addConcretizedType(
                MyInterface.class
            ).expands(ToBeExtended.class)
        );
		unitSG.storeToClassPath(System.getProperty("user.home") + "/Desktop/bw-tests");
        System.out.println("\nGenerated code:\n" + unitSG.make());
	}
}
