package com.rudra.apextrade.core.order;

public enum Side {

    BUY,
    SELL;

    public Side opposite() {
        return this == BUY ? SELL : BUY;
    }

}
