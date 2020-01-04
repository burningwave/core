package org.burningwave.core.classes;

import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.burningwave.core.Component;
import org.burningwave.core.io.ByteBufferInputStream;
import org.burningwave.core.reflection.ObjectRetriever;


public class MemoryClassLoader extends ClassLoader implements Component {
	public final static String PARENT_CLASS_LOADER_SUPPLIER_IMPORTS_CONFIG_KEY = "memoryClassLoader.parent.supplier.imports";
	public final static String PARENT_CLASS_LOADER_SUPPLIER_CONFIG_KEY = "memoryClassLoader.parent";
	public final static Map<String, String> DEFAULT_CONFIG_VALUES = new LinkedHashMap<>();
		
	private ClassHelper classHelper;
	private ObjectRetriever objectRetriever;
	private Map<String, ByteBuffer> notLoadedCompiledClasses;
	private Map<String, ByteBuffer> loadedCompiledClasses;
	private Vector<Class<?>> definedClasses;
	private Map<String, ?> definedPackages;
	
	protected MemoryClassLoader(
		ClassLoader parentClassLoader,
		ClassHelper classHelper,
		ObjectRetriever objectRetriever) {
		super(parentClassLoader);
		this.classHelper = classHelper;
		this.objectRetriever = objectRetriever;
		definedClasses = this.objectRetriever.retrieveClasses(this);
		definedPackages = this.objectRetriever.retrievePackages(this);
		notLoadedCompiledClasses = new ConcurrentHashMap<>();
		loadedCompiledClasses = new ConcurrentHashMap<>();
	}
	
	static {
		DEFAULT_CONFIG_VALUES.put(MemoryClassLoader.PARENT_CLASS_LOADER_SUPPLIER_CONFIG_KEY, "null");
		DEFAULT_CONFIG_VALUES.put(MemoryClassLoader.PARENT_CLASS_LOADER_SUPPLIER_IMPORTS_CONFIG_KEY, "");
	}
	
	public static MemoryClassLoader create(ClassLoader parentClassLoader, ClassHelper classHelper, ObjectRetriever objectRetriever) {
		return new MemoryClassLoader(parentClassLoader, classHelper, objectRetriever);
	}


	public boolean isEmpty() {
		return definedClasses.isEmpty() && notLoadedCompiledClasses.isEmpty();
	}

	public void addCompiledClass(String name, ByteBuffer byteCode) {
    	if (getLoadedClass(name) == null) {
    		synchronized(definedClasses) {
				if (getLoadedClass(name) == null) {
    				notLoadedCompiledClasses.put(name, byteCode);
    			} else {
    				logWarn("Could not add compiled class {} cause it's already defined", name);
    			} 
			}
		} else {
			logWarn("Could not add compiled class {} cause it's already defined", name);
		} 
    }
    
    public Map.Entry<String, ByteBuffer> getNotLoadedCompiledClass(String name) {
    	for (Map.Entry<String, ByteBuffer> entry : notLoadedCompiledClasses.entrySet()){
    	    if (entry.getKey().equals(name)) {
    	    	return entry;
    	    }
    	}
    	return null;
    }
    
    public ByteBuffer getByteCodeOf(String name) {
    	return Optional.ofNullable(getNotLoadedCompiledClass(name)).map(entry -> entry.getValue()).orElse(null);
    }
    
    void addCompiledClasses(Map<String, ByteBuffer> classes) {
		Set<String> clsNames = classes.keySet();
		Iterator<String> clsNamesItr = clsNames.iterator();
		while (clsNamesItr.hasNext()) {
			String clsName = clsNamesItr.next();
			addCompiledClass(clsName, classes.get(clsName));
		}
    }
    
    public void addCompiledClasses(Collection<Entry<String, ByteBuffer>> classes) {
    	classes.forEach(classObject -> addCompiledClass(classObject.getKey(), classObject.getValue()));	
	} 

    
    @SuppressWarnings("unchecked")
	public void addCompiledClasses(Entry<String, ByteBuffer>... classes) {
    	Stream.of(classes).forEach(classObject -> addCompiledClass(classObject.getKey(), classObject.getValue()));	
	} 
    
	public boolean hasPackageBeenDefined(String packageName) {
		if (packageName != null) {
			return objectRetriever.retrievePackage(packageName, this) != null;
		} else {
			return true;
		}
	}
    
    @Override
    protected Package definePackage(String packageName, String specTitle,
		String specVersion, String specVendor, String implTitle,
		String implVersion, String implVendor, URL sealBase
	) throws IllegalArgumentException {
    	Package pkg = null;
    	if (packageName != null) {
    		pkg = objectRetriever.retrievePackage(packageName, this);
    		if (pkg == null) {
    			try {
    				pkg = super.definePackage(packageName, specTitle, specVersion, specVendor, implTitle,
    		    			implVersion, implVendor, sealBase);
    			} catch (IllegalArgumentException exc) {
    				logWarn("Package " + packageName + " already defined");
    				pkg = objectRetriever.retrievePackage(packageName, this);
    			}
    		}
    	}
    	return pkg;
    }
    
