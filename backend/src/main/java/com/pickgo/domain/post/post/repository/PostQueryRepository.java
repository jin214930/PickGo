package com.pickgo.domain.post.post.repository;

import com.pickgo.domain.performance.performance.entity.PerformanceType;
import com.pickgo.domain.post.post.entity.Post;
import com.pickgo.domain.post.post.entity.PostSortType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface PostQueryRepository {
    Page<Post> searchPosts(
            Pageable pageable,
            String keyword,
            PerformanceType type,
            PostSortType sort
    );

    List<Post> findPopularPosts(int size, PerformanceType type);

    List<Post> findOpeningSoonPosts();
}
