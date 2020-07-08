package org.burningwave.core.classes;

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

public class ExecutableMemberHelper<E extends Executable> extends MemberHelper<E> {
	
	List<Object> getArgumentList(Executable method, Object... arguments) {
		Parameter[] parameters = method.getParameters();
		List<Object> argumentList = new ArrayList<>();
		if (arguments != null) {
			for (Object arg : arguments) {
				argumentList.add(arg);
			}
			if (parameters.length > 0 && parameters[parameters.length - 1].isVarArgs() && arguments.length < parameters.length) {
				argumentList.add(null);
			}
		} else {
			argumentList.add(null);
		}
		return argumentList;
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
	
}
