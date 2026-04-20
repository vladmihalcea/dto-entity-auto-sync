package com.vladmihalcea.phd.orm.dto2entity.dto;

import java.util.Objects;

/**
 * @author Vlad Mihalcea
 */
public class PostCommentDTO {

    private Long id;

    private String content;

    private String author;

    private int votes;

    private Long parentId;

    public Long getId() {
        return id;
    }

    public PostCommentDTO setId(Long id) {
        this.id = id;
        return this;
    }

    public String getContent() {
        return content;
    }

    public PostCommentDTO setContent(String content) {
        this.content = content;
        return this;
    }

    public String getAuthor() {
        return author;
    }

    public PostCommentDTO setAuthor(String author) {
        this.author = author;
        return this;
    }

    public int getVotes() {
        return votes;
    }

    public PostCommentDTO setVotes(int votes) {
        this.votes = votes;
        return this;
    }

    public PostCommentDTO upvote() {
        this.votes++;
        return this;
    }

    public PostCommentDTO downvote() {
        this.votes--;
        return this;
    }

    public Long getParentId() {
        return parentId;
    }

    public PostCommentDTO setParentId(Long parentId) {
        this.parentId = parentId;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PostCommentDTO that)) return false;
        return Objects.equals(getContent(), that.getContent()) && Objects.equals(getParentId(), that.getParentId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getContent(), getAuthor(), getParentId());
    }
}
