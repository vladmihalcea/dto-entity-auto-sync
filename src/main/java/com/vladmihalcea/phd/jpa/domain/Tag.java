package com.vladmihalcea.phd.jpa.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.NaturalId;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Vlad Mihalcea
 */
@Entity
@Table(name = "tags")
@DynamicInsert @DynamicUpdate
public class Tag extends Identifiable<Tag> {

    @NaturalId
    private String name;

    private String description;

    @ManyToMany
    @JoinTable(name = "post_tag",
        joinColumns = @JoinColumn(name = "post_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<Post> posts = new HashSet<>();

    public Tag() {
        super(Tag.class);
    }

    public String getName() {
        return name;
    }

    public Tag setName(String name) {
        this.name = name;
        return this;
    }

    public Set<Post> getPosts() {
        return posts;
    }

    public String getDescription() {
        return description;
    }

    public Tag setDescription(String description) {
        this.description = description;
        return this;
    }
}
