package com.rudra.apextrade.egress.buffer;

import com.rudra.apextrade.core.order.Side;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

abstract class SpscPad0 {
    long p00, p01, p02, p03, p04, p05, p06, p07;
}

abstract class SpscProducer extends SpscPad0 {
    protected long producerIndex = 0;
}

abstract class SpscPad1 extends SpscProducer {
    long p10, p11, p12, p13, p14, p15, p16, p17;
}

abstract class SpscConsumer extends SpscPad1 {
    protected long consumerIndex = 0;
}

abstract class SpscPad2 extends SpscConsumer {
    long p20, p21, p22, p23, p24, p25, p26, p27;
}

public class SpscTradeRingBuffer extends SpscPad2 {

    private final int capacity;
    private final int mask;
    private final TradeEvent[] buffer;

    private static final VarHandle PRODUCER_VH;
    private static final VarHandle CONSUMER_VH;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            PRODUCER_VH = lookup.findVarHandle(SpscProducer.class, "producerIndex", long.class);
            CONSUMER_VH = lookup.findVarHandle(SpscConsumer.class, "consumerIndex", long.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public SpscTradeRingBuffer(int requestedCapacity) {
        if (Integer.bitCount(requestedCapacity) != 1) {
            throw new IllegalArgumentException("Capacity must be a power of 2 (e.g. 1024, 65536)");
        }
        this.capacity = requestedCapacity;
        this.mask = requestedCapacity - 1;
        this.buffer = new TradeEvent[requestedCapacity];

        for (int i = 0; i < requestedCapacity; i++) {
            this.buffer[i] = new TradeEvent();
        }
    }

    public boolean offer(long tradeId, long makerOrderId, long takerOrderId,
                         Side takerSide, long price, long quantity, long timestamp) {
        final long currentProducer = (long) PRODUCER_VH.get(this);
        final long currentConsumer = (long) CONSUMER_VH.getAcquire(this);

        if (currentProducer - currentConsumer >= capacity) {
            return false;
        }

        int index = (int) (currentProducer & mask);
        buffer[index].set(tradeId, makerOrderId, takerOrderId, takerSide, price, quantity, timestamp);

        PRODUCER_VH.setRelease(this, currentProducer + 1);
        return true;
    }

    public TradeEvent peek() {
        final long currentConsumer = (long) CONSUMER_VH.get(this);
        final long currentProducer = (long) PRODUCER_VH.getAcquire(this);

        if (currentConsumer >= currentProducer) {
            return null;
        }

        return buffer[(int) (currentConsumer & mask)];
    }

    public void advance() {
        final long currentConsumer = (long) CONSUMER_VH.get(this);
        CONSUMER_VH.setRelease(this, currentConsumer + 1);
    }

    public int size() {
        long currentProducer = (long) PRODUCER_VH.getAcquire(this);
        long currentConsumer = (long) CONSUMER_VH.getAcquire(this);
        return (int) Math.max(0, currentProducer - currentConsumer);
    }

    public boolean isEmpty() {
        return (long) CONSUMER_VH.getAcquire(this) >= (long) PRODUCER_VH.getAcquire(this);
    }
}