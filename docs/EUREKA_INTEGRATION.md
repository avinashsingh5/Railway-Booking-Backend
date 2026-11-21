# Eureka Server Integration Guide

## 1. Overview
Netflix Eureka Server provides service discovery and dynamic registration for the microservices in the Railway Reservation System.

## 2. Port & Service Topology

| Service | Port | Role | Eureka Status |
|---|---|---|---|
| **eureka-server** | `8761` | Service Registry & Discovery Server | Registry Host (`/eureka/`) |
| **api-gateway** | `8080` | Edge Gateway & Routing | Eureka Client & Load Balancer (`lb://`) |
| **user-service** | `8081` | User Management & Auth | Eureka Client (`USER-SERVICE`) |
| **train-service** | `8082` | Train Inventory & Stops | Eureka Client (`TRAIN-SERVICE`) |
| **reservation-service** | `8083` | Bookings & Cancellations | Eureka Client (`RESERVATION-SERVICE`) |
| **payment-service** | `8084` | Payment Processing & Refunds | Eureka Client (`PAYMENT-SERVICE`) |

---

## 3. How It Works

### Eureka Server (`eureka-server` on Port 8761)
- Activated via `@EnableEurekaServer` on `EurekaServerApplication`.
- Configured with `register-with-eureka: false` and `fetch-registry: false` so it serves purely as the registry.
- Dashboard URL: `http://localhost:8761`

### Microservice Clients
- Each service includes `spring-cloud-starter-netflix-eureka-client` and `@EnableDiscoveryClient`.
- Each service connects to `${EUREKA_SERVER_URL:http://localhost:8761/eureka/}` and registers its service name (`spring.application.name`).

### API Gateway Dynamic Routing (`api-gateway`)
- The Gateway uses Spring Cloud LoadBalancer (`lb://<service-name>`) to dynamically resolve instances registered in Eureka:
  - `lb://user-service`
  - `lb://train-service`
  - `lb://reservation-service`
  - `lb://payment-service`
- Fallback/Override support: Environment variables (`USER_SERVICE_URL`, `TRAIN_SERVICE_URL`, etc.) are preserved, allowing direct URL overrides (e.g. for standalone testing or containers) without modifying configuration files.
- Swagger documentation routes on the Gateway (`/docs/<service>`) also resolve dynamically through `lb://<service-name>`.

---

## 4. Startup Order (Recommended)
1. **Start Eureka Server:** `eureka-server` (Port 8761)
2. **Start Downstream Services:** `user-service`, `train-service`, `reservation-service`, `payment-service`
3. **Start API Gateway:** `api-gateway` (Port 8080)
4. **Open Eureka Dashboard:** Navigate to `http://localhost:8761` to view all registered instances.

