package com.elzakaria.mongodb.repository;

import com.elzakaria.mongodb.model.Order;
import com.elzakaria.mongodb.model.OrderStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface OrderRepository extends MongoRepository<Order, String> {

    /** Uses the compound index (customerId, createdAt) we declared on Order. */
    List<Order> findByCustomerIdOrderByCreatedAtDesc(String customerId);

    List<Order> findByStatus(OrderStatus status);

    /** Query into the EMBEDDED array by a nested field. Spring turns
     *  Lines_ProductId into the dotted path "lines.productId" -> matches any
     *  order that contains a line for this product. Dot notation reaching into
     *  embedded documents/arrays is a MongoDB superpower. */
    List<Order> findByLinesProductId(String productId);
}
