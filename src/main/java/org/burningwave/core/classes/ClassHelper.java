package org.burningwave.core.classes;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.burningwave.Throwables;
import org.burningwave.core.Component;
import org.burningwave.core.Virtual;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.common.Streams;
import org.burningwave.core.common.Strings;
import org.burningwave.core.function.ThrowingSupplier;
import org.burningwave.core.io.ByteBufferInputStream;
import org.burningwave.core.reflection.ObjectRetriever;
import org.objectweb.asm.ClassReader;

public class ClassHelper implements Component {
	private ObjectRetriever objectRetriever;
	private ClassFactory classFactory;
	private Supplier<ClassFactory> classFactorySupplier;
	
	private ClassHelper(
		Supplier<ClassFactory> classFactorySupplier,
		ObjectRetriever objectRetriever
	) {
		this.classFactorySupplier = classFactorySupplier;
		this.objectRetriever = objectRetriever;
	}
	
	public static ClassHelper create(Supplier<ClassFactory> classFactorySupplier, ObjectRetriever objectRetriever) {
		return new ClassHelper(classFactorySupplier, objectRetriever);
	}
	
	private ClassFactory getClassFactory() {
		if (classFactory == null) {
			classFactory = classFactorySupplier.get();
		}
		return classFactory;
	}
	
	public String extractClassName(String classCode) {
		return
			Optional.ofNullable(
				Strings.extractAllGroups(
					Pattern.compile("(package)\\s*([[a-zA-Z0-9\\s]*\\.?]*)"), classCode
				).get(2).get(0)
			).map(
				value -> value + "."
			).orElse("") +
			Strings.extractAllGroups(
				Pattern.compile("(?<=\\n|\\A)(?:public\\s*)?(class|interface|enum)\\s*([^\\n\\s<]*)"), classCode
			).get(2).get(0);
	}
	
	public String extractClassName(ByteBuffer byteCode) {
		return ThrowingSupplier.get(() -> {
			try (ByteBufferInputStream inputStream = new ByteBufferInputStream(byteCode)){
				return new ClassReader(inputStream).getClassName().replace("/", ".");
			}
		});
	}
	
	public ByteBuffer getByteCode(Class<?> cls) {
		ClassLoader clsLoader = cls.getClassLoader();
		if (clsLoader == null) {
			clsLoader = ClassLoader.getSystemClassLoader();
		}
		InputStream inputStream = clsLoader.getResourceAsStream(
			cls.getName().replace(".", "/") + ".class"
		);
		return Streams.toByteBuffer(
			Objects.requireNonNull(inputStream, "Could not acquire bytecode for class " + cls.getName())
		);
	}
	
	public String extractClassName(InputStream inputStream) {
		return ThrowingSupplier.get(() -> 
			new ClassReader(inputStream).getClassName().replace("/", ".")
		);
	}

	
	public Class<?> loadOrUploadClass(
		Class<?> toLoad, 
		ClassLoader classLoader
	) throws ClassNotFoundException {
		return loadOrUploadClass(
			toLoad, classLoader,
			objectRetriever.findDefineClassMethod(classLoader), 
			objectRetriever.retrieveClasses(classLoader),
			objectRetriever.findDefinePackageMethod(classLoader)
		);
	}
	
	private Class<?> loadOrUploadClass(
		Class<?> toLoad, 
		ClassLoader classLoader, 
		Method defineClassMethod, 
		Collection<Class<?>> definedClasses,
		Method definePackageMethod
	) throws ClassNotFoundException {
    	ByteBuffer byteCode = getByteCode(toLoad);
    	String className = extractClassName(Streams.shareContent(byteCode));
    	Class<?> cls = null;
    	try {
    		cls = classLoader.loadClass(toLoad.getName());
    	} catch (ClassNotFoundException | NoClassDefFoundError outerEx) {
    		synchronized (definedClasses) {
    			try {
    				cls = classLoader.loadClass(toLoad.getName());
    			} catch (ClassNotFoundException | NoClassDefFoundError outerExc) {
		    		String notFoundClassName = outerExc.getMessage().replace("/", ".");
		    		if (className.equals(notFoundClassName)) {
		    			try {
		            		cls = defineClass(classLoader, defineClassMethod, className, Streams.shareContent(byteCode));
		            		definePackageFor(cls, classLoader, definePackageMethod);
		            	} catch (NoClassDefFoundError innerExc) {
		            		String newNotFoundClassName = innerExc.getMessage().replace("/", ".");
		            		loadOrUploadClass(
		            			Class.forName(
		            				newNotFoundClassName, false, toLoad.getClassLoader()
		            			),
		            			classLoader, defineClassMethod, definedClasses, definePackageMethod
		            		);
		            		cls = defineClass(classLoader, defineClassMethod, className, Streams.shareContent(byteCode));
		            		definePackageFor(cls, classLoader, definePackageMethod);
		            	}
		    		} else {
		    			loadOrUploadClass(
		    				Class.forName(
		    					notFoundClassName, false, toLoad.getClassLoader()
		    				),
		    				classLoader, defineClassMethod, definedClasses, definePackageMethod
		    			);
		    			cls = defineClass(classLoader, defineClassMethod, className, byteCode);
		        		definePackageFor(cls, classLoader, definePackageMethod);
		    		}
    			}
    		}
    	}	
     	return cls;
    }

