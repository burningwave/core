package org.burningwave.core.service;

import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggersRepository;

public class ExtendedService extends Service {

	@Override
	public String apply(Object value_01, String value_02, String value_03) {
		ManagedLoggersRepository.logInfo(this.getClass()::getName, "TriFunction: " + value_01 + " " + value_02 + " " + value_03);
		return "";
	}

}
