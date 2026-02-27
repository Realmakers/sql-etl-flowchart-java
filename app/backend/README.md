# Java Backend for SQL Flowchart Generator

This is a Spring Boot application that parses SQL and returns a structured JSON for the frontend to generate flowcharts.

## Prerequisites

- Java 17 or later
- Maven 3.6 or later

## How to Run

1.  Navigate to this directory:
    ```bash
    cd app/backend
    ```

2.  Run with Maven:
    ```bash
    mvn spring-boot:run
    ```
    
    The server will start on `http://localhost:8080`.

## API

- **POST** `/api/parse`
    - Body: Raw SQL string
    - Returns: JSON object complying with `ParsedSQL` interface.

## Implementation Details

- Uses `JSqlParser` (v4.4) to parse SQL.
- Extracts Tables, Joins, CTEs, Subqueries.
- Logic mirrors the original TypeScript implementation but uses a robust Java parser.
