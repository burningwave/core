package org.burningwave.core.jvm;

import java.io.InputStream;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.BiFunction;

import org.burningwave.ManagedLogger;
import org.burningwave.Throwables;
import org.burningwave.core.io.ByteBufferOutputStream;
import org.burningwave.core.io.Streams;

@SuppressWarnings("restriction")
class LowLevelObjectsHandlerSpecificElementsInitializer4Java9 extends LowLevelObjectsHandlerSpecificElementsInitializer {
	
	private Object illegalAccessLogger;
	
	LowLevelObjectsHandlerSpecificElementsInitializer4Java9(LowLevelObjectsHandler lowLevelObjectsHandler) {
		super(lowLevelObjectsHandler);
		disableWarning();
	}
	
	@Override
	void initSpecificElements() {
		try {
			final Method accessibleSetterMethod = AccessibleObject.class.getDeclaredMethod("setAccessible0", boolean.class);
			accessibleSetterMethod.setAccessible(true);
			lowLevelObjectsHandler.accessibleSetter = (accessibleObject, flag) ->
				accessibleSetterMethod.invoke(accessibleObject, flag);
		} catch (Throwable exc) {
			ManagedLogger.Repository.getInstance().logInfo(LowLevelObjectsHandler.class, "method setAccessible0 class not detected on " + AccessibleObject.class.getName());
			throw Throwables.toRuntimeException(exc);
		}
		try {
			Lookup classLoaderConsulter = getConsulter(ClassLoader.class);
			MethodType methodType = MethodType.methodType(Package.class, String.class);
			MethodHandle methodHandle = classLoaderConsulter.findSpecial(ClassLoader.class, "getDefinedPackage", methodType, ClassLoader.class);
			BiFunction<ClassLoader, String, Package> packageRetriever = (BiFunction<ClassLoader, String, Package>)LambdaMetafactory.metafactory(
				classLoaderConsulter, "apply",
				MethodType.methodType(BiFunction.class),
				methodHandle.type().generic(),
				methodHandle,
				methodHandle.type()
			).getTarget().invokeExact();
			lowLevelObjectsHandler.packageRetriever = (classLoader, object, packageName) ->
				packageRetriever.apply(classLoader, packageName);
		} catch (Throwable exc) {
			throw Throwables.toRuntimeException(exc);
		}
		try {
			lowLevelObjectsHandler.builtinClassLoaderClass = Class.forName("jdk.internal.loader.BuiltinClassLoader");
			try {
				lowLevelObjectsHandler.methodInvoker = Class.forName("jdk.internal.reflect.NativeMethodAccessorImpl").getDeclaredMethod("invoke0", Method.class, Object.class, Object[].class);
				lowLevelObjectsHandler.setAccessible(lowLevelObjectsHandler.methodInvoker, true);
			} catch (Throwable exc) {
				ManagedLogger.Repository.getInstance().logInfo(LowLevelObjectsHandler.class, "method invoke0 of class jdk.internal.reflect.NativeMethodAccessorImpl not detected");
				throw Throwables.toRuntimeException(exc);
			}
			try (
				InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("jdk/internal/loader/ClassLoaderDelegate.bwclass");
				ByteBufferOutputStream bBOS = new ByteBufferOutputStream()
			) {
				Streams.copy(inputStream, bBOS);
				lowLevelObjectsHandler.classLoaderDelegateClass = lowLevelObjectsHandler.unsafe.defineAnonymousClass(lowLevelObjectsHandler.builtinClassLoaderClass, bBOS.toByteArray(), null);
			} catch (Throwable exc) {
				throw Throwables.toRuntimeException(exc);
			}
		} catch (Throwable exc) {
			ManagedLogger.Repository.getInstance().logInfo(LowLevelObjectsHandler.class, "jdk.internal.loader.BuiltinClassLoader class not detected");
			throw Throwables.toRuntimeException(exc);
		}
	}
	
	@Override
	Lookup getConsulter(Class<?> cls) {
		try {
			return (Lookup)MethodHandles.class.getDeclaredMethod("privateLookupIn", Class.class, Lookup.class).invoke(null, cls, MethodHandles.lookup());
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException
				| SecurityException exc) {
			logError("Could not initialize consulter", exc);
			throw Throwables.toRuntimeException(exc);
		}
	}
	
	void disableWarning() {
	    try {
	        Class<?> cls = Class.forName("jdk.internal.module.IllegalAccessLogger");
	        Field logger = cls.getDeclaredField("logger");
	        if (illegalAccessLogger == null) {
	        	illegalAccessLogger = lowLevelObjectsHandler.unsafe.getObjectVolatile(cls, lowLevelObjectsHandler.unsafe.staticFieldOffset(logger));
	        }
	        lowLevelObjectsHandler.unsafe.putObjectVolatile(cls, lowLevelObjectsHandler.unsafe.staticFieldOffset(logger), null);
	    } catch (Throwable e) {
	    	
	    }
	}
	
	void enableWarning() {
	    try {
	    	if (illegalAccessLogger != null) {
	    		Class<?> cls = Class.forName("jdk.internal.module.IllegalAccessLogger");
	    		Field logger = cls.getDeclaredField("logger");
	    		lowLevelObjectsHandler.unsafe.putObjectVolatile(cls, lowLevelObjectsHandler.unsafe.staticFieldOffset(logger), illegalAccessLogger);
	    	}
	    } catch (Throwable e) {
	    	
	    }
	}
	
	@Override
	public void close() {
		enableWarning();
		super.close();
	}
}
