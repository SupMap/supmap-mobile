package com.example.supmap.ui.auth

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.supmap.R

@Composable
fun InscriptionScreen(
    onInscriptionSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: RegisterViewModel = viewModel(factory = RegisterViewModel.Factory(LocalContext.current))
) {
    val context = LocalContext.current
    val registerState = viewModel.registerState.collectAsState().value

    var username by remember { mutableStateOf("") }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(registerState) {
        when (registerState) {
            is RegisterState.Loading -> {
                isLoading = true
            }

            is RegisterState.Success -> {
                isLoading = false
                Toast.makeText(context, "Inscription réussie !", Toast.LENGTH_SHORT).show()
                onInscriptionSuccess()
                viewModel.resetState()
            }

            is RegisterState.Error -> {
                isLoading = false
                Toast.makeText(context, registerState.message, Toast.LENGTH_LONG).show()
            }

            else -> {
                isLoading = false
            }
        }
    }

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
            modifier = Modifier
                .size(150.dp)
                .padding(bottom = 20.dp)
        )

        Text(
            text = "Bienvenue parmi nous !",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF3D2B7A),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        CustomTextField(
            value = username,
            onValueChange = { username = it },
            label = "Nom d'utilisateur"
        )
        Spacer(modifier = Modifier.height(12.dp))
        CustomTextField(value = firstName, onValueChange = { firstName = it }, label = "Prénom")
        Spacer(modifier = Modifier.height(12.dp))
        CustomTextField(value = lastName, onValueChange = { lastName = it }, label = "Nom")
        Spacer(modifier = Modifier.height(12.dp))
        CustomTextField(value = email, onValueChange = { email = it }, label = "Email")
        Spacer(modifier = Modifier.height(12.dp))
        CustomTextField(
            value = password,
            onValueChange = { password = it },
            label = "Mot de passe",
            isPassword = true
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                viewModel.register(username, firstName, lastName, email, password)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF15B4E)),
            enabled = !isLoading && username.isNotEmpty() && firstName.isNotEmpty() &&
                    lastName.isNotEmpty() && email.isNotEmpty() && password.isNotEmpty()
        ) {
            Text(
                text = if (isLoading) "Chargement..." else "S'inscrire",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Vous possédez déjà un compte ?",
            color = Color.Black,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Connectez-vous ici",
            color = Color(0xFF3D2B7A),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { onNavigateToLogin() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isPassword: Boolean = false
) {
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