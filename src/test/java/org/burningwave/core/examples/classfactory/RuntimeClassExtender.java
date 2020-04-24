package org.burningwave.core.examples.classfactory;

import static org.burningwave.core.assembler.StaticComponentContainer.ConstructorHelper;

import java.io.Serializable;
import java.lang.reflect.Modifier;
import java.util.Date;

import org.burningwave.core.Virtual;
import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.ClassFactory;
import org.burningwave.core.classes.ClassSourceGenerator;
import org.burningwave.core.classes.FunctionSourceGenerator;
import org.burningwave.core.classes.GenericSourceGenerator;
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
            //generating new method that override MyInterface.now()
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
                .addOuterCodeRow("@Override")
                .addBodyCodeRow("return (Comparable<T>)new Date();")
                .useType(Date.class)
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
        generatedClassObject.printSomeThing("print something call 1");
        System.out.println(
            ((MyInterface)generatedClassObject).now().toString()
        );
        //You can also invoke methods by casting to Virtual (an interface offered by the
        //library for faciliate use of runtime generated classes)
        Virtual virtualObject = (Virtual)generatedClassObject;
        //Invoke by using reflection
        virtualObject.invoke("printSomeThing", "print something call 2");
        //Invoke by using MethodHandle
        virtualObject.invokeDirect("printSomeThing", "print something call 3");
        System.out.println(
            ((Date)virtualObject.invokeDirect("now")).toString()
        );
    }   

    public static class ToBeExtended {

        public void printSomeThing(String toBePrinted) {
            System.out.println(toBePrinted);
        }

    }

    public static interface MyInterface {

        public <T extends Cloneable & Serializable> Comparable<T> now();

    }

    public static void main(String[] args) throws Throwable {
        execute();
    }
}