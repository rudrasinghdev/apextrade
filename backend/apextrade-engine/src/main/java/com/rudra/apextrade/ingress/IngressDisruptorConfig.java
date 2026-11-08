package com.rudra.apextrade.ingress;

import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.rudra.apextrade.common.event.OrderCommandEvent;
import com.rudra.apextrade.common.event.OrderCommandEventFactory;
import com.rudra.apextrade.matching.MatchingEngine;
import lombok.Getter;

import java.util.concurrent.ThreadFactory;

public class IngressDisruptorConfig implements AutoCloseable {

    private static final int BUFFER_SIZE = 65536;

    private final Disruptor<OrderCommandEvent> disruptor;
    @Getter
    private final OrderCommandPublisher publisher;

    public IngressDisruptorConfig(MatchingEngine matchingEngine) {
        ThreadFactory threadFactory = runnable -> new Thread(runnable, "engine-matcher-core");

        this.disruptor = new Disruptor<>(
                new OrderCommandEventFactory(),
                BUFFER_SIZE,
                threadFactory,
                ProducerType.SINGLE,
                new BusySpinWaitStrategy()
        );

        this.disruptor.handleEventsWith(new OrderCommandHandler(matchingEngine));
        this.disruptor.start();

        this.publisher = new OrderCommandPublisher(this.disruptor.getRingBuffer());
    }

    @Override
    public void close() {
        this.disruptor.shutdown();
    }
}
