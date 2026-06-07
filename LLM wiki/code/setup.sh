#!/bin/bash

echo "Starting Docker Compose services..."
docker compose up --build -d

echo "Waiting for Ollama to be ready..."
# Wait for the Ollama API to respond
while ! curl -s http://localhost:11434/api/tags > /dev/null; do
    sleep 2
done

echo "Ollama is ready! Pulling required models..."
echo "Pulling gemma4:9b..."
docker exec -it ollama ollama pull gemma4:e4b

echo "Pulling nomic-embed-text..."
docker exec -it ollama ollama pull nomic-embed-text

echo "Setup complete! The infrastructure is running and models are pulled."

echo "Tailing logs (Press Ctrl+C to exit without stopping the containers)..."
docker compose logs -f
