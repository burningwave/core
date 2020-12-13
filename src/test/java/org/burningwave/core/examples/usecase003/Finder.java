package org.burningwave.core.examples.usecase003;

import java.util.Collection;

import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.CacheableSearchConfig;
import org.burningwave.core.classes.ClassCriteria;
import org.burningwave.core.classes.ClassPathHunter;
import org.burningwave.core.classes.SearchConfig;
import org.burningwave.core.classes.ClassPathHunter.SearchResult;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.PathHelper;

public class Finder {

	public Collection<FileSystemItem> find() {
		ComponentSupplier componentSupplier = ComponentContainer.getInstance();
		PathHelper pathHelper = componentSupplier.getPathHelper();
		ClassPathHunter classPathHunter = componentSupplier.getClassPathHunter();

		CacheableSearchConfig searchConfig = SearchConfig.forPaths(
			//Here you can add all absolute path you want:
			//both folders, zip and jar will be recursively scanned.
			//For example you can add: "C:\\Users\\user\\.m2"
			//With the row below the search will be executed on runtime Classpaths
			pathHelper.getMainClassPaths()
		).by(
			ClassCriteria.create().allThoseThatMatch(cls ->
				cls.getName().equals("Finder")	  
			)
		);		

		SearchResult searchResult = classPathHunter.findBy(searchConfig);
		return searchResult.getClassPaths();
	}

}