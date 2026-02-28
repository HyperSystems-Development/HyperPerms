# Phase 4: Package Reorganization — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Reorganize the package structure to establish a clear `api/` vs `internal/` boundary. Add deprecated legacy re-export classes at old package paths for backward compatibility.

**Architecture:** `api/` contains stable public contracts. `internal/` contains implementation details that can change freely. `model/` contains shared domain models. Legacy class paths (e.g., `com.hyperperms.chat.ChatManager`) get deprecated shim classes that delegate to the new locations.

**Tech Stack:** Java 25, no new dependencies

**Worktree:** `.worktrees/architecture-rehaul` on branch `refactor/architecture-rehaul`

**Git Author:** `ZenithDevHQ <scrubc1ty4ever@gmail.com>`

**Depends on:** Phases 2 & 3 (command framework and chat system must be finalized first)

---

### Task 1: Move implementation classes to internal/

This is the bulk of the work. Move all non-API classes under `com.hyperperms.internal`.

**Files to move (current → new):**

**Lifecycle (already in correct location after Phase 1):**
- `lifecycle/` → `internal/lifecycle/` (if not already there)

**Command framework:**
- `command/` → `internal/command/`

**Managers:**
- `manager/GroupManagerImpl.java` → `internal/manager/GroupManagerImpl.java`
- `manager/TrackManagerImpl.java` → `internal/manager/TrackManagerImpl.java`
- `manager/UserManagerImpl.java` → `internal/manager/UserManagerImpl.java`

**Storage:**
- `storage/` → `internal/storage/`

**Resolver:**
- `resolver/` → `internal/resolver/`

**Cache:**
- `cache/` → `internal/cache/`

**Chat:**
- `chat/` → `internal/chat/`

**Context implementation:**
- `context/` → `internal/context/`

**Registry:**
- `registry/` → `internal/registry/`

**Config:**
- `config/` → `internal/config/`

**Integration:**
- `integration/` → `internal/integration/`

**Other internal packages:**
- `backup/` → `internal/backup/`
- `web/` → `internal/web/`
- `analytics/` → `internal/analytics/`
- `discovery/` → `internal/discovery/`
- `migration/` → `internal/migration/`
- `task/` → `internal/task/`
- `template/` → `internal/template/`
- `update/` → `internal/update/`
- `util/` → `internal/util/`
- `tablist/` → `internal/tablist/`
- `platform/` → `internal/platform/`
- `metrics/` → `internal/metrics/`
- `query/` → `internal/query/`

**Do NOT move:**
- `api/` — stays as is (public API)
- `model/` — stays as is (shared domain models)
- `HyperPerms.java` — stays at root (entry point)
- `HyperPermsBootstrap.java` — stays at root

**Step 1: Create the internal/ package directory structure**

```bash
cd .worktrees/architecture-rehaul/src/main/java/com/hyperperms
mkdir -p internal
```

**Step 2: Move packages using git mv**

```bash
git mv lifecycle internal/lifecycle
git mv command internal/command
git mv manager internal/manager
git mv storage internal/storage
git mv resolver internal/resolver
git mv cache internal/cache
git mv chat internal/chat
git mv context internal/context
git mv registry internal/registry
git mv config internal/config
git mv integration internal/integration
git mv backup internal/backup
git mv web internal/web
git mv analytics internal/analytics
git mv discovery internal/discovery
git mv migration internal/migration
git mv task internal/task
git mv template internal/template
git mv update internal/update
git mv util internal/util
git mv tablist internal/tablist
git mv platform internal/platform
git mv metrics internal/metrics
git mv query internal/query
```

**Step 3: Update all package declarations**

Use find-and-replace across all moved files:
- `package com.hyperperms.lifecycle` → `package com.hyperperms.internal.lifecycle`
- `package com.hyperperms.command` → `package com.hyperperms.internal.command`
- etc. for every package

**Step 4: Update all import statements across the entire codebase**

Every file that imports from moved packages needs updating. This includes:
- `HyperPerms.java` (imports many internal classes)
- `api/` classes that reference implementations
- Cross-package references within internal/

