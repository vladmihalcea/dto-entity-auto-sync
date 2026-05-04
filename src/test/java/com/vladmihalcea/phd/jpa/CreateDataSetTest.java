package com.vladmihalcea.phd.jpa;

import com.vladmihalcea.phd.common.util.AbstractSpringTest;
import com.vladmihalcea.phd.jpa.config.JpaConfiguration;
import com.vladmihalcea.phd.jpa.domain.Post;
import com.vladmihalcea.phd.jpa.domain.PostComment;
import com.vladmihalcea.phd.jpa.domain.Tag;
import com.vladmihalcea.phd.jpa.domain.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Vlad Mihalcea
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = JpaConfiguration.class)
public class CreateDataSetTest extends AbstractSpringTest {

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private DataSource dataSource;

    public static final List<String> TABLES = List.of(
        "users",
        "tags",
        "posts",
        "post_tags",
        "post_comments"
    );

    @Test
    //@Disabled("This test is used to create a data set for the benchmarks")
    public void createDataSet() {
        LOGGER.info("Start data set creation");

        truncateAll();

        int iterations = 20;
        int postCount = 1000;

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

        transactionTemplate.<Long>execute(transactionStatus -> {
            entityManager.persist(vlad);
            entityManager.persist(john);
            entityManager.persist(jane);

            return null;
        });

        List<Integer> collectionSizes = List.of(0, 1, 5, 25, 100);

        for (int collectionSize : collectionSizes) {
            transactionTemplate.<Long>execute(transactionStatus -> {
                Post post = new Post()
                    .setId(100_000L + collectionSize)
                    .setTitle("DTO to Entity Auto-Sync")
                    .setContent("""
                            Spring and Hibernate can help you do the auto-sync
                            between DTOs and JPA entities
                            """)
                    .setAuthor(vlad);

                for (int i = 0; i < collectionSize; i++) {
                    PostComment comment = new PostComment().setAuthor(vlad);
                    Tag tag = new Tag();
                    post.addComment(comment.setContent(String.format("Comment nr. %d", comment.getId())));
                    post.addTag(tag.setName(String.format("Tag nr. %d", tag.getId())));
                }

                entityManager.persist(post);

                return null;
            });
        }

        AtomicLong counter = new AtomicLong(0);

        List<Long> ids = new ArrayList<>();

        for (int i = 0; i < iterations; i++) {
            ids.clear();
            transactionTemplate.<Long>execute(transactionStatus -> {
                for (long j = 1; j <= postCount; j++) {
                    Post post = new Post()
                        .setId(counter.incrementAndGet())
                        .setTitle("DTO to Entity Auto-Sync")
                        .setContent("""
                            Spring and Hibernate can help you do the o-sync
                            between DTOs and JPA entities
                            """)
                        .setAuthor(vlad)
                        .addComment(
                            new PostComment()
                                .setContent("Interesting topic, but I would like to see more examples")
                                .setAuthor(john)
                        )
                        .addTag(new Tag().setName("Spring nr. " + counter.get()))
                        .addTag(new Tag().setName("JPA nr. " + counter.get()))
                        .addTag(new Tag().setName("Hibernate nr. " + counter.get()))
                        .addTag(new Tag().setName("MapStruct nr. " + counter.get()));

                    entityManager.persist(post);
                    ids.add(counter.get());
                }

                return null;
            });

            transactionTemplate.<Long>execute(transactionStatus -> {
                List<PostComment> postComments = entityManager.createQuery("""
                    select pc
                    from PostComment pc
                    join fetch pc.post
                    where pc.post.id in :ids
                    """, PostComment.class)
                .setParameter("ids", ids)
                .getResultList();

                for (PostComment parentPostComment : postComments) {
                    entityManager.persist(
                        new PostComment()
                            .setPost(parentPostComment.getPost())
                            .setContent("Can you apply this pattern to other platforms?")
                            .setAuthor(jane)
                            .setParent(parentPostComment)
                    );
                }

                return null;
            });
        }

        testExport();

        LOGGER.info("End data set creation");
    }

    @Test
    @Disabled
    public void testImport() {
        truncateAll();
        transactionTemplate.execute(status -> {
            try {
                for(String table : TABLES) {
                    jdbcTemplate.execute(
                        String.format(
                            "COPY %s FROM '%s' DELIMITER ',' CSV;",
                            table,
                            Paths.get(
                                Thread
                                    .currentThread()
                                    .getContextClassLoader()
                                    .getResource(String.format("data/%s.csv", table)).toURI()
                            ).toAbsolutePath()
                        )
                    );
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return null;
        });

        try(Connection connection = dataSource.getConnection();
            Statement statement = connection.createStatement()) {
            connection.setAutoCommit(true);
            statement.executeLargeUpdate("VACUUM FULL ANALYZE");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @Disabled
    public void testExport() {
        File testResourcesFolder;

        try {
            testResourcesFolder = Paths.get(
                Thread
                    .currentThread()
                    .getContextClassLoader()
                    .getResource("data/posts.csv").toURI())
                .getParent()
                .resolve("../../../src/test/resources/data")
                .normalize()
                .toFile();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        assertTrue(testResourcesFolder.exists(), "Test resources folder does not exist: " + testResourcesFolder);

        String testResourcesFolderPath = testResourcesFolder.getAbsolutePath();

        transactionTemplate.execute(status -> {
            try {
                for(String table : TABLES) {
                    jdbcTemplate.execute(
                        String.format(
                            "COPY %s TO '%s' DELIMITER ',' CSV;",
                            table,
                            Paths.get(testResourcesFolderPath, String.format("%s.csv", table)).toAbsolutePath()
                        )
                    );
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }

    private void truncateAll() {
        transactionTemplate.execute(status -> {
            for(String table : TABLES.reversed()) {
                jdbcTemplate.execute(String.format("TRUNCATE %s CASCADE", table));

            }
            return null;
        });
    }
}
