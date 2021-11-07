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

import static org.burningwave.core.assembler.StaticComponentContainer.BufferHandler;
import static org.burningwave.core.assembler.StaticComponentContainer.Cache;
import static org.burningwave.core.assembler.StaticComponentContainer.ClassLoaders;
import static org.burningwave.core.assembler.StaticComponentContainer.Classes;
import static org.burningwave.core.assembler.StaticComponentContainer.Constructors;
import static org.burningwave.core.assembler.StaticComponentContainer.Driver;
import static org.burningwave.core.assembler.StaticComponentContainer.Fields;
import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggersRepository;
import static org.burningwave.core.assembler.StaticComponentContainer.Members;
import static org.burningwave.core.assembler.StaticComponentContainer.Methods;
import static org.burningwave.core.assembler.StaticComponentContainer.Objects;
import static org.burningwave.core.assembler.StaticComponentContainer.Paths;
import static org.burningwave.core.assembler.StaticComponentContainer.Resources;
import static org.burningwave.core.assembler.StaticComponentContainer.Streams;
import static org.burningwave.core.assembler.StaticComponentContainer.Strings;
import static org.burningwave.core.assembler.StaticComponentContainer.Synchronizer;

import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.burningwave.core.Closeable;
import org.burningwave.core.assembler.StaticComponentContainer;
import org.burningwave.core.classes.Fields.NoSuchFieldException;
import org.burningwave.core.function.Executor;
import org.burningwave.core.io.FileSystemItem;

@SuppressWarnings({"unchecked", "resource"})
public class Classes implements MembersRetriever {
	Field[] emtpyFieldsArray;
	Method[] emptyMethodsArray;
	Constructor<?>[] emptyConstructorsArray;
	public final Class<?> java_util_concurrent_ConcurrentHashMap_CollectionViewClass;


	private Classes() {
		emtpyFieldsArray = new Field[]{};
		emptyMethodsArray = new Method[]{};
		emptyConstructorsArray = new Constructor<?>[]{};
		java_util_concurrent_ConcurrentHashMap_CollectionViewClass = Driver.getClassByName(
			"java.util.concurrent.ConcurrentHashMap$CollectionView",
			false,
			this.getClass().getClassLoader(),
			this.getClass()
		);
	}

	public static Classes create() {
		return new Classes();
	}

	public <T> Class<T> retrieveFrom(Object object) {
		return object != null ? (Class<T>)object.getClass() : null;
	}

	public Class<?>[] retrieveFrom(Object... objects) {
		Class<?>[] classes = null;
		if (objects != null) {
			classes = new Class[objects.length];
			for (int i = 0; i < objects.length; i++) {
				if (objects[i] != null) {
					classes[i] = retrieveFrom(objects[i]);
				}
			}
		} else {
			classes = new Class[]{null};
		}
		return classes;
	}

