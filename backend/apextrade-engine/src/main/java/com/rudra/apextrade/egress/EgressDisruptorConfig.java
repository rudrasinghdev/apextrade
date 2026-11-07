package com.rudra.apextrade.egress;

import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;


import java.util.concurrent.ThreadFactory;

public class EgressDisruptorConfig implements AutoCloseable {

    private static final int BUFFER_SIZE = 65536;

    private final Disruptor<TradeEvent> disruptor;
    private final TradeEgressPublisher publisher;
    private final SocketTradeDispatcher dispatcher;

    public EgressDisruptorConfig() {
        this.dispatcher = new SocketTradeDispatcher();
        ThreadFactory threadFactory = runnable -> new Thread(runnable, "engine-trade-egress");

        this.disruptor = new Disruptor<>(
                new TradeEventFactory(),
                BUFFER_SIZE,
                threadFactory,
                ProducerType.SINGLE,                 // Pure SPSC
                new BusySpinWaitStrategy()
        );

        this.disruptor.handleEventsWith(this.dispatcher);
        this.disruptor.start();

        this.publisher = new TradeEgressPublisher(this.disruptor.getRingBuffer());
    }

    public TradeEgressPublisher getPublisher() {
        return this.publisher;
    }

    public SocketTradeDispatcher getDispatcher() {
        return this.dispatcher;
    }

    @Override
    public void close() {
        this.disruptor.shutdown();
    }
}
