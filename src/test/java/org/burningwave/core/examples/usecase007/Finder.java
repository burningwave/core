package org.burningwave.core.examples.usecase007;

import java.util.Collection;

import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.classes.ClassCriteria;
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
		).by(
			ClassCriteria.create().allThat((cls) -> {
				return cls.getPackage().getName().matches(".*springframework.*");
			})
		);
		
		SearchResult searchResult = classHunter.findBy(criteria);
		
		return searchResult.getItemsFound();
	}
	
}