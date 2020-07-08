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

import static org.burningwave.core.assembler.StaticComponentContainer.Classes;

import java.lang.reflect.Array;
import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class ExecutableMemberHelper<E extends Executable> extends MemberHelper<E> {
	
	List<Object> getArgumentList(E member, Object... arguments) {
		Parameter[] parameters = member.getParameters();
		List<Object> argumentList = new ArrayList<>();
		if (arguments != null) {
			if (parameters.length == 1 && parameters[0].isVarArgs()) {
				Object array = Array.newInstance(parameters[0].getType().getComponentType(), arguments.length);
				for (int i=0; i< arguments.length; i++) {
					Array.set(array, i, arguments[i]);
				}
				argumentList.add(array);
			} else {
				for (Object arg : arguments) {
					argumentList.add(arg);
				}
				if (parameters.length > 0 && parameters[parameters.length - 1].isVarArgs() && arguments.length < parameters.length) {
					argumentList.add(null);
				}
			}
		} else {
			argumentList.add(null);
		}
		return argumentList;
	}
	
	Object[] getArgumentArray(E member, Object... arguments) {
		List<Object> argumentList = getArgumentList(member, arguments);
		return argumentList.toArray(new Object[argumentList.size()]);
	}
	
	Class<?>[] retrieveParameterTypes(Executable member, List<Class<?>> argumentsClassesAsList) {
		Parameter[] memberParameter = member.getParameters();
		Class<?>[] memberParameterTypes = member.getParameterTypes();
		if (memberParameter.length > 0 && memberParameter[memberParameter.length - 1].isVarArgs()) {
			Class<?> varArgsType = 
				argumentsClassesAsList.size() > 0 && 
				argumentsClassesAsList.get(argumentsClassesAsList.size()-1) != null &&
				argumentsClassesAsList.get(argumentsClassesAsList.size()-1).isArray()?
				memberParameter[memberParameter.length - 1].getType():
				memberParameter[memberParameter.length - 1].getType().getComponentType();
			if (memberParameter.length == 1) {
				memberParameterTypes = new Class<?>[argumentsClassesAsList.size()];
				for (int j = 0; j < memberParameterTypes.length; j++) {
					memberParameterTypes[j] = varArgsType;
				}
			} else if (memberParameter.length - 1 <= argumentsClassesAsList.size()) {
				memberParameterTypes = new Class<?>[argumentsClassesAsList.size()];
				for (int j = 0; j < memberParameterTypes.length; j++) {
					if (j < (memberParameter.length - 1)) {
						memberParameterTypes[j] = memberParameter[j].getType();
					} else {
						memberParameterTypes[j] = varArgsType;
					}
				}
			}
		}
		return memberParameterTypes;
	}
	

	E searchForExactMatch(Collection<E> members, Object... arguments) {
		for (E executable : members) {
			List<Class<?>> argumentsClassesAsList = Arrays.asList(Classes.retrieveFrom(arguments));
			Class<?>[] parameterTypes = retrieveParameterTypes(executable, argumentsClassesAsList);
			boolean exactMatch = true;
			for (int i = 0; i < parameterTypes.length; i++) {
				if (argumentsClassesAsList.get(i) != null && !argumentsClassesAsList.get(i).equals(parameterTypes[i])) {
					exactMatch = false;
				}
			}
			if (exactMatch) {
				return executable;
			}
		}
		return null;
	}
}
