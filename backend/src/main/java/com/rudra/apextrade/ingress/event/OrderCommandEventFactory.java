package com.rudra.apextrade.ingress.event;

import com.lmax.disruptor.EventFactory;
import org.springframework.stereotype.Component;

@Component
public class OrderCommandEventFactory implements EventFactory<OrderCommandEvent> {

    @Override
    public OrderCommandEvent newInstance() {
        return new OrderCommandEvent();
    }

}
