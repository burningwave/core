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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.ByteBuffer;
import java.security.ProtectionDomain;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.burningwave.Throwables;
import org.burningwave.core.Cache;
import org.burningwave.core.Component;
import org.burningwave.core.jvm.LowLevelObjectsHandler;

public class Classes implements Component {
	private static Classes INSTANCE;
	private LowLevelObjectsHandler lowLevelObjectsHandler;
	private MemberFinder memberFinder;
	protected Map<ClassLoader, Vector<Class<?>>> classLoadersClasses;
	protected Map<ClassLoader, Map<String, ?>> classLoadersPackages;
	protected Map<String, Method> classLoadersMethods;	
	
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
	
	static {
		V15 = 0 << 16 | 59;
	}
	
	private Classes(LowLevelObjectsHandler lowLevelObjectsHandler, MemberFinder memebrFinder) {
		this.lowLevelObjectsHandler = lowLevelObjectsHandler;
		this.memberFinder = memebrFinder;
		this.classLoadersClasses = new ConcurrentHashMap<>();
		this.classLoadersPackages = new ConcurrentHashMap<>();
		this.classLoadersMethods = new ConcurrentHashMap<>();
	}
	
	public static Classes getInstance() {
		if (INSTANCE == null) {
			synchronized(Classes.class) {
				if (INSTANCE == null) {
					INSTANCE = new Classes(LowLevelObjectsHandler.getInstance(), MemberFinder.create());
				}
			}
		}
		return INSTANCE;
	}
	
