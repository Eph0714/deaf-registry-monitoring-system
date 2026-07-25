package com.deafregistry.app.ui.navigation

object Routes {
    const val LOGIN = "login"
    const val SIGNUP = "signup"
    const val DASHBOARD = "dashboard"
    const val MUNICIPALITY_OVERVIEW = "municipality_overview"
    const val ALL_INDIVIDUALS = "all_individuals/{title}?sort={sort}"
    const val BARANGAY_LIST = "barangay_list/{municipalityId}/{municipalityName}"
    const val MUNICIPALITY_LIST = "municipality/{municipalityId}/{municipalityName}?barangayId={barangayId}&barangayName={barangayName}"
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
    const val ADMIN_PENDING_USERS = "admin_pending_users"
    const val ADMIN_AUDIT_LOG = "admin_audit_log"
    const val ADMIN_RESET_DATA = "admin_reset_data"

    fun barangayList(id: Int, name: String) = "barangay_list/$id/${java.net.URLEncoder.encode(name, "UTF-8")}"

    fun allIndividuals(title: String, category: String = "all") =
        "all_individuals/${java.net.URLEncoder.encode(title, "UTF-8")}?sort=$category"

    fun municipalityList(id: Int, name: String, barangayId: Int? = null, barangayName: String? = null): String {
        val base = "municipality/$id/${java.net.URLEncoder.encode(name, "UTF-8")}"
        return if (barangayId != null) {
            "$base?barangayId=$barangayId&barangayName=${java.net.URLEncoder.encode(barangayName ?: "", "UTF-8")}"
        } else {
            base
        }
    }

    fun reportCategoryDetail(category: String, value: String, extra: String = "") =
        "report_category_detail/${java.net.URLEncoder.encode(category, "UTF-8")}/" +
            "${java.net.URLEncoder.encode(value, "UTF-8")}/${java.net.URLEncoder.encode(extra, "UTF-8")}"

    fun deafProfile(uuid: String) = "deaf/$uuid"
    fun deafEditorNew(municipalityId: Int) = "deaf_editor?uuid=&municipalityId=$municipalityId"
    fun deafEditorEdit(uuid: String) = "deaf_editor?uuid=$uuid&municipalityId=-1"
}
