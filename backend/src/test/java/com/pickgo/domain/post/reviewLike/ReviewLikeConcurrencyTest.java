package com.pickgo.domain.post.reviewLike;

import com.pickgo.domain.member.member.entity.Member;
import com.pickgo.domain.member.member.entity.enums.Authority;
import com.pickgo.domain.member.member.entity.enums.SocialProvider;
import com.pickgo.domain.member.member.repository.MemberRepository;
import com.pickgo.domain.performance.performance.entity.Performance;
import com.pickgo.domain.performance.performance.entity.PerformanceState;
import com.pickgo.domain.performance.performance.entity.PerformanceType;
import com.pickgo.domain.performance.performance.repository.PerformanceRepository;
import com.pickgo.domain.performance.venue.entity.Venue;
import com.pickgo.domain.performance.venue.repository.VenueRepository;
import com.pickgo.domain.post.post.entity.Post;
import com.pickgo.domain.post.post.repository.PostRepository;
import com.pickgo.domain.post.review.entity.Review;
import com.pickgo.domain.post.review.repository.PostReviewRepository;
import com.pickgo.domain.post.review.repository.ReviewLikeRepository;
import com.pickgo.domain.post.review.service.PostReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test") // test용 profile 설정
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ReviewLikeConcurrencyTest {

    @Autowired
    private PostReviewService postReviewService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PostReviewRepository postReviewRepository;

    @Autowired
    private ReviewLikeRepository reviewLikeRepository;

    @Autowired
    private PerformanceRepository performanceRepository;

    @Autowired
    private VenueRepository venueRepository;

    private UUID memberId;
    private Long postId;
    private Long reviewId;

    @BeforeEach
    void setUp() {
        // 1. post용 Member 생성
        memberId = UUID.randomUUID();
        Member member = Member.builder()
                .id(memberId)
                .email("test@example.com")
                .password("1234")
                .nickname("tester")
                .authority(Authority.USER)
                .socialProvider(SocialProvider.KAKAO)
                .build();
        memberRepository.save(member);

        // 2. 테스트용 Venue 생성
        Venue venue = Venue.builder()
                .name("테스트 공연장")
                .address("서울시 강남구")
                .build();
        venueRepository.save(venue);

        // 3. Performance 생성
        Performance performance = Performance.builder()
                .kopisId("TEST-PERF-" + UUID.randomUUID())
                .name("테스트 공연")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(7))
                .runtime("120분")
                .poster("https://example.com/poster.jpg")
                .state(PerformanceState.ONGOING)
                .minAge("12세 이상")
                .casts("배우 A, 배우 B")
                .type(PerformanceType.MUSICAL)
                .venue(venue)
                .build();
        performanceRepository.save(performance);

        // 4. Post 생성
        Post post = Post.builder()
                .title("테스트 게시글")
                .content("공연 내용입니다")
                .performance(performance)
                .isPublished(true)
                .views(0L)
                .build();
        postRepository.save(post);
        postId = post.getId();

        // 5. Review 생성
        Member reviewAuthor = memberRepository.findById(memberId).orElseThrow();
        Review review = Review.builder()
                .post(post)
                .member(reviewAuthor)
                .content("리뷰 내용입니다")
                .build();
        postReviewRepository.save(review);
        reviewId = review.getId();
    }

    @Test
    void testConcurrentLikeAndUnlikeRequests() throws InterruptedException {
        int threadCount = 50;
        int half = threadCount / 2;

        // 1. 50명 사용자 생성 및 저장
        List<Member> members = IntStream.range(0, threadCount)
                .mapToObj(i -> {
                    UUID uid = UUID.randomUUID();
                    Member m = Member.builder()
                            .id(uid)
                            .email("user" + i + "@test.com")
                            .password("pw")
                            .nickname("user" + i)
                            .authority(Authority.USER)
                            .socialProvider(SocialProvider.KAKAO)
                            .build();
                    return memberRepository.save(m);
                })
                .toList();

        // 2. 절반은 미리 좋아요 추가 (초기 좋아요 수 = 25)
        for (int i = 0; i < half; i++) {
            postReviewService.likeReview(postId, reviewId, members.get(i).getId());
        }
        AtomicInteger expectedLikes = new AtomicInteger(25); // 초기 좋아요 수

        // 3. 동시 요청: 절반은 좋아요 취소, 절반은 좋아요 요청
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            int index = i;
            executorService.submit(() -> {
                try {
                    if (index < half) {
                        postReviewService.cancelLikeReview(postId, reviewId, members.get(index).getId());
                        expectedLikes.getAndDecrement(); // 좋아요 취소 시 감소
                    } else {
                        postReviewService.likeReview(postId, reviewId, members.get(index).getId());
                        expectedLikes.getAndIncrement(); // 좋아요 추가 시 증가
                    }
                } catch (Exception e) {
                    System.out.println("예외: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        // 4. 검증
        int expectedLikeCount = expectedLikes.get();
//        int likeCount = postReviewRepository.findById(reviewId).get().getLikeCount();
        int likeCount = reviewLikeRepository.countReviewLikeByReview_Id(reviewId);

        System.out.println("💬 DB에 저장된 ReviewLike 수: " + likeCount);
        System.out.println("💬 테스트 Review.likeCount 값: " + expectedLikeCount);

        assertThat(likeCount).isEqualTo(expectedLikeCount);
    }
}
