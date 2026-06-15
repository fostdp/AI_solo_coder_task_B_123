#!/bin/bash
set -e

echo "Starting all barracks-monitor services..."

echo "Starting mqtt-ingest on port 8081..."
java -jar /app/mqtt-ingest.jar &

echo "Starting nutrition-rf on port 8082..."
java -jar /app/nutrition-rf.jar &

echo "Starting outbreak-satscan on port 8083..."
java -jar /app/outbreak-satscan.jar &

echo "Starting alert-broker on port 8080..."
java -jar /app/alert-broker.jar &

echo "All services started. Waiting for processes..."
wait
