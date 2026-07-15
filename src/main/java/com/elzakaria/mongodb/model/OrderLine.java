package com.elzakaria.mongodb.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;

/**
 * EMBEDDED — one line of an order. No @Document, no @Id. Lives inside the
 * Order document as an element of a List.
 *
 * KEY MODELING IDEA — the snapshot:
 * We store productId (a REFERENCE) so we know which product it was, BUT we
 * also COPY name and unitPrice into the line. This intentional duplication is
 * idiomatic in MongoDB: an order is a historical record. If the product's
 * price changes next week, this order must still show what the customer
 * actually paid. Copying also means rendering the order needs zero lookups
 * against the products collection.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderLine {

    /** Reference to the Product's id — a "foreign key", but not enforced. */
    private String productId;

    /** Snapshot fields — frozen at purchase time. */
    private String productName;

    /** Decimal128 so unitPrice * quantity works in the aggregation pipeline. */
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal unitPrice;

    private int quantity;

    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
