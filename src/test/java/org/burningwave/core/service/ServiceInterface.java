package org.burningwave.core.service;

import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository;

public interface ServiceInterface {

	public default void printMyName() {
		ManagedLoggerRepository.logInfo(this.getClass()::getName, "My name is" + this.getClass().getName());
	}

}
