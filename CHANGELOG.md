# Changelog

Release entries are compiled from `changes/` when a version is finalized.

## [0.2.1]

**Compatible client version:** 0.2.0

### Fixed

- Require normal Tensura skill unlocks to have enough base and effective maximum magicules, with a separate `mpSkillUnlockRequirementPercent` gamerule for the acquisition threshold.
- Ask for player consent before automatic Tensura, Mysticism, and FoxAblaze skills consume a large share of permanent maximum magicules; add persistent `/skilloffers` commands and a configurable threshold.
- Preserve ReRebirth-locked skills through Tensura skill, full character, and race resets, ReRebirth resets, explicit administrator race changes, and Reincarnation Magic. All five removal loops use the same reset-only guard; updated player-storage mirrors are restored afterward.
- Route Tensura lock/unlock requests through ReRebirth, and use its synced eligibility and lock list in the updated Tensura skill GUI. Older 0.2.0 clients can still connect and use their existing ReRebirth lock controls.

## [0.2.0]

### Fixed

- Restored Tensura and ReRebirth's skill-lock and reset handling when
  SlimeThrone Extras is installed.
- Stopped SlimeThrone Extras from forcing the `resetPerSkillLock` gamerule to
  zero.
- Cleared Tensura Horizons' Void Spirit contract during a full Tensura reset, so
  a reborn player can contract another Void Spirit and regain its magic.

### Removed

- Removed SlimeThrone Extras' separate skill-lock button, prestige tab, and
  competing reset checks.
- Stopped its quest scheduling and assignment, prestige listeners, and unused
  FTB Quests bridge. Existing quest data remains saved for rollback.
- Hid `/stextras schedule`, `required`, `player`, `quests`, `race`, and
  `debug soulgrade-levels`. The wiki and useful debug commands, including
  `check-lootr`, remain.