	private Class<?> defineClass(
		ClassLoader classLoader, 
		Method method, 
		String className,
		ByteBuffer byteCode
	) throws ClassNotFoundException {
		try {
			method.setAccessible(true);
			Class<?> cls = (Class<?>)method.invoke(classLoader, className, byteCode, null);
			if (classLoader instanceof MemoryClassLoader) {
				((MemoryClassLoader)classLoader).addLoadedCompiledClass(className, byteCode);
			}
			return cls;
		} catch (InvocationTargetException iTE) {
			Throwable targetExcption = iTE.getTargetException();
			if (targetExcption instanceof ClassNotFoundException) {
				throw (ClassNotFoundException)iTE.getTargetException();
			} else if (targetExcption instanceof NoClassDefFoundError) {
				throw (NoClassDefFoundError)iTE.getTargetException();
			}
			throw Throwables.toRuntimeException(iTE);
		} catch (Throwable exc) {
			throw Throwables.toRuntimeException(exc);
		}
	}
	
	
    private Package definePackage(
		String name, String specTitle,
		String specVersion, String specVendor, String implTitle,
		String implVersion, String implVendor, URL sealBase,
		ClassLoader classLoader,
		Method definePackageMethod
	) throws IllegalArgumentException {
    	return ThrowingSupplier.get(() -> {
    		try {
    			definePackageMethod.setAccessible(true);
    			return (Package) definePackageMethod.invoke(classLoader, name, specTitle, specVersion, specVendor, implTitle,
    				implVersion, implVendor, sealBase);
    		} catch (IllegalArgumentException exc) {
    			logWarn("Package " + name + " already defined");
    			return objectRetriever.retrievePackage(name, classLoader);
    		}
		});
    }
    
	private void definePackageFor(Class<?> cls, 
		ClassLoader classLoader,
		Method definePackageMethod
	) {
		if (cls.getName().contains(".")) {
			String pckgName = cls.getName().substring(
		    	0, cls.getName().lastIndexOf(".")
		    );
		    Package pkg = objectRetriever.retrievePackage(pckgName, classLoader);
		    if (pkg == null) {
		    	pkg = definePackage(pckgName, null, null, null, null, null, null, null, classLoader, definePackageMethod);
			}	
		}
	}
	
	public <T> T executeCode(
		String imports, 
		String className, 
		String supplierCode, 
		Class<?> returnedClass,
		ComponentSupplier componentSupplier
	) {	
		return executeCode(imports, className, supplierCode, returnedClass, componentSupplier, Thread.currentThread().getContextClassLoader());
	}
	
	
	public <T> T executeCode(
		String imports, 
		String className, 
		String supplierCode, 
		Class<?> returnedClass,
		ComponentSupplier componentSupplier,
		ClassLoader classLoader
	) {	
		return ThrowingSupplier.get(() -> {
			try (MemoryClassLoader memoryClassLoader = 
				MemoryClassLoader.create(
					classLoader,
					this, 
					objectRetriever
				)
			) {
				Class<?> virtualClass = getClassFactory().getOrBuildCodeExecutorSubType(
					imports, className, supplierCode, returnedClass, componentSupplier, memoryClassLoader
				);
				Virtual virtualObject = ((Virtual)virtualClass.getDeclaredConstructor().newInstance());
				T retrievedElement = virtualObject.invokeWithoutCachingMethod("execute", componentSupplier);
				return retrievedElement;
			}
		});
	}
	
	@Override
	public void close() {
		objectRetriever = null;
		classFactory = null;
		classFactorySupplier = null;
	}
}
