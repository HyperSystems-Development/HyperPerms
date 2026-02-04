package com.hyperperms.template;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a permission node within a template with an optional description.
 */
public record TemplatePermission(
        @NotNull String node,
        @Nullable String description
) {
    /**
     * Creates a permission without a description.
     *
     * @param node the permission node
     */
    public TemplatePermission(@NotNull String node) {
        this(node, null);
    }

    /**
     * Checks if this permission is a negation (starts with -).
     *
     * @return true if this is a negated permission
     */
    public boolean isNegated() {
        return node.startsWith("-");
    }

    /**
     * Gets the base permission node without negation prefix.
     *
     * @return the base permission node
     */
    @NotNull
    public String getBaseNode() {
        return isNegated() ? node.substring(1) : node;
    }

    @Override
    public String toString() {
        if (description != null && !description.isEmpty()) {
            return node + " (" + description + ")";
        }
        return node;
    }
}
