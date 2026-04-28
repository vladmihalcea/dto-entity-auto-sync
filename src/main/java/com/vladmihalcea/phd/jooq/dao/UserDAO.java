package com.vladmihalcea.phd.jooq.dao;

import com.vladmihalcea.phd.common.dto.UserDTO;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import static com.vladmihalcea.phd.jooq.schema.Tables.*;

/**
 * @author Vlad Mihalcea
 */
@Repository
public class UserDAO {

    private final DSLContext ctx;

    public UserDAO(DSLContext ctx) {
        this.ctx = ctx;
    }

    public UserDTO findById(Long id) {
        return ctx.select(USERS_ID, USERS_EMAIL, USERS_FIRST_NAME, USERS_LAST_NAME)
            .from(USERS)
            .where(USERS_ID.eq(id))
            .fetchOne(r -> new UserDTO()
                .setId(r.get(USERS_ID))
                .setEmail(r.get(USERS_EMAIL))
                .setFirstName(r.get(USERS_FIRST_NAME))
                .setLastName(r.get(USERS_LAST_NAME)));
    }

    public void insert(UserDTO user) {
        ctx.insertInto(USERS)
            .set(USERS_ID, user.getId())
            .set(USERS_EMAIL, user.getEmail())
            .set(USERS_FIRST_NAME, user.getFirstName())
            .set(USERS_LAST_NAME, user.getLastName())
            .execute();
    }
}

