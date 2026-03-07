package com.pickgo.domain.performance.performance.service;

import com.pickgo.domain.performance.kopis.service.KopisService;
import com.pickgo.domain.performance.performance.repository.PerformanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
public class PerformanceService {
    private static final int INITIAL_SEED_SIZE = 100;
    private static final int IMPORT_THREAD_COUNT = 5;

    private final KopisService kopisService;
    private final PerformanceImportService performanceImportService;
    private final PerformanceRepository performanceRepository;

    public void fetchAndSavePerformances() {
        List<String> performanceIds = kopisService.fetchPerformanceIds(1, INITIAL_SEED_SIZE);
        List<Future<?>> futures = new ArrayList<>();
        AtomicInteger importedCount = new AtomicInteger();
        AtomicInteger duplicateCount = new AtomicInteger();
        AtomicInteger invalidDataCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();
        long startedAt = System.nanoTime();

        log.info("Starting KOPIS initial seed. requestedCount={}, threadCount={}", performanceIds.size(), IMPORT_THREAD_COUNT);

        ExecutorService executorService = Executors.newFixedThreadPool(IMPORT_THREAD_COUNT);
        try {
            for (String performanceId : performanceIds) {
                futures.add(executorService.submit(() -> {
                    PerformanceImportService.ImportResult result = performanceImportService.importPerformance(performanceId);
                    switch (result) {
                        case IMPORTED -> importedCount.incrementAndGet();
                        case SKIPPED_DUPLICATE -> duplicateCount.incrementAndGet();
                        case SKIPPED_INVALID_DATA -> invalidDataCount.incrementAndGet();
                        case FAILED -> failureCount.incrementAndGet();
                    }
                }));
            }

            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failureCount.incrementAndGet();
                    log.warn("KOPIS initial seed interrupted", e);
                } catch (ExecutionException e) {
                    failureCount.incrementAndGet();
                    log.error("KOPIS initial seed worker failed", e.getCause());
                }
            }
        } finally {
            executorService.shutdown();
        }

        long durationMillis = (System.nanoTime() - startedAt) / 1_000_000;
        log.info(
                "Completed KOPIS initial seed. requestedCount={}, imported={}, duplicates={}, invalidData={}, failures={}, durationMs={}, threadCount={}",
                performanceIds.size(),
                importedCount.get(),
                duplicateCount.get(),
                invalidDataCount.get(),
                failureCount.get(),
                durationMillis,
                IMPORT_THREAD_COUNT
        );
    }

    @Transactional
    public void updatePerformanceState() {
        LocalDate today = LocalDate.now();

        performanceRepository.UpdateToCompleted(today);
        performanceRepository.UpdateToOngoing(today);
    }
}
