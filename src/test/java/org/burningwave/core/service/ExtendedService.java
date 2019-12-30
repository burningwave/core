package org.burningwave.core.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExtendedService extends Service {
	private final static Logger LOGGER = LoggerFactory.getLogger(ExtendedService.class);
	
	@Override
	public String apply(Object value_01, String value_02, String value_03) {
		LOGGER.info("TriFunction: " + value_01 + " " + value_02 + " " + value_03);
		return "";
	}
	
}
