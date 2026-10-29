package com.vladmihalcea.phd.jpa.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.OptimisticLock;
import org.hibernate.annotations.SortComparator;
import org.hibernate.annotations.SortNatural;

import java.util.*;

/**
 * @author Vlad Mihalcea
 */
@Entity
@Table(name = "posts")
@DynamicInsert @DynamicUpdate
public class Post extends Identifiable<Post> {

    private String title;

    private String content;

    private int votes;

    /**
     * The aggregate root version, incremented whenever the post
     * or any of its child entities is modified.
     */
    @Version
    @ColumnDefault("0")
    private short version;

    @ManyToOne(fetch = FetchType.LAZY)
    private User author;

    /*
     * Hibernate excludes the mappedBy collections from the optimistic locking checks by default,
     * so adding or removing a comment or a tag would not increment the post version.
     */

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @OptimisticLock(excluded = false)
    private List<PostComment> comments = new ArrayList<>();

    @ManyToMany(mappedBy = "posts", cascade = {
        CascadeType.PERSIST, CascadeType.MERGE, CascadeType.DETACH}
    )
    @OptimisticLock(excluded = false)
    private SortedSet<Tag> tags = new TreeSet<>();

    public Post() {
        super(Post.class);
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

    public short getVersion() {
        return version;
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

    public void setTags(SortedSet<Tag> tags) {
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
}
