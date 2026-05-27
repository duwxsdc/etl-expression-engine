package com.etl.engine.rest.enhanced;

import org.springframework.web.client.RestClient;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ExtensionRegistry {
    
    private static final ExtensionRegistry INSTANCE = new ExtensionRegistry();
    
    private final Map<String, RestClientExtension> extensions = new ConcurrentHashMap<>();
    private final List<RestClientExtension> sortedExtensions = new ArrayList<>();
    private volatile boolean sorted = false;
    
    private ExtensionRegistry() {
    }
    
    public static ExtensionRegistry getInstance() {
        return INSTANCE;
    }
    
    public synchronized void register(RestClientExtension extension) {
        extensions.put(extension.name(), extension);
        sorted = false;
    }
    
    public synchronized void unregister(String name) {
        extensions.remove(name);
        sorted = false;
    }
    
    public RestClientExtension getExtension(String name) {
        return extensions.get(name);
    }
    
    public List<RestClientExtension> getExtensions() {
        if (!sorted) {
            synchronized (this) {
                if (!sorted) {
                    sortedExtensions.clear();
                    sortedExtensions.addAll(extensions.values());
                    sortedExtensions.sort(Comparator.comparingInt(RestClientExtension::order));
                    sorted = true;
                }
            }
        }
        return Collections.unmodifiableList(sortedExtensions);
    }
    
    public void applyAll(RestClientExtension.ExtensionContext context) {
        for (RestClientExtension extension : getExtensions()) {
            extension.apply(context);
        }
    }
    
    public void applySelected(RestClientExtension.ExtensionContext context, Set<String> selectedExtensions) {
        for (RestClientExtension extension : getExtensions()) {
            if (selectedExtensions.contains(extension.name())) {
                extension.apply(context);
            }
        }
    }
    
    public void clear() {
        extensions.clear();
        sortedExtensions.clear();
        sorted = false;
    }
}
