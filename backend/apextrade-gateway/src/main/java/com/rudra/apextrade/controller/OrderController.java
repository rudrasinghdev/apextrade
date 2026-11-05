package com.rudra.apextrade.controller;

import com.rudra.apextrade.common.OrderType;
import com.rudra.apextrade.dto.OrderRequest;
import com.rudra.apextrade.dto.OrderResponse;
import com.rudra.apextrade.exception.InvalidOrderException;
import com.rudra.apextrade.security.UserPrincipal;
import com.rudra.apextrade.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody OrderRequest request
    ) {
        long effectiveUserId = (principal != null) ? principal.userId() : request.userId();
        if (effectiveUserId <= 0) {
            throw new InvalidOrderException("Invalid userId");
        }
        if (request.side() == null) {
            throw new InvalidOrderException("Side must be BUY or SELL");
        }
        if (request.quantity() <= 0) {
            throw new InvalidOrderException("Quantity must be greater than zero");
        }
        if (request.orderType() == null) {
            throw new InvalidOrderException("OrderType must be LIMIT or MARKET");
        }
        if (request.orderType() == OrderType.LIMIT && request.price() <= 0) {
            throw new InvalidOrderException("Price must be greater than zero for LIMIT orders");
        }

        OrderRequest effectiveReq = (principal != null && request.userId() != principal.userId())
                ? new OrderRequest(effectiveUserId, request.side(), request.price(), request.quantity(), request.orderType())
                : request;

        OrderResponse response = orderService.placeOrder(effectiveReq);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{orderId}")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable long orderId) {
        if (orderId <= 0) {
            throw new InvalidOrderException("Invalid orderId: " + orderId);
        }

        OrderResponse response = orderService.cancelOrder(orderId);
        return ResponseEntity.ok(response);
    }
}
