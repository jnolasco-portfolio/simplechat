# Simplechat Project Customization Guide

## Project Overview

- Spring Boot 4.0.6 application using Spring AI with OpenAI model
- PostgreSQL database with Liquibase migrations
- Chat functionality via REST API with streaming support

## Key Components

1. **ChatController**
   - Handles chat streaming and history management
   - Uses `@CrossOrigin` for CORS
   - Integrates with `ChatClient` and `ChatMemory`

2. **Database Setup**
   - PostgreSQL with Liquibase change logs
   - `db/changelog` directory contains migration scripts

3. **Dependencies**
   - Spring AI (version 2.0.0-M6)
   - Spring Boot Actuator for monitoring
   - Lombok for boilerplate reduction

## Conventions

- All chat-related code in `src/main/java/cloud/nolasco/simplechat`
- Prompts stored in `resources/prompts/`
- Database schema managed via Liquibase
- REST API follows Spring Boot conventions

## Potential Pitfalls

- Spring AI version 2.0.0-M6 may have breaking changes
- Reactor-based streaming requires proper error handling
- Liquibase migrations must be applied in order

## Recommended Agents

- `chat-agent`: For handling chat interactions
- `db-migration-agent`: For managing database changes
- `ai-model-agent`: For model configuration and prompt management
