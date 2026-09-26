package com.rudra.apextrade.disruptor.event;

import com.rudra.apextrade.core.event.Trade;
import com.rudra.apextrade.core.order.OrderType;
import com.rudra.apextrade.core.order.Side;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Getter
@Setter
public class OrderCommandEvent {

    private CommandType commandType;
    private long userId;
    private long orderId;
    private Side side;
    private long price;
    private long quantity;
    private OrderType orderType;
    private CompletableFuture<List<Trade>> resultFuture;

    public void set(CommandType commandType, long userId, long orderId, Side side,
                    long price, long quantity, OrderType orderType,
                    CompletableFuture<List<Trade>> resultFuture) {

        this.commandType = commandType;
        this.userId = userId;
        this.orderId = orderId;
        this.side = side;
        this.price = price;
        this.quantity = quantity;
        this.orderType = orderType;
        this.resultFuture = resultFuture;

    }

    public void setCancel(long orderId, long userId, CompletableFuture<List<Trade>> resultFuture) {
        clear();
        this.commandType = CommandType.CANCEL_ORDER;
        this.orderId = orderId;
        this.userId = userId;
        this.resultFuture = resultFuture;
    }

    public void clear() {
        this.commandType = null;
        this.orderId = 0L;
        this.userId = 0L;
        this.side = null;
        this.price = 0L;
        this.quantity = 0L;
        this.orderType = null;
        this.resultFuture = null;
    }

}