Use IDE-style refactoring or sed to bulk-update imports:
```bash
# Example for one package — repeat for all
find src -name "*.java" -exec sed -i 's/import com\.hyperperms\.cache\./import com.hyperperms.internal.cache./g' {} +
```

**Step 5: Verify it compiles**

Run: `cd .worktrees/architecture-rehaul && ./gradlew compileJava 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL (may need several rounds of fixing imports)

**Step 6: Commit**

```bash
git add -A
git commit -m "refactor(packages): move implementation classes to internal/

Establish clear api/ vs internal/ boundary.
api/ = stable public contracts.
internal/ = implementation details, can change freely.
model/ = shared domain models."
```

---

### Task 2: Create legacy re-export classes for backward compatibility

External plugins may reference classes at their old paths. Create deprecated shim classes that extend/delegate to the new locations.

**Files to create:**

The most likely externally-referenced classes are:
- `com.hyperperms.chat.ChatManager`
- `com.hyperperms.cache.PermissionCache`
- `com.hyperperms.resolver.PermissionResolver`
- `com.hyperperms.storage.StorageProvider`
- `com.hyperperms.config.HyperPermsConfig`
- `com.hyperperms.util.Logger`
- `com.hyperperms.integration.FactionIntegration`
- `com.hyperperms.integration.WerChatIntegration`

**Step 1: Create shim classes**

For each externally-referenceable class, create a deprecated class at the old path:

```java
// src/main/java/com/hyperperms/chat/ChatManager.java
package com.hyperperms.chat;

/**
 * @deprecated Use {@link com.hyperperms.internal.chat.ChatManager} instead.
 * This class exists for backward compatibility with external plugins.
 */
@Deprecated(forRemoval = true, since = "3.0.0")
public class ChatManager extends com.hyperperms.internal.chat.ChatManager {
    public ChatManager(com.hyperperms.HyperPerms plugin) {
        super(plugin);
    }
}
```

For interfaces or classes that can't be extended simply, use delegation:

```java
// src/main/java/com/hyperperms/storage/StorageProvider.java
package com.hyperperms.storage;

/**
 * @deprecated Use {@link com.hyperperms.internal.storage.StorageProvider} instead.
 */
@Deprecated(forRemoval = true, since = "3.0.0")
public interface StorageProvider extends com.hyperperms.internal.storage.StorageProvider {
    // Inherits all methods — no additional code needed for interfaces
}
```

**Note:** Not every class needs a shim — only those likely referenced by external plugins. The implementer should use judgement based on what's exposed via `HyperPerms` getters and the `api/` package.

**Step 2: Verify it compiles**

Run: `cd .worktrees/architecture-rehaul && ./gradlew compileJava 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/com/hyperperms/chat/ChatManager.java src/main/java/com/hyperperms/storage/StorageProvider.java
# ... add all shim classes
git commit -m "refactor(packages): add deprecated legacy re-exports for backward compatibility

External plugins referencing old package paths will continue to work.
Classes are marked @Deprecated(forRemoval = true, since = \"3.0.0\")
with Javadoc pointing to the new locations."
```

---

### Task 3: Verify API package is clean

**Step 1: Review api/ package contents**

Ensure `api/` only contains:
- Interfaces and abstract types
- Event classes
- Value objects (records, enums)
- No implementation logic

Check that no `api/` class imports from `internal/` — that would be a dependency inversion violation.

```bash
grep -r "import com.hyperperms.internal" src/main/java/com/hyperperms/api/ | head -20
```

Expected: No results. If there are any, refactor the offending imports.

**Step 2: Verify and commit any fixes**

```bash
git add -A
git commit -m "refactor(packages): ensure api/ has no dependencies on internal/"
```

---

## Verification Checklist

1. `./gradlew compileJava` — passes
2. `./gradlew shadowJar` — produces valid JAR
3. No `api/` class imports from `internal/`
4. All existing functionality works (commands, chat, storage, integrations)
5. Legacy import paths (`com.hyperperms.chat.ChatManager`) still resolve at compile time
6. Deprecated warnings appear when legacy paths are used
