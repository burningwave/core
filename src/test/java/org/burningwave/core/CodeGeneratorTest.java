package org.burningwave.core;

import java.lang.reflect.Modifier;
import java.util.Collection;

import org.burningwave.core.classes.source.Class;
import org.burningwave.core.classes.source.Function;
import org.burningwave.core.classes.source.Generic;
import org.burningwave.core.classes.source.TypeDeclaration;
import org.burningwave.core.classes.source.Unit;
import org.burningwave.core.classes.source.Variable;
import org.junit.jupiter.api.Test;

public class CodeGeneratorTest extends BaseTest {

	@Test
	public void generateUnitTestOne() throws Throwable {
		testDoesNotThrow(() -> {
			Unit unit = Unit.create("code.generator.try");
			unit.addClass(Class.createEnum(TypeDeclaration.create("MyEnum")).addField(Variable.create(null, "FIRST_VALUE")).addField(Variable.create(null, "SECOND_VALUE")));
			Function method = Function.create("find").addModifier(Modifier.PUBLIC)
					.setTypeDeclaration(TypeDeclaration.create(Generic.create("F"), Generic.create("G")))
					.setReturnType(TypeDeclaration.create(Long.class))
					.addParameter(Variable.create(TypeDeclaration.create(Long.class), "parameter1")
							.addOuterCodeRow("@Parameter"))
					.addParameter(Variable.create(TypeDeclaration.create(String.class), "parameter2"))
					.addParameter(Variable.create(TypeDeclaration.create(Long.class), "parameter3"))
					.addBodyCodeRow("System.out.println(\"Hello world!\");")
					.addBodyCodeRow("System.out.println(\"How are you!\");").addBodyCodeRow("return new Long(1);")
					.addOuterCodeRow("@MethodAnnotation");

			Function method2 = Function.create("find2").addModifier(Modifier.PUBLIC)
					.setTypeDeclaration(TypeDeclaration.create(Generic.create("F"), Generic.create("G")))
					.setReturnType(TypeDeclaration.create(Long.class))
					.addParameter(Variable.create(TypeDeclaration.create(Long.class), "parameter1"))
					.addParameter(Variable.create(TypeDeclaration.create(String.class), "parameter2"))
					.addParameter(Variable.create(TypeDeclaration.create(Long.class), "parameter3"))
					.addBodyCodeRow("System.out.println(\"Hello world!\");")
					.addBodyCodeRow("System.out.println(\"How are you!\");").addBodyCodeRow("return new Long(1);");

			Function constructor = Function.create().addModifier(Modifier.PUBLIC).addBodyCodeRow("this.index1 = 1;");

			Class cls = Class
					.create(TypeDeclaration.create("Generated0")
							.addGeneric(Generic.create("T").expands(TypeDeclaration.create("Class")
									.addGeneric(Generic.create("F").expands(
											TypeDeclaration.create("ClassTwo").addGeneric(Generic.create("H"))))))
							.addGeneric(Generic.create("?")
									.parentOf(TypeDeclaration.create("Free").addGeneric(Generic.create("S"))
											.addGeneric(Generic.create("Y")))))
					.addModifier(Modifier.PUBLIC).expands(Object.class)
					.addField(Variable.create(TypeDeclaration.create(Integer.class), "index1").setValue("new Integer(1)")
							.addModifier(Modifier.PRIVATE).addOuterCodeRow("@Field").addOuterCodeRow("@Annotation2"))
					.addField(Variable.create(TypeDeclaration.create(Collection.class).addGeneric(Generic.create("String").addOuterCode("@NotNull")), "collection")
							.addModifier(Modifier.PRIVATE))
					.addConstructor(constructor).addMethod(method).addMethod(method2);
			cls.addInnerClass(Class
					.create(TypeDeclaration.create("Generated1")
							.addGeneric(Generic.create("T").expands(TypeDeclaration.create("Class")
									.addGeneric(Generic.create("F").expands(
											TypeDeclaration.create("ClassTwo").addGeneric(Generic.create("H"))))))
							.addGeneric(Generic.create("?")
									.parentOf(TypeDeclaration.create("Free").addGeneric(Generic.create("S"))
											.addGeneric(Generic.create("Y")))))
					.addModifier(Modifier.PUBLIC).expands(Object.class)
					.addField(Variable.create(TypeDeclaration.create(Integer.class), "index1")
							.addModifier(Modifier.PRIVATE).addOuterCodeRow("@Field"))
					.addField(Variable.create(TypeDeclaration.create(Integer.class), "index2")
							.addModifier(Modifier.PRIVATE))
					.addOuterCodeRow("@Annotation").addOuterCodeRow("@Annotation2")
					.addInnerClass(Class
							.create(TypeDeclaration.create("Generated2")
									.addGeneric(Generic.create("T")
											.expands(TypeDeclaration.create("Class")
													.addGeneric(Generic.create("F")
															.expands(TypeDeclaration.create("ClassTwo")
																	.addGeneric(Generic.create("H"))))))
									.addGeneric(Generic.create("?")
											.parentOf(TypeDeclaration.create("Free").addGeneric(Generic.create("S"))
													.addGeneric(Generic.create("Y")))))
							.addModifier(Modifier.PUBLIC).expands(Object.class)
							.addField(Variable.create(TypeDeclaration.create(Integer.class), "index1")
									.addModifier(Modifier.PRIVATE).addOuterCodeRow("@Field"))
							.addField(Variable.create(TypeDeclaration.create(Integer.class), "index2")
									.addModifier(Modifier.PRIVATE))
							.addOuterCodeRow("@Annotation").addOuterCodeRow("@Annotation2"))
					.addMethod(method)).addOuterCodeRow("@Annotation").addOuterCodeRow("@Annotation2");
			unit.addClass(cls);
			unit.addClass(cls);
			System.out.println(unit.make());
			unit.getAllClasses().keySet().forEach(System.out::println);
		});
	}
}
