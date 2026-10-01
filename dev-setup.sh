#!/bin/bash

# Reisebus API - Development Setup Script

echo "🚀 Starting Reisebus API Development Setup..."

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker is not running. Please start Docker first."
    exit 1
fi

echo "🧹 Cleaning up old database"
docker compose down -v --remove-orphans

echo "📦 Starting PostgreSQL container..."
docker compose up -d

echo "⏳ Waiting for PostgreSQL to be ready..."
sleep 5

# Check if database is ready
while ! docker compose exec -T postgres pg_isready -U reisebus -d reisebus_dev > /dev/null 2>&1; do
    echo "  ... waiting for database..."
    sleep 2
done

echo "✅ PostgreSQL is ready!"
echo ""
echo "🛠️  Building the application..."
./gradlew clean build -x test

echo ""
echo "✅ Setup complete!"
echo ""
echo "📝 To start the application:"
echo "   ./gradlew bootRun --args='--spring.profiles.active=dev'"
echo ""
echo "🌐 API Documentation will be available at:"
echo "   http://localhost:8080/swagger-ui.html"

