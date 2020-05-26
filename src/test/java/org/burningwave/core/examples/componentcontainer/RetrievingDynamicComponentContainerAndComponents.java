package org.burningwave.core.examples.componentcontainer;

import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.ClassFactory;
import org.burningwave.core.classes.ClassHunter;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.iterable.Properties;

public class RetrievingDynamicComponentContainerAndComponents {

    public static void execute() throws Throwable {
        //In this case we are retrieving the singleton component container instance
        ComponentSupplier componentSupplier = ComponentContainer.getInstance();
        
        //In this case we are creating a component container by using a custom configuration file
        ComponentSupplier customComponentSupplier = ComponentContainer.create("your-custom-properties-file.properties");
        
        //In this case we are creating a component container programmatically by using a custom properties object
        Properties configProps = new Properties();
        configProps.put(ClassFactory.Configuration.Key.DEFAULT_CLASS_LOADER, Thread.currentThread().getContextClassLoader());
        configProps.put(ClassHunter.Configuration.Key.PARENT_CLASS_LOADER_FOR_PATH_SCANNER_CLASS_LOADER, Thread.currentThread().getContextClassLoader());
        ComponentSupplier customComponentSupplier2 = ComponentContainer.create(configProps);
        
        PathHelper pathHelper = componentSupplier.getPathHelper();
        ClassFactory classFactory = customComponentSupplier.getClassFactory();
        ClassHunter classHunter = customComponentSupplier2.getClassHunter();
       
    }   
    
}