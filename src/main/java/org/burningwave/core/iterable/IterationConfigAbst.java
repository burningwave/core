package org.burningwave.core.iterable;

import java.util.Collection;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

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
@SuppressWarnings("unchecked")
abstract class IterationConfigAbst<I> implements IterableObjectHelper.IterationConfig<I, IterationConfigAbst<I>>{
	Collection<I> items;
	Object action;
	Object output;
	Predicate<Collection<?>> predicateForParallelIteration;
	Integer priority;

	public IterationConfigAbst(
		Collection<I> items
	) {
		this.items = items;
	}

	public <O> IterationConfigAbst<I> withAction(BiConsumer<I, Consumer<Consumer<O>>> action) {
		this.action = action;
		return this;
	}

	@Override
	public IterationConfigAbst<I> withAction(Consumer<I> action) {
		BiConsumer<I, Consumer<Consumer<?>>> newAction = (item, outputItemCollector) -> action.accept(item);
		this.action = newAction;
		return this;
	}

	@Override
	public IterationConfigAbst<I> withPriority(Integer priority) {
		this.priority = priority;
		return this;
	}

	@Override
	public IterationConfigAbst<I> parallelIf(Predicate<Collection<?>> predicate) {
		this.predicateForParallelIteration = predicate;
		return this;
	}

	public IterationConfigAbst<I> collectTo(Object output) {
		this.output = output;
		return this;
	}

	static abstract class WithOutput<I, W extends WithOutput<I, W>> implements IterableObjectHelper.IterationConfig<I, W> {
		IterationConfigAbst<I> wrappedConfiguration;

		WithOutput(IterationConfigAbst<I> configuration) {
			this.wrappedConfiguration = configuration;
		}

		@Override
		public W withAction(Consumer<I> action) {
			wrappedConfiguration.withAction(action);
			return (W)this;
		}

		@Override
		public <O> WithOutputOfCollection<I, O> withOutput(Collection<O> output) {
			return new WithOutputOfCollection<>(wrappedConfiguration);
		}

		@Override
		public <K, O> WithOutputOfMap<I, K, O> withOutput(Map<K, O> output) {
			return new WithOutputOfMap<>(wrappedConfiguration);
		}

		@Override
		public W parallelIf(Predicate<Collection<?>> predicate) {
			wrappedConfiguration.parallelIf(predicate);
			return (W)this;
		}

		@Override
		public W withPriority(Integer priority) {
			wrappedConfiguration.withPriority(priority);
			return (W)this;
		}

		IterationConfigAbst<I> getWrappedConfiguration() {
			return wrappedConfiguration;
		}
	}

}
