# Order / Inventory Modular Monolith

Spring Boot and React implementation of the Lab 1 Order/Inventory modular monolith, extended with multi-item orders, cancellation/restocking, live read APIs, and in-process domain events.

## Module boundaries

```
edu.cit.valendez
+-- shop/          Order API, order persistence, and transaction orchestration
+-- inventory/     Inventory API and the public InventoryService contract
+-- events/        Shared domain event payloads only
`-- notification/  Notification event listeners and notification persistence
```

`InventoryServiceImpl` and `NotificationServiceImpl` are package-private. The shop module calls only the public `InventoryService` interface. The notification module imports only event payloads from `events`; it never calls either the Order or Inventory service. Neither Order nor Inventory imports the notification module.

## API

### Place a multi-item order

`POST /api/orders`

```json
{
  "items": [
    { "productId": "P100", "quantity": 2 },
    { "productId": "P200", "quantity": 3 }
  ]
}
```

The response contains `orderId`, `status`, `reason`, an `items` list with a per-line outcome, and a current `inventory` list. Lab 1's single-item request body (`productId`, `quantity`) remains accepted for backwards compatibility.

Before reserving stock, `OrderService` validates every requested product, including the combined quantity for duplicate product lines. A stock failure produces a persisted `REJECTED` order and performs no reservation. If an unexpected reservation failure occurs after a reservation starts, the `@Transactional` method throws and rolls back the order, all reservations, and event-log writes together.

### Cancel an order

`POST /api/orders/{orderId}/cancel`

Only `CONFIRMED` orders can be cancelled. Each order line is restocked in the same transaction, then the order is marked `CANCELLED`. Missing IDs return `404`; an already-cancelled (or otherwise non-confirmed) order returns `409`.

### Read APIs

| Endpoint | Description |
| --- | --- |
| `GET /api/inventory` | Current stock for every product. |
| `GET /api/orders` | Order history, statuses, reasons, and line items. |
| `GET /api/notifications` | Persisted order, rejection, cancellation, and low-stock activity. |

## Events and low-stock rule

`OrderService` publishes `OrderPlacedEvent` for confirmed orders and `OrderRejectedEvent` for rejected ones through Spring's `ApplicationEventPublisher`. `InventoryServiceImpl` publishes `LowStockEvent` after a successful reservation leaves stock below the threshold of `5`. Cancellation also emits `OrderCancelledEvent`.

`NotificationEventListener` uses synchronous `@EventListener` methods intentionally. No `@Async` is used: for this lab, keeping notification writes in the calling transaction makes the demonstration deterministic and ensures a failed transaction cannot leave a notification for an order that did not commit.

## Supabase database setup

1. Create a Supabase project and wait until its database is ready.
2. In the Supabase dashboard, open **SQL Editor**, create a new query, paste the contents of [schema.sql](schema.sql), and run it.
3. In **Connect**, copy the PostgreSQL connection details. Use the JDBC URL form shown below; the hostname normally contains your project reference.
4. Create `backend/.env` from the following template, replacing only the placeholders. Keep this file local because it contains a password.

```dotenv
SUPABASE_DB_URL=jdbc:postgresql://db.<project-ref>.supabase.co:5432/postgres
SUPABASE_DB_USERNAME=postgres
SUPABASE_DB_PASSWORD=<your-supabase-database-password>
```

The backend loads `backend/.env` at startup. Alternatively, set the same values as environment variables:

```powershell
$env:SUPABASE_DB_URL="jdbc:postgresql://db.<project-ref>.supabase.co:5432/postgres"
$env:SUPABASE_DB_USERNAME="postgres"
$env:SUPABASE_DB_PASSWORD="<password>"
```

The script creates and seeds `inventory`, `orders`, `order_items`, and `notifications`. It intentionally drops and recreates those four lab tables, so do not run it against data you need to keep.

## Run and verify

```powershell
cd backend
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

In another terminal:

```powershell
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`. The cart submits the required multi-item JSON payload, the inventory table refreshes after orders and cancellations, low-stock rows are highlighted, and the order/notification feeds show the persisted results.

The test suite includes unit/controller tests plus H2 integration tests for confirmed multi-item orders, rejected all-or-nothing orders, cancellation restocking, and order/low-stock notifications.

## Network-tab evidence

The following HTTP evidence was recorded against a fresh seeded database (`P100=25`, `P200=10`, `P300=0`). In Chrome/Edge DevTools, filter the Network tab by `Fetch/XHR`, perform the same actions in the frontend, and retain the listed request and response entries.

### 1. Confirmed multi-item order

