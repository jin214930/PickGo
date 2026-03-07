package com.pickgo.domain.performance.performance.util;

import com.pickgo.domain.performance.area.area.entity.AreaGrade;
import com.pickgo.domain.performance.area.area.entity.AreaName;
import com.pickgo.domain.performance.area.area.entity.PerformanceArea;
import com.pickgo.domain.performance.kopis.dto.KopisPerformanceDetailResponse;
import com.pickgo.domain.performance.performance.entity.Performance;
import com.pickgo.domain.performance.performance.entity.PerformanceIntro;
import com.pickgo.domain.performance.performance.entity.PerformanceSession;
import com.pickgo.domain.performance.performance.entity.PerformanceState;
import com.pickgo.domain.performance.performance.entity.PerformanceType;
import com.pickgo.domain.performance.venue.entity.Venue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PerformanceMapper {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    // 공연 생성
    public static Performance toPerformance(String kopisId, KopisPerformanceDetailResponse response, Venue venue) {
        Performance performance = Performance.builder()
                .kopisId(kopisId)
                .name(response.getName())
                .startDate(LocalDate.parse(response.getStartDate(), DATE_TIME_FORMATTER))
                .endDate(LocalDate.parse(response.getEndDate(), DATE_TIME_FORMATTER))
                .runtime(response.getRuntime())
                .poster(response.getPoster())
                .state(convertState(response.getState()))
                .minAge(response.getMinAge())
                .casts(response.getCasts())
                .type(convertType(response.getType()))
                .venue(venue)
                .performanceIntros(toPerformanceIntros(response.getIntroImages()))
                .build();

        for (PerformanceIntro intro : performance.getPerformanceIntros()) {
            intro.setPerformance(performance);
        }

        List<PerformanceArea> areas = createPerformanceAreas(performance);
        performance.setPerformanceAreas(areas);

        List<PerformanceSession> sessions = toPerformanceSession(response.getSchedule(), performance);
        performance.setPerformanceSessions(sessions);

        return performance;
    }

    private static PerformanceState convertState(String state) {
        return switch (state) {
            case "공연중" -> PerformanceState.ONGOING;
            case "공연완료" -> PerformanceState.COMPLETED;
            default -> PerformanceState.SCHEDULED;
        };
    }

    private static PerformanceType convertType(String type) {
        return switch (type) {
            case "연극" -> PerformanceType.PLAY;
            case "무용" -> PerformanceType.DANCE;
            case "한국음악(국악)" -> PerformanceType.KOREAN;
            case "대중음악" -> PerformanceType.CONCERT;
            case "서양음악(클래식)" -> PerformanceType.CLASSIC;
            case "뮤지컬" -> PerformanceType.MUSICAL;
            default -> PerformanceType.ETC;
        };
    }

    private static List<PerformanceIntro> toPerformanceIntros(List<String> introDtos) {
        return introDtos.stream()
                .map(introDto -> PerformanceIntro.builder()
                        .introImage(introDto)
                        .build())
                .toList();
    }

    private static List<PerformanceSession> toPerformanceSession(String schedule, Performance performance) {
        Map<DayOfWeek, List<LocalTime>> scheduleMap = parseSchedule(schedule);

        List<PerformanceSession> sessions = new ArrayList<>();
        LocalDate date = performance.getStartDate();
        LocalDate endDate = performance.getEndDate();

        while (!date.isAfter(endDate)) {
            DayOfWeek dayOfWeek = date.getDayOfWeek();
            List<LocalTime> times = scheduleMap.get(dayOfWeek);
            if (times != null) {
                for (LocalTime time : times) {
                    LocalDateTime performanceTime = LocalDateTime.of(date, time);
                    LocalDateTime reserveOpenAt = performanceTime.minusDays(30);

                    sessions.add(PerformanceSession.builder()
                            .performance(performance)
                            .performanceTime(performanceTime)
                            .reserveOpenAt(reserveOpenAt)
                            .build());
                }
            }
            date = date.plusDays(1);
        }

        return sessions;
    }

    private static Map<DayOfWeek, List<LocalTime>> parseSchedule(String schedule) {
        if (schedule == null || schedule.isBlank()) {
            throw new IllegalArgumentException("Schedule is empty");
        }

        Map<DayOfWeek, List<LocalTime>> map = new HashMap<>();
        String normalized = schedule.replace(" ", "").replace("\n", "");
        String[] parts = normalized.split("\\),");

        for (String part : parts) {
            String normalizedPart = part.endsWith(")") ? part : part + ")";
            String[] split = normalizedPart.split("\\(");
            if (split.length != 2) {
                throw new IllegalArgumentException("Invalid schedule format: " + schedule);
            }

            String daysPart = split[0];
            String timesPart = split[1].replace(")", "");

            List<LocalTime> times = Arrays.stream(timesPart.split(","))
                    .map(PerformanceMapper::parseTime)
                    .toList();

            List<DayOfWeek> days = parseDays(daysPart);
            for (DayOfWeek day : days) {
                map.put(day, times);
            }
        }

        if (map.isEmpty()) {
            throw new IllegalArgumentException("No sessions parsed from schedule: " + schedule);
        }

        return map;
    }

    private static LocalTime parseTime(String timeString) {
        String[] parts = timeString.split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);
        return LocalTime.of(hour, minute);
    }

    private static List<DayOfWeek> parseDays(String daysPart) {
        List<DayOfWeek> days = new ArrayList<>();

        if (daysPart.contains("~")) {
            String[] range = daysPart.split("~");
            DayOfWeek start = koreanDayOfWeek(range[0]);
            DayOfWeek end = koreanDayOfWeek(range[1]);

            int startOrdinal = start.getValue();
            int endOrdinal = end.getValue();
            for (int i = startOrdinal; i <= endOrdinal; i++) {
                days.add(DayOfWeek.of(i));
            }
        } else {
            DayOfWeek dayOfWeek = koreanDayOfWeek(daysPart);
            if (dayOfWeek != null) {
                days.add(dayOfWeek);
            }
        }

        return days;
    }

    private static DayOfWeek koreanDayOfWeek(String korean) {
        return switch (korean) {
            case "월요일" -> DayOfWeek.MONDAY;
            case "화요일" -> DayOfWeek.TUESDAY;
            case "수요일" -> DayOfWeek.WEDNESDAY;
            case "목요일" -> DayOfWeek.THURSDAY;
            case "금요일" -> DayOfWeek.FRIDAY;
            case "토요일" -> DayOfWeek.SATURDAY;
            case "일요일" -> DayOfWeek.SUNDAY;
            case "HOL" -> null;
            default -> throw new IllegalArgumentException("Unknown day: " + korean);
        };
    }

    private static class AreaConfig {
        AreaName areaName;
        AreaGrade areaGrade;
        int rowCount;
        int colCount;
        int price;

        AreaConfig(AreaName areaName, AreaGrade areaGrade, int rowCount, int colCount, int price) {
            this.areaName = areaName;
            this.areaGrade = areaGrade;
            this.rowCount = rowCount;
            this.colCount = colCount;
            this.price = price;
        }
    }

    private static List<PerformanceArea> createPerformanceAreas(Performance performance) {
        List<AreaConfig> areaConfigs = List.of(
                new AreaConfig(AreaName.VIP, AreaGrade.PREMIUM, 5, 20, 150000),
                new AreaConfig(AreaName.A, AreaGrade.SPECIAL, 15, 10, 100000),
                new AreaConfig(AreaName.B, AreaGrade.ROYAL, 15, 10, 120000),
                new AreaConfig(AreaName.C, AreaGrade.SPECIAL, 15, 10, 100000),
                new AreaConfig(AreaName.D, AreaGrade.NORMAL, 15, 30, 80000)
        );

        List<PerformanceArea> performanceAreas = new ArrayList<>();

        for (AreaConfig config : areaConfigs) {
            PerformanceArea area = PerformanceArea.builder()
                    .name(config.areaName)
                    .grade(config.areaGrade)
                    .price(config.price)
                    .rowCount(config.rowCount)
                    .colCount(config.colCount)
                    .performance(performance)
                    .build();

            performanceAreas.add(area);
        }

        return performanceAreas;
    }
}
