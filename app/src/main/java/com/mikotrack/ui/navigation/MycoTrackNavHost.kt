package com.mikotrack.ui.navigation

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.mikotrack.core.AppContainer
import com.mikotrack.ui.diagnostics.DiagnosticsScreen
import com.mikotrack.ui.diagnostics.DiagnosticsViewModel
import com.mikotrack.ui.games.ArkanoidScreen
import com.mikotrack.ui.games.GameHubScreen
import com.mikotrack.ui.games.GamesViewModel
import com.mikotrack.ui.games.MemoryScreen
import com.mikotrack.ui.games.PacmanScreen
import com.mikotrack.ui.games.RhythmScreen
import com.mikotrack.ui.games.SolitaireScreen
import com.mikotrack.ui.games.TetrisScreen
import com.mikotrack.ui.games.TopoScreen
import com.mikotrack.ui.gallery.GalleryScreen
import com.mikotrack.ui.gallery.GalleryViewModel
import com.mikotrack.ui.history.HistoryScreen
import com.mikotrack.ui.history.HistoryViewModel
import com.mikotrack.ui.history.TripDetailScreen
import com.mikotrack.ui.history.TripDetailViewModel
import com.mikotrack.ui.map.MapScreen
import com.mikotrack.ui.map.MapController
import com.mikotrack.ui.map.MapViewModel
import com.mikotrack.ui.mushrooms.MushroomDetailScreen
import com.mikotrack.ui.mushrooms.MushroomGuideScreen
import com.mikotrack.ui.mushrooms.MushroomGuideViewModel
import com.mikotrack.ui.mushrooms.guideLanguage
import com.mikotrack.ui.permissions.PermissionCenterScreen
import com.mikotrack.ui.permissions.PermissionCenterViewModel
import com.mikotrack.ui.settings.SettingsScreen
import com.mikotrack.ui.settings.SettingsViewModel

/** Creates a view model that receives the dependency container. */
@Composable
inline fun <reified VM : androidx.lifecycle.ViewModel> containerViewModel(
    container: AppContainer,
    crossinline factory: (AppContainer) -> VM,
): VM = viewModel(
    factory = viewModelFactory {
        initializer { factory(container) }
    },
)

