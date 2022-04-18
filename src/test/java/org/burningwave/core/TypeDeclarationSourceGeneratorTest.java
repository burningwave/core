package org.burningwave.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.burningwave.core.classes.TypeDeclarationSourceGenerator;
import org.junit.jupiter.api.Test;

public class TypeDeclarationSourceGeneratorTest {

	@Test
	public void resultClassNameAndDotClass() {
		TypeDeclarationSourceGenerator useClassExtension = TypeDeclarationSourceGenerator
				.create(TypeDeclarationSourceGenerator.class).useClassExtension(true);
		assertEquals("TypeDeclarationSourceGenerator.class", useClassExtension._toString());
	}

	@Test
	public void resultClassName() {
		TypeDeclarationSourceGenerator useClassExtension = TypeDeclarationSourceGenerator
				.create(TypeDeclarationSourceGenerator.class).useClassExtension(false);
		assertEquals("TypeDeclarationSourceGenerator", useClassExtension._toString());
	}

	@Test
	public void resultClassNameDefaultUseClassExtensionFalse() {
		TypeDeclarationSourceGenerator useClassExtension = TypeDeclarationSourceGenerator
				.create(TypeDeclarationSourceGenerator.class);
		assertEquals("TypeDeclarationSourceGenerator", useClassExtension._toString());
	}
}
