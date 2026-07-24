package com.deafregistry.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.deafregistry.app.data.session.SessionManager
import com.deafregistry.app.ui.admin.AdminHomeScreen
import com.deafregistry.app.ui.admin.BackupRestoreScreen
import com.deafregistry.app.ui.admin.ManageBarangaysScreen
import com.deafregistry.app.ui.admin.ManageDevicesScreen
import com.deafregistry.app.ui.admin.ManageMunicipalitiesScreen
import com.deafregistry.app.ui.admin.ManageTeachersScreen
import com.deafregistry.app.ui.admin.ManageUsersScreen
import com.deafregistry.app.ui.admin.NotificationSettingsScreen
import com.deafregistry.app.ui.dashboard.DashboardScreen
import com.deafregistry.app.ui.editor.DeafEditorScreen
import com.deafregistry.app.ui.login.LoginScreen
import com.deafregistry.app.ui.municipality.AllBarangaysScreen
import com.deafregistry.app.ui.municipality.BarangayListScreen
import com.deafregistry.app.ui.municipality.MunicipalityListScreen
import com.deafregistry.app.ui.municipality.MunicipalityOverviewScreen
import com.deafregistry.app.ui.profile.DeafProfileScreen
import com.deafregistry.app.ui.reports.ReportCategoryDetailScreen
import com.deafregistry.app.ui.reports.ReportsScreen
import com.deafregistry.app.ui.search.AllIndividualsScreen
import com.deafregistry.app.ui.search.SearchScreen
import java.net.URLDecoder

