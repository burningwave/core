package org.burningwave.core.examples.iterableobjecthelper;

import static org.burningwave.core.assembler.StaticComponentContainer.IterableObjectHelper;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.iterable.IterableObjectHelper.ResolveConfig;

@SuppressWarnings("unused")
public class ItemFromMapRetriever {
    
    public void execute() throws IOException {
        ComponentSupplier componentSupplier = ComponentContainer.getInstance();
        PathHelper pathHelper = componentSupplier.getPathHelper();
        Properties properties = new Properties();
        properties.load(pathHelper.getResourceAsStream("burningwave.properties"));
        String code = IterableObjectHelper.resolveStringValue(
            ResolveConfig.forNamedKey("code-block-1")
            .on(properties)
        );

        Map<Object, Object> map = new HashMap<>();
        map.put("class-loader-01", "${class-loader-02}");
        map.put("class-loader-02", "${class-loader-03}");
        map.put("class-loader-03", Thread.currentThread().getContextClassLoader().getParent());
        ClassLoader parentClassLoader = IterableObjectHelper.resolveValue(
            ResolveConfig.forNamedKey("class-loader-01")
            .on(map)
        );
        
        map.clear();
        map.put("class-loaders", "${class-loader-02};${class-loader-03};");
        map.put("class-loader-02", Thread.currentThread().getContextClassLoader());
        map.put("class-loader-03", Thread.currentThread().getContextClassLoader().getParent());
        Collection<ClassLoader> classLoaders = IterableObjectHelper.resolveValues(
            ResolveConfig.forNamedKey("class-loaders")
            .on(map)
            .withValuesSeparator(";")
        );
    }
    
    public static void main(String[] args) throws IOException {
        new ItemFromMapRetriever().execute();
    }
}
