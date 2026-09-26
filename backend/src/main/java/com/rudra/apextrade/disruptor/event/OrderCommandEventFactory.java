package com.rudra.apextrade.disruptor.event;

import com.lmax.disruptor.EventFactory;

public class OrderCommandEventFactory implements EventFactory<OrderCommandEvent> {

    @Override
    public OrderCommandEvent newInstance() {
        return new OrderCommandEvent();
    }
}
