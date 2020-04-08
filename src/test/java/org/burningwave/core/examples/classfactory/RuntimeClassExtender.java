package org.burningwave.core.examples.classfactory;

import static org.burningwave.core.assembler.StaticComponentContainer.ConstructorHelper;

import java.lang.reflect.Modifier;

import org.burningwave.core.Virtual;
import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.ClassFactory;
import org.burningwave.core.classes.ClassSourceGenerator;
import org.burningwave.core.classes.FunctionSourceGenerator;
import org.burningwave.core.classes.TypeDeclarationSourceGenerator;
import org.burningwave.core.classes.UnitSourceGenerator;

public class RuntimeClassExtender {

    @SuppressWarnings("resource")
	public static void execute() throws Throwable {
        UnitSourceGenerator unitSG = UnitSourceGenerator.create("packagename").addClass(
            ClassSourceGenerator.create(
                TypeDeclarationSourceGenerator.create("MyExtendedClass")
            ).addModifier(
                Modifier.PUBLIC
            //generating new method that override MyInterface.getNumber()
            ).addMethod(
                FunctionSourceGenerator.create("getNumber")
                .setReturnType(Integer.class)
                .addModifier(Modifier.PUBLIC)
                .addOuterCodeRow("@Override")
                .addBodyCodeRow("return 1;")
            ).addConcretizedType(
                MyInterface.class
            ).expands(ToBeExtended.class)
        );
        System.out.println("\nGenerated code:\n" + unitSG.make());
        ComponentSupplier componentSupplier = ComponentContainer.getInstance();
        ClassFactory classFactory = componentSupplier.getClassFactory();
        //this method compile all compilation units and upload the generated classes to default
        //class loader declared with property "class-factory.default-class-loader" in 
        //burningwave.properties file (see "Overview and configuration").
        //If you need to upload the class to another class loader use
        //buildAndLoadOrUploadTo(ClassLoader classLoader, UnitSourceGenerator... unitsCode) method
        Class<?> generatedClass = classFactory.buildAndLoadOrUpload(
            unitSG
        ).get(
            "packagename.MyExtendedClass"
        );
        ToBeExtended generatedClassObject =
            ConstructorHelper.newInstanceOf(generatedClass);
        generatedClassObject.printSomeThing();
        System.out.println(
            ((MyInterface)generatedClassObject).getNumber().toString()
        );
        //You can also invoke methods by casting to Virtual (an interface offered by the
        //library for faciliate use of runtime generated classes)
        Virtual virtualObject = (Virtual)generatedClassObject;
        //Invoke by using reflection
        virtualObject.invoke("printSomeThing");
        //Invoke by using MethodHandle
        virtualObject.invokeDirect("printSomeThing");
        System.out.println(
            ((Integer)virtualObject.invokeDirect("getNumber")).toString()
        );
    }   

    public static class ToBeExtended {

        public void printSomeThing() {
            System.out.println("Called");
        }

    }

    public static interface MyInterface {

        public Integer getNumber();

    }

    public static void main(String[] args) throws Throwable {
        execute();
    }
}