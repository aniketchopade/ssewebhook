# SSE Demo Project

This project demonstrates the use of Server-Sent Events (SSE) with JAX-RS and Jersey. It includes endpoints for subscribing to SSE streams and sending responses to those streams.

## Prerequisites

- Java 8 or higher
- Maven
- Git

## Setup

1. **Clone the repository**:
   ```sh
   git clone https://github.com/aniketchopade/ssewebhook.git
   cd sse-demo

2. mvn clean install

3. mvn jetty:run

4. /subscribe/webhooksse/{id}
This endpoint sets up an SSE connection. Replace {id} with a unique identifier.

Example: http://localhost:8080/api/subscribe/webhooksse/123

5. /subscribe/response/{id}
This endpoint sends a response to the SSE connection established by the corresponding /webhooksse/{id} request. Replace {id} with the same unique identifier used in the /webhooksse/{id} endpoint.

Example: http://localhost:8080/api/subscribe/response/123?message=Hello%20World