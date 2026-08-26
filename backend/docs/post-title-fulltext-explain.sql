-- MySQL 8.4.5 / InnoDB
-- Run against a database containing the ft_post_title ngram FULLTEXT index.
-- Replace the search term and pagination values with the benchmark case.

SHOW VARIABLES LIKE 'ngram_token_size';
SHOW INDEX FROM post WHERE Key_name = 'ft_post_title';

-- Before: function + leading wildcard prevents a normal B-Tree title index.
EXPLAIN
SELECT p.post_id, p.title
FROM post p
JOIN performance perf ON perf.performance_id = p.performance_id
JOIN venue v ON v.venue_id = perf.venue_id
WHERE p.is_published = TRUE
  AND LOWER(REPLACE(p.title, ' ', '')) LIKE '%라이온%'
ORDER BY p.post_id DESC
LIMIT 10 OFFSET 0;

-- After: the ngram FULLTEXT index can be selected for the title predicate.
EXPLAIN
SELECT p.post_id, p.title
FROM post p
JOIN performance perf ON perf.performance_id = p.performance_id
JOIN venue v ON v.venue_id = perf.venue_id
WHERE p.is_published = TRUE
  AND MATCH(p.title) AGAINST ('"라이온"' IN BOOLEAN MODE)
ORDER BY p.post_id DESC
LIMIT 10 OFFSET 0;

-- Count comparison for the same published-title condition.
EXPLAIN
SELECT COUNT(*)
FROM post p
WHERE p.is_published = TRUE
  AND MATCH(p.title) AGAINST ('"라이온"' IN BOOLEAN MODE);
