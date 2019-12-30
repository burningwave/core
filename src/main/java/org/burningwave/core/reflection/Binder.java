package org.burningwave.core.reflection;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.burningwave.core.Component;
import org.burningwave.core.classes.MemberFinder;

public interface Binder extends Component {
	
	public <F> F bindTo(Object targetObject, String methodName, Class<?>... types) throws Throwable;
	
	public <F> F bindTo(Object targetObject, Method method) throws Throwable;
	
	static abstract class Abst implements Binder {
		MemberFinder memberFinder;
		
		Abst(MemberFinder memberFinder) {
			this.memberFinder = memberFinder;
		}
	}
	
	public interface Multi extends Binder {
		
		public <F, I> Map<I, F> bindToMany(Object targetObject, String methodName) throws Throwable;
		
		static abstract class Abst extends Binder.Abst implements Multi {
			
			Function<Integer, Class<?>> classRetriever;
			MemberFinder memberFinder;
			CallerRetriever lambdaCallerRetriever;

			Abst(
				MemberFinder memberFinder,
				CallerRetriever lambdaCallerRetriever,
				Function<Integer, Class<?>> classRetriever) {
				super(memberFinder);
				this.classRetriever = classRetriever;
				this.lambdaCallerRetriever = lambdaCallerRetriever;
			}

			Class<?> retrieveClass(Class<?> cls, int parametersCount) throws ClassNotFoundException {
				if (parametersCount < 3) {
					return Class.forName(retrieveClassName(cls, parametersCount));	
				} else {
					return classRetriever.apply(parametersCount);
	
				}
			}

			String retrieveClassName(Class<?>cls, int parametersCount) {
				switch (parametersCount) {
		        	case 2:
		        		return Optional.ofNullable(cls.getPackage()).map(pkg -> pkg.getName()).orElse(null) + "Bi" + cls.getSimpleName();
		        	default : 
		        		return cls.getName();
				}
			}
			
			<F, I> Map<I, F> createResultMap() {
				return new LinkedHashMap<I, F>() {
					private static final long serialVersionUID = -5808566429515776779L;
	
					@Override
					public F get(Object key) {
						if (key instanceof Class[] || key instanceof Class) {
							final Class<?>[] params = key.getClass().isArray()?
								(Class<?>[] )key :
									new Class<?>[] {(Class<?>)key};
							
							key = this.keySet().stream().filter((method) -> {
								Class<?>[] mthParams = ((Method)method).getParameterTypes();
								if (mthParams.length != params.length) {
									return false;
								}
								for (int i = 0; i < mthParams.length; i++) {
									if (mthParams[i] != params[i]) {
										return false;
									}
								}
	
								return true;
							}).findFirst().orElse(null);
						}
						return super.get(key);
					}
				};
			}
		}
	}
}