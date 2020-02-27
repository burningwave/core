package org.burningwave.core.jvm;

import static org.burningwave.core.assembler.StaticComponentsContainer.Streams;

import java.io.InputStream;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.BiFunction;

import static org.burningwave.core.assembler.StaticComponentsContainer.Throwables;
import org.burningwave.core.io.ByteBufferOutputStream;

@SuppressWarnings("restriction")
class LowLevelObjectsHandlerSpecificElementsInitializer4Java9 extends LowLevelObjectsHandlerSpecificElementsInitializer {
	
	LowLevelObjectsHandlerSpecificElementsInitializer4Java9(LowLevelObjectsHandler lowLevelObjectsHandler) {
		super(lowLevelObjectsHandler);
		try {
	        Class<?> cls = Class.forName("jdk.internal.module.IllegalAccessLogger");
	        Field logger = cls.getDeclaredField("logger");
	        final long loggerFieldOffset = lowLevelObjectsHandler.unsafe.staticFieldOffset(logger);
	        final Object illegalAccessLogger = lowLevelObjectsHandler.unsafe.getObjectVolatile(cls, loggerFieldOffset);
	        lowLevelObjectsHandler.illegalAccessLoggerDisabler = () ->
	        	lowLevelObjectsHandler.unsafe.putObjectVolatile(cls, loggerFieldOffset, null);
	        lowLevelObjectsHandler.illegalAccessLoggerEnabler = () ->
	        	lowLevelObjectsHandler.unsafe.putObjectVolatile(cls, loggerFieldOffset, illegalAccessLogger);
	    } catch (Throwable e) {
	    	
	    }
		lowLevelObjectsHandler.disableIllegalAccessLogger();
		try {
			MethodHandles.Lookup consulter = MethodHandles.lookup();
			MethodHandle consulterRetrieverMethod = consulter.findStatic(MethodHandles.class, "privateLookupIn", MethodType.methodType(Lookup.class, Class.class, Lookup.class));
			lowLevelObjectsHandler.consulterRetriever = cls ->
				(Lookup)consulterRetrieverMethod.invoke(cls, MethodHandles.lookup());
		} catch (IllegalArgumentException | NoSuchMethodException
				| SecurityException | IllegalAccessException exc) {
			logError("Could not initialize consulter", exc);
			throw Throwables.toRuntimeException(exc);
		}
	}

	
	@Override
	void initSpecificElements() {
		try {
			final Method accessibleSetterMethod = AccessibleObject.class.getDeclaredMethod("setAccessible0", boolean.class);
			accessibleSetterMethod.setAccessible(true);
			lowLevelObjectsHandler.accessibleSetter = (accessibleObject, flag) ->
				accessibleSetterMethod.invoke(accessibleObject, flag);
		} catch (Throwable exc) {
			logInfo("method setAccessible0 class not detected on " + AccessibleObject.class.getName());
			throw Throwables.toRuntimeException(exc);
		}
		try {
			Lookup classLoaderConsulter = lowLevelObjectsHandler.consulterRetriever.apply(ClassLoader.class);
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
				logInfo("method invoke0 of class jdk.internal.reflect.NativeMethodAccessorImpl not detected");
				throw Throwables.toRuntimeException(exc);
			}
			try (
				InputStream inputStream = Optional.ofNullable(
					this.getClass().getClassLoader()
				).orElseGet(() -> 
					ClassLoader.getSystemClassLoader()
				).getResourceAsStream("org/burningwave/core/classes/ClassLoaderDelegate.bwc");
				ByteBufferOutputStream bBOS = new ByteBufferOutputStream()
			) {
				Streams.copy(inputStream, bBOS);
				lowLevelObjectsHandler.classLoaderDelegateClass = lowLevelObjectsHandler.unsafe.defineAnonymousClass(lowLevelObjectsHandler.builtinClassLoaderClass, bBOS.toByteArray(), null);
			} catch (Throwable exc) {
				throw Throwables.toRuntimeException(exc);
			}
		} catch (Throwable exc) {
			logInfo("jdk.internal.loader.BuiltinClassLoader class not detected");
			throw Throwables.toRuntimeException(exc);
		}
	}
	
	@Override
	public void close() {
		lowLevelObjectsHandler.enableIllegalAccessLogger();
		super.close();
	}
}
