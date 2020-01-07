package org.burningwave.core.classes.hunter;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SearchConfig extends SearchConfigAbst<SearchConfig>{
	
	private SearchConfig() {
		super();
	}
	
	public static SearchConfig create() {
		return new SearchConfig();
	}

	public static SearchConfigForPath forPaths(Collection<String> paths) {
		SearchConfigForPath criteria = new SearchConfigForPath();
		criteria.paths.addAll(paths);
		criteria.useSharedClassLoaderAsMain = true;
		return criteria;
	}
	
	public static SearchConfigForPath forPaths(String... paths) {
		return SearchConfig.forPaths(Stream.of(paths).collect(Collectors.toCollection(ConcurrentHashMap::newKeySet)));
	}
}
