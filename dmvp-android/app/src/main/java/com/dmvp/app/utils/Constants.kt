/**
 * app/src/main/java/com/dmvp/app/utils/Constants.kt
 *
 * Application-wide constants for DMVP v3.0 Android app.
 * Includes API endpoints, shared preferences keys, bundle keys,
 * intent extras, configuration values, and protocol constants.
 */

package com.dmvp.app.utils

/**
 * API-related constants.
 */
object ApiConstants {
    // Base URL - override with BuildConfig in production
    const val BASE_URL = "https://api.dmvp.example.com/"
    const val BASE_URL_DEV = "http://10.0.2.2:3000/" // For emulator debugging

    // Endpoints
    const val ENDPOINT_EVIDENCE = "evidence"
    const val ENDPOINT_EVIDENCE_BY_HASH = "evidence/by-hash"
    const val ENDPOINT_VERIFY = "verify"
    const val ENDPOINT_VERIFY_POLICY = "verify/policy"
    const val ENDPOINT_SEARCH = "search"
    const val ENDPOINT_SEARCH_RELATED = "search/related"
    const val ENDPOINT_DEVICES_REGISTER = "devices/register"
    const val ENDPOINT_DEVICES_ROTATE = "devices/rotate"
    const val ENDPOINT_DEVICES_REVOKE = "devices/revoke"
    const val ENDPOINT_DEVICES_RECOVER = "devices/recover"
    const val ENDPOINT_DEVICES_INFO = "devices"
    const val ENDPOINT_OWNERSHIP_CLAIM = "ownership/claim"
    const val ENDPOINT_OWNERSHIP_BY_EVIDENCE = "ownership"
    const val ENDPOINT_AUDIT_EXPORT = "audit/export"
    const val ENDPOINT_POLICIES_VERSION = "policies/version"
    const val ENDPOINT_PREMIUM_BACKUP = "premium/backup"
    const val ENDPOINT_PREMIUM_RESTORE = "premium/restore"
    const val ENDPOINT_PREMIUM_STATUS = "premium/status"

    // Headers
    const val HEADER_AUTHORIZATION = "Authorization"
    const val HEADER_IDEMPOTENCY_KEY = "Idempotency-Key"
    const val HEADER_REQUEST_SIGNATURE = "X-Request-Signature"
    const val HEADER_NONCE = "X-Nonce"
    const val HEADER_TIMESTAMP = "X-Timestamp"
    const val HEADER_POLICY_VERSION = "X-Policy-Version"
    const val HEADER_REQUEST_ID = "X-Request-Id"

    // Query Parameters
    const val PARAM_MAX_RESULTS = "maxResults"
    const val PARAM_PAGE = "page"
    const val PARAM_LIMIT = "limit"
    const val PARAM_TRUST_TIER = "trust_tier"
    const val PARAM_LIFECYCLE_STATE = "lifecycle_state"
}

/**
 * Shared preferences / DataStore keys.
 */
object PrefsKeys {
    // App settings
    const val KEY_DEVICE_KEY_ID = "device_key_id"
    const val KEY_PUBLIC_KEY = "public_key"
    const val KEY_TRUST_TIER = "trust_tier"
    const val KEY_DEVICE_REGISTERED = "device_registered"
    const val KEY_LAST_SYNC = "last_sync"
    const val KEY_POLICY_VERSION = "policy_version"
    const val KEY_SERVER_TIME_OFFSET = "server_time_offset"

    // User preferences
    const val KEY_DARK_THEME = "dark_theme"
    const val KEY_PRIVACY_GPS = "privacy_gps"
    const val KEY_PRIVACY_EXIF = "privacy_exif"
    const val KEY_PRIVACY_DEVICE_INFO = "privacy_device_info"
    const val KEY_VERIFICATION_MODE = "verification_mode"

    // Tokens and auth
    const val KEY_AUTH_TOKEN = "auth_token"
    const val KEY_REFRESH_TOKEN = "refresh_token"
    const val KEY_TOKEN_EXPIRY = "token_expiry"

    // Onboarding
    const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
    const val KEY_DEVICE_KEY_CREATED = "device_key_created"
}

