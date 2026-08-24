package com.pickgo.domain.performance.performance.service;

import com.pickgo.domain.performance.kopis.dto.KopisPerformanceDetailResponse;
import com.pickgo.domain.performance.kopis.dto.KopisVenueDetailResponse;
import com.pickgo.domain.performance.kopis.service.KopisService;
import com.pickgo.domain.performance.performance.entity.Performance;
import com.pickgo.domain.performance.performance.repository.PerformanceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PerformanceImportServiceTest {

    @Mock
    private KopisService kopisService;

    @Mock
    private PerformanceRepository performanceRepository;

    @Mock
    private PerformancePersistenceService performancePersistenceService;

    @InjectMocks
    private PerformanceImportService performanceImportService;

    @Test
    @DisplayName("공연장 상세 조회에 실패하면 저장 서비스를 호출하지 않는다")
    void importPerformance_skipsPersistenceWhenVenueFetchFails() {
        String performanceId = "PF000001";
        KopisPerformanceDetailResponse performanceDetail = performanceDetail("VENUE001");

        when(performanceRepository.existsByKopisId(performanceId)).thenReturn(false);
        when(kopisService.fetchPerformanceDetail(performanceId)).thenReturn(performanceDetail);
        when(kopisService.fetchVenueDetail("VENUE001")).thenReturn(null);

        PerformanceImportService.ImportResult result = performanceImportService.importPerformance(performanceId);

        assertThat(result).isEqualTo(PerformanceImportService.ImportResult.FAILED);
        verifyNoInteractions(performancePersistenceService);
    }

    @Test
    @DisplayName("공연과 공연장 상세 조회가 성공하면 저장 서비스에 위임한다")
    void importPerformance_delegatesPersistenceAfterExternalFetches() {
        String performanceId = "PF000001";
        KopisPerformanceDetailResponse performanceDetail = performanceDetail("VENUE001");
        KopisVenueDetailResponse venueDetail = new KopisVenueDetailResponse("테스트 공연장", "서울시 강남구");
        Performance saved = mock(Performance.class);

        when(performanceRepository.existsByKopisId(performanceId)).thenReturn(false);
        when(kopisService.fetchPerformanceDetail(performanceId)).thenReturn(performanceDetail);
        when(kopisService.fetchVenueDetail("VENUE001")).thenReturn(venueDetail);
        when(performancePersistenceService.save(performanceId, performanceDetail, venueDetail)).thenReturn(saved);

        PerformanceImportService.ImportResult result = performanceImportService.importPerformance(performanceId);

        assertThat(result).isEqualTo(PerformanceImportService.ImportResult.IMPORTED);
        verify(performancePersistenceService).save(performanceId, performanceDetail, venueDetail);
    }

    private KopisPerformanceDetailResponse performanceDetail(String venueId) {
        KopisPerformanceDetailResponse response = new KopisPerformanceDetailResponse();
        response.setVenueId(venueId);
        return response;
    }
}
