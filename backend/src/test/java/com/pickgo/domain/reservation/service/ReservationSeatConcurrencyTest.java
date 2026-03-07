package com.pickgo.domain.reservation.service;

import com.pickgo.domain.member.member.entity.Member;
import com.pickgo.domain.member.member.entity.enums.Authority;
import com.pickgo.domain.member.member.entity.enums.SocialProvider;
import com.pickgo.domain.member.member.repository.MemberRepository;
import com.pickgo.domain.performance.area.area.entity.AreaGrade;
import com.pickgo.domain.performance.area.area.entity.AreaName;
import com.pickgo.domain.performance.area.area.entity.PerformanceArea;
import com.pickgo.domain.performance.area.area.repository.PerformanceAreaRepository;
import com.pickgo.domain.performance.area.seat.entity.ReservedSeat;
import com.pickgo.domain.performance.area.seat.repository.ReservedSeatRepository;
import com.pickgo.domain.performance.performance.entity.Performance;
import com.pickgo.domain.performance.performance.entity.PerformanceSession;
import com.pickgo.domain.performance.performance.entity.PerformanceState;
import com.pickgo.domain.performance.performance.entity.PerformanceType;
import com.pickgo.domain.performance.performance.repository.PerformanceRepository;
import com.pickgo.domain.performance.performance.repository.PerformanceSessionRepository;
import com.pickgo.domain.performance.venue.entity.Venue;
import com.pickgo.domain.performance.venue.repository.VenueRepository;
import com.pickgo.domain.reservation.dto.request.ReservationCreateRequest;
import com.pickgo.global.exception.BusinessException;
import com.pickgo.global.response.RsCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ReservationSeatConcurrencyTest {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private PerformanceRepository performanceRepository;

    @Autowired
    private PerformanceSessionRepository performanceSessionRepository;

    @Autowired
    private PerformanceAreaRepository performanceAreaRepository;

    @Autowired
    private ReservedSeatRepository reservedSeatRepository;

    private PerformanceSession session;
    private PerformanceArea area;

    @BeforeEach
    void setUp() {
        Venue venue = venueRepository.save(Venue.builder()
                .name("Benchmark Venue " + UUID.randomUUID())
                .address("Benchmark Address")
                .build());

        Performance performance = performanceRepository.save(Performance.builder()
                .kopisId("TEST-" + UUID.randomUUID())
                .name("Concurrency Performance")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(1))
                .runtime("120min")
                .poster("test.jpg")
                .state(PerformanceState.SCHEDULED)
                .minAge("8+")
                .casts("Cast")
                .type(PerformanceType.MUSICAL)
                .venue(venue)
                .build());

        this.session = performanceSessionRepository.save(PerformanceSession.builder()
                .performance(performance)
                .performanceTime(LocalDateTime.now().plusDays(1))
                .reserveOpenAt(LocalDateTime.now().minusDays(1))
                .build());

        this.area = performanceAreaRepository.save(PerformanceArea.builder()
                .performance(performance)
                .name(AreaName.A)
                .grade(AreaGrade.ROYAL)
                .price(10000)
                .rowCount(15)
                .colCount(10)
                .build());
    }

    @Test
    void onlyOneReservationSucceedsForSameSeat() throws InterruptedException {
        int threadCount = 50;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();
        AtomicInteger unexpectedFailureCount = new AtomicInteger();

        List<Member> members = IntStream.range(0, threadCount)
                .mapToObj(i -> memberRepository.save(Member.builder()
                        .id(UUID.randomUUID())
                        .email("seat-user-" + i + "@test.com")
                        .password("pw")
                        .nickname("seat-user-" + i)
                        .authority(Authority.USER)
                        .socialProvider(SocialProvider.NONE)
                        .build()))
                .toList();

        for (Member member : members) {
            executorService.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await();

                    ReservationCreateRequest request = new ReservationCreateRequest(
                            session.getId(),
                            List.of(new ReservationCreateRequest.SeatRequest(area.getId(), 1, 1))
                    );

                    reservationService.createReservation(member.getId(), request);
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    if (e.getRsCode() == RsCode.SEAT_CONFLICT) {
                        conflictCount.incrementAndGet();
                    } else {
                        unexpectedFailureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    unexpectedFailureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await();
        executorService.shutdown();

        List<ReservedSeat> reservedSeats = reservedSeatRepository.findByPerformanceAreaAndPerformanceSession(area, session);

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(threadCount - 1);
        assertThat(unexpectedFailureCount.get()).isZero();
        assertThat(reservedSeats).hasSize(1);
        assertThat(reservedSeats.getFirst().getRow()).isEqualTo("A");
        assertThat(reservedSeats.getFirst().getNumber()).isEqualTo(1);
    }
}