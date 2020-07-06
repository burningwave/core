package org.burningwave.core.examples.propertyaccessor;
import static org.burningwave.core.assembler.StaticComponentContainer.ByFieldOrByMethodPropertyAccessor;
import static org.burningwave.core.assembler.StaticComponentContainer.ByMethodOrByFieldPropertyAccessor;

import org.burningwave.core.bean.Complex;

public class GetAndSetPropertiesThroughPath{
    
    public void execute() {
        Complex complex = new Complex();
        //This type of property accessor try to access by field introspection: if no field was found
        //it will search getter method and invokes it
        String nameFromObjectInArray = ByFieldOrByMethodPropertyAccessor.get(complex, "data.items[1][0].name");
        String nameFromObjectMap = ByFieldOrByMethodPropertyAccessor.get(complex, "data.itemsMap[items][1][1].name");
        System.out.println(nameFromObjectInArray);
        System.out.println(nameFromObjectMap);
        //This type of property accessor looks for getter method and invokes it: if no getter method was found
        //it will search for field and try to retrieve it
        nameFromObjectInArray = ByMethodOrByFieldPropertyAccessor.get(complex, "data.items[1][2].name");
        nameFromObjectMap = ByMethodOrByFieldPropertyAccessor.get(complex, "data.itemsMap[items][1][1].name");
        System.out.println(nameFromObjectInArray);
        System.out.println(nameFromObjectMap);
        ByMethodOrByFieldPropertyAccessor.set(complex, "data.itemsMap[items][1][1].name", "Good evening!");
        nameFromObjectInArray = ByMethodOrByFieldPropertyAccessor.get(complex, "data.itemsMap[items][1][1].name");
        System.out.println(nameFromObjectInArray);
    }
    
    public static void main(String[] args) {
        new GetAndSetPropertiesThroughPath().execute();
    }
    
}