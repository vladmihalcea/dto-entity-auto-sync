package com.vladmihalcea.phd.orm.dto2entity.jmh.rest;

import com.vladmihalcea.phd.common.dto.UserContext;
import com.vladmihalcea.phd.orm.dto2entity.domain.Post;
import com.vladmihalcea.phd.orm.dto2entity.domain.PostComment;
import com.vladmihalcea.phd.orm.dto2entity.domain.Tag;
import com.vladmihalcea.phd.orm.dto2entity.domain.User;
import com.vladmihalcea.phd.orm.dto2entity.dto.mapper.UserMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaDelete;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.jpa.EntityManagerFactoryInfo;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Spring-managed helper used by JMH benchmarks to create and clean up the
 * standard forum dataset. Uses {@link EntityManagerFactory} directly
 * (resource-local transactions) so that each call gets a fully independent
 * {@link EntityManager} with no first-level cache bleed-over between
 * benchmark invocations.
 *
 * @author Vlad Mihalcea
 */
@Component
public class SpringDataJPAForumBenchmarkSetup {

    @Autowired
    private EntityManagerFactory emf;

    @Autowired
    private UserMapper userMapper;

    /**
     * Monotonic counter used to assign entity IDs so that rapid consecutive
     * invocations never hit the TSID same-millisecond collision window.
     */
    private static final AtomicLong ID_COUNTER =
        new AtomicLong(System.currentTimeMillis() * 1_000);

    private static long nextId() {
        return ID_COUNTER.getAndIncrement();
    }

    /**
     * Returns the native (non-Spring-proxied) {@link EntityManagerFactory} so
     * that {@code createEntityManager()} gives a truly fresh Hibernate session
     * with an empty first-level cache on every invocation.
     */
    private EntityManagerFactory nativeEmf() {
        return ((EntityManagerFactoryInfo) emf).getNativeEntityManagerFactory();
    }

    /**
     * Creates the standard forum fixture (3 users, 1 post, 2 comments, 4 tags)
     * and stores the current user in {@link UserContext}.
     *
     * @return the id of the persisted {@link Post}
     */
    public Long createTestData() {
        EntityManager em = nativeEmf().createEntityManager();
        try {
            em.getTransaction().begin();

            User vlad = new User().setId(nextId())
                .setEmail("vlad@acme.com")
                .setFirstName("Vlad")
                .setLastName("Mihalcea");
            User john = new User().setId(nextId())
                .setEmail("john@acme.com")
                .setFirstName("John")
                .setLastName("Doe");
            User jane = new User().setId(nextId())
                .setEmail("jane@acme.com")
                .setFirstName("Jane")
                .setLastName("Doe");
            em.persist(vlad);
            em.persist(john);
            em.persist(jane);

            UserContext.setCurrentUser(userMapper.toDTO(vlad));

            PostComment parentComment = new PostComment().setId(nextId())
                .setContent("Interesting topic, but I would like to see more examples")
                .setAuthor(john);

            PostComment childComment = new PostComment().setId(nextId())
                .setContent("Can you apply this pattern to other platforms?")
                .setAuthor(jane)
                .setParent(parentComment);

            Post post = new Post().setId(nextId())
                .setTitle("DTO to Entity Auto-Sync")
                .setContent("""
                        Spring and Hibernate can help you do the aut-sync
                        between DTOs and JPA entities
                        """)
                .setAuthor(vlad)
                .addComment(parentComment)
                .addComment(childComment)
                .addTag(new Tag().setId(nextId()).setName("Spring"))
                .addTag(new Tag().setId(nextId()).setName("JPA"))
                .addTag(new Tag().setId(nextId()).setName("Hibernate"))
                .addTag(new Tag().setId(nextId()).setName("MapStruct"));

            em.persist(post);
            em.getTransaction().commit();
            return post.getId();
        } catch (Exception e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }

    /** Deletes all entities in the correct order to respect FK constraints. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void deleteAll() {
        EntityManager em = nativeEmf().createEntityManager();
        try {
            em.getTransaction().begin();
            CriteriaBuilder cb = em.getCriteriaBuilder();
            for (Class entity : new Class[]{ PostComment.class, Post.class, Tag.class, User.class }) {
                CriteriaDelete delete = cb.createCriteriaDelete(entity);
                delete.from(entity);
                em.createQuery(delete).executeUpdate();
            }
            em.getTransaction().commit();
        } catch (Exception e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }
}







