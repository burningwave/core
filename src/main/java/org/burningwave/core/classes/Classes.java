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
package org.burningwave.core.classes;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.function.Function;

import org.burningwave.ManagedLogger;
import org.burningwave.core.Cache;
import org.burningwave.core.jvm.LowLevelObjectsHandler;

public class Classes {
	public static class Symbol{
		public static class Tag {
			static final byte UTF8 = 1;
			static final byte INTEGER = 3;
			static final byte FLOAT = 4;
			static final byte LONG = 5;
			static final byte DOUBLE = 6;
			static final byte CLASS = 7;
			static final byte STRING = 8;
			static final byte FIELD_REF = 9;
			static final byte METHOD_REF = 10;
			static final byte INTERFACE_METHOD_REF = 11;
			static final byte NAME_AND_TYPE = 12;
			static final byte METHOD_HANDLE = 15;
			static final byte METHOD_TYPE = 16;
			static final byte DYNAMIC = 17;
			static final byte INVOKE_DYNAMIC = 18;
			static final byte MODULE = 19;
			static final byte PACKAGE = 20;

	    }		
	}

	private static final int V15;
	
	private static final Field[] EMPTY_FIELDS_ARRAY;
	private static final Method[] EMPTY_METHODS_ARRAY;
	private static final Constructor<?>[] EMPTY_CONSTRUCTORS_ARRAY;
	
	static {
		V15 = 0 << 16 | 59;
		EMPTY_FIELDS_ARRAY = new Field[]{};
		EMPTY_METHODS_ARRAY = new Method[]{};
		EMPTY_CONSTRUCTORS_ARRAY = new Constructor<?>[]{};
	}

	@SuppressWarnings({ "unchecked"})
	public static <T> Class<T> retrieveFrom(Object object) {
		return (Class<T>)(object instanceof Class? object : object.getClass());
	}

	public static Class<?>[] retrieveFrom(Object... objects) {
		Class<?>[] classes = null;
		if (objects != null) {
			classes = new Class[objects.length];
			for (int i = 0; i < objects.length; i++) {
				if (objects[i] != null) {
					classes[i] = retrieveFrom(objects[i]);
				}
			}
		}
		return classes;
	}
	
	public static String retrieveName(Throwable exc) {
		String className = exc.getMessage();
		if (className != null) {
			if (className.contains("Could not initialize class ")) {
				className = className.replace("Could not initialize class ", "");
			}
			if (className.contains("NoClassDefFoundError: ")) {
				className = className.substring(className.lastIndexOf("NoClassDefFoundError: ") + "NoClassDefFoundError: ".length());
			}
			if (className.contains("class: ")) {
				className = className.substring(className.lastIndexOf("class: ") + "class: ".length());
			}
			return className.contains(" ")? null : className.replace("/", ".");
		}
		return className;
	}
	
	public static Collection<String> retrieveNames(Throwable exc) {
		Collection<String> classesName = new LinkedHashSet<>();
		Optional.ofNullable(retrieveName(exc)).map(classesName::add);
		if (exc.getCause() != null) {
			classesName.addAll(retrieveNames(exc.getCause()));
		}
		return classesName;
	}
	
	public static String retrieveName(ByteBuffer classFileBuffer) {
		return retrieveName(classFileBuffer, true);
	}
	
	public static String retrieveName(byte[] classFileBuffer) {
		return retrieveName(classFileBuffer, true);
	}
	
	public static String retrieveName(
		final byte[] classFileBuffer,
		final boolean checkClassVersion
	) {
		return retrieveName((index) -> classFileBuffer[index], checkClassVersion);
	}
	
	public static String retrieveName(
		final ByteBuffer classFileBuffer,
		final boolean checkClassVersion
	) {
		return retrieveName(classFileBuffer::get, checkClassVersion);
	}
	
