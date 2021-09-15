package org.burningwave.core.classes;

import static org.burningwave.core.assembler.StaticComponentContainer.Fields;
import static org.burningwave.core.assembler.StaticComponentContainer.Methods;
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("unchecked")
public class Modules {
	private Class<?> moduleClass;
	private Class<?> moduleLayerClass;
	private Set<?> everyOneSet = new HashSet<>();
	
	private Modules() {
		try {
			moduleClass = Class.forName("java.lang.Module");
			moduleLayerClass = Class.forName("java.lang.ModuleLayer");
			everyOneSet = new HashSet<>();
			everyOneSet.add(Fields.getStaticDirect(moduleClass, "ALL_UNNAMED_MODULE"));
			everyOneSet.add(Fields.getStaticDirect(moduleClass, "EVERYONE_MODULE"));
		} catch (Throwable exc) {
			Throwables.throwException(exc);
		}
	}
	
	public static Modules create() {
		return new Modules();
	}
	
	public void exportAllToAll() {
		try {
			Object moduleLayer = Methods.invokeStatic(moduleLayerClass, "boot");
			((Set<Object>)Methods.invoke(moduleLayer, "modules")).forEach(module -> {
				((Set<String>)Methods.invoke(module, "getPackages")).forEach(pkgName -> {
					addTo("exportedPackages", module, pkgName);
					addTo("openPackages", module, pkgName);
				});
			});
		} catch (Throwable exc) {
			Throwables.throwException(exc);
		}
	}
	
	void addTo(String fieldName, Object module, String pkgName) {
		Map<String, Set<?>> pckgForModule = Fields.getDirect(module, fieldName);
		if (pckgForModule == null) {
			pckgForModule = new HashMap<>();
			Fields.setDirect(module, fieldName, pckgForModule);
		}
		pckgForModule.put(pkgName, everyOneSet);
		if (fieldName.startsWith("exported")) {	
			Methods.invokeStatic(moduleClass, "addExportsToAllUnnamed0", module, pkgName);
			Methods.invokeStatic(moduleClass, "addExportsToAll0", module, pkgName);
		}
	}

	
}
