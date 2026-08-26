package com.pickgo.domain.post.post.service;

import com.pickgo.domain.performance.kopis.dto.KopisPerformanceDetailResponse;
import com.pickgo.domain.performance.kopis.dto.KopisVenueDetailResponse;
import com.pickgo.domain.performance.performance.entity.Performance;
import com.pickgo.domain.performance.performance.entity.PerformanceState;
import com.pickgo.domain.performance.performance.entity.PerformanceType;
import com.pickgo.domain.performance.performance.repository.PerformanceRepository;
import com.pickgo.domain.performance.performance.service.PerformancePersistenceService;
import com.pickgo.domain.performance.performance.service.PerformanceService;
import com.pickgo.domain.performance.venue.entity.Venue;
import com.pickgo.domain.performance.venue.repository.VenueRepository;
import com.pickgo.domain.post.post.dto.PostSimpleResponse;
import com.pickgo.domain.post.post.entity.Post;
import com.pickgo.domain.post.post.repository.PostRepository;
import com.pickgo.global.response.PageResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PostCachePolicyTest {
    @Autowired
    private PostService postService;

    @Autowired
    private PerformanceService performanceService;

    @Autowired
    private PerformancePersistenceService performancePersistenceService;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PerformanceRepository performanceRepository;

    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    @AfterEach
    void clearCaches() {
        cache("posts").clear();
        cache("popularPosts").clear();
        cache("openingSoonPosts").clear();
        cache("post").clear();
    }

    @Test
    @DisplayName("기본 목록 조회는 캐시되고 캐시된 결과를 재사용한다")
    void defaultListUsesCache() {
        savePost("Cached Show", true, PerformanceType.MUSICAL);

        PageResponse<PostSimpleResponse> first = postService.getPosts(1, 10, null, null, null);
        postRepository.deleteAll();
        postRepository.flush();

        PageResponse<PostSimpleResponse> second = postService.getPosts(1, 10, null, null, null);

        assertThat(first.items()).hasSize(1);
        assertThat(second.items()).containsExactlyElementsOf(first.items());
        assertThat(cache("posts").get("default")).isNotNull();
    }

    @Test
    @DisplayName("필터 조건이 있는 목록 조회는 기본 목록 캐시를 사용하지 않는다")
    void filteredListBypassesDefaultCache() {
        savePost("Cached Show", true, PerformanceType.MUSICAL);

        postService.getPosts(1, 10, null, PerformanceType.MUSICAL, null);

        assertThat(cache("posts").get("default")).isNull();
    }

    @Test
    @DisplayName("공연 저장 성공 시 관련 목록 캐시를 모두 무효화한다")
    void performanceSaveEvictsPostListCaches() {
        cache("posts").put("default", "cached");
        cache("popularPosts").put("cached", "cached");
        cache("openingSoonPosts").put("cached", "cached");

        performancePersistenceService.save(
                "KOPIS-" + UUID.randomUUID(),
                performanceDetail(),
                new KopisVenueDetailResponse("테스트 공연장", "서울시 강남구")
        );

        assertThat(cache("posts").get("default")).isNull();
        assertThat(cache("popularPosts").get("cached")).isNull();
        assertThat(cache("openingSoonPosts").get("cached")).isNull();
    }

    @Test
    @DisplayName("공연 상태 갱신 시 오픈 임박 목록 캐시를 무효화한다")
    void performanceStateUpdateEvictsOpeningSoonCache() {
        cache("openingSoonPosts").put("cached", "cached");

        performanceService.updatePerformanceState();

        assertThat(cache("openingSoonPosts").get("cached")).isNull();
    }

    private Cache cache(String name) {
        Cache cache = cacheManager.getCache(name);
        assertThat(cache).as("cache %s", name).isNotNull();
        return cache;
    }

    private void savePost(String title, boolean published, PerformanceType type) {
        String suffix = UUID.randomUUID().toString();
        Venue venue = venueRepository.saveAndFlush(
                Venue.builder()
                        .name("Venue-" + suffix)
                        .address("Address-" + suffix)
                        .build()
        );

        Performance performance = performanceRepository.saveAndFlush(
                Performance.builder()
                        .kopisId("KOPIS-" + suffix)
                        .name(title)
                        .startDate(LocalDate.of(2026, 9, 1))
                        .endDate(LocalDate.of(2026, 9, 10))
                        .runtime("120 minutes")
                        .poster("poster.jpg")
                        .state(PerformanceState.SCHEDULED)
                        .minAge("All")
                        .casts("Cast")
                        .type(type)
                        .venue(venue)
                        .build()
        );

        postRepository.saveAndFlush(
                Post.builder()
                        .title(title)
                        .content("content")
                        .isPublished(published)
                        .views(0L)
                        .performance(performance)
                        .build()
        );
    }

    private KopisPerformanceDetailResponse performanceDetail() {
        KopisPerformanceDetailResponse detail = new KopisPerformanceDetailResponse();
        detail.setName("Imported Show");
        detail.setStartDate("2026.09.01");
        detail.setEndDate("2026.09.10");
        detail.setRuntime("120 minutes");
        detail.setPoster("poster.jpg");
        detail.setState("공연예정");
        detail.setMinAge("All");
        detail.setCasts("Cast");
        detail.setType("뮤지컬");
        detail.setSchedule("월요일(19:00)");
        detail.setIntroImages(List.of());
        return detail;
    }
}
