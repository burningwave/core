package org.burningwave.core.examples.classfactory;

import java.io.InputStream;
import java.lang.reflect.Modifier;

import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.ClassFactory;
import org.burningwave.core.classes.ClassSourceGenerator;
import org.burningwave.core.classes.FunctionSourceGenerator;
import org.burningwave.core.classes.LoadOrBuildAndDefineConfig;
import org.burningwave.core.classes.TypeDeclarationSourceGenerator;
import org.burningwave.core.classes.UnitSourceGenerator;
import org.burningwave.core.classes.VariableSourceGenerator;
import org.burningwave.core.io.PathHelper;

public class ExternalClassRuntimeExtender {
	
	//This example try to extend a class that is not in the classpath: in this case the class
	//will be searched in the libs-for-test.zip file
	public static void execute() {
		ComponentSupplier componentSupplier = ComponentContainer.getInstance();
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

		ClassFactory.ClassRetriever classRetriever = componentSupplier.getClassFactory().loadOrBuildAndDefine(
			LoadOrBuildAndDefineConfig.forUnitSourceGenerator(unitSG)
			//With this we are adding an external path where to search classes needed for class compilation and loading.
			//It possible to add folder, .zip, .jar, .ear, .war, .jmod or .class files
			//The difference between this method and .setClassRepository is that the method .addClassRepository will add 
			//paths to the configured defaults paths, instead the method .setClassRepository will replace the configured 
			//defaults paths and the subsequent calls to method .addClassRepository will add paths to the replacement paths
			.addClassRepository(
				pathHelper.getAbsolutePathOfResource("../../src/test/external-resources/libs-for-test.zip")
			)
		);
		classRetriever.get("packagename.ComplexExample");
	}
	
    public static void main(String[] args) throws Throwable {
        execute();
    }
	
}