package com.vladmihalcea.phd.jooq.schema;

import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;

import static org.jooq.impl.DSL.*;

/**
 * Manual schema definition mirroring the Hibernate-generated DDL.
 *
 * @author Vlad Mihalcea
 */
public final class Tables {

    // -------------------------------------------------------------------------
    // Tables
    // -------------------------------------------------------------------------
    public static final Table<Record> POSTS         = table(name("posts"));
    public static final Table<Record> POST_COMMENTS = table(name("post_comments"));
    public static final Table<Record> TAGS          = table(name("tags"));
    public static final Table<Record> USERS         = table(name("users"));
    public static final Table<Record> POST_TAG      = table(name("post_tag"));

    // -------------------------------------------------------------------------
    // posts fields
    // -------------------------------------------------------------------------
    public static final Field<Long>    POSTS_ID        = field(name("posts", "id"),        Long.class);
    public static final Field<String>  POSTS_TITLE     = field(name("posts", "title"),     String.class);
    public static final Field<String>  POSTS_CONTENT   = field(name("posts", "content"),   String.class);
    public static final Field<Integer> POSTS_VOTES     = field(name("posts", "votes"),     Integer.class);
    public static final Field<Long>    POSTS_AUTHOR_ID = field(name("posts", "author_id"), Long.class);

    // -------------------------------------------------------------------------
    // post_comments fields
    // -------------------------------------------------------------------------
    public static final Field<Long>    PC_ID        = field(name("post_comments", "id"),        Long.class);
    public static final Field<String>  PC_CONTENT   = field(name("post_comments", "content"),   String.class);
    public static final Field<Integer> PC_VOTES     = field(name("post_comments", "votes"),     Integer.class);
    public static final Field<Long>    PC_POST_ID   = field(name("post_comments", "post_id"),   Long.class);
    public static final Field<Long>    PC_AUTHOR_ID = field(name("post_comments", "author_id"), Long.class);
    public static final Field<Long>    PC_PARENT_ID = field(name("post_comments", "parent_id"), Long.class);

    // -------------------------------------------------------------------------
    // tags fields
    // -------------------------------------------------------------------------
    public static final Field<Long>   TAGS_ID          = field(name("tags", "id"),          Long.class);
    public static final Field<String> TAGS_NAME        = field(name("tags", "name"),        String.class);
    public static final Field<String> TAGS_DESCRIPTION = field(name("tags", "description"), String.class);

    // -------------------------------------------------------------------------
    // users fields
    // -------------------------------------------------------------------------
    public static final Field<Long>   USERS_ID         = field(name("users", "id"),         Long.class);
    public static final Field<String> USERS_EMAIL      = field(name("users", "email"),      String.class);
    public static final Field<String> USERS_FIRST_NAME = field(name("users", "first_name"), String.class);
    public static final Field<String> USERS_LAST_NAME  = field(name("users", "last_name"),  String.class);

    // -------------------------------------------------------------------------
    // post_tag fields
    // note: Tag owns the join table so post_id -> tags.id, tag_id -> posts.id
    // -------------------------------------------------------------------------
    public static final Field<Long> PT_POST_ID = field(name("post_tag", "post_id"), Long.class);
    public static final Field<Long> PT_TAG_ID  = field(name("post_tag", "tag_id"),  Long.class);

    private Tables() {}
}

