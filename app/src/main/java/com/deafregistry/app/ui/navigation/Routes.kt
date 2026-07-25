package com.deafregistry.app.ui.navigation

object Routes {
    const val LOGIN = "login"
    const val SIGNUP = "signup"
    const val DASHBOARD = "dashboard"
    const val ALL_INDIVIDUALS = "all_individuals/{title}?sort={sort}"
    const val DEAF_PROFILE = "deaf/{uuid}"
    const val DEAF_EDITOR = "deaf_editor?uuid={uuid}&municipalityId={municipalityId}"
    const val SEARCH = "search"
    const val REPORTS = "reports"
    const val REPORT_CATEGORY_DETAIL = "report_category_detail/{category}/{value}/{extra}"
    const val CONTROL_PANEL = "control_panel"
    const val ADMIN_MUNICIPALITIES = "admin_municipalities"
    const val ADMIN_BARANGAYS = "admin_barangays"
    const val ADMIN_TEACHERS = "admin_teachers"
    const val ADMIN_USERS = "admin_users"
    const val ADMIN_BACKUP = "admin_backup"
    const val ADMIN_NOTIFICATIONS = "admin_notifications"
    const val ADMIN_APP_UPDATE = "admin_app_update"
    const val ADMIN_THEME = "admin_theme"
    const val ADMIN_LOCATION_SHARING = "admin_location_sharing"
    const val ADMIN_PENDING_USERS = "admin_pending_users"
    const val ADMIN_AUDIT_LOG = "admin_audit_log"
    const val ADMIN_RESET_DATA = "admin_reset_data"

    fun allIndividuals(title: String, category: String = "all") =
        "all_individuals/${java.net.URLEncoder.encode(title, "UTF-8")}?sort=$category"

    fun reportCategoryDetail(category: String, value: String, extra: String = "") =
        "report_category_detail/${java.net.URLEncoder.encode(category, "UTF-8")}/" +
            "${java.net.URLEncoder.encode(value, "UTF-8")}/${java.net.URLEncoder.encode(extra, "UTF-8")}"

    fun deafProfile(uuid: String) = "deaf/$uuid"
    fun deafEditorNew(municipalityId: Int) = "deaf_editor?uuid=&municipalityId=$municipalityId"
    fun deafEditorEdit(uuid: String) = "deaf_editor?uuid=$uuid&municipalityId=-1"
}
