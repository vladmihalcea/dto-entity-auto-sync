package com.vladmihalcea.phd.jpa.mapper;

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

    /**
     * Copies all non-association properties from the source to the target entity.
     * @param source the detached entity built from the DTO, containing the new state for basic attributes
     * @param target t
     * @param <T> the entity type
     */
    public <T> void mergeBasicAttributes(T source, T target) {
        BeanUtils.copyProperties(source, target, entityAssociationMap.get(source.getClass()));
    }

    /**
     * Generic diff-based collection merge.
     * <p>
     * Removes items present in {@code dbCollection} but absent from {@code dtoCollection},
     * updates matching items via {@code updateFn}, and adds new items via {@code addFn}.
     * Equality for remove/add detection relies on the entity {@code equals}/{@code hashCode}
     * contract (e.g. id for PostComment, name for Tag).
     *
     * @param dbCollection  the live managed collection from the database entity
     * @param dtoCollection the detached collection built from the DTO
     * @param keyExtractor  extracts the key used to pair existing with incoming items for update
     * @param addChildFunction         adds a new item to the parent entity (should maintain bidirectional links)
     * @param removeChildFunction      removes an item from the parent entity (should maintain bidirectional links)
     * @param updateChildFunction      copies mutable properties from the incoming item to the existing managed item
     */
    public <E, K> void mergeCollection(
        Collection<E> dbCollection,
        Collection<E> dtoCollection,
        Function<E, K> keyExtractor,
        Consumer<E> addChildFunction,
        Consumer<E> removeChildFunction,
        BiConsumer<E, E> updateChildFunction
    ) {
        // Identify new items and items to update
        List<E> toAdd = new ArrayList<>(dtoCollection);
        toAdd.removeAll(dbCollection);

        // Remove items no longer present in the DTO
        List<E> toRemove = new ArrayList<>(dbCollection);
        toRemove.removeAll(dtoCollection);
        toRemove.forEach(removeChildFunction);

        // Update existing items
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

        // Add new items
        toAdd.forEach(addChildFunction);
    }
}
