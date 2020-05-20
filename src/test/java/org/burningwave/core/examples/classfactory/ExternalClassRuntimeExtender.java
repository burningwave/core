package org.burningwave.core.examples.classfactory;

import java.io.InputStream;
import java.lang.reflect.Modifier;

import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.ClassFactory;
import org.burningwave.core.classes.ClassFactory.LoadOrBuildAndDefineConfig;
import org.burningwave.core.classes.ClassSourceGenerator;
import org.burningwave.core.classes.FunctionSourceGenerator;
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
			LoadOrBuildAndDefineConfig.forUnitSourceGenerator(unitSG).addCompilationClassPaths(
				pathHelper.getPaths(PathHelper.MAIN_CLASS_PATHS, PathHelper.MAIN_CLASS_PATHS_EXTENSION)
			).addClassPathsWhereToSearchNotFoundClasses(
				pathHelper.getAbsolutePathOfResource("../../src/test/external-resources/libs-for-test.zip")
			)
		);
		classRetriever.get("packagename.ComplexExample");
	}
	
    public static void main(String[] args) throws Throwable {
        execute();
    }
	
}
