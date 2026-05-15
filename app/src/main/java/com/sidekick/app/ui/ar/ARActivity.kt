package com.sidekick.app.ui.ar

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.ar.core.AugmentedImage
import com.google.ar.core.AugmentedImageDatabase
import com.google.ar.core.Config
import com.google.ar.core.TrackingState
import com.sidekick.app.R
import com.sidekick.app.PicPatchApp
import com.sidekick.app.data.model.CardData
import com.sidekick.app.data.model.CardType
import com.sidekick.app.databinding.ActivityArBinding
import com.sidekick.app.unlock.GpsChecker
import io.github.sceneview.ar.ArSceneView
import io.github.sceneview.ar.node.ArModelNode
import io.github.sceneview.ar.node.PlacementMode

/**
 * Gestisce l'esperienza AR per le card speciali (GPS, IMAGE_MARKER, HYBRID).
 *
 * Flusso:
 *  1. Verifica permessi Camera (e Location per GPS/HYBRID)
 *  2. Inizializza ArSceneView con configurazione appropriata al tipo di card
 *  3. Per GPS/HYBRID: avvia il GPS polling e mostra "Avvicinati al luogo" fino al raggio
 *  4. Per IMAGE_MARKER/HYBRID: configura AugmentedImageDatabase con il marker della card
 *  5. Quando tutte le condizioni sono soddisfatte: mostra il modello AR flottante
 *     e il pulsante "Sblocca"
 *  6. Al tap su "Sblocca": chiama UnlockManager e chiude l'activity
 */
class ARActivity : AppCompatActivity() {

    private lateinit var binding: ActivityArBinding
    private lateinit var cardData: CardData

    // GPS
    private var fusedLocation: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var isGpsMet = false

    // Image marker
    private var isMarkerDetected = false

    // AR node della card
    private var cardNode: ArModelNode? = null

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityArBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val cardId = intent.getIntExtra(EXTRA_CARD_ID, -1)
        val app = applicationContext as PicPatchApp
        cardData = app.cardRepository.getById(cardId) ?: run { finish(); return }

        // Precondizione: il GPS deve essere soddisfatto per TIMER? No, siamo in AR.
        // GPS è già stato verificato dal repository; qui inizializziamo l'AR.
        isGpsMet = cardData.type == CardType.IMAGE_MARKER  // IMAGE_MARKER non ha GPS

