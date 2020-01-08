/*
 * This file is part of Burningwave Core.
 *
 * Author: Roberto Gentli
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
package org.burningwave.core.common;

import java.lang.reflect.Method;

public class JVMChecker {

    private static final String OS_ARCH = System.getProperty("os.arch");
    private static final boolean JRE_IS_64BIT;
    private static final boolean JRE_IS_64BIT_HOTSPOT;
    private static final boolean JRE_IS_32BIT;
    private final static boolean COMPRESSED_REFS_ENABLED;
    private static final String MANAGEMENT_FACTORY_CLASS = "java.lang.management.ManagementFactory";
    private static final String HOTSPOT_BEAN_CLASS = "com.sun.management.HotSpotDiagnosticMXBean";

    static {
        boolean is64Bit = false;
        boolean is32Bit = false;
        final String x = System.getProperty("sun.arch.data.model");
        if (x != null) {
            is64Bit = x.contains("64");
            is32Bit = x.contains("32");
        } else {
            if (OS_ARCH != null && OS_ARCH.contains("64")) {
                is64Bit = true;
            } else {
                is64Bit = false;
            }
        }
        boolean compressedOops = false;
        boolean is64BitHotspot = false;

        if (is64Bit) {
            try {
                final Class<?> beanClazz = Class.forName(HOTSPOT_BEAN_CLASS);
                final Object hotSpotBean = Class.forName(MANAGEMENT_FACTORY_CLASS).getMethod("getPlatformMXBean", Class.class)
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
        JRE_IS_64BIT = is64Bit;
        JRE_IS_64BIT_HOTSPOT = is64BitHotspot;
        JRE_IS_32BIT = is32Bit;
        COMPRESSED_REFS_ENABLED = compressedOops;
    }

    public static boolean isCompressedOopsOffOn64Bit() {
        return JRE_IS_64BIT_HOTSPOT && !COMPRESSED_REFS_ENABLED;
    }

    public static boolean is32Bit() {
    	return JRE_IS_32BIT;
    }
    
    public static boolean is64Bit() {
    	return JRE_IS_64BIT;
    }
    
    public static void main(final String[] args) {
        System.out.println("Is 64bit Hotspot JVM: " + JRE_IS_64BIT_HOTSPOT);
        System.out.println("Compressed Oops enabled: " + COMPRESSED_REFS_ENABLED);
        System.out.println("isCompressedOopsOffOn64Bit: " + isCompressedOopsOffOn64Bit());

    }

}