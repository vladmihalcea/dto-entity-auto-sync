CREATE TABLE public.post_comments (
    id bigint NOT NULL,
    content character varying(255),
    votes integer NOT NULL,
    author_id bigint,
    parent_id bigint,
    post_id bigint
);

ALTER TABLE public.post_comments OWNER TO postgres;

CREATE TABLE public.post_tags (
    post_id bigint NOT NULL,
    tag_id bigint NOT NULL
);

ALTER TABLE public.post_tags OWNER TO postgres;

CREATE TABLE public.posts (
    id bigint NOT NULL,
    content character varying(255),
    title character varying(255),
    votes integer NOT NULL,
    author_id bigint
);

ALTER TABLE public.posts OWNER TO postgres;

CREATE TABLE public.tags (
    id bigint NOT NULL,
    description character varying(255),
    name character varying(255)
);

ALTER TABLE public.tags OWNER TO postgres;

CREATE TABLE public.users (
    id bigint NOT NULL,
    email character varying(255),
    first_name character varying(255),
    last_name character varying(255)
);

ALTER TABLE public.users OWNER TO postgres;

ALTER TABLE ONLY public.post_comments
    ADD CONSTRAINT post_comments_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.post_tags
    ADD CONSTRAINT post_tags_pkey PRIMARY KEY (post_id, tag_id);

ALTER TABLE ONLY public.posts
    ADD CONSTRAINT posts_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.tags
    ADD CONSTRAINT tags_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.tags
    ADD CONSTRAINT uk_tags_name UNIQUE (name);

ALTER TABLE ONLY public.users
    ADD CONSTRAINT uk_users_email UNIQUE (email);

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);

CREATE INDEX idx_post_comments_author_id ON public.post_comments USING btree (author_id);
CREATE INDEX idx_post_comments_parent_id ON public.post_comments USING btree (parent_id);
CREATE INDEX idx_post_comments_post_id ON public.post_comments USING btree (post_id);
CREATE INDEX idx_post_tags_post_id ON public.post_tags USING btree (post_id);
CREATE INDEX idx_post_tags_tag_id ON public.post_tags USING btree (tag_id);
CREATE INDEX idx_posts_author_id ON public.posts USING btree (author_id);

ALTER TABLE ONLY public.post_tags
    ADD CONSTRAINT fk_post_tags_tag_id FOREIGN KEY (tag_id) REFERENCES public.tags(id);

ALTER TABLE ONLY public.posts
    ADD CONSTRAINT fk_posts_author_id FOREIGN KEY (author_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.post_comments
    ADD CONSTRAINT fk_post_comments_author_id FOREIGN KEY (author_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.post_comments
    ADD CONSTRAINT fk_post_comments_post_id FOREIGN KEY (post_id) REFERENCES public.posts(id);

ALTER TABLE ONLY public.post_comments
    ADD CONSTRAINT fk_post_comments_parent_id FOREIGN KEY (parent_id) REFERENCES public.post_comments(id);

ALTER TABLE ONLY public.post_tags
    ADD CONSTRAINT fk_post_tags_post_id FOREIGN KEY (post_id) REFERENCES public.posts(id);