/**
 * Bundle keys for passing data between components.
 */
object BundleKeys {
    const val ARG_EVIDENCE_ID = "evidence_id"
    const val ARG_SHA256 = "sha256"
    const val ARG_MEDIA_TYPE = "media_type"
    const val ARG_DEVICE_KEY_ID = "device_key_id"
    const val ARG_VERDICT = "verdict"
    const val ARG_CE = "cee"
    const val ARG_FILE_PATH = "file_path"
    const val ARG_VERIFICATION_MODE = "verification_mode"
    const val ARG_SEARCH_RESULTS = "search_results"
    const val ARG_CLAIM_ID = "claim_id"
    const val ARG_OWNER_IDENTITY = "owner_identity"
    const val ARG_TRUST_TIER = "trust_tier"
    const val ARG_WARNINGS = "warnings"
}

/**
 * Intent extras (for activities).
 */
object IntentExtras {
    const val EXTRA_EVIDENCE_ID = "com.dmvp.app.EVIDENCE_ID"
    const val EXTRA_FILE_URI = "com.dmvp.app.FILE_URI"
    const val EXTRA_MEDIA_TYPE = "com.dmvp.app.MEDIA_TYPE"
    const val EXTRA_VERIFICATION_MODE = "com.dmvp.app.VERIFICATION_MODE"
    const val EXTRA_FROM_NOTIFICATION = "com.dmvp.app.FROM_NOTIFICATION"
}

/**
 * App configuration constants.
 */
object AppConfig {
    // Networking
    const val CONNECT_TIMEOUT_SECONDS = 30L
    const val READ_TIMEOUT_SECONDS = 60L
    const val WRITE_TIMEOUT_SECONDS = 60L
    const val RETRY_COUNT = 3
    const val RETRY_BACKOFF_MS = 1000L

    // Database
    const val DATABASE_NAME = "dmvp_database"
    const val DATABASE_VERSION = 1

    // Cache
    const val CACHE_SIZE_MB = 50
    const val CACHE_MAX_AGE_SECONDS = 3600 // 1 hour

    // UI
    const val DEFAULT_PAGE_SIZE = 20
    const val MAX_SEARCH_RESULTS = 50
    const val MAX_DEVICE_LIST = 100
    const val ANIMATION_DURATION_MS = 300

    // Verification
    const val DEFAULT_VERIFICATION_MODE = "standard"
    const val MAX_IMAGE_SIZE_BYTES = 50 * 1024 * 1024 // 50 MB
    const val MAX_VIDEO_SIZE_BYTES = 200 * 1024 * 1024 // 200 MB
    const val MAX_VIDEO_DURATION_MS = 600000 // 10 minutes
    const val KEYFRAME_EXTRACTION_MAX = 20

    // Thumbnails
    const val THUMBNAIL_WIDTH = 512
    const val THUMBNAIL_HEIGHT = 512
    const val THUMBNAIL_QUALITY = 80

    // Logging
    const val MAX_LOG_LINES = 1000
    const val LOG_TAG_PREFIX = "DMVP"
}

/**
 * DMVP protocol constants (aligned with specification).
 */
object DmvpConstants {
    // Protocol versions
    const val PROTOCOL_VERSION = "dmvp-v3.0.0"
    const val VERIFICATION_POLICY_VERSION = "dmvp-v3.0.0"
    const val CLIENT_APP_VERSION = "3.0.0"
    const val SIGNATURE_ALGORITHM = "SHA256withECDSA"

    // Key algorithms
    const val KEY_ALGORITHM_EC = "EC"
    const val KEY_ALGORITHM_RSA = "RSA"
    const val KEY_SIZE_EC_256 = 256

    // Hash algorithms
    const val HASH_ALGORITHM_SHA256 = "SHA-256"
    const val HASH_ALGORITHM_SHA512 = "SHA-512"

    // Media types
    const val MEDIA_TYPE_IMAGE = "image"
    const val MEDIA_TYPE_VIDEO = "video"

