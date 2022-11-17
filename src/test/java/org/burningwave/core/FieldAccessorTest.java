package org.burningwave.core;

import static org.burningwave.core.assembler.StaticComponentContainer.ByFieldOrByMethodPropertyAccessor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.burningwave.core.bean.Complex;
import org.junit.jupiter.api.Test;

public class FieldAccessorTest extends BaseTest {

	@Test
	public void getTestOne() {
		Complex complex = new Complex();
		assertNotNull(ByFieldOrByMethodPropertyAccessor.get(complex, "data.items[1][1].name"));
		assertNotNull(ByFieldOrByMethodPropertyAccessor.get(complex, "data.itemsMap[items][1][1].name"));
	}

	@Test
	public void setTestOne() {
		Complex complex = new Complex();
		String newName = "Peter";
		ByFieldOrByMethodPropertyAccessor.set(complex, "data.items[0][2].name", newName);
		assertEquals(ByFieldOrByMethodPropertyAccessor.get(complex, "data.items[0][2].name"), newName);
	}

	@Test
	void setTestTwo() {
		Complex complex = new Complex();
		Complex.Data.Item newItem = new Complex.Data.Item("Sam");
		ByFieldOrByMethodPropertyAccessor.set(complex, "data.items[0][1]", newItem);
		assertEquals(ByFieldOrByMethodPropertyAccessor.get(complex, "data.items[0][1]"), newItem);
	}

	@Test
	void setTestThree() {
		Complex complex = new Complex();
		ByFieldOrByMethodPropertyAccessor.set(complex, "data.itemsMap[items]",
				ByFieldOrByMethodPropertyAccessor.get(complex, "data.itemsMap[items]"));
		assertEquals((Object) ByFieldOrByMethodPropertyAccessor.get(complex, "data.itemsMap[items]"),
				(Object) ByFieldOrByMethodPropertyAccessor.get(complex, "data.itemsMap[items]"));
	}

	@Test
	void setTestFour() {
		Complex complex = new Complex();
		ByFieldOrByMethodPropertyAccessor.set(complex, "data", ByFieldOrByMethodPropertyAccessor.get(complex, "data"));
		assertEquals((Object) ByFieldOrByMethodPropertyAccessor.get(complex, "data"),
				(Object) ByFieldOrByMethodPropertyAccessor.get(complex, "data"));
	}

	@Test
	void setTestFive() {
		Map<String, Map<String, Map<String, Object>>> nestedMaps= new LinkedHashMap<>();
		Map<String, Map<String, Object>> innerMapLevelOne = new LinkedHashMap<>();
		Map<String, Object> innerMapLevelTwo = new LinkedHashMap<>();

		ByFieldOrByMethodPropertyAccessor.set(nestedMaps, "[data]", innerMapLevelOne);
		ByFieldOrByMethodPropertyAccessor.set(innerMapLevelOne, "[data]", innerMapLevelTwo);
		ByFieldOrByMethodPropertyAccessor.set(nestedMaps, "[data][data][data]", "Hello");
		assertEquals((Object) ByFieldOrByMethodPropertyAccessor.get(nestedMaps, "[data][data][data]"),
				"Hello");
	}

	@Test
	void setTestSix() {
		setIndexedValue(ArrayList::new);
	}

	@Test
	void setTestSeven() {
		AtomicInteger hashCodeGenerator = new AtomicInteger(1);
		setIndexedValue(() -> new HashSet<>() {
			private int hashCode = hashCodeGenerator.getAndIncrement();
			@Override
			public int hashCode() {
				return hashCode;
			}
			@Override
			public String toString() {
				return " hash code: " + String.valueOf(hashCode) + " - " + super.toString();
			}
		});
	}

	public<T> void setIndexedValue(Supplier<Collection> collSupplier) {
		testDoesNotThrow(() -> {
			Collection<Collection<Collection<String>>> nestedCollections = collSupplier.get();
			nestedCollections.add(collSupplier.get());
			nestedCollections.add(collSupplier.get());
			nestedCollections.add(collSupplier.get());
			Collection<Collection<String>> nestedCollectionsLevelOne = collSupplier.get();
			nestedCollectionsLevelOne.add(collSupplier.get());
			nestedCollectionsLevelOne.add(collSupplier.get());
			nestedCollectionsLevelOne.add(collSupplier.get());
			Collection<String> nestedCollectionsLevelTwo = collSupplier.get();
			nestedCollectionsLevelTwo.add("a");
			nestedCollectionsLevelTwo.add("b");
			nestedCollectionsLevelTwo.add("c");
			ByFieldOrByMethodPropertyAccessor.set(nestedCollections, "[2]", nestedCollectionsLevelOne);
			ByFieldOrByMethodPropertyAccessor.set(nestedCollectionsLevelOne, "[2]", nestedCollectionsLevelTwo);
			ByFieldOrByMethodPropertyAccessor.set(nestedCollections, "[2][2][2]", "Hello");
			assertEquals((Object) ByFieldOrByMethodPropertyAccessor.get(nestedCollections, "[2][2][2]"), "Hello");
		});
	}

}
