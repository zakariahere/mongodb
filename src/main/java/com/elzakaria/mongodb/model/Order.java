package com.elzakaria.mongodb.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * The AGGREGATE ROOT of our domain and the best illustration of embed-vs-reference:
 *
 *   EMBEDDED:   List<OrderLine> lines  -> owned by the order, read together,
 *               bounded in size. Reading an order returns everything in ONE
 *               query. This is the whole point of the document model.
 *   EMBEDDED:   Address shippingAddress -> the address AS SHIPPED, frozen here.
 *   REFERENCED: customerId (String)   -> the customer is shared and independent,
 *               so we store only its id, like a non-enforced foreign key.
 *
 * @CompoundIndex builds a multi-field index (customerId, then createdAt desc) —
 * exactly what "show me this customer's orders, newest first" needs.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "orders")
@CompoundIndex(name = "customer_recent_idx", def = "{'customerId': 1, 'createdAt': -1}")
public class Order {

    @Id
    private String id;

    /** Reference to Customer.id. Mongo will NOT enforce this exists — integrity
     *  is your application's job (a real contrast with SQL FKs). */
    @Indexed
    private String customerId;

    /** Embedded value object — snapshot of where it shipped. */
    private Address shippingAddress;

    /** Embedded collection — the heart of the aggregate. */
    private List<OrderLine> lines;

    @Indexed
    private OrderStatus status;

    private Instant createdAt;

    /** Denormalized total so listing/reporting doesn't recompute every time.
     *  Kept in sync by the app when lines change. Decimal128 for exact money. */
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal total;
}