    // Evidence lifecycle states
    const val EVIDENCE_STATE_ACTIVE = "ACTIVE"
    const val EVIDENCE_STATE_DELETED = "DELETED"
    const val EVIDENCE_STATE_EXPIRED = "EXPIRED"
    const val EVIDENCE_STATE_DISPUTED = "DISPUTED"

    // Ownership claim types
    const val CLAIM_TYPE_ORIGINAL_AUTHOR = "original_author"
    const val CLAIM_TYPE_LICENSE_HOLDER = "license_holder"
    const val CLAIM_TYPE_CUSTODIAN = "custodian"
    const val CLAIM_TYPE_OTHER = "other"

    // Ownership review statuses
    const val CLAIM_STATUS_PENDING = "PENDING"
    const val CLAIM_STATUS_APPROVED = "APPROVED"
    const val CLAIM_STATUS_REJECTED = "REJECTED"
    const val CLAIM_STATUS_DISPUTED = "DISPUTED"

    // Timestamp modes
    const val TIMESTAMP_MODE_STANDARD = "standard"
    const val TIMESTAMP_MODE_ENHANCED = "enhanced"
    const val TIMESTAMP_MODE_HIGH_ASSURANCE = "high_assurance"

    // Nonce expiry (seconds)
    const val NONCE_EXPIRY_SECONDS = 300 // 5 minutes
}

/**
 * Verification constants (aligned with specification).
 */
object VerificationConstants {
    // Verification modes
    const val MODE_FAST = "fast"
    const val MODE_STANDARD = "standard"
    const val MODE_DEEP = "deep"

    // Verdict values
    object Integrity {
        const val EXACT_MATCH = "EXACT_MATCH"
        const val CANONICAL_MATCH = "CANONICAL_MATCH"
        const val NO_EXACT_MATCH = "NO_EXACT_MATCH"
    }

    object Provenance {
        const val SIGNED_TRUSTED_DEVICE = "SIGNED_TRUSTED_DEVICE"
        const val SIGNED_KNOWN_DEVICE_DIFFERENT_LINEAGE = "SIGNED_KNOWN_DEVICE_DIFFERENT_LINEAGE"
        const val NO_TRUSTED_PROVENANCE = "NO_TRUSTED_PROVENANCE"
        const val ATTESTATION_MISSING = "ATTESTATION_MISSING"
    }

    object Similarity {
        const val STRONG_DERIVATIVE = "STRONG_DERIVATIVE"
        const val PROBABLE_DERIVATIVE = "PROBABLE_DERIVATIVE"
        const val WEAK_SIMILARITY = "WEAK_SIMILARITY"
        const val NO_RELIABLE_SIMILARITY = "NO_RELIABLE_SIMILARITY"
    }

    object EvidenceQuality {
        const val HIGH_EVIDENTIARY_STRENGTH = "HIGH_EVIDENTIARY_STRENGTH"
        const val MODERATE_EVIDENTIARY_STRENGTH = "MODERATE_EVIDENTIARY_STRENGTH"
        const val LOW_EVIDENTIARY_STRENGTH = "LOW_EVIDENTIARY_STRENGTH"
    }

    object TransformationIndicator {
        const val COMPRESSION_DETECTED = "compression_detected"
        const val TRANSCODE_LIKELY = "transcode_likely"
        const val TRIM_LIKELY = "trim_likely"
        const val SUBTITLE_OVERLAY = "subtitle_overlay"
        const val CROP_RESIZE = "crop_resize"
        const val FRAME_RATE_CHANGE = "frame_rate_change"
    }

    // Similarity thresholds
    const val THRESHOLD_STRONG_DERIVATIVE = 0.95
    const val THRESHOLD_PROBABLE_DERIVATIVE = 0.80
    const val THRESHOLD_WEAK_SIMILARITY = 0.50
}

/**
 * Device trust constants (aligned with specification).
 */
object DeviceTrustConstants {
    // Trust tiers
    const val TIER_A = "TIER_A"  // Hardware-backed + valid attestation
    const val TIER_B = "TIER_B"  // Hardware-backed, attestation degraded
    const val TIER_C = "TIER_C"  // Software-backed or desktop
    const val TIER_D = "TIER_D"  // Revoked or untrusted

