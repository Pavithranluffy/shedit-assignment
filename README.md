# Schedit Community Library Lending System

This project implements a small but complete community library workflow with a Spring Boot backend and a React + TypeScript frontend. The app supports:

- browsing catalog items
- borrowing and returning books
- renewing loans when no one is waiting
- joining and cancelling waitlists
- collecting reserved copies within a 3-day window
- managing late fees and copy status as a librarian

## Run it

### Backend

```bash
cd backend
mvn spring-boot:run
```

The API runs on http://localhost:8080.

### Frontend

```bash
cd frontend
npm install
npm run dev
```

The UI runs on http://localhost:3000.

## Seeded data

The backend creates a few members, books, and copies on first launch so the UI is immediately usable.

## Key design decisions

- The service layer is the single authority for business rules. The controller only adapts HTTP requests and returns DTOs.
- A single virtual clock is stored in the database and used for all time-based rules such as loan due dates and reservation expiry.
- Each physical copy is modeled separately so the system can track availability, on-loan state, reservations, damage, and loss.
- Waiting-list entries are kept as explicit entities so the app can represent queue order, active state, reservation expiry, and collection windows.

## Trade-offs and scope

- Authentication is intentionally simplified with an "acting as" selector instead of real login.
- No email or push notifications are implemented.
- Fee settlement is manual and local only.
- The system handles the main rule set well, but a production version would need stronger concurrency controls and audit history.

## Tests

```bash
cd backend
mvn test
```

## What I would do next

- add real authentication and role-based access
- add optimistic locking or transactional safeguards for concurrent borrowing and reservations
- add richer librarian views for fee history and copy repair workflows
- add end-to-end tests around the most important member and librarian journeys

## AI usage

This project was built with GitHub Copilot assistance while keeping the core domain rules and behavior under review. The implementation decisions were adapted to fit the stated library requirements.
