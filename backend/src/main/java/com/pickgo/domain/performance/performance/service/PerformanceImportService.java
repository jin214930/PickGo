package com.pickgo.domain.performance.performance.service;

import com.pickgo.domain.performance.kopis.dto.KopisPerformanceDetailResponse;
import com.pickgo.domain.performance.kopis.dto.KopisVenueDetailResponse;
import com.pickgo.domain.performance.kopis.service.KopisService;
import com.pickgo.domain.performance.performance.entity.Performance;
import com.pickgo.domain.performance.performance.repository.PerformanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PerformanceImportService {

    private final KopisService kopisService;
    private final PerformanceRepository performanceRepository;
    private final PerformancePersistenceService performancePersistenceService;

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

        try {
            Performance saved = performancePersistenceService.save(
                    performanceId,
                    performanceDetail,
                    venueDetail
            );
            log.info("Imported KOPIS performance. kopisId={}, performanceId={}", performanceId, saved.getId());
            return ImportResult.IMPORTED;
        } catch (InvalidPerformanceDataException e) {
            log.warn(
                    "Skipping KOPIS performance. Invalid source data. kopisId={}, reason={}",
                    performanceId,
                    e.getMessage()
            );
            return ImportResult.SKIPPED_INVALID_DATA;
        } catch (DataIntegrityViolationException e) {
            log.info("Skipping KOPIS performance. Duplicate insert detected. kopisId={}", performanceId);
            return ImportResult.SKIPPED_DUPLICATE;
        } catch (RuntimeException e) {
            log.warn(
                    "Skipping KOPIS performance. Persistence failed. kopisId={}, venueId={}, reason={}",
                    performanceId,
                    performanceDetail.getVenueId(),
                    e.getMessage()
            );
            return ImportResult.FAILED;
        }
    }

    public enum ImportResult {
        IMPORTED,
        SKIPPED_DUPLICATE,
        SKIPPED_INVALID_DATA,
        FAILED
    }
}
