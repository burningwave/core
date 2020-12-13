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
    
public class ClassForPackageAndAnnotationFinder {
    
    public Collection<Class<?>> find() {
        ComponentSupplier componentSupplier = ComponentContainer.getInstance();
        ClassHunter classHunter = componentSupplier.getClassHunter();
        
        try (
        	SearchResult result = classHunter.findBy(
	            //Highly optimized scanning by filtering resources before loading from ClassLoader
	            SearchConfig.forResources(
	                "org/springframework"
	            ).by(
	                    ClassCriteria.create().allThoseThatMatch((cls) -> {
	                        return cls.getAnnotations() != null && cls.getAnnotations().length > 0;
	                    }).or().byMembers(
	                        MethodCriteria.withoutConsideringParentClasses().allThoseThatMatch((method) -> {
	                            return method.getAnnotations() != null && method.getAnnotations().length > 0;
	                        })
	                    ).or().byMembers(
	                        FieldCriteria.withoutConsideringParentClasses().allThoseThatMatch((field) -> {
	                            return field.getAnnotations() != null && field.getAnnotations().length > 0;
	                        })
	                    ).or().byMembers(
	                        ConstructorCriteria.withoutConsideringParentClasses().allThoseThatMatch((ctor) -> {
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