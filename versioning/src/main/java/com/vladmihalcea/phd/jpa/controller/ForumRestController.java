package com.vladmihalcea.phd.jpa.controller;

import com.vladmihalcea.phd.common.controller.VersionETag;
import com.vladmihalcea.phd.common.dto.PostCommentDto;
import com.vladmihalcea.phd.common.dto.PostDto;
import com.vladmihalcea.phd.common.dto.TagDto;
import com.vladmihalcea.phd.common.util.JsonUtil;
import com.vladmihalcea.phd.jpa.service.ForumService;
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

    @GetMapping(path = "/postMultiset", produces = "application/json")
    public @ResponseBody JsonNode findByIdUsingMultiset(
        @RequestParam("postId") Long postId) {
        return JsonUtil.JSON_MAPPER.valueToTree(forumService.loadPostDTOUsingMultiset(postId));
    }

    @PutMapping("/post")
    public ResponseEntity<Void> updatePost(
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @RequestBody PostDto postDTO) {
        return VersionETag.ok(
            forumService.updatePost(postDTO.setVersion(VersionETag.parse(ifMatch))).getVersion()
        );
    }

    /**
     * The per-change endpoints of the Multi-Ops variant (R1-14), with the paths of JDBC and jOOQ, except that the
     * post's own fields are updated with {@code PATCH}, since {@code PUT /post} merges the whole aggregate.
     */
    @PatchMapping("/post")
    public ResponseEntity<Void> patchPost(
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @RequestBody PostDto postDTO) {
        return VersionETag.ok(
            forumService.patchPost(postDTO.setVersion(VersionETag.parse(ifMatch))).getVersion()
        );
    }

    @PostMapping("/post/{postId}/tag")
    public ResponseEntity<TagDto> createTag(
            @PathVariable Long postId,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @RequestBody TagDto tagDTO) {
        return VersionETag.ok(
            forumService.createTag(postId, VersionETag.parse(ifMatch), tagDTO).getVersion(),
            tagDTO
        );
    }

    @DeleteMapping("/post/{postId}/tag/{tagId}")
    public ResponseEntity<Void> removeTag(
            @PathVariable Long postId,
            @PathVariable Long tagId,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch) {
        return VersionETag.ok(
            forumService.removeTag(postId, VersionETag.parse(ifMatch), new TagDto().setId(tagId)).getVersion()
        );
    }

    @PostMapping("/post/{postId}/comment")
    public ResponseEntity<PostCommentDto> createPostComment(
            @PathVariable Long postId,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @RequestBody PostCommentDto commentDTO) {
        return VersionETag.ok(
            forumService.createPostComment(postId, VersionETag.parse(ifMatch), commentDTO).getVersion(),
            commentDTO
        );
    }

    @DeleteMapping("/post/{postId}/comment/{commentId}")
    public ResponseEntity<Void> removePostComment(
            @PathVariable Long postId,
            @PathVariable Long commentId,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch) {
        return VersionETag.ok(
            forumService.removePostComment(postId, VersionETag.parse(ifMatch), commentId).getVersion()
        );
    }

    @PutMapping("/post/{postId}/comment")
    public ResponseEntity<Void> updatePostComment(
            @PathVariable Long postId,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @RequestBody PostCommentDto commentDTO) {
        return VersionETag.ok(
            forumService.updatePostComment(postId, VersionETag.parse(ifMatch), commentDTO).getVersion()
        );
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Void> onOptimisticLockingFailure() {
        return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).build();
    }
}
