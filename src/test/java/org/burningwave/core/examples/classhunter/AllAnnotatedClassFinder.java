package org.burningwave.core.examples.classhunter;

import java.util.Collection;

import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.CacheableSearchConfig;
import org.burningwave.core.classes.ClassCriteria;
import org.burningwave.core.classes.ClassHunter;
import org.burningwave.core.classes.ClassHunter.SearchResult;
import org.burningwave.core.classes.ConstructorCriteria;
import org.burningwave.core.classes.FieldCriteria;
import org.burningwave.core.classes.MethodCriteria;
import org.burningwave.core.classes.SearchConfig;
import org.burningwave.core.io.PathHelper;

public class AllAnnotatedClassFinder {

    public Collection<Class<?>> find() {
        ComponentSupplier componentSupplier = ComponentContainer.getInstance();
        PathHelper pathHelper = componentSupplier.getPathHelper();
        ClassHunter classHunter = componentSupplier.getClassHunter();

        CacheableSearchConfig searchConfig = SearchConfig.forPaths(
            //Here you can add all absolute path you want:
            //both folders, zip and jar will be recursively scanned.
            //For example you can add: "C:\\Users\\user\\.m2"
            //With the row below the search will be executed on runtime Classpaths
            pathHelper.getMainClassPaths()
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
        );

        try (SearchResult searchResult = classHunter.loadInCache(searchConfig).find()) {
    
            //If you need all annotaded methods unconment this
            //searchResult.getMembersFlatMap().values();
    
            return searchResult.getClasses();
        }
    }
}