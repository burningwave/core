[![logo](Burningwave-logo.jpg "Burningwave")](https://www.burningwave.org/)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.burningwave/core/badge.svg#)](https://search.maven.org/artifact/org.burningwave/core)
[![Tweet](https://img.shields.io/twitter/url/http/shields.io.svg?style=social)](https://twitter.com/intent/tweet?text=Burningwave%20Core%3A%20a%20%23Java%20frameworks%20building%20library%20with%20an%20original%20classpath%20scan%20engine%20(works%20on%20%23Java8%20%23Java9%20%23Java10%20%23Java11%20%23Java12%20%23Java13%20%23Java14)&url=https://github.com/burningwave/core/wiki)

**Burningwave Core** is a fully indipendent, advanced, free and open source Java frameworks building library that contains **THE MOST POWERFUL CLASSPATH SCANNER** for criteria based classes search.
It’s possible to search classes by every criteria that your immagination can made by using lambda expressions. **Scan engine is highly optimized using direct allocated ByteBuffers to avoid heap saturation; searches are executed in multithreading context and are not affected by “_the issue of the same class loaded by different classloaders_”** (normally if you try to execute "isAssignableFrom" method on a same class loaded from different classloader it returns false).

**Tested on Java versions ranging from 8 to 14, Burningwave Core is also useful for scanning class paths, generating classes at runtime, facilitating the use of reflection, scanning the filesystem, executing stringified code and much more...**

**To include Burningwave Core library in your projects simply use with**:

* **Apache Maven**:
```xml
<dependency>
    <groupId>org.burningwave</groupId>
    <artifactId>core</artifactId>
    <version>5.16.3</version>
</dependency>
```

<details>
	<summary><b>... And with (click to expand)</b>:</summary>
	<br/>
	<ul><li><b>Gradle Groovy</b>:</li></ul>
	<pre>implementation 'org.burningwave:core:5.16.3'</pre>
	<br/>
	<ul><li><b>Gradle Kotlin</b>:</li></ul>
	<pre>implementation("org.burningwave:core:5.16.3")</pre>
	<br/>
	<ul><li><b>Scala</b>:</li></ul>
	<pre>libraryDependencies += "org.burningwave" % "core" % "5.16.3"</pre>
	<br/>
	<ul><li><b>Apache Ivy</b>:</li></ul>
	<pre>&lt;dependency org="org.burningwave" name="core" rev="5.16.3" /&gt;</pre>
	<br/>
	<ul><li><b>Groovy Grape</b>:</li></ul>
	<pre>
@Grapes(
  	@Grab(group='org.burningwave', module='core', version='5.16.3')
)
	</pre>
	<br/>
	<ul><li><b>Leiningen</b>:</li></ul>
	<pre>[org.burningwave/core "5.16.3"]</pre>
	<br/>
	<ul><li><b>Apache Buildr</b>:</li></ul>
	<pre>'org.burningwave:core:jar:5.16.3'</pre>
	<br/>
	<ul><li><b>PURL</b>:</li></ul>
	<pre>pkg:maven/org.burningwave/core@5.16.3</pre>
</details>

### [**Get started**](https://github.com/burningwave/core/wiki)
### [Overview and configuration](https://github.com/burningwave/core/wiki/Overview-and-configuration)
### Examples of use of some components:
<details open>
	<summary><b>ClassFactory</b></summary>
	<ul>
		<li>
			<a href="https://github.com/burningwave/core/wiki/Generating-classes-at-runtime-and-invoking-their-methods-with-and-without-the-use-of-reflection">
			<b>USE CASE</b>: generating classes at runtime and invoking their methods with and without the use of the reflection
			</a>
		</li>
		<li>
			<a href="https://github.com/burningwave/core/wiki/Executing-stringified-source-code-at-runtime">
			<b>USE CASE</b>: executing stringified source code at runtime
			</a>
		</li>
	</ul>
</details>
<details open>
	<summary><b>ClassHunter</b></summary>
	<ul>
		<li>
			<a href="https://github.com/burningwave/core/wiki/How-retrieve-all-classes-that-implement-one-or-more-interfaces">
			<b>USE CASE</b>: how retrieve all classes that implement one or more interfaces
			</a>
		</li>
		<li>
			<a href="https://github.com/burningwave/core/wiki/How-search-for-all-classes-with-methods-whose-name-begins-for-a-given-string-and-that-takes-a-specific-type-as-its-first-parameter">
			<b>USE CASE</b>: how search for all classes with methods whose name begins for a given string and that takes a specific type as its first parameter
			</a>
		</li>
		<li>
			<a href="https://github.com/burningwave/core/wiki/Finding-all-annotated-class">
			<b>USE CASE</b>: finding all annotated class
			</a>
		</li>
		<li>
			<a href="https://github.com/burningwave/core/wiki/How-to-scan-classes-for-specific-annotations-and-collect-its-values">
			<b>USE CASE</b>: how to scan classes for specific annotations and collect its values
			</a>
		</li>
		<li>
			<a href="https://github.com/burningwave/core/wiki/How-retrieve-all-classes-of-the-classpath">
			<b>USE CASE</b>: how retrieve all classes of the classpath
			</a>
		</li>
		<li>
			<a href="https://github.com/burningwave/core/wiki/How-to-search-for-all-classes-that-have-package-name-that-matches-a-regex">
			<b>USE CASE</b>: how to search for all classes that have package name that matches a regex
			</a>
		</li>
		<li>
			<a href="https://github.com/burningwave/core/wiki/Finding-all-classes-that-extend-a-base-class">
			<b>USE CASE</b>: finding all classes that extend a base class
			</a>
		</li>
		<li>
			<a href="https://github.com/burningwave/core/wiki/Finding-all-classes-that-have-at-least-2-protected-fields">
			<b>USE CASE</b>: finding all classes that have at least 2 protected fields
			</a>
		</li>
		<li>
			<a href="https://github.com/burningwave/core/wiki/How-search-for-all-classes-with-a-constructor-that-takes-a-specific-type-as-first-parameter-and-with-at-least-2-methods-that-begin-for-a-given-string">
			<b>USE CASE</b>: how search for all classes with a constructor that takes a specific type as first parameter and with at least 2 methods that begin for a given string
			</a>
		</li>
		<li>
			<a href="https://github.com/burningwave/core/wiki/Finding-all-classes-for-module-name-(Java-9-and-later)">
			<b>USE CASE</b>: finding all classes for module name (Java 9 and later)
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
	<summary><b>PropertyAccessor</b></summary>
	<ul>
		<li>
			<a href="https://github.com/burningwave/core/wiki/Getting-property-of-a-Java-bean-through-path">
			<b>USE CASE</b>: getting property of a Java bean through path
			</a>
		</li>
	</ul>
</details>

### [**Official site**](https://www.burningwave.org/)
### [**Help guide**](https://www.burningwave.org/forum/topic/help-guide/)
### [**Ask for assistance to Burningwave community**](https://www.burningwave.org/forum/forum/how-to/)
<a href="https://www.paypal.com/cgi-bin/webscr?cmd=_donations&business=EY4TMTW8SWDAC&item_name=Support+maintenance+and+improvement+of+Burningwave&currency_code=EUR&source=url" rel="nofollow"><img src="https://camo.githubusercontent.com/e14c85b542e06215f7e56c0763333ef1e9b9f9b7/68747470733a2f2f7777772e70617970616c6f626a656374732e636f6d2f656e5f55532f692f62746e2f62746e5f646f6e6174655f534d2e676966" alt="Donate" data-canonical-src="https://www.paypalobjects.com/en_US/i/btn/btn_donate_SM.gif" style="max-width:100%;"></a>
