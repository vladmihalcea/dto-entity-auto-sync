package com.vladmihalcea.phd.orm.dto2entity.domain;

import jakarta.persistence.*;

/**
 * @author Vlad Mihalcea
 */
@Entity
@Table(name = "post_comments")
public class PostComment {

    @Id
    @GeneratedValue
    private Long id;

    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    private User author;

    @ManyToOne(fetch = FetchType.LAZY)
    private PostComment parent;

    private int votes;

    public Long getId() {
        return id;
    }

    public PostComment setId(Long id) {
        this.id = id;
        return this;
    }

    public String getContent() {
        return content;
    }

    public PostComment setContent(String review) {
        this.content = review;
        return this;
    }

    public Post getPost() {
        return post;
    }

    public PostComment setPost(Post post) {
        this.post = post;
        return this;
    }

    public User getAuthor() {
        return author;
    }

    public PostComment setAuthor(User author) {
        this.author = author;
        return this;
    }

    public PostComment getParent() {
        return parent;
    }

    public void setParent(PostComment parent) {
        this.parent = parent;
    }

    public int getVotes() {
        return votes;
    }

    public PostComment setVotes(int votes) {
        this.votes = votes;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PostComment)) return false;
        return id != null && id.equals(((PostComment) o).getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
