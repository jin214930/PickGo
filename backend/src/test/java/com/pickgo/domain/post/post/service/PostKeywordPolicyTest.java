package com.pickgo.domain.post.post.service;

import com.pickgo.domain.performance.performance.entity.PerformanceType;
import com.pickgo.domain.post.post.entity.PostSortType;
import com.pickgo.domain.post.post.repository.PostRepository;
import com.pickgo.global.response.PageResponse;
import com.pickgo.domain.post.post.dto.PostSimpleResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostKeywordPolicyTest {
    @Mock
    private PostRepository postRepository;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @InjectMocks
    private PostService postService;

    @Test
    @DisplayName("검색어 앞뒤와 연속 공백을 정규화해 저장소에 전달한다")
    void normalizesKeywordWhitespace() {
        PageRequest pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "id"));
        when(postRepository.searchPosts(pageable, "라이온 킹", null, PostSortType.ID_DESC))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        PageResponse<PostSimpleResponse> result = postService.getPosts(
                1,
                10,
                "  라이온   킹  ",
                null,
                null
        );

        assertThat(result.items()).isEmpty();
        verify(postRepository).searchPosts(pageable, "라이온 킹", null, PostSortType.ID_DESC);
    }

    @Test
    @DisplayName("한 글자 검색은 빈 결과를 반환하고 저장소를 호출하지 않는다")
    void oneCharacterKeywordReturnsEmptyResult() {
        PageResponse<PostSimpleResponse> result = postService.getPosts(
                1,
                10,
                "킹",
                PerformanceType.MUSICAL,
                PostSortType.ID_DESC
        );

        assertThat(result.items()).isEmpty();
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(10);
        assertThat(result.totalPages()).isZero();
        assertThat(result.totalElements()).isZero();
        verifyNoInteractions(postRepository);
    }
}
