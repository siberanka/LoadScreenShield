# LoadScreenShield

LoadScreenShield protects Java Edition players while a server resource pack is being downloaded and applied. It is designed for Paper and Folia networks, including setups where a proxy sends the pack before the player reaches a backend server.

## What changed in 2.0

- PacketEvents was removed. Fake blocks now use Paper's server-owned `BlockData` API, so Paper/ViaVersion performs protocol encoding instead of the plugin writing raw global block-state IDs.
- Every resource-pack status available through Minecraft 26.2 is classified. Unknown future enum values fail closed and keep the shield active until the configured timeout.
- Multiple resource packs are tracked independently; protection ends only when all observed packs are terminal.
- Player mutations and repeating work use the entity scheduler on both Paper and Folia.
- Floodgate remains optional and is detected without bundling or requiring its API.
- Config and language reloads publish immutable snapshots, preserve custom keys, validate paths and bound packet/task volume.

## Support matrix

| Platform | Minecraft | Runtime JDK | Status | Verification |
|---|---|---:|---|---|
| Paper | 1.21.1–1.21.11 | 21+ | Supported | Compiled/tested against the Paper 1.21.1 API |
| Paper | 26.1–26.2 | 25+ | Supported | Compiled/tested against Paper API `26.2.build.92-stable`; server smoke-tested on Paper 26.2 build 92 |
| Folia | 1.21.x | 21+ | Supported | Same entity-scheduler code path; CI API compatibility |
| Folia | 26.2 | 25+ | Supported | Server smoke-tested on Folia 26.2 build 1 (beta) |
| Spigot/Purpur/Leaf | — | — | Not claimed | They may work when Paper APIs are present, but are not release gates |

The plugin declares `api-version: 1.21`; older servers intentionally refuse to load it. Minecraft versions before 1.21 can report resource-pack status, but this repository's existing Paper/Folia 1.21 architecture and Adventure messaging establish the supported lower bound.

## Installation

1. Use Java 21 for Minecraft 1.21.x or Java 25 for Minecraft 26.1+.
2. Put `LoadScreenShield-2.0.0.jar` in the server's `plugins` directory.
3. PacketEvents is not required. Floodgate is optional.
4. Restart the server. Avoid production hot-reload tools; a normal restart gives reliable plugin lifecycle ordering.

## Resource-pack activation modes

`activation-mode: JOIN` is the default and protects immediately after join. Use it when Velocity/BungeeCord or another proxy sends the pack and the backend may not see the initial request.

`activation-mode: RESOURCE_PACK_STATUS` starts protection only after the backend receives `ACCEPTED`, `DOWNLOADED`, or an unknown future in-progress status. Use it when the backend sends the pack itself.

Success removes the shield. Decline, download/reload failure, invalid URL, or discard removes it with a failure message. Unknown future statuses remain protected until `timeout-seconds`, preventing a new protocol state from silently bypassing protection.

## Configuration

```yaml
schema-version: 2
lang: en
prefix: "<gray>[<gold>LoadScreenShield</gold>]</gray> "
activation-mode: JOIN
timeout-seconds: 120       # clamped to 5..1800
shield-box-radius: 2       # clamped to 1..4
shield-block-type: BLACK_WOOL
title:
  enabled: true
bedrock:
  ignore-floodgate: true
protection:
  enabled: true
  cancel-movement: true
  cancel-commands: true
  cancel-teleports: true
```

Reload safe configuration snapshots with `/loadscreenshield reload` (`loadscreenshield.admin`). Existing custom keys are retained. Invalid language names cannot escape the plugin language directory.

## Build and test

Building requires Maven 3.9.6+ and JDK 25, while the produced class files target Java 21.

```text
mvn clean verify
mvn -Ppaper-1.21 clean verify
```

The first command verifies the 26.2 upper bound; the profile verifies source compatibility with the 1.21.1 lower bound. The test suite covers every known 26.2 status, unknown future states, duplicate callbacks, and multi-pack completion.

## Upgrade and rollback

Version 2.0 adds missing config keys but does not delete custom keys or player/world data. Back up the plugin folder before upgrading. To roll back, stop the server, restore the previous JAR and the backed-up config, then restart. No database or world migration is performed.

---

## Türkçe kısa rehber

LoadScreenShield, kaynak paketi indirilip uygulanırken Java oyuncularını korur. 2.0 sürümünde ham PacketEvents blok paketleri kaldırılmıştır; blok değişikliklerini Paper doğru istemci protokolüne göre kodlar. PacketEvents artık gerekmez, Floodgate isteğe bağlıdır.

- Minecraft 1.21.1–1.21.11: Java 21+
- Minecraft 26.1–26.2: Java 25+
- Proxy paketi gönderiyorsa `activation-mode: JOIN` kullanın.
- Backend paketi gönderiyorsa `activation-mode: RESOURCE_PACK_STATUS` kullanabilirsiniz.
- Kurulumdan sonra sunucuyu normal şekilde yeniden başlatın; üretimde PlugMan benzeri sıcak yükleyiciler önerilmez.
- Ayarları `/loadscreenshield reload` komutuyla yenileyebilirsiniz.
