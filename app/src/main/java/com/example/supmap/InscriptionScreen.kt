package com.example.supmap

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun InscriptionScreen(
    onInscription: () -> Unit = {},
    onNavigateToLogin: () -> Unit = {}
) {
    var username by remember { mutableStateOf("") }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Logo SUPMAP agrandi
        Image(
            painter = painterResource(id = R.drawable.logobleu),
            contentDescription = "Logo SupMap",
            modifier = Modifier
                .size(150.dp)
                .padding(bottom = 20.dp)
        )

        // Titre principal
        Text(
            text = "Bienvenue parmi nous !",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF3D2B7A), // Violet foncé
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Champs de saisie
        CustomTextField(value = username, onValueChange = { username = it }, label = "Nom d'utilisateur")
        Spacer(modifier = Modifier.height(12.dp))
        CustomTextField(value = firstName, onValueChange = { firstName = it }, label = "Prénom")
        Spacer(modifier = Modifier.height(12.dp))
        CustomTextField(value = lastName, onValueChange = { lastName = it }, label = "Nom")
        Spacer(modifier = Modifier.height(12.dp))
        CustomTextField(value = email, onValueChange = { email = it }, label = "Email")
        Spacer(modifier = Modifier.height(12.dp))
        CustomTextField(value = password, onValueChange = { password = it }, label = "Mot de passe", isPassword = true)

        Spacer(modifier = Modifier.height(20.dp))

        // Bouton d'inscription
        Button(
            onClick = onInscription,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFF15B4E) // Rouge foncé
            )
        ) {
            Text(
                text = "S'inscrire",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Texte pour aller à la connexion (placé l’un en dessous de l’autre)
        Text(
            text = "Vous possédez déjà un compte ?",
            color = Color.Black,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Connectez-vous ici",
            color = Color(0xFF3D2B7A), // Violet foncé
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { onNavigateToLogin() }
        )
    }
}

// Composant réutilisable pour les champs de saisie
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomTextField(value: String, onValueChange: (String) -> Unit, label: String, isPassword: Boolean = false) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        colors = TextFieldDefaults.textFieldColors(
            containerColor = Color(0xFFF7F7F7),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        )
    )
}
