package com.kaushalya.interrupter.data

/**
 * The backend rejected the stored session token (401/403) on an authenticated
 * call. Callers use this to force a re-login instead of trusting or retrying
 * with a stale token.
 */
class UnauthorizedException(message: String) : Exception(message)