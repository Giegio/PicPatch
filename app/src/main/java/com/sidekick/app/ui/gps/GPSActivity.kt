package com.sidekick.app.ui.gps

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.sidekick.app.R
import com.sidekick.app.PicPatchApp
import com.sidekick.app.data.model.CardData
import com.sidekick.app.databinding.ActivityGpsUnlockBinding
import com.sidekick.app.unlock.GpsChecker
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.content.Context

class GPSActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var binding: ActivityGpsUnlockBinding
    private lateinit var cardData: CardData
    
    private var fusedLocation: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null

    // Bussole e Orientamento
    private lateinit var sensorManager: SensorManager
    private var rotationSensor: Sensor? = null
    private var lastLocation: Location? = null
    private var targetBearing: Float = 0f
    private var currentAzimuth: Float = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGpsUnlockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val cardId = intent.getIntExtra(EXTRA_CARD_ID, -1)
        val app = applicationContext as PicPatchApp
        cardData = app.cardRepository.getById(cardId) ?: run { finish(); return }

        setupUI()
        startAnimations()
        requestLocationPermission()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }
        binding.tvStatus.text = "Inizializzazione..."
    }

    private fun startAnimations() {
        // Cuore pulsante
        val pulse = android.animation.ObjectAnimator.ofPropertyValuesHolder(
            binding.ivHeart,
            android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.2f),
            android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.2f)
        ).apply {
            duration = 1000
            repeatCount = android.animation.ValueAnimator.INFINITE
            repeatMode = android.animation.ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        // Halo pulsante (leggermente sfasato)
        android.animation.ObjectAnimator.ofPropertyValuesHolder(
            binding.viewHalo,
            android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 0.8f, 2.5f),
            android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.8f, 2.5f),
            android.animation.PropertyValuesHolder.ofFloat(View.ALPHA, 0.3f, 0f)
        ).apply {
            duration = 2000
            repeatCount = android.animation.ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private val locationPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startGps() else {
            Toast.makeText(this, "Il GPS è necessario per sbloccare questa card!", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startGps()
        } else {
            locationPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startGps() {
        fusedLocation = LocationServices.getFusedLocationProviderClient(this)
        
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L).build()
        
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                updateProximityUI(loc)
            }
        }
        
        fusedLocation?.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
    }

    override fun onResume() {
        super.onResume()
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    private fun updateProximityUI(currentLocation: Location) {
        val target = Location("target").apply {
            latitude = cardData.gpsLat ?: 0.0
            longitude = cardData.gpsLon ?: 0.0
        }
        val distance = currentLocation.distanceTo(target)

        when {
            distance <= cardData.gpsRadiusMeters -> {
                handleUnlock()
            }
            distance < 100 -> {
                binding.tvStatus.text = "Ci sei quasi!"
                binding.tvProximityHint.text = "Sento una magia fortissima... guarda meglio!"
            }
            distance < 500 -> {
                binding.tvStatus.text = "Ti stai avvicinando!"
                binding.tvProximityHint.text = "La direzione è quella giusta, continua così."
            }
            distance < 1000 -> {
                binding.tvStatus.text = "Sei nei paraggi!"
                binding.tvProximityHint.text = "Segui la freccia per trovare il punto esatto."
            }
            else -> {
                binding.tvStatus.text = "Sei ancora un po' lontano..."
                binding.tvProximityHint.text = cardData.gpsHint ?: "Cerca un posto speciale che ricordi la nostra storia."
            }
        }

        // Aggiorna visibilità freccia e testo della distanza
        if (distance > cardData.gpsRadiusMeters) {
            binding.tvDistance.visibility = View.VISIBLE
            if (distance >= 1000) {
                binding.tvDistance.text = String.format(java.util.Locale.getDefault(), "%.1f km", distance / 1000.0)
            } else {
                binding.tvDistance.text = String.format(java.util.Locale.getDefault(), "%d m", distance.toInt())
            }
        } else {
            binding.tvDistance.visibility = View.INVISIBLE
        }

        val showArrow = distance > cardData.gpsRadiusMeters && distance <= 1000.0
        binding.ivNavigationArrow.visibility = if (showArrow) View.VISIBLE else View.INVISIBLE
        
        lastLocation = currentLocation
        targetBearing = currentLocation.bearingTo(target)
        updateArrowRotation()
    }

    private var displayedRotation = 0f

    private fun updateArrowRotation() {
        if (binding.ivNavigationArrow.visibility != View.VISIBLE) return

        // La rotazione desiderata della freccia è il bearing (angolo verso target) 
        // meno l'azimuth (angolo dove punta il telefono)
        val desiredRotation = (targetBearing - currentAzimuth + 360) % 360
        
        // Calcola la differenza più breve
        var diff = desiredRotation - (displayedRotation % 360)
        while (diff < -180) diff += 360
        while (diff > 180) diff -= 360

        // Inseriamo una tolleranza di 15 gradi per evitare che tremi continuamente,
        // e usiamo un'animazione per rallentare/ammorbidire la rotazione
        if (kotlin.math.abs(diff) > 15f) {
            displayedRotation += diff
            binding.ivNavigationArrow.animate()
                .rotation(displayedRotation)
                .setDuration(400)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientation)
            
            // Azimuth è l'angolo sull'asse Z (orientamento del telefono rispetto al Nord)
            currentAzimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
            updateArrowRotation()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun handleUnlock() {
        stopGps()
        binding.tvStatus.text = "MAGIA TROVATA!"
        binding.tvProximityHint.text = "Sblocco in corso..."
        binding.ivNavigationArrow.visibility = View.INVISIBLE
        binding.tvDistance.visibility = View.INVISIBLE
        binding.loadingIndicator.visibility = View.VISIBLE
        
        android.os.Handler(Looper.getMainLooper()).postDelayed({
            val app = applicationContext as PicPatchApp
            app.unlockManager.unlockSpecialCard(cardData.id)
            Toast.makeText(this, "Ricordo sbloccato con successo!", Toast.LENGTH_LONG).show()
            finish()
        }, 2000)
    }

    private fun stopGps() {
        locationCallback?.let { fusedLocation?.removeLocationUpdates(it) }
        locationCallback = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopGps()
    }

    companion object {
        const val EXTRA_CARD_ID = "extra_card_id"
    }
}
