# Architecture

Stratum is an isometric voxel action RPG: a block world you mine and build in,
wrapped in an ARPG shell, where the content is data rather than code.

## The module graph

```
                       :app  (Activity, navigation, composition root)
                        │
        ┌───────────────┼───────────────┬──────────────────┐
        │               │               │                  │
  :feature:play   :feature:forge  :feature:studio    :core:data
        │               │               │                  │
        │               │       ┌───────┴──────┐           │
        │               │  :legacy:data   :legacy:domain    │
        │               │       │                           │
        └───────┬───────┴───────┴───────────┬───────────────┘
                │                           │
        :core:designsystem            :core:domain ◄── :content:igbo
                │                           ▲
                └───────────────────────────┤
                                     :engine:world
```

Dependencies point inward only. Nothing in `:core:domain` knows that Android,
Room, OkHttp or Compose exist.

## The modules

| Module | Kind | Holds |
| --- | --- | --- |
| `:core:domain` | Pure Kotlin | The voxel model, content packs, combat and itemisation, enemies, skills, progression, player state, the AI ports and the generation use cases. |
| `:engine:world` | Pure Kotlin | Terrain generation, chunk streaming, mining and building rules, isometric projection, combat, loot rolling, the monster director, and the play session that joins them. |
| `:content:igbo` | Pure Kotlin | The built-in content pack. Data only. |
| `:core:data` | Android library | Adapters: the OpenAI-compatible model client and provider settings. |
| `:core:designsystem` | Android library | The visual language, driven entirely by the loaded pack's palette. |
| `:feature:play` | Android library | The isometric renderer and the play screen. |
| `:feature:forge` | Android library | AI pack generation and its preview. |
| `:legacy:domain` | Pure Kotlin | The original engine's rules, pending port. |
| `:legacy:data` | Android library | The original engine's Room and network layer. |
| `:feature:studio` | Android library | The original creator studio screens. |
| `:app` | Android app | The Activity, navigation between destinations, and the wiring that connects ports to adapters. |

## How the boundary is enforced

Not by review. `:core:domain`, `:engine:world`, `:content:igbo` and
`:legacy:domain` apply only the Kotlin JVM plugin, so the Android SDK is not on
their compile classpath and `import android.*` fails to compile.

`./gradlew architectureCheck` closes the loophole around that: it fails the
build if one of those modules picks up an Android plugin or an Android artifact.
It is wired into CI. To see it work, add `implementation(libs.androidx.core.ktx)`
to `core/domain/build.gradle.kts` and run it.

## Why content packs

The engine ships with no content. Blocks, regions, classes, lore and the
interface palette all come from a `ContentPack`, and the three sources — the
built-in module, an AI generation run, and a file a player imported — are
indistinguishable to everything downstream. Packs layer: a later pack overrides
an earlier one by id, so a generated pack can restyle a handful of blocks
without forking the whole thing.

`ContentPackAssembler` flattens the enabled packs into an `AssembledContent`, and
validates that every block a region names actually exists. That is why a
generated pack fails at the generate button rather than mid-world-generation.

The action RPG half is pack data too: damage types, weapon bases, affixes,
monsters and skills. The engine never names one. A weapon or monster referencing
a damage type nobody defined is rejected at load, because otherwise every hit of
that type would silently resolve as unresisted and quietly skew the balance of
every pack loaded alongside it — far harder to notice than a crash.

Two things are deliberately *not* pack data. Rarity tiers carry a fixed number of
affixes, and the experience curve is fixed, because a pack that could change
either could trivialise every fight in every other pack loaded with it. Packs
control what those tiers are called and what colour they are.

## Why generation logic lives in the domain

Prompt construction, JSON extraction, repair and validation are all in
`:core:domain`, behind a one-method `LanguageModelPort`. The network adapter in
`:core:data` only moves bytes.

That split is what makes the hard part testable: the generation tests run a
fake model that returns markdown-fenced JSON, missing namespaces, dangling block
references and out-of-range numbers, and assert the result is still playable —
with no network and no Android.

## Swapping world generation

Terrain has two seams, because there are two different things people want to
change.

**Describe a different landscape.** A pack ships a `TerrainRecipe`: elevation
noise layers, a terrace step, material strata. No code, so the AI pack forge or
a JSON file can author one. `terraceStep` is the important knob — smooth noise
produces a landscape of one-block steps that reads as texture and cannot be
walked or built on, while snapping heights to plateaus gives ledges you can see
and ground you can use.

**Replace the algorithm entirely.** Register a factory and name it in a recipe:

```kotlin
StratumTerrain.registry.register("mypack:caves") { context ->
    MyCaveGenerator(context.config, context.biomes, context.recipe)
}
```

A pack asking for `mypack:caves` then gets it. `TerrainGenerator` requires one
method; `BiomeSource` is a separate optional interface, so a generator with no
concept of biomes — a dungeon builder, a flat sandbox — does not have to invent
them. `WorldSession` also takes a generator directly, which is how tests run on
terrain they control.

An unknown generator id is an error rather than a silent fallback. A pack asking
for something this build does not have should say so, not quietly hand the
player a different world.

## Why sprites have two models

`SpriteSheet` is a uniform grid read left to right, with each clip a contiguous
run of cells. That is the right thing to *play* — the renderer cuts a rectangle
and the playback clock counts — and the wrong thing to *author*. It can only
describe art that already landed on a perfect grid with the states in the order
the engine expects, and generated art almost never does. It comes back with a
border the model drew, three blank cells, two duplicates, one superb attack pose
and no death frames.

So there is a second model, `SpriteAtlas`, which names frames instead of
counting them. A clip is a list of frame ids, a frame may appear in several
clips or none, and a state with no frames is unmapped rather than invalid —
which is the normal condition of a sheet halfway through being mapped. It also
carries what the grid could not: a per-frame anchor, a flip, and an on/off flag
for the cells that are not art.

`AtlasBaker` collapses one into the other. It packs a mapping into an ordinary
`SpriteSheet` — one clip per row, a frame used twice copied into both, each
frame hung from its anchor so differently cropped poses share a baseline. The
renderer, the library, the playback clock and the save format are all untouched
by hand-mapping: it is something that happened before the asset existed, not
something the engine carries at runtime.

The split runs through the platform boundary too. Where the frames go is decided
in `:core:domain` and tested without a bitmap; `SpriteAtlasBaker` in `:core:data`
only decodes, blits and encodes. That is what makes the interesting question —
does a walk built from six differently cropped cells line up — answerable by a
unit test rather than by looking at a phone.

Baking is one-way, so `SpriteProjectStore` keeps the working file: the untouched
source image beside the mapping. Re-cutting a sheet never destroys the image it
was cut from.

## Determinism

The world seed drives one `Random` for the whole session, so a run replays
exactly: the same seed produces the same terrain, the same spawns, the same crit
rolls and the same drops. `DamageCalculator` takes its crit roll as a parameter
rather than rolling internally, which is what lets a test pin a critical hit
instead of hoping for one.

## Conventions

Module build files stay declarative because `build-logic` carries the shared
configuration: `stratum.jvm`, `stratum.android.library` and
`stratum.android.compose`.

## Running it

```sh
./gradlew test                  # every module's unit tests
./gradlew architectureCheck     # boundary enforcement
./gradlew :app:assembleRelease  # the signed APK CI attaches to each pull request
```

Release builds are always signed; see `SIGNING.md` for which key and how to
switch to a real upload key.

Screenshot tests render the shell, the play screen and the forge. Re-record them
after an intentional visual change:

```sh
./gradlew :app:testDebugUnitTest -Proborazzi.test.record=true
```
