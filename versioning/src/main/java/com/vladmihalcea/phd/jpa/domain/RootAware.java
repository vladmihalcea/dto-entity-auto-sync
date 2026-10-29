package com.vladmihalcea.phd.jpa.domain;

/**
 * Implemented by the child entities of an aggregate, so that any change
 * to a child entity increments the version of the aggregate root.
 *
 * @author Vlad Mihalcea
 */
public interface RootAware<T> {
    T root();
}
