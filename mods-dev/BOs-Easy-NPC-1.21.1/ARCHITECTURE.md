# Architecture: Easy NPC NeoForge 1.21.1

## Goal

This copy keeps the upstream multi-loader source history, but treats NeoForge 1.21.1 as a first-class, independently verifiable target. The architecture separates portable behavior from loader adapters and makes the dependency order explicit for both humans and coding agents.

## Dependency graph

```text
bundle/NeoForge
    |
    +--> config-ui/NeoForge
    |        |
    |        +--> config-ui/Common
    |        +--> published core/NeoForge artifact
    |
    +--> published core/NeoForge artifact

core/NeoForge
    +--> core/Common
    +--> core/Client (shared client-only source)
```

Dependencies must not point upward or sideways. In particular, Core cannot depend on Config UI, and portable Common code cannot depend on NeoForge APIs.

## Source boundaries

### Core Common

Owns the NPC domain model and server-authoritative behavior: entity data, dialogs, quests, trades, commands, actions, networking records, persistence, compatibility contracts, and GameTests. Code here should compile against Minecraft/NeoForm without importing a loader implementation.

### Core Client

Owns client-only screens and transient client state shared by loaders. It is deliberately separate from `core/Common` so dedicated-server code does not acquire client imports. The NeoForge build explicitly compiles these sources into the playable Core jar and verifies representative classes are present.

### Core NeoForge

Owns only NeoForge integration: mod entry points, event-bus registration, registries, payload registration, renderer/menu registration, and NeoForge run configurations. It adapts Common contracts rather than reimplementing domain behavior.

### Config UI

`config-ui/Common` owns portable menu/configuration behavior. `config-ui/NeoForge` adapts it to NeoForge and consumes Core through the versioned Maven Local artifact produced by the Core build. This prevents direct source coupling between independently published modules.

### Bundle

The bundle is dependency metadata and launcher convenience. It does not use jar-in-jar and must not copy Core or Config UI classes into its own jar.

## Build pipeline

The top-level `build.gradle` is an orchestration layer only. Each module remains an independent Gradle build.

```text
buildNeoForgeCore
    -> buildNeoForgeConfigUi
        -> buildNeoForgeBundle

gameTestNeoForgeCore
    -> gameTestNeoForgeConfigUi
```

For full verification, the sequence is:

```text
Core build/publish
    -> Core dedicated-server GameTests
        -> Config UI build/publish
            -> Config UI dedicated-server GameTests
                -> Bundle build
```

The `build` finalizers in Core and Config UI publish their NeoForge artifacts to Maven Local. Config UI and Bundle then resolve those exact versioned artifacts. This is intentional and mirrors release-time module separation.

## Runtime safety

- Common initialization must be safe on a dedicated server.
- Client registrations belong in a client-only NeoForge entry point.
- Payload records may be declared in Common, but code that touches `Minecraft`, screens, renderers, textures, or client input must execute only on the client distribution.
- The server GameTest run is the guard against accidental client class loading during dedicated-server startup.

## Verification contract

A change is acceptable only when all of the following hold:

1. Core NeoForge compiles and its unit checks pass.
2. The Core jar contains shared client classes from `core/Client`.
3. Core starts in a NeoForge dedicated GameTest server and all registered tests pass.
4. Config UI resolves the locally published Core artifact, builds, starts in its dedicated GameTest server, and passes tests.
5. Bundle resolves both locally published modules and produces its NeoForge jar.

Run the complete contract from this directory with:

```bash
gradle verifyNeoForge
```
