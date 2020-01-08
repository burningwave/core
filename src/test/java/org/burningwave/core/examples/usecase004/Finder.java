package org.burningwave.core.examples.usecase004;

import java.util.Collection;

import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.classes.ClassCriteria;
import org.burningwave.core.classes.MethodCriteria;
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

        CacheableSearchConfig criteria = SearchConfig.forPaths(
    		//Here you can add all absolute path you want:
            //both folders, zip and jar will be recursively scanned.
            //For example you can add: "C:\\Users\\user\\.m2"
            //With the row below the search will be executed on runtime Classpaths
            pathHelper.getMainClassPaths()
        ).by(ClassCriteria.create().allThat((cls) -> {
	            return cls.getAnnotations() != null && cls.getAnnotations().length > 0;
	        }).or().byMembers(
	            MethodCriteria.byScanUpTo((lastClassInHierarchy, currentScannedClass) -> {
	                return lastClassInHierarchy.equals(currentScannedClass);
	            }).allThat((method) -> {
	                return method.getAnnotations() != null && method.getAnnotations().length > 0;
	            })
	        )
        );

        SearchResult searchResult = classHunter.findBy(criteria);

        //If you need all annotaded methods unconment this
        //searchResult.getMembersFoundFlatMap().values();

        return searchResult.getItemsFound();
    }
}