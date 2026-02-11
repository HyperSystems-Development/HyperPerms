package com.hyperperms.migration;

import org.jetbrains.annotations.NotNull;

/**
 * Options for controlling migration behavior.
 *
 * @param conflictResolution how to handle conflicts with existing HyperPerms data
 * @param verbose            whether to include verbose output
 * @param skipDefaultUsers   whether to skip users who only have default group assignment and no custom permissions
 * @param skipExpired        whether to skip expired temporary permissions
 * @param maxNodeLength      maximum number of permission node characters to allow; nodes longer than this will be truncated with a warning
 */
public record MigrationOptions(
    @NotNull ConflictResolution conflictResolution,
    boolean verbose,
    boolean skipDefaultUsers,
    boolean skipExpired,
    int maxNodeLength
) {

    /**
     * Default migration options.
     */
    public static final MigrationOptions DEFAULT = new MigrationOptions(
        ConflictResolution.MERGE,
        false,
        false,
        true,
        256
    );

    /**
     * Creates options with default values.
     */
    public MigrationOptions() {
        this(ConflictResolution.MERGE, false, false, true, 256);
    }

    /**
     * Creates a new options builder.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for MigrationOptions.
     */
    public static final class Builder {
        private ConflictResolution conflictResolution = ConflictResolution.MERGE;
        private boolean verbose = false;
        private boolean skipDefaultUsers = false;
        private boolean skipExpired = true;
        private int maxNodeLength = 256;

        private Builder() {}

        public Builder conflictResolution(@NotNull ConflictResolution resolution) {
            this.conflictResolution = resolution;
            return this;
        }

        public Builder verbose(boolean verbose) {
            this.verbose = verbose;
            return this;
        }

        public Builder skipDefaultUsers(boolean skip) {
            this.skipDefaultUsers = skip;
            return this;
        }

        public Builder skipExpired(boolean skip) {
            this.skipExpired = skip;
            return this;
        }

        public Builder maxNodeLength(int length) {
            this.maxNodeLength = length;
            return this;
        }

        public MigrationOptions build() {
            return new MigrationOptions(conflictResolution, verbose, skipDefaultUsers, skipExpired, maxNodeLength);
        }
    }
}
