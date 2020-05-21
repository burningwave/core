package org.burningwave.core.examples.codeexecutor;

import java.util.ArrayList;
import java.util.List;

import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.ExecuteConfig;
import org.burningwave.core.classes.StatementSourceGenerator;

public class SourceCodeExecutor {
    
    public static Integer execute() {
        ComponentSupplier componentSupplier = ComponentContainer.getInstance();
        return componentSupplier.getCodeExecutor().execute(
            ExecuteConfig.forStatementSourceGenerator(
                StatementSourceGenerator.createSimple().useType(ArrayList.class, List.class)
                .addCodeRow("System.out.println(\"number to add: \" + parameter[0]);")
                .addCodeRow("List<Integer> numbers = new ArrayList<>();")
                .addCodeRow("numbers.add((Integer)parameter[0]);")
                .addCodeRow("System.out.println(\"number list size: \" + numbers.size());")
                .addCodeRow("System.out.println(\"number in the list: \" + numbers.get(0));")
                .addCodeRow("Integer inputNumber = (Integer)parameter[0];")
                .addCodeRow("return (T)new Integer(inputNumber + (Integer)parameter[1]);")
            ).withParameter(Integer.valueOf(5), Integer.valueOf(3))
        );
        
    }
    
    public static void main(String[] args) {
        System.out.println("Total is: " + execute());
    }
}
