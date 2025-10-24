/**
 * Logback adapter for Puriflow4j:
 * - PurifyAppender: wraps a real appender and sanitizes messages before delegating.
 * - SanitizedLoggingEvent: lightweight wrapper overriding formatted message.
 */
package io.puriflow4j.logs.logback;
