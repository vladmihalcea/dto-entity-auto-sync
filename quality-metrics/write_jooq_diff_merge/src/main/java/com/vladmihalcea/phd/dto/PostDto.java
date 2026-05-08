package com.vladmihalcea.phd.dto;

import java.util.List;
import java.util.Objects;

/**
 * @author Vlad Mihalcea
 */
public class PostDto {

    private Long id;

    private String title;

    private String content;

    private UserDto author;

    private int votes;

    private List<PostCommentDto> replies;

    private List<TagDto> tags;

    public Long getId() {
        return id;
    }

    public PostDto setId(Long id) {
        this.id = id;
        return this;
    }

    public String getTitle() {
        return title;
    }

    public PostDto setTitle(String title) {
        this.title = title;
        return this;
    }

    public String getContent() {
        return content;
    }

    public PostDto setContent(String content) {
        this.content = content;
        return this;
    }

    public UserDto getAuthor() {
        return author;
    }

    public PostDto setAuthor(UserDto author) {
        this.author = author;
        return this;
    }

    public int getVotes() {
        return votes;
    }

    public PostDto setVotes(int votes) {
        this.votes = votes;
        return this;
    }

    public PostDto upvote() {
        this.votes++;
        return this;
    }

    public PostDto downvote() {
        this.votes--;
        return this;
    }

    public List<PostCommentDto> getReplies() {
        return replies;
    }

    public PostDto setReplies(List<PostCommentDto> replies) {
        this.replies = replies;
        return this;
    }

    public List<TagDto> getTags() {
        return tags;
    }

    public PostDto setTags(List<TagDto> tags) {
        this.tags = tags;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PostDto postDTO)) return false;
        return getVotes() == postDTO.getVotes() && Objects.equals(getId(), postDTO.getId()) && Objects.equals(getTitle(), postDTO.getTitle()) && Objects.equals(getContent(), postDTO.getContent()) && Objects.equals(getAuthor(), postDTO.getAuthor()) && Objects.equals(getReplies(), postDTO.getReplies()) && Objects.equals(getTags(), postDTO.getTags());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getTitle(), getContent(), getAuthor(), getVotes(), getReplies(), getTags());
    }
}
