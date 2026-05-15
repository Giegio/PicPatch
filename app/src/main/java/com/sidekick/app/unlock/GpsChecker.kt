package com.sidekick.app.unlock

import android.location.Location
import com.sidekick.app.data.model.CardData

/**
 * Utility per calcolare la distanza tra la posizione attuale del dispositivo
 * e le coordinate target di una card GPS/HYBRID.
 *
 * Usa [Location.distanceBetween] (calcolo geodesico su sferoide WGS-84).
 */
object GpsChecker {

    /**
     * Restituisce la distanza in metri tra la posizione attuale e il target della card.
     * Ritorna null se la card non ha coordinate GPS.
     */
    fun distanceTo(card: CardData, currentLat: Double, currentLon: Double): Float? {
        val targetLat = card.gpsLat ?: return null
        val targetLon = card.gpsLon ?: return null

        val result = FloatArray(1)
        Location.distanceBetween(currentLat, currentLon, targetLat, targetLon, result)
        return result[0]
    }

    /**
     * true se il dispositivo si trova entro il raggio target della card.
     */
    fun isWithinRadius(card: CardData, currentLat: Double, currentLon: Double): Boolean {
        val distance = distanceTo(card, currentLat, currentLon) ?: return false
        return distance <= card.gpsRadiusMeters
    }
}
