package org.burningwave.core.examples.member;

import static org.burningwave.core.assembler.StaticComponentContainer.Fields;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.burningwave.core.classes.FieldCriteria;


@SuppressWarnings("unused")
public class FieldsHandler {

    public static void execute() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        //Fast access by memory address
        Collection<Class<?>> loadedClasses = Fields.getDirect(classLoader, "classes");
        //Access by Reflection
        loadedClasses = Fields.get(classLoader, "classes");

        //Get all field values of an object through memory address access
        Map<Field, ?> values = Fields.getAllDirect(classLoader);
        //Get all field values of an object through reflection access
        values = Fields.getAll(classLoader);

        Object obj = new Object() {
            volatile List<Object> objectValue;
            volatile int intValue = 1;
            volatile long longValue = 2l;
            volatile float floatValue = 3f;
            volatile double doubleValue = 4.1d;
            volatile boolean booleanValue = true;
            volatile byte byteValue = (byte)5;
            volatile char charValue = 'c';
        };

		//Get all filtered field values of an object through memory address access
		Fields.getAllDirect(
			FieldCriteria.withoutConsideringParentClasses().allThoseThatMatch(field -> {
				return field.getType().isPrimitive();
			}),
			obj
		).values();
    }

    public static void main(String[] args) {
        execute();
    }

}
