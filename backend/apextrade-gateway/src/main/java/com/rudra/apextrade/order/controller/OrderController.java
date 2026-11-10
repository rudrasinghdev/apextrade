package com.rudra.apextrade.order.controller;

import com.rudra.apextrade.auth.security.UserPrincipal;
import com.rudra.apextrade.order.dto.ActiveOrderResponse;
import com.rudra.apextrade.order.exception.InvalidOrderException;
import com.rudra.apextrade.order.dto.OrderRequest;
import com.rudra.apextrade.order.dto.OrderResponse;
import com.rudra.apextrade.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
            @Valid @RequestBody OrderRequest request
    ) {
        OrderResponse response = orderService.placeOrder(principal.userId(), request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{orderId}")
    public ResponseEntity<OrderResponse> cancelOrder(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("orderId") long orderId
    ) {
        if (orderId <= 0) {
            throw new InvalidOrderException("Invalid orderId: " + orderId);
        }

        OrderResponse response = orderService.cancelOrder(principal.userId(), orderId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<ActiveOrderResponse>> getAllActiveOrders(@AuthenticationPrincipal UserPrincipal principal) {
        List<ActiveOrderResponse> allActiveOrders = orderService.getAllActiveOrders(principal.userId());
        return ResponseEntity.ok(allActiveOrders);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<ActiveOrderResponse> getActiveOrderById(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("orderId") long orderId
    ) {
        if (orderId <= 0) {
            throw new InvalidOrderException("Invalid orderId: " + orderId);
        }

        ActiveOrderResponse activeOrder = orderService.getActiveOrderById(principal.userId(), orderId);
        return ResponseEntity.ok(activeOrder);
    }
}

