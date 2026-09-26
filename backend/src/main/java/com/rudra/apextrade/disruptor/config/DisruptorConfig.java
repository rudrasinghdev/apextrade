package com.rudra.apextrade.disruptor.config;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.YieldingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.rudra.apextrade.core.book.OrderBook;
import com.rudra.apextrade.core.engine.MatchingEngine;
import com.rudra.apextrade.disruptor.event.OrderCommandEvent;
import com.rudra.apextrade.disruptor.event.OrderCommandEventFactory;
import com.rudra.apextrade.disruptor.handler.OrderCommandHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ThreadFactory;

@Configuration
public class DisruptorConfig {

    private static final int BUFFER_SIZE = 65536; // 2^16

    @Bean
    public OrderBook orderBook() {
        return new OrderBook("BTC/USDT");
    }

    @Bean
    public MatchingEngine matchingEngine(OrderBook orderBook) {
        return new MatchingEngine(orderBook);
    }

    @Bean
    public OrderCommandHandler orderCommandHandler(MatchingEngine matchingEngine) {
        return new OrderCommandHandler(matchingEngine);
    }

    @Bean(destroyMethod = "shutdown")
    public Disruptor<OrderCommandEvent> disruptor(OrderCommandHandler orderCommandHandler) {
        OrderCommandEventFactory factory = new OrderCommandEventFactory();

        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "matching-core-thread");
            thread.setDaemon(true);
            return thread;
        };

        Disruptor<OrderCommandEvent> disruptor = new Disruptor<>(
                factory,
                BUFFER_SIZE,
                threadFactory,
                ProducerType.MULTI,
                new YieldingWaitStrategy()
        );

        disruptor.handleEventsWith(orderCommandHandler);
        disruptor.start();

        return disruptor;
    }

    @Bean
    public RingBuffer<OrderCommandEvent> ringBuffer(Disruptor<OrderCommandEvent> disruptor) {
        return disruptor.getRingBuffer();
    }
}
