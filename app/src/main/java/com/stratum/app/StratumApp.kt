package com.stratum.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stratum.core.designsystem.component.ActionEmphasis
import com.stratum.core.designsystem.component.SectionLabel
import com.stratum.core.designsystem.component.StratumAction
import com.stratum.core.designsystem.component.StratumDivider
import com.stratum.core.designsystem.component.StratumPanel
import com.stratum.core.designsystem.component.StratumSection
import com.stratum.core.designsystem.component.StratumWell
import com.stratum.core.designsystem.theme.Space
import com.stratum.core.designsystem.theme.safeContent
import com.stratum.core.designsystem.theme.StratumTheme
import com.stratum.feature.play.PlayScreen
import com.stratum.core.domain.content.ContentPack
import com.stratum.feature.forge.ForgeScreen
import com.stratum.feature.forge.ForgeViewModel
import com.stratum.feature.forge.SpriteForgeScreen
import com.stratum.feature.forge.SpriteForgeViewModel
import com.stratum.feature.forge.PoseForgeScreen
import com.stratum.feature.forge.WeaponForgeScreen
import com.stratum.feature.forge.WeaponForgeViewModel
import com.stratum.feature.forge.PoseForgeViewModel
import com.stratum.feature.forge.SpriteMapperScreen
import com.stratum.feature.forge.SpriteMapperViewModel
import com.stratum.feature.hero.ClassForgeScreen
import com.stratum.feature.hero.ClassForgeViewModel
import com.stratum.core.data.hero.CustomClassStore
import android.graphics.BitmapFactory
import com.stratum.core.data.sprite.GeneratedSheetPreparer
import com.stratum.core.data.sprite.PoseGuideRenderer
import com.stratum.core.data.sprite.PoseSheetComposer
import com.stratum.core.data.sprite.WeaponPreparer
import com.stratum.core.data.sprite.SpriteAtlasBaker
import com.stratum.core.domain.sprite.SheetPreparation
import com.stratum.core.domain.sprite.SpriteMapper
import com.stratum.core.domain.sprite.SpriteNamespace
import com.stratum.core.domain.ai.ImageReference
import com.stratum.core.domain.ai.PoseScript
import com.stratum.core.domain.ai.PoseStep
import com.stratum.core.domain.sprite.OpenPoseImageReader
import com.stratum.core.domain.sprite.OpenPoseImport
import com.stratum.core.domain.sprite.OpenPoseJson
import com.stratum.core.domain.sprite.PoseGuides
import com.stratum.core.domain.sprite.Skeleton
import com.stratum.core.domain.sprite.WeaponPosing
import com.stratum.core.domain.sprite.WeaponRig
import com.stratum.core.domain.content.CustomClassPack
import com.stratum.core.domain.content.HeroClassDefinition
import com.stratum.core.designsystem.component.StratumChip
import com.stratum.feature.play.DrawableSprite
import com.stratum.feature.play.DrawableWeapon
import com.stratum.feature.play.SpriteKey
import com.stratum.feature.play.PlayScreen as PlayScreenRoute
import com.stratum.feature.play.PlayViewModel

/** Top-level destinations. Deliberately few: the game is the app, not a tab in it. */
private enum class Destination { HOME, PLAY, CLASSES, FORGE, SPRITES, POSES, WEAPONS, MAPPER, SETTINGS, STUDIO }

/**
 * The app shell.
 *
 * Navigation is a single state value rather than a nav graph: with three
 * destinations and no deep links, a graph would be ceremony. It becomes one when
 * the studio screens are broken into their own feature modules.
 */
