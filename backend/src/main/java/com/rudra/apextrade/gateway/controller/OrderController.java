package com.rudra.apextrade.gateway.controller;

import com.rudra.apextrade.core.order.OrderType;
import com.rudra.apextrade.gateway.dto.OrderRequest;
import com.rudra.apextrade.gateway.dto.OrderResponse;
import com.rudra.apextrade.gateway.service.OrderService;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<OrderResponse> placeOrder(@RequestBody OrderRequest request) {
        if (request.userId() <= 0) {
            return ResponseEntity.badRequest().body(OrderResponse.rejected("Invalid userId"));
        }
        if (request.side() == null) {
            return ResponseEntity.badRequest().body(OrderResponse.rejected("Side must be BUY or SELL"));
        }
        if (request.quantity() <= 0) {
            return ResponseEntity.badRequest().body(OrderResponse.rejected("Quantity must be greater than zero"));
        }
        if (request.orderType() == null) {
            return ResponseEntity.badRequest().body(OrderResponse.rejected("OrderType must be LIMIT or MARKET"));
        }
        if (request.orderType() == OrderType.LIMIT && request.price() <= 0) {
            return ResponseEntity.badRequest().body(OrderResponse.rejected("Price must be greater than zero for LIMIT orders"));
        }

        OrderResponse response = orderService.placeOrder(request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{orderId}")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable long orderId) {
        if (orderId <= 0) {
            return ResponseEntity.badRequest().body(OrderResponse.rejected("Invalid orderId"));
        }

        OrderResponse response = orderService.cancelOrder(orderId);
        return ResponseEntity.ok(response);
    }
    
}
