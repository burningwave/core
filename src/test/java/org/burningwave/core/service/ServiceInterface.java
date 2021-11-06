package org.burningwave.core.service;

import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggersRepository;

public interface ServiceInterface {

	public default void printMyName() {
		ManagedLoggersRepository.logInfo(this.getClass()::getName, "My name is" + this.getClass().getName());
	}

}
