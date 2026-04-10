package com.finvibe.gateway.tokenfamily.domain;

/**
 * Gateway가 TokenFamily 검증 후 취해야 할 최종 동작을 나타낸다.
 */
public enum TokenFamilyValidationResult {
    ALLOW,
    DENY_UNAUTHORIZED,
    DENY_SERVICE_UNAVAILABLE
}
