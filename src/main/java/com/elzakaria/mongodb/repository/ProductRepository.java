package com.elzakaria.mongodb.repository;

import com.elzakaria.mongodb.model.Product;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends MongoRepository<Product, String> {

    Optional<Product> findBySku(String sku);

    /** Derived query -> { price: { $gt: ?0 } }. Note "GreaterThan" becomes $gt. */
    List<Product> findByPriceGreaterThan(BigDecimal price);

    /** Querying INTO an array. Because tags is a List, matching a scalar means
     *  "array contains". Derived name findByTagsContaining -> { tags: ?0 }. */
    List<Product> findByTagsContaining(String tag);

    /**
     * When method names get awkward, drop to a raw query document with @Query.
     * This is the actual MongoDB query language you'd type in the shell.
     * ?0 is the first argument. Here: price within a range, any of given tags.
     */
    @Query("{ 'price': { $gte: ?0, $lte: ?1 }, 'tags': { $in: ?2 } }")
    List<Product> search(BigDecimal min, BigDecimal max, List<String> tags);
}
