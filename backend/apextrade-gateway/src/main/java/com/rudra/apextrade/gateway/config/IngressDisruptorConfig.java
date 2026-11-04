package com.rudra.apextrade.gateway.config;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.YieldingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.rudra.apextrade.core.book.OrderBook;
import com.rudra.apextrade.core.engine.MatchingEngine;
import com.rudra.apextrade.core.order.OrderPool;
import com.rudra.apextrade.egress.publisher.TradeEgressPublisher;
import com.rudra.apextrade.ingress.event.OrderCommandEvent;
import com.rudra.apextrade.ingress.event.OrderCommandEventFactory;
import com.rudra.apextrade.ingress.handler.OrderCommandHandler;
import com.rudra.apextrade.ingress.publisher.OrderCommandPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ThreadFactory;

@Configuration
public class IngressDisruptorConfig {

    private static final int BUFFER_SIZE = 65536;

    @Bean
    public OrderPool orderPool() {
        return new OrderPool();
    }

    @Bean
    public OrderBook orderBook(OrderPool orderPool) {
        return new OrderBook("BTC/USDT", orderPool);
    }

    @Bean
    public MatchingEngine matchingEngine(OrderBook orderBook,
                                         @Value("${apextrade.engine.listing-price:50000}") long listingPrice,
                                         TradeEgressPublisher tradeEgressPublisher) {
        return new MatchingEngine(orderBook, listingPrice, tradeEgressPublisher);
    }

    @Bean
    public OrderCommandHandler orderCommandHandler(MatchingEngine matchingEngine) {
        return new OrderCommandHandler(matchingEngine);
    }

    @Bean(destroyMethod = "shutdown")
    public Disruptor<OrderCommandEvent> disruptor(OrderCommandEventFactory factory, OrderCommandHandler orderCommandHandler) {
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
    public OrderCommandEventFactory orderCommandEventFactory() {
        return new OrderCommandEventFactory();
    }

    @Bean
    public RingBuffer<OrderCommandEvent> ringBuffer(Disruptor<OrderCommandEvent> disruptor) {
        return disruptor.getRingBuffer();
    }

    @Bean
    public OrderCommandPublisher orderCommandPublisher(RingBuffer<OrderCommandEvent> ringBuffer) {
        return new OrderCommandPublisher(ringBuffer);
    }
}

