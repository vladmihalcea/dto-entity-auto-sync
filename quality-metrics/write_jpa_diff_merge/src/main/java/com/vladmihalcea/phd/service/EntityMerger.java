package com.vladmihalcea.phd.service;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.Attribute;
import org.hibernate.SessionFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Vlad Mihalcea
 */
@Component
public class EntityMerger {

    private final Map<Class<?>, String[]> entityAssociationMap;

    public EntityMerger(@Autowired EntityManagerFactory entityManagerFactory) {
        entityAssociationMap = entityManagerFactory.unwrap(SessionFactory.class).getMetamodel()
            .getEntities()
            .stream()
            .collect(Collectors.toMap(
                entityType -> entityType.getJavaType(),
                entityType -> entityType.getAttributes()
                    .stream()
                    .filter(attribute ->
                        attribute.isAssociation() || attribute.isCollection())
                    .map(Attribute::getName)
                    .toArray(String[]::new)
            ));
    }

    public <T> void mergeBasicAttributes(T source, T target) {
        BeanUtils.copyProperties(source, target, entityAssociationMap.get(source.getClass()));
    }

    public <E, K> void mergeCollection(
        Collection<E> dbCollection,
        Collection<E> dtoCollection,
        Function<E, K> keyExtractor,
        Consumer<E> addChildFunction,
        Consumer<E> removeChildFunction,
        BiConsumer<E, E> updateChildFunction
    ) {
        List<E> toAdd = new ArrayList<>(dtoCollection);
        toAdd.removeAll(dbCollection);

        List<E> toRemove = new ArrayList<>(dbCollection);
        toRemove.removeAll(dtoCollection);
        toRemove.forEach(removeChildFunction);

        Map<K, E> dbMap = dbCollection.stream()
            .collect(Collectors.toMap(keyExtractor, Function.identity()));

        for (E incoming : dtoCollection) {
            if (!toAdd.contains(incoming)) {
                E existing = dbMap.get(keyExtractor.apply(incoming));
                if (existing != null) {
                    updateChildFunction.accept(incoming, existing);
                }
            }
        }

        toAdd.forEach(addChildFunction);
    }
}
