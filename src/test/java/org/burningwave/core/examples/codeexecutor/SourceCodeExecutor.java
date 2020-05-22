package org.burningwave.core.examples.codeexecutor;

import java.time.LocalDateTime;

import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.ExecuteConfig;

public class SourceCodeExecutor {
    
    public static void execute() {
        ComponentSupplier componentSupplier = ComponentContainer.getInstance();
        System.out.println("Time is: " +
            componentSupplier.getCodeExecutor().execute(
                ExecuteConfig.forPropertiesFile("custom-folder/code.properties")
                //Uncomment the line below if the path you have supplied is an absolute path
                //.setFilePathAsAbsolute(true)
                .setPropertyName("code-block-1")
                .withParameter(LocalDateTime.now())
            )    
        );
    }
    
    public static void main(String[] args) {
        execute();
    }
}