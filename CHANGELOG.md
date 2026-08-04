# Changelog

## 2.0.0 - 2026-08-04

### Security and correctness

- Replace raw PacketEvents global block-state packets with Paper-owned `BlockData` changes to prevent cross-version protocol encoding failures.
- Add fail-closed handling for unknown future resource-pack statuses.
- Track concurrent pack IDs and make repeated/late terminal callbacks harmless.
- Cancel additional inventory, interaction, teleport, bucket, consume, held-item, outgoing-damage and pickup paths while shielded.
- Validate language file names and preserve custom config/language keys.

### Compatibility

- Add Paper 26.2 (`26.2.build.92-stable`) and JDK 25 build support while retaining Java 21 bytecode and Paper 1.21.1 API compatibility.
- Use Paper's entity scheduler for Paper/Folia player ownership.
- Remove the PacketEvents dependency and make Floodgate a reflection-based optional integration.

### Operations

- Add bounded overlay radius and timeout settings, schema version 2, unit tests and a two-edge CI build.
- Replace unsupported hot-reload claims with restart and rollback guidance.
