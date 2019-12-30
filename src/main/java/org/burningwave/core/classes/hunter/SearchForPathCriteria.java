package org.burningwave.core.classes.hunter;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.function.Function;

import org.burningwave.core.classes.ClassHelper;
import org.burningwave.core.classes.MemberFinder;
import org.burningwave.core.common.Strings;

public class SearchForPathCriteria extends SearchCriteriaAbst<SearchForPathCriteria> {
	int maxParallelTasksForUnit;
	Collection<String> paths;	
	
	SearchForPathCriteria() {
		super();
		paths = ConcurrentHashMap.newKeySet();
		maxParallelTasksForUnit = Runtime.getRuntime().availableProcessors();
	}
	
	@Override
	public SearchForPathCriteria logicOperation(SearchForPathCriteria leftCriteria, SearchForPathCriteria rightCriteria,
			Function<BiPredicate<TestContext<SearchForPathCriteria>, Class<?>>, Function<BiPredicate<? super TestContext<SearchForPathCriteria>, ? super Class<?>>, BiPredicate<TestContext<SearchForPathCriteria>, Class<?>>>> binaryOperator,
			SearchForPathCriteria targetCriteria) {
		return super.logicOperation(leftCriteria, rightCriteria, binaryOperator, targetCriteria)
			.addPaths(leftCriteria.getPaths())
			.addPaths(rightCriteria.getPaths())
			.maxParallelTasksForUnit((leftCriteria.maxParallelTasksForUnit + rightCriteria.maxParallelTasksForUnit) / 2);
	}	
	
	void init(ClassHelper classHelper, PathMemoryClassLoader classSupplier, MemberFinder memberFinder) {
		super.init(classHelper, classSupplier, memberFinder);
		Set<String> temp = new LinkedHashSet<String>(paths);
		paths.clear();
		for(String path : temp) {
			paths.add(Strings.Paths.clean(path));
		}
		temp.clear();
	}
	
	public SearchForPathCriteria maxParallelTasksForUnit(int value) {
		this.maxParallelTasksForUnit = value;
		return this;
	}
	
	public SearchForPathCriteria addPaths(Collection<String> paths) {
		this.paths.addAll(paths);
		return this;
	}
	
	public Collection<String> getPaths() {
		return paths;
	}
	
	
	@Override
	public SearchForPathCriteria createCopy() {
		SearchForPathCriteria copy = super.createCopy();
		copy.paths.addAll(this.getPaths());
		copy.maxParallelTasksForUnit = this.maxParallelTasksForUnit;
		return copy;
	}
	
	@Override
	public void close() {
		paths.clear();
		paths = null;
		super.close();
	}
}