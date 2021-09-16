package org.burningwave.core;

import static org.burningwave.core.assembler.StaticComponentContainer.JVMInfo;
import static org.burningwave.core.assembler.StaticComponentContainer.Modules;
import static org.burningwave.core.assembler.StaticComponentContainer.Resources;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

import org.junit.jupiter.api.Test;

public class ModulesTest extends BaseTest {

	
	@Test
	public void testExportAllToAll() {
		testDoesNotThrow(() -> {
			if (JVMInfo.getVersion() > 8) {
				Modules.exportAllToAll();
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
	
}
