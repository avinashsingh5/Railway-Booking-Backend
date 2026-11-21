# Bolt.new / Bolt AI Prompt: Railway Reservation System (Angular Frontend)

You are an expert Angular developer. Build a clean, modern, responsive, and beginner-friendly **Angular 18+ (Standalone Components, TypeScript, Tailwind CSS or clean CSS)** frontend for a Spring Boot Microservices-based Railway Reservation System.

All API requests go through the single **API Gateway** running at `http://localhost:8080`.

---

## 1. Core Architectural Constraints
- **Framework:** Angular 18+ with **Standalone Components** (No `NgModule`).
- **Styling:** Tailwind CSS or clean modern CSS. Clean navbar with user profile / role badge, responsive cards, and accessible forms.
- **Base URL:** Centralized in `src/environments/environment.ts` (`apiUrl: 'http://localhost:8080'`).
- **Auth Storage:** Store JWT token as string `'token'` and user object as JSON string `'user'` in `localStorage`.
- **HTTP Interceptor:** A functional `HttpInterceptorFn` (`auth.interceptor.ts`) that automatically attaches `Authorization: Bearer <token>` to all outgoing requests if a token exists in `localStorage`.
- **Error Handling:** When backend returns error (HTTP 400, 401, 403, 404, 409, 500), display `error.message` from the standard Spring Boot backend error response:
  ```json
  {
    "timestamp": "2026-11-10T22:00:00Z",
    "status": 409,
    "error": "Conflict",
    "message": "Not enough seats available in class SL",
    "path": "/api/reservations"
  }
  ```

---

## 2. API Endpoints & Contract Specifications

### 2.1 Authentication & User (`/api/users`)
- **Register (`POST /api/users/register`)**
  - Public endpoint.
  - Request:
    ```json
    {
      "name": "John Doe",
      "email": "john@example.com",
      "password": "Password123"
    }
    ```
  - Response (201 Created):
    ```json
    {
      "id": 1,
      "name": "John Doe",
      "email": "john@example.com",
      "role": "PASSENGER"
    }
    ```
- **Login (`POST /api/users/login`)**
  - Public endpoint.
  - Request:
    ```json
    {
      "email": "john@example.com",
      "password": "Password123"
    }
    ```
  - Response (200 OK):
    ```json
    {
      "token": "eyJhbGciOiJIUzI1NiJ9...",
      "user": {
        "id": 1,
        "name": "John Doe",
        "email": "john@example.com",
        "role": "PASSENGER" // or "ADMIN"
      }
    }
    ```
- **Get Profile (`GET /api/users/me`)**
  - Secured (Bearer Token required).
  - Response (200 OK): `{ "id": 1, "name": "John Doe", "email": "john@example.com", "role": "PASSENGER" }`

---

### 2.2 Trains & Search (`/api/trains`)
- **Search Trains (`GET /api/trains?source={src}&destination={dest}&date={YYYY-MM-DD}&travelClass={OPTIONAL}`)**
  - Public endpoint. Supports intermediate stations along the train route.
  - Valid `travelClass` values: `SL` (Sleeper), `AC3` (3-Tier AC), `AC2` (2-Tier AC), `AC1` (First AC).
  - Response (200 OK array):
    ```json
    [
      {
        "trainId": 1,
        "trainNumber": "12951",
        "trainName": "Mumbai - Delhi Express",
        "boardingStation": "MUM",
        "boardingStationName": "Mumbai Central",
        "destinationStation": "DEL",
        "destinationStationName": "New Delhi",
        "intermediateBooking": false,
        "boardingDate": "2026-11-15",
        "departureTime": "17:00:00",
        "arrivalDate": "2026-11-16",
        "arrivalTime": "08:30:00",
        "distanceKm": 1384,
        "classes": [
          {
            "travelClass": "SL",
            "fare": 830.40,
            "availableSeats": 70,
            "status": "AVAILABLE"
          },
          {
            "travelClass": "AC3",
            "fare": 2214.40,
            "availableSeats": 64,
            "status": "AVAILABLE"
          }
        ]
      }
    ]
    ```
