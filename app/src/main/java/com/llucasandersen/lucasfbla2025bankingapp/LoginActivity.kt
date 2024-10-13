package com.llucasandersen.lucasfbla2025bankingapp

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
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
    private lateinit var errorTextView: TextView
    private lateinit var client: Client
    private lateinit var account: Account

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        emailEditText = findViewById(R.id.emailEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        loginButton = findViewById(R.id.loginButton)
        errorTextView = findViewById(R.id.errorTextView)

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
                showErrorMessage("Please enter email and password.")
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
                    errorTextView.visibility = TextView.GONE // Hide the error message if successful

                    // Check if the user has an "admin" label
                    if (user.labels.contains("admin")) {
                        // If user is an admin, navigate to AdminDashboardActivity
                        val intent = Intent(this@LoginActivity, AdminDashboardActivity::class.java)
                        intent.putExtra("user_email", email)
                        startActivity(intent)
                    } else {
                        // Otherwise, navigate to UserDashboardActivity
                        val intent = Intent(this@LoginActivity, UserDashboardActivity::class.java)
                        intent.putExtra("user_email", email)
                        startActivity(intent)
                    }
                }
            } catch (e: Exception) {
                // Handle login error
                withContext(Dispatchers.Main) {
                    loginButton.revertAnimation() // Revert animation on failure
                    showErrorMessage("Invalid credentials. Please check your email and password.")
                }
            }
        }
    }

    private fun showErrorMessage(message: String) {
        errorTextView.text = message
        errorTextView.visibility = TextView.VISIBLE
    }
}