        setupButtons()
        requestPermissionsAndStart()
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
        stopGps()
    }

    override fun onDestroy() {
        super.onDestroy()
        cardNode?.destroy()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Permissions
    // ─────────────────────────────────────────────────────────────────────────

    private val cameraPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onCameraGranted() else finish()
    }

    private val locationPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startGps() else {
            // GPS non disponibile: se è HYBRID trattiamo come IMAGE_MARKER only
            isGpsMet = true
            updateUnlockButton()
        }
    }

    private fun requestPermissionsAndStart() {
        if (cardData.type == CardType.GPS) {
            // Per GPS puro: niente fotocamera o AR, solo check posizione
            binding.arSceneView.visibility = View.GONE
            isMarkerDetected = true
            startGpsIfPermitted()
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            cameraPermLauncher.launch(Manifest.permission.CAMERA)
        } else {
            onCameraGranted()
        }
    }

    private fun onCameraGranted() {
        initArScene()
        startGpsIfPermitted()
    }

    private fun startGpsIfPermitted() {
        val needsGps = cardData.type == CardType.GPS || cardData.type == CardType.HYBRID
        if (needsGps) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
            ) {
                startGps()
            } else {
                locationPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AR Scene initialization
    // ─────────────────────────────────────────────────────────────────────────

    private fun initArScene() {
        val sceneView = binding.arSceneView

        // Configura ARCore session
        sceneView.configureSession { session, config ->
            // Image tracking per IMAGE_MARKER e HYBRID
            if (cardData.type == CardType.IMAGE_MARKER || cardData.type == CardType.HYBRID) {
                cardData.markerPath?.let { markerAssetPath ->
                    try {
                        val db = AugmentedImageDatabase(session)
                        val bitmap = com.sidekick.app.util.BitmapUtils.decodeSampledBitmapFromAsset(
                            this@ARActivity, markerAssetPath, 800, 800
                        )
                        if (bitmap == null) throw Exception("BitmapFactory ha restituito null (file corrotto o formato non valido)")
                        db.addImage("card_marker", bitmap)
                        config.setAugmentedImageDatabase(db)
                        config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE)
                    } catch (e: Exception) {
                        // Mostriamo l'errore reale per capire cosa fallisce
                        val errorMsg = e.message ?: e.toString()
                        runOnUiThread {
                            Toast.makeText(this@ARActivity, "ERRORE Marker: $errorMsg", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } else {
                // GPS only: nessun image tracking necessario
                isMarkerDetected = true
            }
        }

        // Ascolta i frame AR per rilevare il marker
        sceneView.onArFrame = { frame ->
            if (cardData.type == CardType.IMAGE_MARKER || cardData.type == CardType.HYBRID) {
                val augImages = frame.updatedAugmentedImages
                for (img in augImages) {
                    if (img.trackingState == TrackingState.TRACKING && !isMarkerDetected) {
                        isMarkerDetected = true
                        runOnUiThread {
                            showCardInAr(sceneView, img)
                            updateUnlockButton()
                        }
                    }
                }
            } else if (!isMarkerDetected) {
                // GPS only: mostra il modello AR appena il piano è trovato
                isMarkerDetected = true
                runOnUiThread { showCardInArAtCenter(sceneView) }
            }
        }

        updateStatusLabel()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AR Node — card flottante 2.5D
    // ─────────────────────────────────────────────────────────────────────────

    private fun showCardInAr(sceneView: ArSceneView, augImage: AugmentedImage) {
        if (cardNode != null) return

        cardNode = ArModelNode(
            modelGlbFileLocation = "ar/card_frame.glb",
            onError = { _ ->
                loadCardBitmapFallback(sceneView)
            }
        ).apply {
            placementMode = PlacementMode.INSTANT
            // Ancora il nodo all'immagine rilevata
            anchor = augImage.createAnchor(augImage.centerPose)
        }
        sceneView.addChild(cardNode!!)

        // Animazione fluttuante: oscillazione sull'asse Y
        startFloatingAnimation()
    }

    private fun showCardInArAtCenter(sceneView: ArSceneView) {
        if (cardNode != null) return

        cardNode = ArModelNode(
            modelGlbFileLocation = "ar/card_frame.glb"
        ).apply {
            placementMode = PlacementMode.PLANE_HORIZONTAL_AND_VERTICAL
            isVisible = true
        }
        sceneView.addChild(cardNode!!)

        startFloatingAnimation()
    }

    private fun loadCardBitmapFallback(sceneView: ArSceneView) {
        // Se il modello glb non è disponibile, il nodo rimane visibile
        // come placeholder — lo sviluppatore può aggiungere card_frame.glb
        // negli assets/ar/ per il rendering completo.
    }

    private fun startFloatingAnimation() {
        val node = cardNode ?: return
        var angle = 0f
        var baseY: Float? = null   // posizione Y iniziale del nodo, campionata al primo frame
        val handler = android.os.Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                if (isDestroyed) return
                // Campiona la posizione base una sola volta
                if (baseY == null) baseY = node.position.y
                angle = (angle + 1f) % 360f
                val radians = Math.toRadians(angle.toDouble())
                val oscillation = (Math.sin(radians) * 0.04).toFloat()
                // Imposta sempre Y relativo alla base, non accumulare
                node.position = dev.romainguy.kotlin.math.Float3(
                    node.position.x,
                    baseY!! + oscillation,
                    node.position.z
                )
                // Lenta rotazione sull'asse Y (360° ogni ~20s a 60fps)
                node.rotation = dev.romainguy.kotlin.math.Float3(0f, angle * 0.3f, 0f)
                handler.postDelayed(this, 16)
            }
        }
        handler.post(runnable)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GPS polling
    // ─────────────────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private fun startGps() {
        fusedLocation = LocationServices.getFusedLocationProviderClient(this)

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            GPS_INTERVAL_MS
        ).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc: Location = result.lastLocation ?: return
                val met = GpsChecker.isWithinRadius(cardData, loc.latitude, loc.longitude)
                if (met != isGpsMet) {
                    isGpsMet = met
                    runOnUiThread { updateStatusLabel(); updateUnlockButton() }
                }
            }
        }

        fusedLocation?.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
        updateStatusLabel()
    }

    private fun stopGps() {
        locationCallback?.let { fusedLocation?.removeLocationUpdates(it) }
        locationCallback = null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI state
    // ─────────────────────────────────────────────────────────────────────────

    private fun updateStatusLabel() {
        val label = when {
            !isGpsMet && !isMarkerDetected -> getString(R.string.ar_approach_and_scan)
            !isGpsMet                       -> getString(R.string.ar_approach)
            !isMarkerDetected               -> getString(R.string.ar_scanning)
            else                            -> null
        }
        binding.tvArStatus.visibility = if (label != null) View.VISIBLE else View.GONE
        binding.tvArStatus.text = label
    }

    private fun updateUnlockButton() {
        val allMet = isGpsMet && isMarkerDetected
        binding.btnArUnlock.visibility = if (allMet) View.VISIBLE else View.GONE
    }

    private fun setupButtons() {
        binding.btnArClose.setOnClickListener { finish() }

        binding.btnArUnlock.setOnClickListener {
            val app = applicationContext as PicPatchApp
            val wasNew = app.unlockManager.unlockSpecialCard(cardData.id)
            if (wasNew) {
                Toast.makeText(this, getString(R.string.toast_unlocked), Toast.LENGTH_LONG).show()
            }
            finish()
        }
    }

    companion object {
        const val EXTRA_CARD_ID = "extra_card_id"
        private const val GPS_INTERVAL_MS = 3000L
    }
}
