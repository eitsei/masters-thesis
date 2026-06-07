# Banking Research RAG Baseline

This is a baseline Retrieval-Augmented Generation (RAG) system built for a Master's Thesis. It leverages Spring Boot 3.4+, Spring AI, Qdrant, and Ollama.

## Infrastructure Setup

1. **Start the containers and pull the models:**
   Execute the setup script. This will start Ollama, Qdrant, Prometheus, and Grafana via Docker Compose, wait for Ollama to become responsive, and pull the required models (`gemma4:9b` and `nomic-embed-text`).
   ```bash
   chmod +x setup.sh
   ./setup.sh
   ```

2. **Verify Services:**
   - Ollama API: http://localhost:11434
   - Qdrant Dashboard: http://localhost:6333/dashboard
   - Prometheus: http://localhost:9090
   - Grafana: http://localhost:3000 (admin/admin)

## Application Setup

1. **Run the Spring Boot application:**
   You can run the application using Maven:
   ```bash
   ./mvnw spring-boot:run
   ```
   Or use your favorite IDE.

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
