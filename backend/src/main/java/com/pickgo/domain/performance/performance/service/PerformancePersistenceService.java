package com.pickgo.domain.performance.performance.service;

import com.pickgo.domain.performance.kopis.dto.KopisPerformanceDetailResponse;
import com.pickgo.domain.performance.kopis.dto.KopisVenueDetailResponse;
import com.pickgo.domain.performance.performance.entity.Performance;
import com.pickgo.domain.performance.performance.repository.PerformanceRepository;
import com.pickgo.domain.performance.performance.util.PerformanceMapper;
import com.pickgo.domain.performance.venue.entity.Venue;
import com.pickgo.domain.performance.venue.repository.VenueRepository;
import com.pickgo.domain.post.post.service.PostService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PerformancePersistenceService {

    private final PerformanceRepository performanceRepository;
    private final VenueRepository venueRepository;
    private final PostService postService;
    private final EntityManager entityManager;

    @Transactional
    public Performance save(
            String performanceId,
            KopisPerformanceDetailResponse performanceDetail,
            KopisVenueDetailResponse venueDetail
    ) {
        Venue venue = resolveVenue(venueDetail);

        final Performance performance;
        try {
            performance = PerformanceMapper.toPerformance(performanceId, performanceDetail, venue);
        } catch (RuntimeException e) {
            throw new InvalidPerformanceDataException(
                    "Invalid KOPIS performance data: " + e.getMessage(),
                    e
            );
        }

        Performance saved = performanceRepository.save(performance);
        postService.createPostPublished(saved);
        entityManager.flush();
        return saved;
    }

    private Venue resolveVenue(KopisVenueDetailResponse venueDetail) {
        String venueName = venueDetail.name().trim();
        String venueAddress = venueDetail.address().trim();

        return venueRepository.findByNameAndAddress(venueName, venueAddress)
                .orElseGet(() -> createVenue(venueName, venueAddress));
    }

    private Venue createVenue(String venueName, String venueAddress) {
        try {
            return venueRepository.saveAndFlush(
                    Venue.builder()
                            .name(venueName)
                            .address(venueAddress)
                            .build()
            );
        } catch (DataIntegrityViolationException e) {
            entityManager.clear();
            return venueRepository.findByNameAndAddress(venueName, venueAddress)
                    .orElseThrow(() -> new IllegalStateException("Duplicate venue lookup failed after save conflict", e));
        }
    }
}
