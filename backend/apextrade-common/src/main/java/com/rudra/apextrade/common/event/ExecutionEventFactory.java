package com.rudra.apextrade.common.event;

import com.lmax.disruptor.EventFactory;

public class ExecutionEventFactory implements EventFactory<ExecutionEvent> {

    @Override
    public ExecutionEvent newInstance() {
        return new ExecutionEvent();
    }
}

