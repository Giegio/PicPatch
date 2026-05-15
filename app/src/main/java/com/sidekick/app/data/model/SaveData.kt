package com.sidekick.app.data.model

/**
 * Radice del file JSON persistito in filesDir/save_data.json.
 * Contiene tutto lo stato che sopravvive alle chiusure dell'app.
 *
 * @param firstLaunchTimestamp      ms epoch del PRIMO avvio: da qui parte il conteggio dei timer.
 *                                  -1 = non ancora inizializzato.
 * @param lastTimerCheckTimestamp   ms epoch dell'ultima volta che il timer è stato "consumato".
 *                                  Serve per calcolare quante finestre 24h sono passate.
 * @param unlockedCardIds           Set degli ID delle card già sbloccate.
 * @param unlockTimestamps          Map id→ms di quando ogni card è stata sbloccata.
 */
data class SaveData(
    val firstLaunchTimestamp: Long = -1L,
    val lastTimerCheckTimestamp: Long = -1L,
    val unlockedCardIds: MutableSet<Int> = mutableSetOf(),
    val unlockTimestamps: MutableMap<Int, Long> = mutableMapOf()
) {
    /** Numero di card sbloccate al momento */
    val unlockedCount: Int get() = unlockedCardIds.size

    /** true se la card con questo id è già sbloccata */
    fun isUnlocked(cardId: Int): Boolean = cardId in unlockedCardIds

    /** Sblocca una card e registra il timestamp */
    fun unlock(cardId: Int, atMs: Long = System.currentTimeMillis()) {
        unlockedCardIds.add(cardId)
        unlockTimestamps[cardId] = atMs
    }
}