	protected void definePackageOf(Class<?> cls) {
		if (cls.getName().contains(".")) {
			String pckgName = cls.getName().substring(
		    	0, cls.getName().lastIndexOf(".")
		    );
		    if (objectRetriever.retrievePackage(pckgName, this) == null) {
		    	definePackage(pckgName, null, null, null, null, null, null, null);
			}	
		}
	}
    
    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
    	Class<?> cls = super.loadClass(name, resolve);
    	removeNotLoadedCompiledClass(name);
    	return cls;
    }
    
    
    public Class<?> loadOrUploadClass(Class<?> toLoad) throws ClassNotFoundException {
    	return classHelper.loadOrUploadClass(toLoad, this);
    }
    
    
    @Override
    public InputStream getResourceAsStream(String name) {
    	InputStream inputStream = super.getResourceAsStream(name);
    	if (inputStream == null && name.endsWith(".class")) {
    		ByteBuffer byteCode = loadedCompiledClasses.get(name.substring(0, name.lastIndexOf(".class")).replace("/", "."));
    		if (byteCode != null) {
	    		return new ByteBufferInputStream(
	    			byteCode
	    		);
    		}
    	}
    	return inputStream;
    }
    
    
    void addLoadedCompiledClass(String className, ByteBuffer byteCode) {
    	ByteBuffer compiledCode = loadedCompiledClasses.get(className);
    	if (compiledCode == null) {
    		synchronized (loadedCompiledClasses) {
				if((compiledCode = loadedCompiledClasses.get(className)) == null) {
					loadedCompiledClasses.put(className, byteCode);
				}
			}
    	}
    }
    
    
	@Override
    protected Class<?> findClass(String className) throws ClassNotFoundException {
		Class<?> cls = null;
		ByteBuffer byteCode = notLoadedCompiledClasses.get(className);
		if (byteCode != null) {
			synchronized (byteCode) {
				if ((byteCode = notLoadedCompiledClasses.get(className)) != null) {
					cls = getLoadedClass(className);
					if (cls == null) {
						try {
	                		cls = defineClass(className, byteCode, null);
	                		addLoadedCompiledClass(className, byteCode);
	                		definePackageOf(cls);
	                	} catch (NoClassDefFoundError noClassDefFoundError) {
	                		String notFoundClassName = noClassDefFoundError.getMessage().replace("/", ".");
	                		while (!notFoundClassName.equals(className)) {
	                			try {
	                				findClass(notFoundClassName);
	                				cls = findClass(className);
	                			} catch (ClassNotFoundException exc) {
	                				String newNotFoundClass = noClassDefFoundError.getMessage().replace("/", ".");
	                				if (newNotFoundClass.equals(notFoundClassName)) {
	                					break;
	                				} else {
	                					notFoundClassName = newNotFoundClass;
	                				}
	                			}
	                		}
	                		if (cls == null) {
	                			removeNotLoadedCompiledClass(className);
                				logWarn("Could not load compiled class " + className + ", so it will be removed: " + noClassDefFoundError.toString());
	                			throw noClassDefFoundError;
	                		}
	                	}
						if (cls == null){
	                		removeNotLoadedCompiledClass(className);
            				logWarn("Could not load compiled class " + className + ", so it will be removed");
	                	}
					}
				} else {
                	logWarn("Compiled class {} already defined");
                }
			}
		}
		if (cls != null) {
			return cls;
		} else {
			throw new ClassNotFoundException(className);
		}
	}



	public void removeNotLoadedCompiledClass(String name) {
		synchronized (notLoadedCompiledClasses) {
			notLoadedCompiledClasses.remove(name);
		}		
	}
	
	
	public Set<Class<?>> getClassesForPackage(Package pkg) {
		return getClassesForPackage(pkg.getName());
	}
	

	public Set<Class<?>> getClassesForPackage(String pkgName) {
		Set<Class<?>> toRet = new LinkedHashSet<Class<?>>();
		synchronized(definedClasses) {			
			Iterator<?> itr = definedClasses.iterator();
			while (itr.hasNext()) {
				Class<?> cls = (Class<?>)itr.next();
				if (cls.getName().startsWith(pkgName + ".")) {
					toRet.add(cls);
				}
			}
		}
		return toRet;
	}
	
	
	private Class<?> getLoadedClass(String name) {
		synchronized(definedClasses) {
			Iterator<?> itr = definedClasses.iterator();
			while(itr.hasNext()) {
				Class<?> cls = (Class<?>)itr.next();
				if (cls.getName().equals(name)) {
					return cls;
				}
			}
		}
		return null;
	}
	
	public void forceCompiledClassesLoading() {
		Set<String> compiledClassesNotLoaded = new LinkedHashSet<>(loadCompiledClassesNotLoaded());
		if (!compiledClassesNotLoaded.isEmpty()) {	
			Set<String> compiledClassesNotLoaded2 = new LinkedHashSet<>(loadCompiledClassesNotLoaded());
			if (!compiledClassesNotLoaded2.isEmpty() && !compiledClassesNotLoaded2.containsAll(compiledClassesNotLoaded)) {
				forceCompiledClassesLoading();
			}
		}
	}
	
	public Set<String> loadCompiledClassesNotLoaded() {
		for (Map.Entry<String, ByteBuffer> entry : notLoadedCompiledClasses.entrySet()){
			try {
				loadClass(entry.getKey());
			} catch (Throwable exc) {
				logWarn("Could not load class " + entry.getKey(), exc.getMessage());
			}
		}
		return notLoadedCompiledClasses.keySet();
	}
	
	public void clear () {
		notLoadedCompiledClasses.clear();
		loadedCompiledClasses.clear();
		definedClasses.clear();
		definedPackages.clear();
	}
	
	@Override
	public void close() {
		clear();
		objectRetriever.unregister(this);
		notLoadedCompiledClasses = null;
		loadedCompiledClasses = null;
		definedClasses = null;
		definedPackages = null;
		objectRetriever = null;
	}
}