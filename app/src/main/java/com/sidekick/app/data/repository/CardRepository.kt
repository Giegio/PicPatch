package com.sidekick.app.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import com.sidekick.app.data.model.CardData
import com.sidekick.app.data.model.CardType

/**
 * Carica e fornisce la lista di [CardData] dal file
 * assets/cards_config.json.
 *
 * Internamente usa un wrapper [CardsConfig] per il parsing Gson,
 * così il JSON può avere un oggetto root con una chiave "cards".
 *
 * Singleton semplice: la lista è immutabile dopo il caricamento.
 */
class CardRepository(
    private val context: Context? = null,
    private val assetManager: android.content.res.AssetManager = context?.assets ?: throw IllegalArgumentException("AssetManager required")
) {

    /** Lista ordinata per ID (1→15), caricata lazy al primo accesso. */
    val cards: List<CardData> by lazy { loadCards() }

    /** Restituisce una card per ID, o null se non trovata. */
    fun getById(id: Int): CardData? = cards.firstOrNull { it.id == id }

    /** Card di tipo TIMER (le 12 che si sbloccano con il timer) */
    val timerCards: List<CardData> by lazy {
        cards.filter { it.type == CardType.TIMER }
    }

    /** Card speciali AR (GPS, IMAGE_MARKER, HYBRID) */
    val specialCards: List<CardData> by lazy {
        cards.filter { it.type != CardType.TIMER }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Parsing
    // ─────────────────────────────────────────────────────────────────────────

    private fun loadCards(): List<CardData> {
        return try {
            val json = assetManager.open(CONFIG_PATH).bufferedReader().use { it.readText() }
            val raw = Gson().fromJson(json, CardsConfigRaw::class.java)
            raw.cards.map { it.toCardData() }.sortedBy { it.id }
        } catch (e: Exception) {
            throw IllegalStateException(
                "Impossibile caricare $CONFIG_PATH. Assicurati che il file esista negli assets.", e
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal raw models (Gson ↔ JSON mapping)
    // ─────────────────────────────────────────────────────────────────────────

    private data class CardsConfigRaw(
        @SerializedName("cards") val cards: List<CardDataRaw> = emptyList()
    )

    private data class CardDataRaw(
        @SerializedName("id")               val id: Int = 0,
        @SerializedName("type")             val type: String = "TIMER",
        @SerializedName("title")            val title: String = "",
        @SerializedName("dedication")       val dedication: String = "",
        @SerializedName("photoPath")        val photoPath: String = "",
        @SerializedName("gpsLat")           val gpsLat: Double? = null,
        @SerializedName("gpsLon")           val gpsLon: Double? = null,
        @SerializedName("gpsRadiusMeters")  val gpsRadiusMeters: Double = 50.0,
        @SerializedName("gpsHint")          val gpsHint: String? = null,
        @SerializedName("markerPath")       val markerPath: String? = null
    ) {
        fun toCardData() = CardData(
            id = id,
            type = CardType.valueOf(type),
            title = title,
            dedication = dedication,
            photoPath = photoPath,
            gpsLat = gpsLat,
            gpsLon = gpsLon,
            gpsRadiusMeters = gpsRadiusMeters,
            gpsHint = gpsHint,
            markerPath = markerPath
        )
    }

    companion object {
        private const val CONFIG_PATH = "cards_config.json"
    }
}
