package com.vladmihalcea.phd.jpa;

import com.vladmihalcea.phd.common.dto.PostCommentDto;
import com.vladmihalcea.phd.common.dto.PostDto;
import com.vladmihalcea.phd.common.dto.TagDto;
import com.vladmihalcea.phd.common.dto.UserContext;
import com.vladmihalcea.phd.common.util.AbstractSpringTest;
import com.vladmihalcea.phd.common.util.TimeSortedIdentifierGenerator;
import com.vladmihalcea.phd.jpa.config.JpaConfiguration;
import com.vladmihalcea.phd.jpa.domain.Post;
import com.vladmihalcea.phd.jpa.domain.PostComment;
import com.vladmihalcea.phd.jpa.domain.Tag;
import com.vladmihalcea.phd.jpa.domain.User;
import com.vladmihalcea.phd.jpa.mapper.UserMapper;
import com.vladmihalcea.phd.jpa.service.ForumService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Vlad Mihalcea
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = JpaConfiguration.class)
public class JpaTest extends AbstractSpringTest {

    @Autowired
    private ForumService forumService;

    @Autowired
    private UserMapper userMapper;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Long postId;

    @Override
    public void afterInit() {
        postId = transactionTemplate.<Long>execute(transactionStatus -> {
            User vlad = new User()
                .setEmail("vlad@acme.com")
                .setFirstName("Vlad")
                .setLastName("Mihalcea");
            User john = new User()
                .setEmail("john@acme.com")
                .setFirstName("John")
                .setLastName("Doe");
            User jane = new User()
                .setEmail("jane@acme.com")
                .setFirstName("Jane")
                .setLastName("Doe");
            entityManager.persist(vlad);
            entityManager.persist(john);
            entityManager.persist(jane);

            UserContext.setCurrentUser(
                userMapper.toDTO(vlad)
            );

            PostComment parentPostComment = new PostComment()
                .setContent("Interesting topic, but I would like to see more examples")
                .setAuthor(john);

            PostComment childPostComment = new PostComment()
                .setContent("Can you apply this pattern to other platforms?")
                .setAuthor(jane)
                .setParent(parentPostComment);

            Post post = new Post()
                .setTitle("DTO to Entity Auto-Sync")
                .setContent("""
                        Spring and Hibernate can help you do the aut-sync
                        between DTOs and JPA entities
                        """)
                .setAuthor(vlad)
                .addComment(parentPostComment)
                .addComment(childPostComment)
                .addTag(new Tag().setName("Spring"))
                .addTag(new Tag().setName("JPA"))
                .addTag(new Tag().setName("Hibernate"))
                .addTag(new Tag().setName("MapStruct"));

            entityManager.persist(post);

            return post.getId();
        });
    }

    @Test
    public void testDtoToEntitySync() {
        LOGGER.info("Sync DTOs and JPA entity state");
        PostDto postDTO = forumService.loadPostDTO(postId);

        postDTO
            .setTitle("DTO to Entity Auto-Sync Pattern")
            .upvote();

        List<PostCommentDto> replies = postDTO.getReplies();
        replies.removeIf(comment -> comment.getParentId() != null);
        replies.forEach(PostCommentDto::upvote);
        replies.add(
            new PostCommentDto()
                .setId(TimeSortedIdentifierGenerator.generate())
                .setContent("Does it work with Entity Framework?")
                .setAuthor(UserContext.getCurrentUser())
                .setParentId(replies.getFirst().getId())
        );
        postDTO.getTags().remove(
            postDTO.getTags().stream()
                .filter(t -> t.getName().equals("JPA"))
                .findFirst()
                .orElseThrow()
        );
        postDTO.getTags().add(
            new TagDto()
                .setId(TimeSortedIdentifierGenerator.generate())
                .setName("Jakarta Persistence")
        );

        LOGGER.info("Update JPA entity state from PostDTO");
        forumService.updatePost(postDTO);

        LOGGER.info("Reload PostDTO to check the modifications");
        PostDto updatedPostDTO = forumService.loadPostDTO(postId);

        assertEquals(
            postDTO,
            updatedPostDTO
        );
    }

    @Test
    public void testLoadPostDTOUsingMultiset() {
        LOGGER.info("Load PostDTO using loadPostEntityWithAllAssociations and MULTISET");
        PostDto defaultPostDTO = forumService.loadPostDTO(postId);
        PostDto multiSetPostDTO = forumService.loadPostDTOUsingMultiset(postId);

        assertEquals(defaultPostDTO, multiSetPostDTO);
    }

    @Test
    public void testRemoveParentPostComment() {
        LOGGER.info("Remove a parent PostCommentDTO and see whether the children are also removed");
        PostDto postDTO = forumService.loadPostDTO(postId);

        List<PostCommentDto> replies = postDTO.getReplies();
        PostCommentDto parent = replies.stream()
            .filter(c -> c.getParentId() == null)
            .findFirst()
            .orElseThrow();
        replies.remove(parent);

        forumService.updatePost(postDTO);

        PostDto updatedPostDTO = forumService.loadPostDTO(postId);
        assertTrue(updatedPostDTO.getReplies().isEmpty());
    }
}
