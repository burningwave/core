package jdk.internal.loader;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.lang.reflect.Method;


public class ClassLoaderDelegate extends BuiltinClassLoader {
	private ClassLoader classLoader;
	private Method loadClassMethod;
	
	ClassLoaderDelegate(String name, BuiltinClassLoader parent, URLClassPath ucp) {
		super(name, parent, ucp);
	}
	
	public void init(ClassLoader classLoader) {
		this.classLoader = classLoader;
		Class<?> cls = classLoader.getClass();
		while (loadClassMethod == null && cls != ClassLoader.class.getSuperclass()) {
			try {
				loadClassMethod = cls.getDeclaredMethod("loadClass", String.class, boolean.class);
				loadClassMethod.setAccessible(true);
			} catch (NoSuchMethodException | SecurityException e) {
				
			}
			cls = cls.getSuperclass();
		}
	}
	
	@Override
	protected Class<?> loadClassOrNull(String className, boolean resolve) {
		try {
			return (Class<?>)loadClassMethod.invoke(classLoader, className, resolve);
		} catch (Throwable exc) {
			System.out.println("Class " + className + " not found");
			return null;
		}
	}
	
	@Override
	protected Class<?> loadClass(String className, boolean resolve) throws ClassNotFoundException {
		try {
			return (Class<?>)loadClassMethod.invoke(classLoader, className, resolve);
		} catch (Throwable exc) {
			System.out.println("Class " + className + " not found");
			return null;
		}
	}
	
	@Override
	public URL getResource(String name) {
		return classLoader.getResource(name);
	}
	
	@Override
	public Enumeration<URL> getResources(String name) throws IOException {
		return classLoader.getResources(name);
	}
	
    @Override
    public InputStream getResourceAsStream(String name) {
    	return classLoader.getResourceAsStream(name);
    }
}
