package com.sidekick.app.data.persistence

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.sidekick.app.data.model.SaveData
import java.io.File

/**
 * Gestisce la persistenza di [SaveData] su disco.
 *
 * Il file JSON vive in Context.filesDir/save_data.json.
 * È privato all'app: non richiede permessi e non è accessibile ad altre app.
 *
 * Thread-safety: tutti i metodi pubblici sono @Synchronized per evitare
 * race condition in caso di accesso concorrente (es. foreground + timer check).
 */
class SaveSystem(context: Context) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val saveFile: File = File(context.filesDir, FILE_NAME)

    /**
     * Carica [SaveData] da disco.
     * Se il file non esiste o è corrotto, restituisce un [SaveData] vuoto (primo avvio).
     */
    @Synchronized
    fun load(): SaveData {
        if (!saveFile.exists()) return SaveData()
        return try {
            val json = saveFile.readText(Charsets.UTF_8)
            val type = object : com.google.gson.reflect.TypeToken<SaveData>() {}.type
            gson.fromJson<SaveData>(json, type) ?: SaveData()
        } catch (e: Exception) {
            // File corrotto: ripristina stato pulito
            SaveData()
        }
    }

    /**
     * Salva [saveData] su disco in modo atomico (write-then-rename).
     * Garantisce che il file non rimanga in stato parzialmente scritto
     * in caso di crash durante la scrittura.
     */
    @Synchronized
    fun save(saveData: SaveData) {
        val tmpFile = File(saveFile.parent, "$FILE_NAME.tmp")
        try {
            val json = gson.toJson(saveData)
            tmpFile.writeText(json, Charsets.UTF_8)
            // Rename atomico
            tmpFile.renameTo(saveFile)
        } catch (e: Exception) {
            tmpFile.delete()
            throw e
        }
    }

    /** Elimina il file di salvataggio (reset completo dei progressi). */
    @Synchronized
    fun reset() {
        saveFile.delete()
    }

    /** true se esiste già un file di salvataggio (non è il primo avvio). */
    fun hasSaveFile(): Boolean = saveFile.exists()

    companion object {
        private const val FILE_NAME = "save_data.json"
    }
}
