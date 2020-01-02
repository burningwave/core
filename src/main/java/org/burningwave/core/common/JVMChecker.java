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