package org.burningwave.core.classes.hunter;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SearchCriteria extends SearchCriteriaAbst<SearchCriteria>{
	
	private SearchCriteria() {
		super();
	}
	
	public static SearchCriteria create() {
		return new SearchCriteria();
	}

	public static SearchForPathCriteria forPaths(Collection<String> paths) {
		SearchForPathCriteria criteria = new SearchForPathCriteria();
		criteria.paths.addAll(paths);
		criteria.useSharedClassLoaderAsMain = true;
		return criteria;
	}
	
	public static SearchForPathCriteria forPaths(String... paths) {
		return SearchCriteria.forPaths(Stream.of(paths).collect(Collectors.toCollection(ConcurrentHashMap::newKeySet)));
	}
}
