package com.deafregistry.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.deafregistry.app.data.session.SessionManager
import com.deafregistry.app.ui.admin.ControlPanelScreen
import com.deafregistry.app.ui.admin.AdminPendingUsersScreen
import com.deafregistry.app.ui.admin.PasswordResetRequestsScreen
import com.deafregistry.app.ui.admin.AuditLogScreen
import com.deafregistry.app.ui.admin.ResetAllDataScreen
import com.deafregistry.app.ui.admin.BackupRestoreScreen
import com.deafregistry.app.ui.admin.ManageBarangaysScreen
import com.deafregistry.app.ui.admin.ManageMunicipalitiesScreen
import com.deafregistry.app.ui.admin.ManageTeachersScreen
import com.deafregistry.app.ui.admin.ManageUsersScreen
import com.deafregistry.app.ui.admin.AppUpdateSettingsScreen
import com.deafregistry.app.ui.admin.ThemeSettingsScreen
import com.deafregistry.app.ui.admin.BiometricLoginSettingsScreen
import com.deafregistry.app.ui.admin.NotificationSettingsScreen
import com.deafregistry.app.ui.admin.LocationSharingSettingsScreen
import com.deafregistry.app.ui.calendar.CalendarScreen
import com.deafregistry.app.ui.chat.ChatRoomScreen
import com.deafregistry.app.ui.chat.ChatSessionManageScreen
import com.deafregistry.app.ui.chat.ChatRecurringSchedulesScreen
import com.deafregistry.app.ui.dashboard.DashboardScreen
import com.deafregistry.app.ui.location.LocationSharingScreen
import com.deafregistry.app.ui.editor.DeafEditorScreen
import com.deafregistry.app.ui.municipality.MunicipalityDirectoryScreen
import com.deafregistry.app.ui.login.LoginScreen
import com.deafregistry.app.ui.login.SignUpScreen
import com.deafregistry.app.ui.profile.DeafProfileScreen
import com.deafregistry.app.ui.reports.MunicipalityStatisticsScreen
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
            LoginScreen(
                onLoggedIn = {
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onSignUp = { navController.navigate(Routes.SIGNUP) }
            )
        }

        composable(Routes.SIGNUP) {
            SignUpScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onOpenDeafRecords = { navController.navigate(Routes.MUNICIPALITY_DIRECTORY) },
                onOpenAllIndividuals = { title, sort -> navController.navigate(Routes.allIndividuals(title, sort)) },
                onOpenSearch = { navController.navigate(Routes.SEARCH) },
                onOpenReports = { navController.navigate(Routes.REPORTS) },
                onOpenControlPanel = { navController.navigate(Routes.CONTROL_PANEL) },
                onOpenAppUpdate = { navController.navigate(Routes.ADMIN_APP_UPDATE) },
                onOpenUserAccounts = { navController.navigate(Routes.ADMIN_USERS) },
                onOpenCalendar = { navController.navigate(Routes.CALENDAR) },
                onOpenLocationSharing = { navController.navigate(Routes.LOCATION_SHARING) },
                onOpenChat = { navController.navigate(Routes.CHAT) },
                onOpenPendingUsers = { navController.navigate(Routes.ADMIN_PENDING_USERS) },
                onOpenMunicipalityStatistics = { navController.navigate(Routes.MUNICIPALITY_STATISTICS) },
                onOpenProfile = { uuid -> navController.navigate(Routes.deafProfile(uuid)) },
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.CALENDAR) {
            CalendarScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.LOCATION_SHARING) {
            LocationSharingScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.MUNICIPALITY_STATISTICS) {
            MunicipalityStatisticsScreen(
                onBack = { navController.popBackStack() },
                onOpenMunicipality = { name -> navController.navigate(Routes.reportCategoryDetail("municipality", name)) }
            )
        }

        composable(Routes.CHAT) {
            ChatRoomScreen(
                onBack = { navController.popBackStack() },
                onManageSessions = { navController.navigate(Routes.ADMIN_CHAT_SESSIONS) }
            )
        }
        composable(Routes.ADMIN_CHAT_SESSIONS) {
            ChatSessionManageScreen(
                onBack = { navController.popBackStack() },
                onOpenRecurringSchedules = { navController.navigate(Routes.CHAT_RECURRING_SCHEDULES) }
            )
        }
        composable(Routes.CHAT_RECURRING_SCHEDULES) {
            ChatRecurringSchedulesScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.MUNICIPALITY_DIRECTORY) {
            MunicipalityDirectoryScreen(
                onBack = { navController.popBackStack() },
                onOpenProfile = { uuid -> navController.navigate(Routes.deafProfile(uuid)) },
                onEditProfile = { uuid -> navController.navigate(Routes.deafEditorEdit(uuid)) },
                onAddNew = { navController.navigate(Routes.deafEditorNew(-1)) }
            )
        }

        composable(
            Routes.ALL_INDIVIDUALS,
            arguments = listOf(
                navArgument("title") { type = NavType.StringType },
                navArgument("sort") { type = NavType.StringType; defaultValue = "all" }
            )
        ) { backStackEntry ->
            val title = URLDecoder.decode(backStackEntry.arguments?.getString("title") ?: "All Records", "UTF-8")
            val category = backStackEntry.arguments?.getString("sort") ?: "all"
            AllIndividualsScreen(
                title = title,
                initialCategory = category,
                onBack = { navController.popBackStack() },
                onOpenProfile = { uuid -> navController.navigate(Routes.deafProfile(uuid)) },
                onAddNew = { navController.navigate(Routes.deafEditorNew(-1)) }
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

        composable(Routes.CONTROL_PANEL) {
            ControlPanelScreen(onBack = { navController.popBackStack() }, onNavigate = { navController.navigate(it) })
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
        composable(Routes.ADMIN_APP_UPDATE) {
            AppUpdateSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ADMIN_THEME) {
            ThemeSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.BIOMETRIC_SETTINGS) {
            BiometricLoginSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ADMIN_LOCATION_SHARING) {
            LocationSharingSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ADMIN_PENDING_USERS) {
            AdminPendingUsersScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ADMIN_PASSWORD_RESET_REQUESTS) {
            PasswordResetRequestsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ADMIN_AUDIT_LOG) {
            AuditLogScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ADMIN_RESET_DATA) {
            ResetAllDataScreen(onBack = { navController.popBackStack() })
        }
    }
}
