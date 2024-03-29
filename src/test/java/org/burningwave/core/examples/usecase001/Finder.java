package org.burningwave.core.examples.usecase001;

import java.io.Closeable;
import java.io.Serializable;
import java.util.Collection;

import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.ClassCriteria;
import org.burningwave.core.classes.ClassHunter;
import org.burningwave.core.classes.ClassHunter.SearchResult;
import org.burningwave.core.classes.SearchConfig;
import org.burningwave.core.io.PathHelper;

public class Finder {

    public Collection<Class<?>> find() {
        ComponentSupplier componentSupplier = ComponentContainer.getInstance();
        PathHelper pathHelper = componentSupplier.getPathHelper();
        ClassHunter classHunter = componentSupplier.getClassHunter();

        SearchConfig searchConfig = SearchConfig.forPaths(
            //Here you can add all absolute path you want:
            //both folders, zip and jar will be recursively scanned.
            //For example you can add: "C:\\Users\\user\\.m2"
            //With the row below the search will be executed on runtime Classpaths
            pathHelper.getMainClassPaths()
        ).by(
            ClassCriteria.create().byClassesThatMatch((uploadedClasses, currentScannedClass) ->
                //[1]here you recall the uploaded class by "useClasses" method.
                //In this case we're looking for all classes that implement java.io.Closeable or java.io.Serializable
                uploadedClasses.get(Closeable.class).isAssignableFrom(currentScannedClass) ||
                uploadedClasses.get(Serializable.class).isAssignableFrom(currentScannedClass)
            ).useClasses(
                //With this directive we ask the library to load one or more classes to be used for comparisons:
                //it serves to eliminate the problem that a class, loaded by different class loaders,
                //turns out to be different for the comparison operators (eg. The isAssignableFrom method).
                //If you call this method, you must retrieve the uploaded class in all methods that support this feature like in the point[1]
                Closeable.class,
                Serializable.class
            )
        );

        SearchResult searchResult = classHunter.findBy(searchConfig);

        return searchResult.getClasses();
    }

}
