package org.burningwave.core;

import static org.burningwave.core.assembler.StaticComponentContainer.GlobalProperties;

import org.burningwave.core.iterable.Properties;
import org.junit.jupiter.api.Test;


public class ManagedLoggerRepositoryTest extends BaseTest {

	@Test
	public void placeHolderedLogInfoTest() {
		testDoesNotThrow(() -> {
			Properties config = new Properties();
			config.putAll(GlobalProperties);
			config.put(ManagedLogger.Repository.Configuration.Key.TYPE, SimpleManagedLoggerRepository.class.getName());
			ManagedLogger.Repository managedLoggerRepository = ManagedLogger.Repository.create(config);
			managedLoggerRepository.logInfo(() -> ManagedLoggerRepositoryTest.class.getName(), "{}{}{}{}", "Hello", " ", "world", "!");
		});
	}

}
