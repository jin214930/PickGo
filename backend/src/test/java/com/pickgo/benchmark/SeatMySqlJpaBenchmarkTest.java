package com.pickgo.benchmark;

import com.querydsl.jpa.impl.JPAQueryFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(
        properties = {
                "spring.jpa.hibernate.ddl-auto=create",
                "spring.jpa.show-sql=false"
        }
)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(SeatMySqlJpaBenchmarkTest.QuerydslTestConfig.class)
class SeatMySqlJpaBenchmarkTest {
    private static final int SESSION_COUNT = 12;
    private static final int AREA_COUNT = 5;
    private static final int[] SEATS_PER_AREA = {100, 150, 150, 150, 450};
    private static final int METADATA_ROWS = 1 + SESSION_COUNT + AREA_COUNT;
    private static final int TOTAL_SEATS = 12_000;
    private static final int WARMUP_COUNT = 1;
    private static final int MEASUREMENT_COUNT = 5;
    private static final String TABLE = "seat_architecture_benchmark_row";

    private static final BenchmarkDatabase DATABASE = BenchmarkDatabase.create();

    @TestConfiguration(proxyBeanMethods = false)
    static class QuerydslTestConfig {
        @Bean
        JPAQueryFactory jpaQueryFactory(EntityManager entityManager) {
            return new JPAQueryFactory(entityManager);
        }
    }

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void verifyDatabase() throws SQLException {
        try (Connection connection = DATABASE.connectToBenchmarkDatabase()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("MySQL");
            System.out.printf(
                    "MYSQL_JPA_CONFIG database=%s version=%s sessions=%d areas=%d seatsPerSession=%d totalSeats=%d warmup=%d measurements=%d%n",
                    DATABASE.databaseName,
                    connection.getMetaData().getDatabaseProductVersion(),
                    SESSION_COUNT,
                    AREA_COUNT,
                    Arrays.stream(SEATS_PER_AREA).sum(),
                    TOTAL_SEATS,
                    WARMUP_COUNT,
                    MEASUREMENT_COUNT
            );
        }
    }

