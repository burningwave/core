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
package org.burningwave.core.classes;

import static org.burningwave.core.assembler.StaticComponentContainer.Members;

import java.lang.reflect.Member;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.burningwave.core.Component;

abstract class MemberHelper<M extends Member> implements Component {	

	@SuppressWarnings("unchecked")
	<C extends MemberCriteria<M, C, ?>> Collection<M> findAllAndApply(C criteria, Class<?> targetClass, Consumer<M>... consumers) {
		Collection<M> members = Members.findAll(criteria, targetClass);
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
	<C extends MemberCriteria<M, C, ?>> M findOneAndApply(C criteria, Class<?> targetClass, Consumer<M>... consumers) {
		M member = Members.findOne(criteria, targetClass);
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
	
	String getCacheKey(Class<?> targetClass, String groupName, Class<?>... arguments) {
		if (arguments == null) {
			arguments = new Class<?>[] {null};
		}
		String argumentsKey = "";
		if (arguments != null && arguments.length > 0) {
			StringBuffer argumentsKeyStringBuffer = new StringBuffer();
			Stream.of(arguments).forEach(cls ->
				argumentsKeyStringBuffer.append("/" + Optional.ofNullable(cls).map(Class::getName).orElseGet(() ->"null"))
			);
			argumentsKey = argumentsKeyStringBuffer.toString();
		}
		String cacheKey = "/" + targetClass.getName() + "@" + targetClass.hashCode() +
			"/" + groupName +
			argumentsKey;
		return cacheKey;		
	}
}
