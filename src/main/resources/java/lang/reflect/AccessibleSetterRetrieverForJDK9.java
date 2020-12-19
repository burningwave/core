package java.lang.reflect;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.BiConsumer;

@SuppressWarnings("unchecked")
public class AccessibleSetterRetrieverForJDK9 implements BiConsumer<AccessibleObject, Boolean> {
	private static Method accessibleSetterMethod;
	
	static {
		try {
			accessibleSetterMethod = AccessibleObject.class.getDeclaredMethod("setAccessible0", boolean.class);
			accessibleSetterMethod.setAccessible(true);
		} catch (NoSuchMethodException | SecurityException exc) {
			throwException(exc);
		}
		
	}

	private static <E extends Throwable> void throwException(Throwable exc) throws E{
		throw (E)exc;
	}

	@Override
	public void accept(AccessibleObject accessibleObject, Boolean flag) {
		try {
			accessibleSetterMethod.invoke(accessibleObject, flag);
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException exc) {
			throwException(exc);
		}		
	}

}