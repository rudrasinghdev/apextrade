package com.rudra.apextrade.config;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.YieldingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.rudra.apextrade.ingress.event.OrderCommandEvent;
import com.rudra.apextrade.ingress.event.OrderCommandEventFactory;
import com.rudra.apextrade.ingress.handler.GatewayOrderCommandHandler;
import com.rudra.apextrade.ingress.publisher.OrderCommandPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ThreadFactory;

@Configuration
public class IngressDisruptorConfig {

    private static final int BUFFER_SIZE = 65536;

    @Bean
    public OrderCommandEventFactory orderCommandEventFactory() {
        return new OrderCommandEventFactory();
    }

    @Bean
    public GatewayOrderCommandHandler gatewayOrderCommandHandler() {
        return new GatewayOrderCommandHandler();
    }

    @Bean(destroyMethod = "shutdown")
    public Disruptor<OrderCommandEvent> disruptor(
            OrderCommandEventFactory factory,
            GatewayOrderCommandHandler handler
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
    public RingBuffer<OrderCommandEvent> ringBuffer(Disruptor<OrderCommandEvent> disruptor) {
        return disruptor.getRingBuffer();
    }

    @Bean
    public OrderCommandPublisher orderCommandPublisher(RingBuffer<OrderCommandEvent> ringBuffer) {
        return new OrderCommandPublisher(ringBuffer);
    }
}
