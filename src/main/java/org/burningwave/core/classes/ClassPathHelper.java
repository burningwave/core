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
 * Copyright (c) 2019-2023 Roberto Gentili
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

import static org.burningwave.core.assembler.StaticComponentContainer.Strings;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.burningwave.core.io.FileSystemItem;

public interface ClassPathHelper {

	public static abstract class Configuration {

		public static abstract class Key {

			public static final String CLASS_PATH_HUNTER_SEARCH_CONFIG_CHECK_FILE_OPTIONS = "class-path-helper.class-path-hunter.search-config.check-file-option";

		}

		public final static Map<String, Object> DEFAULT_VALUES;

		static {
			Map<String, Object> defaultValues = new HashMap<>();

			defaultValues.put(
				Key.CLASS_PATH_HUNTER_SEARCH_CONFIG_CHECK_FILE_OPTIONS,
				"${" + ClassPathScanner.Configuration.Key.DEFAULT_CHECK_FILE_OPTIONS + "}"
			);

			DEFAULT_VALUES = Collections.unmodifiableMap(defaultValues);
		}
	}

	public static ClassPathHelper create(ClassPathHunter classPathHunter, Map<?, ?> config) {
		return new ClassPathHelperImpl(classPathHunter, config);
	}

	public Supplier<Map<String, String>> compute(SearchConfig searchConfig);

	public Supplier<Map<String, String>> compute(Compute.ByClasses.Config input);

	public Supplier<Map<String, String>> compute(Compute.ByClasses.AndBySourceImportsConfig input);

	public Supplier<Map<String, String>> compute(Compute.Config input);

	public Supplier<Map<String, String>> compute(Compute.BySourceImportsConfig input);

	public Map<String, ClassLoader> compute(Compute.AndAddToClassLoaderConfig input);

	public static interface Compute {

		public static class Config {

			public Collection<String> classRepositories;
			public Predicate<FileSystemItem> pathsToBeRefreshedPredicate;
			public Predicate<FileSystemItem> fileFilter;

			Config(Collection<String> classRepositories) {
				this.classRepositories = classRepositories;
			}

			public static Config create(Collection<String> classRepositories) {
				if (classRepositories == null) {
					throw new IllegalArgumentException("No class repository has been provided");
				}
				return new Config(classRepositories);
			}

			public Config refreshAllPathsThat(Predicate<FileSystemItem> pathsToBeRefreshedPredicate) {
				this.pathsToBeRefreshedPredicate = pathsToBeRefreshedPredicate;
				return this;
			}

			public Config withFileFilter(Predicate<FileSystemItem> javaClassFilter) {
				this.fileFilter = javaClassFilter;
				return this;
			}

		}

		public static class AndAddToClassLoaderConfig {
			public ClassLoader classLoader;
			public Collection<String> classRepositories;
			public Collection<String> pathsToBeRefreshed;
			public String nameOfTheClassToBeLoaded;
			public Collection<String> nameOfTheClassesRequiredByTheClassToBeLoaded;

			AndAddToClassLoaderConfig(ClassLoader classLoader, Collection<String> classRepositories, String nameOfTheClassToBeLoaded) {
				this.classLoader = classLoader;
				this.classRepositories = classRepositories;
				this.nameOfTheClassToBeLoaded = nameOfTheClassToBeLoaded;
			}

			public static AndAddToClassLoaderConfig create(
				ClassLoader classLoader, Collection<String> classRepositories, String nameOfTheClassToBeLoaded
			) {
				if (classLoader == null) {
					throw new IllegalArgumentException("No class loader has been provided");
				}
				if (classRepositories == null) {
					throw new IllegalArgumentException("No class repository has been provided");
				}
				if (Strings.isEmpty(nameOfTheClassToBeLoaded)) {
					throw new IllegalArgumentException("No class name to be found has been provided");
				}
				return new AndAddToClassLoaderConfig(classLoader, classRepositories, nameOfTheClassToBeLoaded);
			}

			public AndAddToClassLoaderConfig setClassesRequiredByTheClassToBeLoaded(Collection<String> nameOfTheClassesRequiredByTheClassToBeLoaded) {
				this.nameOfTheClassesRequiredByTheClassToBeLoaded = nameOfTheClassesRequiredByTheClassToBeLoaded;
				return this;
			}

			public AndAddToClassLoaderConfig refreshPaths(Collection<String> pathsToBeRefreshed) {
				this.pathsToBeRefreshed = pathsToBeRefreshed;
				return this;
			}

		}

		public static class BySourceImportsConfig {
			public Collection<String> sources;
			public Collection<String> classRepositories;
			public Predicate<FileSystemItem> pathsToBeRefreshedPredicate;
			public Predicate<FileSystemItem> additionalFileFilter;

			BySourceImportsConfig(Collection<String> sources, Collection<String> classRepositories) {
				this.sources = sources;
				this.classRepositories = classRepositories;
			}

			public static BySourceImportsConfig create(
				Collection<String>sources, Collection<String> classRepositories
			) {
				if (sources == null) {
					throw new IllegalArgumentException("No source has been provided");
				}
				if (classRepositories == null) {
					throw new IllegalArgumentException("No class repository has been provided");
				}
				return new BySourceImportsConfig(sources, classRepositories);
			}

			public BySourceImportsConfig refreshAllPathsThat(Predicate<FileSystemItem> pathsToBeRefreshedPredicate) {
				this.pathsToBeRefreshedPredicate = pathsToBeRefreshedPredicate;
				return this;
			}

			public BySourceImportsConfig withAdditionalFileFilter(Predicate<FileSystemItem> additionalFileFilter) {
				this.additionalFileFilter = additionalFileFilter;
				return this;
			}
		}

		public interface ByClasses {

			public static class Config {
				Collection<String> classRepositories;
				Collection<String> pathsToBeRefreshed;
				ClassCriteria classCriteria;

				Config(Collection<String> classRepositories) {
					this.classRepositories = classRepositories;
				}

				public static Config create(Collection<String> classRepositories) {
					if (classRepositories == null) {
						throw new IllegalArgumentException("No class repository has been provided");
					}
					return new Config(classRepositories);
				}

				public Config refreshPaths(Collection<String> pathsToBeRefreshed) {
					this.pathsToBeRefreshed = pathsToBeRefreshed;
					return this;
				}

				public Config withClassFilter(ClassCriteria classCriteria) {
					this.classCriteria = classCriteria;
					return this;
				}
			}

			public static class AndBySourceImportsConfig {
				public Collection<String> sources;
				public Collection<String> classRepositories;
				public ClassCriteria additionalClassCriteria;

				AndBySourceImportsConfig(Collection<String> sources, Collection<String> classRepositories) {
					this.sources = sources;
					this.classRepositories = classRepositories;
				}


				public static ByClasses.AndBySourceImportsConfig create(
					Collection<String>sources, Collection<String> classRepositories
				) {
					if (sources == null) {
						throw new IllegalArgumentException("No source has been provided");
					}
					if (classRepositories == null) {
						throw new IllegalArgumentException("No class repository has been provided");
					}
					return new ByClasses.AndBySourceImportsConfig(sources, classRepositories);
				}

				public AndBySourceImportsConfig setClassRepositories(Collection<String> classRepositories) {
					this.classRepositories = classRepositories;
					return this;
				}

				public AndBySourceImportsConfig withAdditionalClassFilter(ClassCriteria additionalClassCriteria) {
					this.additionalClassCriteria = additionalClassCriteria;
					return this;
				}
			}
		}
	}
}
