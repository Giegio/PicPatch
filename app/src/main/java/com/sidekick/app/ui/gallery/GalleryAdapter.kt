package com.sidekick.app.ui.gallery

import android.content.Context
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sidekick.app.data.model.CardState
import com.sidekick.app.databinding.ItemCardBinding

/**
 * Adapter per la griglia della gallery.
 *
 * Usa [ListAdapter] + [DiffUtil] per aggiornamenti efficienti senza
 * notifyDataSetChanged() (evita il flash sulla griglia).
 *
 * Ogni card può essere in due stati visivi:
 *  - LOCKED  → overlay scuro + icona lucchetto, non cliccabile
 *  - UNLOCKED → foto + titolo, cliccabile → [onCardClick]
 */
class GalleryAdapter(
    private val context: Context,
    private val onCardClick: (CardState) -> Unit
) : ListAdapter<CardState, GalleryAdapter.CardViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val binding = ItemCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return CardViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    // ─────────────────────────────────────────────────────────────────────────

    inner class CardViewHolder(
        private val binding: ItemCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(state: CardState) {
            if (state.isUnlocked) bindUnlocked(state) else bindLocked(state)
        }

        private fun bindUnlocked(state: CardState) {
            val colorIndex = (state.data.id - 1) % 3
            with(binding) {
                loadAssetImage(state.data.photoPath)

                tvCardTitle.text = state.data.title
                tvCardTitle.visibility = View.VISIBLE
                lockedOverlay.visibility = View.GONE
                ivLock.visibility = View.GONE

                // Colore di sfondo della card (interno)
                val bgColor = when (colorIndex) {
                    0 -> androidx.core.content.ContextCompat.getColor(context, com.sidekick.app.R.color.color_lilla)
                    1 -> androidx.core.content.ContextCompat.getColor(context, com.sidekick.app.R.color.color_rosa_cipria)
                    else -> androidx.core.content.ContextCompat.getColor(context, com.sidekick.app.R.color.color_oro_pastello)
                }
                
                // Cornice bianca, interno colorato
                cardRoot.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(context, android.R.color.white))
                binding.root.findViewById<View>(com.sidekick.app.R.id.photo_frame).parent.let { 
                    (it as View).setBackgroundColor(bgColor)
                }
                
                cardRoot.cardElevation = 4f.dpToPx()
                cardRoot.alpha = 1f

                cardRoot.isClickable = true
                cardRoot.setOnClickListener { onCardClick(state) }
            }
        }

        private fun bindLocked(state: CardState) {
            val colorIndex = (state.data.id - 1) % 3
            with(binding) {
                ivCardPhoto.setImageDrawable(null)

                // Cornice bianca
                cardRoot.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(context, android.R.color.white))
                
                // Overlay colorato (come "prima")
                val bgRes = when (colorIndex) {
                    0 -> com.sidekick.app.R.drawable.bg_card_locked_lilac
                    1 -> com.sidekick.app.R.drawable.bg_card_locked_pink
                    else -> com.sidekick.app.R.drawable.bg_card_locked_gold
                }
                lockedOverlay.setBackgroundResource(bgRes)
                lockedOverlay.visibility = View.VISIBLE

                // Icona coordinata
                val iconRes = when (colorIndex) {
                    0 -> com.sidekick.app.R.drawable.ic_lock_heart
                    1 -> com.sidekick.app.R.drawable.ic_lock_flower
                    else -> com.sidekick.app.R.drawable.ic_lock_star
                }
                ivLock.setImageResource(iconRes)
                ivLock.visibility = View.VISIBLE

                tvCardTitle.visibility = View.GONE
                cardRoot.cardElevation = 2f.dpToPx()
                cardRoot.alpha = 1f

                cardRoot.isClickable = true
                cardRoot.setOnClickListener { onCardClick(state) }
            }
        }

        private fun loadAssetImage(assetPath: String) {
            // Per la gallery (anteprima), carichiamo una versione ridotta (es. 400px larghezza)
            val bitmap = com.sidekick.app.util.BitmapUtils.decodeSampledBitmapFromAsset(
                context, assetPath, 400, 400
            )
            
            if (bitmap != null) {
                binding.ivCardPhoto.setImageBitmap(bitmap)
                binding.ivCardPhoto.background = null
            } else {
                binding.ivCardPhoto.setImageDrawable(null)
                binding.ivCardPhoto.setBackgroundColor(
                    androidx.core.content.ContextCompat.getColor(context, com.sidekick.app.R.color.color_lilla)
                )
            }
        }

        private fun Float.dpToPx(): Float =
            this * context.resources.displayMetrics.density
    }

    // ─────────────────────────────────────────────────────────────────────────

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<CardState>() {
            override fun areItemsTheSame(old: CardState, new: CardState) =
                old.id == new.id

            override fun areContentsTheSame(old: CardState, new: CardState) =
                old.isUnlocked == new.isUnlocked &&
                old.data.title == new.data.title
        }
    }
}
