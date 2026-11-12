package com.rudra.apextrade.ingress;

import com.lmax.disruptor.EventHandler;
import com.rudra.apextrade.common.domain.CommandType;
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
            System.out.printf("[Engine Sequencer] S2 RingBuffer dispatched Order #%d for Trader #%d [%s %d @ %d USD] -> Handing to MatchingEngine.%n",
                    event.getOrderId(), event.getUserId(), event.getSide(), event.getQuantity(), event.getPrice());
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
            System.out.printf("[Engine Sequencer] S2 RingBuffer dispatched Cancel for Order #%d (Trader #%d) -> Handing to MatchingEngine.%n",
                    event.getOrderId(), event.getUserId());
            matchingEngine.cancelOrder(event.getUserId(), event.getOrderId());
        }
    }
}
