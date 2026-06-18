# Banking Research RAG Baseline

This is a baseline Retrieval-Augmented Generation (RAG) system built for a Master's Thesis. It leverages Spring Boot 3.4+, Spring AI, Qdrant, and Ollama.

## How to Start the App

You can run this application in two ways: **with Docker** (fully containerized) or **without Docker** (running the Spring Boot application locally on your host).

### Option A: Running with Docker (Fully Containerized)

In this mode, all services—including the Spring Boot application, Qdrant database, Prometheus, and Grafana—run inside Docker containers.

1. **Run the setup script:**
   This script builds the Spring Boot container, starts the services via Docker Compose, and pulls the required LLM/embedding models in Ollama:
   ```bash
   chmod +x setup.sh
   ./setup.sh
   ```

---

### Option B: Running without Docker (Local Application)

In this mode, you run the Spring Boot application locally on your host (ideal for faster debugging, hot-reloads, and IDE integration) while database services run in Docker.

1. **Start only the database and monitoring services:**
   ```bash
   docker compose up -d qdrant prometheus grafana
   ```

2. **Ensure Ollama is running on your host:**
   Make sure Ollama is running locally on port `11434` and that you have pulled the required models:
   ```bash
   ollama pull gemma4:12b
   ollama pull nomic-embed-text
   ```

3. **Run the Spring Boot application locally:**
   ```bash
   ./mvnw spring-boot:run
   ```
   *(Or run/debug the `RagApplication.java` main class directly inside your IDE).*

---

### Verify Services

* **Spring Application API:** http://localhost:8080
* **Ollama API:** http://localhost:11434
* **Qdrant Dashboard:** http://localhost:6333/dashboard
* **Prometheus:** http://localhost:9090
* **Grafana:** http://localhost:3000 (Credentials: `admin` / `admin`)

## Usage

1. **Ingest a Document:**
   Create a sample text file (e.g., `sample.txt`) and trigger the ingestion endpoint.
   ```bash
   echo "The central bank recently announced a new policy regarding interest rates for savings accounts, effective next month." > sample.txt
   curl -X POST "http://localhost:8080/api/rag/ingest?filePath=$(pwd)/sample.txt"
   ```

2. **Test the RAG Endpoint:**
   Ask a question based on the ingested document.
   ```bash
   curl "http://localhost:8080/api/rag/ask?query=What+did+the+central+bank+announce%3F"
   ```

## Why this approach?

- **CPU-First:** By starting with CPU, we eliminate the "driver hell" of WSL2/GPU passthrough. If the inference is too slow, you only need to change the image name and add the deploy block in the Docker Compose later.
- **Code-Centric Ingestion:** By using `IngestionService.java` instead of a UI like AnythingLLM, you have immediate programmatic control over the chunking parameters, which is essential for your thesis documentation.
- **Clean Slate:** This gives you a working "Hello World" for RAG within minutes, allowing you to move directly to the optimization phase.
