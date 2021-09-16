/*
 * This file is part of Burningwave Core.
 *
 * Author: Roberto Gentili
 *
 * Hosted at: https://github.com/burningwave/core
 *
 * --
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2019-2021 Roberto Gentili
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without
 * limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
 * EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
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
			Object moduleLayer = Methods.invokeStaticDirect(moduleLayerClass, "boot");
			((Set<Object>)Methods.invokeDirect(moduleLayer, "modules")).forEach(module -> {
				((Set<String>)Methods.invokeDirect(module, "getPackages")).forEach(pkgName -> {
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
			Methods.invokeStaticDirect(moduleClass, "addExportsToAllUnnamed0", module, pkgName);
			Methods.invokeStaticDirect(moduleClass, "addExportsToAll0", module, pkgName);
		}
	}

	
}
