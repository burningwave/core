package org.burningwave.core.service;

import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository;

public class ExtendedService extends Service {

	@Override
	public String apply(Object value_01, String value_02, String value_03) {
		ManagedLoggerRepository.logInfo(this.getClass()::getName, "TriFunction: " + value_01 + " " + value_02 + " " + value_03);
		return "";
	}

}
