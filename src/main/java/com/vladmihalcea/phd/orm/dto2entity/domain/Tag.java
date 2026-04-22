package com.vladmihalcea.phd.orm.dto2entity.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.NaturalId;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Vlad Mihalcea
 */
@Entity
@Table(name = "tags")
public class Tag extends Identifiable<Tag> {

    @NaturalId
    private String name;

    private String description;

    @ManyToMany
    @JoinTable(name = "post_tag",
        joinColumns = @JoinColumn(name = "post_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private List<Post> posts = new ArrayList<>();

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

    public List<Post> getPosts() {
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
