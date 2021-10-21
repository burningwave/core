package org.burningwave.core.examples.codeexecutor;

import java.util.ArrayList;
import java.util.List;

import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.BodySourceGenerator;
import org.burningwave.core.classes.ExecuteConfig;

public class SourceCodeExecutor {
    
    public static Integer execute() {
        ComponentSupplier componentSupplier = ComponentContainer.getInstance();
        return componentSupplier.getCodeExecutor().execute(
            ExecuteConfig.forBodySourceGenerator(
                BodySourceGenerator.createSimple().useType(ArrayList.class, List.class)
                .addCodeLine("System.out.println(\"number to add: \" + parameter[0]);")
                .addCodeLine("List<Integer> numbers = new ArrayList<>();")
                .addCodeLine("numbers.add((Integer)parameter[0]);")
                .addCodeLine("System.out.println(\"number list size: \" + numbers.size());")
                .addCodeLine("System.out.println(\"number in the list: \" + numbers.get(0));")
                .addCodeLine("Integer inputNumber = (Integer)parameter[0];")
                .addCodeLine("return Integer.valueOf(inputNumber + (Integer)parameter[1]);")
            ).withParameter(Integer.valueOf(5), Integer.valueOf(3))
        );
        
    }
    
    public static void main(String[] args) {
        System.out.println("Total is: " + execute());
    }

}