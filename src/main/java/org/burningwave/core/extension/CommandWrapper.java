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
 * Copyright (c) 2019 Roberto Gentili
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
package org.burningwave.core.extension;

import org.burningwave.core.Component;

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