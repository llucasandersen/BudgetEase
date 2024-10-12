package com.llucasandersen.lucasfbla2025bankingapp

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.leandroborgesferreira.loadingbutton.customViews.CircularProgressButton
import io.appwrite.Client
import io.appwrite.services.Account
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: CircularProgressButton
    private lateinit var client: Client
    private lateinit var account: Account

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        emailEditText = findViewById(R.id.emailEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        loginButton = findViewById(R.id.loginButton)

        // Initialize Appwrite Client
        client = Client(this)
            .setEndpoint("https://append.lucasserver.cloud/v1") // Your Appwrite server endpoint
            .setProject("6709882f194aa1acc096") // Your Appwrite project ID
            .setSelfSigned(true) // Optional, for self-signed certs

        account = Account(client)

        // Login button click listener
        loginButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
            } else {
                loginUser(email, password)
            }
        }
    }

    private fun loginUser(email: String, password: String) {
        loginButton.startAnimation() // Start button animation
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Create a session with the provided email and password
                val session = account.createEmailSession(
                    email = email,
                    password = password
                )

                // If login is successful, proceed to get the user data
                val user = account.get()

                withContext(Dispatchers.Main) {
                    loginButton.revertAnimation() // Revert animation on success
                    if (user.labels.contains("admin")) {
                        navigateToAdminDashboard()
                    } else if (user.labels.contains("user")) {
                        navigateToUserDashboard()
                    } else {
                        Toast.makeText(this@LoginActivity, "Unknown user role", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                // Handle login error
                withContext(Dispatchers.Main) {
                    loginButton.revertAnimation() // Revert animation on failure
                    Toast.makeText(this@LoginActivity, "Login failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun navigateToAdminDashboard() {
        val intent = Intent(this, AdminDashboardActivity::class.java)
        startActivity(intent)
    }

    private fun navigateToUserDashboard() {
        val intent = Intent(this, UserDashboardActivity::class.java)
        startActivity(intent)
    }
}
