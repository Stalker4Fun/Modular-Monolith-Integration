# Modular Monolith Integration with React Frontend & Supabase

A robust, enterprise-style modular monolith web application built with **Java Spring Boot**, **React (Vite)**, and **Supabase (PostgreSQL)**.

This project demonstrates three critical integration patterns:
1. **Module-to-Module In-Process Integration**: Strict package boundary enforcement between the `Order` and `Inventory` modules running in the same JVM process without inter-service network overhead.
2. **Service-to-Database Integration**: Direct Spring Data JPA integration with a shared PostgreSQL database hosted on Supabase, keeping database credentials out of the codebase via environment variables.
3. **Client-to-Service REST Integration**: External client integration via JSON-over-HTTP REST APIs with CORS support for the React Vite frontend dev server.

---

## 🏛️ Architecture & Package Boundary

```
backend/src/main/java/edu/cit/valendez/
├── ModularMonolithApplication.java       # Root @SpringBootApplication scanning both modules
├── config/
│   └── CorsConfig.java                  # Enables CORS for http://localhost:5173
│
├── inventory/                           # === INVENTORY MODULE ===
│   ├── InventoryItem.java               # JPA entity mapped to 'inventory' table
│   ├── InventoryRepository.java         # Package-private Spring Data JPA repository
│   ├── InventoryItemDto.java            # Public DTO exposed across module boundary
│   ├── ReservationResult.java           # Public DTO for reservation status & snapshot
│   ├── InventoryService.java            # PUBLIC interface (the module's contract)
│   ├── InventoryServiceImpl.java        # PACKAGE-PRIVATE service (hidden implementation)
│   ├── InventoryDataInitializer.java    # Seed runner for initial warehouse stock
│   └── InventoryController.java         # REST endpoints (GET /api/inventory)
│
└── shop/                                # === ORDER MODULE ===
    ├── Order.java                       # JPA entity mapped to 'orders' table
    ├── OrderRepository.java             # Spring Data JPA repository
    ├── OrderRequest.java                # DTO for incoming order requests
    ├── OrderResponse.java               # DTO for order placement responses
    ├── OrderService.java                # Injects InventoryService interface (constructor injection)
    └── OrderController.java             # REST endpoints (POST /api/orders, GET /api/orders)
```

### Module Boundary Enforcement:
- `InventoryServiceImpl` is declared with **package-private visibility** (`class InventoryServiceImpl implements InventoryService`).
- Classes in `edu.cit.valendez.shop` (e.g. `OrderService`) **cannot import or reference** `InventoryServiceImpl` directly. The Java compiler strictly enforces this encapsulation boundary at compile time.
- Spring IoC automatically wires the `InventoryServiceImpl` bean into `OrderService` via the public `InventoryService` interface using constructor injection.

---

## 🛠️ Tech Stack

- **Backend**: Java 21+ / Java 26, Spring Boot 4.1.0, Spring Data JPA, Hibernate, HikariCP, PostgreSQL Driver, Jakarta Validation
- **Frontend**: React 18, Vite 5, Vanilla CSS3 (modern responsive dashboard)
- **Database**: PostgreSQL (Supabase)
- **Testing**: JUnit 5, Mockito, Spring Test, MockMvc, H2 in PostgreSQL mode

---

## 📦 Supabase Database Setup

