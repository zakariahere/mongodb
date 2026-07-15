package com.elzakaria.mongodb.repository;

import com.elzakaria.mongodb.model.Customer;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/**
 * MongoRepository<T, ID> is the Mongo analogue of JpaRepository. You get
 * save/findById/findAll/delete for free. ID is String because Customer.id
 * is a String.
 */
public interface CustomerRepository extends MongoRepository<Customer, String> {

    /** Derived query: Spring parses the method name and builds the query
     *  document { email: ?0 } for you. No @Query needed. */
    Optional<Customer> findByEmail(String email);
}
