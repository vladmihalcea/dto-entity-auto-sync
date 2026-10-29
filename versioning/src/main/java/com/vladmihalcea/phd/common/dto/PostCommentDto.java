package com.vladmihalcea.phd.common.dto;

import java.util.Objects;

/**
 * @author Vlad Mihalcea
 */
public class PostCommentDto {

    private Long id;

    private String content;

    private UserDto author;

    private int votes;

    private Long parentId;

    public Long getId() {
        return id;
    }

    public PostCommentDto setId(Long id) {
        this.id = id;
        return this;
    }

    public String getContent() {
        return content;
    }

    public PostCommentDto setContent(String content) {
        this.content = content;
        return this;
    }

    public UserDto getAuthor() {
        return author;
    }

    public PostCommentDto setAuthor(UserDto author) {
        this.author = author;
        return this;
    }

    public int getVotes() {
        return votes;
    }

    public PostCommentDto setVotes(int votes) {
        this.votes = votes;
        return this;
    }

    public PostCommentDto upvote() {
        this.votes++;
        return this;
    }

    public PostCommentDto downvote() {
        this.votes--;
        return this;
    }

    public Long getParentId() {
        return parentId;
    }

    public PostCommentDto setParentId(Long parentId) {
        this.parentId = parentId;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PostCommentDto that)) return false;
        return Objects.equals(getContent(), that.getContent()) && Objects.equals(getParentId(), that.getParentId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getContent(), getAuthor(), getParentId());
    }
}
