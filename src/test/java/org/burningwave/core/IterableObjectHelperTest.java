package org.burningwave.core;

import static org.burningwave.core.assembler.StaticComponentContainer.IterableObjectHelper;

import org.burningwave.core.iterable.Properties;
import org.junit.jupiter.api.Test;


public class IterableObjectHelperTest extends BaseTest {
	
	@Test
	public void resolveTestOne() {
		testNotNull(() -> {
			Properties properties = new Properties();
			properties.put("class-loader-01", "${class-loader-02}");
			properties.put("class-loader-02", Thread.currentThread().getContextClassLoader());
			return IterableObjectHelper.resolveObjectValue(properties, "class-loader-01");
		});
	}
	
	@Test
	public void resolveTestTwo() {
		testNotEmpty(() -> {
			Properties properties = new Properties();
			properties.put("class-loader-01", "${class-loader-02};${class-loader-03};");
			properties.put("class-loader-02", Thread.currentThread().getContextClassLoader());
			properties.put("class-loader-03", Thread.currentThread().getContextClassLoader().getParent());
			return IterableObjectHelper.resolveObjectValues(properties, "class-loader-01", ";");
		});
	}
	
	@Test
	public void resolveTestThree() {
		testNotNull(() -> {
			Properties properties = new Properties();
			properties.put("class-loader-01", "${class-loader-02}");
			properties.put("class-loader-02", "${class-loader-03}");
			properties.put("class-loader-03", Thread.currentThread().getContextClassLoader().getParent());
			return IterableObjectHelper.resolveObjectValue(properties, "class-loader-01");
		});
	}
	
}
