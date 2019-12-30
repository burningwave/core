**Burningwave core** is an advanced, free and open source Java library that contains **THE MOST POWERFUL CLASSPATH SCANNER** for criteria based classes search.
It’s possible to search classes by every criteria that your immagination can made by using lambda expressions. **Scan engine is highly optimized using direct allocated ByteBuffers to avoid heap saturation; searches are executed in multithreading context and are not affected by “_the issue of the same class loaded by different classloaders_”** (normally if you try to execute "isAssignableFrom" method on a same class loaded from different classloader it returns false).

**This library is useful also for creating classes during runtime, facilitate the use of reflection and much more...**

Below you will find how to include BurningWave core in you're projects and a simple code example and in the [wiki](https://github.com/burningwave/bw-core/wiki) you will find more detailed examples.

## Get started

**To include Burningwave core library in your projects simply use with**:

* **Apache Maven**:
```xml
<dependency>
    <groupId>com.github.burningwave</groupId>
    <artifactId>bw-core</artifactId>
    <version>1.7.5</version>
</dependency>
```

* **Gradle Groovy**:
```
implementation 'com.github.burningwave:bw-core:1.7.5'
```

* **Gradle Kotlin**:
```
implementation("com.github.burningwave:bw-core:1.7.5")
```

* **Scala**:
```
libraryDependencies += "com.github.burningwave" % "bw-core" % "1.7.5"
```

* **Apache Ivy**:
```
<dependency org="com.github.burningwave" name="bw-core" rev="1.7.5" />
```

* **Groovy Grape**:
```
@Grapes(
  @Grab(group='com.github.burningwave', module='bw-core', version='1.7.5')
)
```

* **Leiningen**:
```
[com.github.burningwave/bw-core "1.7.5"]
```

* **Apache Buildr**:
```
'com.github.burningwave:bw-core:jar:1.7.5'
```

* **PURL**:
```
pkg:maven/com.github.burningwave/bw-core@1.7.5
```

## ... And now the code: let's retrieve all classes of the runtime classpath!
```java
import java.util.Collection;

import com.github.burningwave.core.assembler.ComponentContainer;
import com.github.burningwave.core.classes.hunter.ClassHunter;
import com.github.burningwave.core.classes.hunter.ClassHunter.SearchResult;
import com.github.burningwave.core.classes.hunter.SearchCriteria;
import com.github.burningwave.core.classes.hunter.SearchForPathCriteria;
import com.github.burningwave.core.io.PathHelper;

public class Finder {

    public Collection<Class<?>> find() {
        ComponentContainer componentConatiner = ComponentContainer.getInstance();
        PathHelper pathHelper = componentConatiner.getPathHelper();
        ClassHunter classHunter = componentConatiner.getClassHunter();

        SearchForPathCriteria criteria = SearchCriteria.forPaths(
            //Here you can add all absolute path you want:
            //both folders, zip and jar will be scanned recursively.
            //For example you can add: "C:\\Users\\.m2"
            //With the row below the search will be executed on runtime Classpaths
            pathHelper.getMainClassPaths()
	);

        SearchResult searchResult = classHunter.findBy(criteria);
        return searchResult.getItemsFound();
    }

}
```
