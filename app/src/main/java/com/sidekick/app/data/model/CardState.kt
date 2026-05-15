package com.sidekick.app.data.model

/**
 * Stato RUNTIME di una singola card, derivato da [SaveData] + [CardData].
 * Questo è l'oggetto che il UI layer consuma direttamente.
 *
 * @param data          Configurazione statica della card
 * @param isUnlocked    true se la card è già stata sbloccata
 * @param unlockedAtMs  Timestamp (ms) del momento in cui è stata sbloccata, null se ancora bloccata
 */
data class CardState(
    val data: CardData,
    val isUnlocked: Boolean,
    val unlockedAtMs: Long? = null
) {
    /** Shortcut per accedere all'ID senza passare per data */
    val id: Int get() = data.id

    /** true se questa card appena sbloccata deve mostrare il toast di celebrazione */
    var isNewlyUnlocked: Boolean = false
}
