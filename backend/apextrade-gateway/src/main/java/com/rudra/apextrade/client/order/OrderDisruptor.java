package com.rudra.apextrade.client.order;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.YieldingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.rudra.apextrade.common.event.OrderCommandEvent;
import com.rudra.apextrade.common.event.OrderCommandEventFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ThreadFactory;

@Configuration
public class OrderDisruptor {

    private static final int BUFFER_SIZE = 65536;

    @Bean
    public OrderCommandEventFactory orderCommandEventFactory() {
        return new OrderCommandEventFactory();
    }

    @Bean
    public OrderWireHandler orderWireHandler() {
        return new OrderWireHandler();
    }

    @Bean(destroyMethod = "shutdown")
    public Disruptor<OrderCommandEvent> orderDisruptorInstance(
            OrderCommandEventFactory factory,
            OrderWireHandler handler
    ) {
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "gateway-socket-thread");
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

        disruptor.handleEventsWith(handler);
        disruptor.start();

        return disruptor;
    }

    @Bean
    public RingBuffer<OrderCommandEvent> orderRingBuffer(Disruptor<OrderCommandEvent> orderDisruptorInstance) {
        return orderDisruptorInstance.getRingBuffer();
    }

    @Bean
    public OrderCommandPublisher orderCommandPublisher(RingBuffer<OrderCommandEvent> orderRingBuffer) {
        return new OrderCommandPublisher(orderRingBuffer);
    }
}

