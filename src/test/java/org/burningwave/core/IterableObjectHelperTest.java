package org.burningwave.core;

import static org.burningwave.core.assembler.StaticComponentContainer.IterableObjectHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.burningwave.core.iterable.IterableObjectHelper.IterationConfig;
import org.burningwave.core.iterable.IterableObjectHelper.ResolveConfig;
import org.burningwave.core.iterable.Properties;
import org.junit.jupiter.api.Test;


@SuppressWarnings("unused")
public class IterableObjectHelperTest extends BaseTest {

	@Test
	public void resolveTestOne() {
		testNotNull(() -> {
			Properties properties = new Properties();
			properties.put("class-loader-01", "${class-loader-02}");
			properties.put("class-loader-02", Thread.currentThread().getContextClassLoader());
			return IterableObjectHelper.resolveValue(
				ResolveConfig.forNamedKey("class-loader-01")
				.on(properties)
			);
		});
	}

	@Test
	public void resolveTestTwo() {
		testNotEmpty(() -> {
			Properties properties = new Properties();
			properties.put("class-loaders", "${class-loader-02};${class-loader-03};");
			properties.put("class-loader-02", Thread.currentThread().getContextClassLoader());
			properties.put("class-loader-03", Thread.currentThread().getContextClassLoader().getParent());
			return IterableObjectHelper.resolveValues(
				ResolveConfig.forNamedKey("class-loaders")
				.on(properties)
			);
		});
	}
	
	@Test
	public void iterateParallelTestOne() {
		Collection<Integer> input = IntStream.rangeClosed(1, 1000000).boxed().collect(Collectors.toList());
//		long initialTime = System.currentTimeMillis();
//		for (int i = 0; i < 10; i++) {
			testNotEmpty(() -> {
				return IterableObjectHelper.createIterateAndGetTask(
					IterationConfig.of(input)
					.parallelIf(inputColl -> inputColl.size() > 2)
					.withOutput(new ArrayList<>())
					.withAction((number, outputCollectionSupplier) -> {
						//ManagedLoggerRepository.logDebug(getClass()::getName, "Iterated number: {}", number);
						if ((number % 2) == 0) {						
							outputCollectionSupplier.accept(outputCollection -> 
								outputCollection.add(number)
							);
						}
					}).threadBased()
					
				).submit().join();
			}, false);
//		}
//		org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository.logInfo(
//			getClass()::getName,
//			"Total - Elapsed time: " + getFormattedDifferenceOfMillis(System.currentTimeMillis(),initialTime)
//		);
//		initialTime = System.currentTimeMillis();
//		for (int i = 0; i < 10; i++) {
//			testNotEmpty(() -> {
//				return input.parallelStream().filter(number -> (number % 2) == 0).collect(Collectors.toCollection(ArrayList::new));
//			}, false);
//		}
//		org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository.logInfo(
//			getClass()::getName,
//			"Total - Elapsed time: " + getFormattedDifferenceOfMillis(System.currentTimeMillis(),initialTime)
//		);
	}
	
	@Test
	public void iterateParallelTestTwo() {
		int[] input = new int[1000000];
		for (int i = 0; i < input.length; i++) {
			input[i] = i;
		}
		testNotEmpty(() -> {
			return IterableObjectHelper.iterateAndGet(
				IterationConfig.ofInts(input)
				.parallelIf(inputColl -> inputColl.length > 2)
				.withOutput(new HashSet<>())
				.withAction((number, outputCollectionSupplier) -> {
					//ManagedLoggerRepository.logDebug(getClass()::getName, "Iterated number: {}", number);
					if ((number % 2) == 0) {						
						outputCollectionSupplier.accept(outputCollection -> 
							outputCollection.add(number)
						);
					}
				})
				
			);
		}, false);
	}
	
	@Test
	public void iterateParallelTestThree() {
		Object[] input = new Object[1000000];
		for (int i = 0; i < input.length; i++) {
			input[i] = i;
		}
//		long initialTime = System.currentTimeMillis();
//		for (int i = 0; i < 10; i++) {
			testNotEmpty(() -> {
				return IterableObjectHelper.iterateAndGet(
					IterationConfig.of(input)
					.parallelIf(inputColl -> inputColl.length > 2)
					.withOutput(new HashSet<Integer>())
					.withAction((number, outputCollectionSupplier) -> {
						//ManagedLoggerRepository.logDebug(getClass()::getName, "Iterated number: {}", number);
						if (((int)number % 2) == 0) {						
							outputCollectionSupplier.accept(outputCollection -> 
								outputCollection.add((int)number)
							);
						}
					})
					
				);
			}, false);
//		}
//		org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository.logInfo(
//			getClass()::getName,
//			"Total - Elapsed time: " + getFormattedDifferenceOfMillis(System.currentTimeMillis(),initialTime)
//		);
//		initialTime = System.currentTimeMillis();
//		for (int i = 0; i < 10; i++) {
//			testNotEmpty(() -> {
//				return Stream.of(input).parallel().collect(Collectors.toCollection(ArrayList::new));
//			}, false);
//		}
//		org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository.logInfo(
//			getClass()::getName,
//			"Total - Elapsed time: " + getFormattedDifferenceOfMillis(System.currentTimeMillis(),initialTime)
//		);
	}
	
	@Test
	public void resolveTestThree() {
		testNotNull(() -> {
			Properties properties = new Properties();
			properties.put("class-loader-01", "${class-loader-02}");
			properties.put("class-loader-02", "${class-loader-03}");
			properties.put("class-loader-03", Thread.currentThread().getContextClassLoader().getParent());
			return IterableObjectHelper.resolveValue(
				ResolveConfig.forNamedKey("class-loader-01")
				.on(properties)
			);
		});
	}

	@Test
	public void containsTestOne() {
		testNotNull(() -> {
			Properties properties = new Properties();
			properties.put("class-loader-01", "${class-loader-02}");
			properties.put("class-loader-02", "${class-loader-03}");
			properties.put("class-loader-03", Thread.currentThread().getContextClassLoader().getParent());
			return IterableObjectHelper.containsValue(
				properties, "class-loader-01",
				Thread.currentThread().getContextClassLoader().getParent()
			);
		});
	}

}
