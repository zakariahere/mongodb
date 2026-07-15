package com.elzakaria.mongodb.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A first-class entity that exists independently and is edited on its own.
 * Orders will REFERENCE this by id rather than embedding it (an order should
 * not copy the whole customer, because the customer lives its own life).
 *
 * @Document(collection = "customers") maps this class to the "customers"
 * collection. Without the name, Spring derives it from the class name ("customer").
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "customers")
public class Customer {

    /** Maps to MongoDB's _id. String here means Mongo generates an ObjectId
     *  and stores its hex form; we let the DB own identity. */
    @Id
    private String id;

    private String name;

    /** unique=true builds a unique index — the closest thing to a UNIQUE
     *  constraint you get. Enforced by the index, not by a schema. */
    @Indexed(unique = true)
    private String email;
}
