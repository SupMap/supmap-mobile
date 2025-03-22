package com.example.supmap

import android.content.Context
import android.content.Intent
import android.util.Log
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.supmap.api.loginUser
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLogin: () -> Unit = {},
    onNavigateToRegister: () -> Unit = {}
) {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.logobleu),
            contentDescription = "Logo SupMap",
            modifier = Modifier.size(150.dp).padding(bottom = 20.dp)
        )

        Text(
            text = "Bon retour parmi nous !",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF3D2B7A),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        TextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Entrez votre email") },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.textFieldColors(
                containerColor = Color(0xFFF7F7F7),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        TextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Entrez votre mot de passe") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            colors = TextFieldDefaults.textFieldColors(
                containerColor = Color(0xFFF7F7F7),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                coroutineScope.launch {
                    isLoading = true
                    loginUser(
                        context,
                        email,
                        password,
                        onSuccess = { token ->
                            sharedPreferences.edit().putString("auth_token", token).apply()
                            isLoading = false
                            onLogin()
                            // Après avoir stocké le token
                            sharedPreferences.edit().putString("auth_token", token).apply()
                        },
                        onFailure = {
                            isLoading = false
                        }
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF15B4E)),
            enabled = !isLoading
        ) {
            Text(
                text = if (isLoading) "Connexion..." else "Se connecter",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }



        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Mot de passe oublié ?",
            color = Color(0xFF3D2B7A),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { /* Ajouter action */ }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Vous ne possédez pas de compte ?",
            color = Color.Black,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Inscrivez-vous ici",
            color = Color(0xFF3D2B7A),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { onNavigateToRegister() }
        )
    }
}
