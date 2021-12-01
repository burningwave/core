package org.burningwave.core.examples.iterableobjecthelper;

import static org.burningwave.core.assembler.StaticComponentContainer.IterableObjectHelper;
import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.burningwave.core.iterable.IterableObjectHelper.IterationConfig;

public class CollectionAndArrayIterator {

    public static void execute() {
        Collection<Integer> inputCollection =
            IntStream.rangeClosed(1, 1000000).boxed().collect(Collectors.toList());
        
        List<String> outputCollection = IterableObjectHelper.iterateAndGet(
            IterationConfig.of(inputCollection)
            //Enabling parallel iteration when the input collection size is greater than 2
            .parallelIf(inputColl -> inputColl.size() > 2)
            //Setting threads priority
            .withPriority(Thread.MAX_PRIORITY)
            //Setting up the output collection
            .withOutput(new ArrayList<String>())
            .withAction((number, outputCollectionSupplier) -> {
                if (number > 500000) {
                    //Terminating the current thread iteration early.
                    IterableObjectHelper.terminateCurrentThreadIteration();
                    //If you need to terminate all threads iteration (useful for a find first iteration) use
                    //IterableObjectHelper.terminateIteration();
                }
                if ((number % 2) == 0) {                        
                    outputCollectionSupplier.accept(outputColl ->
                        //Converting and adding item to output collection
                        outputColl.add(number.toString())
                    );
                }
            })    
        );
        
        IterableObjectHelper.iterate(
            IterationConfig.of(outputCollection)
            //Disabling parallel iteration
            .parallelIf(inputColl -> false)
            .withAction((number) -> {
                ManagedLoggerRepository.logInfo(CollectionAndArrayIterator.class::getName, "Iterated number: {}", number);
            })    
        );
        
        ManagedLoggerRepository.logInfo(
            CollectionAndArrayIterator.class::getName,
            "Output collection size {}", outputCollection.size()
        );
    }

    public static void main(String[] args) {
        execute();
    }
    
}