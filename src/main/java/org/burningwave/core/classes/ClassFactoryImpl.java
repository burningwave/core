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

import static org.burningwave.core.assembler.StaticComponentContainer.BackgroundExecutor;
import static org.burningwave.core.assembler.StaticComponentContainer.IterableObjectHelper;
import static org.burningwave.core.assembler.StaticComponentContainer.Synchronizer;
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.util.Collection;
import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Supplier;

import org.burningwave.core.Component;
import org.burningwave.core.classes.JavaMemoryCompiler.Compilation;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.iterable.Properties;
import org.burningwave.core.iterable.Properties.Event;

public class ClassFactoryImpl implements ClassFactory, Component {
	PathHelper pathHelper;
	ClassPathHelper classPathHelper;
	JavaMemoryCompiler javaMemoryCompiler;
	ByteCodeHunter byteCodeHunter;
	private ClassPathHunter classPathHunter;
	private Supplier<ClassPathHunter> classPathHunterSupplier;
	private ClassLoaderManager<ClassLoader> defaultClassLoaderManager;
	private Collection<ClassRetriever> classRetrievers;
	Properties config;
	
	ClassFactoryImpl(
		ByteCodeHunter byteCodeHunter,
		Supplier<ClassPathHunter> classPathHunterSupplier,
		JavaMemoryCompiler javaMemoryCompiler,
		PathHelper pathHelper,
		ClassPathHelper classPathHelper,
		Object defaultClassLoaderOrDefaultClassLoaderSupplier,
		Properties config
	) {	
		this.byteCodeHunter = byteCodeHunter;
		this.classPathHunterSupplier = classPathHunterSupplier;
		this.javaMemoryCompiler = javaMemoryCompiler;
		this.pathHelper = pathHelper;
		this.classPathHelper = classPathHelper;
		this.defaultClassLoaderManager = new ClassLoaderManager<>(
			defaultClassLoaderOrDefaultClassLoaderSupplier
		);
		this.classRetrievers = new CopyOnWriteArrayList<>();
		this.config = config;
		listenTo(config);
	}
	
	@Override
	public <K, V> void processChangeNotification(Properties properties, Event event, K key, V newValue,
			V previousValue) {
		if (event.name().equals(Event.PUT.name())) {
			if (key instanceof String) {
				String keyAsString = (String)key;
				if (keyAsString.equals(Configuration.Key.DEFAULT_CLASS_LOADER)) {
					this.defaultClassLoaderManager.reset();
				}
			}
		}
	}
	
	ClassLoader getDefaultClassLoader(Object client) {
		return this.defaultClassLoaderManager.get(client);
	}
	
	ClassPathHunter getClassPathHunter() {
		return classPathHunter != null? classPathHunter :
			(classPathHunter = classPathHunterSupplier.get());
	}
	
	@Override
	public ClassRetriever loadOrBuildAndDefine(UnitSourceGenerator... unitsCode) {
		return loadOrBuildAndDefine(LoadOrBuildAndDefineConfig.forUnitSourceGenerator(unitsCode));
	}
	
	@Override
	public <L extends LoadOrBuildAndDefineConfigAbst<L>> ClassRetriever loadOrBuildAndDefine(L config) {
		if (config.isVirtualizeClassesEnabled()) {
			config.addClassPaths(pathHelper.getBurningwaveRuntimeClassPath());
		}
		return loadOrBuildAndDefine(
			config.getClassesName(),
			config.getCompileConfigSupplier(),			
			config.isUseOneShotJavaCompilerEnabled(),
			IterableObjectHelper.merge(
				() -> config.getClassRepositoriesWhereToSearchNotFoundClassesDuringLoading(),
				() -> config.getAdditionalClassRepositoriesWhereToSearchNotFoundClassesDuringLoading(),
				() -> {
					Collection<String> classRepositoriesForNotFoundClasses = pathHelper.getPaths(
						Configuration.Key.CLASS_REPOSITORIES_FOR_DEFAULT_CLASS_LOADER
					);
					if (!classRepositoriesForNotFoundClasses.isEmpty()) {
						config.addClassRepositoriesWhereToSearchNotFoundClasses(classRepositoriesForNotFoundClasses);
					}
					return classRepositoriesForNotFoundClasses;
				}
			),
			(client) -> Optional.ofNullable(
				config.getClassLoader()
			).orElseGet(() -> 
				getDefaultClassLoader(client)
			)
		);
	}
	
	private ClassRetriever loadOrBuildAndDefine(
		Collection<String> classNames,
		Supplier<Compilation.Config> compileConfigSupplier,		
		boolean useOneShotJavaCompiler,
		Collection<String> additionalClassRepositoriesForClassLoader,
		Function<Object, ClassLoader> classLoaderSupplier
	) {
		try {
			Object temporaryClient = new Object(){};
			ClassLoader classLoader = classLoaderSupplier.apply(temporaryClient);			
			return new ClassRetriever(
				this,
				 (classRetriever) -> {
					if (classLoader instanceof MemoryClassLoader) {
						((MemoryClassLoader)classLoader).register(classRetriever);
						((MemoryClassLoader)classLoader).unregister(temporaryClient, true);
						if (classLoader != this.defaultClassLoaderManager.get()) {
							((MemoryClassLoader) classLoader).unregister(this, true);
						}
					}
					return classLoader;
				},
				compileConfigSupplier,
				useOneShotJavaCompiler,
				additionalClassRepositoriesForClassLoader,
				classNames
			);
		} catch (Throwable exc) {
			return Throwables.throwException(exc);
		}
	}
	
	boolean register(ClassRetriever classRetriever) {
		classRetrievers.add(classRetriever);
		return true;
	}
	
	boolean unregister(ClassRetriever classRetriever) {
		classRetrievers.remove(classRetriever);
		return true;
	}
	
	@Override
	public void closeClassRetrievers() {
		Synchronizer.execute(getOperationId("closeClassRetrievers"), () -> {
			Collection<ClassRetriever> classRetrievers = this.classRetrievers;
			if (classRetrievers != null) {
				Iterator<ClassRetriever> classRetrieverIterator = classRetrievers.iterator();		
				while(classRetrieverIterator.hasNext()) {
					ClassRetriever classRetriever = classRetrieverIterator.next();
					classRetriever.close();
				}
			}
		});
	}
	
	@Override
	public void reset(boolean closeClassRetrievers) {
		if (closeClassRetrievers) {
			closeClassRetrievers();
		}
		this.defaultClassLoaderManager.reset();		
	}
	
	@Override
	public void close() {
		closeResources(() -> this.classRetrievers == null, () -> {
			this.defaultClassLoaderManager.close();
			unregister(config);
			closeClassRetrievers();
			BackgroundExecutor.createTask(() -> {
				this.classRetrievers = null;
			}).submit();
			pathHelper = null;
			javaMemoryCompiler = null;
			byteCodeHunter = null;
			classPathHunter = null;
			classPathHunterSupplier = null;	
			config = null;
		});
	}

	
}