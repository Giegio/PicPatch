package com.sidekick.app.data.model

/**
 * Tipo di card che determina il meccanismo di sblocco.
 *
 * - TIMER       → si sblocca automaticamente dopo 24h dal precedente sblocco
 * - GPS         → si sblocca quando il destinatario si avvicina a coordinate specifiche
 * - IMAGE_MARKER→ si sblocca inquadrando un'immagine marker fisica con la fotocamera
 * - HYBRID      → richiede sia il GPS che il marker per sbloccarsi
 */
enum class CardType {
    TIMER,
    GPS,
    IMAGE_MARKER,
    HYBRID
}
