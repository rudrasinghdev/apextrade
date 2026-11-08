package com.rudra.apextrade.ingress;

import com.lmax.disruptor.EventHandler;
import com.rudra.apextrade.common.CommandType;
import com.rudra.apextrade.common.event.OrderCommandEvent;
import com.rudra.apextrade.matching.MatchingEngine;

public class OrderCommandHandler implements EventHandler<OrderCommandEvent> {

    private final MatchingEngine matchingEngine;

    public OrderCommandHandler(MatchingEngine matchingEngine) {
        this.matchingEngine = matchingEngine;
    }

    @Override
    public void onEvent(OrderCommandEvent event, long sequence, boolean endOfBatch) throws Exception {
        if (event.getCommandType() == null) {
            return;
        }

        if (event.getCommandType() == CommandType.PLACE_ORDER) {
            matchingEngine.processOrder(
                    event.getUserId(),
                    event.getOrderId(),
                    event.getSide(),
                    event.getPrice(),
                    event.getQuantity(),
                    event.getOrderType(),
                    event.getTimestamp()
            );
        } else if (event.getCommandType() == CommandType.CANCEL_ORDER) {
            matchingEngine.cancelOrder(event.getOrderId());
        }
    }
}
