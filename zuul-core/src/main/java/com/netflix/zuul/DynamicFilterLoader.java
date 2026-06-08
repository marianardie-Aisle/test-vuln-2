/*
 * Copyright 2018 Netflix, Inc.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 */

package com.netflix.zuul;

import com.netflix.zuul.filters.FilterRegistry;
import com.netflix.zuul.filters.FilterType;
import com.netflix.zuul.filters.ZuulFilter;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public final class DynamicFilterLoader implements FilterLoader {
    private static final Logger LOG = LoggerFactory.getLogger(DynamicFilterLoader.class);

    private final ConcurrentMap<String, Long> filterClassLastModified = new ConcurrentHashMap<>();
    private final ConcurrentMap<FilterType, SortedSet<ZuulFilter<?, ?>>> hashFiltersByType = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ZuulFilter<?, ?>> filtersByNameAndType = new ConcurrentHashMap<>();

    private final FilterRegistry filterRegistry;

    private final FilterFactory filterFactory;
    private final Map<String, Class<? extends ZuulFilter<?, ?>>> registeredFiltersByClassName;

    @Inject
    public DynamicFilterLoader(FilterRegistry filterRegistry, FilterFactory filterFactory) {
        this(filterRegistry, filterFactory, loadRegisteredFilters(resolveClassLoader()));
    }

    DynamicFilterLoader(
            FilterRegistry filterRegistry,
            FilterFactory filterFactory,
            Set<? extends Class<? extends ZuulFilter<?, ?>>> registeredFilters) {
        this(filterRegistry, filterFactory, mapRegisteredFilters(registeredFilters));
    }

    private DynamicFilterLoader(
            FilterRegistry filterRegistry,
            FilterFactory filterFactory,
            Map<String, Class<? extends ZuulFilter<?, ?>>> registeredFiltersByClassName) {
        this.filterRegistry = filterRegistry;
        this.filterFactory = filterFactory;
        this.registeredFiltersByClassName = registeredFiltersByClassName;
    }

    private static ClassLoader resolveClassLoader() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader != null) {
            return classLoader;
        }
        return DynamicFilterLoader.class.getClassLoader();
    }

    private static Set<Class<ZuulFilter<?, ?>>> loadRegisteredFilters(ClassLoader classLoader) {
        try {
            return StaticFilterLoader.loadFilterTypesFromResources(classLoader);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load registered Zuul filters.", e);
        }
    }

    private static Map<String, Class<? extends ZuulFilter<?, ?>>> mapRegisteredFilters(
            Set<? extends Class<? extends ZuulFilter<?, ?>>> registeredFilters) {
        Map<String, Class<? extends ZuulFilter<?, ?>>> filtersByClassName = new HashMap<>();
        for (Class<? extends ZuulFilter<?, ?>> registeredFilter : registeredFilters) {
            filtersByClassName.put(registeredFilter.getName(), registeredFilter);
        }
        return Collections.unmodifiableMap(filtersByClassName);
    }

    /**
     * @return the total number of Zuul filters
     */
    public int filterInstanceMapSize() {
        return filterRegistry.size();
    }

    private void putFilter(String filterName, ZuulFilter<?, ?> filter, long lastModified) {
        if (!filterRegistry.isMutable()) {
            LOG.warn("Filter registry is not mutable, discarding {}", filterName);
            return;
        }
        SortedSet<ZuulFilter<?, ?>> set = hashFiltersByType.get(filter.filterType());
        if (set != null) {
            hashFiltersByType.remove(filter.filterType()); // rebuild this list
        }

        String nameAndType = filter.filterType() + ":" + filter.filterName();
        filtersByNameAndType.put(nameAndType, filter);

        filterRegistry.put(filterName, filter);
        filterClassLastModified.put(filterName, lastModified);
    }

    /**
     * Load and cache filters by className
     *
     * @param classNames The class names to load
     * @return List of the loaded filters
     * @throws Exception If any specified filter fails to load, this will abort. This is a safety mechanism so we can
     * prevent running in a partially loaded state.
     */
    @Override
    public List<ZuulFilter<?, ?>> putFiltersForClasses(String[] classNames) throws Exception {
        List<ZuulFilter<?, ?>> newFilters = new ArrayList<>();
        for (String className : classNames) {
            newFilters.add(putFilterForClassName(className));
        }
        return Collections.unmodifiableList(newFilters);
    }

    @Override
    public ZuulFilter<?, ?> putFilterForClassName(String className) throws Exception {
        Class<? extends ZuulFilter<?, ?>> clazz = registeredFiltersByClassName.get(className);
        if (clazz == null) {
            throw new IllegalArgumentException("Specified filter class is not in the registered filter allowlist: " + className);
        }

        ZuulFilter<?, ?> filter = filterFactory.newInstance(clazz);
        putFilter(className, filter, System.currentTimeMillis());
        return filter;
    }

    /**
     * Returns a list of filters by the filterType specified
     */
    @Override
    public SortedSet<ZuulFilter<?, ?>> getFiltersByType(FilterType filterType) {
        SortedSet<ZuulFilter<?, ?>> set = hashFiltersByType.get(filterType);
        if (set != null) {
            return set;
        }

        set = new TreeSet<>(FILTER_COMPARATOR);

        for (ZuulFilter<?, ?> filter : filterRegistry.getAllFilters()) {
            if (filter.filterType().equals(filterType)) {
                set.add(filter);
            }
        }

        hashFiltersByType.putIfAbsent(filterType, set);
        return Collections.unmodifiableSortedSet(set);
    }

    @Override
    public ZuulFilter<?, ?> getFilterByNameAndType(String name, FilterType type) {
        if (name == null || type == null) {
            return null;
        }

        String nameAndType = type + ":" + name;
        return filtersByNameAndType.get(nameAndType);
    }
}
