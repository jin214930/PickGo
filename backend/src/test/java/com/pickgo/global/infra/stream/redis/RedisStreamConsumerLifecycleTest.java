package com.pickgo.global.infra.stream.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.StreamOperations;

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

    @Test
    void shutdown후_blocking_read가_메시지를_반환해도_handler를_예약하지_않는다() throws InterruptedException {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        StreamOperations<String, Object, Object> streamOperations = mock(StreamOperations.class);
        MapRecord<String, Object, Object> record = mock(MapRecord.class);
        AtomicInteger submittedTasks = new AtomicInteger();
        AtomicReference<Runnable> consumerLoop = new AtomicReference<>();
        CountDownLatch readStarted = new CountDownLatch(1);
        CountDownLatch releaseRead = new CountDownLatch(1);

        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        when(streamOperations.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenAnswer(invocation -> {
                    readStarted.countDown();
                    releaseRead.await(5, TimeUnit.SECONDS);
                    return List.of(record);
                });

        ReadRaceConsumer consumer = new ReadRaceConsumer(redisTemplate, command -> {
            submittedTasks.incrementAndGet();
            consumerLoop.set(command);
        });
        consumer.startConsumer();

        Thread consumerThread = new Thread(consumerLoop.get());
        consumerThread.start();
        try {
            assertThat(readStarted.await(5, TimeUnit.SECONDS)).isTrue();
            consumer.shutdown();
        } finally {
            releaseRead.countDown();
            consumerThread.join(5_000);
        }

        assertThat(submittedTasks).hasValue(1);
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

    private static class ReadRaceConsumer extends RedisStreamConsumer {

        private final Executor executor;

        private ReadRaceConsumer(StringRedisTemplate redisTemplate, Executor executor) {
            super(redisTemplate, null);
            this.executor = executor;
        }

        @Override
        public void initConsumerGroup() {
            // 테스트에서는 Redis Consumer Group 초기화를 생략한다.
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
            return executor;
        }

        @Override
        protected void handleMessage(MapRecord<String, Object, Object> message) {
        }
    }
}
