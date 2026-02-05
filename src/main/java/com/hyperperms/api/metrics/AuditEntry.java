package com.hyperperms.api.metrics;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * Represents an entry in the audit log.
 *
 * @param timestamp  when the action occurred
 * @param holderType the type of holder ("user" or "group")
 * @param holderId   the holder identifier (UUID or group name)
 * @param action     the action performed
 * @param permission the permission affected
 * @param executor   who performed the action (UUID or "console")
 */
public record AuditEntry(
        @NotNull Instant timestamp,
        @NotNull String holderType,
        @NotNull String holderId,
        @NotNull String action,
        @Nullable String permission,
        @Nullable String executor
) {
    /**
     * Common audit actions.
     */
    public static final class Actions {
        public static final String PERMISSION_ADD = "permission_add";
        public static final String PERMISSION_REMOVE = "permission_remove";
        public static final String PERMISSION_SET = "permission_set";
        public static final String GROUP_ADD = "group_add";
        public static final String GROUP_REMOVE = "group_remove";
        public static final String PRIMARY_GROUP_CHANGE = "primary_group_change";
        public static final String GROUP_CREATE = "group_create";
        public static final String GROUP_DELETE = "group_delete";
        public static final String PROMOTION = "promotion";
        public static final String DEMOTION = "demotion";

        private Actions() {}
    }
}
