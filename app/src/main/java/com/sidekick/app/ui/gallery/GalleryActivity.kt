package com.sidekick.app.ui.gallery

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.sidekick.app.R
import com.sidekick.app.PicPatchApp
import com.sidekick.app.data.model.CardState
import com.sidekick.app.data.model.CardType
import com.sidekick.app.databinding.ActivityGalleryBinding
import com.sidekick.app.ui.ar.ARActivity
import com.sidekick.app.ui.detail.DetailFragment
import android.content.Intent
import android.widget.Toast
import com.sidekick.app.unlock.UnlockManager
import com.sidekick.app.notification.NotificationReceiver
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * Entry point dell'app e schermata principale.
 *
 * Responsabilità:
 *  1. Eseguire [UnlockManager.runTimerCheck] al primo avvio e ogni volta
 *     che l'app torna in foreground
 *  2. Popolare la RecyclerView con le [CardState]
 *  3. Mostrare il toast animato per ogni card appena sbloccata
 *  4. Aprire [DetailFragment] al tap su una card sbloccata
 */
class GalleryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGalleryBinding
    private lateinit var adapter: GalleryAdapter
    private lateinit var unlockManager: UnlockManager

    // ID delle card sbloccate in questa sessione (per il toast)
    private val newlyUnlockedThisSession = mutableSetOf<Int>()

    private var pulseAnimator: android.animation.ObjectAnimator? = null
    private var isHeightReduced: Boolean? = null

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    private val countdownHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val countdownRunnable = object : Runnable {
        override fun run() {
            updateDrawButtonUI()
            countdownHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        unlockManager = (applicationContext as PicPatchApp).unlockManager
        unlockManager.checkFirstLaunch()

        setupRecyclerView()
        setupDrawButton()
        checkNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        refreshGallery()
        countdownHandler.post(countdownRunnable)
    }

    override fun onPause() {
        super.onPause()
        countdownHandler.removeCallbacks(countdownRunnable)
        scheduleNextNotification()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Setup
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        adapter = GalleryAdapter(this) { cardState ->
            if (cardState.isUnlocked) {
                openDetail(cardState)
            } else {
                when (cardState.data.type) {
                    CardType.TIMER -> {
                        Toast.makeText(this, R.string.toast_timer_locked, Toast.LENGTH_SHORT).show()
                    }
                    CardType.GPS -> {
                        val intent = android.content.Intent(this, com.sidekick.app.ui.gps.GPSActivity::class.java).apply {
                            putExtra(com.sidekick.app.ui.gps.GPSActivity.EXTRA_CARD_ID, cardState.data.id)
                        }
                        startActivity(intent)
                    }
                    else -> {
                        // IMAGE_MARKER o HYBRID
                        val intent = android.content.Intent(this, com.sidekick.app.ui.ar.ARActivity::class.java).apply {
                            putExtra(com.sidekick.app.ui.ar.ARActivity.EXTRA_CARD_ID, cardState.data.id)
                        }
                        startActivity(intent)
                    }
                }
            }
        }

        binding.rvGallery.apply {
            layoutManager = GridLayoutManager(this@GalleryActivity, GRID_COLUMNS)
            adapter = this@GalleryActivity.adapter
            setHasFixedSize(false)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Timer check + UI refresh
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupDrawButton() {
        binding.btnDrawCard.minimumHeight = 0 // Remove default MaterialButton minHeight
        binding.btnDrawCard.setOnClickListener {
            val card = unlockManager.drawTimerCard()
            if (card != null) {
                newlyUnlockedThisSession.add(card.id)
                refreshGallery()
                
                // Mostra la carta a schermo intero appena sbloccata
                DetailFragment.newInstance(card.id)
                    .show(supportFragmentManager, DetailFragment.TAG)
                
                scheduleNextNotification()
            }
        }
    }

    private fun updateButtonAnimation(available: Boolean) {
        val btn = binding.btnDrawCard
        val density = resources.displayMetrics.density
        val fullHeight = (60 * density).toInt()
        val reducedHeight = (32 * density).toInt()
        val targetHeight = if (available) fullHeight else reducedHeight
        
        val fullTextSize = 22f
        val reducedTextSize = 12f
        val targetTextSize = if (available) fullTextSize else reducedTextSize

        if (isHeightReduced != !available) {
            isHeightReduced = !available
            
            btn.insetTop = 0
            btn.insetBottom = 0
            btn.setPadding(btn.paddingLeft, 0, btn.paddingRight, 0)

            val startHeight = btn.layoutParams.height.takeIf { it > 0 } ?: fullHeight
            val heightAnim = android.animation.ValueAnimator.ofInt(startHeight, targetHeight)
            heightAnim.addUpdateListener { va ->
                val lp = btn.layoutParams
                lp.height = va.animatedValue as Int
                btn.layoutParams = lp
            }

            val startTextSize = btn.textSize / resources.displayMetrics.scaledDensity
            val sizeAnim = android.animation.ValueAnimator.ofFloat(startTextSize, targetTextSize)
            sizeAnim.addUpdateListener { va ->
                btn.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, va.animatedValue as Float)
            }

            android.animation.AnimatorSet().apply {
                playTogether(heightAnim, sizeAnim)
                duration = 400
                start()
            }
        }

        if (available) {
            if (pulseAnimator == null) {
                // Effetto "Onda": scala più pronunciata ed elevazione (ombra) che pulsa
                val scaleX = android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.12f)
                val scaleY = android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.12f)
                val translationZ = android.animation.PropertyValuesHolder.ofFloat(View.TRANSLATION_Z, 2f * density, 16f * density)
                
                pulseAnimator = android.animation.ObjectAnimator.ofPropertyValuesHolder(btn, scaleX, scaleY, translationZ).apply {
                    duration = 1500
                    repeatCount = android.animation.ValueAnimator.INFINITE
                    repeatMode = android.animation.ValueAnimator.REVERSE
                    interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                    start()
                }
            }
        } else {
            pulseAnimator?.cancel()
            pulseAnimator = null
            btn.animate().scaleX(1f).scaleY(1f).translationZ(0f).setDuration(200).start()
        }
    }

    private fun updateDrawButtonUI() {
        val available = unlockManager.getAvailableDraws()
        if (available > 0) {
            binding.btnDrawCard.isEnabled = true
            binding.btnDrawCard.text = "Sblocca un ricordo..."
            binding.btnDrawCard.backgroundTintList = ContextCompat.getColorStateList(this, R.color.color_rosa_cipria)
            binding.btnDrawCard.setTextColor(getColor(R.color.color_on_rosa))
            updateButtonAnimation(true)
        } else {
            val ms = unlockManager.getTimeToNextDrawMs()
            if (ms == 0L) {
                 binding.btnDrawCard.isEnabled = false
                 binding.btnDrawCard.text = "Tutti i ricordi svelati ✨"
                 binding.btnDrawCard.backgroundTintList = ContextCompat.getColorStateList(this, R.color.color_lilla)
                 binding.btnDrawCard.setTextColor(getColor(R.color.color_on_lilla))
            } else {
                 binding.btnDrawCard.isEnabled = false
                 val h = (ms / 3600000)
                 val m = (ms % 3600000) / 60000
                 val s = (ms % 60000) / 1000
                 binding.btnDrawCard.text = String.format("Prossimo tra %02d:%02d:%02d", h, m, s)
                 binding.btnDrawCard.backgroundTintList = ContextCompat.getColorStateList(this, R.color.color_lilla)
                 binding.btnDrawCard.setTextColor(getColor(R.color.color_on_lilla))
            }
            updateButtonAnimation(false)
        }
    }

    private fun refreshGallery() {
        val states = unlockManager.buildCardStates(newlyUnlockedThisSession)
        adapter.submitList(states)
        updateProgress(states)
        updateDrawButtonUI()
    }

    private fun updateProgress(states: List<CardState>) {
        val total = states.size
        val unlocked = states.count { it.isUnlocked }

        binding.progressBar.max = total
        binding.progressBar.setProgress(unlocked, /* animated = */ true)
        binding.tvProgressLabel.text = getString(R.string.progress_format, unlocked, total)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Toast animato
    // ─────────────────────────────────────────────────────────────────────────

    private fun showUnlockToast() {
        val toast = binding.toastUnlock
        toast.animate()
            .alpha(1f)
            .setDuration(TOAST_FADE_MS)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // Aspetta un po' poi scompare
                    toast.postDelayed({
                        toast.animate()
                            .alpha(0f)
                            .setDuration(TOAST_FADE_MS)
                            .setListener(null)
                            .start()
                    }, TOAST_HOLD_MS)
                }
            })
            .start()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Navigation → Detail
    // ─────────────────────────────────────────────────────────────────────────

    private fun openDetail(state: CardState) {
        DetailFragment.newInstance(state.data.id)
            .show(supportFragmentManager, DetailFragment.TAG)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Notification logic
    // ─────────────────────────────────────────────────────────────────────────

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun scheduleNextNotification() {
        val ms = unlockManager.getTimeToNextDrawMs()
        if (ms <= 0) return

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, NotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = System.currentTimeMillis() + ms
        alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
    }

    companion object {
        private const val GRID_COLUMNS = 3
        private const val TOAST_FADE_MS = 400L
        private const val TOAST_HOLD_MS = 2500L
    }
}
