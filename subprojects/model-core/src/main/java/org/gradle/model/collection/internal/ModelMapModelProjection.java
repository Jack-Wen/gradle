/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.model.collection.internal;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import org.gradle.api.Nullable;
import org.gradle.model.ModelMap;
import org.gradle.model.collection.CollectionBuilder;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;
import org.gradle.util.CollectionUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.gradle.internal.Cast.uncheckedCast;

public class ModelMapModelProjection<I> implements ModelProjection {

    @SuppressWarnings("deprecation")
    private final static Set<Class<?>> SUPPORTED_CONTAINER_TYPES = ImmutableSet.<Class<?>>of(ModelMap.class, CollectionBuilder.class);

    public static <T> ModelProjection of(ModelType<T> itemType, ChildNodeCreatorStrategy<T> creatorStrategy) {
        return new ModelMapModelProjection<T>(itemType, creatorStrategy);
    }

    public static <T> ModelProjection of(Class<T> itemType, ChildNodeCreatorStrategy<T> creatorStrategy) {
        return of(ModelType.of(itemType), creatorStrategy);
    }

    protected final Class<I> baseItemType;
    protected final ModelType<I> baseItemModelType;
    private final ChildNodeCreatorStrategy<I> creatorStrategy;

    public ModelMapModelProjection(ModelType<I> baseItemModelType, ChildNodeCreatorStrategy<I> creatorStrategy) {
        this.baseItemModelType = baseItemModelType;
        this.baseItemType = baseItemModelType.getConcreteClass();
        this.creatorStrategy = creatorStrategy;
    }

    protected Collection<? extends Class<?>> getCreatableTypes(MutableModelNode node) {
        return Collections.singleton(baseItemType);
    }

    private String getContainerTypeDescription(Class<?> containerType, Collection<? extends Class<?>> creatableTypes) {
        StringBuilder sb = new StringBuilder(containerType.getName());
        if (creatableTypes.size() == 1) {
            @SuppressWarnings("ConstantConditions")
            String onlyType = Iterables.getFirst(creatableTypes, null).getName();
            sb.append("<").append(onlyType).append(">");
        } else {
            sb.append("<T>; where T is one of [");
            Joiner.on(", ").appendTo(sb, CollectionUtils.sort(Iterables.transform(creatableTypes, new Function<Class<?>, String>() {
                public String apply(Class<?> input) {
                    return input.getName();
                }
            })));
            sb.append("]");
        }
        return sb.toString();
    }

    public Iterable<String> getReadableTypeDescriptions(MutableModelNode node) {
        return getWritableTypeDescriptions(node);
    }

    protected Class<? extends I> itemType(ModelType<?> targetType) {
        Class<?> targetClass = targetType.getRawClass();
        if (SUPPORTED_CONTAINER_TYPES.contains(targetClass)) {
            Class<?> targetItemClass = targetType.getTypeVariables().get(0).getRawClass();
            if (targetItemClass.isAssignableFrom(baseItemType)) {
                return baseItemType;
            }
            if (baseItemType.isAssignableFrom(targetItemClass)) {
                return targetItemClass.asSubclass(baseItemType);
            }
            return null;
        }
        if (targetClass.isAssignableFrom(ModelMap.class)) {
            return baseItemType;
        }
        return null;
    }

    public <T> boolean canBeViewedAsWritable(ModelType<T> targetType) {
        return itemType(targetType) != null;
    }

    public <T> boolean canBeViewedAsReadOnly(ModelType<T> type) {
        return canBeViewedAsWritable(type);
    }

    public <T> ModelView<? extends T> asReadOnly(ModelType<T> type, MutableModelNode modelNode, @Nullable ModelRuleDescriptor ruleDescriptor) {
        return asWritable(type, modelNode, ruleDescriptor, null);
    }

    public <T> ModelView<? extends T> asWritable(ModelType<T> targetType, MutableModelNode node, ModelRuleDescriptor ruleDescriptor, List<ModelView<?>> inputs) {
        Class<? extends I> itemType = itemType(targetType);
        if (itemType != null) {
            return uncheckedCast(toView(ruleDescriptor, node, itemType));
        }
        return null;
    }

    private <S extends I> ModelView<ModelMap<S>> toView(ModelRuleDescriptor sourceDescriptor, MutableModelNode node, Class<S> itemClass) {
        ModelType<S> itemType = ModelType.of(itemClass);
        ModelMap<I> builder = new DefaultModelMap<I>(baseItemModelType, sourceDescriptor, node, creatorStrategy);

        ModelMap<S> subBuilder = builder.withType(itemClass);
        ModelType<ModelMap<S>> viewType = DefaultModelMap.modelMapTypeOf(itemType);
        DefaultModelViewState state = new DefaultModelViewState(viewType, sourceDescriptor);
        return new ModelMapModelView<ModelMap<S>>(node.getPath(), viewType, new ModelMapGroovyDecorator<S>(subBuilder, state), state);
    }

    @Override
    public Iterable<String> getWritableTypeDescriptions(final MutableModelNode node) {
        final Collection<? extends Class<?>> creatableTypes = getCreatableTypes(node);
        return Iterables.transform(SUPPORTED_CONTAINER_TYPES, new Function<Class<?>, String>() {
            public String apply(Class<?> containerType) {
                return getContainerTypeDescription(containerType, creatableTypes);
            }
        });
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ModelMapModelProjection<?> that = (ModelMapModelProjection<?>) o;

        return baseItemType.equals(that.baseItemType) && baseItemModelType.equals(that.baseItemModelType);
    }

    @Override
    public int hashCode() {
        int result = baseItemType.hashCode();
        result = 31 * result + baseItemModelType.hashCode();
        return result;
    }
}