@Composable
fun AppNavGraph(sessionManager: SessionManager) {
    val navController: NavHostController = rememberNavController()
    val startDestination = if (sessionManager.isLoggedIn()) Routes.DASHBOARD else Routes.LOGIN

    NavHost(navController = navController, startDestination = startDestination) {

        composable(Routes.LOGIN) {
            LoginScreen(onLoggedIn = {
                navController.navigate(Routes.DASHBOARD) {
                    popUpTo(Routes.LOGIN) { inclusive = true }
                }
            })
        }

        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onOpenDeafRecords = { navController.navigate(Routes.MUNICIPALITY_OVERVIEW) },
                onOpenAllBarangays = { navController.navigate(Routes.ALL_BARANGAYS) },
                onOpenAllIndividuals = { title, sort -> navController.navigate(Routes.allIndividuals(title, sort)) },
                onOpenSearch = { navController.navigate(Routes.SEARCH) },
                onOpenReports = { navController.navigate(Routes.REPORTS) },
                onOpenAdmin = { navController.navigate(Routes.ADMIN_HOME) },
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.MUNICIPALITY_OVERVIEW) {
            MunicipalityOverviewScreen(
                onBack = { navController.popBackStack() },
                onOpenMunicipality = { id, name -> navController.navigate(Routes.barangayList(id, name)) }
            )
        }

        composable(Routes.ALL_BARANGAYS) {
            AllBarangaysScreen(
                onBack = { navController.popBackStack() },
                onOpenBarangay = { municipalityId, municipalityName, barangayId, barangayName ->
                    navController.navigate(Routes.municipalityList(municipalityId, municipalityName, barangayId, barangayName))
                }
            )
        }

        composable(
            Routes.ALL_INDIVIDUALS,
            arguments = listOf(
                navArgument("title") { type = NavType.StringType },
                navArgument("sort") { type = NavType.StringType; defaultValue = "name" }
            )
        ) { backStackEntry ->
            val title = URLDecoder.decode(backStackEntry.arguments?.getString("title") ?: "All Records", "UTF-8")
            val sort = backStackEntry.arguments?.getString("sort") ?: "name"
            AllIndividualsScreen(
                title = title,
                sort = sort,
                onBack = { navController.popBackStack() },
                onOpenProfile = { uuid -> navController.navigate(Routes.deafProfile(uuid)) },
                onAddNew = { navController.navigate(Routes.deafEditorNew(-1)) }
            )
        }

        composable(
            Routes.BARANGAY_LIST,
            arguments = listOf(
                navArgument("municipalityId") { type = NavType.IntType },
                navArgument("municipalityName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getInt("municipalityId") ?: 0
            val name = URLDecoder.decode(backStackEntry.arguments?.getString("municipalityName") ?: "", "UTF-8")
            BarangayListScreen(
                municipalityId = id,
                municipalityName = name,
                onBack = { navController.popBackStack() },
                onOpenBarangay = { barangayId, barangayName ->
                    navController.navigate(Routes.municipalityList(id, name, barangayId, barangayName))
                }
            )
        }

        composable(
            Routes.MUNICIPALITY_LIST,
            arguments = listOf(
                navArgument("municipalityId") { type = NavType.IntType },
                navArgument("municipalityName") { type = NavType.StringType },
                navArgument("barangayId") { type = NavType.IntType; defaultValue = -1 },
                navArgument("barangayName") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getInt("municipalityId") ?: 0
            val name = URLDecoder.decode(backStackEntry.arguments?.getString("municipalityName") ?: "", "UTF-8")
            val barangayIdArg = backStackEntry.arguments?.getInt("barangayId")?.takeIf { it != -1 }
            val barangayNameArg = URLDecoder.decode(backStackEntry.arguments?.getString("barangayName") ?: "", "UTF-8").ifBlank { null }
            MunicipalityListScreen(
                municipalityId = id,
                municipalityName = name,
                onBack = { navController.popBackStack() },
                onOpenProfile = { uuid -> navController.navigate(Routes.deafProfile(uuid)) },
                onAddNew = { navController.navigate(Routes.deafEditorNew(id)) },
                initialBarangayId = barangayIdArg,
                barangayName = barangayNameArg
            )
        }

        composable(
            Routes.DEAF_PROFILE,
            arguments = listOf(navArgument("uuid") { type = NavType.StringType })
        ) { backStackEntry ->
            val uuid = backStackEntry.arguments?.getString("uuid") ?: return@composable
            DeafProfileScreen(
                uuid = uuid,
                onBack = { navController.popBackStack() },
                onEdit = { editUuid -> navController.navigate(Routes.deafEditorEdit(editUuid)) }
            )
        }

        composable(
            Routes.DEAF_EDITOR,
            arguments = listOf(
                navArgument("uuid") { type = NavType.StringType; nullable = true; defaultValue = "" },
                navArgument("municipalityId") { type = NavType.IntType; defaultValue = -1 }
            )
        ) { backStackEntry ->
            val uuidArg = backStackEntry.arguments?.getString("uuid").orEmpty().ifBlank { null }
            val municipalityIdArg = backStackEntry.arguments?.getInt("municipalityId")?.takeIf { it != -1 }
            DeafEditorScreen(
                uuid = uuidArg,
                municipalityId = municipalityIdArg,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }

        composable(Routes.SEARCH) {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onOpenProfile = { uuid -> navController.navigate(Routes.deafProfile(uuid)) }
            )
        }

        composable(Routes.REPORTS) {
            ReportsScreen(
                onBack = { navController.popBackStack() },
                onOpenCategoryDetail = { category, value, extra ->
                    navController.navigate(Routes.reportCategoryDetail(category, value, extra))
                }
            )
        }

        composable(
            Routes.REPORT_CATEGORY_DETAIL,
            arguments = listOf(
                navArgument("category") { type = NavType.StringType },
                navArgument("value") { type = NavType.StringType },
                navArgument("extra") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val category = backStackEntry.arguments?.getString("category") ?: ""
            val value = URLDecoder.decode(backStackEntry.arguments?.getString("value") ?: "", "UTF-8")
            val extra = URLDecoder.decode(backStackEntry.arguments?.getString("extra") ?: "", "UTF-8")
            ReportCategoryDetailScreen(
                category = category,
                value = value,
                extra = extra,
                onBack = { navController.popBackStack() },
                onOpenProfile = { uuid -> navController.navigate(Routes.deafProfile(uuid)) }
            )
        }

        composable(Routes.ADMIN_HOME) {
            AdminHomeScreen(onBack = { navController.popBackStack() }, onNavigate = { navController.navigate(it) })
        }
        composable(Routes.ADMIN_MUNICIPALITIES) {
            ManageMunicipalitiesScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ADMIN_BARANGAYS) {
            ManageBarangaysScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ADMIN_TEACHERS) {
            ManageTeachersScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ADMIN_USERS) {
            ManageUsersScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ADMIN_BACKUP) {
            BackupRestoreScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ADMIN_NOTIFICATIONS) {
            NotificationSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ADMIN_DEVICES) {
            ManageDevicesScreen(onBack = { navController.popBackStack() })
        }
    }
}
