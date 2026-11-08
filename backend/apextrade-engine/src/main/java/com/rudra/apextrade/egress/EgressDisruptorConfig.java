package com.rudra.apextrade.egress;

import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.rudra.apextrade.common.event.ExecutionEvent;
import com.rudra.apextrade.common.event.ExecutionEventFactory;
import lombok.Getter;

import java.util.concurrent.ThreadFactory;

public class EgressDisruptorConfig implements AutoCloseable {

    private static final int BUFFER_SIZE = 65536;

    private final Disruptor<ExecutionEvent> disruptor;
    @Getter
    private final ExecutionEgressPublisher publisher;
    @Getter
    private final SocketExecutionDispatcher dispatcher;

    public EgressDisruptorConfig() {
        this.dispatcher = new SocketExecutionDispatcher();
        ThreadFactory threadFactory = runnable -> new Thread(runnable, "engine-execution-egress");

        this.disruptor = new Disruptor<>(
                new ExecutionEventFactory(),
                BUFFER_SIZE,
                threadFactory,
                ProducerType.SINGLE,
                new BusySpinWaitStrategy()
        );

        this.disruptor.handleEventsWith(this.dispatcher);
        this.disruptor.start();

        this.publisher = new ExecutionEgressPublisher(this.disruptor.getRingBuffer());
    }

    @Override
    public void close() {
        this.disruptor.shutdown();
    }
}
