package com.example.social.server.entity;

/** Типи дій адміністратора, що записуються в журнал. */
public enum AuditAction {
    WEIGHTS_UPDATE,
    WEIGHTS_RESTORE,
    USER_BLOCK,
    USER_UNBLOCK,
    USER_ROLE_CHANGE,
    USER_PROFILE_EDIT,
    USER_PASSWORD_RESET,
    POST_DELETE,
    COMMENT_DELETE,
    ECHO_SNAPSHOT
}