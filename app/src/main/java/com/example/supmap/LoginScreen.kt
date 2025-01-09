package com.example.supmap

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLogin: () -> Unit = {},
    onNavigateToRegister: () -> Unit = {},
    onGoogleLogin: () -> Unit = {}
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.logobleu),
            contentDescription = "Logo de l'application",
            modifier = Modifier.size(200.dp).padding(bottom = 60.dp)
        )

        Text(
            text = "Bon retour parmi nous !",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        TextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("E-mail ou nom d'utilisateur") },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.textFieldColors(
                containerColor = Color.LightGray.copy(alpha = 0.3f), // Light gray background
                focusedIndicatorColor = Color.Transparent, // Remove border when focused
                unfocusedIndicatorColor = Color.Transparent, // Remove border when unfocused
                disabledIndicatorColor = Color.Transparent // Remove border when disabled
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        TextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Mot de passe") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            colors = TextFieldDefaults.textFieldColors(
                containerColor = Color.LightGray.copy(alpha = 0.3f), // Light gray background
                focusedIndicatorColor = Color.Transparent, // Remove border when focused
                unfocusedIndicatorColor = Color.Transparent, // Remove border when unfocused
                disabledIndicatorColor = Color.Transparent // Remove border when disabled
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onLogin,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text(text = "Se connecter", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Divider with "OU"
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Divider(modifier = Modifier.weight(1f), color = Color.Gray, thickness = 1.dp)
            Text(
                text = "OU",
                modifier = Modifier.padding(horizontal = 8.dp),
                color = Color.Gray,
                fontWeight = FontWeight.Bold
            )
            Divider(modifier = Modifier.weight(1f), color = Color.Gray, thickness = 1.dp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Google Login Button
        Button(
            onClick = onGoogleLogin,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFF1F3F4),
                contentColor = Color.Black
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logogoogle),
                    contentDescription = "Logo Google",
                    modifier = Modifier.size(32.dp)
                )
                Text(text = "Se connecter avec Google", fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Un problème ?",
                color = Color(0xFFF15B4E),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { /* Handle Help */ }.padding(16.dp)
            )
            Text(
                text = "Inscription",
                color = Color(0xFFF15B4E),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onNavigateToRegister() }.padding(16.dp)
            )
        }
    }
}