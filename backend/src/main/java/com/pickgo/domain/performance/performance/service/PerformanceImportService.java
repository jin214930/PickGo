package com.pickgo.domain.performance.performance.service;

import com.pickgo.domain.performance.kopis.dto.KopisPerformanceDetailResponse;
import com.pickgo.domain.performance.kopis.dto.KopisVenueDetailResponse;
import com.pickgo.domain.performance.kopis.service.KopisService;
import com.pickgo.domain.performance.performance.entity.Performance;
import com.pickgo.domain.performance.performance.repository.PerformanceRepository;
import com.pickgo.domain.performance.performance.util.PerformanceMapper;
import com.pickgo.domain.performance.venue.entity.Venue;
import com.pickgo.domain.performance.venue.repository.VenueRepository;
import com.pickgo.domain.post.post.service.PostService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PerformanceImportService {

    private final KopisService kopisService;
    private final PostService postService;
    private final PerformanceRepository performanceRepository;
    private final VenueRepository venueRepository;
    private final EntityManager entityManager;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ImportResult importPerformance(String performanceId) {
        if (performanceRepository.existsByKopisId(performanceId)) {
            log.info("Skipping KOPIS performance. Already imported. kopisId={}", performanceId);
            return ImportResult.SKIPPED_DUPLICATE;
        }

        KopisPerformanceDetailResponse performanceDetail = kopisService.fetchPerformanceDetail(performanceId);
        if (performanceDetail == null) {
            log.warn("Skipping KOPIS performance. Detail fetch failed after retries. kopisId={}", performanceId);
            return ImportResult.FAILED;
        }

        if (performanceDetail.getVenueId() == null || performanceDetail.getVenueId().isBlank()) {
            log.warn("Skipping KOPIS performance. Missing venue id. kopisId={}", performanceId);
            return ImportResult.SKIPPED_INVALID_DATA;
        }

        KopisVenueDetailResponse venueDetail = kopisService.fetchVenueDetail(performanceDetail.getVenueId());
        if (venueDetail == null) {
            log.warn(
                    "Skipping KOPIS performance. Venue fetch failed after retries. kopisId={}, venueId={}",
                    performanceId,
                    performanceDetail.getVenueId()
            );
            return ImportResult.FAILED;
        }

        Venue venue;
        try {
            venue = resolveVenue(venueDetail);
        } catch (RuntimeException e) {
            log.warn(
                    "Skipping KOPIS performance. Venue resolve failed. kopisId={}, venueId={}, reason={}",
                    performanceId,
                    performanceDetail.getVenueId(),
                    e.getMessage()
            );
            return ImportResult.FAILED;
        }

        final Performance performance;
        try {
            performance = PerformanceMapper.toPerformance(performanceId, performanceDetail, venue);
        } catch (RuntimeException e) {
            log.warn(
                    "Skipping KOPIS performance. Invalid source data. kopisId={}, reason={}",
                    performanceId,
                    e.getMessage()
            );
            return ImportResult.SKIPPED_INVALID_DATA;
        }

        try {
            Performance saved = performanceRepository.save(performance);
            postService.createPostPublished(saved);
            log.info("Imported KOPIS performance. kopisId={}, performanceId={}", performanceId, saved.getId());
            return ImportResult.IMPORTED;
        } catch (DataIntegrityViolationException e) {
            log.info("Skipping KOPIS performance. Duplicate insert detected. kopisId={}", performanceId);
            return ImportResult.SKIPPED_DUPLICATE;
        }
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

    public enum ImportResult {
        IMPORTED,
        SKIPPED_DUPLICATE,
        SKIPPED_INVALID_DATA,
        FAILED
    }
}
