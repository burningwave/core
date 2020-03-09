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
package org.burningwave.core.reflection;

import static org.burningwave.core.assembler.StaticComponentContainer.Classes;
import static org.burningwave.core.assembler.StaticComponentContainer.MemberFinder;

import java.lang.reflect.Member;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.burningwave.core.Component;
import org.burningwave.core.classes.MemberCriteria;

public abstract class MemberHelper<M extends Member> implements Component {
	
	public MemberHelper() {}
	

	@SuppressWarnings("unchecked")
	<C extends MemberCriteria<M, C, ?>> Collection<M> findAllAndApply(C criteria, Object target, Consumer<M>... consumers) {
		Collection<M> members = MemberFinder.findAll(criteria, target);
		Optional.ofNullable(consumers).ifPresent(cnsms -> 
			members.stream().forEach(member -> 
				Stream.of(cnsms).filter(consumer -> 
					consumer != null
				).forEach(consumer ->
					consumer.accept(member)
				)
			)
		);
		return members;
	}
	
	@SuppressWarnings("unchecked")
	<C extends MemberCriteria<M, C, ?>> M findOneAndApply(C criteria, Object target, Consumer<M>... consumers) {
		M member = MemberFinder.findOne(criteria, target);
		Optional.ofNullable(consumers).ifPresent(cnsms -> 
			Optional.ofNullable(member).ifPresent(mmb -> 
				Stream.of(cnsms).filter(consumer -> 
					consumer != null
				).forEach(consumer ->
					consumer.accept(mmb)
				)
			)
		);
		return member;
	}
	
	String getCacheKey(Object target, String memberName, Object... arguments) {
		String argumentsKey = "";
		if (arguments != null && arguments.length > 0) {
			StringBuffer argumentsKeyStringBuffer = new StringBuffer();
			Stream.of(Classes.retrieveFrom(arguments)).forEach(cls ->
				argumentsKeyStringBuffer.append("/" + cls.getName())
			);
			argumentsKey = argumentsKeyStringBuffer.toString();
		}
		Class<?> targetClass = Classes.retrieveFrom(target);
		String cacheKey = "/" + Classes.getId(targetClass.getClassLoader()) + "/" + targetClass.getName() + "@" + targetClass.hashCode() +
			"/" + memberName +
			argumentsKey;
		return cacheKey;		
	}
	
	@Override
	public void close() {
		
	}

}
