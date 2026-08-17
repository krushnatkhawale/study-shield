# StudyShield - System Design Document

## 1. Project Overview
**StudyShield** is a parent-controlled educational companion app for Android TV.  
The main goal is to help parents reduce children's exposure to random ads and convert screen time into meaningful, parent-initiated learning activities.

**Core Philosophy**:
- All study/quizzes are **parent-initiated** (no auto-forced sessions).
- Full control remains with parents, especially for children under 7-8 years.
- Secondary benefit: Encourage parents to reduce their own phone addiction by spending quality time with kids.

## 2. High-Level Architecture
- Microservices architecture (4 services)
- Mobile App (Android) as the primary client
- Android TV App for displaying quizzes
- Communication via REST APIs through API Gateway

## 3. Microservices

- **User Service** – Authentication, profiles, consent
- **Content Service** – Educational content, boards, quizzes
- **Quiz Attempt Service** – Recording parent-initiated quiz attempts and results
- **TV Device Service** – TV connections and network history
- **API Gateway** – Routing and security

## 4. Non-Functional Requirements
- Privacy-first (DPDP Act + GDPR-like principles)
- Scalable for future international expansion
- Incremental development (adapt to existing app behavior)
- High usability for Indian parents

## 5. Future Considerations
- International boards (IB, Cambridge, etc.)
- Optional ad detection (parked for now)
- Analytics and parent engagement insights

**Version**: 1.0 (Initial Design)
**Date**: July 2026