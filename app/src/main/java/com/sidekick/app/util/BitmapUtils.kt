package com.sidekick.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.InputStream

/**
 * Utility per il caricamento ottimizzato delle immagini dagli Assets.
 * Previene errori di OutOfMemory (OOM) eseguendo il downsampling delle immagini
 * in base alle dimensioni target richieste.
 */
object BitmapUtils {

    /**
     * Carica una bitmap dagli assets eseguendo il downsampling.
     * 
     * @param context Il contesto dell'applicazione
     * @param assetPath Il percorso relativo del file negli assets
     * @param reqWidth Larghezza target desiderata (in pixel)
     * @param reqHeight Altezza target desiderata (in pixel)
     * @return La bitmap caricata e ridimensionata, o null in caso di errore
     */
    fun decodeSampledBitmapFromAsset(
        context: Context,
        assetPath: String,
        reqWidth: Int,
        reqHeight: Int
    ): Bitmap? {
        return try {
            // Primo passo: decodifica solo i bordi per conoscere le dimensioni originali
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.assets.open(assetPath).use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            // Calcola il fattore di campionamento (inSampleSize)
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)

            // Secondo passo: decodifica la bitmap reale con il fattore calcolato
            options.inJustDecodeBounds = false
            context.assets.open(assetPath).use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Calcola il valore ottimale per inSampleSize.
     * Una potenza di 2 è solitamente più efficiente per BitmapFactory.
     */
    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            // Calcola il più grande inSampleSize che sia una potenza di 2 e mantenga
            // sia l'altezza che la larghezza maggiori di quelle richieste.
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }
}
