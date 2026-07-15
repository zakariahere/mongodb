# Spring Boot + MongoDB — a learning project

A small, heavily-commented Spring Boot application I built to learn MongoDB the way I actually
work: a realistic little commerce domain (customers, products, orders), modelled the document way,
then queried with everything from derived repository methods up to full aggregation pipelines.

The code is the lesson. Every model, repository, and pipeline carries Javadoc that explains **why**
it's shaped the way it is — embed vs. reference, the money-storage gotcha, the `$lookup` type trap,
stage ordering as a cost decision. This README is the map; the source comments are the territory.

> 📝 There's a companion write-up series on my blog:
> **Part 1 — the document model** and **[Part 2 — the aggregation pipeline](https://blog.zakaria.lu/mongodb-part-2-the-aggregation-pipeline-stage-by-stage)**.

---

## Tech stack

| Piece | Version / choice | Notes |
|---|---|---|
| Language | **Java 25** | Uses the classic `public static void main` on purpose (see gotcha below) |
| Framework | **Spring Boot 4.1.0** | `spring-boot-starter-data-mongodb` |
| Data | **Spring Data MongoDB** | Repositories **and** `MongoTemplate` for aggregation |
| Database | **MongoDB** (`mongo:latest`) | Spun up automatically via Docker Compose |
| Boilerplate | **Lombok** | `@Data`, `@Builder`, `@Slf4j`, `@RequiredArgsConstructor` |
| Build | **Maven** (wrapper included) | `./mvnw` — no local Maven needed |
| Dev loop | **spring-boot-devtools** + **docker-compose** integration | App starts Mongo for you |

---

## Quick start

You need **JDK 25** and **Docker** (for the MongoDB container). That's it — the Maven wrapper and the
Docker Compose integration handle the rest.

```bash
# 1. Clone
git clone https://github.com/zakariahere/mongodb.git
cd mongodb

# 2. Run. Spring Boot's docker-compose support reads compose.yaml and starts
#    MongoDB automatically (lifecycle-management=start_only, so it's left running).
./mvnw spring-boot:run
```

On Windows PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

There's **no web server** — this is a `CommandLineRunner` demo. On boot it seeds data, runs every
query, and prints the results to the log, then the JVM stays up (devtools). Watch the console:

```
>>> Seeded 3 products; generated ObjectId example: 68a1...
>>> Seeded 4 orders across 2 customers
========== AGGREGATION PIPELINE LESSON ==========
 1. Revenue per product
      { "_id" : "4K Monitor", "revenue" : ... }
 ...
=================================================
```

If you'd rather manage Mongo yourself, start it with `docker compose up -d` and point
`spring.data.mongodb.uri` at it in `application.properties`.

---

## Domain model — embed vs. reference

The whole point of the document model is deciding **what to store together**. This project makes
that decision explicit:

```
Customer  (own collection)  ─── first-class entity, edited on its own, shared
Product   (own collection)  ─── first-class entity, has its own lifecycle

Order     (own collection)  ─── the AGGREGATE ROOT
  ├─ customerId : String      REFERENCE  → the customer lives its own life
  ├─ shippingAddress : Address EMBEDDED   → snapshot of where it shipped, frozen
  └─ lines : List<OrderLine>   EMBEDDED   → owned by the order, read together
       ├─ productId : String   REFERENCE  → which product it was
       └─ productName/unitPrice SNAPSHOT  → copied at purchase time (historical record)
```

| Decision | Applied to | Why |
|---|---|---|
| **Embed** | `Order.lines`, `Order.shippingAddress` | Owned, bounded, always read with the order → one query returns everything |
| **Reference** | `Order.customerId`, `OrderLine.productId` | The target is shared and independent → store only the id |
| **Snapshot** | `OrderLine.productName`, `OrderLine.unitPrice` | An order is a historical record; it must show what was actually paid, even if the product's price changes later |

MongoDB does **not** enforce these references — there are no foreign keys. Referential integrity is
the application's job. That's a deliberate contrast with SQL, called out throughout the code.

---

## Project structure

```
src/main/java/com/elzakaria/mongodb/
├─ MongodbApplication.java        # entry point (static main — see gotcha)
├─ model/
│  ├─ Customer.java               # @Document, unique index on email
│  ├─ Product.java                # tags (List), attributes (Map), Decimal128 price
│  ├─ Order.java                  # aggregate root, @CompoundIndex, Decimal128 total
│  ├─ OrderLine.java              # EMBEDDED — no @Document, snapshot fields
│  ├─ Address.java                # EMBEDDED value object
│  └─ OrderStatus.java            # enum: NEW, PAID, SHIPPED, DELIVERED, CANCELLED
├─ repository/
│  ├─ ProductRepository.java      # derived queries, array queries, raw @Query
│  ├─ CustomerRepository.java
│  └─ OrderRepository.java        # nested-path & compound-index-backed queries
└─ bootstrap/
   ├─ DataSeeder.java             # CommandLineRunner: seeds + recaps basic queries
   └─ AggregationDemo.java        # the aggregation pipeline lesson (5 pipelines)
```

---

## Lesson 1 — querying with repositories

`ProductRepository` / `OrderRepository` show the ladder from "derived method names" down to "raw
MongoDB query language", so you learn when each rung is appropriate:

```java
Optional<Product> findBySku(String sku);              // exact match
List<Product> findByPriceGreaterThan(BigDecimal p);   // → { price: { $gt: ?0 } }
List<Product> findByTagsContaining(String tag);       // querying INTO an array
List<Order>   findByLinesProductId(String productId); // dotted path "lines.productId"
List<Order>   findByCustomerIdOrderByCreatedAtDesc(String id); // uses compound index

@Query("{ 'price': { $gte: ?0, $lte: ?1 }, 'tags': { $in: ?2 } }")   // raw query doc
List<Product> search(BigDecimal min, BigDecimal max, List<String> tags);
```

Highlights: arrays and embedded documents are **queryable via dot notation** (`lines.productId`),
and when a derived method name gets awkward you drop to a raw `@Query` — the actual query language
you'd type in `mongosh`.

---

## Lesson 2 — the aggregation pipeline

Repositories can't express `GROUP BY`, `AVG`, or `JOIN`, so `AggregationDemo` drops to
`MongoTemplate`. A pipeline is an **assembly line**: documents enter, each stage reshapes the
stream, and the result falls out the end. Five pipelines, each earning a stage:

| # | Pipeline | Stages exercised | SQL analogue |
|---|---|---|---|
| 1 | Revenue per product | `$unwind` → `$project` → `$group` → `$sort` | `SUM(unitPrice*qty) GROUP BY product` |
| 2 | Units sold per product | `$unwind` → `$group` (on nested path) → `$sort` | `SUM(quantity) GROUP BY product` |
| 3 | Orders & AOV by status | `$group` (multi-accumulator) → `$sort` | `COUNT(*), AVG(total), SUM(total) GROUP BY status` |
| 4 | Top customers by spend | `$group` → `$sort` → `$lookup` → `$unwind` → `$project` | `JOIN customers … GROUP BY name` |
| 5 | Revenue, PAID only | `$match` → `$unwind` → `$project` → `$group` → `$sort` | `… WHERE status='PAID' GROUP BY …` |

Key ideas the pipelines teach:

- **`$unwind` explodes embedded arrays.** One order with three lines becomes three documents, one
  per line — the price you pay back for the convenience of embedding, so you can group across lines.
- **After `$group`, the key is always `_id`.** Group by `customerId` and the value lands in `_id`,
  not `customerId`. Miss this and your next stage silently references nothing.
- **`$lookup` is MongoDB's JOIN** — and it always produces an **array**, even for a 1:1 match, so you
  almost always `$unwind` it right after.
- **Stage order is a cost decision.** Pipeline 4 groups *first*, then looks up (join 4 rows, not
  thousands). Pipeline 5 puts `$match` *first* so it filters on an index before any real work.

---

## Gotchas worth the price of admission

These are the bugs I actually hit. Each is documented at its source.

### 1. `$lookup` matches on exact BSON type — String ≠ ObjectId
This is why the customers are seeded with explicit ids (`"alice"`, `"bob"`) instead of generated
ObjectIds. `Order.customerId` is a `String`, so it's stored as a BSON string. But an `@Id` that
looks like a 24-char hex string gets stored as an **ObjectId**. A `$lookup` across that type
boundary matches **nothing** — no error, just an empty array every time. Keep both sides the same
BSON type. *(See `DataSeeder.java`.)*

### 2. Money must be Decimal128, not a String
On Spring Data MongoDB 5.0 / Spring Boot 4, `BigDecimal` now defaults to being stored as a
**String**, which breaks numeric range queries (`findByPriceGreaterThan`) and aggregation math.
`@Field(targetType = FieldType.DECIMAL128)` forces exact-decimal numeric storage. Keep money as
`BigDecimal` in Java, `Decimal128` in Mongo. *(See `Product.java`, `Order.java`, `OrderLine.java`.)*

### 3. Static `main` for devtools
The Java 25 Initializr generates the new instance `void main`, but `spring-boot-devtools`'
`Restarter` can't locate a non-static main and dies with *"Unable to find the main class to
restart."* Classic `public static void main` fixes it. *(See `MongodbApplication.java`.)*

### 4. Aggregation lives on `MongoTemplate`, not repositories
Repositories cover the CRUD 90%. `GROUP BY` / `AVG` / `JOIN` have no derived-method-name form, so
aggregation is where you drop to the lower-level `MongoTemplate` API.

---

## Indexes declared

| Collection | Index | Purpose |
|---|---|---|
| `customers` | `email` (unique) | The closest thing to a `UNIQUE` constraint |
| `products` | `sku` (unique), `name` | Fast business-key lookup; frequent filtering by name |
| `orders` | `customerId`, `status`, `(customerId, createdAt desc)` compound | "This customer's orders, newest first" reads straight off the compound index |

---

## Running the data seeder again

`DataSeeder` is **idempotent for a learning project**: it wipes the `orders`, `products`, and
`customers` collections on every boot before re-seeding, so each run starts from a known state.
Don't point this at anything you care about. 🙂

---

## License

Personal learning project — no license, use freely as a reference.
