package org.burningwave.core.examples.codeexecutor;

import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.assembler.ComponentSupplier;

public class SourceCodeExecutor {
    
    public static Integer execute() {
        ComponentSupplier componentSupplier = ComponentContainer.getInstance();
        return componentSupplier.getCodeExecutor().execute("code-block-1");
        
    }
    
    public static void main(String[] args) {
        System.out.println("Total is: " + execute());
    }
}
