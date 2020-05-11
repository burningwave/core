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
 * Copyright (c) 2019 Roberto Gentili
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
package org.burningwave.core.jvm;

import java.lang.reflect.Method;

import org.burningwave.core.Component;

public class JVMInfo implements Component {

    private final String osArch;
    private boolean is64Bit;
    private boolean is64BitHotspot;
    private boolean is32Bit;
    private boolean compressedRefsEnabled;
    private final String managementFactoryClass;
    private final String hotSpotBeanClass;
    private int version;
    
    public JVMInfo() {
    	osArch = System.getProperty("os.arch");
    	managementFactoryClass = "java.lang.management.ManagementFactory";
    	hotSpotBeanClass = "com.sun.management.HotSpotDiagnosticMXBean";
    	init();
    }
    
    public static JVMInfo create() {
    	return new JVMInfo();
    }
    
    private void init() {
    	String version = System.getProperty("java.version");
        if(version.startsWith("1.")) {
        	version = version.substring(2, 3);
        } else {
        	int separatorIdx = version.indexOf(".");
        	if(separatorIdx != -1) {
        		version = version.substring(0, separatorIdx);
        	} else {
        		separatorIdx = version.indexOf("-");
        		if(separatorIdx != -1) {
            		version = version.substring(0, separatorIdx);
            	}
        	}
        }
        this.version = Integer.parseInt(version);
        boolean is64Bit = false;
        boolean is32Bit = false;
        final String x = System.getProperty("sun.arch.data.model");
        if (x != null) {
            is64Bit = x.contains("64");
            is32Bit = x.contains("32");
        } else {
            if (osArch != null && osArch.contains("64")) {
                is64Bit = true;
            } else {
                is64Bit = false;
            }
        }
        boolean compressedOops = false;
        boolean is64BitHotspot = false;

        if (is64Bit) {
            try {
                final Class<?> beanClazz = Class.forName(hotSpotBeanClass);
                final Object hotSpotBean = Class.forName(managementFactoryClass).getMethod("getPlatformMXBean", Class.class)
                        .invoke(null, beanClazz);
                if (hotSpotBean != null) {
                    is64BitHotspot = true;
                    final Method getVMOptionMethod = beanClazz.getMethod("getVMOption", String.class);
                    try {
                        final Object vmOption = getVMOptionMethod.invoke(hotSpotBean, "UseCompressedOops");
                        compressedOops = Boolean.parseBoolean(vmOption.getClass().getMethod("getValue").invoke(vmOption).toString());
                    } catch (ReflectiveOperationException | RuntimeException e) {
                        is64BitHotspot = false;
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                is64BitHotspot = false;
            }
        }
        this.is64Bit = is64Bit;
        this.is64BitHotspot = is64BitHotspot;
        this.is32Bit = is32Bit;
        this.compressedRefsEnabled = compressedOops;
    }

    public boolean isCompressedOopsOffOn64BitHotspot() {
        return is64BitHotspot && !compressedRefsEnabled;
    }
    
    public boolean isCompressedOopsOffOn64Bit() {
        return is64Bit && !compressedRefsEnabled;
    }

    public boolean is32Bit() {
    	return is32Bit;
    }
    
    public boolean is64Bit() {
    	return is64Bit;
    }
    
    public int getVersion() {
    	return version;
    }
}