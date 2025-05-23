package com.example.supmap.ui.map

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import androidx.lifecycle.LifecycleCoroutineScope
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class PlaceAutocomplete(val placeId: String, val fullText: CharSequence)

class PlaceAutocompleteManager(
    private val context: Context,
    private val placesClient: PlacesClient,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val onPlaceSelected: (String) -> Unit
) {
    private var searchJob: Job? = null
    private val searchDebounceTime = 300L
    private lateinit var placesAdapter: PlacesAdapter

    fun setupAutoComplete(destinationField: AutoCompleteTextView) {
        placesAdapter = PlacesAdapter(context)
        destinationField.setAdapter(placesAdapter)

        destinationField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!destinationField.hasFocus() || s.isNullOrEmpty()) return

                searchJob?.cancel()

                searchJob = lifecycleScope.launch {
                    delay(searchDebounceTime)
                    performPlaceSearch(s.toString(), placesAdapter, destinationField)
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        destinationField.setOnItemClickListener { parent, _, position, _ ->
            val place = parent.getItemAtPosition(position) as PlaceAutocomplete
            val placeText = place.fullText.toString()
            searchJob?.cancel()
            destinationField.safelySetPlace(placesAdapter, place) { it.fullText.toString() }
            onPlaceSelected(placeText)
        }
    }

    private fun performPlaceSearch(
        query: String,
        adapter: PlacesAdapter,
        destinationField: AutoCompleteTextView
    ) {
        val request = FindAutocompletePredictionsRequest.builder()
            .setQuery(query)
            .setCountries("FR")
            .build()

        placesClient.findAutocompletePredictions(request)
            .addOnSuccessListener { response ->
                adapter.setPredictions(response.autocompletePredictions)
                forceSuggestionDropdown(destinationField)
            }
    }

    private fun forceSuggestionDropdown(destinationField: AutoCompleteTextView) {
        destinationField.post {
            val adapter = destinationField.adapter
            if (adapter != null && adapter.count > 0) {
                destinationField.showDropDown()
            }
        }
    }

    inner class PlacesAdapter(context: Context) : ArrayAdapter<PlaceAutocomplete>(
        context, android.R.layout.simple_dropdown_item_1line
    ) {
        private val placesList = ArrayList<PlaceAutocomplete>()

        fun setPredictions(predictions: List<AutocompletePrediction>) {
            placesList.clear()
            for (prediction in predictions) {
                placesList.add(
                    PlaceAutocomplete(
                        prediction.placeId,
                        prediction.getFullText(null)
                    )
                )
            }
            notifyDataSetChanged()
        }

        override fun getCount(): Int = placesList.size

        override fun getItem(position: Int): PlaceAutocomplete = placesList[position]

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context)
                .inflate(android.R.layout.simple_dropdown_item_1line, parent, false)

            val textView = view.findViewById<TextView>(android.R.id.text1)
            textView.text = placesList[position].fullText

            return view
        }
    }

    fun <T> AutoCompleteTextView.safelySetPlace(
        adapter: ArrayAdapter<T>,
        item: T,
        toDisplayText: (T) -> String
    ) {
        this.setAdapter(null)

        val text = toDisplayText(item)
        this.setText(text)
        this.setSelection(text.length)

        this.dismissDropDown()
        this.clearFocus()

        val imm = this.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(this.windowToken, 0)

        this.postDelayed({
            this.setAdapter(adapter)
            this.dismissDropDown()
        }, 420)
    }
}