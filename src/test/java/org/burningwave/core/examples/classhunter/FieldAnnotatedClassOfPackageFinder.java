package org.burningwave.core.examples.classhunter;
import java.util.Collection;

import javax.validation.constraints.NotNull;

import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.ClassCriteria;
import org.burningwave.core.classes.ClassHunter;
import org.burningwave.core.classes.ClassHunter.SearchResult;
import org.burningwave.core.classes.FieldCriteria;
import org.burningwave.core.classes.SearchConfig;
    
public class FieldAnnotatedClassOfPackageFinder {
    
    public Collection<Class<?>> find() {
        ComponentSupplier componentSupplier = ComponentContainer.getInstance();
        ClassHunter classHunter = componentSupplier.getClassHunter();
        
        try (
            SearchResult result = classHunter.findBy(
                //Highly optimized scanning by filtering resources before loading from ClassLoader
                SearchConfig.forResources(
                    "org/springframework"
                ).by(
                    ClassCriteria.create().byMembers(
                        FieldCriteria.withoutConsideringParentClasses().allThoseThatMatch((field) -> {
                            return field.getAnnotation(NotNull.class) != null;
                        })
                    )
                )
            )
        ) {
            return result.getClasses();
        }
    }
    
}