| Network field | Recorded value |
| --- | --- |
| Request | `POST http://localhost:8080/api/orders` |
| Status | `200 OK` |
| Request payload | `{"items":[{"productId":"P100","quantity":2},{"productId":"P200","quantity":6}]}` |
| Response result | `orderId: 1`, `status: CONFIRMED`; both item outcomes are `CONFIRMED` |
| Inventory returned | `P100: 23`, `P200: 4`, `P300: 0` |

```json
{
  "orderId": 1,
  "status": "CONFIRMED",
  "reason": null,
  "items": [
    { "productId": "P100", "quantity": 2, "outcome": "CONFIRMED" },
    { "productId": "P200", "quantity": 6, "outcome": "CONFIRMED" }
  ]
}
```

### 2. Rejected multi-item order with no partial reservation

| Network field | Recorded value |
| --- | --- |
| Request | `POST http://localhost:8080/api/orders` |
| Status | `200 OK` |
| Request payload | `{"items":[{"productId":"P100","quantity":1},{"productId":"P300","quantity":1}]}` |
| Response result | `orderId: 2`, `status: REJECTED` because P300 has zero stock |
| Follow-up `GET /api/inventory` | P100 remains `23`, proving its valid line was not partially reserved |

```json
{
  "status": "REJECTED",
  "reason": "Insufficient stock for USB-C Hub (P300): requested 1, available 0",
  "items": [
    { "productId": "P100", "quantity": 1, "outcome": "REJECTED: Order rolled back due to failure on other item" },
    { "productId": "P300", "quantity": 1, "outcome": "REJECTED: Insufficient stock for USB-C Hub (P300): requested 1, available 0" }
  ]
}
```

### 3. Cancellation and restock

| Network field | Recorded value |
| --- | --- |
| Request | `POST http://localhost:8080/api/orders/1/cancel` |
| Status | `200 OK` |
| Response result | `status: CANCELLED`; P100 and P200 line outcomes are `RESTOCKED` |
| Follow-up `GET /api/inventory` | P100 returns to `25`; P200 returns to `10` |

This captures the restock after the confirmed order, while the rejected order did not need compensation because it never reserved inventory.

### 4. Notification activity feed

| Network field | Recorded value |
| --- | --- |
| Request | `GET http://localhost:8080/api/notifications` |
| Status | `200 OK` |
| Evidence in response | `Order O1 confirmed`, `Order O2 rejected`, and `Low-stock alert: Reorder needed for Mechanical Keyboard (P200) - only 4 units remaining` |

The same feed also contains the cancellation message for Order O1. These entries are persisted by the Notification module's synchronous event listeners, rather than by direct calls from OrderService.

## Architectural reflection

Multi-item ordering remains atomic here because the order orchestration and every `InventoryService.reserve()` call run inside the same Spring-managed `@Transactional` method and use one database transaction. The service validates all requested stock before reserving anything, including the total where a product appears on more than one line. If a reserve unexpectedly fails after another one succeeded, the method throws a runtime exception; Spring rolls back the order row, its line items, inventory updates, and synchronous notification writes together. This is unusually convenient because both modules share a process and a database transaction manager. Across a network, an HTTP call cannot join that local transaction. Splitting Inventory would require a saga: persist an order in a pending state, send reservation commands, track replies, and issue compensating release commands for inventory already reserved if a later reservation fails. It would also require timeouts, retries, idempotency keys, and a durable outbox so crashes do not lose commands.

Publishing an application event means `OrderService` knows only the event contract, not the Notification service, repository, or notification implementation. Notification can add its own handler, storage format, or presentation without changing order placement. The dependency direction is therefore much looser than a direct service call. If Notification became remote, Spring in-memory events would not cross the process boundary. The producer would write an event to an outbox in the order transaction, and a relay would publish it to a broker such as RabbitMQ or Kafka. The consumer would need at-least-once delivery handling, retries and dead-letter processing, an event identifier with idempotent persistence, schema/version compatibility, and monitoring. Exactly-once end-to-end delivery is generally impractical, so duplicate-safe consumers are essential.

If exactly one module had to be extracted first, I would choose Notification. It is already event-driven, has no synchronous business decision in the order path, and can tolerate eventual consistency better than Inventory. Inventory is part of the order's correctness boundary, so extracting it first would immediately require the reservation saga described above. Extracting Notification would move its table and listener into a new service, replace the in-process publisher with transactional-outbox publication, and have the new service consume the same versioned event payloads from a broker. Order and Inventory code would no longer import the local event listener or share Notification's database; only the broker-facing event contract would remain shared.
