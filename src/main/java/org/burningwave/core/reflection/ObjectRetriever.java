package org.burningwave.core.reflection;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.ByteBuffer;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.burningwave.Throwables;
import org.burningwave.core.Component;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.ClassHelper;
import org.burningwave.core.classes.MemberFinder;
import org.burningwave.core.classes.MethodCriteria;
import org.burningwave.core.common.JVMChecker;
import org.burningwave.core.common.Strings;
import org.burningwave.core.function.TriFunction;
import org.burningwave.core.iterable.IterableObjectHelper;

import sun.misc.Unsafe;

@SuppressWarnings("restriction")
public class ObjectRetriever implements Component {
	private IterableObjectHelper iterableObjectHelper;
	private Supplier<ClassHelper> classHelperSupplier;
	private ClassHelper classHelper;
	private MemberFinder memberFinder;
	private Map<ClassLoader, Vector<Class<?>>> classLoadersClasses;
	private Map<ClassLoader, Map<String, ?>> classLoadersPackages;
	private Predicate<Object> packageMapTester;
	private TriFunction<ClassLoader, Object, String, Package> packageRetriever;
	private Unsafe unsafe;
	
	private ObjectRetriever(
		Supplier<ClassHelper> classHelperSupplier,
		MemberFinder memberFinder,
		IterableObjectHelper iterableObjectHelper
	) {	
		try {
			Field theUnsafeField = Unsafe.class.getDeclaredField("theUnsafe");
			theUnsafeField.setAccessible(true);
			this.unsafe = (Unsafe)theUnsafeField.get(null);
		} catch (Throwable exc) {
			Throwables.toRuntimeException(exc);
		}
		this.classHelperSupplier = classHelperSupplier;
		this.memberFinder = memberFinder;
		this.iterableObjectHelper = iterableObjectHelper;
		this.classLoadersClasses = new ConcurrentHashMap<>();
		this.classLoadersPackages = new ConcurrentHashMap<>();
		try {
			Class.forName("java.lang.NamedPackage");
			packageMapTester = (object) -> object != null && object instanceof ConcurrentHashMap;
		} catch (ClassNotFoundException e) {
			packageMapTester = (object) -> object != null && object instanceof HashMap;
		}
		if (findGetDefinedPackageMethod() == null) {
			packageRetriever = (classLoader, object, packageName) -> (Package)object;
		} else {
			packageRetriever = (classLoader, object, packageName) -> getClassHelper().getClassLoaderDelegate("ForJDKVersionLaterThan8").getPackage(classLoader, packageName);
		}
	}
	
	public static ObjectRetriever create(
		Supplier<ClassHelper> classHelperSupplier,
		MemberFinder memberFinder,
		IterableObjectHelper iterableObjectHelper
	) {
		return new ObjectRetriever(classHelperSupplier, memberFinder, iterableObjectHelper);
	}
	
	private ClassHelper getClassHelper() {
		return classHelper != null ?
			classHelper :
			(classHelper = classHelperSupplier.get());
	}
	
	public Unsafe getUnsafe() {
		return this.unsafe;
	}
	
	public Method findDefinePackageMethodAndMakeItAccesible(ClassLoader classLoader) {
		Method method = memberFinder.findAll(
			MethodCriteria.byScanUpTo((cls) -> 
				cls.getName().equals(ClassLoader.class.getName())
			).name(
				"definePackage"::equals
			).and().parameterTypesAreAssignableFrom(
				String.class, String.class, String.class, String.class,
				String.class, String.class, String.class, URL.class
			),
			classLoader
		).stream().findFirst().orElse(null);
		method.setAccessible(true);
		return method;
	}
	
	public Method findDefineClassMethodAndMakeItAccesible(ClassLoader classLoader) {
		Method method = memberFinder.findAll(
			MethodCriteria.byScanUpTo((cls) -> cls.getName().equals(ClassLoader.class.getName())).name(
				"defineClass"::equals
			).and().parameterTypes(params -> 
				params.length == 3
			).and().parameterTypesAreAssignableFrom(
				String.class, ByteBuffer.class, ProtectionDomain.class
			).and().returnType((cls) -> cls.getName().equals(Class.class.getName())),
			classLoader
		).stream().findFirst().orElse(null);
		method.setAccessible(true);
		return method;
	}
	
	public Method findGetDefinedPackageMethod() {
		Method method = memberFinder.findAll(
			MethodCriteria.byScanUpTo((cls) -> cls.getName().equals(ClassLoader.class.getName())).name(
				"getDefinedPackage"::equals
			).and().parameterTypes(params -> 
				params.length == 1
			).and().parameterTypesAreAssignableFrom(
				String.class
			).and().returnType((cls) -> cls.getName().equals(Package.class.getName())),
			ClassLoader.class
		).stream().findFirst().orElse(null);
		return method;
	}
	
