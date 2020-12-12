package org.burningwave.core.examples.usecase002;

import java.util.Collection;
import java.util.Date;

import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.CacheableSearchConfig;
import org.burningwave.core.classes.ClassCriteria;
import org.burningwave.core.classes.ClassHunter;
import org.burningwave.core.classes.ClassHunter.SearchResult;
import org.burningwave.core.classes.MethodCriteria;
import org.burningwave.core.classes.SearchConfig;
import org.burningwave.core.io.PathHelper;

public class Finder {       

    public Collection<Class<?>> find() {
        ComponentSupplier componentSupplier = ComponentContainer.getInstance();
        PathHelper pathHelper = componentSupplier.getPathHelper();
        ClassHunter classHunter = componentSupplier.getClassHunter();

        MethodCriteria methodCriteria = MethodCriteria.forEntireClassHierarchy().name(methodName -> 
            methodName.startsWith("set")
        ).and().parameterType(
            (uploadedClasses, array, idx) ->
                idx == 0 && array[idx].equals(uploadedClasses.get(Date.class))
        ).skip((classes, initialClass, examinedClass) -> 
            classes.get(Object.class) == examinedClass
        );            

        CacheableSearchConfig searchConfig = SearchConfig.forPaths(
            //Here you can add all absolute path you want:
            //both folders, zip and jar will be recursively scanned.
            //For example you can add: "C:\\Users\\user\\.m2"
            //With the row below the search will be executed on runtime Classpaths
            pathHelper.getMainClassPaths()
        ).by(
            ClassCriteria.create().byMembers(
                methodCriteria
            ).useClasses(
                Date.class,
                Object.class
            )
        );

        SearchResult searchResult = classHunter.findBy(searchConfig);

        //If you need all found methods unconment this
        //searchResult.getMembersFlatMap().values();

        return searchResult.getClasses();
    }

}