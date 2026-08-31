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
import com.boardbanker.app.ui.screens.playerdetails.PlayerDetailsScreen
import com.boardbanker.app.ui.screens.playerdetails.PlayerDetailsViewModel
import com.boardbanker.app.ui.screens.playerdetails.PlayerDetailsViewModelFactory
import com.boardbanker.app.ui.screens.home.HomeScreen
import com.boardbanker.app.ui.screens.home.HomeViewModel
import com.boardbanker.app.ui.screens.home.HomeViewModelFactory
import com.boardbanker.app.ui.screens.persistence.PersistenceDebugScreen
import com.boardbanker.app.ui.screens.resume.ResumeGameScreen
import com.boardbanker.app.ui.screens.setup.GameSetupViewModel
import com.boardbanker.app.ui.screens.setup.GameSetupViewModelFactory
import com.boardbanker.app.ui.screens.setup.PlayerSetupScreen
import com.boardbanker.app.scanner.ScanRequest
import com.boardbanker.app.scanner.delivery.CollectScanResults
import com.boardbanker.app.scanner.delivery.ScanResultConsumer
import com.boardbanker.app.scanner.ui.ScannerScreen
import com.boardbanker.app.audio.ScanPromptAudio

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
                    editionRepository = app.editionRepository,
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
                    app.scanResultDeliverer.prepareConsumer(
                        ScanResultConsumer.PLAYER_SETUP,
                        ScanRequest.player(),
                    )
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
                scanRequest = ScanRequest.player(),
                onCardAccepted = { navController.popBackStack() },
                onBack = {
                    app.scanResultDeliverer.clearPendingScanRequest()
                    navController.popBackStack()
                },
            )
        }

        composable(AppDestination.Game.route) { backStackEntry ->
            val gameViewModel: GameViewModel = viewModel(
                factory = GameViewModelFactory(
                    sessionManager = app.activeGameSessionManager,
                    definitions = app.gameDefinitions,
                    transientWorkflow = app.transientScanWorkflow,
                    locationWorkflowHolder = app.locationWorkflowHolder,
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
                onOpenScanner = { request ->
                    app.scanResultDeliverer.prepareConsumer(ScanResultConsumer.GAME, request)
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
                onNavigateToPlayerDetails = { playerId ->
                    navController.navigate(AppDestination.playerDetailsRoute(playerId))
                },
            )
        }

        composable(
            route = AppDestination.PlayerDetails.route,
            arguments = listOf(navArgument("playerId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val playerId = backStackEntry.arguments?.getString("playerId") ?: return@composable
            val playerDetailsViewModel: PlayerDetailsViewModel = viewModel(
                factory = PlayerDetailsViewModelFactory(
                    playerId = playerId,
                    sessionManager = app.activeGameSessionManager,
                    definitions = app.gameDefinitions,
                    locationWorkflowHolder = app.locationWorkflowHolder,
                    gameAudioFeedback = app.gameAudioFeedback,
                    gameEndAudioCoordinator = app.gameEndAudioCoordinator,
                ),
            )

            CollectScanResults(
                deliverer = app.scanResultDeliverer,
                consumer = ScanResultConsumer.PLAYER_DETAILS,
            ) { card ->
                if (card.cardType == com.boardbanker.core.card.CardType.PROPERTY) {
                    playerDetailsViewModel.onPropertyScanned(card.cardId)
                }
            }

            PlayerDetailsScreen(
                viewModel = playerDetailsViewModel,
                onNavigateBack = { navController.popBackStack() },
                onOpenPropertyScanner = {
                    app.scanResultDeliverer.prepareConsumer(
                        ScanResultConsumer.PLAYER_DETAILS,
                        ScanRequest.property(),
                    )
                    navController.navigate(AppDestination.BankingScanner.route)
                },
                onNavigateToDebt = {
                    navController.navigate(AppDestination.DebtResolution.route)
                },
                onNavigateToGameOver = {
                    navController.navigate(AppDestination.GameOver.route)
                },
                onContinueLocationOnActiveGame = {
                    navController.popBackStack(AppDestination.Game.route, inclusive = false)
                },
            )
        }

        composable(AppDestination.AdvancedBanking.route) { backStackEntry ->
            val bankingViewModel: AdvancedBankingViewModel = viewModel(
                factory = AdvancedBankingViewModelFactory(
                    sessionManager = app.activeGameSessionManager,
                    definitions = app.gameDefinitions,
                    locationWorkflowHolder = app.locationWorkflowHolder,
                    gameAudioFeedback = app.gameAudioFeedback,
                    gameEndAudioCoordinator = app.gameEndAudioCoordinator,
                ),
            )

            CollectScanResults(
                deliverer = app.scanResultDeliverer,
                consumer = ScanResultConsumer.BANKING,
            ) { card ->
                bankingViewModel.onScanDelivered(card.cardId, card.cardType)
            }

            AdvancedBankingScreen(
                viewModel = bankingViewModel,
                onNavigateBack = { navController.popBackStack() },
                onOpenScanner = { request ->
                    app.scanResultDeliverer.prepareConsumer(ScanResultConsumer.BANKING, request)
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
                onContinueLocationOnActiveGame = {
                    navController.popBackStack(AppDestination.Game.route, inclusive = false)
                },
            )
        }

        composable(AppDestination.BankingScanner.route) {
            val request = app.scanResultDeliverer.peekScanRequest() ?: ScanRequest.player()
            ScannerScreen(
                scanRequest = request,
                onCardAccepted = { navController.popBackStack() },
                onBack = {
                    app.scanResultDeliverer.clearPendingScanRequest()
                    navController.popBackStack()
                },
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
                    app.scanResultDeliverer.prepareConsumer(
                        ScanResultConsumer.AUCTION,
                        ScanRequest.player(),
                    )
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
                    app.scanResultDeliverer.prepareConsumer(
                        ScanResultConsumer.DEBT,
                        ScanRequest.property(),
                    )
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
            val request = app.scanResultDeliverer.peekScanRequest() ?: ScanRequest.gameCard()
            ScannerScreen(
                scanRequest = request,
                onCardAccepted = { navController.popBackStack() },
                onBack = {
                    app.scanResultDeliverer.clearPendingScanRequest()
                    navController.popBackStack()
                },
            )
        }

        composable(AppDestination.QrScanner.route) {
            ScannerScreen(
                scanRequest = ScanRequest.gameCard(),
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
