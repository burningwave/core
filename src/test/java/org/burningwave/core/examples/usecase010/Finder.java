package org.burningwave.core.examples.usecase010;

import java.util.Collection;
import java.util.Date;

import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.ClassCriteria;
import org.burningwave.core.classes.ConstructorCriteria;
import org.burningwave.core.classes.MethodCriteria;
import org.burningwave.core.classes.hunter.CacheableSearchConfig;
import org.burningwave.core.classes.hunter.ClassHunter;
import org.burningwave.core.classes.hunter.ClassHunter.SearchResult;
import org.burningwave.core.classes.hunter.SearchConfig;
import org.burningwave.core.io.PathHelper;

public class Finder {       
	
    public Collection<Class<?>> find() {
    	ComponentSupplier componentSupplier = ComponentContainer.getInstance();
        PathHelper pathHelper = componentSupplier.getPathHelper();
        ClassHunter classHunter = componentSupplier.getClassHunter();
        
        MethodCriteria methodCriteria = MethodCriteria.byScanUpTo((classes, initialClass, examinedClass) -> 
			//By default all class hierarchy is scanned, with this expression we don't scan over class hierarchy
        	initialClass == examinedClass
        ).and().name((methodName) ->
        	methodName.startsWith("set")
		).result((methodFounds) ->
			methodFounds.size() >= 2
		);	
        
		ConstructorCriteria constructorCriteria = ConstructorCriteria.byScanUpTo((classes, initialClass, examinedClass) -> 
			initialClass == examinedClass
		).and().parameterType((uploadedClasses, array, idx) ->
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
	        ).and().byMembers(
	        	constructorCriteria
	        ).useClasses(
	            Date.class,
	            Object.class
	        )
	    );

        SearchResult searchResult = classHunter.findBy(searchConfig);

        //If you need all Constructor founds  unconment this
        //searchResult.getMembersFoundBy(constructorCriteria);
        
        //If you need all Method founds  unconment this
        //searchResult.getMembersFoundBy(methodCriteria);

        return searchResult.getItemsFound();
    }

}