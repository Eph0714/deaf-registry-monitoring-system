package com.deafregistry.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object MapsUtil {
    /** Opens the given coordinates in the Google Maps app (or any maps app) for viewing/navigation. */
    fun openInMaps(context: Context, latitude: Double, longitude: Double, label: String) {
        val geoUri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude(${Uri.encode(label)})")
        val intent = Intent(Intent.ACTION_VIEW, geoUri)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$latitude,$longitude")
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
            } catch (e2: ActivityNotFoundException) {
                Toast.makeText(context, "No app available to show the map", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
