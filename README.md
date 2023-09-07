# Burningwave Core [![Tweet](https://img.shields.io/twitter/url/http/shields.io.svg?style=social)](https://twitter.com/intent/tweet?text=%40burningwave_org%20Core%2C%20the%20%23Java%20frameworks%20building%20library%20%28works%20on%20%23Java8%20%23Java9%20%23Java10%20%23Java11%20%23Java12%20%23Java13%20%23Java14%20%23Java15%20%23Java16%20%23Java17%20%23Java18%20%23Java19%20%23Java20%29&url=https://burningwave.github.io/core/)

<a href="https://www.burningwave.org">
<img src="https://raw.githubusercontent.com/burningwave/burningwave.github.io/main/logo.png" alt="logo.png" height="180px" align="right"/>
</a>

[![Maven Central with version prefix filter](https://img.shields.io/maven-central/v/org.burningwave/core/12)](https://maven-badges.herokuapp.com/maven-central/org.burningwave/core/)
[![GitHub](https://img.shields.io/github/license/burningwave/core)](https://github.com/burningwave/core/blob/master/LICENSE)

[![Platforms](https://img.shields.io/badge/platforms-Windows%2C%20Mac%20OS%2C%20Linux-orange)](https://github.com/burningwave/core/actions/runs/6106807912)

[![Supported JVM](https://img.shields.io/badge/supported%20JVM-8%2C%209+%20(20)-blueviolet)](https://github.com/burningwave/core/actions/runs/6106807912)

[![Coveralls github branch](https://img.shields.io/coveralls/github/burningwave/core/master)](https://coveralls.io/github/burningwave/core?branch=master)
[![GitHub open issues](https://img.shields.io/github/issues/burningwave/core)](https://github.com/burningwave/core/issues)
[![GitHub closed issues](https://img.shields.io/github/issues-closed/burningwave/core)](https://github.com/burningwave/core/issues?q=is%3Aissue+is%3Aclosed)

[![Artifact downloads](https://www.burningwave.org/generators/generate-burningwave-artifact-downloads-badge.php?artifactId=core)](https://www.burningwave.org/artifact-downloads/?show-overall-trend-chart=false&artifactId=core)
[![Repository dependents](https://badgen.net/github/dependents-repo/burningwave/core)](https://github.com/burningwave/core/network/dependents)
[![HitCount](https://www.burningwave.org/generators/generate-visited-pages-badge.php)](https://www.burningwave.org#bw-counters)

**Burningwave Core** is an advanced, free and open source Java frameworks building library and it is useful for scanning class paths, generating classes at runtime, facilitating the use of reflection, scanning the filesystem, executing stringified source code, iterating collections or arrays in parallel, executing tasks in parallel and much more...

Burningwave Core contains **AN EXTREMELY POWERFUL CLASSPATH SCANNER**: it’s possible to search classes by every criteria that your imagination can make by using lambda expressions; **scan engine is highly optimized using direct allocated ByteBuffers to avoid heap saturation; searches are executed in multithreading context and are not affected by “_the issue of the same class loaded by different classloaders_”** (normally if you try to execute "isAssignableFrom" method on a same class loaded from different classloader it returns false).

And now we will see:
* [including Burningwave Core in your project](#Including-Burningwave-Core-in-your-project)
* [generating classes at runtime and invoking their methods with and without the use of reflection](#Generating-classes-at-runtime-and-invoking-their-methods-with-and-without-the-use-of-reflection)
* [retrieving classes of runtime class paths or of other paths through the ClassHunter](#Retrieving-classes-of-runtime-class-paths-or-of-other-paths-through-the-ClassHunter)
* [finding where a class is loaded from](#Finding-where-a-class-is-loaded-from)
* [performing tasks in parallel with different priorities](#Performing-tasks-in-parallel-with-different-priorities)
* [iterating collections and arrays in parallel by setting thread priority](#Iterating-collections-and-arrays-in-parallel-by-setting-thread-priority)
* [reaching a resource of the file system](#Reaching-a-resource-of-the-file-system)
* [resolving, collecting or retrieving paths](#Resolving-collecting-or-retrieving-paths)
* [retrieving placeholdered items from map and properties file](#Retrieving-placeholdered-items-from-map-and-properties-file)
* [handling privates and all other members of an object](#Handling-privates-and-all-other-members-of-an-object)
* [executing stringified source code](#Executing-stringified-source-code)
* [getting and setting properties of a Java bean through path](#Getting-and-setting-properties-of-a-Java-bean-through-path)
* [architectural overview and configuration](#Architectural-overview-and-configuration)
* [other examples of using some components](#Other-examples-of-using-some-components)
* [**how to ask for assistance**](#Ask-for-assistance)

<br/>

# <a name="Including-Burningwave-Core-in-your-project"></a>Including Burningwave Core in your project 
To include Burningwave Core library in your projects simply use with **Apache Maven**:

```xml
<dependency>
    <groupId>org.burningwave</groupId>
    <artifactId>core</artifactId>
    <version>12.63.0</version>
</dependency>
```

### Requiring the Burningwave Core module

To use Burningwave Core as a Java module you need to add the following to your `module-info.java`: 

```java
requires org.burningwave.core;
```

<br/>

# <a name="Generating-classes-at-runtime-and-invoking-their-methods-with-and-without-the-use-of-reflection"></a>Generating classes at runtime and invoking their methods with and without the use of reflection

For this purpose is necessary the use of **ClassFactory** component and of the **sources generating components**. Once the sources have been set in **UnitSourceGenerator** objects, they must be passed to **`loadOrBuildAndDefine`** method of ClassFactory with the ClassLoader where you want to define new generated classes. This method performs the following operations: tries to load all the classes present in the UnitSourceGenerator through the class loader, if at least one of these is not found it proceeds to compiling all the UnitSourceGenerators and uploading their classes on class loader: **in this case, keep in mind that if a class with the same name was previously loaded by the class loader, the compiled class will not be uploaded**. **If you need more information you can**:
* see a [**complete example about source code generators**](https://github.com/burningwave/core/blob/master/src/test/java/org/burningwave/core/UnitSourceGeneratorTest.java#L153)
* read this [**guide**](https://github.com/burningwave/core/wiki/FAQ#how-can-i-use-classes-located-outside-the-runtime-class-path-in-my-sources-to-be-generated) where you also can find a link to an [**example about generating classes by using libraries located outside the runtime class paths**](https://github.com/burningwave/core/blob/master/src/test/java/org/burningwave/core/examples/classfactory/ExternalClassRuntimeExtender.java)
* go [**here**](https://github.com/burningwave/core/tree/master/src/test/java/org/burningwave/core/examples/classfactory) for more examples
* [**ask for assistance**](#Ask-for-assistance)

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

# <a name="Retrieving-classes-of-runtime-class-paths-or-of-other-paths-through-the-ClassHunter"></a>Retrieving classes of runtime class paths or of other paths through the ClassHunter
The components of the class paths scanning engine are: **ByteCodeHunter**, [**ClassHunter**](https://github.com/burningwave/core/wiki/In-depth-look-to-ClassHunter-and-configuration-guide) and the **ClassPathHunter**. Now we are going to use the ClassHunter to search for all classes that have package name that matches a regex. So in this example we're looking for all classes whose package name contains "springframework" string in the runtime class paths:

```java
import java.util.Collection;

import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.ClassHunter;
import org.burningwave.core.classes.JavaClass;
import org.burningwave.core.classes.SearchConfig;
import org.burningwave.core.io.FileSystemItem;
    
public class Finder {
    
   public Collection<Class<?>> find() {
        ComponentSupplier componentSupplier = ComponentContainer.getInstance();
        ClassHunter classHunter = componentSupplier.getClassHunter();
        
        SearchConfig searchConfig = SearchConfig.create().addFileFilter(
            FileSystemItem.Criteria.forAllFileThat( fileSystemItem -> {
                JavaClass javaClass = fileSystemItem.toJavaClass();
                if (javaClass == null) {
                    return false;
                }
                String packageName = javaClass.getPackageName();
                return packageName != null && packageName.contains("springframework");
            })
        );

        try(ClassHunter.SearchResult searchResult = classHunter.findBy(searchConfig)) {
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
import org.burningwave.core.classes.ClassCriteria;
import org.burningwave.core.classes.ClassHunter;
import org.burningwave.core.classes.JavaClass;
import org.burningwave.core.classes.SearchConfig;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.PathHelper;
    
public class Finder {
    
   public Collection<Class<?>> find() {
        ComponentSupplier componentSupplier = ComponentContainer.getInstance();
        PathHelper pathHelper = componentSupplier.getPathHelper();
        ClassHunter classHunter = componentSupplier.getClassHunter();
        
        SearchConfig searchConfig = SearchConfig.forPaths(
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
        ).addFileFilter(
            FileSystemItem.Criteria.forAllFileThat( fileSystemItem -> {
                JavaClass javaClass = fileSystemItem.toJavaClass();
                if (javaClass == null) {
                    return false;
                }
                String packageName = javaClass.getPackageName();                       
                return packageName != null && packageName.contains("springframework");
            })
        );

        try(ClassHunter.SearchResult searchResult = classHunter.findBy(searchConfig)) {
            return searchResult.getClasses();
        }
    }
    
}
```

<br/>

# <a name="Finding-where-a-class-is-loaded-from"></a>Finding where a class is loaded from

For this purpose we are going to use the **ClassPathHunter** component:
```java
import java.util.Collection;

import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.ClassPathHunter;
import org.burningwave.core.classes.JavaClass;
import org.burningwave.core.classes.SearchConfig;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.PathHelper;
    
public class Finder {
    
   public Collection<FileSystemItem> find() {
        ComponentSupplier componentSupplier = ComponentContainer.getInstance();
        PathHelper pathHelper = componentSupplier.getPathHelper();
        ClassPathHunter classPathHunter = componentSupplier.getClassPathHunter();
        
        SearchConfig searchConfig = SearchConfig.forPaths(
            //Here you can add all absolute path you want:
            //both folders, zip and jar will be recursively scanned.
            //For example you can add: "C:\\Users\\user\\.m2"
            //With the line below the search will be executed on runtime class paths
            pathHelper.getMainClassPaths()
        ).addFileFilter(
            FileSystemItem.Criteria.forAllFileThat(fileSystemItem -> {
	            JavaClass javaClass = fileSystemItem.toJavaClass();
        	    return javaClass != null && javaClass.getName().equals(Finder.class.getName());
            })
        );

        try(ClassPathHunter.SearchResult searchResult = classPathHunter.findBy(searchConfig)) {
            return searchResult.getClassPaths();
        }
    }
    
}
```

<br/>

# <a name="Performing-tasks-in-parallel-with-different-priorities"></a>Performing tasks in parallel with different priorities
Used by the **IterableObjectHelper** to [iterate collections or arrays in parallel](#Iterating-collections-and-arrays-in-parallel-by-setting-thread-priority), the **BackgroundExecutor** component is able to run different functional interfaces in parallel **by setting the priority of the thread they will be assigned to**. There is also the option to wait for them start or finish.

For obtaining threads this component uses the <a name="ThreadSupplier">**ThreadSupplier**</a> that can be customized in the [burningwave.static.properties](#configuration) file and provides a fixed number of reusable threads indicated by the **`thread-supplier.max-poolable-thread-count`** property and, if these threads have already been assigned, new non-reusable threads will be created whose quantity maximum is indicated by the **`thread-supplier.max-detached-thread-count`** property. Once this limit is reached if the request for a new thread exceeds the waiting time indicated by the **`thread-supplier.poolable-thread-request-timeout`** property, the ThreadSupplier will proceed to increase the limit indicated by the 'thread-supplier.max-detached-thread-count' property for the quantity indicated by the **`thread-supplier.max-detached-thread-count.increasing-step`** property. Resetting the 'thread-supplier.max-detached-thread-count' property to its initial value, will occur gradually only when there have been no more waits on thread requests for an amount of time indicated by the **`thread-supplier.max-detached-thread-count.elapsed-time-threshold-from-last-increase-for-gradual-decreasing-to-initial-value`** property.
```java
import static org.burningwave.core.assembler.StaticComponentContainer.BackgroundExecutor;

import org.burningwave.core.ManagedLogger;
import org.burningwave.core.concurrent.QueuedTasksExecutor.ProducerTask;
import org.burningwave.core.concurrent.QueuedTasksExecutor.Task;


public class TaskLauncher implements ManagedLogger {
    
    public void launch() {
        ProducerTask<Long> taskOne = BackgroundExecutor.createProducerTask(task -> {
            Long startTime = System.currentTimeMillis();
            logInfo("task one started");
            synchronized (this) {                
                wait(5000);
            }
            Task internalTask = BackgroundExecutor.createTask(tsk -> {
                logInfo("internal task started");    
                synchronized (this) {                
                    wait(5000);
                }
                logInfo("internal task finished");    
            }, Thread.MAX_PRIORITY).submit();
            internalTask.waitForFinish();
            logInfo("task one finished");
            return startTime;
        }, Thread.MAX_PRIORITY);
        taskOne.submit();
        Task taskTwo = BackgroundExecutor.createTask(task -> {
            logInfo("task two started and wait for task one finishing");
            taskOne.waitForFinish();
            logInfo("task two finished");    
        }, Thread.NORM_PRIORITY);
        taskTwo.submit();
        ProducerTask<Long> taskThree = BackgroundExecutor.createProducerTask(task -> {
            logInfo("task three started and wait for task two finishing");
            taskTwo.waitForFinish();
            logInfo("task two finished");
            return System.currentTimeMillis();
        }, Thread.MIN_PRIORITY);
        taskThree.submit();
        taskThree.waitForFinish();
        logInfo("Elapsed time: {}ms", taskThree.join() - taskOne.join());
    }
    
    public static void main(String[] args) {
        new TaskLauncher().launch();
    }
    
}
```

<br/>

# <a name="Iterating-collections-and-arrays-in-parallel-by-setting-thread-priority"></a>Iterating collections and arrays in parallel by setting thread priority
Through the underlying configurable [**BackgroundExecutor**](#Performing-tasks-in-parallel-with-different-priorities) the **IterableObjectHelper** component is able to iterate a collection or an array in parallel and execute an action on each iterated item giving also the ability to set the threads priority:
```java
package org.burningwave.core.examples.iterableobjecthelper;

import static org.burningwave.core.assembler.StaticComponentContainer.IterableObjectHelper;
import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.burningwave.core.iterable.IterableObjectHelper.IterationConfig;

public class CollectionAndArrayIterator {

    public static void execute() {
        Collection<Integer> inputCollection =
            IntStream.rangeClosed(1, 1000000).boxed().collect(Collectors.toList());
        
        List<String> outputCollection = IterableObjectHelper.iterateAndGet(
            IterationConfig.of(inputCollection)
            //Enabling parallel iteration when the input collection size is greater than 2
            .parallelIf(inputColl -> inputColl.size() > 2)
            //Setting threads priority
            .withPriority(Thread.MAX_PRIORITY)
            //Setting up the output collection
            .withOutput(new ArrayList<String>())
            .withAction((number, outputCollectionSupplier) -> {
                if (number > 500000) {
                    //Terminating the current thread iteration early.
                    IterableObjectHelper.terminateCurrentThreadIteration();
                    //If you need to terminate all threads iteration (useful for a find first iteration) use
                    //IterableObjectHelper.terminateIteration();
                }
                if ((number % 2) == 0) {                        
                    outputCollectionSupplier.accept(outputColl ->
                        //Converting and adding item to output collection
                        outputColl.add(number.toString())
                    );
                }
            })    
        );
        
        IterableObjectHelper.iterate(
            IterationConfig.of(outputCollection)
            //Disabling parallel iteration
            .parallelIf(inputColl -> false)
            .withAction((number) -> {
                ManagedLoggerRepository.logInfo(CollectionAndArrayIterator.class::getName, "Iterated number: {}", number);
            })    
        );
        
        ManagedLoggerRepository.logInfo(
            CollectionAndArrayIterator.class::getName,
            "Output collection size {}", outputCollection.size()
        );
    }

    public static void main(String[] args) {
        execute();
    }
    
}
```

<br/>

# <a name="Reaching-a-resource-of-the-file-system"></a>Reaching a resource of the file system
Through **FileSystemItem** you can reach a resource of the file system even if it is contained in a nested supported (**zip, jar, war, ear, jmod**) compressed archive and obtain the content of it or other informations such as if it is a folder or a file or a compressed archive or if it is a compressed entry or obtain, if it is a folder or a compressed archive, the direct children or all nested children or a filtered collection of them. You can retrieve a FileSystemItem through an absolute path or through a relative path referred to your class path by using the PathHelper. FileSystemItems are cached and **there will only be one instance of them for an absolute path** and you can also clear the cache e reload all informations of a FileSystemItem. In the example below we show how to retrieve and use a FileSystemItem.

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

# <a name="Resolving-collecting-or-retrieving-paths"></a>Resolving, collecting or retrieving paths

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

# <a name="Retrieving-placeholdered-items-from-map-and-properties-file"></a>Retrieving placeholdered items from map and properties file

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
import static org.burningwave.core.assembler.StaticComponentContainer.IterableObjectHelper;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.iterable.IterableObjectHelper.ResolveConfig;

@SuppressWarnings("unused")
public class ItemFromMapRetriever {
    
    public void execute() throws IOException {
        ComponentSupplier componentSupplier = ComponentContainer.getInstance();
        PathHelper pathHelper = componentSupplier.getPathHelper();
        Properties properties = new Properties();
        properties.load(pathHelper.getResourceAsStream("burningwave.properties"));
        String code = IterableObjectHelper.resolveStringValue(
            ResolveConfig.forNamedKey("code-block-1")
            .on(properties)
        );

        Map<Object, Object> map = new HashMap<>();
        map.put("class-loader-01", "${class-loader-02}");
        map.put("class-loader-02", "${class-loader-03}");
        map.put("class-loader-03", Thread.currentThread().getContextClassLoader().getParent());
        ClassLoader parentClassLoader = IterableObjectHelper.resolveValue(
            ResolveConfig.forNamedKey("class-loader-01")
            .on(map)
        );
        
        map.clear();
        map.put("class-loaders", "${class-loader-02};${class-loader-03};");
        map.put("class-loader-02", Thread.currentThread().getContextClassLoader());
        map.put("class-loader-03", Thread.currentThread().getContextClassLoader().getParent());
        Collection<ClassLoader> classLoaders = IterableObjectHelper.resolveValues(
            ResolveConfig.forNamedKey("class-loaders")
            .on(map)
            .withValuesSeparator(";")
        );
    }
    
    public static void main(String[] args) throws IOException {
        new ItemFromMapRetriever().execute();
    }
}
```
<br>

# <a name="Handling-privates-and-all-other-members-of-an-object"></a>Handling privates and all other members of an object
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
            volatile int intValue = 1;
            volatile long longValue = 2l;
            volatile float floatValue = 3f;
            volatile double doubleValue = 4.1d;
            volatile boolean booleanValue = true;
            volatile byte byteValue = (byte)5;
            volatile char charValue = 'c';
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

<br/>

# <a name="Executing-stringified-source-code"></a>Executing stringified source code
It is possible to execute stringified source code by using the **CodeExecutor** in three three different ways:
* [through **BodySourceGenerator**](#Executing-code-with-BodySourceGenerator)
* [through a property located in Burningwave configuration file](#Executing-code-of-a-property-located-in-Burningwave-configuration-file)
* [through a property located in a custom Properties file](#Executing-code-of-a-property-located-in-a-custom-properties-file)

<br/>

## <a name="Executing-code-with-BodySourceGenerator"></a>Executing code with BodySourceGenerator
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
                .addCodeLine("return Integer.valueOf(inputNumber + (Integer)parameter[1]);")
            ).withParameter(Integer.valueOf(5), Integer.valueOf(3))
        );
        
    }
    
    public static void main(String[] args) {
        System.out.println("Total is: " + execute());
    }
}
```

<br/>

## <a name="Executing-code-of-a-property-located-in-Burningwave-configuration-file"></a>Executing code of a property located in Burningwave configuration file
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
    ${code-block-2.imports};\
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

## <a name="Executing-code-of-a-property-located-in-a-custom-properties-file"></a>Executing code of a property located in a custom properties file
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
    ${code-block-2.imports};\
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

# <a name="Getting-and-setting-properties-of-a-Java-bean-through-path"></a>Getting and setting properties of a Java bean through path
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

# <a name="Architectural-overview-and-configuration"></a>Architectural overview and configuration

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
public static final org.burningwave.core.jvm.BufferHandler BufferHandler;
public static final org.burningwave.core.classes.FieldAccessor ByFieldOrByMethodPropertyAccessor;
public static final org.burningwave.core.classes.FieldAccessor ByMethodOrByFieldPropertyAccessor;
public static final org.burningwave.core.Cache Cache;
public static final org.burningwave.core.classes.Classes Classes;
public static final org.burningwave.core.classes.Classes.Loaders ClassLoaders;
public static final org.burningwave.core.classes.Constructors Constructors;
public static final io.github.toolfactory.jvm.Driver Driver;
public static final org.burningwave.core.io.FileSystemHelper FileSystemHelper;
public static final org.burningwave.core.classes.Fields Fields;
public static final org.burningwave.core.iterable.Properties GlobalProperties;
public static final org.burningwave.core.iterable.IterableObjectHelper IterableObjectHelper;
public static final io.github.toolfactory.jvm.Info JVMInfo;
public static final org.burningwave.core.ManagedLogger.Repository ManagedLoggerRepository;
public static final org.burningwave.core.classes.Members Members;
public static final org.burningwave.core.classes.Methods Methods;
public static final org.burningwave.core.classes.Modules Modules; //Null on JDK 8
public static final org.burningwave.core.Objects Objects;
public static final org.burningwave.core.Strings.Paths Paths;
public static final org.burningwave.core.io.Resources Resources;
public static final org.burningwave.core.classes.SourceCodeHandler SourceCodeHandler;
public static final org.burningwave.core.io.Streams Streams;
public static final org.burningwave.core.Strings Strings;
public static final org.burningwave.core.concurrent.Synchronizer Synchronizer;
public static final org.burningwave.core.SystemProperties SystemProperties;
public static final org.burningwave.core.concurrent.Thread.Holder ThreadHolder;
public static final org.burningwave.core.concurrent.Thread.Supplier ThreadSupplier;
```

... That can be used within your application, simply adding a static import to your compilation unit, i.e.:
```java
import static org.burningwave.core.assembler.StaticComponentContainer.ClassLoaders;
import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository;

public class UseOfStaticComponentsExample {
    
    public void yourMethod(){
        ManagedLoggerRepository.logInfo(
            UseOfStaticComponentsExample.class::getName,
            "Master class loader is {}",
            ClassLoaders.getMaster(Thread.currentThread().getContextClassLoader())
        );
    }

}
```
### <a name="static-components-configuration-file"></a>Configuration
The configuration of this type of container is done via **burningwave.static.properties** file that must be located in the base path of your class path: the library looks for all files with this name and **merges them according to
to the property `priority-of-this-configuration` contained within it** which is optional but becomes mandatory if in the base class paths there are multiple files with the file name indicated above. It is possible to change the file name of the configuration file through the method  `org.burningwave.core.assembler.StaticComponentContainer.Configuration.Default.setFileName` before using the static component container or if you need **to integrate the configuration properties into Spring** you can follow [this guide](https://github.com/burningwave/core/wiki/FAQ#how-can-i-integrate-the-configuration-properties-into-spring). **If no configuration file is found, the library programmatically sets the default configuration with following values**:
```properties
background-executor.all-tasks-monitoring.enabled=\
	true
background-executor.all-tasks-monitoring.interval=\
	30000
background-executor.all-tasks-monitoring.logger.enabled=\
	false
background-executor.all-tasks-monitoring.minimum-elapsed-time-to-consider-a-task-as-probable-dead-locked=\
	300000
#Other possible values are: 'mark as probable dead locked',
#'interrupt', 'kill'. It is also possible to combine these values, e.g.:
#background-executor.all-tasks-monitoring.probable-dead-locked-tasks-handling.policy=\
#	mark as probable dead locked, kill
background-executor.all-tasks-monitoring.probable-dead-locked-tasks-handling.policy=\
	log only
background-executor.queued-task-executor[0].name=\
	Low priority tasks
background-executor.queued-task-executor[0].priority=\
	1
background-executor.queued-task-executor[1].name=\
	Normal priority tasks
background-executor.queued-task-executor[1].priority=\
	5
background-executor.queued-task-executor[2].name=\
	High priority tasks
background-executor.queued-task-executor[2].priority=\
	10
background-executor.task-creation-tracking.enabled=\
	${background-executor.all-tasks-monitoring.enabled}
banner.additonal-informations=\
	${Implementation-Title} ${Implementation-Version}
banner.additonal-informations.retrieve-from-manifest-file-with-implementation-title=\
	Burningwave Core
banner.hide=\
	false
banner.file=\
	org/burningwave/banner.bwb
buffer-handler.default-buffer-size=\
	1024
buffer-handler.default-allocation-mode=\
	ByteBuffer::allocateDirect
group-name-for-named-elements=\
	Burningwave
iterable-object-helper.default-values-separator=\
	;
iterable-object-helper.parallel-iteration.applicability.default-minimum-collection-size=\
	2
iterable-object-helper.parallel-iteration.applicability.max-runtime-thread-count-threshold=\
	autodetect
iterable-object-helper.parallel-iteration.applicability.output-collection-enabled-types=\
	java.util.concurrent.ConcurrentHashMap$CollectionView;\
	java.util.Collections$SynchronizedCollection;\
	java.util.concurrent.CopyOnWriteArrayList;\
	java.util.concurrent.CopyOnWriteArraySet;\
	java.util.concurrent.BlockingQueue;\
	java.util.concurrent.ConcurrentSkipListSet;\
	java.util.concurrent.ConcurrentSkipListMap$EntrySet;\
	java.util.concurrent.ConcurrentSkipListMap$KeySet;\
	java.util.concurrent.ConcurrentSkipListMap$Values;
#This property is optional and it is possible to use a custom JVM Driver which implements
#the io.github.toolfactory.jvm.Driver interface.
#Other possible values are: io.github.toolfactory.jvm.DefaultDriver, 
#org.burningwave.jvm.HybridDriver, org.burningwave.jvm.NativeDriver
jvm.driver.type=\
	org.burningwave.jvm.DynamicDriver
jvm.driver.init=\
	false
#With this value the library will search if org.slf4j.Logger is present and, in this case,
#the SLF4JManagedLoggerRepository will be instantiated, otherwise
#the SimpleManagedLoggerRepository will be instantiated
managed-logger.repository=\
	autodetect
#to increase performance set it to false
managed-logger.repository.enabled=\
	true
managed-logger.repository.logging.warn.disabled-for=\
	org.burningwave.core.assembler.ComponentContainer$ClassLoader;\
	org.burningwave.core.classes.MemoryClassLoader;\
	org.burningwave.core.classes.PathScannerClassLoader;
modules.export-all-to-all=\
	true
#mandatory if more burningwave.static.properties file are in the class paths
priority-of-this-configuration=0
resource-releaser.enabled=true
synchronizer.all-threads-monitoring.enabled=\
	false
synchronizer.all-threads-monitoring.interval=\
	90000
thread-supplier.default-daemon-flag-value=\
	true
thread-supplier.default-thread-priority=\
	5
thread-supplier.max-detached-thread-count=\
	${thread-supplier.max-poolable-thread-count}
thread-supplier.max-detached-thread-count.elapsed-time-threshold-from-last-increase-for-gradual-decreasing-to-initial-value=\
	30000
thread-supplier.max-detached-thread-count.increasing-step=\
	autodetect
thread-supplier.max-poolable-thread-count=\
	autodetect
thread-supplier.poolable-thread-request-timeout=\
	6000
```
**If in your custom burningwave.static.properties file one of this default properties is not found, the relative default value here in the box above is assumed**.
[Here an example of a **burningwave.static.properties** file.](https://github.com/burningwave/core/blob/master/src/test/resources/burningwave.static.properties#L1)
<br/>

## Dynamic component container
It is represented by the **org.burningwave.core.assembler.ComponentContainer** class that provides the following methods for each component supplied:
```java
public ByteCodeHunter getByteCodeHunter();
public ClassFactory getClassFactory();
public ClassHunter getClassHunter();
public ClassPathHelper getClassPathHelper();
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
If you use the singleton instance obtained via **`ComponentContainer.getInstance()`** method, you must create a **burningwave.properties** file and put it on base path of your class path project: the library looks for all files with this name and **merges them according to
to the property `priority-of-this-configuration` contained within it** which is optional but becomes mandatory if in the base class paths there are multiple files with the file name indicated above. It is possible to change the file name of the configuration file through the method `org.burningwave.core.assembler.ComponentContainer.Configuration.Default.setFileName` before using the component container or if you need **to integrate the configuration properties into Spring** you can follow [this guide](https://github.com/burningwave/core/wiki/FAQ#how-can-i-integrate-the-configuration-properties-into-spring). **If no configuration file is found, the library programmatically sets the default configuration with following values**:
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
	//${system.properties:java.home}/lib/ext//children:.*?\.jar;\
	//${system.properties:java.home}/../lib//children:.*?\.jar;
paths.main-class-repositories=\
	//${system.properties:java.home}/jmods//children:.*?\.jmod;
#mandatory if more burningwave.properties file are in the class paths
priority-of-this-configuration=0
```
**If in your custom burningwave.properties file one of this default properties is not found, the relative default value here in the box above is assumed**.

If you create a component container instance through method **`ComponentContainer.create(String relativeConfigFileName)`**, you can specify the file name of your properties file and you can locate it everywhere in your class path project but remember to use a relative path in this case, i.e.: if you name your file "custom-config-file.properties" and put it in package "org.burningwave" you must create the component container as follow: 
```java
ComponentContainer.create("org/burningwave/custom-config-file.properties")
```
[Here an example of a **burningwave.properties** file.](https://github.com/burningwave/core/blob/master/src/test/resources/burningwave.properties#L1)

<br/>

# <a name="Other-examples-of-using-some-components"></a>Other examples of using some components:
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
			<b>USE CASE</b>: retrieving  all classes of the class path
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
			<a href="https://github.com/burningwave/core/wiki/Iterating-collections-and-arrays-in-parallel-by-setting-thread-priority">
			<b>USE CASE</b>: iterating collections and arrays in parallel by setting thread priority
			</a>
		</li>
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

<br />

# <a name="Ask-for-assistance"></a>Ask for assistance
If the [**wiki**](https://github.com/burningwave/core/wiki) and the [**FAQ**](https://github.com/burningwave/core/wiki/FAQ) can't help you, you can:
* [open a discussion](https://github.com/burningwave/core/discussions) here on GitHub
* [report a bug](https://github.com/burningwave/core/issues) here on GitHub
* ask on [Stack Overflow](https://stackoverflow.com/search?q=burningwave)
