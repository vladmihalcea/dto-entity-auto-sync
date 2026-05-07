package com.vladmihalcea.phd.dto;

import java.util.Objects;

/**
 * @author Vlad Mihalcea
 */
public class TagDto {

    private Long id;

    private String name;

    public Long getId() {
        return id;
    }

    public TagDto setId(Long id) {
        this.id = id;
        return this;
    }

    public String getName() {
        return name;
    }

    public TagDto setName(String name) {
        this.name = name;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TagDto tagDTO)) return false;
        return Objects.equals(getName(), tagDTO.getName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getName());
    }
}
