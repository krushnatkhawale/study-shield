# User Service - Detailed Description

## Purpose
Manage all user-related data, authentication, and parental consent.

## Key Responsibilities
- Parent and child profile management
- Authentication & authorization (JWT)
- Consent recording (important for child data)
- Data export / deletion support (privacy compliance)

## Current App Adaptation Note
This service should integrate with existing user login/profile logic. New features should be added incrementally without breaking current behavior.

## Main Entities
- User
- Parent
- ChildProfile (linked to parent, with board, class, age)

## Future Enhancements
- Multi-child support with individual settings