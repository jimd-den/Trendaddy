package com.example.igboarpg.di

import android.content.Context
import com.example.igboarpg.data.AppDatabase
import com.example.igboarpg.data.GeminiAiServiceImpl
import com.example.igboarpg.data.PnpRuleRepositoryImpl
import com.example.igboarpg.data.SpriteSheetRepositoryImpl
import com.example.igboarpg.data.SpriteTransparencyPoliceServiceImpl
import com.example.igboarpg.data.WeaponRepositoryImpl
import com.example.igboarpg.domain.AiGameMechanicGeneratorService
import com.example.igboarpg.domain.ArpgGameLoopEngine
import com.example.igboarpg.domain.ExecutePnpCheckUseCase
import com.example.igboarpg.domain.ExportSpriteSheetToJsonUseCase
import com.example.igboarpg.domain.FetchAiModelsUseCase
import com.example.igboarpg.domain.GenerateRawImageSpriteSheetUseCase
import com.example.igboarpg.domain.GenerateSpriteWithAiUseCase
import com.example.igboarpg.domain.ImportSpriteSheetFromJsonUseCase
import com.example.igboarpg.domain.ImportSpriteSheetUseCase
import com.example.igboarpg.domain.InventGameMechanicUseCase
import com.example.igboarpg.domain.PnpRuleRepository
import com.example.igboarpg.domain.PoliceSpriteSheetUseCase
import com.example.igboarpg.domain.RollRandomWeaponUseCase
import com.example.igboarpg.domain.SimulateCombatDpsUseCase
import com.example.igboarpg.domain.SpriteSheetRepository
import com.example.igboarpg.domain.SpriteTransparencyPoliceService
import com.example.igboarpg.domain.ToggleGamePauseUseCase
import com.example.igboarpg.domain.WeaponRepository

/**
 * CLEAN ARCHITECTURE DEPENDENCY CONTAINER
 *
 * Inverts dependencies so that the Presentation Layer (ViewModel) does NOT
 * directly instantiate Data Layer implementations (Room, DAOs, HTTP services).
 *
 * This enables modularity, testability, and clean separation between Domain, Data,
 * and Presentation layers.
 */
interface AppContainer {
    val weaponRepository: WeaponRepository
    val spriteSheetRepository: SpriteSheetRepository
    val pnpRuleRepository: PnpRuleRepository
    val aiService: AiGameMechanicGeneratorService
    val spritePoliceService: SpriteTransparencyPoliceService

    // Domain Use Cases
    val rollRandomWeaponUseCase: RollRandomWeaponUseCase
    val inventGameMechanicUseCase: InventGameMechanicUseCase
    val importSpriteSheetUseCase: ImportSpriteSheetUseCase
    val executePnpCheckUseCase: ExecutePnpCheckUseCase
    val simulateCombatDpsUseCase: SimulateCombatDpsUseCase
    val generateSpriteWithAiUseCase: GenerateSpriteWithAiUseCase
    val exportSpriteSheetToJsonUseCase: ExportSpriteSheetToJsonUseCase
    val importSpriteSheetFromJsonUseCase: ImportSpriteSheetFromJsonUseCase
    val fetchAiModelsUseCase: FetchAiModelsUseCase
    val policeSpriteSheetUseCase: PoliceSpriteSheetUseCase
    val generateRawImageSpriteSheetUseCase: GenerateRawImageSpriteSheetUseCase
    fun createToggleGamePauseUseCase(engine: ArpgGameLoopEngine): ToggleGamePauseUseCase
}

/**
 * Default production container implementation backed by SQLite Room Database
 * and real network / domain services.
 */
class DefaultAppContainer(private val context: Context) : AppContainer {

    private val database: AppDatabase by lazy {
        AppDatabase.getInstance(context.applicationContext)
    }

    override val weaponRepository: WeaponRepository by lazy {
        WeaponRepositoryImpl(database.weaponDao())
    }

    override val spriteSheetRepository: SpriteSheetRepository by lazy {
        SpriteSheetRepositoryImpl(database.spriteSheetDao())
    }

    override val pnpRuleRepository: PnpRuleRepository by lazy {
        PnpRuleRepositoryImpl(database.pnpRulebookDao())
    }

    override val aiService: AiGameMechanicGeneratorService by lazy {
        GeminiAiServiceImpl()
    }

    override val spritePoliceService: SpriteTransparencyPoliceService by lazy {
        SpriteTransparencyPoliceServiceImpl()
    }

    override val rollRandomWeaponUseCase: RollRandomWeaponUseCase by lazy {
        RollRandomWeaponUseCase(weaponRepository)
    }

    override val inventGameMechanicUseCase: InventGameMechanicUseCase by lazy {
        InventGameMechanicUseCase()
    }

    override val importSpriteSheetUseCase: ImportSpriteSheetUseCase by lazy {
        ImportSpriteSheetUseCase(spriteSheetRepository)
    }

    override val executePnpCheckUseCase: ExecutePnpCheckUseCase by lazy {
        ExecutePnpCheckUseCase(pnpRuleRepository)
    }

    override val simulateCombatDpsUseCase: SimulateCombatDpsUseCase by lazy {
        SimulateCombatDpsUseCase()
    }

    override val generateSpriteWithAiUseCase: GenerateSpriteWithAiUseCase by lazy {
        GenerateSpriteWithAiUseCase(aiService, spriteSheetRepository)
    }

    override val exportSpriteSheetToJsonUseCase: ExportSpriteSheetToJsonUseCase by lazy {
        ExportSpriteSheetToJsonUseCase()
    }

    override val importSpriteSheetFromJsonUseCase: ImportSpriteSheetFromJsonUseCase by lazy {
        ImportSpriteSheetFromJsonUseCase(spriteSheetRepository)
    }

    override val fetchAiModelsUseCase: FetchAiModelsUseCase by lazy {
        FetchAiModelsUseCase(aiService)
    }

    override val policeSpriteSheetUseCase: PoliceSpriteSheetUseCase by lazy {
        PoliceSpriteSheetUseCase(spritePoliceService, spriteSheetRepository)
    }

    override val generateRawImageSpriteSheetUseCase: GenerateRawImageSpriteSheetUseCase by lazy {
        GenerateRawImageSpriteSheetUseCase(aiService, spriteSheetRepository)
    }

    override fun createToggleGamePauseUseCase(engine: ArpgGameLoopEngine): ToggleGamePauseUseCase {
        return ToggleGamePauseUseCase(engine)
    }

    companion object {
        @Volatile
        private var instance: AppContainer? = null

        fun getInstance(context: Context): AppContainer {
            return instance ?: synchronized(this) {
                instance ?: DefaultAppContainer(context.applicationContext).also { instance = it }
            }
        }

        // For unit testing or swapping test containers
        fun setTestInstance(testContainer: AppContainer?) {
            instance = testContainer
        }
    }
}
