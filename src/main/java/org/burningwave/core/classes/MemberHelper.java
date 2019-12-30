package org.burningwave.core.classes;

import java.lang.reflect.Member;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.burningwave.core.Component;
import org.burningwave.core.common.Classes;

public abstract class MemberHelper<M extends Member> implements Component {
	MemberFinder memberFinder;
	Map<String, Collection<M>> cache;
	
	public MemberHelper(MemberFinder memberFinder) {
		this.memberFinder = memberFinder;
		cache = new ConcurrentHashMap<>();
	}
	

	@SuppressWarnings("unchecked")
	<C extends MemberCriteria<M, C, ?>> Collection<M> findAllAndApply(C criteria, Object target, Consumer<M>... consumers) {
		Collection<M> members = memberFinder.findAll(criteria, target);
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
		M member = memberFinder.findOne(criteria, target);
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
				argumentsKeyStringBuffer.append("[" + cls + "]")
			);
			argumentsKey = "[" + argumentsKeyStringBuffer.toString() + "]";
		}
		Class<?> targetClass = Classes.retrieveFrom(target);
		String cacheKey = "[" + targetClass.getClassLoader() + "_" + targetClass.getName() + "]" + 
			"[" + memberName + "]" +
			argumentsKey;
		return cacheKey;		
	}
	
	@Override
	public void close() {
		cache.clear();
		memberFinder = null;
	}

}
