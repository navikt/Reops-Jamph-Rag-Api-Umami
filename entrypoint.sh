#!/bin/sh
set -e

# Apply route defaults from routes.json only if not already set by the environment
if [ -z "$OLLAMA_BASE_URL" ]; then
  export OLLAMA_BASE_URL=$(grep '^OLLAMA_BASE_URL=' /app/routes.env | cut -d= -f2-)
fi

exec java -jar /app/app.jar "$@"
