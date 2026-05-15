package com.sidekick.app.ui.detail

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.sidekick.app.R
import com.sidekick.app.PicPatchApp
import com.sidekick.app.data.model.CardData
import com.sidekick.app.data.model.CardType
import com.sidekick.app.databinding.FragmentDetailBinding
import com.sidekick.app.ui.ar.ARActivity

/**
 * Popup fullscreen che mostra foto, titolo e dedica di una card sbloccata.
 *
 * Mostrato come [DialogFragment] fullscreen dalla [GalleryActivity] via
 * `show(supportFragmentManager, TAG)`.
 *
 * Per le card speciali (GPS / IMAGE_MARKER / HYBRID) mostra il pulsante
 * "Visualizza in AR" che avvia [ARActivity].
 */
class DetailFragment : DialogFragment() {

    private var _binding: FragmentDetailBinding? = null
    private val binding get() = _binding!!

    private var cardId: Int = -1
    private lateinit var cardData: CardData

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Fullscreen dialog
        setStyle(STYLE_NORMAL, R.style.Theme_PicPatch_FullscreenDialog)
        cardId = arguments?.getInt(ARG_CARD_ID, -1) ?: -1
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val app = requireActivity().applicationContext as PicPatchApp
        cardData = app.cardRepository.getById(cardId)
            ?: run { dismiss(); return }

        // Lo sfondo della schermata rimane costante (Lilla neutro)
        binding.detailRoot.setBackgroundColor(requireContext().getColor(R.color.color_background))

        // Imposta il colore della Polaroid coerente con la card nella gallery
        val bgColor = when ((cardId - 1) % 3) {
            0 -> requireContext().getColor(R.color.color_lilla)
            1 -> requireContext().getColor(R.color.color_rosa_cipria)
            else -> requireContext().getColor(R.color.color_oro_pastello)
        }
        binding.detailCard.setCardBackgroundColor(bgColor)

        populateContent()
        setupButtons()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Content
    // ─────────────────────────────────────────────────────────────────────────

    private fun populateContent() {
        // Foto
        val bitmap = com.sidekick.app.util.BitmapUtils.decodeSampledBitmapFromAsset(
            requireContext(), cardData.photoPath, 1024, 1024
        )
        
        if (bitmap != null) {
            binding.ivDetailPhoto.setImageBitmap(bitmap)
        } else {
            binding.ivDetailPhoto.setBackgroundColor(
                androidx.core.content.ContextCompat.getColor(requireContext(), R.color.color_primary_variant)
            )
        }

        binding.tvDetailTitle.text = cardData.title
        binding.tvDetailDedication.text = cardData.dedication

        // Pulsante AR: visibile solo per card speciali
        val isSpecial = cardData.type != CardType.TIMER
        binding.btnViewAr.visibility = if (isSpecial) View.VISIBLE else View.GONE
    }

    private fun setupButtons() {
        binding.btnClose.setOnClickListener { dismiss() }
        
        // Chiudi il dettaglio al tocco dello schermo
        binding.detailRoot.setOnClickListener { dismiss() }
        binding.ivDetailPhoto.setOnClickListener { dismiss() }

        binding.btnViewAr.setOnClickListener {
            val intent = Intent(requireContext(), ARActivity::class.java).apply {
                putExtra(ARActivity.EXTRA_CARD_ID, cardId)
            }
            startActivity(intent)
        }
    }

    companion object {
        const val TAG = "DetailFragment"
        private const val ARG_CARD_ID = "card_id"

        fun newInstance(cardId: Int): DetailFragment {
            return DetailFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_CARD_ID, cardId)
                }
            }
        }
    }
}
