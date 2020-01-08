package org.burningwave.core.examples.usecase003;

import java.io.File;
import java.util.Collection;

import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.classes.ClassCriteria;
import org.burningwave.core.classes.hunter.CacheableSearchConfig;
import org.burningwave.core.classes.hunter.ClassPathHunter;
import org.burningwave.core.classes.hunter.SearchConfig;
import org.burningwave.core.classes.hunter.SearchResult;
import org.burningwave.core.io.PathHelper;

public class Finder {

    public Collection<File> find() {
        ComponentContainer componentConatiner = ComponentContainer.getInstance();
        PathHelper pathHelper = componentConatiner.getPathHelper();
        ClassPathHunter classPathHunter = componentConatiner.getClassPathHunter();

        CacheableSearchConfig criteria = SearchConfig.forPaths(
    		//Here you can add all absolute path you want:
            //both folders, zip and jar will be recursively scanned.
            //For example you can add: "C:\\Users\\user\.m2"
            //With the row below the search will be executed on runtime Classpaths
            pathHelper.getMainClassPaths()
        ).by(ClassCriteria.create().allThat(cls ->
        		cls.getName().equals("Finder")      
        	)
        );        

        SearchResult<Class<?>, File> searchResult = classPathHunter.findBy(criteria);
        return searchResult.getItemsFound();
    }

}