# Minecraft Code Breaking Changes
## 1.19.4
Current master

## 1.20.5
### No Workaround:
- `BlockEntity::readNbt` and `writeNbt` now requires a Registry Wrapper Lookup parameter.
- `BehiveBlockEntity` now has a new `BeeData` reccord that tracks ticks-in-hive and nectar, across most functions.
- `BehiveBlockEntity::tryEnterHive` and `addBee` no longer take parameters carried by `BeeData`.
- `BehiveBlockEntity::onReleaseBee` no longer directly calls `EntityType::loadEntityWithPassengers`.

## 1.21.2
### Worked around:
- `World.getGamerules()` was moved to `ServerWorld`. Use `MinecraftServer::getGamerules` instead.

## 1.21.4
### Worked around:
- `tryEnterHive` now takes a `BeeEntity` instead of an `Entity`. Target both versions and use `@Coerce`

## 1.21.5
- `NbtCompound::contains` no longer check the element type.
- `NbtCompound` various get methods are now wrapped in optionals, or require a fallback as parameter.
