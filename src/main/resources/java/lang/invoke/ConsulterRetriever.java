package java.lang.invoke;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.util.function.Function;

@SuppressWarnings("unchecked")
public class ConsulterRetriever implements Function<Class<?>, MethodHandles.Lookup> {
	private static MethodHandle consulterRetrieverMethod;
	
	static {
		try {
			MethodHandles.Lookup consulter = MethodHandles.lookup();
			try {
				consulterRetrieverMethod = consulter.findStatic(
					MethodHandles.class, "privateLookupIn",
					MethodType.methodType(MethodHandles.Lookup.class, Class.class, MethodHandles.Lookup.class)
				);
			} catch (IllegalAccessException exc) {
				throwException(exc);
			}			
		} catch (NoSuchMethodException | SecurityException exc) {
			throwException(exc);
		}
		
	}
	
	@Override
	public Lookup apply(Class<?> cls) {
		try {
			return (MethodHandles.Lookup)consulterRetrieverMethod.invoke(cls, MethodHandles.lookup());
		} catch (Throwable exc) {
			return throwExceptionWithReturn(exc);
		}
	}
	
	private static <T> T throwExceptionWithReturn(Throwable exc) {
		throwException(exc);
		return null;
	}

	private static <E extends Throwable> void throwException(Throwable exc) throws E{
		throw (E)exc;
	}

}
