package org.burningwave.core;

import static org.burningwave.core.assembler.StaticComponentContainer.GlobalProperties;

import org.junit.jupiter.api.Test;

public class PropertiesTest extends BaseTest {
	
	@Test
	public void changeManagedLoggerRepository() {
		GlobalProperties.put(
			ManagedLogger.Repository.Configuration.Key.TYPE, SimpleManagedLoggerRepository.class.getName()
		);
		
	}
	
}
