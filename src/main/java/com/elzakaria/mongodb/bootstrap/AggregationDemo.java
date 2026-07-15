package com.elzakaria.mongodb.bootstrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.stereotype.Component;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

/**
 * The aggregation pipeline lesson. Each method is one pipeline, with the
 * equivalent SQL and the raw MongoDB stages documented.
 *
 * Uses MongoTemplate (not a repository) because aggregation is where you drop
 * to the lower-level API — repositories can't express this.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AggregationDemo {

    private final MongoTemplate mongo;

    public void report() {
        log.info("========== AGGREGATION PIPELINE LESSON ==========");
        revenuePerProduct();
        unitsSoldPerProduct();
        ordersAndAovByStatus();
        topCustomersBySpend();
        revenueForPaidOrdersOnly();
        log.info("=================================================");
    }

    /**
     * 1. Revenue per product.
     *
     * SQL: SELECT productName, SUM(unitPrice*quantity) FROM lines GROUP BY productName
     *
     * Mongo:
     *   [ { $unwind: "$lines" },
     *     { $project: { product: "$lines.productName",
     *                   revenue: { $multiply: ["$lines.unitPrice", "$lines.quantity"] } } },
     *     { $group: { _id: "$product", revenue: { $sum: "$revenue" } } },
     *     { $sort: { revenue: -1 } } ]
     *
     * $unwind is the star: each order holds an ARRAY of lines, so we explode one
     * order document into one document PER LINE before we can group across them.
     */
    private void revenuePerProduct() {
        Aggregation agg = newAggregation(
                unwind("lines"),
                project()
                        .and("lines.productName").as("product")
                        .andExpression("lines.unitPrice * lines.quantity").as("revenue"),
                group("product").sum("revenue").as("revenue"),
                sort(Sort.Direction.DESC, "revenue")
        );
        log(" 1. Revenue per product", agg);
    }

    /**
     * 2. Units sold per product — same shape, a different accumulator.
     * Shows you can group straight on a nested path ("lines.productName")
     * without a separate $project stage.
     *
     * SQL: SELECT productName, SUM(quantity) FROM lines GROUP BY productName
     */
    private void unitsSoldPerProduct() {
        Aggregation agg = newAggregation(
                unwind("lines"),
                group("lines.productName").sum("lines.quantity").as("units"),
                sort(Sort.Direction.DESC, "units")
        );
        log(" 2. Units sold per product", agg);
    }

    /**
     * 3. Orders + average order value by status.
     * NO $unwind here — we aggregate over whole orders, not their lines.
     * Multiple accumulators in one $group, like several aggregate functions
     * in one SQL SELECT.
     *
     * SQL: SELECT status, COUNT(*), AVG(total), SUM(total) FROM orders GROUP BY status
     */
    private void ordersAndAovByStatus() {
        Aggregation agg = newAggregation(
                group("status")
                        .count().as("orders")
                        .avg("total").as("avgOrderValue")
                        .sum("total").as("revenue"),
                sort(Sort.Direction.DESC, "revenue")
        );
        log(" 3. Orders & AOV by status", agg);
    }

    /**
     * 4. Top customers by spend — this one uses $lookup, the closest thing
     * MongoDB has to a JOIN.
     *
     * SQL: SELECT c.name, SUM(o.total), COUNT(*)
     *      FROM orders o JOIN customers c ON o.customerId = c._id
     *      GROUP BY c.name ORDER BY 2 DESC
     *
     * Mongo:
     *   [ { $group: { _id: "$customerId", spend: { $sum: "$total" }, orders: { $sum: 1 } } },
     *     { $sort:  { spend: -1 } },
     *     { $lookup:{ from: "customers", localField: "_id",
     *                 foreignField: "_id", as: "customer" } },
     *     { $unwind: "$customer" },
     *     { $project:{ spend: 1, orders: 1, name: "$customer.name" } } ]
     *
     * Two things to notice:
     *  - After $group the grouping key is ALWAYS called "_id" — that's why
     *    localField is "_id" here (it holds the customerId).
     *  - $lookup ALWAYS produces an ARRAY ("customer"), even for a 1:1 match,
     *    so you almost always $unwind it right after.
     *  - We group FIRST, then $lookup. Joining 4 orders is cheap; joining before
     *    grouping would look up the customer once per order. Stage order = cost.
     */
    private void topCustomersBySpend() {
        Aggregation agg = newAggregation(
                group("customerId")
                        .sum("total").as("spend")
                        .count().as("orders"),
                sort(Sort.Direction.DESC, "spend"),
                lookup("customers", "_id", "_id", "customer"),
                unwind("customer"),
                project("spend", "orders").and("customer.name").as("name")
        );
        log(" 4. Top customers by spend ($lookup)", agg);
    }

    /**
     * 5. Revenue from PAID orders only — demonstrates the #1 performance rule:
     * $match FIRST. It runs before $unwind/$group, can use the index on
     * `status`, and throws away documents before any real work happens.
     * The same $match placed last would produce the same answer, slowly.
     *
     * SQL: SELECT productName, SUM(...) FROM ... WHERE status='PAID' GROUP BY ...
     */
    private void revenueForPaidOrdersOnly() {
        Aggregation agg = newAggregation(
                match(org.springframework.data.mongodb.core.query.Criteria
                        .where("status").is("PAID")),          // <-- stage 1: filter early
                unwind("lines"),
                project()
                        .and("lines.productName").as("product")
                        .andExpression("lines.unitPrice * lines.quantity").as("revenue"),
                group("product").sum("revenue").as("revenue"),
                sort(Sort.Direction.DESC, "revenue")
        );
        log(" 5. Revenue per product, PAID only ($match first)", agg);
    }

    /** Run the pipeline against the `orders` collection and print each result row. */
    private void log(String title, Aggregation agg) {
        AggregationResults<Document> results = mongo.aggregate(agg, "orders", Document.class);
        log.info("{}", title);
        results.getMappedResults().forEach(doc -> log.info("      {}", doc.toJson()));
    }
}
