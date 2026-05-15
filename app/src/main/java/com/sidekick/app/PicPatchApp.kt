package com.sidekick.app

import android.app.Application
import com.sidekick.app.data.persistence.SaveSystem
import com.sidekick.app.data.repository.CardRepository
import com.sidekick.app.unlock.UnlockManager

/**
 * Application class: unico punto di accesso alle dipendenze condivise.
 * Evita un DI framework mantenendo lo stack snello come da requisiti.
 *
 * Accesso dal codice: (applicationContext as PicPatchApp).unlockManager
 */
class PicPatchApp : Application() {

    val cardRepository: CardRepository by lazy { CardRepository(this) }
    val saveSystem: SaveSystem by lazy { SaveSystem(this) }
    val unlockManager: UnlockManager by lazy {
        UnlockManager(cardRepository, saveSystem)
    }
}
