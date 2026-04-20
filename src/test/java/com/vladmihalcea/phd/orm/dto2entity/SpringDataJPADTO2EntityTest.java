package com.vladmihalcea.phd.orm.dto2entity;

import com.vladmihalcea.phd.orm.dto2entity.config.SpringDataJPADTO2EntityConfiguration;
import com.vladmihalcea.phd.orm.dto2entity.domain.Post;
import com.vladmihalcea.phd.orm.dto2entity.domain.PostComment;
import com.vladmihalcea.phd.orm.dto2entity.domain.Tag;
import com.vladmihalcea.phd.orm.dto2entity.domain.User;
import com.vladmihalcea.phd.orm.dto2entity.dto.PostCommentDTO;
import com.vladmihalcea.phd.orm.dto2entity.dto.PostDTO;
import com.vladmihalcea.phd.orm.dto2entity.dto.TagDTO;
import com.vladmihalcea.phd.orm.dto2entity.dto.UserContext;
import com.vladmihalcea.phd.orm.dto2entity.dto.mapper.UserMapper;
import com.vladmihalcea.phd.orm.dto2entity.service.ForumService;
import com.vladmihalcea.phd.orm.util.AbstractSpringTest;
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

/**
 * @author Vlad Mihalcea
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = SpringDataJPADTO2EntityConfiguration.class)
public class SpringDataJPADTO2EntityTest extends AbstractSpringTest {

    @Autowired
    private ForumService forumService;

    @Autowired
    private UserMapper userMapper;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Override
    protected Class<?>[] entities() {
        return new Class[] {
            Post.class,
            PostComment.class,
            Tag.class,
            User.class
        };
    }

    @Override
    public void afterInit() {
        transactionTemplate.execute(transactionStatus -> {
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

            entityManager.persist(
                new Post()
                    .setId(1L)
                    .setTitle("DTO to Entity Auto-Sync")
                    .setContent("""
                        Spring and Hibernate can help you do the aut-sync
                        between DTOs and JPA entities
                        """)
                    .setAuthor(vlad)
                    .addComment(
                        new PostComment()
                            .setContent("Interesting topic, but I would like to see more examples")
                            .setAuthor(john)
                    )
                    .addComment(
                        new PostComment()
                            .setContent("Can you apply this pattern to other platforms?")
                            .setAuthor(jane)
                    )
                    .addTag(new Tag().setName("Spring"))
                    .addTag(new Tag().setName("JPA"))
                    .addTag(new Tag().setName("Hibernate"))
                    .addTag(new Tag().setName("MapStruct"))
            );

            return null;
        });
    }

    @Test
    public void testDtoToEntitySync() {
        LOGGER.info("Sync DTOs and JPA entity state");

        PostDTO postDTO = forumService.findWithCommentsAndTagsById(1L);

        postDTO
            .setTitle("DTO to Entity Auto-Sync Pattern")
            .upvote();

        List<PostCommentDTO> replies = postDTO.getReplies();
        replies.removeFirst();
        replies.forEach(PostCommentDTO::upvote);
        replies.add(
            new PostCommentDTO()
                .setContent("Does it work with Entity Framework?")
                .setParentId(replies.getFirst().getId())
        );
        postDTO.getTags().remove(
            postDTO.getTags().stream()
                .filter(t -> t.getName().equals("JPA"))
                .findFirst()
                .orElseThrow()
        );
        postDTO.getTags().add(new TagDTO().setName("Jakarta Persistence"));

        forumService.updatePost(postDTO);

        PostDTO updatedPostDTO = forumService.findWithCommentsAndTagsById(1L);

        assertEquals(
            postDTO,
            updatedPostDTO
        );
    }
}
