package org.burningwave.core.reflection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Vector;
import java.util.function.Supplier;

import org.burningwave.Throwables;
import org.burningwave.core.Component;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.ClassHelper;
import org.burningwave.core.classes.MethodHelper;
import org.burningwave.core.common.Strings;
import org.burningwave.core.iterable.IterableObjectHelper;

public class ObjectRetriever implements Component {
	private PropertyAccessor.ByFieldOrByMethod byFieldOrByMethodPropertyAccessor;
	private MethodHelper methodHelper;
	private IterableObjectHelper iterableObjectHelper;
	private Supplier<ClassHelper> classHelperSupplier;
	private ClassHelper classHelper;

	
	private ObjectRetriever(
		Supplier<ClassHelper> classHelperSupplier,
		PropertyAccessor.ByFieldOrByMethod byFieldOrByMethodPropertyAccessor, 
		MethodHelper methodHelper, 
		IterableObjectHelper iterableObjectHelper
	) {
		this.classHelperSupplier = classHelperSupplier;
		this.byFieldOrByMethodPropertyAccessor = byFieldOrByMethodPropertyAccessor;
		this.methodHelper = methodHelper;
		this.iterableObjectHelper = iterableObjectHelper;
	}
	
	public static ObjectRetriever create(
		Supplier<ClassHelper> classHelperSupplier,
		PropertyAccessor.ByFieldOrByMethod byFieldOrByMethodPropertyAccessor,
		MethodHelper methodHelper,
		IterableObjectHelper iterableObjectHelper
	) {
		return new ObjectRetriever(classHelperSupplier, byFieldOrByMethodPropertyAccessor, methodHelper, iterableObjectHelper);
	}
	
	private ClassHelper getClassHelper() {
		return classHelper != null ?
			classHelper :
			(classHelper = classHelperSupplier.get());
	}
	
	public Vector<Class<?>> retrieveClasses(ClassLoader classLoader) {
		return byFieldOrByMethodPropertyAccessor.get(classLoader, "classes");
	}
	
	public Map<String, Package> retrievePackages(ClassLoader classLoader) {
		return byFieldOrByMethodPropertyAccessor.get(classLoader, "packages");
	}
	
	@SuppressWarnings("unchecked")
	public Package retrievePackage(String pkgNm, ClassLoader... classLoaders) {
		if (classLoaders == null || classLoaders.length == 0) {
			throw Throwables.toRuntimeException("classLoaders parameter must be valorized");
		}
		Supplier<Package>[] suppliers = new Supplier[classLoaders.length * 2];
		int idx = 0;
		for (ClassLoader classLoader : classLoaders) {
			suppliers[idx++] = () -> methodHelper.invoke(classLoader, "getPackage", pkgNm);
			suppliers[idx++] = () -> methodHelper.invoke(classLoader, "getDefinedPackage", pkgNm);
		}
		return retrieveByIntrospection(suppliers);
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

	
	@SuppressWarnings("unchecked")
	public <T> T retrieveByIntrospection(Supplier<T>... suppliers) {
		List<Throwable> exceptions = new ArrayList<>();
		for (Supplier<T> supplier : suppliers) {
			try {
				return supplier.get();
			} catch (Throwable exc) {
				exceptions.add(exc);
			}
		}
		if (exceptions.size() == suppliers.length) {
			StringBuffer exceptionsMessages = new StringBuffer();
			for (Throwable exc : exceptions) {
				exceptionsMessages.append(exc.getMessage());
			}
			throw Throwables.toRuntimeException(exceptionsMessages.toString());
		}
		return null;
	}
}
