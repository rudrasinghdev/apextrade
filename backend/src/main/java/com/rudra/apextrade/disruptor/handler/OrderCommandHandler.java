package com.rudra.apextrade.disruptor.handler;

import com.lmax.disruptor.EventHandler;
import com.rudra.apextrade.core.engine.MatchingEngine;
import com.rudra.apextrade.core.event.Trade;
import com.rudra.apextrade.core.order.Order;
import com.rudra.apextrade.disruptor.event.CommandType;
import com.rudra.apextrade.disruptor.event.OrderCommandEvent;

import java.util.Collections;
import java.util.List;

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

        try {
            if (event.getCommandType() == CommandType.PLACE_ORDER) {
                Order order = new Order(
                        event.getOrderId(),
                        event.getUserId(),
                        event.getSide(),
                        event.getOrderType(),
                        event.getPrice(),
                        event.getQuantity()
                );
                List<Trade> trades = matchingEngine.processOrder(order);
                if (event.getResultFuture() != null) {
                    event.getResultFuture().complete(trades);
                }
            } else if (event.getCommandType() == CommandType.CANCEL_ORDER) {
                boolean cancelled = matchingEngine.cancelOrder(event.getOrderId());
                if (event.getResultFuture() != null) {
                    event.getResultFuture().complete(Collections.emptyList());
                }
            }
        } catch (Throwable t) {
            if (event.getResultFuture() != null) {
                event.getResultFuture().completeExceptionally(t);
            }
        } finally {
            event.clear();
        }
    }
}
