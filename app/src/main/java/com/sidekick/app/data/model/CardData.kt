package com.sidekick.app.data.model

/**
 * Configurazione STATICA di una card, caricata da assets/cards_config.json.
 * Non cambia mai a runtime: è la "definizione" della card.
 *
 * @param id            Identificatore univoco (1–15)
 * @param type          Tipo di sblocco ([CardType])
 * @param title         Titolo breve mostrato sulla card e nel popup
 * @param dedication    Testo della dedica mostrato nel popup fullscreen
 * @param photoPath     Percorso relativo agli assets (es. "cards/card_01/photo.jpg")
 * @param gpsLat        Latitudine target (solo per GPS / HYBRID)
 * @param gpsLon        Longitudine target (solo per GPS / HYBRID)
 * @param gpsRadiusMeters Raggio in metri entro cui scatta lo sblocco (default 50m)
 * @param markerPath    Percorso relativo agli assets del marker fisico
 *                      (solo per IMAGE_MARKER / HYBRID)
 */
data class CardData(
    val id: Int,
    val type: CardType,
    val title: String,
    val dedication: String,
    val photoPath: String,
    // GPS fields
    val gpsLat: Double? = null,
    val gpsLon: Double? = null,
    val gpsRadiusMeters: Double = 50.0,
    val gpsHint: String? = null,
    // AR marker field
    val markerPath: String? = null
)
