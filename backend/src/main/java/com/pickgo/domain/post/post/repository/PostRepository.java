package com.pickgo.domain.post.post.repository;

import com.pickgo.domain.post.post.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface PostRepository
        extends JpaRepository<Post, Long>, PostQueryRepository {
    @Query("""
                SELECT DISTINCT p FROM Post p
                JOIN FETCH p.performance perf
                LEFT JOIN FETCH perf.venue v
                LEFT JOIN FETCH perf.performanceSessions ps
                WHERE p.id = :id
                AND p.isPublished = true
            """)
    Optional<Post> findByIdWithAll(Long id);

    @Modifying
    @Query("UPDATE Post p SET p.views = p.views + :viewCount WHERE p.id = :id")
    void updateViewCount(long id, long viewCount);
}
