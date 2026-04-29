package com.vladmihalcea.phd.common.util;

import com.vladmihalcea.phd.orm.dto2entity.domain.Post;
import com.vladmihalcea.phd.orm.dto2entity.domain.PostComment;
import com.vladmihalcea.phd.orm.dto2entity.domain.Tag;
import com.vladmihalcea.phd.orm.dto2entity.domain.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * @author Vlad Mihalcea
 */
@ExtendWith(SpringExtension.class)
public abstract class AbstractSpringTest {

    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    @Autowired
    protected TransactionTemplate transactionTemplate;

    @PersistenceContext
    protected EntityManager entityManager;

    @BeforeEach
    public void init() {
        transactionTemplate.execute(transactionStatus -> {
            deleteAll();
            return null;
        });
        afterInit();
    }

    protected Class<?>[] entities() {
        return new Class[] {
            Post.class,
            PostComment.class,
            Tag.class,
            User.class
        };
    }

    protected void deleteAll() {
        entityManager.createNativeQuery("DELETE FROM post_tag").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM post_comments").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM posts").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM tags").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM users").executeUpdate();
    }

    protected void afterInit() {
    }
}

