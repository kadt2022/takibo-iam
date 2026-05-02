# Takibo IAM

**Takibo (TAKIBO)** stands for **Trust, Access Key, Identity, Boundary, Organization**.

Takibo IAM is a modular Identity and Access Management platform designed around OAuth2, OpenID Connect, adaptive authorization, multi-tenant governance, auditability, and event-driven security workflows.

The project is currently developed as a **modular monolith**, with a clear internal separation of domains and modules. This approach allows fast iteration while preserving future readiness for microservice extraction.

## Overview

Takibo IAM aims to provide a modern IAM foundation for organizations that need:

- Centralized identity and access management
- OAuth2 and OpenID Connect support
- Organization and space-based multi-tenancy
- Role-based and permission-based access control
- Adaptive access decisions based on risk context
- Secure audit and traceability
- Transactional event processing through the outbox pattern
- Extensible messaging and notification workflows

Takibo is designed with enterprise-grade principles in mind: security by default, modular architecture, explicit boundaries, observability, and strong data consistency.

## Architectural Principles

Takibo follows these core principles:

1. **Modular Monolith First**

   The platform is organized into independent modules with clear responsibilities. This reduces deployment complexity during the early phase while keeping the architecture compatible with a future microservice split.

2. **Domain Separation**

   Business concepts are separated from persistence concerns. The architecture favors domain models, application services, infrastructure adapters, and explicit module boundaries.

3. **Security by Design**

   Security is not treated as a secondary concern. Authentication, authorization, audit, tenant boundaries, JWT handling, and adaptive decisions are part of the core design.

4. **Tenant Boundary Enforcement**

   Takibo models organizations and spaces explicitly. Access control is designed to respect organizational and space boundaries.

5. **Adaptive Authorization**

   Access decisions can include contextual risk evaluation through the Adaptive Decision Pipeline.

6. **Reliable Event Processing**

   Domain events are persisted through the outbox pattern before being processed asynchronously.

7. **Operational Observability**

   The platform is designed to support structured logs, metrics, audit events, and operational diagnostics.

## Main Modules

The repository is organized into several modules.

### `takibo-iam-boot`

Main Spring Boot application module.

It assembles the IAM platform and starts the modular monolith.

### `takibo-authorization-server`

Authorization server module.

Responsible for OAuth2 and token-related capabilities.

### `takibo-identity-core`

Identity domain module.

Responsible for users, accounts, credentials, roles, groups, and identity-related business rules.

### `takibo-management-service`

Management domain module.

Responsible for organizations, spaces, OAuth clients, registration workflows, and administrative operations.

### `takibo-security-management`

Security management module.

Responsible for security filters, JWT authentication, boundary checks, and security-related infrastructure.

### `takibo-security-context`

Core security context module.

Defines reusable security context concepts shared across modules.

### `takibo-security-context-spring`

Spring integration for the Takibo security context.

### `takibo-adp-api`

Adaptive Decision Pipeline API module.

Defines the public contracts for adaptive authorization.

### `takibo-adp-core`

Core Adaptive Decision Pipeline implementation.

Contains risk evaluation and decision logic.

### `takibo-adp-spring`

Spring integration for the Adaptive Decision Pipeline.

### `takibo-outbox-core`

Core outbox contracts and domain concepts.

### `takibo-outbox-jpa`

JPA-based outbox persistence implementation.

### `takibo-outbox-spring`

Spring integration for outbox processing.

### `takibo-outbox-spring-starter`

Starter module for easier outbox integration.

### `takibo-messaging`

Messaging module.

Responsible for message dispatching, delivery tracking, channels, and notification workflows.

### `takibo-audit`

Audit module.

Responsible for structured audit events, audit annotations, and traceability support.

## Key Features

Current and planned capabilities include:

- Organization signup flow
- Space provisioning
- Account and user creation
- Role and permission provisioning
- OAuth client registration
- JWT-based authentication
- Space and organization boundary enforcement
- Adaptive authorization decisions
- Transactional outbox publishing
- Asynchronous messaging dispatch
- Email notification channel
- Audit and logging infrastructure
- Spring Boot Actuator integration
- OpenAPI documentation support

## Technology Stack

Takibo is built with:

- Java 21
- Spring Boot
- Spring Security
- Spring Data JPA
- Hibernate
- PostgreSQL
- Flyway
- Gradle
- OAuth2 / OpenID Connect concepts
- Micrometer / Actuator
- JavaMail
- Modular Spring architecture

## Local Development

### Prerequisites

You need:

- JDK 21
- Gradle Wrapper
- PostgreSQL
- Git

### Clone the repository

```bash
git clone https://github.com/kadt2022/takibo-iam.git
cd takibo-iam