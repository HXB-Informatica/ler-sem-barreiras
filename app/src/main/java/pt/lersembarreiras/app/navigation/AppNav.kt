package pt.lersembarreiras.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import pt.lersembarreiras.app.LerSemBarreirasApp
import pt.lersembarreiras.app.ui.AboutScreen
import pt.lersembarreiras.app.ui.HomeScreen
import pt.lersembarreiras.reader.ReaderListScreen
import pt.lersembarreiras.reader.ReaderListViewModel
import pt.lersembarreiras.reader.ReaderScreen
import pt.lersembarreiras.reader.ReaderViewModel
import pt.lersembarreiras.teacher.TeacherEditScreen
import pt.lersembarreiras.teacher.TeacherEditViewModel
import pt.lersembarreiras.teacher.TeacherListScreen
import pt.lersembarreiras.teacher.TeacherListViewModel

@Composable
fun AppNav() {
    val navController = rememberNavController()
    val app = LocalContext.current.applicationContext as LerSemBarreirasApp
    val container = app.container

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onTeacher = { navController.navigate("teacher") },
                onStudent = { navController.navigate("reader") },
                onAbout = { navController.navigate("about") },
            )
        }
        composable("about") {
            AboutScreen(onBack = { navController.popBackStack() })
        }
        composable("teacher") {
            val vm: TeacherListViewModel = viewModel(factory = TeacherListViewModel.factory(container.repository))
            TeacherListScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onCreate = { navController.navigate("teacher/edit/new") },
                onOpen = { id -> navController.navigate("teacher/edit/$id") },
            )
        }
        composable("teacher/edit/new") {
            val player = remember { container.newPlayer() }
            val vm: TeacherEditViewModel = viewModel(
                factory = TeacherEditViewModel.factory(
                    contentId = null,
                    repository = container.repository,
                    recorder = remember { container.newRecorder() },
                    importer = remember { container.newImporter() },
                    player = player,
                    speechEngine = container.speechEngine,
                    aligner = container.aligner,
                ),
            )
            TeacherEditScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }
        composable(
            route = "teacher/edit/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("id")
            val player = remember(id) { container.newPlayer() }
            val vm: TeacherEditViewModel = viewModel(
                factory = TeacherEditViewModel.factory(
                    contentId = id,
                    repository = container.repository,
                    recorder = remember(id) { container.newRecorder() },
                    importer = remember(id) { container.newImporter() },
                    player = player,
                    speechEngine = container.speechEngine,
                    aligner = container.aligner,
                ),
            )
            TeacherEditScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }
        composable("reader") {
            val vm: ReaderListViewModel = viewModel(factory = ReaderListViewModel.factory(container.repository))
            ReaderListScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpen = { id -> navController.navigate("reader/$id") },
            )
        }
        composable(
            route = "reader/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("id") ?: return@composable
            val player = remember(id) { container.newPlayer() }
            val vm: ReaderViewModel = viewModel(
                factory = ReaderViewModel.factory(id, container.repository, player),
            )
            ReaderScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }
    }
}
