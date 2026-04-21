package com.vladmihalcea.phd.orm.dto2entity.domain;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Vlad Mihalcea
 */
@Entity
@Table(name = "posts")
public class Post {

    @Id
    private Long id;

    private String title;

    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    private User author;

    private int votes;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PostComment> comments = new ArrayList<>();

    @ManyToMany(mappedBy = "posts", cascade = {
        CascadeType.PERSIST, CascadeType.MERGE, CascadeType.DETACH}
    )
    private Set<Tag> tags = new HashSet<>();

    public Long getId() {
        return id;
    }

    public Post setId(Long id) {
        this.id = id;
        return this;
    }

    public String getTitle() {
        return title;
    }

    public Post setTitle(String title) {
        this.title = title;
        return this;
    }

    public String getContent() {
        return content;
    }

    public Post setContent(String content) {
        this.content = content;
        return this;
    }

    public User getAuthor() {
        return author;
    }

    public Post setAuthor(User author) {
        this.author = author;
        return this;
    }

    public int getVotes() {
        return votes;
    }

    public Post setVotes(int votes) {
        this.votes = votes;
        return this;
    }

    public List<PostComment> getComments() {
        return comments;
    }

    public Post addComment(PostComment comment) {
        comments.add(comment);
        comment.setPost(this);
        return this;
    }

    public Post removeComment(PostComment comment) {
        comments.stream()
            .filter(c -> comment.equals(c.getParent()))
            .toList()
            .forEach(child -> {
                child.setParent(null);
                removeComment(child);
            });

        comments.remove(comment);
        comment.setPost(null);
        return this;
    }

    public Set<Tag> getTags() {
        return tags;
    }

    public void setTags(Set<Tag> tags) {
        this.tags = tags;
    }

    public Post addTag(Tag tag) {
        tags.add(tag);
        tag.getPosts().add(this);
        return this;
    }

    public void removeTag(Tag tag) {
        tags.remove(tag);
        tag.getPosts().remove(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Post)) return false;
        return id != null && id.equals(((Post) o).getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
