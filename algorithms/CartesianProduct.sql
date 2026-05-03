SELECT p.id,
       p.title,
       p.content,
       p.votes,
       p_u.id AS author_id,
       p_u.email,
       p_u.first_name,
       p_u.last_name,
       pc.id,
       pc.content,
       pc.votes,
       pc.parent_id,
       pc_u.id AS author_id,
       pc_u.email,
       pc_u.first_name,
       pc_u.last_name,
       t.id, t.name, t.description
FROM posts p
JOIN users p_u ON p_u.id = p.author_id
JOIN post_comments pc ON p.id = pc.post_id
JOIN users pc_u ON pc_u.id = pc.author_id
JOIN post_tags pt ON pt.post_id = p.id
JOIN tags t ON t.id = pt.tag_id
WHERE p.id = 838713941752041681
