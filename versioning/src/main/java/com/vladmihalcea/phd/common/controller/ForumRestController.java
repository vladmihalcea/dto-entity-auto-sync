package com.vladmihalcea.phd.common.controller;

import com.vladmihalcea.phd.common.dto.PostCommentDto;
import com.vladmihalcea.phd.common.dto.PostDto;
import com.vladmihalcea.phd.common.dto.TagDto;
import com.vladmihalcea.phd.common.service.ForumService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

/**
 * Every write requires the {@code If-Match} header with the post version the client
 * has read and returns the new post version in the {@code ETag} header.
 * Otherwise, the endpoints respond as in the {@code core} module.
 *
 * @author Vlad Mihalcea
 */
@RestController
public class ForumRestController {

    @Autowired
    private ForumService forumService;

    @GetMapping("/post")
    public PostDto findById(
        @RequestParam("postId") Long postId) {
        return forumService.loadPostDTO(postId);
    }

    @GetMapping("/postJsonNode")
    public JsonNode findJsonNodeByIdUsingMultiset(
        @RequestParam("postId") Long postId) {
        return forumService.loadPostJsonNodeUsingMultiset(postId);
    }

    @GetMapping("/postJsonString")
    public String findJsonStringByIdUsingMultiset(
        @RequestParam("postId") Long postId) {
        return forumService.loadPostJsonStringUsingMultiset(postId);
    }

    /**
     * Maps the MULTISET JSON into a {@code PostDto}, which Jackson serializes again, so that, compared with
     * {@code /postJsonString}, it measures the cost of returning a typed DTO instead of forwarding the database JSON.
     */
    @GetMapping("/postMultiset")
    public PostDto findByIdUsingMultiset(
        @RequestParam("postId") Long postId) {
        return forumService.loadPostDTOUsingMultiset(postId);
    }

    @PutMapping("/post")
    public ResponseEntity<Void> updatePost(
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @RequestBody PostDto postDTO) {
        return VersionETag.ok(
            forumService.updatePost(postDTO.setVersion(VersionETag.parse(ifMatch)))
        );
    }

    @PostMapping("/post/{postId}/tag")
    public ResponseEntity<TagDto> createTag(
            @PathVariable Long postId,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @RequestBody TagDto tagDTO) {
        return VersionETag.ok(
            forumService.createTag(postId, VersionETag.parse(ifMatch), tagDTO),
            tagDTO
        );
    }

    @DeleteMapping("/post/{postId}/tag/{tagId}")
    public ResponseEntity<Void> removeTag(
            @PathVariable Long postId,
            @PathVariable Long tagId,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch) {
        return VersionETag.ok(
            forumService.removeTag(postId, VersionETag.parse(ifMatch), new TagDto().setId(tagId))
        );
    }

    @PostMapping("/post/{postId}/comment")
    public ResponseEntity<PostCommentDto> createPostComment(
            @PathVariable Long postId,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @RequestBody PostCommentDto commentDTO) {
        return VersionETag.ok(
            forumService.createPostComment(postId, VersionETag.parse(ifMatch), commentDTO),
            commentDTO
        );
    }

    @DeleteMapping("/post/{postId}/comment/{commentId}")
    public ResponseEntity<Void> removePostComment(
            @PathVariable Long postId,
            @PathVariable Long commentId,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch) {
        return VersionETag.ok(
            forumService.removePostComment(postId, VersionETag.parse(ifMatch), commentId)
        );
    }

    @PutMapping("/post/{postId}/comment")
    public ResponseEntity<Void> updatePostComment(
            @PathVariable Long postId,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @RequestBody PostCommentDto commentDTO) {
        return VersionETag.ok(
            forumService.updatePostComment(postId, VersionETag.parse(ifMatch), commentDTO)
        );
    }

    @PostMapping("/post/diff-merge")
    public ResponseEntity<Void> diffMerge(
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @RequestBody PostDto[] posts) {
        return VersionETag.ok(
            forumService.diffMerge(posts[0], posts[1].setVersion(VersionETag.parse(ifMatch)))
        );
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Void> onOptimisticLockingFailure() {
        return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).build();
    }
}
