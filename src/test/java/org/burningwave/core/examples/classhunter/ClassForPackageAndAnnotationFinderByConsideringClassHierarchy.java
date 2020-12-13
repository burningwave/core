package org.burningwave.core.examples.classhunter;

import java.util.Collection;

import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.ClassCriteria;
import org.burningwave.core.classes.ClassHunter;
import org.burningwave.core.classes.ClassHunter.SearchResult;
import org.burningwave.core.classes.ConstructorCriteria;
import org.burningwave.core.classes.FieldCriteria;
import org.burningwave.core.classes.MethodCriteria;
import org.burningwave.core.classes.SearchConfig;
    
public class ClassForPackageAndAnnotationFinderByConsideringClassHierarchy {
    
    public Collection<Class<?>> find() {
        ComponentSupplier componentSupplier = ComponentContainer.getInstance();
        ClassHunter classHunter = componentSupplier.getClassHunter();
        
        try (
                SearchResult result = classHunter.findBy(
                //Highly optimized scanning by filtering resources before loading from ClassLoader
                SearchConfig.forResources(
                    "org/springframework"
                ).by(
                    ClassCriteria.create().allThoseThatHaveAMatchInHierarchy((cls) -> {
                        return cls.getAnnotations() != null && cls.getAnnotations().length > 0;
                    }).or().byMembers(
                        MethodCriteria.forEntireClassHierarchy().allThoseThatMatch((method) -> {
                            return method.getAnnotations() != null && method.getAnnotations().length > 0;
                        })
                    ).or().byMembers(
                        FieldCriteria.forEntireClassHierarchy().allThoseThatMatch((field) -> {
                            return field.getAnnotations() != null && field.getAnnotations().length > 0;
                        })
                    ).or().byMembers(
                        ConstructorCriteria.forEntireClassHierarchy().allThoseThatMatch((ctor) -> {
                            return ctor.getAnnotations() != null && ctor.getAnnotations().length > 0;
                        })
                    )
                )
            )
        ) {
            return result.getClasses();
        }
    }
   
}