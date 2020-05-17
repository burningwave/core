package org.burningwave.core.examples.classfactory;

import java.lang.reflect.Modifier;
import java.util.function.Function;

import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.AnnotationSourceGenerator;
import org.burningwave.core.classes.ClassFactory;
import org.burningwave.core.classes.ClassSourceGenerator;
import org.burningwave.core.classes.FunctionSourceGenerator;
import org.burningwave.core.classes.TypeDeclarationSourceGenerator;
import org.burningwave.core.classes.UnitSourceGenerator;
import org.burningwave.core.classes.VariableSourceGenerator;

public class FunctionalInterfaceBuilder {

    @SuppressWarnings("resource")
	public static void execute() throws Throwable {
        UnitSourceGenerator unitSG = UnitSourceGenerator.create("packagename").addClass(
            ClassSourceGenerator.createInterface(
                TypeDeclarationSourceGenerator.create("MyFunctionalInterface")
            ).addModifier(
                Modifier.PUBLIC
            //generating new method that override MyInterface.convert(LocalDateTime)
            ).addMethod(
                FunctionSourceGenerator.create("convert")
                .setReturnType(TypeDeclarationSourceGenerator.create(Integer.class))
                .addParameter(VariableSourceGenerator.create(String.class, "number"))
                .addModifier(Modifier.ABSTRACT)
                .useType(Function.class)
            ).addAnnotation(AnnotationSourceGenerator.create(FunctionalInterface.class))
        );
        System.out.println("\nGenerated code:\n" + unitSG.make());
        //With this we store the generated source to a path
        unitSG.storeToClassPath(System.getProperty("user.home") + "/Desktop/bw-tests");
        ComponentSupplier componentSupplier = ComponentContainer.getInstance();
        ClassFactory classFactory = componentSupplier.getClassFactory();
        @SuppressWarnings("unused")
		Class<?> generatedClass = classFactory.buildAndLoadOrUpload(
            unitSG
        ).get(
            "packagename.MyFunctionalInterface"
        );
    }   

    public static void main(String[] args) throws Throwable {
        execute();
    }
}