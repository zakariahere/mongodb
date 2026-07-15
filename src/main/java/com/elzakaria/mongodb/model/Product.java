package com.elzakaria.mongodb.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * A catalog product. Notice how much lives INSIDE one document that would be
 * multiple tables in SQL:
 *   - tags  -> a List<String> (would be a product_tags join table)
 *   - attributes -> a Map (would be an EAV table or many columns)
 * This is the document model's core value: related data that is read together
 * is stored together, no joins needed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "products")
public class Product {

    @Id
    private String id;

    /** Business key. Indexed + unique so lookups by sku are fast and no
     *  duplicates slip in. _id (ObjectId) is the technical key; sku is ours. */
    @Indexed(unique = true)
    private String sku;

    @Indexed // we filter by name-ish/category often, so index it
    private String name;

    /**
     * MONEY: never store money as double. BigDecimal is correct in Java.
     * GOTCHA (Spring Data MongoDB 5.0 / Spring Boot 4): BigDecimal now defaults
     * to being stored as a STRING, which would make numeric range queries
     * (findByPriceGreaterThan) and aggregation math wrong. @Field(targetType =
     * DECIMAL128) forces exact-decimal numeric storage. Keep money as BigDecimal
     * end-to-end and store it as Decimal128.
     */
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal price;

    private int stock;

    /** Arrays are first-class and QUERYABLE. A query {tags: "keyboard"}
     *  matches any product whose tags array contains "keyboard". */
    private List<String> tags;

    /** Flexible key/value attributes — the schema-per-document freedom.
     *  A keyboard has "switch"; a monitor would have "resolution". */
    private Map<String, String> attributes;
}
