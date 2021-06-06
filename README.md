# Burningwave Core [![Tweet](https://img.shields.io/twitter/url/http/shields.io.svg?style=social)](https://twitter.com/intent/tweet?text=%40Burningwave_fw%20Core%2C%20the%20%23Java%20frameworks%20building%20library%20%28works%20on%20%23Java8%20%23Java9%20%23Java10%20%23Java11%20%23Java12%20%23Java13%20%23Java14%20%23Java15%20%23Java16%29&url=https://github.com/burningwave/core%23burningwave-core-)

<a href="https://www.burningwave.org">
<img src="https://raw.githubusercontent.com/burningwave/core/master/Burningwave-logo.png" alt="Burningwave-logo.png" height="180px" align="right"/>
</a>

[![Maven Central with version prefix filter](https://img.shields.io/maven-central/v/org.burningwave/core/8)](https://maven-badges.herokuapp.com/maven-central/org.burningwave/core/)
[![GitHub](https://img.shields.io/github/license/burningwave/core)](https://github.com/burningwave/core/blob/master/LICENSE)

[![Platforms](https://img.shields.io/badge/platforms-Windows%2C%20Max%20OS%2C%20Linux-orange)](https://github.com/burningwave/core/actions/runs/809066503)

[![Supported JVM](https://img.shields.io/badge/supported%20JVM-8%2C%209%2C%2010%2C%2011%2C%2012%2C%2013%2C%2014%2C%2015%2C%2016-blueviolet)](https://github.com/burningwave/core/actions/runs/809066503)

[![Coveralls github branch](https://img.shields.io/coveralls/github/burningwave/core/master)](https://coveralls.io/github/burningwave/core?branch=master)
[![GitHub open issues](https://img.shields.io/github/issues/burningwave/core)](https://github.com/burningwave/core/issues)
[![GitHub closed issues](https://img.shields.io/github/issues-closed/burningwave/core)](https://github.com/burningwave/core/issues?q=is%3Aissue+is%3Aclosed)

[![ArtifactDownload](https://www.burningwave.org/generators/generate-burningwave-artifact-downloads-badge.php?type=svg&artifactId=core)](https://www.burningwave.org/artifact-downloads/?show-monthly-trend-chart=false)

[![Gitter](https://badges.gitter.im/burningwave/core.svg)](https://gitter.im/burningwave/core?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge)

**Tested on Java versions ranging from 8 to 16, Burningwave Core** is a fully independent, advanced, free and open source Java frameworks building library and it is useful for scanning class paths, generating classes at runtime, facilitating the use of reflection, scanning the filesystem, executing stringified source code and much more...

Burningwave Core contains **THE MOST POWERFUL CLASSPATH SCANNER**: it’s possible to search classes by every criteria that your imagination can make by using lambda expressions; **scan engine is highly optimized using direct allocated ByteBuffers to avoid heap saturation; searches are executed in multithreading context and are not affected by “_the issue of the same class loaded by different classloaders_”** (normally if you try to execute "isAssignableFrom" method on a same class loaded from different classloader it returns false).

And now we will see:
* [**including Burningwave Core in your project**](#Including-Burningwave-Core-in-your-project)
* [**generating classes at runtime and invoking their methods with and without the use of reflection**](#Generating-classes-at-runtime-and-invoking-their-methods-with-and-without-the-use-of-reflection)
* [**executing stringified source code**](#Executing-stringified-source-code)
* [**retrieving classes of runtime class paths or of other paths through the ClassHunter**](#Retrieving-classes-of-runtime-class-paths-or-of-other-paths-through-the-ClassHunter)
* [**finding where a class is loaded from**](#Finding-where-a-class-is-loaded-from)
* [**performing tasks in parallel with different priorities**](#Performing-tasks-in-parallel-with-different-priorities)
* [**reaching a resource of the file system**](#Reaching-a-resource-of-the-file-system)
* [**resolving, collecting or retrieving paths**](#Resolving-collecting-or-retrieving-paths)
* [**retrieving placeholdered items from map and properties file**](#Retrieving-placeholdered-items-from-map-and-properties-file)
* [**handling privates and all other members of an object**](#Handling-privates-and-all-other-members-of-an-object)
* [**getting and setting properties of a Java bean through path**](#Getting-and-setting-properties-of-a-Java-bean-through-path)
* [**architectural overview and configuration**](#Architectural-overview-and-configuration)
* [**other examples of using some components**](#Other-examples-of-using-some-components)

<br/>

**For assistance you can [subscribe](https://www.burningwave.org/registration/) to the [forum](https://www.burningwave.org/forum/) and then ask in the topic ["How to do?"](https://www.burningwave.org/forum/forum/how-to/) or you can ask on [Stack Overflow](https://stackoverflow.com/search?q=burningwave)**.

<br/>

# Including Burningwave Core in your project 
To include Burningwave Core library in your projects simply use with **Apache Maven**:

```xml
<dependency>
    <groupId>org.burningwave</groupId>
    <artifactId>core</artifactId>
    <version>8.21.2</version>
</dependency>
```

<br/>

# Generating classes at runtime and invoking their methods with and without the use of reflection

For this purpose is necessary the use of **ClassFactory** component and of the **sources generating components**. Once the sources have been set in **UnitSourceGenerator** objects, they must be passed to **`loadOrBuildAndDefine`** method of ClassFactory with the ClassLoader where you want to define new generated classes. This method performs the following operations: tries to load all the classes present in the UnitSourceGenerator through the class loader, if at least one of these is not found it proceeds to compiling all the UnitSourceGenerators and uploading their classes on class loader: **in this case, keep in mind that if a class with the same name was previously loaded by the class loader, the compiled class will not be uploaded**. **If you need more information you can**:
* see a [**complete example about source code generators**](https://github.com/burningwave/core/blob/master/src/test/java/org/burningwave/core/UnitSourceGeneratorTest.java#L153)
* read this [**guide**](https://www.burningwave.org/forum/topic/how-can-i-use-classes-outside-the-runtime-class-path-in-my-generated-sources/) where you also can find a link to an [**example about generating classes by using libraries located outside the runtime class paths**](https://github.com/burningwave/core/blob/master/src/test/java/org/burningwave/core/examples/classfactory/ExternalClassRuntimeExtender.java)
* go [**here**](https://github.com/burningwave/core/tree/master/src/test/java/org/burningwave/core/examples/classfactory) for more examples
* ask for assistance at the [**official forum**](https://www.burningwave.org/forum/) (topic [**"How to do?"**](https://www.burningwave.org/forum/forum/how-to/))
* [**ask Stack Overflow for assistance**](https://stackoverflow.com/search?q=burningwave)

Once the classes have been compiled and loaded, it is possible to invoke their methods in severals ways as shown at the end of the example below.

```java
package org.burningwave.core.examples.classfactory;

import static org.burningwave.core.assembler.StaticComponentContainer.Constructors;

import java.lang.reflect.Modifier;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import org.burningwave.core.Virtual;
import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.AnnotationSourceGenerator;
import org.burningwave.core.classes.ClassFactory;
import org.burningwave.core.classes.ClassSourceGenerator;
import org.burningwave.core.classes.FunctionSourceGenerator;
import org.burningwave.core.classes.GenericSourceGenerator;
import org.burningwave.core.classes.TypeDeclarationSourceGenerator;
import org.burningwave.core.classes.UnitSourceGenerator;
import org.burningwave.core.classes.VariableSourceGenerator;

public class RuntimeClassExtender {

    @SuppressWarnings("resource")
    public static void execute() throws Throwable {
        UnitSourceGenerator unitSG = UnitSourceGenerator.create("packagename").addClass(
            ClassSourceGenerator.create(
                TypeDeclarationSourceGenerator.create("MyExtendedClass")
            ).addModifier(
                Modifier.PUBLIC
            //generating new method that override MyInterface.convert(LocalDateTime)
            ).addMethod(
                FunctionSourceGenerator.create("convert")
                .setReturnType(
                    TypeDeclarationSourceGenerator.create(Comparable.class)
                    .addGeneric(GenericSourceGenerator.create(Date.class))
                ).addParameter(VariableSourceGenerator.create(LocalDateTime.class, "localDateTime"))
                .addModifier(Modifier.PUBLIC)
                .addAnnotation(AnnotationSourceGenerator.create(Override.class))
                .addBodyCodeLine("return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());")
                .useType(ZoneId.class)
            ).addConcretizedType(
                MyInterface.class
            ).expands(ToBeExtended.class)
        );
        System.out.println("\nGenerated code:\n" + unitSG.make());
        //With this we store the generated source to a path
        unitSG.storeToClassPath(System.getProperty("user.home") + "/Desktop/bw-tests");
        ComponentSupplier componentSupplier = ComponentContainer.getInstance();
        ClassFactory classFactory = componentSupplier.getClassFactory();
        //this method compile all compilation units and upload the generated classes to default
        //class loader declared with property "class-factory.default-class-loader" in 
        //burningwave.properties file (see "Overview and configuration").
        //If you need to upload the class to another class loader use
        //loadOrBuildAndDefine(LoadOrBuildAndDefineConfig) method
        ClassFactory.ClassRetriever classRetriever = classFactory.loadOrBuildAndDefine(
            unitSG
        );
        Class<?> generatedClass = classRetriever.get(
            "packagename.MyExtendedClass"
        );
        ToBeExtended generatedClassObject =
            Constructors.newInstanceOf(generatedClass);
        generatedClassObject.printSomeThing();
        System.out.println(
            ((MyInterface)generatedClassObject).convert(LocalDateTime.now()).toString()
        );
        //You can also invoke methods by casting to Virtual (an interface offered by the
        //library for faciliate use of runtime generated classes)
        Virtual virtualObject = (Virtual)generatedClassObject;
        //Invoke by using reflection
        virtualObject.invoke("printSomeThing");
        //Invoke by using MethodHandle
        virtualObject.invokeDirect("printSomeThing");
        System.out.println(
            ((Date)virtualObject.invokeDirect("convert", LocalDateTime.now())).toString()
        );
        classRetriever.close();
    }   

    public static class ToBeExtended {

        public void printSomeThing() {
            System.out.println("Called method printSomeThing");
        }

    }

    public static interface MyInterface {

        public Comparable<Date> convert(LocalDateTime localDateTime);

    }

    public static void main(String[] args) throws Throwable {
        execute();
    }
}
```

<br/>

# Executing stringified source code
It is possible to execute stringified source code by using the **CodeExecutor** in three three different ways:
* [through **BodySourceGenerator**](#Executing-code-with-BodySourceGenerator)
* [through a property located in Burningwave configuration file](#Executing-code-of-a-property-located-in-Burningwave-configuration-file)
* [through a property located in a custom Properties file](#Executing-code-of-a-property-located-in-a-custom-properties-file)

<br/>

## Executing code with BodySourceGenerator
For first way we must create a **ExecuteConfig** by using the within static method **`forBodySourceGenerator`** to which must be passed the **BodySourceGenerator** that contains the source code with the parameters used within: after that we must pass the created configuration to the **`execute`** method of CodeExecutor as shown below:
```java
package org.burningwave.core.examples.codeexecutor;

import java.util.ArrayList;
import java.util.List;

import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.ExecuteConfig;
import org.burningwave.core.classes.BodySourceGenerator;

public class SourceCodeExecutor {
    
    public static Integer execute() {
        ComponentSupplier componentSupplier = ComponentContainer.getInstance();
        return componentSupplier.getCodeExecutor().execute(
            ExecuteConfig.forBodySourceGenerator(
                BodySourceGenerator.createSimple().useType(ArrayList.class, List.class)
                .addCodeLine("System.out.println(\"number to add: \" + parameter[0]);")
                .addCodeLine("List<Integer> numbers = new ArrayList<>();")
                .addCodeLine("numbers.add((Integer)parameter[0]);")
                .addCodeLine("System.out.println(\"number list size: \" + numbers.size());")
                .addCodeLine("System.out.println(\"number in the list: \" + numbers.get(0));")
                .addCodeLine("Integer inputNumber = (Integer)parameter[0];")
                .addCodeLine("return (T)new Integer(inputNumber + (Integer)parameter[1]);")
            ).withParameter(Integer.valueOf(5), Integer.valueOf(3))
        );
        
    }
    
    public static void main(String[] args) {
        System.out.println("Total is: " + execute());
    }
}
```

<br/>

## Executing code of a property located in Burningwave configuration file
To execute code from Burningwave configuration file ([**burningwave.properties**](#configuration-1) or other file that we have used to create the ComponentContainer: [**see architectural overview and configuration**](#Architectural-overview-and-configuration)) we must add to it a  property that contains the code and, if it is necessary to import classes, we must add them to another property named as the property that contains the code plus the suffix **'imports'**. E.g:
```properties
code-block-1=\
    Date now= new Date();\
    return (T)now;
code-block-1.imports=java.util.Date;
```
It is also possible to include the code of a property in another property:
```properties
code-block-1=\
    ${code-block-2}\
    return (T)Date.from(zonedDateTime.toInstant());
code-block-1.imports=\
    ${code-block-2.imports}\
    java.util.Date;
code-block-2=\
    LocalDateTime localDateTime = (LocalDateTime)parameter[0];\
    ZonedDateTime zonedDateTime = localDateTime.atZone(ZoneId.systemDefault());
code-block-2.imports=\
    static org.burningwave.core.assembler.StaticComponentContainer.Strings;\
    java.time.LocalDateTime;\
    java.time.ZonedDateTime;\
    java.time.ZoneId;
```
After that, for executing the code of the property we must call the **executeProperty** method of CodeExecutor and passing to it the property name to be executed and the parameters used in the property code:
```java
package org.burningwave.core.examples.codeexecutor;

import java.time.LocalDateTime;

import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.assembler.ComponentSupplier;

public class SourceCodeExecutor {
    
    public static void execute() {
        ComponentSupplier componentSupplier = ComponentContainer.getInstance();
        System.out.println("Time is: " +
            componentSupplier.getCodeExecutor().executeProperty("code-block-1", LocalDateTime.now())    
        );
    }
    
    public static void main(String[] args) {
        execute();
    }
}
```

<br/>

## Executing code of a property located in a custom properties file
To execute code from a custom properties file we must add to it a  property that contains the code and, if it is necessary to import classes, we must add them to another property named as the property that contains the code plus the suffix **'imports'**. E.g:
```properties
code-block-1=\
    Date now= new Date();\
    return (T)now;
code-block-1.imports=java.util.Date;
```
It is also possible to include the code of a property in another property:
```properties
code-block-1=\
    ${code-block-2}\
    return (T)Date.from(zonedDateTime.toInstant());
code-block-1.imports=\
    ${code-block-2.imports}\
    java.util.Date;
code-block-2=\
    LocalDateTime localDateTime = (LocalDateTime)parameter[0];\
    ZonedDateTime zonedDateTime = localDateTime.atZone(ZoneId.systemDefault());
code-block-2.imports=\
    static org.burningwave.core.assembler.StaticComponentContainer.Strings;\
    java.time.LocalDateTime;\
    java.time.ZonedDateTime;\
    java.time.ZoneId;
```
After that, for executing the code of the property we must create an **ExecuteConfig** object and set on it:
* the path (relative or absolute) of our custom properties file 
* the property name to be executed 
* the parameters used in the property code

Then we must call the **execute** method of CodeExecutor with the created ExecuteConfig object:
```java
package org.burningwave.core.examples.codeexecutor;

import java.time.LocalDateTime;

import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.ExecuteConfig;

public class SourceCodeExecutor {
    
    public static void execute() {
        ComponentSupplier componentSupplier = ComponentContainer.getInstance();
        System.out.println("Time is: " +
            componentSupplier.getCodeExecutor().execute(
                ExecuteConfig.forPropertiesFile("custom-folder/code.properties")
                //Uncomment the line below if the path you have supplied is an absolute path
                //.setFilePathAsAbsolute(true)
                .setPropertyName("code-block-1")
                .withParameter(LocalDateTime.now())
            )    
        );
    }
    
    public static void main(String[] args) {
        execute();
    }
}
```

<br/>

# Retrieving classes of runtime class paths or of other paths through the ClassHunter
The components of the class paths scanning engine are: **ByteCodeHunter**, [**ClassHunter**](https://github.com/burningwave/core/wiki/In-depth-look-to-ClassHunter-and-configuration-guide) and the **ClassPathHunter**. Now we are going to use the ClassHunter to search for all classes that have package name that matches a regex. So in this example we're looking for all classes whose package name contains "springframework" string in the runtime class paths:

```java
import java.util.Collection;

import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.ClassCriteria;
import org.burningwave.core.classes.ClassHunter;
import org.burningwave.core.classes.ClassHunter.SearchResult;
import org.burningwave.core.classes.SearchConfig;
    
public class Finder {
    
    public Collection<Class<?>> simplifiedFind() {
        ComponentSupplier componentSupplier = ComponentContainer.getInstance();
        ClassHunter classHunter = componentSupplier.getClassHunter();
        
        //With this the search will be executed on default configured paths that are the 
        //runtime class paths plus, on java 9 and later, the jmods folder of the Java home.
        //The default configured paths are indicated in the 'paths.hunters.default-search-config.paths'
        //property of burningwave.properties file
        //(see https://github.com/burningwave/core/wiki/In-depth-look-to-ClassHunter-and-configuration-guide)
        try (SearchResult searchResult = classHunter.loadInCache(SearchConfig.byCriteria(
            ClassCriteria.create().allThoseThatMatch((cls) -> {
                return cls.getPackage().getName().matches(".*springframework.*");
            })
        )).find()
        ) {
            return searchResult.getClasses();
        }
    }
    
}
```

It is also possible to expressly indicate the paths on which to search:

```java
import java.util.Collection;

import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.CacheableSearchConfig;
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
        
        CacheableSearchConfig searchConfig = SearchConfig.forPaths(
            //Here you can add all absolute path you want:
            //both folders, zip, jar, ear and war will be recursively scanned.
            //For example you can add: "C:\\Users\\user\\.m2", or a path of
            //an ear file that contains nested war with nested jar files
            //With the rows below the search will be executed on runtime class paths and
            //on java 9 and later also on .jmod files contained in jmods folder of the Java home
            //(see https://github.com/burningwave/core/wiki/In-depth-look-to-ClassHunter-and-configuration-guide)
            pathHelper.getAllMainClassPaths(),
            pathHelper.getPaths(PathHelper.Configuration.Key.MAIN_CLASS_REPOSITORIES)
            //If you want to scan only one jar you can replace the two line of code above with:
            //pathHelper.getPaths(path -> path.contains("spring-core-4.3.4.RELEASE.jar"))
        ).by(
            ClassCriteria.create().allThoseThatMatch((cls) -> {
                return cls.getPackage().getName().matches(".*springframework.*");
            })
        );
        //The loadInCache method loads all classes in the paths of the SearchConfig received as input
        //and then execute the queries of the ClassCriteria on the cached data. Once the data has been 
        //cached, it is possible to take advantage of faster searches for the loaded paths also through 
        //the findBy method. In addition to the loadCache method, loading data into the cache can also
        //take place via the findBy method if the latter receives a SearchConfig without ClassCriteria
        //as input. It is possible to clear the cache individually for every hunter (ClassHunter, 
        //ByteCodeHunter and ClassPathHunter) with clearCache method but to avoid inconsistencies 
        //it is recommended to perform this cleaning using the clearHuntersCache method of the ComponentSupplier.
        //To perform searches that do not use the cache you must instantiate the search configuration with 
        //SearchConfig.withoutUsingCache() method
        try(SearchResult searchResult = classHunter.loadInCache(searchConfig).find()) {
            return searchResult.getClasses();
        }
    }
    
}
```

<br/>

# Finding where a class is loaded from

For this purpose we are going to use the **ClassPathHunter** component:
```java
import java.util.Collection;

import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.ClassCriteria;
import org.burningwave.core.classes.CacheableSearchConfig;
import org.burningwave.core.classes.ClassPathHunter;
import org.burningwave.core.classes.ClassPathHunter.SearchResult;
import org.burningwave.core.classes.SearchConfig;
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

        try (SearchResult searchResult = classPathHunter.loadInCache(searchConfig).find()) {
            return searchResult.getClassPaths();
        }
    }

}
```

<br>

# Performing tasks in parallel with different priorities
By using the **BackgroundExecutor** component you can launch different Runnables or Suppliers in a parallel way and wait for them starting or finishing. For obtaining threads the BackgroundExecutor uses the **ThreadSupplier** component which can be customized in the [burningwave.static.properties](#configuration) file. The ThreadSupplier provides a fixed number of reusable threads indicated by the **`thread-supplier.max-poolable-threads-count`** property and, if these threads have already been assigned, new non-reusable threads will be created whose quantity maximum is indicated by the **`thread-supplier.max-detached-threads-count`** property. Once this limit is reached if the request for a new thread exceeds the waiting time indicated by the **`thread-supplier.poolable-thread-request-timeout`** property, the ThreadSupplier will proceed to increase the limit indicated by the 'thread-supplier.max-detached-threads-count' property for the quantity indicated by the `thread-supplier.max-detached-threads-count.increasing-step` property. Resetting the 'thread-supplier.max-detached-threads-count' property to its initial value, will occur gradually only when there have been no more waits on thread requests for an amount of time indicated by the **`thread-supplier.max-detached-threads-count.elapsed-time-threshold-from-last-increase-for-gradual-decreasing-to-initial-value`** property.
```java
import static org.burningwave.core.assembler.StaticComponentContainer.BackgroundExecutor;

import org.burningwave.core.ManagedLogger;
import org.burningwave.core.concurrent.QueuedTasksExecutor.ProducerTask;
import org.burningwave.core.concurrent.QueuedTasksExecutor.Task;


public class TaskLauncher implements ManagedLogger {
    
    public void launch() {
        
        ProducerTask<Long> taskOne = BackgroundExecutor.createTask(() -> {
            Long startTime = System.currentTimeMillis();
            logInfo("task one started");
            synchronized (this) {                
                wait(5000);
            }
            Task internalTask = BackgroundExecutor.createTask(() -> {
                logInfo("internal task started");    
                synchronized (this) {                
                    wait(5000);
                }
                logInfo("internal task finished");    
            }, Thread.MAX_PRIORITY).submit();
            internalTask.waitForFinish();
            logInfo("task one finished");
            return startTime;
        }, Thread.MAX_PRIORITY).submit();

        Task taskTwo = BackgroundExecutor.createTask(() -> {
            logInfo("task two started and wait for task one finishing");
            taskOne.waitForFinish();
            logInfo("task two finished");    
        }, Thread.NORM_PRIORITY).submit();

        ProducerTask<Long> taskThree = BackgroundExecutor.createTask(() -> {
            logInfo("task three started and wait for task two finishing");
            taskTwo.waitForFinish();
            logInfo("task three finished");
            return System.currentTimeMillis();
        }, Thread.MIN_PRIORITY).submit();

        taskThree.waitForFinish();

        logInfo("Elapsed time: {}ms", taskThree.join() - taskOne.join());
    }
    
    public static void main(String[] args) {
        new TaskLauncher().launch();
    }
    
}
```

<br/>

# Reaching a resource of the file system
Through **FileSystemItem** you can reach a resource of the file system even if it is contained in a nested supported (**zip, jar, war, ear, jmod**) compressed archive and obtain the content of it or other informations such as if it is a folder or a file or a compressed archive or if it is a compressed entry or obtain, if it is a folder or a compressed archive, the direct children or all nested children or a filtered collection of them. You can retrieve a FileSystemItem through an absolute path or through a relative path referred to your classpath by using the PathHelper. FileSystemItems are cached and **there will only be one instance of them for an absolute path** and you can also clear the cache e reload all informations of a FileSystemItem. In the example below we show how to retrieve and use a FileSystemItem.

```java
package org.burningwave.core.examples.filesystemitem;

import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.io.FileSystemItem;

public class ResourceReacher {
    
    private static void execute() {
        //Obtaining FileSystemItem through absolute path
        FileSystemItem fSI = FileSystemItem.ofPath("C:/Program Files (x86)");
       
        
        FileSystemItem firstFolderFound = null;
        
        //Obtaining direct children
        for (FileSystemItem child : fSI.getChildren()) {
            System.out.println("child name:" + child.getAbsolutePath());
            if (firstFolderFound == null && child.isFolder()) {
                 System.out.println(child.getAbsolutePath() + " is a folder: " + child.isFolder());
                 firstFolderFound = child;
            }
        }
        
        //Filtering all nested children for extension
        for (FileSystemItem child : firstFolderFound.findInAllChildren(
            FileSystemItem.Criteria.forAllFileThat(fSIC -> 
                "txt".equals(fSIC.getExtension()) || "exe".equals(fSIC.getExtension()))
            )
        ){
            System.out.println("child name: " + child.getName() + " - child parent: " + child.getParent().getName());
            //copy the file to a folder
            child.copyTo(System.getProperty("user.home") + "/Desktop/copy");
        }
        
        //Obtaining a FileSystemItem through a relative path (in this case we are obtaining a reference to a jar
        //contained in an ear that is contained in a zip
        fSI = ComponentContainer.getInstance().getPathHelper().getResource(
            "/../../src/test/external-resources/libs-for-test.zip/ESC-Lib.ear/APP-INF/lib/jaxb-xjc-2.1.7.jar"
        );
        
        System.out.println("is an archive:" + fSI.isArchive());
        
        //This method return true if the file or folder is located inside a compressed archive
        System.out.println("is compressed:" + fSI.isCompressed());
        
        //this clear cache
        fSI.refresh(true);
        
        //Obtaining direct children
        for (FileSystemItem child : fSI.getChildren()) {
            System.out.println("child name:" + child.getAbsolutePath());
        }
        
        //Obtaining all nested children
        for (FileSystemItem child : fSI.getAllChildren()) {
            System.out.println("child name:" + child.getAbsolutePath());
        }
        
        //Obtaining the content of the resource (once the content is loaded it will be cached)
        fSI.toByteBuffer();
    }
    
    public static void main(String[] args) {
        execute();
    }
    
}
```

<br/>

# Resolving, collecting or retrieving paths

Through **PathHelper** we can resolve or collect paths or retrieving resources even through supported archive files (zip, jar, jmod, ear and war).
So we can create a path collection by adding an entry in **[burningwave.properties](#configuration-1)** file that **starts with `paths.` prefix (this is a fundamental requirement to allow PathHelper to load the paths)**, e.g.:
```properties
paths.my-collection=c:/some folder;C:/some folder 2/ some folder 3;
paths.my-collection-2=c:/some folder 4;C:/some folder 6;
```
These paths could be retrieved through **`PathHelper.getPaths`** method and we can find a resource in all configured paths plus the runtime class paths (that is automatically loaded under the entry named **`paths.main-class-paths`**) by using **PathHelper.getResource** method, e.g.:
```java
ComponentSupplier componentSupplier = ComponentContainer.getInstance();
PathHelper pathHelper = componentSupplier.getPathHelper();
Collection<String> paths = pathHelper.getPaths("paths.my-collection", "paths.my-collection-2"));
//With the code below all configured paths plus runtime class paths will be iterated to search
//the resource called some.jar
FileSystemItem resource = pathHelper.getResource("/../some.jar");
InputStream inputStream = resource.toInputStream();
```
We can also use placeholder and relative paths, e.g.:
```properties
paths.my-collection-3=C:/some folder 2/ some folder 3;
paths.my-jar=${paths.my-collection-3}/../some.jar;
```
It is also possibile to obtain references to resources of the runtime class paths by using the pre-loaded entry 'paths.main-class-paths' (runtime class paths are automatically iterated for searching the path that match the entry), e.g.:
```properties
paths.my-jar=${paths.main-class-paths}/../some.jar;
```
We can also use a [**FileSystemItem**](#Reaching-a-resource-of-the-file-system) listing (**FSIL**) expression and, for example, create a path collection of all absolute path of all classes of the runtime class paths:
```properties
paths.all-runtime-classes=//${paths.main-class-paths}//allChildren:.*?\.classes;
```
A **FSIL** expression encloses in a couple of double slash an absolute path or a placeholdered path collection that will be scanned; after the second double slash we have the listing type that could refear to direct children of scanned paths ('**children**') or to all nested children of scanned paths ('**allChildren**'); after that and colons we have the regular expression with we are going to filter the absolute paths iterated.

<br/>

# Retrieving placeholdered items from map and properties file

With **IterableObjectHelper** component it is possible to retrieve items from map by using placeholder or not. In the following example we are going to show how to retrieve strings or objects from **[burningwave.properties](#configuration-1)** file and from maps.

**[burningwave.properties](#configuration-1)** file:
```properties
...
code-block-1=\
    ${code-block-2}\
    return (T)Date.from(zonedDateTime.toInstant());
code-block-2=\
    LocalDateTime localDateTime = (LocalDateTime)parameter[0];\
    ZonedDateTime zonedDateTime = localDateTime.atZone(ZoneId.systemDefault());
...
```
**Java code**:
```java
package org.burningwave.core.examples.iterableobjecthelper;

import static org.burningwave.core.assembler.StaticComponentContainer.IterableObjectHelper;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.io.PathHelper;

public class ItemFromMapRetriever {
    
    public void execute() throws IOException {
        ComponentSupplier componentSupplier = ComponentContainer.getInstance();
        PathHelper pathHelper = componentSupplier.getPathHelper();
        Properties properties = new Properties();
        properties.load(pathHelper.getResourceAsStream("burningwave.properties"));
        String code = IterableObjectHelper.resolveStringValue(properties, "code-block-1");        
        
        Map<Object, Object> map = new HashMap<>();
        map.put("class-loader-01", "${class-loader-02}");
        map.put("class-loader-02", "${class-loader-03}");
        map.put("class-loader-03", Thread.currentThread().getContextClassLoader().getParent());
        ClassLoader parentClassLoader = IterableObjectHelper.resolveValue(map, "class-loader-01");
        
        map.clear();
        map.put("class-loaders", "${class-loader-02};${class-loader-03};");
        map.put("class-loader-02", Thread.currentThread().getContextClassLoader());
        map.put("class-loader-03", Thread.currentThread().getContextClassLoader().getParent());
        Collection<ClassLoader> classLoaders = IterableObjectHelper.resolveValues(map, "class-loaders", ";");
    }
    
    public static void main(String[] args) throws IOException {
        new ItemFromMapRetriever().execute();
    }
}
```
<br>

# Handling privates and all other members of an object
Through **Fields**, **Constructors** and **Methods** components it is possible to get or set fields value, invoking or finding constructors or methods of an object.
Members handlers use to cache all members for faster access.
For fields handling we are going to use **Fields** component:
```java
import static org.burningwave.core.assembler.StaticComponentContainer.Fields;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.burningwave.core.classes.FieldCriteria;


@SuppressWarnings("unused")
public class FieldsHandler {
    
    public static void execute() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        //Fast access by memory address
        Collection<Class<?>> loadedClasses = Fields.getDirect(classLoader, "classes");
        //Access by Reflection
        loadedClasses = Fields.get(classLoader, "classes");
        
        //Get all field values of an object through memory address access
        Map<Field, ?> values = Fields.getAllDirect(classLoader);
        //Get all field values of an object through reflection access
        values = Fields.getAll(classLoader);
        
        Object obj = new Object() {
            volatile List<Object> objectValue;
            volatile int intValue;
            volatile long longValue;
            volatile float floatValue;
            volatile double doubleValue;
            volatile boolean booleanValue;
            volatile byte byteValue;
            volatile char charValue;
        };
        
        //Get all filtered field values of an object through memory address access
        Fields.getAllDirect(
            FieldCriteria.forEntireClassHierarchy().allThoseThatMatch(field -> {
                return field.getType().isPrimitive();
            }), 
            obj
        ).values();
    }
    
    public static void main(String[] args) {
        execute();
    }
    
}
```
For methods handling we are going to use **Methods** component:
```java
import static org.burningwave.core.assembler.StaticComponentContainer.Classes;
import static org.burningwave.core.assembler.StaticComponentContainer.Methods;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.security.ProtectionDomain;
import java.util.Collection;

import org.burningwave.core.classes.MethodCriteria;


@SuppressWarnings("unused")
public class MethodsHandler {
    
    public static void execute() {
        //Invoking method by using reflection
        Methods.invoke(System.out, "println", "Hello World");

        //Invoking static method by using MethodHandle
        Integer number = Methods.invokeStaticDirect(Integer.class, "valueOf", 1);
        
        //Invoking method by using MethodHandle
        Methods.invokeDirect(System.out, "println", number);
        
        //Filtering and obtaining a MethodHandle reference
        MethodHandle methodHandle = Methods.findFirstDirectHandle(
            MethodCriteria.byScanUpTo((cls) ->
            //We only analyze the ClassLoader class and not all of its hierarchy (default behavior)
                cls.getName().equals(ClassLoader.class.getName())
            ).name(
                "defineClass"::equals
            ).and().parameterTypes(params -> 
                params.length == 3
            ).and().parameterTypesAreAssignableFrom(
                String.class, ByteBuffer.class, ProtectionDomain.class
            ).and().returnType((cls) -> 
                cls.getName().equals(Class.class.getName())
            ), ClassLoader.class
        );        
        
        //Filtering and obtaining all methods of ClassLoader class that have at least
        //one input parameter of Class type
        Collection<Method> methods = Methods.findAll(
            MethodCriteria.byScanUpTo((cls) ->
            	//We only analyze the ClassLoader class and not all of its hierarchy (default behavior)
                cls.getName().equals(ClassLoader.class.getName())
            ).parameter((params, idx) -> {
                return Classes.isAssignableFrom(params[idx].getType(), Class.class);
            }), ClassLoader.class
        );
    }
    
    public static void main(String[] args) {
        execute();
    }
    
}
```

For constructors handling we are going to use **Constructors** component:
```java
import static org.burningwave.core.assembler.StaticComponentContainer.Constructors;

import org.burningwave.core.classes.MemoryClassLoader;

public class ConstructorsHandler {
    
    public static void execute() {
        //Invoking constructor by using reflection
        MemoryClassLoader classLoader = Constructors.newInstanceOf(MemoryClassLoader.class, Thread.currentThread().getContextClassLoader());
        
        //Invoking constructor with a null parameter value by using MethodHandle
        classLoader = Constructors.newInstanceDirectOf(MemoryClassLoader.class, null);
    }
    
    public static void main(String[] args) {
        execute();
    }
    
}
```

<br>

# Getting and setting properties of a Java bean through path
Through **ByFieldOrByMethodPropertyAccessor** and **ByMethodOrByFieldPropertyAccessor** it is possible to get and set properties of a Java bean by using path. So for this example we will use these Java beans:

```java
package org.burningwave.core.bean;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Complex {
    private Complex.Data data;
    
    public Complex() {
        setData(new Data());
    }
    
    
    public Complex.Data getData() {
        return data;
    }
    
    public void setData(Complex.Data data) {
        this.data = data;
    }


    public static class Data {
        private Data.Item[][] items;
        private List<Data.Item> itemsList;
        private Map<String, Data.Item[][]> itemsMap;
        
        public Data() {
            items = new Data.Item[][] {
                new Data.Item[] {
                    new Item("Hello"),
                    new Item("World!"),
                    new Item("How do you do?")
                },
                new Data.Item[] {
                    new Item("How do you do?"),
                    new Item("Hello"),
                    new Item("Bye")
                }
            };
            itemsMap = new LinkedHashMap<>();
            itemsMap.put("items", items);
        }
        
        public Data.Item[][] getItems() {
            return items;
        }
        public void setItems(Data.Item[][] items) {
            this.items = items;
        }
        
        public List<Data.Item> getItemsList() {
            return itemsList;
        }
        public void setItemsList(List<Data.Item> itemsList) {
            this.itemsList = itemsList;
        }
        
        public Map<String, Data.Item[][]> getItemsMap() {
            return itemsMap;
        }
        public void setItemsMap(Map<String, Data.Item[][]> itemsMap) {
            this.itemsMap = itemsMap;
        }
        
        public static class Item {
            private String name;
            
            public Item(String name) {
                this.name = name;
            }
            
            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }
        }
    }
}
```
... And now we are going to get and set some properties:
```java
import static org.burningwave.core.assembler.StaticComponentContainer.ByFieldOrByMethodPropertyAccessor;
import static org.burningwave.core.assembler.StaticComponentContainer.ByMethodOrByFieldPropertyAccessor;

import org.burningwave.core.bean.Complex;

public class GetAndSetPropertiesThroughPath{
    
    public void execute() {
        Complex complex = new Complex();
        //This type of property accessor try to access by field introspection: if no field was found
        //it will search getter method and invokes it
        String nameFromObjectInArray = ByFieldOrByMethodPropertyAccessor.get(complex, "data.items[1][0].name");
        String nameFromObjectMap = ByFieldOrByMethodPropertyAccessor.get(complex, "data.itemsMap[items][1][1].name");
        System.out.println(nameFromObjectInArray);
        System.out.println(nameFromObjectMap);
        //This type of property accessor looks for getter method and invokes it: if no getter method was found
        //it will search for field and try to retrieve it
        nameFromObjectInArray = ByMethodOrByFieldPropertyAccessor.get(complex, "data.items[1][2].name");
        nameFromObjectMap = ByMethodOrByFieldPropertyAccessor.get(complex, "data.itemsMap[items][1][1].name");
        System.out.println(nameFromObjectInArray);
        System.out.println(nameFromObjectMap);
        ByMethodOrByFieldPropertyAccessor.set(complex, "data.itemsMap[items][1][1].name", "Good evening!");
        nameFromObjectInArray = ByMethodOrByFieldPropertyAccessor.get(complex, "data.itemsMap[items][1][1].name");
        System.out.println(nameFromObjectInArray);
    }
    
    public static void main(String[] args) {
        new GetAndSetPropertiesThroughPath().execute();
    }
    
}
```


<br/>

# Architectural overview and configuration

**Burningwave Core** is based on the concept of component and component container. A **component** is a dynamic object that perform functionality related to the domain it belong to.
A **component container** contains a set of dynamic components and could be of two types:
* **static component container**
* **dynamic component container**

More than one dynamic container can be created, while only one static container can exists.
<br/>

## Static component container
It is represented by the **org.burningwave.core.assembler.StaticComponentContainer** class that provides the following fields for each component supplied:
```java
public static final org.burningwave.core.concurrent.QueuedTasksExecutor.Group BackgroundExecutor;
public static final org.burningwave.core.classes.PropertyAccessor ByFieldOrByMethodPropertyAccessor;
public static final org.burningwave.core.classes.PropertyAccessor ByMethodOrByFieldPropertyAccessor;
public static final org.burningwave.core.jvm.LowLevelObjectsHandler.ByteBufferHandler ByteBufferHandler;
public static final org.burningwave.core.Cache Cache;
public static final org.burningwave.core.classes.Classes Classes;
public static final org.burningwave.core.classes.Classes.Loaders ClassLoaders;
public static final org.burningwave.core.classes.Constructors Constructors;
public static final org.burningwave.core.io.FileSystemHelper FileSystemHelper;
public static final org.burningwave.core.classes.Fields Fields;
public static final org.burningwave.core.iterable.Properties GlobalProperties;
public static final org.burningwave.core.iterable.IterableObjectHelper IterableObjectHelper;
public static final org.burningwave.core.jvm.JVMInfo JVMInfo;
public static final org.burningwave.core.jvm.LowLevelObjectsHandler LowLevelObjectsHandler;
public static final org.burningwave.core.ManagedLogger.Repository ManagedLoggersRepository;
public static final org.burningwave.core.classes.Members Members;
public static final org.burningwave.core.classes.Methods Methods;
public static final org.burningwave.core.Objects Objects;
public static final org.burningwave.core.Strings.Paths Paths;
public static final org.burningwave.core.io.Resources Resources;
public static final org.burningwave.core.classes.SourceCodeHandler SourceCodeHandler;
public static final org.burningwave.core.io.Streams Streams;
public static final org.burningwave.core.Strings Strings;
public static final org.burningwave.core.concurrent.Synchronizer Synchronizer;
public static final org.burningwave.core.concurrent.Thread.Holder ThreadHolder;
public static final org.burningwave.core.concurrent.Thread.Supplier ThreadSupplier;
public static final org.burningwave.core.Throwables Throwables;
```

... That can be used within your application, simply adding a static import to your compilation unit, i.e.:
```java
package org.burningwave.core.examples.staticcomponents;

import static org.burningwave.core.assembler.StaticComponentContainer.ClassLoaders;
import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggersRepository;

public class UseOfStaticComponentsExample {
    
    public void yourMethod(){
        ManagedLoggersRepository.logInfo(
            () -> UseOfStaticComponentsExample.class.getName(),
            "Master class loader is {}",
            ClassLoaders.getMaster(Thread.currentThread().getContextClassLoader())
        );
    }

}
```
### Configuration
The configuration of this type of container is done via **burningwave.static.properties** file or via **burningwave.static.default.properties** file: the library searches for the first file and if it does not find it, then it searches for the second file and if neither this one is found then the library sets the default configuration programmatically. **The default configuration loaded programmatically if no configuration file is found is the following**:
```properties
background-executor.all-tasks-monitoring.enabled=\
	true
background-executor.all-tasks-monitoring.interval=\
	30000
background-executor.all-tasks-monitoring.logger.enabled=\
	false
background-executor.all-tasks-monitoring.minimum-elapsed-time-to-consider-a-task-as-probable-dead-locked=\
	300000
#Other possible values are: 'mark as probable dead locked', 'abort' or both comma separated
background-executor.all-tasks-monitoring.probable-dead-locked-tasks-handling.policy=\
	log only
background-executor.task-creation-tracking.enabled=\
	${background-executor.all-tasks-monitoring.enabled}
group-name-for-named-elements=\
	Burningwave
hide-banner-on-init=\
	false
iterable-object-helper.default-values-separator=\
	;
iterable-object-helper.parallel-iteration.applicability.max-runtime-threads-count-threshold=\
	autodetect
#With this value the library will search if org.slf4j.Logger is present and, in this case,
#the SLF4JManagedLoggerRepository will be instantiated, otherwise the SimpleManagedLoggerRepository will be instantiated
managed-logger.repository=\
	autodetect
#to increase performance set it to false
managed-logger.repository.enabled=\
	true
managed-logger.repository.logging.warn.disabled-for=\
	org.burningwave.core.classes.ClassHunter$SearchContext;\
	org.burningwave.core.classes.ClassPathHunter$SearchContext;\
	org.burningwave.core.jvm.LowLevelObjectsHandler;\
	org.burningwave.core.classes.MemoryClassLoader;\
	org.burningwave.core.classes.PathScannerClassLoader;\
	org.burningwave.core.classes.SearchContext;
streams.default-buffer-size=\
	1024
streams.default-byte-buffer-allocation-mode=\
	ByteBuffer::allocateDirect
synchronizer.all-threads-monitoring.enabled=\
	false
synchronizer.all-threads-monitoring.interval=\
	90000
thread-supplier.default-daemon-flag-value=\
	true
thread-supplier.max-detached-threads-count=\
	autodetect
thread-supplier.max-detached-threads-count.elapsed-time-threshold-from-last-increase-for-gradual-decreasing-to-initial-value=\
	30000
thread-supplier.max-detached-threads-count.increasing-step=\
	8
thread-supplier.max-poolable-threads-count=\
	autodetect
thread-supplier.poolable-thread-request-timeout=\
	6000
```
**If in your custom burningwave.static.properties or burningwave.static.default.properties file one of this default properties is not found, the relative default value here in the box above is assumed**.
[Here an example of a **burningwave.static.properties** file.](https://github.com/burningwave/core/blob/master/src/test/resources/burningwave.static.properties#L1)
<br/>

## Dynamic component container
It is represented by the **org.burningwave.core.assembler.ComponentContainer** class that provides the following methods for each component supplied:
```java
public ByteCodeHunter getByteCodeHunter();
public ClassFactory getClassFactory();
public ClassHunter getClassHunter();
public ClassPathHunter getClassPathHelper();
public ClassPathHunter getClassPathHunter();
public CodeExecutor getCodeExecutor();
public FunctionalInterfaceFactory getFunctionalInterfaceFactory();
public JavaMemoryCompiler getJavaMemoryCompiler();
public PathHelper getPathHelper();
public PathScannerClassLoader getPathScannerClassLoader();
```
... That can be used within your application, simply as follow:
```java
package org.burningwave.core.examples.componentcontainer;

import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.ClassFactory;
import org.burningwave.core.classes.ClassHunter;
import org.burningwave.core.io.PathHelper;
import java.util.Properties;

public class RetrievingDynamicComponentContainerAndComponents {

    public static void execute() throws Throwable {
        //In this case we are retrieving the singleton component container instance
        ComponentSupplier componentSupplier = ComponentContainer.getInstance();
        
        //In this case we are creating a component container by using a custom configuration file
        ComponentSupplier customComponentSupplier = ComponentContainer.create("your-custom-properties-file.properties");
        
        //In this case we are creating a component container programmatically by using a custom properties object
        Properties configProps = new Properties();
        configProps.put(ClassFactory.Configuration.Key.DEFAULT_CLASS_LOADER, Thread.currentThread().getContextClassLoader());
        configProps.put(ClassHunter.Configuration.Key.DEFAULT_PATH_SCANNER_CLASS_LOADER, componentSupplier.getPathScannerClassLoader());
        ComponentSupplier customComponentSupplier2 = ComponentContainer.create(configProps);
        
        PathHelper pathHelper = componentSupplier.getPathHelper();
        ClassFactory classFactory = customComponentSupplier.getClassFactory();
        ClassHunter classHunter = customComponentSupplier2.getClassHunter();
       
    }   
    
}
```
### Configuration
The configuration of this type of container can be done via Properties file or programmatically via a Properties object.
If you use the singleton instance obtained via **`ComponentContainer.getInstance()`** method, you must create a **burningwave.properties** file and put it on base path of your classpath project.
**The default configuration automatically loaded if no configuration file is found is the following**:
```properties
byte-code-hunter.default-path-scanner-class-loader=\
	(Supplier<PathScannerClassLoader>)() -> ((ComponentSupplier)parameter[0]).getPathScannerClassLoader()
#This variable is empty by default and can be valorized by developer and it is
#included by 'byte-code-hunter.default-path-scanner-class-loader.supplier.imports' property
byte-code-hunter.default-path-scanner-class-loader.supplier.additional-imports=
byte-code-hunter.default-path-scanner-class-loader.supplier.imports=\
	${code-executor.common.imports};\
	${byte-code-hunter.default-path-scanner-class-loader.supplier.additional-imports};\
	org.burningwave.core.classes.PathScannerClassLoader;
byte-code-hunter.default-path-scanner-class-loader.supplier.name=\
	org.burningwave.core.classes.DefaultPathScannerClassLoaderRetrieverForByteCodeHunter
byte-code-hunter.new-isolated-path-scanner-class-loader.search-config.check-file-option=\
	${hunters.default-search-config.check-file-option}
class-factory.byte-code-hunter.search-config.check-file-option=\
	${hunters.default-search-config.check-file-option}
#default classloader used by the ClassFactory to load generated classes
class-factory.default-class-loader=\
	(Supplier<ClassLoader>)() -> ((ComponentSupplier)parameter[0]).getPathScannerClassLoader()
#This variable is empty by default and can be valorized by developer and it is
#included by 'class-factory.default-class-loader.supplier.imports' property
class-factory.default-class-loader.supplier.additional-imports=
class-factory.default-class-loader.supplier.imports=\
	${code-executor.common.imports};\
	${class-factory.default-class-loader.supplier.additional-imports};\
	org.burningwave.core.classes.PathScannerClassLoader;
class-factory.default-class-loader.supplier.name=\
	org.burningwave.core.classes.DefaultClassLoaderRetrieverForClassFactory
class-hunter.default-path-scanner-class-loader=\
	(Supplier<PathScannerClassLoader>)() -> ((ComponentSupplier)parameter[0]).getPathScannerClassLoader()
#This variable is empty by default and can be valorized by developer and it is
#included by 'class-hunter.default-path-scanner-class-loader.supplier.imports' property
class-hunter.default-path-scanner-class-loader.supplier.additional-imports=
class-hunter.default-path-scanner-class-loader.supplier.imports=\
	${code-executor.common.imports};\
	${class-hunter.default-path-scanner-class-loader.supplier.additional-imports};\
	org.burningwave.core.classes.PathScannerClassLoader;
class-hunter.default-path-scanner-class-loader.supplier.name=\
	org.burningwave.core.classes.DefaultPathScannerClassLoaderRetrieverForClassHunter
class-hunter.new-isolated-path-scanner-class-loader.search-config.check-file-option=\
	${hunters.default-search-config.check-file-option}
class-path-helper.class-path-hunter.search-config.check-file-option=\
	${hunters.default-search-config.check-file-option}
class-hunter.default-path-scanner-class-loader=\
	(Supplier<PathScannerClassLoader>)() -> ((ComponentSupplier)parameter[0]).getPathScannerClassLoader()
class-path-hunter.default-path-scanner-class-loader=\
	(Supplier<PathScannerClassLoader>)() -> ((ComponentSupplier)parameter[0]).getPathScannerClassLoader()
#This variable is empty by default and can be valorized by developer and it is
#included by 'class-path-hunter.default-path-scanner-class-loader.supplier.imports' property
class-path-hunter.default-path-scanner-class-loader.supplier.additional-imports=
class-path-hunter.default-path-scanner-class-loader.supplier.imports=\
	${code-executor.common.imports};\
	${class-path-hunter.default-path-scanner-class-loader.supplier.additional-imports};\
	org.burningwave.core.classes.PathScannerClassLoader;
class-path-hunter.default-path-scanner-class-loader.supplier.name=\
	org.burningwave.core.classes.DefaultPathScannerClassLoaderRetrieverForClassPathHunter
class-path-hunter.new-isolated-path-scanner-class-loader.search-config.check-file-option=\
	${hunters.default-search-config.check-file-option}
#This variable is empty by default and can be valorized by developer and it is
#included by 'code-executor.common.import' property
code-executor.common.additional-imports=
code-executor.common.imports=\
	static org.burningwave.core.assembler.StaticComponentContainer.BackgroundExecutor;\
	${code-executor.common.additional-imports};\
	org.burningwave.core.assembler.ComponentSupplier;\
	java.util.function.Function;\
	org.burningwave.core.io.FileSystemItem;\
	org.burningwave.core.io.PathHelper;\
	org.burningwave.core.concurrent.QueuedTasksExecutor$ProducerTask;\
	org.burningwave.core.concurrent.QueuedTasksExecutor$Task;\
	java.util.function.Supplier;
component-container.after-init.operations.imports=\
	${code-executor.common.imports};\
	${component-container.after-init.operations.additional-imports};\
	org.burningwave.core.classes.SearchResult;
component-container.after-init.operations.executor.name=\
	org.burningwave.core.assembler.AfterInitOperations
hunters.default-search-config.check-file-option=\
	${path-scanner-class-loader.search-config.check-file-option}
path-scanner-class-loader.parent=\
	Thread.currentThread().getContextClassLoader()
#This variable is empty by default and can be valorized by developer and it is
#included by 'path-scanner-class-loader.parent.supplier.imports' property
path-scanner-class-loader.parent.supplier.additional-imports=\
path-scanner-class-loader.parent.supplier.imports=\
	${code-executor.common.imports};\
	${path-scanner-class-loader.parent.supplier.additional-imports};
path-scanner-class-loader.parent.supplier.name=\
	org.burningwave.core.classes.ParentClassLoaderRetrieverForPathScannerClassLoader
#other possible values are: checkFileName, checkFileName|checkFileSignature, checkFileName&checkFileSignature
path-scanner-class-loader.search-config.check-file-option=checkFileName
#This variable is empty by default and can be valorized by developer and it is
#included by 'paths.class-factory.default-class-loader.class-repositories' property
paths.class-factory.default-class-loader.additional-class-repositories=
#this variable indicates all the paths from which the classes 
#must be taken if during the definition of the compiled classes
#on classloader there will be classes not found
paths.class-factory.default-class-loader.class-repositories=\
	${paths.java-memory-compiler.class-paths};\
	${paths.java-memory-compiler.class-repositories};\
	${paths.class-factory.default-class-loader.additional-class-repositories}
paths.hunters.default-search-config.paths=\
	${paths.main-class-paths};\
	${paths.main-class-paths.extension};\
	${paths.main-class-repositories};
#This variable is empty by default and can be valorized by developer and it is
#included by 'paths.java-memory-compiler.class-paths' property
paths.java-memory-compiler.additional-class-paths=
paths.java-memory-compiler.black-listed-class-paths=\
	//${paths.main-class-paths}/..//children:.*?surefirebooter\d{0,}\.jar;
#this variable indicates all the class paths used by the JavaMemoryCompiler
#component for compiling
paths.java-memory-compiler.class-paths=\
	${paths.main-class-paths};\
	${paths.main-class-paths.extension};\
	${paths.java-memory-compiler.additional-class-paths}
#This variable is empty by default and can be valorized by developer. and it is
#included by 'paths.java-memory-compiler.class-repositories' property
paths.java-memory-compiler.additional-class-repositories=
#All paths inserted here will be analyzed by JavaMemoryCompiler component in case 
#of compilation failure to search for class paths of all classes imported by sources 
paths.java-memory-compiler.class-repositories=\
	${paths.main-class-repositories};\
	${paths.java-memory-compiler.additional-class-repositories};
paths.main-class-paths=\
	${system.properties:java.class.path}
paths.main-class-paths.extension=\
	//${system.properties:java.home}/lib//children:.*?\.jar;\
	//${system.properties:java.home}/lib/ext//children:.*?\.jar;
paths.main-class-repositories=\
	//${system.properties:java.home}/jmods//children:.*?\.jmod;
```
**If in your custom burningwave.properties file one of this default properties is not found, the relative default value here in the box above is assumed**.

If you create a component container instance through method **`ComponentContainer.create(String relativeConfigFileName)`**, you can specify the file name of your properties file and you can locate it everywhere in your classpath project but remember to use a relative path in this case, i.e.: if you name your file "custom-config-file.properties" and put it in package "org.burningwave" you must create the component container as follow: 
```java
ComponentContainer.create("org/burningwave/custom-config-file.properties")
```
[Here an example of a **burningwave.properties** file.](https://github.com/burningwave/core/blob/master/src/test/resources/burningwave.properties#L1)

### Other examples of using some components:
<details open>
	<summary><b>BackgroundExecutor</b></summary>
	<ul>
		<li>
			<a href="https://github.com/burningwave/core/wiki/Performing-tasks-in-parallel-with-different-priorities">
			<b>USE CASE</b>: performing different tasks in parallel and with different priorities
			</a>
		</li>
	</ul>
</details>
<details open>
	<summary><b>ClassFactory</b></summary>
	<ul>
		<li>
			<a href="https://github.com/burningwave/core/wiki/Generating-classes-at-runtime-and-invoking-their-methods-with-and-without-the-use-of-reflection">
			<b>USE CASE</b>: generating classes at runtime and invoking their methods with and without the use of the reflection
			</a>
		</li>
	</ul>
</details>
<details open>
	<summary><b>ClassHunter</b></summary>
	<ul>
		<li>
			<a href="https://github.com/burningwave/core/wiki/In-depth-look-to-ClassHunter-and-configuration-guide">
			<b>In depth look to and configuration guide</b>
			</a>
		</li>
		<li>
			<a href="https://github.com/burningwave/core/wiki/How-to-retrieve-all-classes-of-the-classpath">
			<b>USE CASE</b>: retrieving  all classes of the classpath
			</a>
		</li>
		<li>
			<a href="https://github.com/burningwave/core/wiki/How-retrieve-all-classes-that-implement-one-or-more-interfaces">
			<b>USE CASE</b>: retrieving all classes that implement one or more interfaces
			</a>
		</li>
		<li>
			<a href="https://github.com/burningwave/core/wiki/Finding-all-classes-that-extend-a-base-class">
			<b>USE CASE</b>: finding all classes that extend a base class
			</a>
		</li>
		<li>
			<a href="https://github.com/burningwave/core/wiki/How-to-search-for-all-classes-that-have-package-name-that-matches-a-regex">
			<b>USE CASE</b>: searching for all classes that have package name that matches a regex
			</a>
		</li>
		<li>
			<a href="https://github.com/burningwave/core/wiki/Finding-all-classes-for-module-name-(Java-9-and-later)">
			<b>USE CASE</b>: finding all classes for module name (Java 9 and later)
			</a>
		</li>
		<li>
			<a href="https://github.com/burningwave/core/wiki/Finding-all-annotated-classes">
			<b>USE CASE</b>: finding all annotated classes
			</a>
		</li>
		<li>
			<a href="https://github.com/burningwave/core/wiki/How-to-scan-classes-for-specific-annotations-and-collect-its-values">
			<b>USE CASE</b>: how to scan classes for specific annotations and collect its values
			</a>
		</li>
		<li>
			<a href="https://github.com/burningwave/core/wiki/How-to-search-for-all-classes-with-a-constructor-that-takes-a-specific-type-as-first-parameter-and-with-at-least-2-methods-that-begin-for-a-given-string">
			<b>USE CASE</b>: searching for all classes with a constructor that takes a specific type as first parameter and with at least 2 methods that begin for a given string
			</a>
		</li>
		<li>
			<a href="https://github.com/burningwave/core/wiki/How-to-search-for-all-classes-with-methods-whose-name-begins-for-a-given-string-and-that-takes-a-specific-type-as-its-first-parameter">
			<b>USE CASE</b>: searching for all classes with methods whose name begins for a given string and that takes a specific type as its first parameter
			</a>
		</li>
		<li>
			<a href="https://github.com/burningwave/core/wiki/Finding-all-classes-that-have-at-least-2-protected-fields">
			<b>USE CASE</b>: finding all classes that have at least 2 protected fields
			</a>
		</li>
	</ul>
</details>
<details open>
	<summary><b>ClassPathHunter</b></summary>
	<ul>
		<li>
			<a href="https://github.com/burningwave/core/wiki/Finding-where-a-class-is-loaded-from">
			<b>USE CASE</b>: finding where a class is loaded from
			</a>
		</li>
	</ul>
</details>
<details open>
	<summary><b>CodeExecutor</b></summary>
	<ul>
		<li>
			<a href="https://github.com/burningwave/core/wiki/Executing-stringified-source-code">
			<b>USE CASE</b>: executing stringified source code
			</a>
		</li>
	</ul>
</details>
<details open>
	<summary><b>Constructors</b></summary>
	<ul>
		<li>
			<a href="https://github.com/burningwave/core/wiki/Handling-privates-and-all-other-constructors-of-an-object">
			<b>USE CASE</b>: handling privates and all other constructors of an object
			</a>
		</li>
	</ul>
</details>
<details open>
	<summary><b>Fields</b></summary>
	<ul>
		<li>
			<a href="https://github.com/burningwave/core/wiki/Handling-privates-and-all-other-fields-of-an-object">
			<b>USE CASE</b>: handling privates and all other fields of an object
			</a>
		</li>
	</ul>
</details>
<details open>
	<summary><b>FileSystemItem</b></summary>
	<ul>
		<li>
			<a href="https://github.com/burningwave/core/wiki/Reaching-a-resource-of-the-file-system">
			<b>USE CASE</b>: reaching a resource of the file system
			</a>
		</li>
	</ul>
</details>
<details open>
	<summary><b>FunctionalInterfaceFactory</b></summary>
	<ul>
		<li>
			<a href="https://github.com/burningwave/core/wiki/How-to-bind-methods-or-constructors-to-functional-interfaces">
			<b>USE CASE</b>: How to bind methods or constructors to functional interfaces
			</a>
		</li>
	</ul>
</details>
<details open>
	<summary><b>IterableObjectHelper</b></summary>
	<ul>
		<li>
			<a href="https://github.com/burningwave/core/wiki/Retrieving-placeholdered-items-from-map-and-properties-file">
			<b>USE CASE</b>: retrieving placeholdered items from map and properties file
			</a>
		</li>
	</ul>
</details>
<details open>
	<summary><b>JavaMemoryCompiler</b></summary>
	<ul>
		<li>
			<a href="https://github.com/burningwave/core/wiki/How-to-compile-sources-at-runtime">
			<b>USE CASE</b>: compiling sources at runtime
			</a>
		</li>
	</ul>
</details>
<details open>
	<summary><b>Methods</b></summary>
	<ul>
		<li>
			<a href="https://github.com/burningwave/core/wiki/Handling-privates-and-all-other-methods-of-an-object">
			<b>USE CASE</b>: handling privates and all other methods of an object
			</a>
		</li>
	</ul>
</details>
<details open>
	<summary><b>PathHelper</b></summary>
	<ul>
		<li>
			<a href="https://github.com/burningwave/core/wiki/Resolving,-collecting-or-retrieving-paths">
			<b>USE CASE</b>: resolving, collecting or retrieving paths
			</a>
		</li>
	</ul>
</details>
<details open>
	<summary><b>PropertyAccessor</b></summary>
	<ul>
		<li>
			<a href="https://github.com/burningwave/core/wiki/Getting-and-setting-properties-of-a-Java-bean-through-path">
			<b>USE CASE</b>: getting and setting properties of a Java bean through path
			</a>
		</li>
	</ul>
</details>
<details open>
	<summary><b>UnitSourceGenerator</b></summary>
	<ul>
		<li>
			<a href="https://github.com/burningwave/core/wiki/How-to-generate-sources-at-runtime">
			<b>USE CASE</b>: generating sources at runtime
			</a>
		</li>
	</ul>
</details>

### [**Official site**](https://www.burningwave.org/)
### [**Help guide**](https://www.burningwave.org/forum/topic/help-guide/)
### [**Ask the Burningwave community for assistance**](https://www.burningwave.org/forum/forum/how-to/)
