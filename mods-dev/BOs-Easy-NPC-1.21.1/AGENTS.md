# Agent Guide — Easy NPC for NeoForge 1.21.1

## Scope

This subtree targets Minecraft 1.21.1, NeoForge 21.1.x, Java 21, and Gradle 8.14.3.
Keep changes inside this subtree unless repository-level CI wiring is required.

## Module map

- `core/Common`: loader-independent gameplay, data, commands, networking contracts, and server-safe code.
- `core/Client`: shared Minecraft client code. Nothing here may be loaded by a dedicated server entrypoint.
- `core/NeoForge`: the NeoForge adapter and composition roots. Keep this layer thin.
- `config-ui/Common`: loader-independent configuration UI protocol and shared behavior.
- `config-ui/Client`: client-only configuration screens and rendering.
- `config-ui/NeoForge`: NeoForge wiring for the configuration UI.
- `bundle`: metadata-only convenience bundle; it must not duplicate the implementation modules.
- `core/gradle/tasks`: code-generation and build-support scripts.

## Dependency direction

1. `Common` must not import NeoForge, Forge, Fabric, or Minecraft client classes.
2. `Client` may depend on `Common`, but server startup must never reference `Client` classes.
3. `NeoForge` depends inward on shared code and contains only loader registration, event adaptation, and launch wiring.
4. Put gameplay rules in `Common`; do not copy them into loader modules.
5. Put each registration family behind one bootstrap or registry owner rather than extending the mod constructor.
6. Treat output produced by `core/gradle/tasks/generateRawNPCs.gradle` as generated code. Change the generator, not generated files.

## NeoForge composition roots

- `EasyNPCMain`: thin server-safe mod entrypoint.
- `bootstrap/EnvironmentBootstrap`: paths, configuration, common data, debugging, and compatibility setup.
- `bootstrap/RegistryBootstrap`: deferred registers and registry-adjacent services.
- `bootstrap/NetworkBootstrap`: NeoForge payload registration and common network handler binding.
- `EasyNPCClient`: client-only renderer, screen, overlay, and keybinding setup.

## Build and validation

Run commands from `mods-dev/BOs-Easy-NPC-1.21.1`.

```bash
./gradlew -p core :NeoForge:compileJava --stacktrace
./gradlew -p core :NeoForge:build --stacktrace
./gradlew -p core :NeoForge:runGameTestServer --stacktrace
```

When changing `config-ui` or `bundle`, build their NeoForge targets after publishing the required local module artifacts in dependency order.
The repository-level workflow at `/.github/workflows/easy-npc-neoforge-1.21.1.yml` is the source of truth for CI in this monorepo.
Nested upstream workflow files under this subtree are not executed by GitHub from their current location.

## Definition of done

A NeoForge change is complete only when all of the following are true:

- Java 21 compilation succeeds with no missing loader APIs.
- The NeoForge jar is produced successfully.
- GameTests pass on the NeoForge game-test server.
- A dedicated-server launch does not load client-only classes.
- Client launch reaches the title screen and the NPC configuration UI can be opened in a test world.
- Networking, entity registration, menus, and saved NPC data survive a server restart smoke test.