	private static String retrieveName(
		final Function<Integer, Byte> byteSupplier,
		final boolean checkClassVersion
	) {
		int classFileOffset = 0;
		if (checkClassVersion && readShort(byteSupplier, classFileOffset + 6) > V15) {
			throw new IllegalArgumentException(
					"Unsupported class file major version " + readShort(byteSupplier, classFileOffset + 6));
		}
		int constantPoolCount = readUnsignedShort(byteSupplier, classFileOffset + 8);
		int[] cpInfoOffsets = new int[constantPoolCount];
		String[] constantUtf8Values = new String[constantPoolCount];
		int currentCpInfoIndex = 1;
		int currentCpInfoOffset = classFileOffset + 10;
		int currentMaxStringLength = 0;
		while (currentCpInfoIndex < constantPoolCount) {
			cpInfoOffsets[currentCpInfoIndex++] = currentCpInfoOffset + 1;
			int cpInfoSize;
			byte currentCpInfoValue = byteSupplier.apply(currentCpInfoOffset);
			if (currentCpInfoValue == Symbol.Tag.INTEGER ||
				currentCpInfoValue == Symbol.Tag.FLOAT ||
				currentCpInfoValue == Symbol.Tag.FIELD_REF ||
				currentCpInfoValue == Symbol.Tag.METHOD_REF ||
				currentCpInfoValue == Symbol.Tag.INTERFACE_METHOD_REF ||
				currentCpInfoValue == Symbol.Tag.NAME_AND_TYPE ||
				currentCpInfoValue == Symbol.Tag.DYNAMIC ||
				currentCpInfoValue == Symbol.Tag.INVOKE_DYNAMIC
			) {
				cpInfoSize = 5;
			} else if (currentCpInfoValue == Symbol.Tag.LONG ||
				currentCpInfoValue == Symbol.Tag.DOUBLE
			) {
				cpInfoSize = 9;
				currentCpInfoIndex++;
			} else if (currentCpInfoValue == Symbol.Tag.UTF8) {
				cpInfoSize = 3 + readUnsignedShort(byteSupplier, currentCpInfoOffset + 1);
				if (cpInfoSize > currentMaxStringLength) {
					currentMaxStringLength = cpInfoSize;
				}
			} else if (currentCpInfoValue == Symbol.Tag.METHOD_HANDLE) {
				cpInfoSize = 4;
			} else if (currentCpInfoValue == Symbol.Tag.CLASS ||
				currentCpInfoValue == Symbol.Tag.STRING ||
				currentCpInfoValue == Symbol.Tag.METHOD_TYPE ||
				currentCpInfoValue == Symbol.Tag.MODULE ||
				currentCpInfoValue == Symbol.Tag.PACKAGE			
			) {
				cpInfoSize = 3;
			} else {
				throw new IllegalArgumentException();
			}
			currentCpInfoOffset += cpInfoSize;
		}
		int maxStringLength = currentMaxStringLength;
		int header = currentCpInfoOffset;
		return readUTF8(
			byteSupplier, 
			cpInfoOffsets[readUnsignedShort(byteSupplier, header + 2)], new char[maxStringLength], constantUtf8Values, cpInfoOffsets
		);
	}

	private static String readUTF8(
		Function<Integer, Byte> byteSupplier,
		final int offset,
		final char[] charBuffer,
		String[] constantUtf8Values,
		int[] cpInfoOffsets
	) {
		int constantPoolEntryIndex = readUnsignedShort(byteSupplier, offset);
		if (offset == 0 || constantPoolEntryIndex == 0) {
			return null;
		}
		return readUtf(byteSupplier, constantPoolEntryIndex, charBuffer, constantUtf8Values, cpInfoOffsets);
	}

	private static String readUtf(
		Function<Integer, Byte> byteSupplier,
		final int constantPoolEntryIndex,
		final char[] charBuffer,
		String[] constantUtf8Values,
		int[] cpInfoOffsets
	) {
		String value = constantUtf8Values[constantPoolEntryIndex];
		if (value != null) {
			return value;
		}
		int cpInfoOffset = cpInfoOffsets[constantPoolEntryIndex];
		return constantUtf8Values[constantPoolEntryIndex] = readUtf(byteSupplier, cpInfoOffset + 2, readUnsignedShort(byteSupplier, cpInfoOffset),
				charBuffer);
	}

	private static int readUnsignedShort(
		Function<Integer, Byte> byteSupplier,
		final int offset
	) {
		return ((byteSupplier.apply(offset) & 0xFF) << 8) | (byteSupplier.apply(offset + 1) & 0xFF);
	}

	private static String readUtf(Function<Integer, Byte> byteSupplier, final int utfOffset, final int utfLength, final char[] charBuffer) {
		int currentOffset = utfOffset;
		int endOffset = currentOffset + utfLength;
		int strLength = 0;
		while (currentOffset < endOffset) {
			int currentByte = byteSupplier.apply(currentOffset++);
			if ((currentByte & 0x80) == 0) {
				charBuffer[strLength++] = (char) (currentByte & 0x7F);
			} else if ((currentByte & 0xE0) == 0xC0) {
				charBuffer[strLength++] = (char) (((currentByte & 0x1F) << 6) + (byteSupplier.apply(currentOffset++) & 0x3F));
			} else {
				charBuffer[strLength++] = (char) (((currentByte & 0xF) << 12)
						+ ((byteSupplier.apply(currentOffset++) & 0x3F) << 6) + (byteSupplier.apply(currentOffset++) & 0x3F));
			}
		}
		return new String(charBuffer, 0, strLength);
	}