	@SuppressWarnings({ "unchecked" })
	public Vector<Class<?>> retrieveClasses(ClassLoader classLoader) {
		Vector<Class<?>> classes = classLoadersClasses.get(classLoader);
		if (classes != null) {
			return classes;
		} else {
			classes = classLoadersClasses.get(classLoader);
			if (classes == null) {
				synchronized (classLoadersClasses) {
					classes = classLoadersClasses.get(classLoader);
					if (classes == null) {
						classes = (Vector<Class<?>>)iterateClassLoaderFields(classLoader, (object) -> object instanceof Vector);
						classLoadersClasses.put(classLoader, classes);
						return classes;
					}
				}
			}
		}
		throw Throwables.toRuntimeException("Could not find classes Vector on " + classLoader);
	}
	
	@SuppressWarnings({ "unchecked" })
	public Map<String, ?> retrievePackages(ClassLoader classLoader) {
		Map<String, ?> packages = classLoadersPackages.get(classLoader);
		if (packages != null) {
			return packages;
		} else {
			packages = classLoadersPackages.get(classLoader);
			if (packages == null) {
				synchronized (classLoadersPackages) {
					packages = classLoadersPackages.get(classLoader);
					if (packages == null) {
						packages = (Map<String, Package>)iterateClassLoaderFields(
							classLoader, packageMapTester
						);
						classLoadersPackages.put(classLoader, packages);
						return packages;
					}
				}
			}
		}
		throw Throwables.toRuntimeException("Could not find packages Map on " + classLoader);
	}
	

	protected Object iterateClassLoaderFields(ClassLoader classLoader, Predicate<Object> predicate) {
		long offset;
		long step;
		if (JVMChecker.is32Bit()) {
			offset = 8;
			step = 4;
		} else if (!JVMChecker.isCompressedOopsOffOn64Bit()) {
			offset = 12;
			step = 4;
		} else {
			offset = 16;
			step = 8;
		}
		while (true) {
			Object object = unsafe.getObject(classLoader, offset);
			//logDebug(offset + " " + object);
			if (predicate.test(object)) {
				return object;
			}
			offset+=step;
		}
	}
	
	public Package retrievePackage(String pkgNm, ClassLoader classLoader) {
		Map<String, ?> packages = retrievePackages(classLoader);
		Object pckgToFind = packages.get(pkgNm);
		if (pckgToFind != null) {
			return packageRetriever.apply(classLoader, pckgToFind, pkgNm);
		} else if (classLoader.getParent() != null) {
			return retrievePackage(pkgNm, classLoader.getParent());
		} else {
			return null;
		}
	}	

	
	public <T> T retrieveFromProperties(
		Properties config, 
		String supplierImportsKey, 
		String supplierCodeKey,
		Map<String, String> defaultValues,
		Class<?> returnedClass, 
		ComponentSupplier componentSupplier
	) {	
		String supplierCode = iterableObjectHelper.get(config, supplierCodeKey, defaultValues);
		supplierCode = supplierCode.contains("return")?
			supplierCode:
			"return " + supplierCode + ";";
		String importFromConfig = iterableObjectHelper.get(config, supplierImportsKey, defaultValues);
		if (Strings.isNotEmpty(importFromConfig)) {
			final StringBuffer stringBufferImports = new StringBuffer();
			Arrays.stream(importFromConfig.split(";")).forEach(imp -> {
				stringBufferImports.append("import ").append(imp).append(";\n");
			});
			importFromConfig = stringBufferImports.toString();
		}
		String imports =
			"import " + ComponentSupplier.class.getName() + ";\n" +
			"import " + componentSupplier.getClass().getName() + ";\n" + importFromConfig;
		String className = returnedClass.getSimpleName() + "Supplier";
		return getClassHelper().executeCode(imports, className, supplierCode, returnedClass, componentSupplier);
	}
	
	public void unregister(ClassLoader classLoader) {
		classLoadersClasses.remove(classLoader);
		classLoadersPackages.remove(classLoader);
	}
	
	@Override
	public void close() {
		Component.super.close();
		this.classLoadersClasses.clear();
		this.classLoadersClasses = null;
		this.classLoadersPackages.clear();
		this.classLoadersPackages = null;
		this.iterableObjectHelper = null;
		this.classHelperSupplier = null;
		this.classHelper = null;
		this.memberFinder = null;
		this.packageMapTester = null;
		this.packageRetriever = null;
		this.unsafe = null;
	}
	
}
