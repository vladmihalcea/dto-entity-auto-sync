package com.vladmihalcea.phd.orm.dto2entity.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

/**
 * @author Vlad Mihalcea
 */
@Entity
@Table(name = "post_comments")
@DynamicInsert @DynamicUpdate
public class PostComment extends Identifiable<PostComment> {

    private String content;

    private int votes;

    @ManyToOne(fetch = FetchType.LAZY)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    private User author;

    @ManyToOne(fetch = FetchType.LAZY)
    private PostComment parent;

    public PostComment() {
        super(PostComment.class);
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

    public PostComment setParent(PostComment parent) {
        this.parent = parent;
        return this;
    }

    public int getVotes() {
        return votes;
    }

    public PostComment setVotes(int votes) {
        this.votes = votes;
        return this;
    }
}
