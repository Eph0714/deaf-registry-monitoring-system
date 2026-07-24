package com.deafregistry.app.ui.navigation

object Routes {
    const val LOGIN = "login"
    const val DASHBOARD = "dashboard"
    const val MUNICIPALITY_OVERVIEW = "municipality_overview"
    const val ALL_BARANGAYS = "all_barangays"
    const val ALL_INDIVIDUALS = "all_individuals/{title}"
    const val BARANGAY_LIST = "barangay_list/{municipalityId}/{municipalityName}"
    const val MUNICIPALITY_LIST = "municipality/{municipalityId}/{municipalityName}?barangayId={barangayId}&barangayName={barangayName}"
    const val DEAF_PROFILE = "deaf/{uuid}"
    const val DEAF_EDITOR = "deaf_editor?uuid={uuid}&municipalityId={municipalityId}"
    const val SEARCH = "search"
    const val REPORTS = "reports"
    const val ADMIN_HOME = "admin_home"
    const val ADMIN_MUNICIPALITIES = "admin_municipalities"
    const val ADMIN_BARANGAYS = "admin_barangays"
    const val ADMIN_TEACHERS = "admin_teachers"
    const val ADMIN_USERS = "admin_users"
    const val ADMIN_BACKUP = "admin_backup"
    const val ADMIN_NOTIFICATIONS = "admin_notifications"
    const val ADMIN_DEVICES = "admin_devices"

    fun barangayList(id: Int, name: String) = "barangay_list/$id/${java.net.URLEncoder.encode(name, "UTF-8")}"

    fun allIndividuals(title: String) = "all_individuals/${java.net.URLEncoder.encode(title, "UTF-8")}"

    fun municipalityList(id: Int, name: String, barangayId: Int? = null, barangayName: String? = null): String {
        val base = "municipality/$id/${java.net.URLEncoder.encode(name, "UTF-8")}"
        return if (barangayId != null) {
            "$base?barangayId=$barangayId&barangayName=${java.net.URLEncoder.encode(barangayName ?: "", "UTF-8")}"
        } else {
            base
        }
    }

    fun deafProfile(uuid: String) = "deaf/$uuid"
    fun deafEditorNew(municipalityId: Int) = "deaf_editor?uuid=&municipalityId=$municipalityId"
    fun deafEditorEdit(uuid: String) = "deaf_editor?uuid=$uuid&municipalityId=-1"
}
