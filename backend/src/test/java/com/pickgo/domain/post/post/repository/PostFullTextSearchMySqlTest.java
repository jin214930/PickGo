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
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QuerydslConfig.class, JpaConfig.class})
@ActiveProfiles("fulltext-test")
@EnabledIfSystemProperty(named = "fulltext.test.enabled", matches = "true")
class PostFullTextSearchMySqlTest {
    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PerformanceRepository performanceRepository;

    @Autowired
    private VenueRepository venueRepository;

    @Test
    @DisplayName("MySQL ngram FULLTEXT 인덱스로 제목의 부분 검색을 수행한다")
    void searchPosts_usesNgramFullTextSearch() {
        Post lionKing = savePost("테스트라이온 킹", true);
        Post lionConcert = savePost("테스트라이온 콘서트", true);
        savePost("테스트라이온 킹 비공개", false);

        Page<Post> result = postRepository.searchPosts(
                PageRequest.of(0, 10, PostSortType.ID_DESC.getSort()),
                "테스트라이오",
                null,
                PostSortType.ID_DESC
        );

        assertThat(result.getContent())
                .extracting(Post::getId)
                .containsExactly(lionConcert.getId(), lionKing.getId());
        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("MySQL ngram FULLTEXT 인덱스로 공백이 있는 제목을 검색한다")
    void searchPosts_matchesWhitespaceSeparatedTitle() {
        Post post = savePost("테스트 오페라의 유령", true);

        Page<Post> result = postRepository.searchPosts(
                PageRequest.of(0, 10, PostSortType.ID_DESC.getSort()),
                "테스트 오페라",
                null,
                PostSortType.ID_DESC
        );

        assertThat(result.getContent())
                .extracting(Post::getId)
                .contains(post.getId());
    }

    private Post savePost(String title, boolean published) {
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
                        .type(PerformanceType.MUSICAL)
                        .venue(venue)
                        .build()
        );

        return postRepository.saveAndFlush(
                Post.builder()
                        .title(title)
                        .content("content")
                        .isPublished(published)
                        .views(0L)
                        .performance(performance)
                        .build()
        );
    }
}
