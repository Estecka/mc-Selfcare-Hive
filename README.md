# Self-Care Hive

## Overview
Bees are dumb. Unsupervised, they will inevitably find a way to drown themselves, and go extinct.
Unless you trap them in an air-tight cage that is, but come on, that's not how nature works.

This mod gives bees some more autonomy, allowing them to consume honey from the hive in order to heal themselves, and create offsprings when needed.

Each behaviour can be tweaked or disabled in the gamerules.

## Features
### Self-Healing
Everytime a bee exits the hive, it may consume some honey in order to heal itself by a set amount.
Bees will refrain from over-healing, unless the hive is overflowing with honey.

By default, bees consume 1 honey level to heal 1 heart at a time.

### Auto-breeding
A hive that has yet to reach its maximum population will attempt to create babies by consuming honey. Hives will keep track of when a bee leaves nest; if a bee has been gone for too long, it will be considered missing and may be replaced.

Babies may be created whenever a bee leaves the nest. Only a single adult bee is required to create an offspring. This also checks for and triggers the parent's own breeding cooldown.

By default, 5 honey levels are required for each offspring.