- **Get Train Route & Details (`GET /api/trains/{id}`)**
  - Public endpoint. Returns route schedule stops and class fares.
  - Response (200 OK):
    ```json
    {
      "id": 1,
      "trainNumber": "12951",
      "name": "Mumbai - Delhi Express",
      "source": "MUM",
      "destination": "DEL",
      "stops": [
        { "sequence": 1, "stationCode": "MUM", "stationName": "Mumbai Central", "arrivalTime": null, "departureTime": "17:00:00", "dayOffset": 0, "distanceKm": 0 },
        { "sequence": 2, "stationCode": "ST", "stationName": "Surat", "arrivalTime": "19:45:00", "departureTime": "19:50:00", "dayOffset": 0, "distanceKm": 263 },
        { "sequence": 3, "stationCode": "DEL", "stationName": "New Delhi", "arrivalTime": "08:30:00", "departureTime": null, "dayOffset": 1, "distanceKm": 1384 }
      ],
      "classes": [
        { "travelClass": "SL", "totalSeats": 72, "farePerKm": 0.60 },
        { "travelClass": "AC3", "totalSeats": 64, "farePerKm": 1.60 }
      ],
      "runningDays": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"]
    }
    ```

---

### 2.3 Reservations & Bookings (`/api/reservations`)
- **Book Ticket (`POST /api/reservations`)**
  - Secured (Bearer token).
  - Holds seats for 15 minutes in `PENDING_PAYMENT` state.
  - Request:
    ```json
    {
      "trainId": 1,
      "journeyDate": "2026-11-15",
      "travelClass": "SL",
      "boardingStation": "MUM",
      "destinationStation": "DEL",
      "passengers": [
        {
          "name": "Alice Smith",
          "age": 28,
          "gender": "FEMALE" // MALE, FEMALE, or OTHER
        },
        {
          "name": "Bob Smith",
          "age": 32,
          "gender": "MALE"
        }
      ]
    }
    ```
  - Response (201 Created):
    ```json
    {
      "id": 101,
      "pnr": null, // PNR is issued only after successful payment
      "status": "PENDING_PAYMENT",
      "trainNumber": "12951",
      "trainName": "Mumbai - Delhi Express",
      "boardingStation": "MUM",
      "boardingStationName": "Mumbai Central",
      "destinationStation": "DEL",
      "destinationStationName": "New Delhi",
      "boardingDate": "2026-11-15",
      "departureTime": "17:00:00",
      "arrivalDate": "2026-11-16",
      "arrivalTime": "08:30:00",
      "travelClass": "SL",
      "distanceKm": 1384,
      "totalFare": 1660.80,
      "remainingFare": 1660.80,
      "passengers": [
        { "id": 201, "name": "Alice Smith", "age": 28, "gender": "FEMALE", "seatNumber": 1, "status": "CONFIRMED" },
        { "id": 202, "name": "Bob Smith", "age": 32, "gender": "MALE", "seatNumber": 2, "status": "CONFIRMED" }
      ],
      "createdAt": "2026-11-10T22:30:00",
      "paymentDueBy": "2026-11-10T22:45:00" // 15-minute hold timer
    }
    ```
- **List My Bookings (`GET /api/reservations/my`)**
  - Secured. Returns list of `ReservationResponse` objects for the logged-in user.
- **Get Ticket by PNR (`GET /api/reservations/pnr/{pnr}`)**
  - Secured. Looks up ticket details by 10-digit numeric PNR.

---

### 2.4 Payments (`/api/payments`)
- **Step 1: Initiate Payment (`POST /api/payments`)**
  - Secured.
  - Request:
    ```json
    {
      "reservationId": 101,
      "paymentMethod": "UPI" // UPI, CARD, or NETBANKING
    }
    ```
  - Response (201 Created):
    ```json
    {
      "paymentId": 501,
      "reservationId": 101,
      "transactionId": "TXN1234567890ABCDEF",
      "amount": 1660.80,
      "status": "INITIATED",
      "paymentMethod": "UPI",
      "refundAmount": null,
      "message": "Redirect the user to the payment gateway. The gateway will call POST /api/payments/callback with this transactionId."
    }
    ```
- **Step 2: Payment Gateway Callback / Simulation (`POST /api/payments/callback`)**
  - Public endpoint (simulates the external bank webhook).
  - Request:
    ```json
    {
      "transactionId": "TXN1234567890ABCDEF",
      "status": "SUCCESS" // or "FAILURE"
    }
    ```
  - Response (200 OK): Status changes to `SUCCESS`. Backend automatically confirms reservation and assigns a 10-digit PNR!

---

