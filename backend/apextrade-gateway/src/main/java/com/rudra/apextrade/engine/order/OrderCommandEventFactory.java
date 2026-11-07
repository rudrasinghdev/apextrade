package com.rudra.apextrade.engine.order;

import com.lmax.disruptor.EventFactory;

public class OrderCommandEventFactory implements EventFactory<OrderCommandEvent> {
    @Override
    public OrderCommandEvent newInstance() {
        return new OrderCommandEvent();
    }
}
