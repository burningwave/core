package org.burningwave.core;

import static org.burningwave.core.assembler.StaticComponentContainer.JVMInfo;
import static org.burningwave.core.assembler.StaticComponentContainer.Modules;
import static org.burningwave.core.assembler.StaticComponentContainer.Resources;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SuppressWarnings("unchecked")
public class ModulesTest extends BaseTest {

	
	@Test
	@Order(1)
	public void exportPackageToAllUnnamedTest() {
		testDoesNotThrow(() -> {
			if (JVMInfo.getVersion() > 8) {
				Modules.exportPackageToAllUnnamed ("java.base", "java.net");
			    Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
			    method.setAccessible(true);
			    ClassLoader classLoader = new URLClassLoader(new URL[] {}, null);
			    method.invoke(
			    	classLoader,
			    	Resources.getClassPath(ModulesTest.class).getURL()
			    );
			    method.invoke(
			    	classLoader,
			    	Resources.getClassPath(Modules.getClass()).getURL()
			    );
			    classLoader.loadClass(ModulesTest.class.getName());
			}
		});
	}
	
	
	@Test
	@Order(10)
	public void exportAllToAllTest() {
		testDoesNotThrow(() -> {
			if (JVMInfo.getVersion() > 8) {
				Modules.exportAllToAll();
	    		Class<?> bootClassLoaderClass = Class.forName("jdk.internal.loader.ClassLoaders$BootClassLoader");
				Constructor<? extends ClassLoader> constructor = (Constructor<? extends ClassLoader>)
	    			Class.forName("jdk.internal.loader.ClassLoaders$PlatformClassLoader").getDeclaredConstructor(bootClassLoaderClass);
	    		constructor.setAccessible(true);
	    		Class<?> classLoadersClass = Class.forName("jdk.internal.loader.ClassLoaders");
	    		Method bootClassLoaderRetriever = classLoadersClass.getDeclaredMethod("bootLoader");
	    		bootClassLoaderRetriever.setAccessible(true);
	    		ClassLoader newBuiltinclassLoader = constructor.newInstance(bootClassLoaderRetriever.invoke(classLoadersClass));
	    		System.out.println(newBuiltinclassLoader + " instantiated");
			}
		});
	}
	
}
