# Changelog

Release entries are compiled from `changes/` when a version is finalized.

## [0.2.0]

### Fixed

- Restored Tensura and ReRebirth's skill-lock and reset handling when
  SlimeThrone Extras is installed.
- Stopped SlimeThrone Extras from forcing the `resetPerSkillLock` gamerule to
  zero.

### Removed

- Removed SlimeThrone Extras' separate skill-lock button, prestige tab, and
  competing reset checks.
- Stopped its quest scheduling and assignment, prestige listeners, and unused
  FTB Quests bridge. Existing quest data remains saved for rollback.
- Hid `/stextras schedule`, `required`, `player`, `quests`, `race`, and
  `debug soulgrade-levels`. The wiki and useful debug commands, including
  `check-lootr`, remain.
