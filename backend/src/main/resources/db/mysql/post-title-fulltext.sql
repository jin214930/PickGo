-- MySQL 8.4.5 / InnoDB
-- ngram_token_size=2 상태에서 실행한다.
-- 실행 전 확인: SHOW INDEX FROM post WHERE Key_name = 'ft_post_title';

ALTER TABLE post
    ADD FULLTEXT INDEX ft_post_title (title) WITH PARSER ngram;

-- 실행 후 확인: SHOW INDEX FROM post WHERE Key_name = 'ft_post_title';
