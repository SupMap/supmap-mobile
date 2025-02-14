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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLogin: () -> Unit = {},
    onNavigateToRegister: () -> Unit = {},
    onForgotPassword: () -> Unit = {}
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White) // Fond blanc
            .padding(horizontal = 32.dp), // Padding ajusté
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center // Recentre verticalement
    ) {
        // Logo SUPMAP (agrandi)
        Image(
            painter = painterResource(id = R.drawable.logobleu),
            contentDescription = "Logo SupMap",
            modifier = Modifier
                .size(150.dp) // Taille augmentée
                .padding(bottom = 20.dp) // Espacement ajusté
        )

        // Titre principal
        Text(
            text = "Bon retour parmi nous !",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF3D2B7A), // Violet foncé
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Champ email
        TextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Entrez votre email") },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.textFieldColors(
                containerColor = Color(0xFFF7F7F7), // Fond gris clair
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Champ mot de passe
        TextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Entrez votre mot de passe") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.textFieldColors(
                containerColor = Color(0xFFF7F7F7),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Bouton "Se connecter"
        Button(
            onClick = onLogin,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFF15B4E) // Rouge foncé
            )
        ) {
            Text(
                text = "Se connecter",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Lien "Mot de passe oublié"
        Text(
            text = "Mot de passe oublié ?",
            color = Color(0xFFF15B4E),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clickable { onForgotPassword() }
                .padding(8.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Ligne de séparation
        Divider(color = Color.Gray.copy(alpha = 0.5f), thickness = 1.dp)

        Spacer(modifier = Modifier.height(20.dp))

        // Texte Inscription (changé pour être l'un en dessous de l'autre)
        Text(
            text = "Vous ne possédez pas de compte ?",
            color = Color.Black,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Inscrivez-vous ici",
            color = Color(0xFF3D2B7A), // Violet foncé
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { onNavigateToRegister() }
        )
    }
}
