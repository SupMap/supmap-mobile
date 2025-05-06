package com.example.supmap.ui.map

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.example.supmap.R
import com.example.supmap.data.api.IncidentDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class IncidentRatingDialogFragment : DialogFragment() {

    private var incident: IncidentDto? = null
    private var onRatingCallback: ((Long, Boolean) -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // Créer un Dialog transparent sans titre
        return Dialog(requireContext()).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setCanceledOnTouchOutside(false)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.incident_rating_popup, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Configurer le texte de l'incident
        val incidentTypeText = view.findViewById<TextView>(R.id.incidentTypeText)
        incidentTypeText.text = incident?.typeName ?: "Incident"

        // Configurer les boutons
        view.findViewById<View>(R.id.thumbsUpButton).setOnClickListener {
            incident?.id?.let { id -> onRatingCallback?.invoke(id, true) }
            dismiss()
        }

        view.findViewById<View>(R.id.thumbsDownButton).setOnClickListener {
            incident?.id?.let { id -> onRatingCallback?.invoke(id, false) }
            dismiss()
        }

        // Fermeture automatique après 10 secondes
        lifecycleScope.launch {
            delay(10000)
            if (isAdded) dismiss()
        }
    }

    override fun onStart() {
        super.onStart()

        dialog?.window?.apply {
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            // Définir la largeur à MATCH_PARENT
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            // Calculer les marges depuis les valeurs de votre XML
            val params = attributes

            // Marges horizontales de 16dp (comme dans votre XML)
            val horizontalMargin = (16 * resources.displayMetrics.density).toInt()
            params.width = resources.displayMetrics.widthPixels - (horizontalMargin * 2)

            // Marge supérieure de 130dp (comme dans votre XML)
            val topMargin = (130 * resources.displayMetrics.density).toInt()

            // Positionner en haut avec la marge
            setGravity(Gravity.TOP)
            params.y = topMargin

            attributes = params
        }
    }

    companion object {
        fun newInstance(
            incident: IncidentDto,
            callback: (Long, Boolean) -> Unit
        ): IncidentRatingDialogFragment {
            return IncidentRatingDialogFragment().apply {
                this.incident = incident
                this.onRatingCallback = callback
            }
        }
    }
}