### 2.5 Cancellations & Refunds (`/api/cancellations`)
- **Cancel Ticket (`POST /api/cancellations`)**
  - Secured. Supports FULL or PARTIAL passenger cancellation with automatic tiered refund rule ($90\%$ if $\ge 24\text{h}$, $50\%$ if $4\text{h}-24\text{h}$, $0\%$ if $<4\text{h}$).
  - Request (Full cancellation):
    ```json
    {
      "pnr": "4829173650"
    }
    ```
  - Request (Partial cancellation - only cancel specific passenger IDs):
    ```json
    {
      "pnr": "4829173650",
      "passengerIds": [201]
    }
    ```
  - Response (201 Created):
    ```json
    {
      "cancelId": 301,
      "pnr": "4829173650",
      "status": "REFUNDED", // or "NO_REFUND"
      "refundAmount": 747.36,
      "cancelTime": "2026-11-10T22:45:00",
      "type": "PARTIAL", // or "FULL"
      "cancelledPassengers": [
        { "id": 201, "name": "Alice Smith", "seatNumber": 1 }
      ],
      "remainingPassengers": 1,
      "remainingFare": 830.40
    }
    ```

---

### 2.6 Admin Train Management (`/api/admin/trains`)
- Accessible only when user has `"role": "ADMIN"`.
- **List All Trains (`GET /api/admin/trains`)** -> returns array of `TrainDetailsResponse`.
- **Create Train (`POST /api/admin/trains`)**
  - Request:
    ```json
    {
      "trainNumber": "12951",
      "name": "Mumbai - Delhi Express",
      "stops": [
        { "stationCode": "MUM", "stationName": "Mumbai Central", "departureTime": "17:00", "dayOffset": 0, "distanceKm": 0 },
        { "stationCode": "ST", "stationName": "Surat", "arrivalTime": "19:45", "departureTime": "19:50", "dayOffset": 0, "distanceKm": 263 },
        { "stationCode": "DEL", "stationName": "New Delhi", "arrivalTime": "08:30", "dayOffset": 1, "distanceKm": 1384 }
      ],
      "classes": [
        { "travelClass": "SL", "totalSeats": 72, "farePerKm": 0.60 },
        { "travelClass": "AC3", "totalSeats": 64, "farePerKm": 1.60 }
      ],
      "runningDays": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"]
    }
    ```
- **Delete Train (`DELETE /api/admin/trains/{id}`)** -> returns 204 No Content.

---

## 3. Desired Pages & UI Flows to Build

1. **Navbar:**
   - Brand title "Railway Reservation System".
   - Links: "Search Trains", "My Bookings", "PNR Status".
   - If Admin: "Manage Trains" link.
   - If logged in: User name badge + "Logout" button.
   - If logged out: "Login" and "Register" buttons.

2. **Login Page (`/login`) & Register Page (`/register`):**
   - Clean, centered cards with validation error alerts.
   - On login, store token and user in `localStorage` and redirect to `/`.

3. **Train Search & Results (`/` or `/search`):**
   - Search bar: Source Station Code (e.g. `MUM`), Destination Station Code (e.g. `DEL`), Journey Date, and optional Class dropdown (`SL`, `AC3`, `AC2`, `AC1`).
   - Train list cards showing Departure / Arrival times, distance, running days, and class chips with price & seat availability.
   - "Book Now" button on each class that navigates to the Booking Form with prefilled train/class.

4. **Booking & Payment Flow (`/book/:trainId`):**
   - Step 1: Add Passengers (1 to 6 passengers with Name, Age, Gender).
   - Shows summary calculation: Boarding station, Destination station, Travel Class, Total Fare.
   - "Confirm & Proceed to Payment" button triggers `POST /api/reservations`.
   - Step 2: Payment Modal / Screen showing 15-minute countdown timer (`paymentDueBy`).
   - Select Payment method (`UPI`, `CARD`, `NETBANKING`).
   - "Pay Now" button triggers `POST /api/payments` -> then simulates `POST /api/payments/callback` (`SUCCESS`).
   - Step 3: Success Screen displaying Confirmed 10-digit **PNR**, allocated Seat numbers (e.g. `Seat 1, Seat 2`), and download/print receipt option.

5. **My Bookings & PNR Status (`/my-bookings` and `/pnr`):**
   - View all historical and upcoming bookings.
   - Status badges: `CONFIRMED` (green), `PENDING_PAYMENT` (yellow), `PARTIALLY_CANCELLED` (orange), `CANCELLED` (red), `EXPIRED` (gray).
   - "Cancel Ticket" action with option to cancel the whole ticket OR select specific passengers (partial cancellation). Displays instant preview of the refund amount before confirming.

6. **Admin Dashboard (`/admin/trains` - protected by role guard):**
   - Table of all trains with stops count, seat capacity.
   - "Add Train" modal/form with dynamic stop rows (Station Code, Name, Arrival, Departure, Distance km).
   - Delete train button.

Build this cleanly with standalone components, clean typed services, and modern aesthetic styling.

