package org.burningwave.core;


import static org.burningwave.core.assembler.StaticComponentContainer.Fields;

import org.burningwave.core.assembler.ComponentContainer;
import org.junit.jupiter.api.Test;


public class FieldsTest extends BaseTest {
	
	@Test
	public void getAllTestOne() {
		testNotEmpty(
			() -> {
				return Fields.getAll(ComponentContainer.getInstance()).values();	
			},
		true);
	}
	
	@Test
	public void getAllDirectTestOne() {
		testNotEmpty(
			() -> {
				return Fields.getAllDirect(ComponentContainer.getInstance()).values();	
			},
		true);
	}
}
