package org.burningwave.core.examples.usecase010;

import java.util.Collection;
import java.util.Date;

import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.CacheableSearchConfig;
import org.burningwave.core.classes.ClassCriteria;
import org.burningwave.core.classes.ClassHunter;
import org.burningwave.core.classes.ClassHunter.SearchResult;
import org.burningwave.core.classes.ConstructorCriteria;
import org.burningwave.core.classes.MethodCriteria;
import org.burningwave.core.classes.SearchConfig;
import org.burningwave.core.io.PathHelper;

public class Finder {       
    
    public Collection<Class<?>> find() {
        ComponentSupplier componentSupplier = ComponentContainer.getInstance();
        PathHelper pathHelper = componentSupplier.getPathHelper();
        ClassHunter classHunter = componentSupplier.getClassHunter();
        
        MethodCriteria methodCriteria = MethodCriteria.withoutConsideringParentClasses().name((methodName) ->
            methodName.startsWith("set")
        ).result((methodFounds) ->
            methodFounds.size() >= 2
        );    
        
        ConstructorCriteria constructorCriteria = ConstructorCriteria.withoutConsideringParentClasses()
            .parameterType((uploadedClasses, array, idx) ->
                idx == 0 && array[idx].equals(uploadedClasses.get(Date.class)
            )
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
            ).and().byMembers(
                constructorCriteria
            ).useClasses(
                Date.class,
                Object.class
            )
        );

        try(SearchResult searchResult = classHunter.findBy(searchConfig)) {

            //If you need all Constructor founds  unconment this
            //searchResult.getMembersFoundBy(constructorCriteria);
            
            //If you need all Method founds  unconment this
            //searchResult.getMembersFoundBy(methodCriteria);
    
            return searchResult.getClasses();
        }
    }

}