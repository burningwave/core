package org.burningwave.core;

import java.lang.reflect.Modifier;
import java.util.Collection;

import org.burningwave.core.classes.ClassSourceGenerator;
import org.burningwave.core.classes.FunctionSourceGenerator;
import org.burningwave.core.classes.GenericSourceGenerator;
import org.burningwave.core.classes.TypeDeclarationSourceGenerator;
import org.burningwave.core.classes.UnitSourceGenerator;
import org.burningwave.core.classes.VariableSourceGenerator;
import org.junit.jupiter.api.Test;

public class CodeGeneratorTest extends BaseTest {

	@Test
	public void generateUnitTestOne() throws Throwable {
		testDoesNotThrow(() -> {
			UnitSourceGenerator unit = UnitSourceGenerator.create("code.generator.try");
			unit.addClass(ClassSourceGenerator.createEnum(TypeDeclarationSourceGenerator.create("MyEnum")).addField(VariableSourceGenerator.create((TypeDeclarationSourceGenerator)null, "FIRST_VALUE")).addField(VariableSourceGenerator.create((TypeDeclarationSourceGenerator)null, "SECOND_VALUE")));
			FunctionSourceGenerator method = FunctionSourceGenerator.create("find").addModifier(Modifier.PUBLIC)
					.setTypeDeclaration(TypeDeclarationSourceGenerator.create(GenericSourceGenerator.create("F"), GenericSourceGenerator.create("G")))
					.setReturnType(TypeDeclarationSourceGenerator.create(Long.class))
					.addParameter(VariableSourceGenerator.create(TypeDeclarationSourceGenerator.create(Long.class), "parameter1")
							.addOuterCodeRow("@Parameter"))
					.addParameter(VariableSourceGenerator.create(TypeDeclarationSourceGenerator.create(String.class), "parameter2"))
					.addParameter(VariableSourceGenerator.create(TypeDeclarationSourceGenerator.create(Long.class), "parameter3"))
					.addBodyCodeRow("System.out.println(\"Hello world!\");")
					.addBodyCodeRow("System.out.println(\"How are you!\");").addBodyCodeRow("return new Long(1);")
					.addOuterCodeRow("@MethodAnnotation");

			FunctionSourceGenerator method2 = FunctionSourceGenerator.create("find2").addModifier(Modifier.PUBLIC)
					.setTypeDeclaration(TypeDeclarationSourceGenerator.create(GenericSourceGenerator.create("F"), GenericSourceGenerator.create("G")))
					.setReturnType(TypeDeclarationSourceGenerator.create(Long.class))
					.addParameter(VariableSourceGenerator.create(TypeDeclarationSourceGenerator.create(Long.class), "parameter1"))
					.addParameter(VariableSourceGenerator.create(TypeDeclarationSourceGenerator.create(String.class), "parameter2"))
					.addParameter(VariableSourceGenerator.create(TypeDeclarationSourceGenerator.create(Long.class), "parameter3"))
					.addBodyCodeRow("System.out.println(\"Hello world!\");")
					.addBodyCodeRow("System.out.println(\"How are you!\");").addBodyCodeRow("return new Long(1);");

			FunctionSourceGenerator constructor = FunctionSourceGenerator.create().addModifier(Modifier.PUBLIC).addBodyCodeRow("this.index1 = 1;");

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
							.addModifier(Modifier.PRIVATE).addOuterCodeRow("@Field").addOuterCodeRow("@Annotation2"))
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
							.addModifier(Modifier.PRIVATE).addOuterCodeRow("@Field"))
					.addField(VariableSourceGenerator.create(TypeDeclarationSourceGenerator.create(Integer.class), "index2")
							.addModifier(Modifier.PRIVATE))
					.addOuterCodeRow("@Annotation").addOuterCodeRow("@Annotation2")
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
									.addModifier(Modifier.PRIVATE).addOuterCodeRow("@Field"))
							.addField(VariableSourceGenerator.create(TypeDeclarationSourceGenerator.create(Integer.class), "index2")
									.addModifier(Modifier.PRIVATE))
							.addOuterCodeRow("@Annotation").addOuterCodeRow("@Annotation2"))
					.addMethod(method)).addOuterCodeRow("@Annotation").addOuterCodeRow("@Annotation2");
			unit.addClass(cls);
			unit.addClass(cls);
			logDebug(unit.make());
			unit.getAllClasses().keySet().forEach(this::logDebug);
		});
	}
}