	private static short readShort(Function<Integer, Byte> byteSupplier, final int offset) {
		return (short) (((byteSupplier.apply(offset) & 0xFF) << 8) | (byteSupplier.apply(offset + 1) & 0xFF));
	}

	public static boolean isLoadedBy(Class<?> cls, ClassLoader classLoader) {
		if (cls.getClassLoader() == classLoader) {
			return true;
		} else if (classLoader != null && classLoader.getParent() != null) {
			return isLoadedBy(cls, classLoader.getParent());
		} else {
			return false;
		}
	}
	
	public static Field[] getDeclaredFields(Class<?> cls)  {
		return Cache.CLASS_LOADER_FOR_FIELDS.getOrDefault(
			cls.getClassLoader(), cls.getName().replace(".", "/"),
			() -> {
				try {
					return (Field[])LowLevelObjectsHandler.GET_DECLARED_FIELDS_RETRIEVER.invoke(cls, false);
				} catch (Throwable exc) {
					ManagedLogger.Repository.getInstance().logWarn(Classes.class, "Could not retrieve fields of class {}. Cause: {}", cls.getName(), exc.getMessage());
					return EMPTY_FIELDS_ARRAY;
				}
			}
		);
		
	}
	
	public static Method[] getDeclaredMethods(Class<?> cls)  {
		return Cache.CLASS_LOADER_FOR_METHODS.getOrDefault(
			cls.getClassLoader(), cls.getName().replace(".", "/"),
			() -> {
				try {
					return (Method[]) LowLevelObjectsHandler.GET_DECLARED_METHODS_RETRIEVER.invoke(cls, false);
				} catch (Throwable exc) {
					ManagedLogger.Repository.getInstance().logWarn(Classes.class, "Could not retrieve methods of class {}. Cause: {}", cls.getName(), exc.getMessage());
					return EMPTY_METHODS_ARRAY;
				}
			}
		);
	}
	
	public static Constructor<?>[] getDeclaredConstructors(Class<?> cls)  {
		return Cache.CLASS_LOADER_FOR_CONSTRUCTORS.getOrDefault(
			cls.getClassLoader(), cls.getName().replace(".", "/"),
			() -> {
				try {
					return (Constructor<?>[])LowLevelObjectsHandler.GET_DECLARED_CONSTRUCTORS_RETRIEVER.invoke(cls, false);
				} catch (Throwable exc) {
					ManagedLogger.Repository.getInstance().logWarn(Classes.class, "Could not retrieve constructors of class {}. Cause: {}", cls.getName(), exc.getMessage());
					return EMPTY_CONSTRUCTORS_ARRAY;
				}
			}
		);
	}
	
	public static Collection<ClassLoader> getAllParents(ClassLoader classLoader) {
		return getClassLoaderHierarchy(classLoader, false);
	}
	
	public static Collection<ClassLoader> getClassLoaderHierarchy(ClassLoader classLoader) {
		return getClassLoaderHierarchy(classLoader, true);
	}
	
	private static Collection<ClassLoader> getClassLoaderHierarchy(ClassLoader classLoader, boolean includeClassLoader) {
		Collection<ClassLoader> classLoaders = new LinkedHashSet<>();
		if (includeClassLoader) {
			classLoaders.add(classLoader);
		}
		while ((classLoader = getParent(classLoader)) != null) {
			classLoaders.add(classLoader);
		}
		return classLoaders;
	}
	
	public static Function<Boolean, ClassLoader> setAsParent(ClassLoader classLoader, ClassLoader futureParent, boolean mantainHierarchy) {
		return LowLevelObjectsHandler.setParent(classLoader, futureParent, mantainHierarchy);
	}
	
	public static ClassLoader getParent(ClassLoader classLoader) {
		return LowLevelObjectsHandler.getParent(classLoader);
	}

	public static void setAccessible(AccessibleObject object, boolean flag) {
		LowLevelObjectsHandler.setAccessible(object, flag);
	}
	
	public static String getId(Object object) {
        return object.getClass().getName() + "@" + object.hashCode();
    }
	 
	public static String getStringForSync(Object... objects) {
		String id = "_";
		for (Object object : objects) {
			id += getId(object) + "_";
		}
		return id;
	}
}
