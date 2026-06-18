#!/bin/bash

echo "Starting Docker Compose services..."
docker compose up --build -d

echo "Waiting for Ollama (on Windows/WSL host) to be ready..."
# Wait for the Ollama API to respond
while ! curl -s http://localhost:11434/api/tags > /dev/null; do
    echo "Waiting for Ollama on port 11434..."
    sleep 2
done

echo "Ollama is ready! Pulling required models..."
echo "Pulling gemma4:12b..."
# docker exec -it ollama ollama pull gemma4:e4b
curl -d '{"name": "gemma4:12b"}' http://localhost:11434/api/pull

echo "Pulling nomic-embed-text..."
# docker exec -it ollama ollama pull nomic-embed-text
curl -d '{"name": "nomic-embed-text"}' http://localhost:11434/api/pull

echo "Setup complete! The infrastructure is running and models are pulled."

echo "Tailing logs (Press Ctrl+C to exit without stopping the containers)..."
docker compose logs -f
