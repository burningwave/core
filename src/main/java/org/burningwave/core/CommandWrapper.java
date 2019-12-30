package org.burningwave.core;

public abstract class CommandWrapper<T, I, O>  implements Component {
	T command;
	Object target;
	
	public Object getTarget() {
		return target;
	}

	public abstract O executeOn(I data);
	
	@SuppressWarnings("unchecked")
	public static <F, I, O, W extends CommandWrapper<F ,I, O>> W create(
		F functionInterface, Object instance) throws Throwable {
		if (functionInterface instanceof java.lang.Runnable) {
			return (W)new Runnable<I, O>(instance, (java.lang.Runnable)functionInterface);
		} else if (functionInterface instanceof java.util.function.Consumer) {
			return (W)new Consumer<I, O>(instance, (java.util.function.Consumer<I>)functionInterface);
		} else if (functionInterface instanceof java.util.function.Supplier) {
			return (W)new Supplier<I, O>(instance, ((java.util.function.Supplier<O>)functionInterface));
		} else if (functionInterface instanceof java.util.function.Function) {
			return (W)new Function<I, O>(instance, ((java.util.function.Function<I, O>)functionInterface));
		} 
		return null;
	}

	CommandWrapper(Object target, T t) {
		this.target = target;
		this.command = t;
	}
	
	static class Function<I, O> extends CommandWrapper<java.util.function.Function<I, O>, I, O> {
		Function(Object target, java.util.function.Function<I, O> t) {
			super(target, t);
		}

		@Override
		public O executeOn(I data) {
			return command.apply(data);
		}

	}
	
	static class Consumer<I, O> extends CommandWrapper<java.util.function.Consumer<I>, I, O> {
		Consumer(Object target, java.util.function.Consumer<I> t) {
			super(target, t);
		}

		@SuppressWarnings("unchecked")
		@Override
		public O executeOn(I data) {
			command.accept(data);
			return (O)data;
		}
	}
	
	static class Supplier<I, O> extends CommandWrapper<java.util.function.Supplier<O>, I, O> {
		Supplier(Object target, java.util.function.Supplier<O> t) {
			super(target, t);
		}


		@Override
		public O executeOn(I data) {
			return command.get();
		}
	}
	
	static class Runnable<I, O> extends CommandWrapper<java.lang.Runnable, I, O> {

		Runnable(Object target, java.lang.Runnable t) {
			super(target, t);
		}

		@SuppressWarnings("unchecked")
		@Override
		public O executeOn(I data) {
			command.run();
			return (O)data;
		}
		
	}
	
	@Override
	public void close() {
		command = null;
		target = null;
	}

}