### Step 1: Create a Supabase Project
1. Log in to [Supabase](https://supabase.com/).
2. Create a new project (e.g., `modular-monolith-db`).
3. Note your database password and project connection settings.

### Step 2: Execute the Database Script
1. Navigate to the **SQL Editor** in your Supabase dashboard.
2. Open or copy the contents of [`schema.sql`](./schema.sql) located at the root of this repository.
3. Click **Run** to execute the script.

```sql
-- 1. Create inventory table
CREATE TABLE IF NOT EXISTS inventory (
    product_id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    stock INT NOT NULL CHECK (stock >= 0)
);

-- 2. Create orders table
CREATE TABLE IF NOT EXISTS orders (
    order_id BIGSERIAL PRIMARY KEY,
    product_id VARCHAR(50) NOT NULL,
    quantity INT NOT NULL CHECK (quantity > 0),
    status VARCHAR(50) NOT NULL,
    reason VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 3. Seed initial inventory data
INSERT INTO inventory (product_id, name, stock) VALUES
    ('P100', 'Wireless Mouse', 25),
    ('P200', 'Mechanical Keyboard', 10),
    ('P300', 'USB-C Hub', 0)
ON CONFLICT (product_id) DO UPDATE 
SET 
    name = EXCLUDED.name,
    stock = EXCLUDED.stock;
```

### Step 3: Configure Environment Variables
Set the following environment variables in your terminal session before starting Spring Boot. **Never commit actual database passwords to Git.**

**Windows (PowerShell):**
```powershell
$env:SUPABASE_DB_URL="jdbc:postgresql://db.<your-project-ref>.supabase.co:5432/postgres"
$env:SUPABASE_DB_USERNAME="postgres"
$env:SUPABASE_DB_PASSWORD="<your-database-password>"
```

**Linux / macOS (Bash / Zsh):**
```bash
export SUPABASE_DB_URL="jdbc:postgresql://db.<your-project-ref>.supabase.co:5432/postgres"
export SUPABASE_DB_USERNAME="postgres"
export SUPABASE_DB_PASSWORD="<your-database-password>"
```

*(Note: If no Supabase environment variables are provided, the application automatically falls back to an embedded in-memory H2 database in PostgreSQL mode, pre-seeded with the same items for local development and CI testing).*

---

## 🚀 Running the Application

### 1. Run Automated Tests
```powershell
cd backend
.\mvnw.cmd test
```
All 13 tests (unit tests, integration tests, and reflection-based architectural boundary tests) will execute and pass.

### 2. Run Backend Server (Spring Boot)
```powershell
cd backend
.\mvnw.cmd spring-boot:run
```
The backend starts on `http://localhost:8080`.

### 3. Run Frontend Dev Server (React + Vite)
In a separate terminal:
```powershell
cd frontend
npm install
npm run dev
```
Open `http://localhost:5173` in your browser.

---

## 📡 REST API Documentation

### 1. Place Order
- **Endpoint**: `POST /api/orders`
- **Headers**: `Content-Type: application/json`, `Accept: application/json`
- **Request Body**:
  ```json
  {
    "productId": "P100",
    "quantity": 2
  }
  ```

#### Confirmed Response (`200 OK`):
```json
{
  "orderId": 1,
  "status": "CONFIRMED",
  "reason": null,
  "inventory": {
    "productId": "P100",
    "name": "Wireless Mouse",
    "stock": 23
  }
}
```

#### Rejected Response (`200 OK`):
```json
{
  "orderId": 2,
  "status": "REJECTED",
  "reason": "Insufficient stock: requested 1, available 0",
  "inventory": {
    "productId": "P300",
    "name": "USB-C Hub",
    "stock": 0
  }
}
```

### 2. Get Live Inventory
- **Endpoint**: `GET /api/inventory`
- **Response**: List of all inventory items with remaining stock.

### 3. Get Order History
- **Endpoint**: `GET /api/orders`
- **Response**: Audit log of all submitted orders from the `orders` table.

---

## 🌐 Network Tab Evidence

Below is the verified Network tab evidence for both order paths captured during test execution.

### 1. Confirmed Order Evidence
![Confirmed Order Network Evidence]("C:\Users\L23Y19W41\Desktop\sc1.png")
| Parameter | Value |
|---|---|
| **Request URL** | `http://localhost:8080/api/orders` (or `http://localhost:5173/api/orders`) |
| **Request Method** | `POST` |
| **Status Code** | `200 OK` |
| **Request Headers** | `Content-Type: application/json`, `Accept: application/json` |
| **Request Payload** | `{"productId": "P100", "quantity": 2}` |

**Response Payload:**
```json
{
  "orderId": 1,
  "status": "CONFIRMED",
  "reason": null,
  "inventory": {
    "productId": "P100",
    "name": "Wireless Mouse",
    "stock": 23
  }
}
```
*Result: Stock for `P100` decremented from 25 to 23 in the database, order persisted as `CONFIRMED`.*

---

### 2. Rejected Order Evidence (Zero Stock)

| Parameter | Value |
|---|---|
| **Request URL** | `http://localhost:8080/api/orders` (or `http://localhost:5173/api/orders`) |
| **Request Method** | `POST` |
| **Status Code** | `200 OK` |
| **Request Headers** | `Content-Type: application/json`, `Accept: application/json` |
| **Request Payload** | `{"productId": "P300", "quantity": 1}` |

**Response Payload:**
```json
{
  "orderId": 2,
  "status": "REJECTED",
  "reason": "Insufficient stock: requested 1, available 0",
  "inventory": {
    "productId": "P300",
    "name": "USB-C Hub",
    "stock": 0
  }
}
```
*Result: Stock for `P300` remained 0, order persisted in `orders` table as `REJECTED` with reason.*

---

### 3. Rejected Order Evidence (Exceeds Available Stock)
![Rejected Order Network Evidence]("C:\Users\L23Y19W41\Desktop\sc2.png")
| Parameter | Value |
|---|---|
| **Request URL** | `http://localhost:8080/api/orders` |
| **Request Method** | `POST` |
| **Status Code** | `200 OK` |
| **Request Payload** | `{"productId": "P200", "quantity": 50}` |

**Response Payload:**
```json
{
  "orderId": 3,
  "status": "REJECTED",
  "reason": "Insufficient stock: requested 50, available 10",
  "inventory": {
    "productId": "P200",
    "name": "Mechanical Keyboard",
    "stock": 10
  }
}
```
*Result: Stock for `P200` remained 10, order persisted in `orders` table as `REJECTED`.*

---

## 📝 Architectural Reflection (300–500 Words)

### 1. In-Process Integration vs. Separate Microservices Over a Network
Integrating the Order and Inventory modules in-process within a modular monolith yields tremendous architectural simplicity and operational efficiency that developers often take for granted. By running in the same JVM, communication occurs through standard Java method invocations rather than network I/O. We get lightning-fast sub-millisecond execution, zero serialization/deserialization overhead, compile-time type safety, and direct in-memory object passing for free. Crucially, in-process integration allows us to leverage local database ACID transactions: reserving inventory stock and writing the order audit record can occur atomically within a single database commit. 

If this boundary were split into separate microservices over HTTP or gRPC, all of these guarantees evaporate. We would need to introduce complex infrastructure to handle the fallacies of distributed computing: network latency, connection timeouts, TLS overhead, circuit breakers (e.g., Resilience4j), retries with exponential backoff, service discovery, API gateways, and distributed tracing (e.g., Micrometer/Zipkin). Furthermore, we lose atomic ACID transactions across services. We would have to implement distributed consensus or the Saga pattern (orchestrated or choreographed) alongside compensating transactions to ensure eventual consistency if an order confirmation or payment fails downstream.

### 2. Why Package-Private Visibility on InventoryServiceImpl Matters
Package-private visibility (`class InventoryServiceImpl implements InventoryService`) enforces the modular boundary at the Java compiler level. By omitting the `public` modifier, `InventoryServiceImpl` is strictly invisible outside the `edu.cit.valendez.inventory` package. The `Order` module (`edu.cit.valendez.shop`) is physically prevented from importing or directly instantiating the concrete implementation. It can interact solely through the published, public `InventoryService` interface, injected by Spring's constructor injection.

If `InventoryServiceImpl` were made public, the encapsulation boundary would immediately degrade. Developers could inadvertently instantiate the class directly with `new`, bypass Spring's transaction management and proxy mechanisms, or depend on internal implementation details and helper methods not specified in the public interface. This would create tight coupling between the modules, making it impossible to refactor, replace, or extract the Inventory module in the future without breaking the Order module. Package-private visibility enforces the Dependency Inversion Principle (DIP) and ensures loose coupling by design.

### 3. When to Extract Inventory into Its Own Microservice and Necessary Changes
Inventory should be extracted into an independent microservice only when clear technical or organizational drivers demand it. Key triggers include: (1) **Asymmetric scaling requirements**, where inventory queries (read traffic from catalog browsing) outpace order placement by orders of magnitude and require independent auto-scaling or caching tiers; (2) **Team autonomy**, when separate dedicated teams manage warehouse logistics and e-commerce checkout; or (3) **Independent deployment and lifecycle cadence**, where inventory updates must deploy without redeploying the shop module.

To execute this extraction, our code would require three fundamental changes:
1. **Transport Layer Adaptation**: In `OrderService`, the direct in-process interface call would be replaced by an HTTP REST client (such as Spring's `RestClient` or `WebClient`) or gRPC stub targeting the standalone Inventory microservice URL.
2. **Data & Schema Decoupling**: The shared database would be split into dedicated databases (`inventory_db` and `order_db`), eliminating shared tables and direct foreign keys.
3. **Event-Driven Resilience & Saga Orchestration**: Synchronous reservation would transition to asynchronous messaging (using Apache Kafka or RabbitMQ) via the Transactional Outbox pattern, with compensating events handling stock rollbacks upon order cancellation.
