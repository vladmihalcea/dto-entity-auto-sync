package com.vladmihalcea.phd.orm.dto2entity.dto;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @author Vlad Mihalcea
 */
public class PostDTO {

    private Long id;

    private String title;

    private String content;

    private UserDTO author;

    private int votes;

    private List<PostCommentDTO> replies;

    private Set<TagDTO> tags;

    public Long getId() {
        return id;
    }

    public PostDTO setId(Long id) {
        this.id = id;
        return this;
    }

    public String getTitle() {
        return title;
    }

    public PostDTO setTitle(String title) {
        this.title = title;
        return this;
    }

    public String getContent() {
        return content;
    }

    public PostDTO setContent(String content) {
        this.content = content;
        return this;
    }

    public UserDTO getAuthor() {
        return author;
    }

    public PostDTO setAuthor(UserDTO author) {
        this.author = author;
        return this;
    }

    public int getVotes() {
        return votes;
    }

    public PostDTO setVotes(int votes) {
        this.votes = votes;
        return this;
    }

    public PostDTO upvote() {
        this.votes++;
        return this;
    }

    public PostDTO downvote() {
        this.votes--;
        return this;
    }

    public List<PostCommentDTO> getReplies() {
        return replies;
    }

    public PostDTO setReplies(List<PostCommentDTO> replies) {
        this.replies = replies;
        return this;
    }

    public Set<TagDTO> getTags() {
        return tags;
    }

    public PostDTO setTags(Set<TagDTO> tags) {
        this.tags = tags;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PostDTO postDTO)) return false;
        return getVotes() == postDTO.getVotes() && Objects.equals(getId(), postDTO.getId()) && Objects.equals(getTitle(), postDTO.getTitle()) && Objects.equals(getContent(), postDTO.getContent()) && Objects.equals(getAuthor(), postDTO.getAuthor()) && Objects.equals(getReplies(), postDTO.getReplies()) && Objects.equals(getTags(), postDTO.getTags());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getTitle(), getContent(), getAuthor(), getVotes(), getReplies(), getTags());
    }
}
