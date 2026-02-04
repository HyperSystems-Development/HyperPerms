package com.hyperperms.template;

import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Validates permission templates for correctness before application.
 */
public final class TemplateValidation {

    private TemplateValidation() {
        // Utility class
    }

    /**
     * Result of a template validation.
     */
    public record ValidationResult(
            boolean valid,
            @NotNull List<String> errors,
            @NotNull List<String> warnings
    ) {
        /**
         * Creates a successful validation result.
         */
        public static ValidationResult success() {
            return new ValidationResult(true, Collections.emptyList(), Collections.emptyList());
        }

        /**
         * Creates a successful validation result with warnings.
         */
        public static ValidationResult successWithWarnings(@NotNull List<String> warnings) {
            return new ValidationResult(true, Collections.emptyList(), warnings);
        }

        /**
         * Creates a failed validation result.
         */
        public static ValidationResult failure(@NotNull List<String> errors) {
            return new ValidationResult(false, errors, Collections.emptyList());
        }

        /**
         * Creates a failed validation result with warnings.
         */
        public static ValidationResult failure(@NotNull List<String> errors, @NotNull List<String> warnings) {
            return new ValidationResult(false, errors, warnings);
        }

        /**
         * Checks if there are any warnings.
         */
        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
    }

    /**
     * Validates a template for correctness.
     *
     * @param template the template to validate
     * @return the validation result
     */
    @NotNull
    public static ValidationResult validate(@NotNull PermissionTemplate template) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Check for empty template
        if (template.getGroups().isEmpty()) {
            errors.add("Template has no groups defined");
            return ValidationResult.failure(errors);
        }

        // Check for circular inheritance
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();
        for (String groupName : template.getGroups().keySet()) {
            if (hasCyclicInheritance(template, groupName, visited, recursionStack)) {
                errors.add("Circular inheritance detected involving group: " + groupName);
            }
        }

        // Check for missing parent references
        for (TemplateGroup group : template.getGroups().values()) {
            for (String parent : group.getParents()) {
                if (!template.getGroups().containsKey(parent)) {
                    errors.add("Group '" + group.getName() + "' references non-existent parent: " + parent);
                }
            }
        }

        // Check tracks reference valid groups
        for (TemplateTrack track : template.getTracks().values()) {
            for (String groupName : track.groups()) {
                if (!template.getGroups().containsKey(groupName)) {
                    errors.add("Track '" + track.name() + "' references non-existent group: " + groupName);
                }
            }
        }

        // Check for duplicate weights (warning, not error)
        Map<Integer, List<String>> weightGroups = new HashMap<>();
        for (TemplateGroup group : template.getGroups().values()) {
            weightGroups.computeIfAbsent(group.getWeight(), k -> new ArrayList<>()).add(group.getName());
        }
        for (Map.Entry<Integer, List<String>> entry : weightGroups.entrySet()) {
            if (entry.getValue().size() > 1) {
                warnings.add("Multiple groups have weight " + entry.getKey() + ": " + entry.getValue());
            }
        }

        // Check for default group
        boolean hasDefaultGroup = template.getGroups().containsKey("default");
        if (!hasDefaultGroup) {
            warnings.add("Template does not contain a 'default' group - users may not receive any permissions");
        }

        // Check for very high permission counts (performance warning)
        int totalPerms = template.getTotalPermissionCount();
        if (totalPerms > 500) {
            warnings.add("Template has " + totalPerms + " total permissions - consider optimizing with wildcards");
        }

        if (errors.isEmpty()) {
            return warnings.isEmpty() ? ValidationResult.success() : ValidationResult.successWithWarnings(warnings);
        }
        return ValidationResult.failure(errors, warnings);
    }

    /**
     * Checks for cyclic inheritance using DFS.
     */
    private static boolean hasCyclicInheritance(
            PermissionTemplate template,
            String groupName,
            Set<String> visited,
            Set<String> recursionStack
    ) {
        if (recursionStack.contains(groupName)) {
            return true; // Found a cycle
        }

        if (visited.contains(groupName)) {
            return false; // Already checked this path
        }

        visited.add(groupName);
        recursionStack.add(groupName);

        TemplateGroup group = template.getGroup(groupName);
        if (group != null) {
            for (String parent : group.getParents()) {
                if (hasCyclicInheritance(template, parent, visited, recursionStack)) {
                    return true;
                }
            }
        }

        recursionStack.remove(groupName);
        return false;
    }

    /**
     * Validates that a template can be applied with the given mode.
     *
     * @param template     the template to validate
     * @param existingGroups names of existing groups
     * @param mergeMode    true if merge mode, false if replace mode
     * @return the validation result
     */
    @NotNull
    public static ValidationResult validateForApply(
            @NotNull PermissionTemplate template,
            @NotNull Set<String> existingGroups,
            boolean mergeMode
    ) {
        // First validate the template itself
        ValidationResult templateValidation = validate(template);
        if (!templateValidation.valid()) {
            return templateValidation;
        }

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>(templateValidation.warnings());

        if (mergeMode) {
            // In merge mode, warn about groups that will be updated
            Set<String> overlapping = new HashSet<>(template.getGroups().keySet());
            overlapping.retainAll(existingGroups);
            if (!overlapping.isEmpty()) {
                warnings.add("The following existing groups will be updated: " + overlapping);
            }
        } else {
            // In replace mode, warn about groups that will be removed
            Set<String> toRemove = new HashSet<>(existingGroups);
            toRemove.removeAll(template.getGroups().keySet());
            if (!toRemove.isEmpty()) {
                warnings.add("The following groups will be removed: " + toRemove);
            }
        }

        if (errors.isEmpty()) {
            return warnings.isEmpty() ? ValidationResult.success() : ValidationResult.successWithWarnings(warnings);
        }
        return ValidationResult.failure(errors, warnings);
    }
}
