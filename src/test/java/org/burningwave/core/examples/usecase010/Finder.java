package org.burningwave.core.examples.usecase010;

import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Date;

import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.classes.ClassCriteria;
import org.burningwave.core.classes.FieldCriteria;
import org.burningwave.core.classes.hunter.CacheableSearchConfig;
import org.burningwave.core.classes.hunter.ClassHunter;
import org.burningwave.core.classes.hunter.ClassHunter.SearchResult;
import org.burningwave.core.classes.hunter.SearchConfig;
import org.burningwave.core.io.PathHelper;

public class Finder {       
	
    public Collection<Class<?>> find() {
        ComponentContainer componentConatiner = ComponentContainer.getInstance();
        PathHelper pathHelper = componentConatiner.getPathHelper();
        ClassHunter classHunter = componentConatiner.getClassHunter();

        FieldCriteria fieldCriteria = FieldCriteria.create().allThat((field) -> {
            return Modifier.isProtected(field.getModifiers());
        }).result((foundFields) -> {
            return foundFields.size() >= 2;
        });
        
        CacheableSearchConfig searchConfig = SearchConfig.forPaths(
    		//Here you can add all absolute path you want:
            //both folders, zip and jar will be recursively scanned.
            //For example you can add: "C:\\Users\\user\\.m2"
            //With the row below the search will be executed on runtime Classpaths
            pathHelper.getMainClassPaths()
        ).by(
        	ClassCriteria.create().byMembers(
	            fieldCriteria
	        ).useClasses(
	            Date.class,
	            Object.class
	        )
	    );

        SearchResult searchResult = classHunter.findBy(searchConfig);

        //If you need all found fields unconment this
        //searchResult.getMembersFoundFlatMap().values();

        return searchResult.getItemsFound();
    }

}