package org.burningwave.core.examples.iterableobjecthelper;

import static org.burningwave.core.assembler.StaticComponentContainer.IterableObjectHelper;
import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggersRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.burningwave.core.iterable.IterableObjectHelper.IterationConfig;

public class CollectionAndArrayIterator {
	
    private static Collection<Integer> buildInputCollection() {
        return IntStream.rangeClosed(1, 1000000).boxed().collect(Collectors.toList());
    }
	
    public static void execute() {
        List<String> output = IterableObjectHelper.iterateAndGet(
            IterationConfig.of(buildInputCollection())
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

    public static void main(String[] args) {
        execute();
    }
    
}