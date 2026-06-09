package com.example.cheapestoilfinder.entry

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.graphics.Color
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.cheapestoilfinder.destination.DestinationSearchSuggestion

class DestinationAutocompleteAdapter(
    private val onSuggestionClick: (DestinationSearchSuggestion) -> Unit
) : ListAdapter<DestinationSearchSuggestion, DestinationAutocompleteAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleView: TextView = itemView.findViewById(android.R.id.text1)
        private val descriptionView: TextView = itemView.findViewById(android.R.id.text2)

        fun bind(item: DestinationSearchSuggestion) {
            titleView.text = item.displayText
            descriptionView.text = item.description
            titleView.setTextColor(Color.parseColor("#2F2A27"))
            descriptionView.setTextColor(Color.parseColor("#6C5E53"))
            itemView.setOnClickListener { onSuggestionClick(item) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<DestinationSearchSuggestion>() {
        override fun areItemsTheSame(
            oldItem: DestinationSearchSuggestion,
            newItem: DestinationSearchSuggestion
        ): Boolean {
            return oldItem.displayText == newItem.displayText && oldItem.description == newItem.description
        }

        override fun areContentsTheSame(
            oldItem: DestinationSearchSuggestion,
            newItem: DestinationSearchSuggestion
        ): Boolean {
            return oldItem == newItem
        }
    }
}
