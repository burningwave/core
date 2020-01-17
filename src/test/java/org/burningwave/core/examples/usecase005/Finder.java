package org.burningwave.core.examples.usecase005;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.ClassCriteria;
import org.burningwave.core.classes.hunter.CacheableSearchConfig;
import org.burningwave.core.classes.hunter.ClassHunter;
import org.burningwave.core.classes.hunter.SearchConfig;
import org.burningwave.core.io.PathHelper;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;


public class Finder {

	public List<String> find() {
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
			ClassCriteria.create().allThat((cls) -> {
				return
					//Unconment one of this if you need to filter for package name
					//cls.getPackage().getName().matches("regular expression") &&
					//cls.getPackage().getName().startsWith("com") &&
					//cls.getPackage().getName().equals("com.something") &&
					cls.getAnnotation(Controller.class) != null &&
					cls.getAnnotation(RequestMapping.class) != null;
			})
		);

		List<String> pathsList = classHunter.findBy(searchConfig).getClasses().stream().map(
			cls -> Arrays.asList(cls.getAnnotation(RequestMapping.class).value())
		).flatMap(List::stream).distinct().collect(Collectors.toList());

		return pathsList;
	}

}