	public String retrieveName(Throwable exc) {
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

	public Collection<String> retrieveNames(Throwable exc) {
		Collection<String> classesName = new LinkedHashSet<>();
		Optional.ofNullable(retrieveName(exc)).map(classesName::add);
		if (exc.getCause() != null) {
			classesName.addAll(retrieveNames(exc.getCause()));
		}
		return classesName;
	}

	public String retrievePackageName(String className) {
		String packageName = null;
		if (className.contains(("."))) {
			packageName = className.substring(0, className.lastIndexOf("."));
		}
		return packageName;
	}

	public String retrieveSimpleName(String className) {
		String classSimpleName = null;
		if (className.contains(("."))) {
			classSimpleName = className.substring(className.lastIndexOf(".")+1);
		} else {
			classSimpleName = className;
		}
		if (classSimpleName.contains("$")) {
			classSimpleName = classSimpleName.substring(classSimpleName.lastIndexOf("$")+1);
		}
		return classSimpleName;
	}

	public String toPath(Class<?> cls) {
		String path = cls.getSimpleName().replace(".", "$");
		Package pckg = cls.getPackage();
		if (pckg != null) {
			path = pckg.getName().replace(".", "/") + "/" + path + ".class";
		}
		return path;
	}

	public String toPath(String className) {
		return className.replace(".", "/");
	}

	public ClassLoader getClassLoader(Class<?> cls) {
		ClassLoader clsLoader = cls.getClassLoader();
		if (clsLoader == null) {
			clsLoader = ClassLoader.getSystemClassLoader();
		}
		return clsLoader;
	}

	public ByteBuffer getByteCode(Class<?> cls) {
		if (cls.isPrimitive()) {
			return null;
		}
		ClassLoader clsLoader = getClassLoader(cls);
		InputStream inputStream = clsLoader.getResourceAsStream(
			cls.getName().replace(".", "/") + ".class"
		);
		try {
			return Streams.toByteBuffer(inputStream);
		} catch (NullPointerException exc) {
			if (inputStream == null) {
				inputStream = Resources.getAsInputStream(
					cls.getName().replace(".", "/") + ".class",
					clsLoader,
					false
				).getValue();
				return Streams.toByteBuffer(
					java.util.Objects.requireNonNull(inputStream, "Could not acquire bytecode for class " + cls.getName())
				);
			}
			return Driver.throwException(exc);
		}
	}

	public <T> T newInstance(Constructor<T> ctor, Object... params) {
		if (params == null) {
			params = new Object[] {null};
		}
		try {
			return ctor.newInstance(params);
		} catch (Throwable exc) {
			return Driver.newInstance(ctor, params);
		}
	}

	@Override
	public Field[] getDeclaredFields(Class<?> cls)  {
		return Cache.classLoaderForFields.getOrUploadIfAbsent(
			getClassLoader(cls), getCacheKey(cls),
			() -> {
				try {
					return Driver.getDeclaredFields(cls);
				} catch (Throwable exc) {
					ManagedLoggersRepository.logWarn(getClass()::getName, "Could not retrieve fields of class {}. Cause: {}", cls.getName(), exc.getMessage());
					return emtpyFieldsArray;
				}
			}
		);
	}

	@Override
	public <T> Constructor<T>[] getDeclaredConstructors(Class<T> cls)  {
		return (Constructor<T>[]) Cache.classLoaderForConstructors.getOrUploadIfAbsent(
			getClassLoader(cls), getCacheKey(cls),
			() -> {
				try {
					return Driver.getDeclaredConstructors(cls);
				} catch (Throwable exc) {
					ManagedLoggersRepository.logWarn(getClass()::getName, "Could not retrieve constructors of class {}. Cause: {}", cls.getName(), exc.getMessage());
					return emptyConstructorsArray;
				}
			}
		);
	}

	@Override
	public Method[] getDeclaredMethods(Class<?> cls)  {
		return Cache.classLoaderForMethods.getOrUploadIfAbsent(
			getClassLoader(cls), getCacheKey(cls),
			() -> {
				try {
					return Driver.getDeclaredMethods(cls);
				} catch (Throwable exc) {
					ManagedLoggersRepository.logWarn(getClass()::getName, "Could not retrieve methods of class {}. Cause: {}", cls.getName(), exc.getMessage());
					return emptyMethodsArray;
				}
			}
		);
	}

	String getCacheKey(Class<?> cls) {
		return cls.getName().replace(".", "/");
	}

	public boolean isLoadedBy(Class<?> cls, ClassLoader classLoader) {
		ClassLoader parentClassLoader = null;
		if (cls.getClassLoader() == classLoader) {
			return true;
		} else if (classLoader != null && (parentClassLoader = ClassLoaders.getParent(classLoader)) != null) {
			return isLoadedBy(cls, parentClassLoader);
		} else {
			return false;
		}
	}

	public boolean isAssignableFrom(Class<?> cls_01, Class<?> cls_02) {
		return getClassOrWrapper(cls_01).isAssignableFrom(getClassOrWrapper(cls_02));
	}

	public Class<?> getClassOrWrapper(Class<?> cls) {
		return io.github.toolfactory.jvm.util.Classes.getClassOrWrapper(cls);
	}

	public static class Loaders implements Closeable {
		protected Map<ClassLoader, Map<String, ?>> classLoadersPackages;
		protected Map<String, MethodHandle> classLoadersMethods;
		protected Field builtinClassLoaderClassParentField;
		protected Collection<NotificationListenerOfParentsChange> registeredNotificationListenerOfParentsChange;

		private Loaders() {
			this.classLoadersPackages = new HashMap<>();
			this.classLoadersMethods = new HashMap<>();
			Class<?> builtinClassLoaderClass = Driver.getBuiltinClassLoaderClass();
			if (builtinClassLoaderClass != null) {
				this.builtinClassLoaderClassParentField = Fields.findFirstAndMakeItAccessible(builtinClassLoaderClass, "parent", builtinClassLoaderClass);
			}
			registeredNotificationListenerOfParentsChange = ConcurrentHashMap.newKeySet();

			//Preload required for the setAsMaster method
			@SuppressWarnings("unused")
			Class<?> cls = ChangeParentsContext.class;
			cls = ChangeParentsContext.Elements.class;
		}

		public static Loaders create() {
			return new Loaders();
		}

		public void registerNotificationListenerOfParentsChange(NotificationListenerOfParentsChange listener) {
			synchronized (registeredNotificationListenerOfParentsChange) {
				this.registeredNotificationListenerOfParentsChange.add(listener);
			}
		}

		private void notifyParentsChange(ChangeParentsContext context) {
			Iterator<NotificationListenerOfParentsChange> itr = this.registeredNotificationListenerOfParentsChange.iterator();
			while (itr.hasNext()) {
				NotificationListenerOfParentsChange listener = itr.next();
				try {
					listener.receive(context);
				} catch (Throwable exc) {
					ManagedLoggersRepository.logError(
						getClass()::getName, "Could not notify parent change to listener {}", exc, listener
					);
				}
			}
		}

		public void unregisterNotificationListenerOfParentsChange(NotificationListenerOfParentsChange listener) {
			synchronized (registeredNotificationListenerOfParentsChange) {
				this.registeredNotificationListenerOfParentsChange.remove(listener);
			}
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

		public Function<Boolean, ClassLoader> setAsMaster(ClassLoader classLoader, ClassLoader futureParent) {
			return setAsParent(getMaster(classLoader), futureParent);
		}

		public Function<Boolean, ClassLoader> setAsParent(ClassLoader target, ClassLoader originalFutureParent) {
			return setAsParent(target, originalFutureParent, true);
		}

		public Function<Boolean, ClassLoader> setAsParent(ClassLoader target, ClassLoader newParent, boolean mantainHierarchy) {
			if (target == newParent) {
				throw new IllegalArgumentException("The target cannot be the same instance");
			}
			ClassLoader oldParent = getParent(target);
			if (oldParent == newParent) {
				throw new IllegalArgumentException("The new parent cannot be the same of the old parent");
			}
			if (mantainHierarchy) {
				AtomicReference<Function<Boolean, ClassLoader>> resetterOne = new AtomicReference<>();
				if (oldParent != null && newParent != null) {
					ClassLoader masterClassLoaderOfOriginalFutureParent = getMaster(newParent);
					if (masterClassLoaderOfOriginalFutureParent != newParent) {
						resetterOne.set(setAsParent0(masterClassLoaderOfOriginalFutureParent, oldParent));
					} else {
						resetterOne.set(setAsParent0(newParent, oldParent));
					}

				}
				Function<Boolean, ClassLoader> resetterTwo = setAsParent0(target, newParent);
				return resetterOne.get() != null ? (reset) -> {
					ClassLoader targetExParent = resetterTwo.apply(reset);
					resetterOne.get().apply(reset);
					return targetExParent;
				} : resetterTwo;
			} else {
				return setAsParent0(target, newParent);
			}
		}

		private Function<Boolean, ClassLoader> setAsParent0(ClassLoader target, ClassLoader originalFutureParent) {
			if (Driver.isClassLoaderDelegate(target)) {
				return setAsParent(Fields.getDirect(target, "classLoader"), originalFutureParent);
			}
			ClassLoader futureParentTemp = originalFutureParent;
			if (isBuiltinClassLoader(target) && futureParentTemp != null) {
				futureParentTemp = checkAndConvertBuiltinClassLoader(futureParentTemp);
			}
			ClassLoader targetExParent = Fields.get(target, "parent");
			ClassLoader futureParent = futureParentTemp;
			checkAndRegisterOrUnregisterMemoryClassLoaders(target, targetExParent, originalFutureParent);
			Fields.setDirect(target, "parent", futureParent);
			notifyParentsChange(
				new ChangeParentsContext(
					target, futureParent, targetExParent
				)
			);
			return (reset) -> {
				if (reset) {
					checkAndRegisterOrUnregisterMemoryClassLoaders(target, originalFutureParent, targetExParent);
					Fields.setDirect(target, "parent", targetExParent);
					notifyParentsChange(
						new ChangeParentsContext(
							target, targetExParent, futureParent
						)
					);
				}
				return targetExParent;
			};
		}

		private void checkAndRegisterOrUnregisterMemoryClassLoaders(ClassLoader target, ClassLoader exParent, ClassLoader futureParent) {
			if (Driver.isClassLoaderDelegate(target)) {
				target = Fields.getDirect(target, "classLoader");
			}
			if (exParent != null && Driver.isClassLoaderDelegate(exParent)) {
				exParent = Fields.getDirect(exParent, "classLoader");
			}
			if (futureParent != null && Driver.isClassLoaderDelegate(futureParent)) {
				futureParent = Fields.getDirect(futureParent, "classLoader");
			}
			MemoryClassLoader exParentMC = exParent instanceof MemoryClassLoader? (MemoryClassLoader)exParent : null;
			MemoryClassLoader futureParentMC = futureParent instanceof MemoryClassLoader? (MemoryClassLoader)futureParent : null;
			MemoryClassLoader targetMemoryClassLoader = target instanceof MemoryClassLoader? (MemoryClassLoader)target : null;
			if (targetMemoryClassLoader != null) {
				if (futureParentMC != null) {
					futureParentMC.register(targetMemoryClassLoader);
				}
				if (exParentMC != null) {
					exParentMC.unregister(targetMemoryClassLoader, false);
				}
			}
		}

		private ClassLoader checkAndConvertBuiltinClassLoader(ClassLoader classLoader) {
			if (!isBuiltinClassLoader(classLoader)) {
				try {
					Collection<Method> methods = Members.findAll(
						MethodCriteria.byScanUpTo(
							cls -> cls.getName().equals(ClassLoader.class.getName())
						).name(
							"loadClass"::equals
						).and().parameterTypesAreAssignableFrom(
							String.class, boolean.class
						), classLoader.getClass()
					);
					classLoader = (ClassLoader)Constructors.newInstanceOf(Driver.getClassLoaderDelegateClass(), null, classLoader, Methods.findDirectHandle(
						methods.stream().findFirst().get()
					));
				} catch (Throwable exc) {
					Driver.throwException(exc);
				}
			}
			return classLoader;
		}

		public ClassLoader getParent(ClassLoader classLoader) {
			if (Driver.isClassLoaderDelegate(classLoader)) {
				return getParent(Fields.getDirect(classLoader, "classLoader"));
			} else if (isBuiltinClassLoader(classLoader)) {
				return Executor.get(() ->(ClassLoader) builtinClassLoaderClassParentField.get(classLoader));
			} else {
				return classLoader.getParent();
			}
		}

		public  ClassLoader getMaster(ClassLoader classLoader) {
			ClassLoader parentClassLoader = null;
			while ((parentClassLoader = getParent(classLoader)) != null) {
				classLoader = parentClassLoader;
			}
			return classLoader;
		}

		public MethodHandle getDefinePackageMethod(ClassLoader classLoader) {
			return getMethod(
				classLoader.getClass().getName() + "_" + "definePackage",
				() -> findDefinePackageMethodAndMakeItAccesible(classLoader)
			);
		}

		private MethodHandle findDefinePackageMethodAndMakeItAccesible(ClassLoader classLoader) {
			return Methods.findFirstDirectHandle(
				MethodCriteria.byScanUpTo((cls) ->
					cls.getName().equals(ClassLoader.class.getName())
				).name(
					"definePackage"::equals
				).and().parameterTypesAreAssignableFrom(
					String.class, String.class, String.class, String.class,
					String.class, String.class, String.class, URL.class
				), classLoader.getClass()
			);
		}

		public MethodHandle getDefineClassMethod(ClassLoader classLoader) {
			return getMethod(
				Classes.getClassLoader(classLoader.getClass()) + "_" + classLoader + "_" +  "defineClass",
				() -> findDefineClassMethodAndMakeItAccesible(classLoader)
			);
		}

		Object getClassLoadingLock(ClassLoader classLoader, String className) {
			try {
				return getGetClassLoadingLockMethod(classLoader).invoke(classLoader, className);
			} catch (Throwable exc) {
				return Driver.throwException(exc);
			}
		}

		public MethodHandle getGetClassLoadingLockMethod(ClassLoader classLoader) {
			return getMethod(
				Classes.getClassLoader(classLoader.getClass()) + "_" + classLoader + "_" +  "getClassLoadingLock",
				() -> findGetClassLoadingLockMethodAndMakeItAccesible(classLoader)
			);
		}

		private MethodHandle findDefineClassMethodAndMakeItAccesible(ClassLoader classLoader) {
			return Methods.findFirstDirectHandle(
				MethodCriteria.byScanUpTo((cls) -> cls.getName().equals(ClassLoader.class.getName())).name(
					(classLoader instanceof MemoryClassLoader? "_defineClass" : "defineClass")::equals
				).and().parameterTypes(params ->
					params.length == 3
				).and().parameterTypesAreAssignableFrom(
					String.class, ByteBuffer.class, ProtectionDomain.class
				).and().returnType((cls) -> cls.getName().equals(Class.class.getName())),
				classLoader.getClass()
			);
		}

		private MethodHandle findGetClassLoadingLockMethodAndMakeItAccesible(ClassLoader classLoader) {
			return Methods.findFirstDirectHandle(
				MethodCriteria.byScanUpTo((cls) -> cls.getName().equals(ClassLoader.class.getName())).name(
					"getClassLoadingLock"::equals
				).and().parameterTypes(params ->
					params.length == 1
				).and().parameterTypesAreAssignableFrom(
					String.class
				),
				classLoader.getClass()
			);
		}

		private MethodHandle getMethod(String key, Supplier<MethodHandle> methodSupplier) {
			MethodHandle method = classLoadersMethods.get(key);
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

		public Map<String, ?> retrieveLoadedPackages(ClassLoader classLoader) {
			Map<String, ?> packages = classLoadersPackages.get(classLoader);
			if (packages == null) {
				synchronized (classLoadersPackages) {
					packages = classLoadersPackages.get(classLoader);
					if (packages == null) {
						classLoadersPackages.put(classLoader, (packages = Driver.retrieveLoadedPackages(classLoader)));
					}
				}

			}
			if (packages == null) {
				Driver.throwException("Could not find packages Map on {}", classLoader);
			}
			return packages;

		}

		public <T> Class<T> loadOrDefineByJavaClass(
			JavaClass javaClass,
			ClassLoader classLoader
		) throws ClassNotFoundException {
			Map<String, JavaClass> repository = new HashMap<>();
			repository.put(javaClass.getName(), javaClass);
			return loadOrDefineByJavaClass(javaClass.getName(), repository, classLoader);
		}


		public <T> Class<T> loadOrDefineByJavaClass(
			String className,
			Map<String, JavaClass> byteCodes,
			ClassLoader classLoader
		) throws ClassNotFoundException {
			if (!(classLoader instanceof MemoryClassLoader)) {
				return loadOrDefineByByteCode(
					className, clsName -> byteCodes.get(clsName).getByteCode(), classLoader,
					getDefineClassMethod(classLoader), getDefinePackageMethod(classLoader)
				);
			} else {
				for (Map.Entry<String, JavaClass> clazz : byteCodes.entrySet()) {
					((MemoryClassLoader)classLoader).addByteCode(
						clazz.getKey(), clazz.getValue().getByteCode()
					);
				}
				return (Class<T>) classLoader.loadClass(className);
			}
		}

		public Class<?> loadOrDefineByByteCode(ByteBuffer byteCode, ClassLoader classLoader) throws ClassNotFoundException {
			Map<String, JavaClass> repository = new HashMap<>();
			return JavaClass.extractByUsing(byteCode, javaClass -> {
				repository.put(javaClass.getName(), javaClass);
				return loadOrDefineByJavaClass(javaClass.getName(), repository, classLoader);
			});
		}

		public <T> Class<T> loadOrDefineByByteCode(
			String className,
			Map<String, ByteBuffer> repository,
			ClassLoader classLoader
		) throws ClassNotFoundException {
			if (!(classLoader instanceof MemoryClassLoader)) {
				return loadOrDefineByByteCode(
					className, clsName -> repository.get(clsName), classLoader,
					getDefineClassMethod(classLoader), getDefinePackageMethod(classLoader)
				);
			} else {
				for (Map.Entry<String, ByteBuffer> clazz : repository.entrySet()) {
					((MemoryClassLoader)classLoader).addByteCode(
						clazz.getKey(), clazz.getValue()
					);
				}
				return (Class<T>) classLoader.loadClass(className);
			}
		}


		private <T> Class<T> loadOrDefineByByteCode(
			String className,
			Function<String, ByteBuffer> byteCodeSupplier,
			ClassLoader classLoader,
			MethodHandle defineClassMethod,
			MethodHandle definePackageMethod
		) throws ClassNotFoundException {
			try {
				try {
					return (Class<T>) classLoader.loadClass(className);
				}  catch (ClassNotFoundException | NoClassDefFoundError exc) {
					Class<T> cls = defineOrLoad(classLoader, defineClassMethod, className, byteCodeSupplier.apply(className));
	    			definePackageFor(cls, classLoader, definePackageMethod);
	    			return cls;
				}
			}  catch (ClassNotFoundException | NoClassDefFoundError | InvocationTargetException exc) {
				if (byteCodeSupplier.apply(className) == null) {
					throw new ClassNotFoundException(className);
				}
				String newNotFoundClassName = Classes.retrieveNames(exc).stream().findFirst().orElseGet(() -> null);
				loadOrDefineByByteCode(
					newNotFoundClassName,
					byteCodeSupplier, classLoader, defineClassMethod, definePackageMethod
        		);
				return loadOrDefineByByteCode(className, byteCodeSupplier,
					classLoader,
					defineClassMethod, definePackageMethod
        		);
			}
	    }

		public <T> Class<T> loadOrDefine(
			Class<T> toLoad,
			ClassLoader classLoader
		) throws ClassNotFoundException {
			return loadOrDefine(
				toLoad, classLoader,
				getDefineClassMethod(classLoader),
				getDefinePackageMethod(classLoader)
			);
		}

		private <T> Class<T> loadOrDefine(
			Class<T> toLoad,
			ClassLoader classLoader,
			MethodHandle defineClassMethod,
			MethodHandle definePackageMethod
		) throws ClassNotFoundException {
			String className = toLoad.getName();
			try {
				try {
					return (Class<T>)classLoader.loadClass(className);
				} catch (ClassNotFoundException | NoClassDefFoundError exc) {
					Class<T> cls = defineOrLoad(classLoader, defineClassMethod, className, BufferHandler.shareContent(Classes.getByteCode(toLoad)));
	    			definePackageFor(cls, classLoader, definePackageMethod);
	    			return cls;
				}
			} catch (ClassNotFoundException | NoClassDefFoundError | InvocationTargetException exc) {
				String newNotFoundClassName = Classes.retrieveNames(exc).stream().findFirst().orElseGet(() -> null);
				loadOrDefine(
        			Driver.getClassByName(
        				newNotFoundClassName, false,
        				Classes.getClassLoader(toLoad), this.getClass()
        			),
        			classLoader, defineClassMethod, definePackageMethod
        		);
				return (Class<T>)loadOrDefine(
					Driver.getClassByName(
        				className, false, Classes.getClassLoader(toLoad), this.getClass()
        			),
        			classLoader, defineClassMethod, definePackageMethod
        		);
			}
	    }

		public <T> Class<T> defineOrLoad(ClassLoader classLoader, JavaClass javaClass) throws ReflectiveOperationException {
			String className = javaClass.getName();
			Class<T> definedClass = defineOrLoad(classLoader, getDefineClassMethod(classLoader), className, javaClass.getByteCode());
			definePackageFor(definedClass, classLoader, getDefinePackageMethod(classLoader));
			return definedClass;
		}


		private <T> Class<T> defineOrLoad(
			ClassLoader classLoader,
			MethodHandle method,
			String className,
			ByteBuffer byteCode
		) throws ClassNotFoundException, InvocationTargetException, NoClassDefFoundError {
			try {
				synchronized (getClassLoadingLock(classLoader, className)) {
					return (Class<T>)method.invoke(classLoader, className, byteCode, null);
				}
			} catch (InvocationTargetException | ClassNotFoundException | NoClassDefFoundError exc) {
				throw exc;
			} catch (java.lang.LinkageError exc) {
				ManagedLoggersRepository.logWarn(getClass()::getName, "Class {} is already defined", className);
				return (Class<T>)classLoader.loadClass(className);
			} catch (Throwable exc) {
				if (byteCode == null) {
					throw new ClassNotFoundException(className);
				}
				return Driver.throwException(exc);
			}
		}

		private Package definePackage(
			ClassLoader classLoader, MethodHandle definePackageMethod,
			String name, String specTitle, String specVersion,
			String specVendor, String implTitle, String implVersion,
			String implVendor,
			URL sealBase
		) throws IllegalArgumentException {
			return Executor.get(() -> {
				try {
					return (Package) definePackageMethod.invoke(
						classLoader, name, specTitle, specVersion, specVendor,
						implTitle, implVersion, implVendor, sealBase
					);
				} catch (IllegalArgumentException exc) {
					ManagedLoggersRepository.logWarn(getClass()::getName, "Package " + name + " already defined");
					return retrieveLoadedPackage(classLoader, name);
				}
			});
	    }

		private void definePackageFor(Class<?> cls,
			ClassLoader classLoader,
			MethodHandle definePackageMethod
		) {
			if (cls.getName().contains(".")) {
				String pckgName = cls.getName().substring(
			    	0, cls.getName().lastIndexOf(".")
			    );
			    if (retrieveLoadedPackage(classLoader, pckgName) == null) {
			    	Synchronizer.execute(classLoader + "_" + pckgName,() -> {
			    		if (retrieveLoadedPackage(classLoader, pckgName) == null) {
			    			definePackage(classLoader, definePackageMethod, pckgName, null, null, null, null, null, null, null);
			    		}
			    	});
			    }
			}
		}


		public Package retrieveLoadedPackage(ClassLoader classLoader, String packageName) {
			Map<String, ?> packages = retrieveLoadedPackages(classLoader);
			Object packageToFind = packages.get(packageName);
			ClassLoader parentClassLoader = null;
			if (packageToFind != null) {
				if (packageToFind instanceof Package) {
					return (Package)packageToFind;
				} else {
					return Driver.getPackage(classLoader, packageName);
				}
			} else if ((parentClassLoader = getParent(classLoader)) != null) {
				return retrieveLoadedPackage(parentClassLoader, packageName);
			} else {
				return null;
			}
		}

		public ClassLoader getClassLoaderOfPath(ClassLoader classLoader, String path) {
			FileSystemItem fIS = FileSystemItem.ofPath(path);
			ClassLoader pathLoader = null;
			for (ClassLoader cl : getHierarchy(classLoader)) {
				URL[] urls = getURLs(cl);
				if (urls != null) {
					for (URL url : urls) {
						FileSystemItem loadedPathFIS = FileSystemItem.of(url);
						if (loadedPathFIS.equals(fIS) || loadedPathFIS.isParentOf(fIS)) {
							pathLoader = cl;
						}
					}
				}
			}
			return pathLoader;
		}

		public boolean isItPossibleToAddClassPaths(ClassLoader classLoader) {
			if (classLoader != null) {
				if (classLoader instanceof URLClassLoader || isBuiltinClassLoader(classLoader) || classLoader instanceof PathScannerClassLoader) {
					return true;
				} else {
					return isItPossibleToAddClassPaths(getParent(classLoader));
				}
			} else {
				return false;
			}
		}

		public Collection<String> addClassPath(ClassLoader classLoader, String... classPaths) {
			return addClassPaths(classLoader, Arrays.asList(classPaths));
		}

		public Collection<String> addClassPath(ClassLoader classLoader, Predicate<String> checkForAddedClasses, String... classPaths) {
			return addClassPaths(classLoader, checkForAddedClasses, Arrays.asList(classPaths));
		}

		public Collection<String> addClassPaths(ClassLoader classLoader, Predicate<String> checkForAddedClasses, Collection<String>... classPathCollections) {
			if (!(classLoader instanceof URLClassLoader || isBuiltinClassLoader(classLoader) || classLoader instanceof PathScannerClassLoader)) {
				if (!isItPossibleToAddClassPaths(classLoader)) {
					throw new UnsupportedException(
						Strings.compile("Could not add class paths to {} because the type {} is not supported",
								Objects.getId(classLoader), classLoader.getClass())
					);
				} else {
					return addClassPaths(getParent(classLoader), checkForAddedClasses, classPathCollections);
				}
			}
			if (Driver.isClassLoaderDelegate(classLoader)) {
				return addClassPaths(Fields.getDirect(classLoader, "classLoader"), checkForAddedClasses, classPathCollections);
			}
			Collection<String> paths = new HashSet<>();
			for (Collection<String> classPaths : classPathCollections) {
				paths.addAll(classPaths);
			}
			if (classLoader instanceof URLClassLoader || Driver.isBuiltinClassLoader(classLoader)) {
				paths.removeAll(getAllLoadedPaths(classLoader));
				if (!paths.isEmpty()) {
					Object target = classLoader instanceof URLClassLoader ?
						classLoader :
						Fields.getDirect(classLoader, "ucp");
					if (target != null) {
						Consumer<URL> classPathAdder = 	urls -> Methods.invokeDirect(target, "addURL", urls);
						paths.stream().map(classPath -> FileSystemItem.ofPath(classPath).getURL()).forEach(url -> {
							classPathAdder.accept(url);
						});
						return paths;
					}
				}
			} else if (classLoader instanceof PathScannerClassLoader) {
				return ((PathScannerClassLoader)classLoader).scanPathsAndAddAllByteCodesFound(paths, checkForAddedClasses);
			}
			return new HashSet<>();
		}

		public Collection<String> addClassPaths(ClassLoader classLoader, Collection<String>... classPathCollections) {
			return addClassPaths(classLoader, (path) -> true, classPathCollections);
		}

		public Collection<String> getLoadedPaths(ClassLoader classLoader) {
			Collection<String> paths = new LinkedHashSet<>();
			if (classLoader instanceof PathScannerClassLoader) {
				paths.addAll(((PathScannerClassLoader)classLoader).loadedPaths.entrySet()
					.stream().filter(entry -> entry.getValue()).map(entry -> entry.getKey()).collect(Collectors.toSet()));
			} else {
				URL[] resUrl = getURLs(classLoader);
				if (resUrl != null) {
					for (URL element : resUrl) {
						paths.add(Paths.convertURLPathToAbsolutePath(element.getPath()));
					}
				}
			}
			return paths;
		}

		public Collection<String> getAllLoadedPaths(ClassLoader classLoader) {
			Collection<String> paths = new LinkedHashSet<>();
			while(classLoader != null) {
				paths.addAll(getLoadedPaths(classLoader));
				classLoader = getParent(classLoader);
			}
			return paths;
		}

		public boolean isBuiltinClassLoader(ClassLoader classLoader) {
			return Driver.isBuiltinClassLoader(classLoader);
		}

		public URL[] getURLs(ClassLoader classLoader) {
			if (classLoader instanceof URLClassLoader) {
				return ((URLClassLoader)classLoader).getURLs();
			} else if (Driver.isClassLoaderDelegate(classLoader)) {
				return getURLs(Fields.getDirect(classLoader, "classLoader"));
			} else if (Driver.isBuiltinClassLoader(classLoader)) {
				Object urlClassPath = Fields.getDirect(classLoader, "ucp");
				Collection<URL> urls = new ArrayList<>();
				if (urlClassPath != null) {
					urls.addAll(Arrays.asList(Methods.invoke(urlClassPath, "getURLs")));
				}
				Map<String, ?> nameToModule = Fields.getDirect(classLoader, "nameToModule");
				Map<?, ?> moduleToReader = Fields.getDirect(classLoader, "moduleToReader");
				if (nameToModule != null) {
					for (Object moduleReference : nameToModule.values() ) {
						URI uri = Fields.getDirect(moduleReference, "location");
						try {
							URL url = uri.toURL();
							if (url.toString().startsWith("file")) {
								Object moduleReader = moduleToReader.get(moduleReference);
								try {
									Collection<?> finders = Fields.getDirect(moduleReader, "finders");
									if (finders != null) {
										for (Object finder : finders) {
											Path path = Fields.getDirect(finder, "dir");
											if (path != null) {
												urls.add(path.toUri().toURL());
											}
										}
									}
								} catch (NoSuchFieldException exc) {

								}
								urls.add(url);

							}
						} catch (MalformedURLException exc) {
							Driver.throwException(exc);
						}
					}
				}
				return urls.toArray(new URL[urls.size()]);
			} else if (classLoader instanceof PathScannerClassLoader) {
				return ((PathScannerClassLoader)classLoader).getURLs();
			}
			return null;
		}


		public void unregister(ClassLoader classLoader) {
			classLoadersPackages.remove(classLoader);
		}

		@Override
		public void close() {
			if (this != StaticComponentContainer.ClassLoaders) {
				this.classLoadersMethods.clear();
				this.classLoadersMethods = null;
				this.classLoadersPackages.clear();
				this.classLoadersPackages = null;
				this.builtinClassLoaderClassParentField = null;
			} else {
				Driver.throwException("Could not close singleton instance {}", this);
			}
		}

		public static class UnsupportedException extends RuntimeException {

			private static final long serialVersionUID = 8964839983768809586L;

			public UnsupportedException(String s) {
				super(s);
			}

			public UnsupportedException(String s, Throwable cause) {
				super(s, cause);
			}
		}

		public static interface NotificationListenerOfParentsChange {

			public void receive(ChangeParentsContext context);

		}

		public static class ChangeParentsContext extends org.burningwave.core.Context {

			private enum Elements {
				TARGET,
				NEW_PARENT,
				OLD_PARENT
			}

			private ChangeParentsContext(ClassLoader target, ClassLoader newParent, ClassLoader oldParent) {
				put(Elements.TARGET, target);
				put(Elements.NEW_PARENT, newParent);
				put(Elements.OLD_PARENT, oldParent);
			}

			public <C extends ClassLoader> C getTarget() {
				return get(Elements.TARGET);
			}

			public <C extends ClassLoader> C getNewParent() {
				return get(Elements.NEW_PARENT);
			}

			public <C extends ClassLoader> C getOldParent() {
				return get(Elements.OLD_PARENT);
			}
		}

	}

}
