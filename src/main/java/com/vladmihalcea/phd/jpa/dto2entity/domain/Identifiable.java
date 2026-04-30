package com.vladmihalcea.phd.jpa.dto2entity.domain;

import com.vladmihalcea.phd.common.util.TimeSortedIdentifierGenerator;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Transient;

import java.util.Objects;

/**
 * @author Vlad Mihalcea
 */
@MappedSuperclass
public abstract class Identifiable<T extends Identifiable<T>> {

    @Transient
    private final Class<T> entityClass;

    @Id
    private Long id = TimeSortedIdentifierGenerator.generate();

    protected Identifiable(Class<T> entityClass) {
        this.entityClass = entityClass;
    }

    public Long getId() {
        return id;
    }

    public T setId(Long id) {
        this.id = id;
        return entityClass.cast(this);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Identifiable<?> that)) return false;
        return Objects.equals(entityClass, that.entityClass) &&
               Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityClass, id);
    }
}
