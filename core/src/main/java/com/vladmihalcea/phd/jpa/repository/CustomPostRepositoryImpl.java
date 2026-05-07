package com.vladmihalcea.phd.jpa.repository;

import com.blazebit.persistence.CriteriaBuilder;
import com.blazebit.persistence.CriteriaBuilderFactory;
import com.blazebit.persistence.view.EntityViewManager;
import com.blazebit.persistence.view.EntityViewSetting;
import com.vladmihalcea.phd.jpa.domain.Post;
import jakarta.persistence.EntityManager;

/**
 * @author Vlad Mihalcea
 */
public class CustomPostRepositoryImpl implements CustomPostRepository {

    private final CriteriaBuilderFactory cbf;
    private final EntityViewManager evm;
    private final EntityManager entityManager;

    public CustomPostRepositoryImpl(CriteriaBuilderFactory cbf, EntityViewManager evm, EntityManager entityManager) {
        this.cbf = cbf;
        this.evm = evm;
        this.entityManager = entityManager;
    }

    @Override
    public com.vladmihalcea.phd.jpa.view.PostDto loadPostDTOUsingMultiset(Long id) {
        CriteriaBuilder<Post> cb = cbf.create(entityManager, Post.class)
            .where("id").eq(id);

        return evm
            .applySetting(EntityViewSetting.create(com.vladmihalcea.phd.jpa.view.PostDto.class), cb)
            .getSingleResult();
    }
}
