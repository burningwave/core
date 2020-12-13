package org.burningwave.core;


import static org.burningwave.core.assembler.StaticComponentContainer.Fields;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.classes.FieldCriteria;
import org.junit.jupiter.api.Test;


@SuppressWarnings("unused")
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
	
	@Test
	public void setDirectTestOne() {
		testDoesNotThrow(() -> {
			Object obj = new Object() {
				List<Object> objectValue;
				int intValue;
				long longValue;
				float floatValue;
				double doubleValue;
				boolean booleanValue;
				byte byteValue;
				char charValue;
			};
			List<Object> objectValue = new ArrayList<>();
			Fields.setDirect(obj, "objectValue", objectValue);
			List<Object> objectValue2Var = Fields.getDirect(obj, "objectValue");
			assertTrue(objectValue2Var == objectValue);
			Fields.setDirect(obj, "intValue", 1);
			int intValue = Fields.getDirect(obj, "intValue");
			assertTrue(intValue == 1);
			Fields.setDirect(obj, "longValue", 2l);
			long longValue = Fields.getDirect(obj, "longValue");
			assertTrue(longValue == 2l);
			Fields.setDirect(obj, "floatValue", 3f);
			float floatValue = Fields.getDirect(obj, "floatValue");
			assertTrue(floatValue == 3f);
			Fields.setDirect(obj, "doubleValue", 4.0d);
			double doubleValue = Fields.getDirect(obj, "doubleValue");
			assertTrue(doubleValue == 4.0);
			Fields.setDirect(obj, "booleanValue", true);
			boolean booleanValue = Fields.getDirect(obj, "booleanValue");
			assertTrue(booleanValue);
			Fields.setDirect(obj, "byteValue", (byte)5);
			byte byteValue = Fields.getDirect(obj, "byteValue");
			assertTrue(byteValue == 5);
			Fields.setDirect(obj, "charValue", 'a');
			char charValue = Fields.getDirect(obj, "charValue");
			assertTrue(charValue == 'a');
		});
	}
	
	@Test
	public void setDirectVolatileTestOne() {
		testDoesNotThrow(() -> {
			Object obj = new Object() {
				volatile List<Object> objectValue;
				volatile int intValue;
				volatile long longValue;
				volatile float floatValue;
				volatile double doubleValue;
				volatile boolean booleanValue;
				volatile byte byteValue;
				volatile char charValue;
			};
			List<Object> objectValue = new ArrayList<>();
			Fields.setDirect(obj, "objectValue", objectValue);
			List<Object> objectValue2Var = Fields.getDirect(obj, "objectValue");
			assertTrue(objectValue2Var == objectValue);
			Fields.setDirect(obj, "intValue", 1);
			int intValue = Fields.getDirect(obj, "intValue");
			assertTrue(intValue == 1);
			Fields.setDirect(obj, "longValue", 2l);
			long longValue = Fields.getDirect(obj, "longValue");
			assertTrue(longValue == 2l);
			Fields.setDirect(obj, "floatValue", 3f);
			float floatValue = Fields.getDirect(obj, "floatValue");
			assertTrue(floatValue == 3f);
			Fields.setDirect(obj, "doubleValue", 4.0d);
			double doubleValue = Fields.getDirect(obj, "doubleValue");
			assertTrue(doubleValue == 4.0);
			Fields.setDirect(obj, "booleanValue", true);
			boolean booleanValue = Fields.getDirect(obj, "booleanValue");
			assertTrue(booleanValue);
			Fields.setDirect(obj, "byteValue", (byte)5);
			byte byteValue = Fields.getDirect(obj, "byteValue");
			assertTrue(byteValue == 5);
			Fields.setDirect(obj, "charValue", 'a');
			char charValue = Fields.getDirect(obj, "charValue");
			assertTrue(charValue == 'a');
		});
	}
	
	
	@Test
	public void getAllTestTwo() {
		testNotEmpty(() -> {
			Object obj = new Object() {
				volatile List<Object> objectValue;
				volatile int intValue;
				volatile long longValue;
				volatile float floatValue;
				volatile double doubleValue;
				volatile boolean booleanValue;
				volatile byte byteValue;
				volatile char charValue;
			};
			return Fields.getAll(FieldCriteria.forEntireClassHierarchy().allThoseThatMatch(field -> {
				return field.getType().isPrimitive();
			}), obj).values();
		}, true);
	}
}
