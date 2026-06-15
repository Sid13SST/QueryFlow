# QueryFlow

QueryFlow is a high-performance distributed search typeahead system designed to deliver real-time query suggestions. The system leverages consistent hashing, caching, trending analytics, and batch-write optimization to handle high-throughput read/write search traffic efficiently.

This workspace contains **Phase 0: Project Setup & Architecture Foundation**, establishing the boilerplate, basic health check endpoints, centralized API clients, global exception handling, and layout configuration.

---

## Tech Stack

### Backend
* **Java 17**
* **Spring Boot 3** (Maven)
* **Spring Data JPA**
* **PostgreSQL Driver**
* **Lombok**

### Frontend
* **React (Vite)**
* **Axios** (Centralized API client)
* **Tailwind CSS v4** (Utility-first styling with modern PostCSS pipeline)

### Database
* **PostgreSQL**

---

## Project Structure

```text
QueryFlow/
├── backend/
│   ├── src/main/java/com/queryflow/
│   │   ├── config/            # Exception handlers, db loggers, spring configs
│   │   ├── controller/        # Rest APIs (e.g. /health)
│   │   ├── dto/               # Data Transfer Objects (ApiResponse, ErrorResponse)
│   │   └── QueryFlowApplication.java
│   ├── src/main/resources/    # application.properties configuration
│   ├── .env.example           # Example local environment variable configuration
│   ├── pom.xml                # Maven project descriptor
│   └── mvnw / mvnw.cmd        # Maven Wrapper scripts
│
└── frontend/
    ├── src/
    │   ├── api/               # Centralized Axios HTTP clients
    │   ├── components/        # Reusable presentation components
    │   ├── pages/             # Page layouts
    │   ├── hooks/             # Custom React hooks
    │   ├── services/          # API calling services
    │   ├── App.jsx            # Core layout & health check
    │   ├── index.css          # Tailwind CSS configurations
    │   └── main.jsx
    ├── .env.example           # Example frontend environment configuration
    ├── tailwind.config.js     # Tailwind CSS settings
    └── postcss.config.js      # PostCSS compilation pipeline
```

---

## Database Setup Instructions

1. Make sure **PostgreSQL** is installed and running on your system.
2. Connect to your PostgreSQL server and create a database named `queryflow`:
   ```sql
   CREATE DATABASE queryflow;
   ```
3. Locate the `backend/.env.example` file, copy it to `backend/.env`, and customize it with your database connection parameters:
   * By default, it is configured for port `5432` with username `postgres` and password `postgres`.
   * If your PostgreSQL is running on a different port (e.g., `5433`), update the `DB_URL` accordingly:
     ```env
     DB_URL=jdbc:postgresql://localhost:5433/queryflow
     DB_USERNAME=postgres
     DB_PASSWORD=your_secure_password
     ```

---

## Local Setup & Run Instructions

### 1. Run the Backend (Spring Boot)

1. Open a terminal in the `backend/` directory:
   ```bash
   cd backend
   ```
2. Make sure you have created your local `backend/.env` file with the correct database connection details.
3. Build and package the application:
   * **Windows (PowerShell)**:
     ```powershell
     .\mvnw.cmd clean compile
     ```
   * **Linux/macOS**:
     ```bash
     ./mvnw clean compile
     ```
4. Start the application:
   * **Windows (PowerShell)**:
     ```powershell
     .\mvnw.cmd spring-boot:run
     ```
   * **Linux/macOS**:
     ```bash
     ./mvnw spring-boot:run
     ```
5. Check that the health check endpoint returns `UP`:
   ```bash
   curl http://localhost:8080/health
   ```
   Expected JSON response:
   ```json
   {
     "status": "UP",
     "service": "QueryFlow"
   }
   ```

### 2. Run the Frontend (React-Vite)

1. Open a terminal in the `frontend/` directory:
   ```bash
   cd frontend
   ```
2. Install dependencies:
   ```bash
   npm install
   ```
3. Copy `frontend/.env.example` to `frontend/.env` to configure the API base URL:
   ```env
   VITE_API_BASE_URL=http://localhost:8080
   ```
4. Run the development server:
   ```bash
   npm run dev
   ```
5. Open your browser and navigate to the address shown in the terminal (usually `http://localhost:5173`).
6. Click **Check Backend Health** to verify frontend-to-backend integration.

---

## Verification & Acceptance

* **Backend Compilation**: Run `.\mvnw.cmd clean compile` inside the `backend` directory to ensure zero errors.
* **Frontend Compilation**: Run `npm run build` inside the `frontend` directory to ensure production bundles compile successfully.
* **API Validation**: Calling `/health` logs incoming requests and returns status `UP`.
