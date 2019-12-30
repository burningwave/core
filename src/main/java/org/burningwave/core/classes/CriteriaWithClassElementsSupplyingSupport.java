package org.burningwave.core.classes;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiPredicate;
import java.util.function.Function;

import org.burningwave.core.Criteria;
import org.burningwave.core.common.Streams;

@SuppressWarnings("unchecked")
public abstract class CriteriaWithClassElementsSupplyingSupport<
	E, 
	C extends CriteriaWithClassElementsSupplyingSupport<E, C, T>,
	T extends Criteria.TestContext<E, C>
> extends Criteria<E, C, T>  {
	protected Map<Class<?>[], List<Class<?>>> uploadedClassesMap;
	protected List<Class<?>> classesToBeUploaded;
	protected Map<Class<?>, Class<?>> uploadedClasses;
	protected Function<Class<?>, Class<?>> classSupplier;
	
	protected Map<Class<?>[], List<ByteBuffer>> byteCodeForClasses;
	protected Map<Class<?>, byte[]> loadedBytecode;
	protected Function<Class<?>, ByteBuffer> byteCodeSupplier;
	
	protected CriteriaWithClassElementsSupplyingSupport() {}
	
	public C init(Function<Class<?>, Class<?>> classSupplier, Function<Class<?>, ByteBuffer> byteCodeSupplier) {
		if (classSupplier != null) {
			this.classSupplier = classSupplier;
			this.uploadedClassesMap = new ConcurrentHashMap<>();
		}
		if (byteCodeSupplier != null) {
			this.byteCodeSupplier = byteCodeSupplier;
			this.byteCodeForClasses = new ConcurrentHashMap<>();
		}
		return (C)this;
	}
	
	@Override
	protected C logicOperation(C leftCriteria, C rightCriteria,
		Function<BiPredicate<T, E>, Function<BiPredicate<? super T, ? super E>, BiPredicate<T, E>>> binaryOperator,
		C targetCriteria
	) {
		C newCriteria = super.logicOperation(leftCriteria, rightCriteria, binaryOperator, targetCriteria);
		if (leftCriteria.classesToBeUploaded != null) {
			newCriteria.useClasses(leftCriteria.classesToBeUploaded);
		}
		if (rightCriteria.classesToBeUploaded != null) {
			newCriteria.useClasses(rightCriteria.classesToBeUploaded);
		}
		return newCriteria;
	}
	
	public Function<Class<?>, Class<?>> getClassSupplier() {
		return classSupplier;
	}
	
	public Function<Class<?>, ByteBuffer> getByteCodeSupplier() {
		return byteCodeSupplier;
	}
	
	public Class<?> retrieveClass(Class<?> cls) {
		if (classSupplier != null) {
			return classSupplier.apply(cls);
		}
		return cls;
	}
	
	protected Map<Class<?>, Class<?>> getUploadedClasses() {
		if (uploadedClasses == null) {
			synchronized (this) {
				if (uploadedClasses == null) {
					Map<Class<?>, Class<?>> uploadedClasses = new ConcurrentHashMap<>();
					for (Class<?> cls : classesToBeUploaded) {
						uploadedClasses.put(cls, classSupplier.apply(cls));
					}
					this.uploadedClasses = uploadedClasses;
				}
			}
		}
		return uploadedClasses;
	}
	
	public List<Class<?>> getClassesToBeUploaded() {
		return classesToBeUploaded;
	}
	
	public C useClasses(Class<?>... classes) {
		if (classesToBeUploaded == null) {
			classesToBeUploaded = new CopyOnWriteArrayList<>();
		}
		for (Class<?> cls : classes) {
			classesToBeUploaded.add(cls);
		}
		return (C)this;
	}
	
	
	public C useClasses(Collection<Class<?>> classes) {
		if (classesToBeUploaded == null) {
			classesToBeUploaded = new CopyOnWriteArrayList<>();
		}
		classesToBeUploaded.addAll(classes);
		return (C)this;
	}
	
	protected List<Class<?>> retrieveUploadedClasses(Class<?>... classes) {
		List<Class<?>> uploadedClasses = uploadedClassesMap.get(classes);
		if (uploadedClasses == null) {
			synchronized(uploadedClassesMap) {
				if ((uploadedClasses = uploadedClassesMap.get(classes)) == null) {
					uploadedClasses = new CopyOnWriteArrayList<>();
					for (int i = 0; i < classes.length; i++) {
						uploadedClasses.add(classSupplier.apply(classes[i]));
					}
					uploadedClassesMap.put(
						classes, uploadedClasses
					);
				}
			}
		}
		return uploadedClasses;
	}
	
	protected Map<Class<?>, byte[]> getLoadedBytecode() {
		if (loadedBytecode == null) {
			synchronized (this) {
				Map<Class<?>, byte[]> loadedBytecode = new ConcurrentHashMap<>();
				for (Class<?> cls : classesToBeUploaded) {
					loadedBytecode.put(cls, Streams.toByteArray(byteCodeSupplier.apply(cls)));
				}
				this.loadedBytecode = loadedBytecode;
			}
		}
		return loadedBytecode;
	}
	
	protected List<ByteBuffer> retrieveByteCode(C criteria, Class<?>[] classes) {
		List<ByteBuffer> byteCode = criteria.byteCodeForClasses.get(classes);
		if (byteCode == null) {
			synchronized (criteria.byteCodeForClasses) {
				byteCode = criteria.byteCodeForClasses.get(classes);
				if (byteCode == null) {
					byteCode = new CopyOnWriteArrayList<>();
					for (Class<?> cls : classes) {
						byteCode.add(byteCodeSupplier.apply(cls));
					}
					criteria.byteCodeForClasses.put(classes, byteCode);
				}
			}
		}
		return byteCode;
	}
	
	@Override
	public C createCopy() {
		C copy = super.createCopy();
		if (this.classesToBeUploaded != null) {
			copy.useClasses(this.classesToBeUploaded);
		}		
		return copy;
	}
	
	@Override
	public void close() {
		uploadedClassesMap.clear();
		uploadedClassesMap = null;
		classesToBeUploaded.clear();
		classesToBeUploaded = null;
		uploadedClasses.clear();
		uploadedClasses = null;
		byteCodeForClasses.clear();
		byteCodeForClasses = null;
		loadedBytecode.clear();
		loadedBytecode = null;
		classSupplier = null;
		byteCodeSupplier = null;
		super.close();
	}
}