/** The navigation graph of the application. */
@Composable
fun MycoTrackNavHost(
    container: AppContainer,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // The detail of a trip belongs to the history section, so its tab stays selected.
    val selectedTab = when (currentRoute) {
        Routes.TRIP_DETAIL -> TopLevelDestination.HISTORY
        Routes.DIAGNOSTICS -> TopLevelDestination.MAP
        Routes.MUSHROOM_DETAIL -> TopLevelDestination.MUSHROOMS
        Routes.GAMES, Routes.GAME -> TopLevelDestination.SETTINGS
        else -> TopLevelDestination.entries.firstOrNull { it.route == currentRoute }
    }

    // The controller of the map is created here, above the navigation graph, and not
    // inside the map destination. The destination is disposed every time the user
    // visits another section of the application, and a controller created inside it
    // would be thrown away, taking the camera of the map with it. Keeping it here
    // makes sure that the map comes back where the user left it.
    val mapController = rememberSaveable(saver = MapController.Saver) { MapController() }

    // Opens the map from another section (after posting a map focus request or enabling
    // a layer), with the same navigation as the bottom bar so that section keeps its state.
    val navigateToMap: () -> Unit = {
        navController.navigate(TopLevelDestination.MAP.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        // The application draws edge to edge, so the screens are responsible for
        // keeping their content away from the system bars. The navigation bar below
        // applies the bottom inset on its own.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = selectedTab == destination,
                        onClick = {
                            if (selectedTab == destination) {
                                // Tapping the current section goes back to its first
                                // screen, e.g. from a trip to the list of trips.
                                navController.popBackStack(destination.route, inclusive = false)
                                return@NavigationBarItem
                            }
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = stringResource(destination.labelRes)) },
                        // Five sections share the bar, so a long label is cut instead of wrapping.
                        label = {
                            Text(
                                text = stringResource(destination.labelRes),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.MAP.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(TopLevelDestination.MAP.route) {
                val viewModel: MapViewModel = containerViewModel(container) {
                    MapViewModel(
                        tripRepository = it.tripRepository,
                        waypointRepository = it.waypointRepository,
                        categoryRepository = it.categoryRepository,
                        settingsStore = it.settingsStore,
                        offlineMapManager = it.offlineMapManager,
                        imageStore = it.imageStore,
                        locationTracker = it.locationTracker,
                        proximityAlertManager = it.proximityAlertManager,
                        gnssMonitor = it.gnssMonitor,
                    )
                }
                MapScreen(
                    viewModel = viewModel,
                    controller = mapController,
                    onOpenPermissions = {
                        navController.navigate(Routes.PERMISSIONS) {
                            launchSingleTop = true
                        }
                    },
                    onOpenDiagnostics = {
                        navController.navigate(Routes.DIAGNOSTICS) { launchSingleTop = true }
                    },
                )
            }

            composable(TopLevelDestination.HISTORY.route) {
                val viewModel: HistoryViewModel = containerViewModel(container) {
                    HistoryViewModel(it.tripRepository)
                }
                HistoryScreen(
                    viewModel = viewModel,
                    onOpenTrip = { tripId ->
                        navController.navigate(Routes.tripDetail(tripId))
                    },
                )
            }

            composable(TopLevelDestination.GALLERY.route) {
                val viewModel: GalleryViewModel = containerViewModel(container) {
                    GalleryViewModel(it.waypointRepository, it.tripRepository, it.settingsStore)
                }
                GalleryScreen(
                    viewModel = viewModel,
                    onShowOnMap = navigateToMap,
                )
            }

            composable(TopLevelDestination.MUSHROOMS.route) {
                val viewModel: MushroomGuideViewModel = viewModel()
                val guideLocale = guideLanguage()
                val suitabilityViewModel: com.mikotrack.ui.map.SuitabilitySearchViewModel =
                    containerViewModel(container) {
                        com.mikotrack.ui.map.SuitabilitySearchViewModel(it.settingsStore, it.locationTracker)
                    }
                MushroomGuideScreen(
                    viewModel = viewModel,
                    onOpenSpecies = { id -> navController.navigate(Routes.mushroomDetail(id)) },
                    onFindSpecies = { species, radiusKm, aroundMapCenter, findHotspots, onDone ->
                        suitabilityViewModel.startMulti(
                            species = species,
                            radiusKm = radiusKm,
                            aroundMapCenter = aroundMapCenter,
                            mapCenter = mapController.cameraTarget(),
                            findHotspots = findHotspots,
                            language = guideLocale,
                            onDone = { ok ->
                                if (ok) navigateToMap()
                                onDone(ok)
                            },
                        )
                    },
                )
            }

            composable(
                route = Routes.MUSHROOM_DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                val viewModel: MushroomGuideViewModel = viewModel()
                val suitabilityViewModel: com.mikotrack.ui.map.SuitabilitySearchViewModel =
                    containerViewModel(container) {
                        com.mikotrack.ui.map.SuitabilitySearchViewModel(it.settingsStore, it.locationTracker)
                    }
                MushroomDetailScreen(
                    viewModel = viewModel,
                    speciesId = entry.arguments?.getString("id").orEmpty(),
                    onShowOnMap = navigateToMap,
                    onSearchZones = { id, name, radiusKm, aroundMapCenter, onDone ->
                        suitabilityViewModel.start(id, name, radiusKm, aroundMapCenter, mapController.cameraTarget(), onDone)
                    },
                    onBack = {
                        if (!navController.popBackStack(TopLevelDestination.MUSHROOMS.route, inclusive = false)) {
                            navController.navigate(TopLevelDestination.MUSHROOMS.route)
                        }
                    },
                )
            }

            composable(TopLevelDestination.SETTINGS.route) {
                val viewModel: SettingsViewModel = containerViewModel(container) {
                    SettingsViewModel(it.settingsStore, it.offlineMapManager, it.updateManager)
                }
                SettingsScreen(
                    viewModel = viewModel,
                    onOpenPermissions = {
                        navController.navigate(Routes.PERMISSIONS) {
                            launchSingleTop = true
                        }
                    },
                    onOpenDiagnostics = {
                        navController.navigate(Routes.DIAGNOSTICS) { launchSingleTop = true }
                    },
                    onOpenGames = { navController.navigate(Routes.GAMES) },
                )            }

            composable(Routes.GAMES) {
                val viewModel: GamesViewModel = containerViewModel(container) {
                    GamesViewModel(it.settingsStore)
                }
                GameHubScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenGame = { game -> navController.navigate(Routes.game(game.name)) },
                )
            }

            composable(
                route = Routes.GAME,
                arguments = listOf(navArgument("game") { type = NavType.StringType }),
            ) { entry ->
                val viewModel: GamesViewModel = containerViewModel(container) {
                    GamesViewModel(it.settingsStore)
                }
                val back: () -> Unit = { navController.popBackStack(Routes.GAMES, inclusive = false) }
                when (entry.arguments?.getString("game")) {
                    "ARKANOID" -> ArkanoidScreen(viewModel = viewModel, onBack = back)
                    "TETRIS" -> TetrisScreen(viewModel = viewModel, onBack = back)
                    "PACMAN" -> PacmanScreen(viewModel = viewModel, onBack = back)
                    "RHYTHM" -> RhythmScreen(viewModel = viewModel, onBack = back)
                    "MEMORY" -> MemoryScreen(viewModel = viewModel, onBack = back)
                    "TOPO" -> TopoScreen(viewModel = viewModel, onBack = back)
                    "SOLITAIRE" -> SolitaireScreen(viewModel = viewModel, onBack = back)
                    else -> navController.popBackStack()
                }
            }

            composable(Routes.DIAGNOSTICS) {
                val appContext = LocalContext.current.applicationContext
                val viewModel: DiagnosticsViewModel = containerViewModel(container) {
                    DiagnosticsViewModel(
                        context = appContext,
                        locationTracker = it.locationTracker,
                        gnssMonitor = it.gnssMonitor,
                        sensorMonitor = it.sensorMonitor,
                    )
                }
                DiagnosticsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenPermissions = {
                        navController.navigate(Routes.PERMISSIONS) { launchSingleTop = true }
                    },
                )
            }

            composable(Routes.PERMISSIONS) {
                val viewModel: PermissionCenterViewModel = containerViewModel(container) {
                    PermissionCenterViewModel(it.settingsStore)
                }
                PermissionCenterScreen(
                    viewModel = viewModel,
                    onClose = { navController.popBackStack() },
                    onReady = { navController.popBackStack() },
                )
            }

            composable(
                route = Routes.TRIP_DETAIL,
                arguments = listOf(navArgument("tripId") { type = NavType.LongType }),
            ) { entry ->
                val tripId = entry.arguments?.getLong("tripId") ?: 0L
                val viewModel: TripDetailViewModel = containerViewModel(container) {
                    TripDetailViewModel(
                        tripRepository = it.tripRepository,
                        waypointRepository = it.waypointRepository,
                        settingsStore = it.settingsStore,
                        offlineMapManager = it.offlineMapManager,
                    )
                }
                TripDetailScreen(
                    viewModel = viewModel,
                    tripId = tripId,
                    onShowOnMap = navigateToMap,
                    onBack = {
                        if (!navController.popBackStack(TopLevelDestination.HISTORY.route, inclusive = false)) {
                            navController.navigate(TopLevelDestination.HISTORY.route)
                        }
                    },
                    onDelete = {
                        navController.popBackStack(TopLevelDestination.HISTORY.route, inclusive = false)
                    },
                )
            }
        }
    }
}
