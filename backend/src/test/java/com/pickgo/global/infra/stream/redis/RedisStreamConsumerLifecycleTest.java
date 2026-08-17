package com.pickgo.global.infra.stream.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisStreamConsumerLifecycleTest {

    @Test
    void shutdown하면_아직_실행되지않은_소비루프가_메시지를_처리하지_않는다() {
        AtomicReference<Runnable> task = new AtomicReference<>();
        AtomicInteger consumedCount = new AtomicInteger();
        TestConsumer consumer = new TestConsumer(task, consumedCount);

        consumer.startConsumer();
        consumer.shutdown();
        task.get().run();

        assertThat(consumedCount).hasValue(0);
    }

    private static class TestConsumer extends RedisStreamConsumer {

        private final AtomicReference<Runnable> task;
        private final AtomicInteger consumedCount;

        private TestConsumer(AtomicReference<Runnable> task, AtomicInteger consumedCount) {
            super(null, null);
            this.task = task;
            this.consumedCount = consumedCount;
        }

        @Override
        public void initConsumerGroup() {
            // 테스트에서는 Redis 연결 없이 생명주기만 검증한다.
        }

        @Override
        public void consume(String consumerGroup, String consumerName, String streamKey) {
            consumedCount.incrementAndGet();
        }

        @Override
        protected String getConsumerGroupName() {
            return "test-group";
        }

        @Override
        protected String getConsumerName() {
            return "test-consumer";
        }

        @Override
        protected String getStreamKey() {
            return "test-stream";
        }

        @Override
        protected Executor getExecutor() {
            return task::set;
        }

        @Override
        protected void handleMessage(MapRecord<String, Object, Object> message) {
        }
    }
}