	public static Classes create(LowLevelObjectsHandler lowLevelObjectsHandler, MemberFinder memebrFinder) {
		return new Classes(lowLevelObjectsHandler, memebrFinder);
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
	
	public Collection<Field> getDeclaredFields(Class<?> cls, Predicate<Field> fieldPredicate) {
		Collection<Field> members = new LinkedHashSet<>();
		for (Field member : getDeclaredFields(cls)) {
			if (fieldPredicate.test(member)) {
				members.add(member);
			}
		}
		return members;
	}
	
	public Field[] getDeclaredFields(Class<?> cls)  {
		return Cache.CLASS_LOADER_FOR_FIELDS.getOrDefault(
			getClassLoader(cls), cls.getName().replace(".", "/"),
			() -> lowLevelObjectsHandler.getDeclaredFields(cls)
		);
		
	}
	
	public Collection<Method> getDeclaredMethods(Class<?> cls, Predicate<Method> fieldPredicate) {
		Collection<Method> members = new LinkedHashSet<>();
		for (Method member : getDeclaredMethods(cls)) {
			if (fieldPredicate.test(member)) {
				members.add(member);
			}
		}
		return members;
	}
	
	public Method[] getDeclaredMethods(Class<?> cls)  {
		return Cache.CLASS_LOADER_FOR_METHODS.getOrDefault(
			getClassLoader(cls), cls.getName().replace(".", "/"),
			() -> lowLevelObjectsHandler.getDeclaredMethods(cls)
		);
	}
	
	public Collection<Constructor<?>> getDeclaredConstructors(Class<?> cls, Predicate<Constructor<?>> fieldPredicate) {
		Collection<Constructor<?>> members = new LinkedHashSet<>();
		for (Constructor<?> member : getDeclaredConstructors(cls)) {
			if (fieldPredicate.test(member)) {
				members.add(member);
			}
		}
		return members;
	}
	
	public Constructor<?>[] getDeclaredConstructors(Class<?> cls)  {
		return Cache.CLASS_LOADER_FOR_CONSTRUCTORS.getOrDefault(
			getClassLoader(cls), cls.getName().replace(".", "/"),
			() -> lowLevelObjectsHandler.getDeclaredConstructors(cls)
		);
	}
	
	public Collection<ClassLoader> getAllParents(ClassLoader classLoader) {
		return getHierarchy(classLoader, false);
	}
	
	public Collection<ClassLoader> getHierarchy(ClassLoader classLoader) {
		return getHierarchy(classLoader, true);
	}
	
	private  Collection<ClassLoader> getHierarchy(ClassLoader classLoader, boolean includeClassLoader) {
		Collection<ClassLoader> classLoaders = new LinkedHashSet<>();
		if (includeClassLoader) {
			classLoaders.add(classLoader);
		}
		while ((classLoader = getParent(classLoader)) != null) {
			classLoaders.add(classLoader);
		}
		return classLoaders;
	}
	
	public ClassLoader getClassLoader(Class<?> cls) {
		ClassLoader clsLoader = cls.getClassLoader();
		if (clsLoader == null) {
			clsLoader = ClassLoader.getSystemClassLoader();
		}
		return clsLoader;
	}
	
	public Function<Boolean, ClassLoader> setAsMaster(ClassLoader classLoader, ClassLoader futureParent, boolean mantainHierarchy) {
		return lowLevelObjectsHandler.setAsParent(getMaster(classLoader), futureParent, mantainHierarchy);
	}
	
	public Function<Boolean, ClassLoader> setAsParent(ClassLoader classLoader, ClassLoader futureParent, boolean mantainHierarchy) {
		return lowLevelObjectsHandler.setAsParent(classLoader, futureParent, mantainHierarchy);
	}
	
	public ClassLoader getParent(ClassLoader classLoader) {
		return lowLevelObjectsHandler.getParent(classLoader);
	}
	
	private  ClassLoader getMaster(ClassLoader classLoader) {
		while (getParent(classLoader) != null) {
			classLoader = getParent(classLoader); 
		}
		return classLoader;
	}

	public void setAccessible(AccessibleObject object, boolean flag) {
		lowLevelObjectsHandler.setAccessible(object, flag);
	}
	
	public static String getId(Object object) {
		if (object instanceof String) {
			return (String)object;
		} else if (object.getClass().isPrimitive()) {
			return ((Integer)object).toString();
		}
        return object.getClass().getName() + "@" + object.hashCode();
    }
	 
	public static String getStringForSync(Object... objects) {
		String id = "_";
		for (Object object : objects) {
			id += getId(object) + "_";
		}
		return id;
	}
	
	public Method getDefinePackageMethod(ClassLoader classLoader) {
		return getMethod(
			classLoader,
			classLoader.getClass().getName() + "_" + "definePackage",
			() -> findDefinePackageMethodAndMakeItAccesible(classLoader)
		);
	}
	
	
	
	private Method findDefinePackageMethodAndMakeItAccesible(ClassLoader classLoader) {
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
	
	public Method getDefineClassMethod(ClassLoader classLoader) {
		return getMethod(
			classLoader,
			classLoader.getClass().getName() + "_" + "defineClass",
			() -> findDefineClassMethodAndMakeItAccesible(classLoader)
		);
	}
	
	private Method findDefineClassMethodAndMakeItAccesible(ClassLoader classLoader) {
		Method method = memberFinder.findAll(
			MethodCriteria.byScanUpTo((cls) -> cls.getName().equals(ClassLoader.class.getName())).name(
				(classLoader instanceof MemoryClassLoader? "_defineClass" : "defineClass")::equals
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
	
	private Method getMethod(ClassLoader classLoader, String key, Supplier<Method> methodSupplier) {
		Method method = classLoadersMethods.get(key);
		if (method == null) {
			synchronized (classLoadersMethods) {
				method = classLoadersMethods.get(key);
				if (method == null) {
					classLoadersMethods.put(key, method = methodSupplier.get());
				}
			}
		}
		return method;
	}
	
	public Vector<Class<?>> retrieveLoadedClasses(ClassLoader classLoader) {
		Vector<Class<?>> classes = classLoadersClasses.get(classLoader);
		if (classes != null) {
			return classes;
		} else {
			classes = classLoadersClasses.get(classLoader);
			if (classes == null) {
				synchronized (classLoadersClasses) {
					classes = classLoadersClasses.get(classLoader);
					if (classes == null) {
						classLoadersClasses.put(classLoader, (classes = lowLevelObjectsHandler.retrieveLoadedClasses(classLoader)));
						return classes;
					}
				}
			}
		}
		throw Throwables.toRuntimeException("Could not find classes Vector on " + classLoader);
	}
	
	public Collection<Class<?>> retrieveAllLoadedClasses(ClassLoader classLoader) {
		Collection<Class<?>> allLoadedClasses = new LinkedHashSet<>();
		allLoadedClasses.addAll(retrieveLoadedClasses(classLoader));
		if (classLoader.getParent() != null) {
			allLoadedClasses.addAll(retrieveAllLoadedClasses(classLoader.getParent()));
		}
		return allLoadedClasses;
	}
	
	public Map<String, ?> retrieveLoadedPackages(ClassLoader classLoader) {
		Map<String, ?> packages = classLoadersPackages.get(classLoader);
		if (packages == null) {
			synchronized (classLoadersPackages) {
				packages = classLoadersPackages.get(classLoader);
				if (packages == null) {
					classLoadersPackages.put(classLoader, (packages = (Map<String, ?>)lowLevelObjectsHandler.retrieveLoadedPackages(classLoader)));
				}
			}
		
		}
		if (packages == null) {
			throw Throwables.toRuntimeException("Could not find packages Map on " + classLoader);
		}
		return packages;
		
	}
	
	public Package retrieveLoadedPackage(ClassLoader classLoader, Object packageToFind, String packageName) {
		try {
			return lowLevelObjectsHandler.retrieveLoadedPackage(classLoader, packageToFind, packageName);
		} catch (Throwable exc) {
			throw Throwables.toRuntimeException(exc);
		}
	}
	
	public Object invoke(Object target, Method method, Object... params) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		return lowLevelObjectsHandler.invoke(target, method, params);
	}
	
	public void unregister(ClassLoader classLoader) {
		classLoadersClasses.remove(classLoader);
		classLoadersPackages.remove(classLoader);
	}
}
