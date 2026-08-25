package com.pickgo.domain.post.post.repository;

import com.pickgo.domain.performance.performance.entity.Performance;
import com.pickgo.domain.performance.performance.entity.PerformanceState;
import com.pickgo.domain.performance.performance.entity.PerformanceType;
import com.pickgo.domain.performance.performance.repository.PerformanceRepository;
import com.pickgo.domain.performance.venue.entity.Venue;
import com.pickgo.domain.performance.venue.repository.VenueRepository;
import com.pickgo.domain.post.post.entity.Post;
import com.pickgo.domain.post.post.entity.PostSortType;
import com.pickgo.global.config.JpaConfig;
import com.pickgo.global.config.QuerydslConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QuerydslConfig.class, JpaConfig.class})
@ActiveProfiles("test")
class PostQueryRepositoryTest {
    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PerformanceRepository performanceRepository;

    @Autowired
    private VenueRepository venueRepository;

    @Test
    @DisplayName("게시글 검색은 공개 여부, 검색어, 공연 타입과 페이징을 적용한다")
    void searchPosts_appliesConditionsAndPagination() {
        savePost("Musical Show A", true, PerformanceType.MUSICAL, PerformanceState.SCHEDULED,
                LocalDate.of(2026, 9, 1), 10L);
        savePost("Musical Show B", true, PerformanceType.MUSICAL, PerformanceState.SCHEDULED,
                LocalDate.of(2026, 9, 2), 20L);
        savePost("Musical Show C", true, PerformanceType.MUSICAL, PerformanceState.SCHEDULED,
                LocalDate.of(2026, 9, 3), 30L);
        savePost("Musical Show Hidden", false, PerformanceType.MUSICAL, PerformanceState.SCHEDULED,
                LocalDate.of(2026, 9, 4), 40L);
        savePost("Musical Show Play", true, PerformanceType.PLAY, PerformanceState.SCHEDULED,
                LocalDate.of(2026, 9, 5), 50L);

        Page<Post> result = postRepository.searchPosts(
                PageRequest.of(0, 2, PostSortType.ID_DESC.getSort()),
                "musicalshow",
                PerformanceType.MUSICAL,
                PostSortType.ID_DESC
        );

        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent())
                .allMatch(post -> post.getIsPublished()
                        && post.getPerformance().getType() == PerformanceType.MUSICAL);
    }

    @Test
    @DisplayName("공연 타입 필터가 없으면 공개 게시글 기준으로 전체 개수를 계산한다")
    void searchPosts_countsPublishedPostsWithoutTypeFilter() {
        savePost("Published Show", true, PerformanceType.MUSICAL, PerformanceState.SCHEDULED,
                LocalDate.of(2026, 9, 1), 10L);
        savePost("Hidden Show", false, PerformanceType.MUSICAL, PerformanceState.SCHEDULED,
                LocalDate.of(2026, 9, 2), 20L);

        Page<Post> result = postRepository.searchPosts(
                PageRequest.of(0, 10, PostSortType.ID_DESC.getSort()),
                "",
                null,
                PostSortType.ID_DESC
        );

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("공연장은 필수 값이다")
    void performance_requiresVenue() {
        String suffix = UUID.randomUUID().toString();
        Performance performance = Performance.builder()
                .kopisId("KOPIS-" + suffix)
                .name("Performance")
                .startDate(LocalDate.of(2026, 9, 1))
                .endDate(LocalDate.of(2026, 9, 10))
                .runtime("120 minutes")
                .poster("poster.jpg")
                .state(PerformanceState.SCHEDULED)
                .minAge("All")
                .casts("Cast")
                .type(PerformanceType.MUSICAL)
                .venue(null)
                .build();

        assertThatThrownBy(() -> performanceRepository.saveAndFlush(performance))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("조회수가 같으면 게시글 ID 내림차순으로 정렬한다")
    void searchPosts_usesPostIdAsTieBreakerForViewSort() {
        Post first = savePost("Tie Show A", true, PerformanceType.MUSICAL, PerformanceState.SCHEDULED,
                LocalDate.of(2026, 9, 1), 100L);
        Post second = savePost("Tie Show B", true, PerformanceType.MUSICAL, PerformanceState.SCHEDULED,
                LocalDate.of(2026, 9, 2), 100L);

        Page<Post> result = postRepository.searchPosts(
                PageRequest.of(0, 10, PostSortType.VIEW_DESC.getSort()),
                "tie",
                null,
                PostSortType.VIEW_DESC
        );

        assertThat(result.getContent())
                .extracting(Post::getId)
                .containsExactly(second.getId(), first.getId());
    }

    @Test
    @DisplayName("인기 게시글은 공개된 게시글 중 타입과 개수 제한을 적용한다")
    void findPopularPosts_appliesTypeAndLimit() {
        Post popularMusical = savePost("Popular Musical", true, PerformanceType.MUSICAL,
                PerformanceState.SCHEDULED, LocalDate.of(2026, 9, 1), 100L);
        savePost("Less Popular Musical", true, PerformanceType.MUSICAL,
                PerformanceState.SCHEDULED, LocalDate.of(2026, 9, 2), 50L);
        savePost("Popular Play", true, PerformanceType.PLAY,
                PerformanceState.SCHEDULED, LocalDate.of(2026, 9, 3), 200L);
        savePost("Hidden Musical", false, PerformanceType.MUSICAL,
                PerformanceState.SCHEDULED, LocalDate.of(2026, 9, 4), 300L);

        var result = postRepository.findPopularPosts(1, PerformanceType.MUSICAL);

        assertThat(result).extracting(Post::getId).containsExactly(popularMusical.getId());
    }

    @Test
    @DisplayName("오픈 예정 게시글은 예정 상태만 시작일 순서로 조회한다")
    void findOpeningSoonPosts_filtersStateAndOrdersByStartDate() {
        Post later = savePost("Later Show", true, PerformanceType.MUSICAL,
                PerformanceState.SCHEDULED, LocalDate.of(2026, 9, 10), 0L);
        Post sooner = savePost("Sooner Show", true, PerformanceType.MUSICAL,
                PerformanceState.SCHEDULED, LocalDate.of(2026, 9, 5), 0L);
        savePost("Ongoing Show", true, PerformanceType.MUSICAL,
                PerformanceState.ONGOING, LocalDate.of(2026, 9, 1), 0L);

        var result = postRepository.findOpeningSoonPosts();

        assertThat(result)
                .extracting(Post::getId)
                .containsExactly(sooner.getId(), later.getId());
    }

    private Post savePost(
            String title,
            boolean published,
            PerformanceType type,
            PerformanceState state,
            LocalDate startDate,
            long views
    ) {
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
                        .startDate(startDate)
                        .endDate(startDate.plusDays(10))
                        .runtime("120 minutes")
                        .poster("poster.jpg")
                        .state(state)
                        .minAge("All")
                        .casts("Cast")
                        .type(type)
                        .venue(venue)
                        .build()
        );

        return postRepository.saveAndFlush(
                Post.builder()
                        .title(title)
                        .content("content")
                        .isPublished(published)
                        .views(views)
                        .performance(performance)
                        .build()
        );
    }
}
