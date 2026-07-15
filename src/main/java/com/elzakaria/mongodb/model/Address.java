package com.elzakaria.mongodb.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * EMBEDDED document — this class has NO @Document annotation and NO @Id.
 * It never lives in its own collection; it is stored *inside* the document
 * that owns it (here, inside an Order). In relational terms this would be a
 * separate `address` table joined by FK. In MongoDB it's just nested fields.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Address {
    private String line1;
    private String city;
    private String country;
    private String postalCode;
}
