package org.burningwave.core.jvm;

import static org.burningwave.core.assembler.StaticComponentContainer.JVMInfo;
import static org.burningwave.core.assembler.StaticComponentContainer.Streams;
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Vector;
import java.util.function.BiPredicate;

import org.burningwave.core.Component;

import sun.misc.Unsafe;

@SuppressWarnings("restriction")
abstract class LowLevelObjectsHandlerInitializer implements Component {
	LowLevelObjectsHandler lowLevelObjectsHandler;
	
	LowLevelObjectsHandlerInitializer(LowLevelObjectsHandler lowLevelObjectsHandler) {
		this.lowLevelObjectsHandler = lowLevelObjectsHandler;
		try {
			Field theUnsafeField = Unsafe.class.getDeclaredField("theUnsafe");
			theUnsafeField.setAccessible(true);
			this.lowLevelObjectsHandler.unsafe = (Unsafe)theUnsafeField.get(null);
		} catch (Throwable exc) {
			logInfo("Exception while retrieving unsafe");
			throw Throwables.toRuntimeException(exc);
		}
	}	
	
	void init() {
		CavyForRetrievingElementsOfClassLoaderClass cavy = new CavyForRetrievingElementsOfClassLoaderClass();
		initLoadedClassesVectorMemoryOffset(cavy);
		initLoadedPackageMapOffset(cavy);
		initEmptyMembersArrays();
		initMembersRetrievers();
		initSpecificElements();
	}


	private void initEmptyMembersArrays() {
		lowLevelObjectsHandler.emtpyFieldsArray = new Field[]{};
		lowLevelObjectsHandler.emptyMethodsArray = new Method[]{};
		lowLevelObjectsHandler.emptyConstructorsArray = new Constructor<?>[]{};
	}
	
	public static void build(LowLevelObjectsHandler lowLevelObjectsHandler) {
		try (LowLevelObjectsHandlerInitializer initializer =
				JVMInfo.getVersion() > 8 ?
				new LowLevelObjectsHandlerInitializer4Java9(lowLevelObjectsHandler):
				new LowLevelObjectsHandlerInitializer4Java8(lowLevelObjectsHandler)) {
			initializer.init();
		}
	}
	
	private void initMembersRetrievers() {
		try {
			Lookup consulter = lowLevelObjectsHandler.consulterRetriever.apply(Class.class);
			lowLevelObjectsHandler.getDeclaredFieldsRetriever = consulter.findSpecial(
				Class.class, "getDeclaredFields0",
				MethodType.methodType(Field[].class, boolean.class),
				Class.class
			);
			
			lowLevelObjectsHandler.getDeclaredMethodsRetriever = consulter.findSpecial(
				Class.class,
				"getDeclaredMethods0",
				MethodType.methodType(Method[].class, boolean.class),
				Class.class
			);

			lowLevelObjectsHandler.getDeclaredConstructorsRetriever = consulter.findSpecial(
				Class.class,
				"getDeclaredConstructors0",
				MethodType.methodType(Constructor[].class, boolean.class),
				Class.class
			);
			lowLevelObjectsHandler.parentClassLoaderFields = new HashMap<>();
		} catch (Throwable exc) {
			throw Throwables.toRuntimeException(exc);
		}
	}
	
	private void initLoadedClassesVectorMemoryOffset(CavyForRetrievingElementsOfClassLoaderClass cavy) {
		iterateClassLoaderFields(
			cavy, 
			getLoadedClassesVectorMemoryOffsetInitializator(cavy.clsForTest)
		);
	}
	
	private void initLoadedPackageMapOffset(CavyForRetrievingElementsOfClassLoaderClass cavy) {
		iterateClassLoaderFields(
			cavy, 
			getLoadedPackageMapMemoryOffsetInitializator(cavy.packageForTest)
		);
	}
	
	private BiPredicate<Object, Long> getLoadedClassesVectorMemoryOffsetInitializator(Class<?> definedClass) {
		return (object, offset) -> {
			if (object != null && object instanceof Vector) {
				Vector<?> vector = (Vector<?>)object;
				if (vector.contains(definedClass)) {
					lowLevelObjectsHandler.loadedClassesVectorMemoryOffset = offset;
					return true;
				}
			}
			return false;
		};
	}
	
	private BiPredicate<Object, Long> getLoadedPackageMapMemoryOffsetInitializator(Object pckg) {
		return (object, offset) -> {
			if (object != null && object instanceof Map) {
				Map<?, ?> map = (Map<?, ?>)object;
				if (map.containsValue(pckg)) {
					lowLevelObjectsHandler.loadedPackagesMapMemoryOffset = offset;
					return true;
				}
			}
			return false;
		};
	}
	
	protected Object iterateClassLoaderFields(ClassLoader classLoader, BiPredicate<Object, Long> predicate) {
		long offset;
		long step;
		if (JVMInfo.is32Bit()) {
			logInfo("JVM is 32 bit");
			offset = 8;
			step = 4;
		} else if (!JVMInfo.isCompressedOopsOffOn64BitHotspot()) {
			logInfo("JVM is 64 bit Hotspot and Compressed Oops is enabled");
			offset = 12;
			step = 4;
		} else {
			logInfo("JVM is 64 bit but is not Hotspot or Compressed Oops is disabled");
			offset = 16;
			step = 8;
		}
		logInfo("Iterating by unsafe over fields of classLoader {}", classLoader);
		while (true) {
			logInfo("Evaluating offset {}", offset);
			Object object = lowLevelObjectsHandler.unsafe.getObject(classLoader, offset);
			//logDebug(offset + " " + object);
			if (predicate.test(object, offset)) {
				return object;
			}
			offset+=step;
		}
	}
	
	abstract void initSpecificElements();
	
	@Override
	public void close() {
		this.lowLevelObjectsHandler = null;
	}
	
	private static class CavyForRetrievingElementsOfClassLoaderClass extends ClassLoader {
		Class<?> clsForTest;
		Object packageForTest;
		
		CavyForRetrievingElementsOfClassLoaderClass() {
			clsForTest = super.defineClass(
				CavyForRetrievingElementsOfClassLoaderClass.class.getName(),
				Streams.toByteBuffer(
					Optional.ofNullable(
						this.getClass().getClassLoader()
					).orElseGet(() -> ClassLoader.getSystemClassLoader()).getResourceAsStream(
						CavyForRetrievingElementsOfClassLoaderClass.class.getName().replace(".", "/") + ".class"
					)
				),	
				null
			);
			packageForTest = super.definePackage(
				"lowlevelobjectshandler.loadedpackagemapoffset.initializator.packagefortesting", 
				null, null, null, null, null, null, null
			);
		}
		
	}
}