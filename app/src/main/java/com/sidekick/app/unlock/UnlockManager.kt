package com.sidekick.app.unlock

import com.sidekick.app.data.model.CardData
import com.sidekick.app.data.model.CardState
import com.sidekick.app.data.model.CardType
import com.sidekick.app.data.model.SaveData
import com.sidekick.app.data.persistence.SaveSystem
import com.sidekick.app.data.repository.CardRepository

/**
 * Cuore della logica di sblocco delle card.
 *
 * Responsabilità:
 *  1. Inizializzare il timestamp del primo avvio
 *  2. Calcolare quante finestre 24h sono trascorse e sbloccare card TIMER di conseguenza
 *  3. Fornire la lista di [CardState] aggiornata per il UI
 *  4. Sbloccare card AR su richiesta esplicita (GPS/IMAGE_MARKER/HYBRID)
 *
 * NON gestisce la richiesta di permessi GPS né il tracking AR: quelli vivono nelle Activity.
 */
class UnlockManager(
    private val repository: CardRepository,
    private val saveSystem: SaveSystem,
    private val clock: com.sidekick.app.util.Clock = com.sidekick.app.util.SystemClock()
) {

    companion object {
        private const val WINDOW_MS = 24L * 60 * 60 * 1000 // 24 ore in millisecondi
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Inizializza i timestamp se è il primo avvio.
     * Da chiamare all'avvio dell'app.
     */
    fun checkFirstLaunch() {
        val save = saveSystem.load()
        val now = clock.currentTimeMillis()
        if (save.firstLaunchTimestamp == -1L) {
            val initialized = save.copy(
                firstLaunchTimestamp = now,
                // Sottraendo WINDOW_MS garantiamo che la prima pescata sia subito disponibile
                lastTimerCheckTimestamp = now - WINDOW_MS
            )
            saveSystem.save(initialized)
        }
    }

    /**
     * Restituisce il numero di carte TIMER che possono essere pescate attualmente.
     * Limitato a massimo 1 pescata alla volta (non si accumulano).
     */
    fun getAvailableDraws(): Int {
        val save = saveSystem.load()
        if (save.firstLaunchTimestamp == -1L) return 0
        
        val lockedTimerCards = repository.timerCards.count { !save.isUnlocked(it.id) }
        if (lockedTimerCards == 0) return 0

        val now = clock.currentTimeMillis()
        val elapsed = now - save.lastTimerCheckTimestamp
        
        return if (elapsed >= WINDOW_MS) 1 else 0
    }

    /**
     * Restituisce i millisecondi rimanenti alla prossima pescata.
     * Se ci sono pescate disponibili o se non ci sono più carte TIMER da sbloccare, restituisce 0.
     */
    fun getTimeToNextDrawMs(): Long {
        val save = saveSystem.load()
        if (save.firstLaunchTimestamp == -1L) return 0L
        
        val lockedTimerCards = repository.timerCards.count { !save.isUnlocked(it.id) }
        if (lockedTimerCards == 0) return 0L
        
        val now = clock.currentTimeMillis()
        val elapsed = now - save.lastTimerCheckTimestamp
        if (elapsed >= WINDOW_MS) return 0L
        
        return WINDOW_MS - elapsed
    }

    /**
     * Pesca una singola carta TIMER, consumando una pescata disponibile.
     * Il timer per la prossima carta riparte da questo esatto momento.
     * @return La carta sbloccata, oppure null se non era possibile pescare.
     */
    fun drawTimerCard(): CardData? {
        val save = saveSystem.load()
        if (save.firstLaunchTimestamp == -1L) return null
        
        val now = clock.currentTimeMillis()
        val elapsed = now - save.lastTimerCheckTimestamp
        
        if (elapsed < WINDOW_MS) return null
        
        val lockedTimerCards = repository.timerCards
            .filter { !save.isUnlocked(it.id) }
            .toMutableList()
            
        if (lockedTimerCards.isEmpty()) return null
        
        val card = lockedTimerCards.random()
        
        // Il timer riparte da zero in questo momento
        save.unlock(card.id, now)
        saveSystem.save(save.copy(lastTimerCheckTimestamp = now))
        
        return card
    }

    /**
     * Sblocca una card speciale (GPS / IMAGE_MARKER / HYBRID) su richiesta esplicita
     * dell'ARActivity dopo che le condizioni sono state verificate.
     *
     * @return true se la card è stata sbloccata (era ancora bloccata),
     *         false se era già sbloccata.
     */
    fun unlockSpecialCard(cardId: Int): Boolean {
        val save = saveSystem.load()
        if (save.isUnlocked(cardId)) return false
        save.unlock(cardId, clock.currentTimeMillis())
        saveSystem.save(save)
        return true
    }

    /**
     * Costruisce la lista completa di [CardState] per il UI, combinando
     * la configurazione statica con il save state corrente.
     *
     * @param newlyUnlockedIds  ID delle card appena sbloccate in questa sessione,
     *                          usato per impostare [CardState.isNewlyUnlocked].
     */
    fun buildCardStates(newlyUnlockedIds: Set<Int> = emptySet()): List<CardState> {
        val save = saveSystem.load()
        return repository.cards.map { cardData ->
            val isUnlocked = save.isUnlocked(cardData.id)
            val state = CardState(
                data = cardData,
                isUnlocked = isUnlocked,
                unlockedAtMs = save.unlockTimestamps[cardData.id]
            )
            state.isNewlyUnlocked = cardData.id in newlyUnlockedIds
            state
        }
    }

    /** Restituisce il numero di card sbloccate. */
    fun unlockedCount(): Int = saveSystem.load().unlockedCount

    /**
     * Controlla se il requisito GPS di una card è soddisfatto.
     * Usato da ARActivity prima di tentare l'image marker.
     */
    fun isGpsConditionMet(card: CardData, currentLat: Double, currentLon: Double): Boolean {
        if (card.type != CardType.GPS && card.type != CardType.HYBRID) return true
        return GpsChecker.isWithinRadius(card, currentLat, currentLon)
    }

    /**
     * Resetta completamente i progressi (utile per debug/test).
     */
    fun resetAll() {
        saveSystem.reset()
    }
}
