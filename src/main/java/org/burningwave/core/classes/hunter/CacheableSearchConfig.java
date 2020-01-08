package org.burningwave.core.classes.hunter;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.burningwave.core.classes.ClassHelper;
import org.burningwave.core.classes.MemberFinder;
import org.burningwave.core.common.Strings;

public class CacheableSearchConfig extends SearchConfigAbst<CacheableSearchConfig> {
	int maxParallelTasksForUnit;
	Collection<String> paths;	
	
	CacheableSearchConfig() {
		super();
		paths = ConcurrentHashMap.newKeySet();
		maxParallelTasksForUnit = Runtime.getRuntime().availableProcessors();
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
	
	public CacheableSearchConfig maxParallelTasksForUnit(int value) {
		this.maxParallelTasksForUnit = value;
		return this;
	}
	
	public CacheableSearchConfig addPaths(Collection<String> paths) {
		this.paths.addAll(paths);
		return this;
	}
	
	public Collection<String> getPaths() {
		return paths;
	}
	
	
	@Override
	public CacheableSearchConfig createCopy() {
		CacheableSearchConfig copy = super.createCopy();
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