@Composable
fun StratumApp(
    modifier: Modifier = Modifier,
    studioContent: @Composable (onBack: () -> Unit) -> Unit = {},
) {
    var destination by remember { mutableStateOf(Destination.HOME) }

    val context = LocalContext.current

    // Packs the player has forged this session. Adding one rebuilds the
    // assembled content, so the next world is made of the new material.
    var forgedPacks by remember { mutableStateOf(emptyList<ContentPack>()) }

    // Classes the player built, loaded as a pack of their own so a class they
    // made and a class the pack shipped travel the same road.
    val classStore = remember(context) { CustomClassStore(context) }
    var classRevision by remember { mutableStateOf(0) }
    val customClasses = remember(classRevision) { classStore.all() }
    val content = remember(forgedPacks, customClasses) {
        GameSetup.assemble(
            forgedPacks + if (customClasses.isEmpty()) emptyList()
            else listOf(CustomClassPack.of(customClasses)),
        )
    }

    // Null means "whatever the packs list first", which is what a player who has
    // never opened the class forge should get.
    var heroClassId by remember { mutableStateOf<String?>(null) }

    // A new seed per run, but stable across recomposition so walking around does
    // not regenerate the world under the player.
    var seed by remember { mutableStateOf(System.currentTimeMillis()) }
    val config = remember(seed) { GameSetup.worldConfig(seed) }

    val ai = remember(context) { AiWiring(context) }

    // Sheets generated this session join the loaded packs, so a drawing made
    // five minutes ago is used by the world exactly like one a pack shipped.
    var spriteRevision by remember { mutableStateOf(0) }
    // What the player is holding. A weapon is a separate drawing attached at
    // the hand, so changing it is changing one id -- no character is redrawn.
    var equippedWeaponId by remember { mutableStateOf<String?>(null) }
    val spriteSheets = remember(spriteRevision) { ai.sprites.all() }
    val contentWithSprites = remember(content, spriteSheets) {
        content.withSpriteSheets(spriteSheets)
    }

    // Keyed on the chosen class: the player's art is a property of who they are
    // playing, and resolving it without that was the whole bug — every class
    // was drawn with whichever hero sheet happened to be newest.
    val spriteResolver = remember(spriteRevision, contentWithSprites, heroClassId, equippedWeaponId) {
        // The resolver is asked for a sprite on every drawn frame, for every
        // actor, so anything built here has to be built once and kept. A rig is
        // a map the size of the sheet's frame count; rebuilding it per frame
        // would allocate one per actor per frame inside the draw loop.
        val rigs = HashMap<String, WeaponRig>()
        val held = equippedWeaponId?.let { id ->
            val weapon = ai.weapons.find(id)
            val bitmap = ai.weapons.bitmapFor(id)
            if (weapon != null && bitmap != null) weapon to bitmap.asImageBitmap() else null
        }

        // Named rather than left as a bare trailing lambda: with a statement
        // above it, the compiler reads `{ ... }` as an argument to that
        // statement instead of as the value being remembered.
        val resolve: (SpriteKey) -> DrawableSprite? = { key: SpriteKey ->
            // Candidates in order of preference rather than one guess. The
            // first choice can resolve to a sheet with no usable image — one
            // that came back blank, or a pack sheet with no pixels on this
            // device — and picking it and then drawing nothing is how a
            // character ends up with no skin and no explanation.
            val candidates = when (key) {
                SpriteKey.Player -> {
                    val chosen = heroClassId ?: contentWithSprites.heroClasses.firstOrNull()?.id
                    // A player who has drawn art but assigned none still gets
                    // to see it, rather than art they made sitting unused
                    // because they missed a picker.
                    listOfNotNull(chosen?.let(contentWithSprites::sheetForHero)) +
                        contentWithSprites.spriteSheets
                            .filter { SpriteNamespace.servesHero(it.id) }
                }
                is SpriteKey.Monster ->
                    listOfNotNull(contentWithSprites.sheetForEnemy(key.definitionId)) +
                        contentWithSprites.spriteSheets
                            .filter { SpriteNamespace.servesMonster(it.id) }
            }

            candidates.distinctBy { it.id }.firstNotNullOfOrNull { found ->
                ai.sprites.drawableBitmapFor(found.id)?.let { bitmap ->
                    DrawableSprite(
                        sheet = found,
                        image = bitmap.asImageBitmap(),
                        // Only the player carries one for now. Giving monsters
                        // weapons is the same mechanism plus a decision about
                        // which monster holds what, which is pack data.
                        weapon = if (key == SpriteKey.Player && held != null) {
                            DrawableWeapon(
                                sprite = held.first,
                                image = held.second,
                                rig = rigs.getOrPut(found.id) {
                                    WeaponPosing.rigFor(
                                        sheet = found,
                                        fit = ai.weaponFits.fitFor(found.id),
                                        // The poses the art was drawn against.
                                        // Rigging against anything else hangs
                                        // the sword off a hand that is not there.
                                        guides = ai.poseGuides.guidesFor(found.id),
                                    )
                                },
                            )
                        } else {
                            null
                        },
                    )
                }
            }
        }
        resolve
    }

    when (destination) {
        Destination.HOME -> HomeScreen(
            packName = content.packs.joinToString(" + ") { it.name },
            blockCount = content.registry.size,
            biomeCount = content.biomes.size,
            classCount = content.heroClasses.size,
            heroClasses = content.heroClasses,
            selectedClassId = heroClassId ?: content.heroClasses.firstOrNull()?.id,
            onSelectClass = { heroClassId = it },
            onBuildClass = { destination = Destination.CLASSES },
            onDescend = {
                seed = System.currentTimeMillis()
                destination = Destination.PLAY
            },
            onForge = { destination = Destination.FORGE },
            onSprites = { destination = Destination.SPRITES },
            spriteCount = spriteSheets.size,
            onSettings = { destination = Destination.SETTINGS },
            onStudio = { destination = Destination.STUDIO },
            modifier = modifier,
        )

        Destination.PLAY -> {
            // Keyed so forging a pack or starting a new run builds a fresh
            // session rather than reusing the previous world.
            key(contentWithSprites, config, heroClassId) {
                val viewModel: PlayViewModel = viewModel(
                    factory = PlayViewModel.factory(
                        contentWithSprites, config,
                        heroClassId = heroClassId,
                        spriteResolver = spriteResolver,
                    ),
                )
                PlayScreenRoute(
                    viewModel = viewModel,
                    modifier = modifier,
                    onOpenMenu = { destination = Destination.HOME },
                )
            }
        }

        Destination.CLASSES -> {
            val classViewModel: ClassForgeViewModel = viewModel(
                // Keyed on the sprite revision too. Without it the picker is
                // built once and then lists whatever art existed at that
                // moment, so a character generated afterwards cannot be
                // chosen -- which looks exactly like the generator not having
                // worked.
                key = "classes-$classRevision-$spriteRevision",
                factory = ClassForgeViewModel.factory(
                    content = content,
                    saveClass = { hero ->
                        classStore.save(hero)
                        classRevision++
                    },
                    deleteClass = { id ->
                        classStore.delete(id)
                        // Playing as a class that no longer exists would silently
                        // fall back to another one; forget the choice instead.
                        if (heroClassId == id) heroClassId = null
                        classRevision++
                    },
                    loadClasses = classStore::all,
                    loadSheets = ai.sprites::all,
                ),
            )
            ClassForgeScreen(
                viewModel = classViewModel,
                modifier = modifier,
                onBack = { destination = Destination.HOME },
            )
        }

        Destination.FORGE -> {
            val forgeViewModel: ForgeViewModel = viewModel(
                factory = ForgeViewModel.factory(
                    generatePack = ai.generateContentPack,
                    generateLore = ai.generateLore,
                    isProviderConfigured = ai::isConfigured,
                    onPackAccepted = { pack -> forgedPacks = forgedPacks + pack },
                ),
            )
            ForgeScreen(
                viewModel = forgeViewModel,
                modifier = modifier,
                onBack = { destination = Destination.HOME },
                onOpenSettings = { destination = Destination.SETTINGS },
            )
        }

        Destination.SPRITES -> {
            val spriteViewModel: SpriteForgeViewModel = viewModel(
                factory = SpriteForgeViewModel.factory(
                    generateSheet = ai.generateSpriteSheet,
                    saveSheet = { sheet, bytes ->
                        // Keyed and checked before it is stored, so a sheet on
                        // disk is always one the world can draw, cut on the grid
                        // the model actually drew. Doing either at draw time
                        // would pay the cost every frame.
                        val prepared = GeneratedSheetPreparer.prepare(sheet, bytes)
                        ai.sprites.save(prepared.sheet, prepared.bytes)
                        spriteRevision++
                        SheetPreparation(
                            prepared.sheet,
                            prepared.keyStrategy,
                            prepared.grid,
                            prepared.looksEmpty,
                        )
                    },
                    loadSheets = ai.sprites::all,
                    deleteSheet = { id ->
                        ai.sprites.delete(id)
                        spriteRevision++
                    },
                    isProviderConfigured = ai::isConfigured,
                ),
            )
            SpriteForgeScreen(
                viewModel = spriteViewModel,
                modifier = modifier,
                onBack = { destination = Destination.HOME },
                onOpenSettings = { destination = Destination.SETTINGS },
                // The drawable check, so a blank sheet reads as blank here
                // rather than as an empty rectangle the player has to
                // interpret.
                previewFor = { id -> ai.sprites.drawableBitmapFor(id)?.asImageBitmap() },
                onMapFrames = { destination = Destination.MAPPER },
                onPoseForge = { destination = Destination.POSES },
                onWeaponForge = { destination = Destination.WEAPONS },
            )
        }

        Destination.POSES -> {
            val poseViewModel: PoseForgeViewModel = viewModel(
                factory = PoseForgeViewModel.factory(
                    drawReference = { request, observer ->
                        ai.generateBasePose(request, observer)
                    },
                    drawPose = { request, observer -> ai.generatePoseFrame(request, observer) },
                    guideFor = { step, guides ->
                        // The same skeleton the weapon rig reads, drawn. One
                        // source of truth for where the body is, so the art and
                        // the sword can never disagree about it.
                        guides.poseFor(
                            state = step.state,
                            index = step.index,
                            frameCount = PoseScript.posesFor(step.state).size,
                        )?.let { pose ->
                            PoseGuideRenderer.render(pose, style = guides.style)
                                ?.let { ImageReference(it) }
                        }
                    },
                    readGuideImage = { bytes ->
                        // Pose libraries ship the rendered skeleton, not its
                        // keypoints, so the picture is read back to find the
                        // joints the weapon needs.
                        val bitmap = SpriteAtlasBaker.decode(bytes)
                        val pose = bitmap?.let {
                            val found = OpenPoseImageReader.read(
                                pixels = SpriteAtlasBaker.pixelsOf(it),
                                width = it.width,
                                height = it.height,
                            )
                            it.recycle()
                            found?.let(OpenPoseImport::toPose)
                        }
                        pose?.let(OpenPoseImport::normalised)
                    },
                    readGuideJson = { text ->
                        OpenPoseJson.parse(text)
                            ?.let(OpenPoseImport::toPose)
                            ?.let(OpenPoseImport::normalised)
                    },
                    loadGuides = ai.poseGuides::guidesFor,
                    saveGuides = { setId, guides ->
                        ai.poseGuides.save(setId, guides)
                        // The resolver caches rigs, so a changed pose source has
                        // to rebuild them or the sword keeps the old hand.
                        spriteRevision++
                    },
                    saveReference = ai.poses::saveReference,
                    loadReference = ai.poses::reference,
                    hasReference = ai.poses::hasReference,
                    savePose = ai.poses::savePose,
                    dropPose = ai.poses::deletePose,
                    posesDrawn = ai.poses::keysIn,
                    composeSheet = { setId, plan ->
                        // Loaded by key rather than all at once: a full
                        // character is forty 1024-pixel images, which is more
                        // than a phone will hold decoded at the same time.
                        val composed = PoseSheetComposer.compose(plan) { key ->
                            ai.poses.pose(setId, key)
                        }
                        composed?.let {
                            ai.sprites.save(it.sheet, it.bytes)
                            spriteRevision++
                            it.packed()
                        }
                    },
                    isProviderConfigured = ai::isConfigured,
                ),
            )
            // Which frame an imported pose is destined for. The picker hands
            // back a Uri and nothing else, so the step has to be remembered
            // across the trip out to the system and back.
            var importingStep by remember { mutableStateOf<PoseStep?>(null) }
            val poseFilePicker = rememberLauncherForActivityResult(
                ActivityResultContracts.PickVisualMedia(),
            ) { uri ->
                val step = importingStep
                importingStep = null
                if (uri != null && step != null) {
                    val bytes = runCatching {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }.getOrNull()
                    if (bytes != null) poseViewModel.importGuideImage(step, bytes)
                }
            }

            PoseForgeScreen(
                viewModel = poseViewModel,
                modifier = modifier,
                onImportPose = { step ->
                    importingStep = step
                    poseFilePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onBack = { destination = Destination.SPRITES },
                onOpenSettings = { destination = Destination.SETTINGS },
                referenceFor = { setId ->
                    ai.poses.reference(setId)?.let { bytes ->
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    }
                },
                sheetPreviewFor = { id -> ai.sprites.drawableBitmapFor(id)?.asImageBitmap() },
            )
        }

        Destination.WEAPONS -> {
            val weaponViewModel: WeaponForgeViewModel = viewModel(
                factory = WeaponForgeViewModel.factory(
                    drawWeapon = { request, observer -> ai.generateWeapon(request, observer) },
                    storeWeapon = { request, bytes ->
                        // Keyed and trimmed before it is stored, because the
                        // grip is a fraction of the weapon's box and a weapon
                        // adrift on an empty canvas would be held by the air
                        // beside it.
                        WeaponPreparer.prepare(
                            id = "weapon:${request.slug()}",
                            name = request.subject.trim(),
                            kind = request.kind,
                            bytes = bytes,
                        )?.let { prepared ->
                            ai.weapons.save(prepared.weapon, prepared.bytes)
                            prepared.weapon
                        }
                    },
                    loadWeapons = ai.weapons::all,
                    loadSheets = { spriteSheets },
                    fitFor = ai.weaponFits::fitFor,
                    saveFit = { sheetId, fit ->
                        ai.weaponFits.save(sheetId, fit)
                        // The resolver caches rigs, so it has to be rebuilt for
                        // a corrected fit to reach the world.
                        spriteRevision++
                    },
                    deleteWeapon = { id ->
                        ai.weapons.delete(id)
                        if (equippedWeaponId == id) equippedWeaponId = null
                    },
                    isProviderConfigured = ai::isConfigured,
                ),
            )
            WeaponForgeScreen(
                viewModel = weaponViewModel,
                modifier = modifier,
                onBack = { destination = Destination.SPRITES },
                onOpenSettings = { destination = Destination.SETTINGS },
                onEquip = { id -> equippedWeaponId = id },
                equippedId = equippedWeaponId,
                previewFor = { id -> ai.weapons.bitmapFor(id)?.asImageBitmap() },
                sheetImageFor = { id -> ai.sprites.drawableBitmapFor(id)?.asImageBitmap() },
            )
        }

        Destination.MAPPER -> {
            val mapperViewModel: SpriteMapperViewModel = viewModel(
                factory = SpriteMapperViewModel.factory(
                    openProject = ai.spriteProjects::load,
                    sourcePixels = { atlas ->
                        ai.spriteProjects.sourceFor(atlas.id)?.let(SpriteAtlasBaker::pixelsOf)
                    },
                    startProject = { sheet ->
                        // The art is copied into a project of its own before
                        // anything is mapped, so re-cutting a sheet can never
                        // damage the only copy of the image it was cut from.
                        val bitmap = ai.sprites.bitmapFor(sheet.id)
                        val bytes = ai.sprites.bytesFor(sheet.id)
                        if (bitmap == null || bytes == null) {
                            null
                        } else {
                            SpriteMapper.fromSheet(
                                sheet = sheet,
                                imageWidth = bitmap.width,
                                imageHeight = bitmap.height,
                            ).also { ai.spriteProjects.save(it, bytes) }
                        }
                    },
                    saveProject = { atlas -> ai.spriteProjects.save(atlas) },
                    bakeAtlas = { atlas ->
                        val source = ai.spriteProjects.sourceFor(atlas.id)
                        val baked = source?.let { SpriteAtlasBaker.bake(atlas, it) }
                        baked?.let {
                            // Saved under the atlas's own id, so re-baking a
                            // mapping replaces that sheet rather than leaving
                            // the world to choose between two versions of it.
                            ai.sprites.save(it.sheet, it.bytes)
                            spriteRevision++
                            it.sheet
                        }
                    },
                    loadSheets = ai.sprites::all,
                ),
            )
            SpriteMapperScreen(
                viewModel = mapperViewModel,
                modifier = modifier,
                onBack = { destination = Destination.SPRITES },
                sourceFor = { atlas ->
                    ai.spriteProjects.sourceFor(atlas.id)?.asImageBitmap()
                },
            )
        }

        Destination.SETTINGS -> ProviderSettingsScreen(
            initial = ai.settings.load(),
            onSave = ai.settings::save,
            onBack = { destination = Destination.HOME },
            modifier = modifier,
        )

        Destination.STUDIO -> studioContent { destination = Destination.HOME }
    }
}

/**
 * The landing screen. It reports what the loaded pack actually contains, so the
 * customization story is visible before the player ever enters a world.
 */
@Composable
private fun HomeScreen(
    packName: String,
    blockCount: Int,
    biomeCount: Int,
    classCount: Int,
    heroClasses: List<HeroClassDefinition>,
    selectedClassId: String?,
    onSelectClass: (String) -> Unit,
    onBuildClass: () -> Unit,
    onDescend: () -> Unit,
    onForge: () -> Unit,
    onSprites: () -> Unit,
    spriteCount: Int,
    onSettings: () -> Unit,
    onStudio: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StratumTheme.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
            // Inset before the scroll, so the content scrolls under nothing and
            // the first line is never behind the status bar on a tall phone.
            .safeContent()
            .verticalScroll(rememberScrollState())
            .padding(Space.large),
    ) {
        Spacer(Modifier.height(Space.huge))

        Text(
            text = "STRATUM",
            style = MaterialTheme.typography.displaySmall,
            color = colors.ink,
        )
        Text(
            text = "An isometric world you dig apart and rebuild.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.inkMuted,
        )

        Spacer(Modifier.height(Space.wide))

        StratumSection(
            title = "Loaded pack",
            subtitle = packName,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.small),
            ) {
                Stat("Blocks", blockCount, Modifier.weight(1f))
                Stat("Regions", biomeCount, Modifier.weight(1f))
                Stat("Classes", classCount, Modifier.weight(1f))
            }
            Spacer(Modifier.height(Space.medium))
            StratumDivider()
            Spacer(Modifier.height(Space.medium))
            Text(
                text = "Every block, region, class and line of lore above comes from a content " +
                    "pack. The engine ships with none of its own, so a generated pack sits beside " +
                    "the built-in one as an equal.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkMuted,
            )
        }

        Spacer(Modifier.height(Space.large))

        StratumPanel(modifier = Modifier.fillMaxWidth()) {
            SectionLabel("Begin")
            Spacer(Modifier.height(Space.medium))

            // The class is chosen before the run, not after: it decides the
            // spawn, the starting weapon and the skill bar.
            if (heroClasses.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Space.small),
                ) {
                    items(heroClasses, key = HeroClassDefinition::id) { hero ->
                        StratumChip(
                            label = hero.name,
                            selected = hero.id == selectedClassId,
                            onClick = { onSelectClass(hero.id) },
                        )
                    }
                }
                heroClasses.firstOrNull { it.id == selectedClassId }?.let { hero ->
                    Spacer(Modifier.height(Space.small))
                    Text(
                        text = "${hero.resolvedStats.maxHealth} hp · " +
                            "${hero.resolvedStats.attackPower} attack · " +
                            "${hero.baseResource} ${hero.resourceName.lowercase()}" +
                            if (hero.title.isNotBlank()) " · ${hero.title}" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.inkMuted,
                    )
                }
                Spacer(Modifier.height(Space.medium))
            }

            StratumAction(
                label = "Descend",
                onClick = onDescend,
                emphasis = ActionEmphasis.PRIMARY,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.small))
            StratumAction(
                label = "Build a class",
                onClick = onBuildClass,
                emphasis = ActionEmphasis.SECONDARY,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.small))
            StratumAction(
                label = "Forge a pack with AI",
                onClick = onForge,
                emphasis = ActionEmphasis.SECONDARY,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.small))
            StratumAction(
                label = if (spriteCount > 0) "Sprite forge ($spriteCount)" else "Sprite forge",
                onClick = onSprites,
                emphasis = ActionEmphasis.SECONDARY,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.small))
            StratumAction(
                label = "Creator studio",
                onClick = onStudio,
                emphasis = ActionEmphasis.SECONDARY,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.small))
            StratumAction(
                label = "Model provider",
                onClick = onSettings,
                emphasis = ActionEmphasis.QUIET,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(Space.huge))
    }
}

@Composable
private fun Stat(label: String, value: Int, modifier: Modifier = Modifier) {
    StratumWell(modifier = modifier) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.headlineMedium,
            color = StratumTheme.colors.accent,
        )
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = StratumTheme.colors.inkMuted,
        )
    }
}