    @AfterAll
    static void removeBenchmarkDatabase() {
        DATABASE.drop();
    }

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::jdbcUrl);
        registry.add("spring.datasource.username", () -> DATABASE.username);
        registry.add("spring.datasource.password", () -> DATABASE.password);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.database-platform", () -> "com.pickgo.global.config.MySqlFullTextDialect");
        registry.add("spring.jpa.properties.hibernate.jdbc.batch_size", () -> "0");
        registry.add("spring.jpa.properties.hibernate.order_inserts", () -> "false");
        registry.add("spring.jpa.properties.hibernate.order_updates", () -> "false");
    }

    @Test
    void compareJpaPersistenceAgainstMySql() {
        clearRows();
        runOnce(Mode.ON_DEMAND);
        clearRows();
        runOnce(Mode.PRECREATE);

        List<Long> onDemand = measure(Mode.ON_DEMAND);
        List<Long> precreate = measure(Mode.PRECREATE);

        assertThat(rowCount()).isEqualTo(METADATA_ROWS + TOTAL_SEATS);
        printResult("ON_DEMAND", onDemand);
        printResult("PRECREATE", precreate);
    }

    private List<Long> measure(Mode mode) {
        List<Long> times = new ArrayList<>();
        for (int i = 0; i < MEASUREMENT_COUNT; i++) {
            clearRows();
            times.add(runOnce(mode));
        }
        return times;
    }

    private long runOnce(Mode mode) {
        long startedAt = System.nanoTime();
        transactionTemplate.executeWithoutResult(status -> {
            persistMetadata();
            if (mode == Mode.PRECREATE) {
                persistSeats();
            }
            entityManager.flush();
        });
        entityManager.clear();
        long elapsed = (System.nanoTime() - startedAt) / 1_000_000;

        long expectedRows = mode == Mode.PRECREATE ? METADATA_ROWS + TOTAL_SEATS : METADATA_ROWS;
        assertThat(rowCount()).as("row count after %s", mode).isEqualTo(expectedRows);
        return elapsed;
    }

    private void persistMetadata() {
        entityManager.persist(SeatArchitectureBenchmarkRow.metadata(
                SeatArchitectureBenchmarkRow.Kind.PERFORMANCE, 0, 0));
        for (int session = 1; session <= SESSION_COUNT; session++) {
            entityManager.persist(SeatArchitectureBenchmarkRow.metadata(
                    SeatArchitectureBenchmarkRow.Kind.SESSION, session, 0));
        }
        for (int area = 1; area <= AREA_COUNT; area++) {
            entityManager.persist(SeatArchitectureBenchmarkRow.metadata(
                    SeatArchitectureBenchmarkRow.Kind.AREA, 0, area));
        }
    }

    private void persistSeats() {
        for (int session = 1; session <= SESSION_COUNT; session++) {
            for (int area = 1; area <= AREA_COUNT; area++) {
                int seatsInArea = SEATS_PER_AREA[area - 1];
                int columns = area == 1 ? 20 : area == AREA_COUNT ? 30 : 10;
                for (int seat = 0; seat < seatsInArea; seat++) {
                    entityManager.persist(SeatArchitectureBenchmarkRow.seat(
                            session,
                            area,
                            "R" + (seat / columns + 1),
                            seat % columns + 1
                    ));
                }
            }
        }
    }

    private void clearRows() {
        jdbcTemplate.execute("TRUNCATE TABLE " + TABLE);
        entityManager.clear();
    }

    private long rowCount() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + TABLE, Long.class);
        return count == null ? 0 : count;
    }

    private static void printResult(String mode, List<Long> times) {
        List<Long> sorted = times.stream().sorted().toList();
        double average = times.stream().mapToLong(Long::longValue).average().orElse(0);
        double median = sorted.size() % 2 == 1
                ? sorted.get(sorted.size() / 2)
                : (sorted.get(sorted.size() / 2 - 1) + sorted.get(sorted.size() / 2)) / 2.0;
        System.out.printf("JPA_MYSQL_RESULT mode=%s timesMs=%s averageMs=%.1f medianMs=%.1f%n",
                mode, times, average, median);
    }

    private enum Mode {
        ON_DEMAND,
        PRECREATE
    }

    private static final class BenchmarkDatabase {
        private final String host;
        private final String port;
        private final String username;
        private final String password;
        private final String databaseName;

        private BenchmarkDatabase(String host, String port, String username, String password, String databaseName) {
            this.host = host;
            this.port = port;
            this.username = username;
            this.password = password;
            this.databaseName = databaseName;
        }

        private static BenchmarkDatabase create() {
            try {
                Properties env = loadEnv();
                BenchmarkDatabase database = new BenchmarkDatabase(
                        env.getProperty("DB_HOST", "localhost"),
                        env.getProperty("DB_PORT", "3306"),
                        "root",
                        env.getProperty("DB_ROOT_PASSWORD"),
                        "pickgo_jpa_seat_benchmark_" + UUID.randomUUID().toString().replace("-", "")
                );
                database.createDatabase();
                Runtime.getRuntime().addShutdownHook(new Thread(database::drop, "drop-jpa-benchmark-database"));
                return database;
            } catch (Exception e) {
                throw new IllegalStateException("Unable to create isolated MySQL benchmark database", e);
            }
        }

        private void createDatabase() throws SQLException {
            try (Connection connection = connectToServer();
                 var statement = connection.createStatement()) {
                statement.executeUpdate("CREATE DATABASE `" + databaseName
                        + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            }
        }

        private Connection connectToServer() throws SQLException {
            return DriverManager.getConnection(
                    "jdbc:mysql://" + host + ":" + port + "/?useSSL=false&allowPublicKeyRetrieval=true",
                    username,
                    password
            );
        }

        private Connection connectToBenchmarkDatabase() throws SQLException {
            return DriverManager.getConnection(jdbcUrl(), username, password);
        }

        private String jdbcUrl() {
            return "jdbc:mysql://" + host + ":" + port + "/" + databaseName
                    + "?useSSL=false&allowPublicKeyRetrieval=true";
        }

        private void drop() {
            try (Connection connection = connectToServer();
                 var statement = connection.createStatement()) {
                statement.executeUpdate("DROP DATABASE IF EXISTS `" + databaseName + "`");
            } catch (SQLException ignored) {
                // Best-effort cleanup for a database created solely by this test.
            }
        }

        private static Properties loadEnv() throws IOException {
            Properties properties = new Properties();
            for (String line : Files.readAllLines(Path.of(".env"))) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int delimiter = trimmed.indexOf('=');
                if (delimiter > 0) {
                    properties.setProperty(trimmed.substring(0, delimiter), trimmed.substring(delimiter + 1));
                }
            }
            return properties;
        }
    }
}
