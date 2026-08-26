package com.pickgo.domain.performance.kopis.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withTooManyRequests;
import static org.springframework.http.MediaType.APPLICATION_XML;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = KopisServiceTest.TestConfig.class)
@TestPropertySource(properties = "kopis.apikey=test-key")
class KopisServiceTest {
    private static final String PERFORMANCE_LIST_XML = "<dbs><db><mt20id>PF000001</mt20id></db></dbs>";

    @org.springframework.beans.factory.annotation.Autowired
    private KopisService kopisService;

    @org.springframework.beans.factory.annotation.Autowired
    private MockRestServiceServer mockServer;

    @AfterEach
    void verifyMockServer() {
        mockServer.verify();
    }

    @Test
    @DisplayName("5xx 응답은 최대 3회 시도 후 성공하면 결과를 반환한다")
    void retriesOnServerError() {
        mockServer.expect(ExpectedCount.times(2), anything())
                .andRespond(withServerError());
        mockServer.expect(ExpectedCount.once(), anything())
                .andRespond(withSuccess(PERFORMANCE_LIST_XML, APPLICATION_XML));

        List<String> result = kopisService.fetchPerformanceIds(1, 1);

        assertThat(result).containsExactly("PF000001");
    }

    @Test
    @DisplayName("429 응답은 재시도 후 성공하면 결과를 반환한다")
    void retriesOnTooManyRequests() {
        mockServer.expect(ExpectedCount.times(2), anything())
                .andRespond(withTooManyRequests());
        mockServer.expect(ExpectedCount.once(), anything())
                .andRespond(withSuccess(PERFORMANCE_LIST_XML, APPLICATION_XML));

        List<String> result = kopisService.fetchPerformanceIds(1, 1);

        assertThat(result).containsExactly("PF000001");
    }

    @Test
    @DisplayName("네트워크 오류는 재시도 후 성공하면 결과를 반환한다")
    void retriesOnNetworkError() {
        mockServer.expect(ExpectedCount.times(2), anything())
                .andRespond(withException(new IOException("connection reset")));
        mockServer.expect(ExpectedCount.once(), anything())
                .andRespond(withSuccess(PERFORMANCE_LIST_XML, APPLICATION_XML));

        List<String> result = kopisService.fetchPerformanceIds(1, 1);

        assertThat(result).containsExactly("PF000001");
    }

    @Test
    @DisplayName("400 응답은 재시도하지 않고 빈 목록으로 복구한다")
    void doesNotRetryOnClientError() {
        mockServer.expect(ExpectedCount.once(), anything())
                .andRespond(withBadRequest());

        List<String> result = kopisService.fetchPerformanceIds(1, 1);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("XML 파싱 오류는 재시도하지 않고 빈 목록으로 복구한다")
    void doesNotRetryOnXmlParsingError() {
        mockServer.expect(ExpectedCount.once(), anything())
                .andRespond(withSuccess("<invalid", APPLICATION_XML));

        List<String> result = kopisService.fetchPerformanceIds(1, 1);

        assertThat(result).isEmpty();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableRetry
    static class TestConfig {
        @Bean
        RestClient.Builder restClientBuilder() {
            return RestClient.builder();
        }

        @Bean
        MockRestServiceServer mockRestServiceServer(RestClient.Builder builder) {
            return MockRestServiceServer.bindTo(builder).build();
        }

        @Bean
        @DependsOn("mockRestServiceServer")
        KopisService kopisService(RestClient.Builder builder) {
            return new KopisService(builder);
        }
    }
}
