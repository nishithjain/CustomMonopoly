package com.boardbanker.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.boardbanker.app.BankingQrApplication
import com.boardbanker.app.navigation.BankingNavigation
import com.boardbanker.app.navigation.BankingScanContext
import com.boardbanker.app.ui.screens.auction.AuctionScreen
import com.boardbanker.app.ui.screens.auction.AuctionViewModel
import com.boardbanker.app.ui.screens.auction.AuctionViewModelFactory
import com.boardbanker.app.ui.screens.banking.AdvancedBankingScreen
import com.boardbanker.app.ui.screens.banking.AdvancedBankingViewModel
import com.boardbanker.app.ui.screens.banking.AdvancedBankingViewModelFactory
import com.boardbanker.app.ui.screens.banking.GameStatusScreen
import com.boardbanker.app.ui.screens.debt.DebtResolutionScreen
import com.boardbanker.app.ui.screens.debt.DebtResolutionViewModel
import com.boardbanker.app.ui.screens.debt.DebtResolutionViewModelFactory
import com.boardbanker.app.ui.screens.gameover.GameOverScreen
import com.boardbanker.app.ui.screens.gameover.GameOverViewModel
import com.boardbanker.app.ui.screens.gameover.GameOverViewModelFactory
import com.boardbanker.app.ui.screens.history.TransactionHistoryScreen
import com.boardbanker.app.ui.screens.game.GameScreen
import com.boardbanker.app.ui.screens.game.GameViewModel
import com.boardbanker.app.ui.screens.game.GameViewModelFactory
import com.boardbanker.app.ui.screens.home.HomeScreen
import com.boardbanker.app.ui.screens.home.HomeViewModel
import com.boardbanker.app.ui.screens.home.HomeViewModelFactory
import com.boardbanker.app.ui.screens.persistence.PersistenceDebugScreen
import com.boardbanker.app.ui.screens.resume.ResumeGameScreen
import com.boardbanker.app.ui.screens.setup.GameSetupViewModel
import com.boardbanker.app.ui.screens.setup.GameSetupViewModelFactory
import com.boardbanker.app.ui.screens.setup.PlayerSetupScreen
import com.boardbanker.app.scanner.delivery.CollectScanResults
import com.boardbanker.app.scanner.delivery.ScanResultConsumer
import com.boardbanker.app.scanner.ui.ScannerScreen
import com.boardbanker.app.audio.ScanPromptAudio
import com.boardbanker.core.card.CardType

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
) {
    val context = LocalContext.current
    val app = context.applicationContext as BankingQrApplication
    val homeViewModel: HomeViewModel = viewModel(
        factory = HomeViewModelFactory(
            sessionManager = app.activeGameSessionManager,
            repository = app.gameSessionRepository,
            definitionsError = app.definitionsLoadError,
        ),
    )

    NavHost(
        navController = navController,
        startDestination = AppDestination.Home.route,
    ) {
        composable(AppDestination.Home.route) {
            HomeScreen(
                onNewGame = {
                    navController.navigate(AppDestination.playerSetupRoute(newGame = true)) {
                        launchSingleTop = true
                    }
                },
                onResumeSetup = {
                    navController.navigate(AppDestination.playerSetupRoute(newGame = false)) {
                        launchSingleTop = true
                    }
                },
                onResumeGame = {
                    navController.navigate(AppDestination.Game.route) {
                        launchSingleTop = true
                    }
                },
                onTestQrScanner = { navController.navigate(AppDestination.QrScanner.route) },
                onTestPersistence = { navController.navigate(AppDestination.PersistenceDebug.route) },
                viewModel = homeViewModel,
            )
        }

        composable(
            route = AppDestination.PlayerSetup.route,
            arguments = listOf(navArgument("newGame") { type = NavType.BoolType }),
        ) { backStackEntry ->
            val createNewGame = backStackEntry.arguments?.getBoolean("newGame") ?: true
            val setupViewModel: GameSetupViewModel = viewModel(
                factory = GameSetupViewModelFactory(
                    sessionManager = app.activeGameSessionManager,
                    definitions = app.gameDefinitions,
                    createNewGame = createNewGame,
                    gameAudioFeedback = app.gameAudioFeedback,
                    gameEndAudioCoordinator = app.gameEndAudioCoordinator,
                ),
            )

            CollectScanResults(
                deliverer = app.scanResultDeliverer,
                consumer = ScanResultConsumer.PLAYER_SETUP,
            ) { card ->
                setupViewModel.onPlayerIdScanned(card.cardId)
            }

            PlayerSetupScreen(
                viewModel = setupViewModel,
                onScanPlayerCard = {
                    app.scanResultDeliverer.prepareConsumer(ScanResultConsumer.PLAYER_SETUP)
                    ScanPromptAudio.playOnce(
                        app.gameAudioFeedback,
                        ScanPromptAudio.beginPromptSession(),
                    )
                    navController.navigate(AppDestination.PlayerScanner.route)
                },
                onNavigateToGame = {
                    navController.navigate(AppDestination.Game.route) {
                        popUpTo(AppDestination.Home.route) { inclusive = false }
                    }
                },
                onNavigateHome = {
                    navController.popBackStack(AppDestination.Home.route, inclusive = false)
                },
            )
        }

        composable(AppDestination.PlayerScanner.route) {
            ScannerScreen(
                title = "SCAN PLAYER CARD",
                expectedCardType = CardType.USER,
                onCardAccepted = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }

        composable(AppDestination.Game.route) { backStackEntry ->
            val gameViewModel: GameViewModel = viewModel(
                factory = GameViewModelFactory(
                    sessionManager = app.activeGameSessionManager,
                    definitions = app.gameDefinitions,
                    transientWorkflow = app.transientScanWorkflow,
                    gameAudioFeedback = app.gameAudioFeedback,
                    gameEndAudioCoordinator = app.gameEndAudioCoordinator,
                ),
            )

            CollectScanResults(
                deliverer = app.scanResultDeliverer,
                consumer = ScanResultConsumer.GAME,
            ) { card ->
                gameViewModel.onCardScanned(card.cardId, card.cardType)
            }

            GameScreen(
                viewModel = gameViewModel,
                onNavigateHome = {
                    navController.popBackStack(AppDestination.Home.route, inclusive = false)
                },
                onOpenScanner = { expectedCardType ->
                    backStackEntry.savedStateHandle[GameNavigation.EXPECTED_SCAN_TYPE] =
                        expectedCardType?.name ?: "ANY"
                    app.scanResultDeliverer.prepareConsumer(ScanResultConsumer.GAME)
                    navController.navigate(AppDestination.GameScanner.route)
                },
                onNavigateToBanking = {
                    navController.navigate(AppDestination.AdvancedBanking.route)
                },
                onNavigateToAuction = { propertyId, startedByPlayerId ->
                    navController.navigate(
                        AppDestination.auctionRoute(propertyId, startedByPlayerId),
                    )
                },
                onNavigateToDebt = {
                    navController.navigate(AppDestination.DebtResolution.route)
                },
                onNavigateToGameOver = {
                    navController.navigate(AppDestination.GameOver.route)
                },
            )
        }

        composable(AppDestination.AdvancedBanking.route) { backStackEntry ->
            val bankingViewModel: AdvancedBankingViewModel = viewModel(
                factory = AdvancedBankingViewModelFactory(
                    sessionManager = app.activeGameSessionManager,
                    definitions = app.gameDefinitions,
                    gameAudioFeedback = app.gameAudioFeedback,
                    gameEndAudioCoordinator = app.gameEndAudioCoordinator,
                ),
            )

            CollectScanResults(
                deliverer = app.scanResultDeliverer,
                consumer = ScanResultConsumer.BANKING,
            ) { card ->
                when (card.cardType) {
                    CardType.USER -> bankingViewModel.onPlayerScanned(card.cardId)
                    CardType.PROPERTY -> bankingViewModel.onPropertyScanned(card.cardId)
                    else -> Unit
                }
            }

            AdvancedBankingScreen(
                viewModel = bankingViewModel,
                onNavigateBack = { navController.popBackStack() },
                onOpenScanner = { context ->
                    backStackEntry.savedStateHandle[BankingNavigation.SCAN_CONTEXT] = context.name
                    app.scanResultDeliverer.prepareConsumer(ScanResultConsumer.BANKING)
                    navController.navigate(AppDestination.BankingScanner.route)
                },
                onNavigateToDebt = {
                    navController.navigate(AppDestination.DebtResolution.route)
                },
                onNavigateToGameOver = {
                    navController.navigate(AppDestination.GameOver.route)
                },
                onNavigateToGameStatus = {
                    navController.navigate(AppDestination.GameStatus.route)
                },
                onNavigateToHistory = {
                    navController.navigate(AppDestination.TransactionHistory.route)
                },
            )
        }

        composable(AppDestination.BankingScanner.route) {
            val parentEntry = navController.previousBackStackEntry ?: return@composable
            val contextName = parentEntry.savedStateHandle.get<String>(BankingNavigation.SCAN_CONTEXT)
            val expectedCardType = when (contextName) {
                BankingScanContext.PROPERTY.name -> CardType.PROPERTY
                else -> CardType.USER
            }
            ScannerScreen(
                title = if (expectedCardType == CardType.PROPERTY) "SCAN PROPERTY CARD" else "SCAN PLAYER CARD",
                expectedCardType = expectedCardType,
                onCardAccepted = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = AppDestination.Auction.route,
            arguments = listOf(
                navArgument("propertyId") { type = NavType.StringType },
                navArgument("startedByPlayerId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val propertyId = backStackEntry.arguments?.getString("propertyId") ?: return@composable
            val startedByPlayerId = backStackEntry.arguments?.getString("startedByPlayerId") ?: return@composable
            val auctionViewModel: AuctionViewModel = viewModel(
                factory = AuctionViewModelFactory(
                    sessionManager = app.activeGameSessionManager,
                    definitions = app.gameDefinitions,
                    propertyId = propertyId,
                    startedByPlayerId = startedByPlayerId,
                    gameAudioFeedback = app.gameAudioFeedback,
                    gameEndAudioCoordinator = app.gameEndAudioCoordinator,
                ),
            )

            CollectScanResults(
                deliverer = app.scanResultDeliverer,
                consumer = ScanResultConsumer.AUCTION,
            ) { card ->
                auctionViewModel.onPlayerScanned(card.cardId)
            }

            AuctionScreen(
                viewModel = auctionViewModel,
                onNavigateBack = { navController.popBackStack() },
                onOpenScanner = {
                    backStackEntry.savedStateHandle[BankingNavigation.SCAN_CONTEXT] =
                        BankingScanContext.PLAYER.name
                    app.scanResultDeliverer.prepareConsumer(ScanResultConsumer.AUCTION)
                    navController.navigate(AppDestination.BankingScanner.route)
                },
                onNavigateToDebt = {
                    navController.navigate(AppDestination.DebtResolution.route)
                },
                onNavigateToGameOver = {
                    navController.navigate(AppDestination.GameOver.route)
                },
            )
        }

        composable(AppDestination.DebtResolution.route) { backStackEntry ->
            val debtViewModel: DebtResolutionViewModel = viewModel(
                factory = DebtResolutionViewModelFactory(
                    sessionManager = app.activeGameSessionManager,
                    definitions = app.gameDefinitions,
                    gameAudioFeedback = app.gameAudioFeedback,
                    gameEndAudioCoordinator = app.gameEndAudioCoordinator,
                ),
            )

            CollectScanResults(
                deliverer = app.scanResultDeliverer,
                consumer = ScanResultConsumer.DEBT,
            ) { card ->
                debtViewModel.onPropertyScanned(card.cardId)
            }

            DebtResolutionScreen(
                viewModel = debtViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToGameOver = {
                    navController.navigate(AppDestination.GameOver.route)
                },
                onOpenPropertyScanner = {
                    backStackEntry.savedStateHandle[BankingNavigation.SCAN_CONTEXT] =
                        BankingScanContext.PROPERTY.name
                    app.scanResultDeliverer.prepareConsumer(ScanResultConsumer.DEBT)
                    navController.navigate(AppDestination.BankingScanner.route)
                },
            )
        }

        composable(AppDestination.GameOver.route) {
            val gameOverViewModel: GameOverViewModel = viewModel(
                factory = GameOverViewModelFactory(
                    sessionManager = app.activeGameSessionManager,
                    definitions = app.gameDefinitions,
                    gameAudioFeedback = app.gameAudioFeedback,
                    gameEndAudioCoordinator = app.gameEndAudioCoordinator,
                ),
            )
            GameOverScreen(
                viewModel = gameOverViewModel,
                onNavigateHome = {
                    navController.popBackStack(AppDestination.Home.route, inclusive = false)
                },
                onNewGame = {
                    navController.navigate(AppDestination.playerSetupRoute(newGame = true)) {
                        popUpTo(AppDestination.Home.route) { inclusive = false }
                    }
                },
            )
        }

        composable(AppDestination.TransactionHistory.route) {
            TransactionHistoryScreen(
                sessionManager = app.activeGameSessionManager,
                definitions = app.gameDefinitions,
                onBack = { navController.popBackStack() },
            )
        }

        composable(AppDestination.GameStatus.route) {
            GameStatusScreen(
                sessionManager = app.activeGameSessionManager,
                definitions = app.gameDefinitions,
                onBack = { navController.popBackStack() },
            )
        }

        composable(AppDestination.GameScanner.route) {
            val parentEntry = navController.previousBackStackEntry ?: return@composable
            val expectedName = parentEntry.savedStateHandle.get<String>(GameNavigation.EXPECTED_SCAN_TYPE)
            val expectedCardType = when (expectedName) {
                CardType.USER.name -> CardType.USER
                CardType.PROPERTY.name -> CardType.PROPERTY
                CardType.EVENT.name -> CardType.EVENT
                else -> null
            }
            ScannerScreen(
                title = "SCAN GAME CARD",
                expectedCardType = expectedCardType,
                onCardAccepted = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }

        composable(AppDestination.QrScanner.route) {
            ScannerScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(AppDestination.ResumeGame.route) {
            ResumeGameScreen(
                app = app,
                onBack = { navController.popBackStack() },
            )
        }

        composable(AppDestination.PersistenceDebug.route) {
            PersistenceDebugScreen(
                app = app,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
