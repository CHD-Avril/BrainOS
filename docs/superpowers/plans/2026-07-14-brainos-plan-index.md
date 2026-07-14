# BrainOS Implementation Plan Index

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Coordinate four independently verifiable delivery phases into the approved BrainOS enterprise AI knowledge-base product.

**Architecture:** Execute a vertical-slice modular-monolith plan: foundation/auth, knowledge/document, RAG chat, then admin/dashboard/delivery. Each phase ends with backend, frontend and E2E evidence before the next phase starts.

**Tech Stack:** Java 21, Spring Boot 3.5.16, Spring AI 1.1.8, MyBatis-Plus, MySQL, Redis, Chroma, Vue 3, Vite, Pinia, Element Plus, JUnit 5, Testcontainers, Vitest, Playwright.

## Global Constraints

- The approved overview in `specs/overview/` is authoritative.
- Module Specs in `specs/foundation`, `auth`, `knowledge`, `document`, `rag`, `dashboard`, `admin` must be approved before their implementation task starts.
- TDD order is mandatory for production behavior: failing test, observed RED, minimal implementation, observed GREEN, refactor, regression.
- PC Web only; 1024px and 1440px are the visual acceptance widths.
- No OCR, mobile client, multi-tenancy, SSO, microservices, message queue, Serverless or unrecorded features.

## Execution Order

1. [Phase 1: Foundation and Auth](2026-07-14-01-foundation-auth.md)
2. [Phase 2: Knowledge and Document](2026-07-14-02-knowledge-document.md)
3. [Phase 3: RAG Chat](2026-07-14-03-rag-chat.md)
4. [Phase 4: Admin, Dashboard, and Delivery](2026-07-14-04-admin-dashboard-delivery.md)

## Spec Coverage Self-Review

| Requirement | Implementing tasks | Verification |
| --- | --- | --- |
| R1 / A-R1-01..04 | Phase 1 Tasks 3-4 | Phase 1 Task 6 |
| R2 / A-R2-01..03 | Phase 4 Tasks 3-4 | Phase 4 Task 6 |
| R3 / A-R3-01..03 | Phase 2 Task 1 and Task 6 | Phase 2 Task 7 |
| R4 / A-R4-01..04 | Phase 2 Tasks 2-3 and Task 5 | Phase 2 Task 7 |
| R5 / A-R5-01..03 | Phase 2 Task 4 and Task 5 | Phase 2 Task 7 |
| R6 / A-R6-01..04 | Phase 3 Tasks 1-2 and Task 4-5 | Phase 3 Task 6 |
| R7 / A-R7-01..03 | Phase 3 Task 3 and Task 5 | Phase 3 Task 6 |
| R8 / A-R8-01..03 | Phase 4 Task 1 and Task 4 | Phase 4 Task 6 |
| R9 / A-R9-01..03 | Phase 4 Task 2 and Task 4 | Phase 4 Task 6 |
| R10 / A-R10-01..05 | Phase 1 Tasks 1-2 and Task 6; Phase 4 Tasks 5-7 | Phase 4 Task 6 |

All 35 overview Acceptance items have an implementation task and a verification task.

## Shared Type Registry

| Type or function | Defined in | Consumers |
| --- | --- | --- |
| `ApiResponse<T>`, `ErrorCode` | Phase 1 Task 1 | Every REST controller |
| `UserPrincipal(userId, username, role)` | Phase 1 Task 4 | Knowledge, document, chat and admin controllers |
| `KnowledgeBaseCleanupPort.cleanup(Long)` | Phase 2 Task 1 | Document and RAG cleanup composition |
| `DocumentStatus` | Phase 2 Task 2 | Indexing service, API and frontend status tag |
| `DocumentParserPort.parse(Path,String)` | Phase 2 Task 3 | `DocumentIndexingService` |
| `VectorIndexPort` | Phase 2 Task 4 | Document indexing and RAG retrieval |
| `CitationCandidate` | Phase 3 Task 1 | Prompt factory, stream events and persisted citations |
| `ChatModelType` | Phase 3 Task 2 | Session, model router and model selector |
| `ChatStreamEvent` | Phase 3 Task 4 | SSE controller and frontend SSE client |
| `AuditEvent` | Phase 4 Task 2 | Auth, knowledge, document and admin application services |

Names, parameters and ownership in later plans must match this table. Any necessary signature change is first applied to this registry and the consuming plans.

## Plan Exit Criteria

- Every phase command reports the expected GREEN result.
- `scripts/verify.sh` and the complete Playwright suite pass from a clean checkout.
- All module `status.yaml` files are `done`, all module tasks are `[done]`, and every Acceptance is present in `docs/TEST-REPORT.md`.
- Browser screenshots at 1024x768 and 1440x900 match `design-system/MASTER.md` with no material visual or interaction defects.
