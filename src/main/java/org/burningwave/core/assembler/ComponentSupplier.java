package org.burningwave.core.assembler;

import java.util.function.Supplier;

import org.burningwave.core.Component;
import org.burningwave.core.classes.ClassFactory;
import org.burningwave.core.classes.ClassHelper;
import org.burningwave.core.classes.CodeGenerator;
import org.burningwave.core.classes.ConstructorHelper;
import org.burningwave.core.classes.FieldHelper;
import org.burningwave.core.classes.JavaMemoryCompiler;
import org.burningwave.core.classes.MemberFinder;
import org.burningwave.core.classes.MemoryClassLoader;
import org.burningwave.core.classes.MethodHelper;
import org.burningwave.core.classes.hunter.ByteCodeHunter;
import org.burningwave.core.classes.hunter.ClassHunter;
import org.burningwave.core.classes.hunter.ClassPathHunter;
import org.burningwave.core.concurrent.ConcurrentHelper;
import org.burningwave.core.io.FileSystemHelper;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.io.StreamHelper;
import org.burningwave.core.iterable.IterableObjectHelper;
import org.burningwave.core.reflection.CallerRetriever;
import org.burningwave.core.reflection.ConsumerBinder;
import org.burningwave.core.reflection.FunctionBinder;
import org.burningwave.core.reflection.FunctionalInterfaceFactory;
import org.burningwave.core.reflection.ObjectRetriever;
import org.burningwave.core.reflection.PropertyAccessor;
import org.burningwave.core.reflection.RunnableBinder;
import org.burningwave.core.reflection.SupplierBinder;

public interface ComponentSupplier extends Component {
	
	public<T extends Component> T getOrCreate(Class<T> componentType, Supplier<T> componentSupplier);

	public ConstructorHelper getConstructorHelper();

	public MethodHelper getMethodHelper();

	public FieldHelper getFieldHelper();

	public MemberFinder getMemberFinder();

	public MemoryClassLoader getMemoryClassLoader();

	public ClassFactory getClassFactory();
	
	public ClassHelper getClassHelper();

	public JavaMemoryCompiler getJavaMemoryCompiler();

	public CodeGenerator.ForConsumer getCodeGeneratorForConsumer();

	public CodeGenerator.ForFunction getCodeGeneratorForFunction();
	
	public CodeGenerator.ForPredicate getCodeGeneratorForPredicate();
	
	public CodeGenerator.ForPojo getCodeGeneratorForPojo();
	
	public CodeGenerator.ForCodeExecutor getCodeGeneratorForCodeExecutor();
	
	public ByteCodeHunter getByteCodeHunter();
	
	public ClassPathHunter getClassPathHunter();
	
	public ClassHunter getClassHunter();

	public PropertyAccessor.ByFieldOrByMethod getByFieldOrByMethodPropertyAccessor();
	
	public PropertyAccessor.ByMethodOrByField getByMethodOrByFieldPropertyAccessor();

	public RunnableBinder getRunnableBinder();

	public SupplierBinder getSupplierBinder();

	public ConsumerBinder getConsumerBinder();

	public FunctionBinder getFunctionBinder();

	public FunctionalInterfaceFactory getFunctionalInterfaceFactory();

	public CallerRetriever getLambdaCallerRetriever();

	public PathHelper getPathHelper();

	public StreamHelper getStreamHelper();

	public FileSystemHelper getFileSystemHelper();
	
	public ConcurrentHelper getConcurrentHelper();
	
	public IterableObjectHelper getIterableObjectHelper();
	
	public ObjectRetriever getObjectRetriever();
	
	public static ComponentSupplier getInstance() {
		return ComponentContainer.getInstance();
	}
}