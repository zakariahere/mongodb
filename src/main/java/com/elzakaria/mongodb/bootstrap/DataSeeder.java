package com.elzakaria.mongodb.bootstrap;

import com.elzakaria.mongodb.model.*;
import com.elzakaria.mongodb.repository.CustomerRepository;
import com.elzakaria.mongodb.repository.OrderRepository;
import com.elzakaria.mongodb.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Runs once at startup: seeds data, exercises the basic query APIs, then hands
 * off to AggregationDemo for the pipeline lesson.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final ProductRepository products;
    private final CustomerRepository customers;
    private final OrderRepository orders;
    private final AggregationDemo aggregationDemo;

    @Override
    public void run(String... args) {
        // Idempotent for a learning project: wipe collections each boot.
        orders.deleteAll();
        products.deleteAll();
        customers.deleteAll();

        // ---- 1. Customers ------------------------------------------------
        // NOTE: we assign EXPLICIT string ids ("alice") instead of letting Mongo
        // generate an ObjectId. Why? $lookup matches on EXACT TYPE. Order.customerId
        // is a plain String field, so it's stored as a BSON string. But an @Id that
        // looks like a 24-char hex string gets stored as an ObjectId. String != ObjectId,
        // so the $lookup would silently match NOTHING. Keeping both sides String makes
        // the join work. This type mismatch is one of the most common MongoDB bugs.
        Customer alice = customers.save(Customer.builder()
                .id("alice").name("Alice").email("alice@example.com").build());
        Customer bob = customers.save(Customer.builder()
                .id("bob").name("Bob").email("bob@example.com").build());

        // ---- 2. Products (these DO get generated ObjectIds) --------------
        Product keyboard = products.save(Product.builder()
                .sku("SKU-1001").name("Mechanical Keyboard")
                .price(new BigDecimal("129.99")).stock(50)
                .tags(List.of("peripherals", "keyboard"))
                .attributes(Map.of("switch", "brown", "layout", "ANSI"))
                .build());

        Product mouse = products.save(Product.builder()
                .sku("SKU-1002").name("Wireless Mouse")
                .price(new BigDecimal("59.99")).stock(200)
                .tags(List.of("peripherals", "mouse"))
                .attributes(Map.of("dpi", "16000"))
                .build());

        Product monitor = products.save(Product.builder()
                .sku("SKU-1003").name("4K Monitor")
                .price(new BigDecimal("399.00")).stock(20)
                .tags(List.of("display", "monitor"))
                .attributes(Map.of("resolution", "3840x2160", "size", "27in"))
                .build());

        log.info(">>> Seeded {} products; generated ObjectId example: {}",
                products.count(), keyboard.getId());

        // ---- 3. Orders: embed lines, reference the customer ---------------
        Instant now = Instant.now();
        saveOrder(alice, List.of(line(keyboard, 1), line(monitor, 2)),
                OrderStatus.PAID, now.minus(5, ChronoUnit.DAYS));
        saveOrder(alice, List.of(line(mouse, 3)),
                OrderStatus.SHIPPED, now.minus(2, ChronoUnit.DAYS));
        saveOrder(bob, List.of(line(monitor, 1), line(mouse, 1)),
                OrderStatus.PAID, now.minus(3, ChronoUnit.DAYS));
        saveOrder(bob, List.of(line(keyboard, 2)),
                OrderStatus.DELIVERED, now.minus(1, ChronoUnit.DAYS));

        log.info(">>> Seeded {} orders across {} customers", orders.count(), customers.count());

        // ---- 4. Derived + raw queries (recap of the last lesson) ----------
        log.info(">>> findBySku(SKU-1002): {}", products.findBySku("SKU-1002").orElseThrow().getName());
        log.info(">>> findByPriceGreaterThan(100): {}",
                products.findByPriceGreaterThan(new BigDecimal("100"))
                        .stream().map(Product::getName).toList());
        log.info(">>> findByTagsContaining('peripherals'): {}",
                products.findByTagsContaining("peripherals")
                        .stream().map(Product::getName).toList());
        log.info(">>> orders containing the monitor: {}",
                orders.findByLinesProductId(monitor.getId()).size());
        log.info(">>> Alice's orders (newest first): {}",
                orders.findByCustomerIdOrderByCreatedAtDesc("alice").size());

        // ---- 5. Hand off to the aggregation lesson ------------------------
        aggregationDemo.report();
    }

    /** Build an embedded line, snapshotting name + price at purchase time. */
    private OrderLine line(Product p, int qty) {
        return OrderLine.builder()
                .productId(p.getId())
                .productName(p.getName())
                .unitPrice(p.getPrice())
                .quantity(qty)
                .build();
    }

    private void saveOrder(Customer c, List<OrderLine> lines, OrderStatus status, Instant when) {
        BigDecimal total = lines.stream()
                .map(OrderLine::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        orders.save(Order.builder()
                .customerId(c.getId())
                .shippingAddress(new Address("1 Main St", "Casablanca", "MA", "20000"))
                .lines(lines)
                .status(status)
                .createdAt(when)
                .total(total)
                .build());
    }
}