    // Lifecycle states
    const val STATE_ACTIVE = "ACTIVE"
    const val STATE_ROTATED = "ROTATED"
    const val STATE_REVOKED = "REVOKED"
    const val STATE_RECOVERED = "RECOVERED"
}

/**
 * Error codes (matching backend error taxonomy).
 */
object ErrorCodes {
    const val VALIDATION_ERROR = "VALIDATION_ERROR"
    const val EVIDENCE_NOT_FOUND = "EVIDENCE_NOT_FOUND"
    const val DEVICE_KEY_NOT_FOUND = "DEVICE_KEY_NOT_FOUND"
    const val DUPLICATE_EVIDENCE = "DUPLICATE_EVIDENCE"
    const val DUPLICATE_DEVICE_KEY = "DUPLICATE_DEVICE_KEY"
    const val CLAIM_NOT_FOUND = "CLAIM_NOT_FOUND"
    const val INVALID_SIGNATURE = "INVALID_SIGNATURE"
    const val REPLAY_DETECTED = "REPLAY_DETECTED"
    const val RATE_LIMIT_EXCEEDED = "RATE_LIMIT_EXCEEDED"
    const val INSUFFICIENT_DEVICE_TRUST = "INSUFFICIENT_DEVICE_TRUST"
    const val PRIVACY_FLAG_VIOLATION = "PRIVACY_FLAG_VIOLATION"
    const val DEGRADED_TIMESTAMP_MODE = "DEGRADED_TIMESTAMP_MODE"
    const val ALREADY_REVOKED = "ALREADY_REVOKED"
    const val INVALID_STATE = "INVALID_STATE"
    const val INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR"
    const val UNSUPPORTED_PROTOCOL_VERSION = "UNSUPPORTED_PROTOCOL_VERSION"
    const val INVALID_EVIDENCE_ENVELOPE = "INVALID_EVIDENCE_ENVELOPE"
    const val EVIDENCE_NOT_FOUND_IN_SEARCH = "EVIDENCE_NOT_FOUND_IN_SEARCH"
}

/**
 * Intent action constants.
 */
object IntentActions {
    const val ACTION_VERIFY_MEDIA = "com.dmvp.app.VERIFY_MEDIA"
    const val ACTION_REGISTER_MEDIA = "com.dmvp.app.REGISTER_MEDIA"
    const val ACTION_VIEW_EVIDENCE = "com.dmvp.app.VIEW_EVIDENCE"
    const val ACTION_DEVICE_SETTINGS = "com.dmvp.app.DEVICE_SETTINGS"
    const val ACTION_SHOW_VERDICT = "com.dmvp.app.SHOW_VERDICT"
}

/**
 * Notification constants.
 */
object NotificationConstants {
    const val CHANNEL_ID_VERIFICATION = "verification_channel"
    const val CHANNEL_ID_REGISTRATION = "registration_channel"
    const val CHANNEL_ID_SYSTEM = "system_channel"
    const val NOTIFICATION_ID_VERIFICATION = 1001
    const val NOTIFICATION_ID_REGISTRATION = 1002
    const val NOTIFICATION_ID_SYNC = 1003
}

/**
 * Regex patterns.
 */
object RegexPatterns {
    val SHA256_HEX = Regex("^[0-9a-fA-F]{64}$")
    val UUID = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    val ISO8601 = Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{3})?Z$")
    val BASE64 = Regex("^[A-Za-z0-9+/=]+$")
}

/**
 * Default values for various components.
 */
object Defaults {
    const val DEFAULT_EVIDENCE_ID = ""
    const val DEFAULT_SHA256 = ""
    const val DEFAULT_PUBLIC_KEY = ""
    const val DEFAULT_DEVICE_KEY_ID = ""

    val DEFAULT_PRIVACY_FLAGS = com.dmvp.app.data.model.PrivacyFlags(
        gps = false,
        exif = false,
        deviceInfo = true
    )

    const val DEFAULT_SCORE = 0.0
    const val MAX_SCORE = 100.0
}
