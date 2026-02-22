# Contributing to HyperPerms

Thank you for your interest in contributing to HyperPerms! This guide covers the development setup and contribution workflow for HyperPerms and the broader HyperSystems ecosystem.

## Getting Started

### Prerequisites

- **Java 25** (build and runtime)
- **Gradle 9.3.0+** (included via wrapper — no manual install needed)
- **Git** for version control

### Soft Dependencies

HyperPerms compiles against one optional mod. Download the JAR and place it in `HyperPerms/libs/`:

| JAR | Required | Download |
|-----|----------|----------|
| VaultUnlocked-Hytale | Yes (compile) | [CurseForge](https://www.curseforge.com/hytale/mods/vaultunlocked) |

The Hytale Server API is resolved automatically from `maven.hytale.com` — no local JAR needed.

### Repository Structure

HyperPerms lives inside the HyperSystems multi-project workspace:

```
HyperSystems/
├── HyperPerms/       # This plugin
├── HyperHomes/       # Home teleportation
├── HyperFactions/    # Faction management
├── HyperWarp/        # Warps, spawns, TPA
├── HyperSpawns/      # Mob spawn zone control
├── servers/          # Dev and prerelease servers
├── settings.gradle   # Multi-project configuration
└── build.gradle      # Root build with shared properties
```

Each plugin has its own Git repository with independent versioning.

### Cloning

```bash
# Clone the HyperPerms repository
git clone git@github.com:HyperSystemsDev/HyperPerms.git
```

## Building

All builds use the root Gradle wrapper. **Never use subproject wrappers.**

```bash
# Build HyperPerms only
./gradlew :HyperPerms:shadowJar

# Build all plugins
./gradlew buildAll

# Clean and rebuild (recommended after branch switches)
./gradlew :HyperPerms:clean :HyperPerms:shadowJar --no-build-cache
```

Output JAR: `HyperPerms/build/libs/HyperPerms-<version>.jar`

### Shadow Plugin

Dependencies are relocated into the JAR to avoid conflicts:
- `com.google.gson` -> `com.hyperperms.lib.gson`
- `com.github.benmanes.caffeine` -> `com.hyperperms.lib.caffeine`
- `org.yaml.snakeyaml` -> `com.hyperperms.lib.snakeyaml`

The `jar` task has `archiveClassifier = 'plain'` to prevent it from overwriting the shadow JAR in multi-project builds.

## Deploying to Test Server

```bash
# Build all plugins and deploy to test server
./gradlew buildAndDeploy

# Deploy already-built JARs only
./gradlew deployMods
```

### Server Management

```bash
# Start the test server (uses screen)
cd servers/dev && ./start.sh

# Attach to server console
screen -r hytale-server

# Stop the server
screen -S hytale-server -X stuff "stop\n"
```

## Code Style

### Java 25 Features

- Use **records** for immutable data models
- Use **pattern matching** where it improves clarity
- Use **sealed interfaces** for closed type hierarchies

### Conventions

- **`Message.join()`** for message formatting — never `.then()` or legacy color codes
- **`@NotNull` / `@Nullable`** annotations on all public API parameters and return types
- **`ConcurrentHashMap`** for thread-safe collections
- **`CompletableFuture`** for all async storage operations
- **No raw types** — always specify generic parameters
- **Package-private by default** — only `public` what needs to be accessed externally

### Naming

- Manager classes in `com.hyperperms.manager`
- Data records in `com.hyperperms.data`
- Commands follow `<Action>Command` naming (e.g., `CheckCommand`)
- Integration classes in `com.hyperperms.integration`

## Commit Guidelines

We use [Conventional Commits](https://www.conventionalcommits.org/):

```
feat: add context-aware permission resolution
fix: wildcard matching ignores negated nodes
docs: update permission node reference
refactor: extract caching logic into CacheManager
chore: bump Caffeine to 3.1.8
```

### Rules

1. **Add specific files by name** — never use `git add -A` or `git add .`
2. **Write meaningful messages** — focus on "why" not "what"
3. **One logical change per commit** — don't mix features with refactors

## Branch Strategy

| Branch | Purpose |
|--------|---------|
| `main` | Stable releases only |
| `dev/phase1` | Primary development branch |
| `feat/<name>` | Feature branches (branch from `dev/phase1`) |
| `fix/<name>` | Bug fix branches |

### Workflow

1. Branch from `dev/phase1`: `git checkout -b feat/my-feature dev/phase1`
2. Make changes, commit with conventional commit messages
3. Push and open a PR against `dev/phase1`
4. After review, merge into `dev/phase1`
5. `dev/phase1` merges into `main` for releases

## Testing

```bash
# Run all tests
./gradlew testAll

# Run HyperPerms tests only
./gradlew :HyperPerms:test
```

Tests use **JUnit Jupiter 5.10.2**. Place test files in `src/test/java/`.

## Reporting Issues

- **Bug reports:** Use the [Bug Report template](https://github.com/HyperSystemsDev/HyperPerms/issues/new?template=bug_report.md) on GitHub
- **Feature requests:** Use the [Feature Request template](https://github.com/HyperSystemsDev/HyperPerms/issues/new?template=feature_request.md) on GitHub
- **Discord:** Join [our server](https://discord.com/invite/aZaa5vcFYh) for discussion

## License

By contributing, you agree that your contributions will be licensed under the [GPLv3](LICENSE).
