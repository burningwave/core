package org.burningwave.core.examples.usecase001;

import java.io.Closeable;
import java.io.Serializable;
import java.util.Collection;

import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.CacheableSearchConfig;
import org.burningwave.core.classes.ClassCriteria;
import org.burningwave.core.classes.ClassHunter;
import org.burningwave.core.classes.SearchConfig;
import org.burningwave.core.classes.ClassHunter.SearchResult;
import org.burningwave.core.io.PathHelper;

public class Finder {

    public Collection<Class<?>> find() {
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

        //The loadInCache method loads all classes in the paths of the SearchConfig received as input
        //and then execute the queries of the ClassCriteria on the cached data. Once the data has been 
        //cached, it is possible to take advantage of faster searches for the loaded paths also through 
        //the findBy method. In addition to the loadCache method, loading data into the cache can also
        //take place via the findBy method if the latter receives a SearchConfig without ClassCriteria
        //as input. It is possible to clear the cache individually for every hunter (ClassHunter, 
        //ByteCodeHunter and ClassPathHunter) with clearCache method but to avoid inconsistencies 
        //it is recommended to perform this cleaning using the clearHuntersCache method of the ComponentSupplier.
        //To perform searches that do not use the cache you must intantiate the search configuration with 
        //SearchConfig.withoutUsingCache() method
        SearchResult searchResult = classHunter.findBy(searchConfig);
        
        return searchResult.getClasses();
    }

}
