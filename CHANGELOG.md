# Changelog

## 2.0.1 - 2026-08-04

### Correctness

- Prevent cached/local packs that finish before the delayed `JOIN` activation task from entering a stale shield session and timing out.
- Read Paper's latest per-player resource-pack status before activating the delayed join shield.

### Configuration

- Migrate `config.yml` to schema 3 and rebuild it in the documented canonical order.
- Add missing settings, repair invalid known values, and remove unknown settings only after backing up the original file.
- Preserve malformed config files before restoring safe defaults; reject newer schemas without destructive downgrade.
- Use atomic replacement, content-addressed backup deduplication, a 1 MiB input bound, synchronized reloads, and a maximum of 10 automatic config backups.
- Document every setting, valid enum/boolean choice, numeric bound, and operational use directly in `config.yml`.

### Verification

- Add regression coverage for the cached-pack event race, schema migration, malformed YAML, canonical comments/order, idempotent rewrites, backup deduplication, and bounded backup retention.

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
