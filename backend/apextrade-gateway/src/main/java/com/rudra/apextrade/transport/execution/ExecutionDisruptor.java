package com.rudra.apextrade.transport.execution;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.YieldingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.rudra.apextrade.account.service.AccountService;
import com.rudra.apextrade.common.event.ExecutionEvent;
import com.rudra.apextrade.common.event.ExecutionEventFactory;
import com.rudra.apextrade.websocket.TradeWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ThreadFactory;

@Configuration
public class ExecutionDisruptor {

    private static final int BUFFER_SIZE = 65536;

    @Bean
    public ExecutionEventFactory executionEventFactory() {
        return new ExecutionEventFactory();
    }

    @Bean
    public ExecutionDispatchHandler executionDispatchHandler(
            AccountService ledger,
            TradeWebSocketHandler webSocketHandler
    ) {
        return new ExecutionDispatchHandler(ledger, webSocketHandler);
    }

    @Bean(destroyMethod = "shutdown")
    public Disruptor<ExecutionEvent> executionDisruptorInstance(
            ExecutionEventFactory factory,
            ExecutionDispatchHandler handler
    ) {
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "gateway-execution-disruptor");
            thread.setDaemon(true);
            return thread;
        };

        Disruptor<ExecutionEvent> disruptor = new Disruptor<>(
                factory,
                BUFFER_SIZE,
                threadFactory,
                ProducerType.SINGLE,
                new YieldingWaitStrategy()
        );

        disruptor.setDefaultExceptionHandler(new com.lmax.disruptor.ExceptionHandler<ExecutionEvent>() {
            @Override
            public void handleEventException(Throwable ex, long sequence, ExecutionEvent event) {
                System.err.printf("[Execution Disruptor] Exception processing sequence %d: %s%n", sequence, ex.getMessage());
            }

            @Override
            public void handleOnStartException(Throwable ex) {
                System.err.printf("[Execution Disruptor] Exception during start: %s%n", ex.getMessage());
            }

            @Override
            public void handleOnShutdownException(Throwable ex) {
                System.err.printf("[Execution Disruptor] Exception during shutdown: %s%n", ex.getMessage());
            }
        });

        disruptor.handleEventsWith(handler);
        disruptor.start();

        return disruptor;
    }

    @Bean
    public RingBuffer<ExecutionEvent> executionRingBuffer(Disruptor<ExecutionEvent> executionDisruptorInstance) {
        return executionDisruptorInstance.getRingBuffer();
    }

    @Bean
    public ExecutionEventPublisher executionEventPublisher(RingBuffer<ExecutionEvent> ringBuffer) {
        return new ExecutionEventPublisher(ringBuffer);
    }
}
