package com.rudra.apextrade.order.service;

import com.rudra.apextrade.account.model.ActiveOrder;
import com.rudra.apextrade.account.service.AccountService;
import com.rudra.apextrade.common.domain.OrderType;
import com.rudra.apextrade.common.domain.Side;
import com.rudra.apextrade.order.dto.OrderRequest;
import com.rudra.apextrade.order.dto.OrderResponse;
import com.rudra.apextrade.order.exception.DuplicateCancelException;
import com.rudra.apextrade.order.exception.OrderNotFoundException;
import com.rudra.apextrade.transport.order.OrderCommandPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderServiceTest {

    private OrderCommandPublisher publisher;
    private AccountService accountService;
    private OrderService orderService;

    private static final long USER_ID = 101L;

    @BeforeEach
    void setUp() {
        publisher = mock(OrderCommandPublisher.class);
        accountService = new AccountService();
        accountService.createAccount(USER_ID);
        orderService = new OrderService(publisher, accountService);
    }

    @Test
    @DisplayName("Cancel Fence: Initial cancel sets pendingCancel flag and dispatches to Disruptor")
    void testInitialCancelSucceeds() {
        // Place an active order
        OrderRequest placeReq = new OrderRequest(Side.BUY, 50_000L, 1L, OrderType.LIMIT);
        OrderResponse placeResp = orderService.placeOrder(USER_ID, placeReq);
        long orderId = placeResp.orderId();

        ActiveOrder order = accountService.getActiveOrder(USER_ID, orderId);
        assertNotNull(order);
        assertFalse(order.isPendingCancel(), "Order must start with pendingCancel = false");

        // Fire cancel
        OrderResponse cancelResp = orderService.cancelOrder(USER_ID, orderId);
        assertEquals(orderId, cancelResp.orderId());
        assertEquals("ACCEPTED", cancelResp.status());
        assertTrue(order.isPendingCancel(), "Order must now be flagged as pendingCancel = true");

        // Verify publisher was called exactly once
        verify(publisher, times(1)).publishCancelOrder(eq(USER_ID), eq(orderId), anyLong());
    }

    @Test
    @DisplayName("Cancel Fence: Duplicate cancel while in-flight throws DuplicateCancelException")
    void testDuplicateCancelThrows() {
        OrderRequest placeReq = new OrderRequest(Side.BUY, 50_000L, 1L, OrderType.LIMIT);
        OrderResponse placeResp = orderService.placeOrder(USER_ID, placeReq);
        long orderId = placeResp.orderId();

        // First cancel succeeds
        orderService.cancelOrder(USER_ID, orderId);

        // Immediate duplicate cancel must throw DuplicateCancelException
        DuplicateCancelException ex = assertThrows(DuplicateCancelException.class, () -> {
            orderService.cancelOrder(USER_ID, orderId);
        });

        assertEquals("CANCEL_ALREADY_PENDING", ex.getErrorCode());
        // Verify publisher was STILL only called once (second attempt dropped at ingress)
        verify(publisher, times(1)).publishCancelOrder(eq(USER_ID), eq(orderId), anyLong());
    }

    @Test
    @DisplayName("Cancel Fence: Non-existent order throws OrderNotFoundException")
    void testCancelNonExistentOrderThrows() {
        assertThrows(OrderNotFoundException.class, () -> {
            orderService.cancelOrder(USER_ID, 9999L);
        });
        verify(publisher, never()).publishCancelOrder(anyLong(), anyLong(), anyLong());
    }
}
