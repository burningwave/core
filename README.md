[![logo](Burningwave-logo.jpg "Burningwave")](https://www.burningwave.org/)

**Burningwave Core** is a fully indipendent, advanced, free and open source Java frameworks building library that contains **THE MOST POWERFUL CLASSPATH SCANNER** for criteria based classes search.
It’s possible to search classes by every criteria that your immagination can made by using lambda expressions. **Scan engine is highly optimized using direct allocated ByteBuffers to avoid heap saturation; searches are executed in multithreading context and are not affected by “_the issue of the same class loaded by different classloaders_”** (normally if you try to execute "isAssignableFrom" method on a same class loaded from different classloader it returns false).

**Tested on Java versions ranging from 8 to 13, Burningwave Core is also useful for creating classes during runtime, facilitate the use of reflection and much more...**

Below you will find how to include the library in your projects and a simple code example and in the [wiki](https://github.com/burningwave/core/wiki) you will find more detailed examples.

## Get started

**To include Burningwave Core library in your projects simply use with**:

* **Apache Maven**:
```xml
<dependency>
    <groupId>org.burningwave</groupId>
    <artifactId>core</artifactId>
    <version>5.2.0</version>
</dependency>
```

<details>
	<summary><b>... And with (click to expand)</b>:</summary>
	<br/>
	<ul><li><b>Gradle Groovy</b>:</li></ul>
	<pre>implementation 'org.burningwave:core:5.2.0'</pre>
	<br/>
	<ul><li><b>Gradle Kotlin</b>:</li></ul>
	<pre>implementation("org.burningwave:core:5.2.0")</pre>
	<br/>
	<ul><li><b>Scala</b>:</li></ul>
	<pre>libraryDependencies += "org.burningwave" % "core" % "5.2.0"</pre>
	<br/>
	<ul><li><b>Apache Ivy</b>:</li></ul>
	<pre>&lt;dependency org="org.burningwave" name="core" rev="5.2.0" /&gt;</pre>
	<br/>
	<ul><li><b>Groovy Grape</b>:</li></ul>
	<pre>
		@Grapes(
  			@Grab(group='org.burningwave', module='core', version='5.2.0')
		)
	</pre>
	<br/>
	<ul><li><b>Leiningen</b>:</li></ul>
	<pre>[org.burningwave/core "5.2.0"]</pre>
	<br/>
	<ul><li><b>Apache Buildr</b>:</li></ul>
	<pre>'org.burningwave:core:jar:5.2.0'</pre>
	<br/>
	<ul><li><b>PURL</b>:</li></ul>
	<pre>pkg:maven/org.burningwave/core@5.2.0</pre>
</details>
<br/>

## ... And now the code: let's retrieve all classes of the runtime classpath!
```java
import java.util.Collection;

import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.CacheableSearchConfig;
import org.burningwave.core.classes.ClassHunter;
import org.burningwave.core.classes.ClassHunter.SearchResult;
import org.burningwave.core.classes.SearchConfig;
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
		);

		SearchResult searchResult = classHunter.findBy(searchConfig);
		return searchResult.getClasses();
	}

}
```
