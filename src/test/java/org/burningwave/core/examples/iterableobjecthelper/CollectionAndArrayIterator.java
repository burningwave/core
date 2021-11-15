package org.burningwave.core.examples.iterableobjecthelper;

import static org.burningwave.core.assembler.StaticComponentContainer.IterableObjectHelper;
import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggersRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.burningwave.core.iterable.IterableObjectHelper.IterationConfig;

public class CollectionAndArrayIterator {

    public static void execute() {
        List<String> output = IterableObjectHelper.iterateAndGet(
            IterationConfig.of(buildCollection())
            //Enabling parallel iteration when the input collection size is greater than 2
            .parallelIf(inputColl -> inputColl.size() > 2)
            //Setting up the output collection
            .withOutput(new ArrayList<String>())
            .withAction((number, outputCollectionSupplier) -> {
                if ((number % 2) == 0) {                        
                    outputCollectionSupplier.accept(outputCollection ->
                        //Converting and adding item to output collection
                        outputCollection.add(number.toString())
                    );
                }
            })    
        );
        
        IterableObjectHelper.iterate(
            IterationConfig.of(output)
            //Disabling parallel iteration
            .parallelIf(inputColl -> false)
            .withAction((number) -> {
                ManagedLoggersRepository.logInfo(CollectionAndArrayIterator.class::getName, "Iterated number: {}", number);
            })    
        );
        
        ManagedLoggersRepository.logInfo(
            CollectionAndArrayIterator.class::getName,
            "Output collection size {}", output.size()
        );
    }

    private static Collection<Integer> buildCollection() {
        Collection<Integer> inputCollection = new ArrayList<>();
        for (int i = 0; i < 1000000; i++) {
            inputCollection.add(i);
        }
        return inputCollection;
    }
    
    public static void main(String[] args) {
        execute();
    }
    
}
