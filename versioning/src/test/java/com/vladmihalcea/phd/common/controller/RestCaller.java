package com.vladmihalcea.phd.common.controller;

import com.vladmihalcea.phd.common.dto.PostCommentDto;
import com.vladmihalcea.phd.common.dto.PostDto;
import com.vladmihalcea.phd.common.dto.TagDto;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;

/**
 * The write methods send the post version as the {@code If-Match} header
 * and return the new post version from the {@code ETag} header.
 *
 * @author Vlad Mihalcea
 */
public class RestCaller {

    private final int port;
    private final RestTemplate restTemplate;

    public RestCaller(int port, RestTemplate restTemplate) {
        this.port = port;
        this.restTemplate = restTemplate;
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    public PostDto loadPostDTO(Long postId) {
        return restTemplate.getForObject(
            baseUrl() + "/post?postId={postId}",
            PostDto.class,
            postId
        );
    }

    public JsonNode loadPostJsonNode(Long postId) {
        return restTemplate.getForObject(
            baseUrl() + "/postJsonNode?postId={postId}",
            JsonNode.class,
            postId
        );
    }

    public String loadPostJsonString(Long postId) {
        return restTemplate.getForObject(
            baseUrl() + "/postJsonString?postId={postId}",
            String.class,
            postId
        );
    }

    /**
     * Reads the typed MS-JSON response as a String, like {@link #loadPostJsonString(Long)}, so that both MS-JSON
     * variants do the same work on the client and differ only in what the server does.
     */
    public String loadPostDTOUsingMultisetAsString(Long postId) {
        return restTemplate.getForObject(
            baseUrl() + "/postMultiset?postId={postId}",
            String.class,
            postId
        );
    }

    public JsonNode loadPostDTOUsingMultiset(Long postId) {
        return restTemplate.getForObject(
            baseUrl() + "/postMultiset?postId={postId}",
            JsonNode.class,
            postId
        );
    }

    public short updatePost(PostDto postDTO) {
        return write(
            HttpMethod.PUT,
            postDTO.getVersion(),
            postDTO,
            Void.class,
            baseUrl() + "/post"
        );
    }

    /**
     * Updates the post's own fields with {@code PATCH}, as the JPA Multi-Ops variant does. The JDK {@code HttpClient}
     * of {@link com.vladmihalcea.phd.common.util.BenchmarkRestTemplate} supports {@code PATCH}, while the
     * {@code HttpURLConnection} client of {@code new RestTemplate()} does not.
     */
    public short patchPost(PostDto postDTO) {
        return write(
            HttpMethod.PATCH,
            postDTO.getVersion(),
            postDTO,
            Void.class,
            baseUrl() + "/post"
        );
    }

    public short createTag(Long postId, short version, TagDto tagDTO) {
        return write(
            HttpMethod.POST,
            version,
            tagDTO,
            TagDto.class,
            baseUrl() + "/post/{postId}/tag",
            postId
        );
    }

    public short removeTag(Long postId, short version, TagDto tag) {
        return write(
            HttpMethod.DELETE,
            version,
            null,
            Void.class,
            baseUrl() + "/post/{postId}/tag/{tagId}",
            postId,
            tag.getId()
        );
    }

    public short createPostComment(Long postId, short version, PostCommentDto newReply) {
        return write(
            HttpMethod.POST,
            version,
            newReply,
            PostCommentDto.class,
            baseUrl() + "/post/{postId}/comment",
            postId
        );
    }

    public short removePostComment(Long postId, short version, Long id) {
        return write(
            HttpMethod.DELETE,
            version,
            null,
            Void.class,
            baseUrl() + "/post/{postId}/comment/{commentId}",
            postId,
            id
        );
    }

    public short updatePostComment(Long postId, short version, PostCommentDto reply) {
        return write(
            HttpMethod.PUT,
            version,
            reply,
            Void.class,
            baseUrl() + "/post/{postId}/comment",
            postId
        );
    }

    public short diffMerge(PostDto originalPost, PostDto modifiedPost) {
        return write(
            HttpMethod.POST,
            modifiedPost.getVersion(),
            new PostDto[]{originalPost, modifiedPost},
            Void.class,
            baseUrl() + "/post/diff-merge"
        );
    }

    /**
     * Reads the response body as the given type, which is the type that the {@code core} module reads,
     * so that the two modules do the same work on the client and differ only in the version headers.
     */
    private short write(HttpMethod method, short version, Object body, Class<?> responseType,
                        String url, Object... uriVariables) {
        HttpHeaders headers = new HttpHeaders();
        headers.setIfMatch(VersionETag.format(version));
        ResponseEntity<?> response = restTemplate.exchange(
            url,
            method,
            new HttpEntity<>(body, headers),
            responseType,
            uriVariables
        );
        return VersionETag.parse(response.getHeaders().getETag());
    }
}
