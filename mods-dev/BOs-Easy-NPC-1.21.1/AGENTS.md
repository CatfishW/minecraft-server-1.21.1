# Agent Guide — Easy NPC for NeoForge 1.21.1

This guide applies to `mods-dev/BOs-Easy-NPC-1.21.1` and all child directories.

## Scope and toolchain

- Minecraft 1.21.1
- NeoForge 21.1.x, pinned in each module's `gradle.properties`
- Java 21
- Gradle 8.14.3

The upstream wrapper scripts are present, but this monorepo copy does not contain `gradle-wrapper.jar`.
Use a pinned Gradle 8.14.3 installation from this directory:

```bash
gradle buildNeoForge
gradle gameTestNeoForge
gradle verifyNeoForge
```

`verifyNeoForge` is the automated definition-of-done command. It builds every required NeoForge jar in dependency order and runs the dedicated-server GameTests.

## Module map

- `core/Common`: loader-neutral gameplay, NPC data, commands, actions, networking contracts, persistence, compatibility contracts, and tests.
- `core/Client`: shared Minecraft client screens and transient client state. It is packaged by loader builds but must never be referenced from dedicated-server initialization.
- `core/NeoForge`: NeoForge entry points, bootstrap classes, registrations, payload wiring, rendering adapters, and NeoForge run configuration.
- `config-ui/Common`: loader-neutral configuration menus and protocol behavior.
- `config-ui/NeoForge`: NeoForge adapters for Config UI; consumes the published Core artifact.
- `bundle/NeoForge`: dependency metadata only. It must not embed or duplicate Core or Config UI classes.
- `core/gradle/tasks`: generators and build-support scripts.

See `ARCHITECTURE.md` for the dependency graph and runtime boundaries.

## Dependency direction

1. `Common` must not import NeoForge, Forge, Fabric, or Minecraft client classes.
2. `core/Client` may depend on Core Common, but server code must not load it.
3. `NeoForge` depends inward on shared code and contains only loader registration, event adaptation, and launch wiring.
4. Dependency direction is one-way: Bundle -> Config UI -> Core. Core must never depend on Config UI or Bundle.
5. Core, Config UI, and Bundle exchange versioned Maven Local artifacts; do not add direct source-path coupling between those independently published modules.
6. Put gameplay rules in Common; do not copy them into loader modules.
7. Treat output produced by `core/gradle/tasks/generateRawNPCs.gradle` as generated code. Change the template/generator, not generated raw NPC classes.

## NeoForge composition roots

- `EasyNPCMain`: thin, server-safe mod entry point.
- `bootstrap/EnvironmentBootstrap`: paths, configuration, common data, debugging, and optional compatibility setup.
- `bootstrap/RegistryBootstrap`: deferred registers and registry-adjacent services.
- `bootstrap/NetworkBootstrap`: NeoForge payload registration and common network-handler binding.
- `EasyNPCClient`: client-only renderer, screen, overlay, and creative-tab setup.

Add a new registration family to the appropriate bootstrap instead of extending the mod constructor.

## Change workflow

1. Put portable behavior in the relevant Common module.
2. Add only the smallest required adapter in NeoForge.
3. Add or update a unit test or GameTest for behavior changes.
4. Run `gradle verifyNeoForge`.
5. Confirm playable jars exist in:
   - `core/NeoForge/build/libs`
   - `config-ui/NeoForge/build/libs`
   - `bundle/NeoForge/build/libs`

The repository workflow at `/.github/workflows/easy-npc-neoforge-1.21.1.yml` is the CI source of truth. Nested upstream workflows under this subtree are not executed by GitHub from their current location.

## Definition of done

Automated requirements:

- Java 21 compilation and checks pass.
- The Core jar contains the shared client screens from `core/Client`.
- Core and Config UI pass NeoForge dedicated-server GameTests.
- Config UI resolves the locally published Core artifact.
- Bundle resolves both published modules and produces its NeoForge jar.

Manual release smoke requirements:

- A NeoForge client reaches the title screen and opens an NPC configuration screen in a test world.
- A dedicated server starts with Core and Config UI installed and accepts a client connection.
- Create, save, restart, and reload an NPC; confirm its dialog/menu data persists.
- Verify one client-to-server action and one server-to-client menu/data synchronization path.
