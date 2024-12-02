package com.llucasandersen.lucasfbla2025bankingapp

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.leandroborgesferreira.loadingbutton.customViews.CircularProgressButton
import io.appwrite.Client
import io.appwrite.Query
import io.appwrite.services.Account
import io.appwrite.services.Databases
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

                // Fetch user and balances
                val user = account.get()
                val databases = Databases(client)

                // Fetch "isnew" status
                val response = databases.listDocuments(
                    databaseId = "670b28f7d3c283d1d07c",
                    collectionId = "670b29153753e63ec2b7",
                    queries = listOf(Query.equal("username", email))
                )

                val document = response.documents.firstOrNull()
                val isNew = document?.data?.get("isnew") as? Boolean ?: true

                // Update "isnew" to false if user is new
                if (isNew) {
                    document?.let {
                        databases.updateDocument(
                            databaseId = "670b28f7d3c283d1d07c",
                            collectionId = "670b29153753e63ec2b7",
                            documentId = it.id,
                            data = mapOf("isnew" to false)
                        )
                    }
                }

                withContext(Dispatchers.Main) {
                    loginButton.revertAnimation() // Revert animation on success
                    errorTextView.visibility = TextView.GONE // Hide the error message if successful

                    if (user.labels.contains("admin")) {
                        // Navigate to AdminDashboardActivity for admins
                        val intent = Intent(this@LoginActivity, AdminDashboardActivity::class.java)
                        intent.putExtra("user_email", email)
                        startActivity(intent)
                    } else if (isNew) {
                        // Navigate to HelpActivity for new users
                        val intent = Intent(this@LoginActivity, HelpActivity::class.java)
                        intent.putExtra("user_email", email)
                        startActivity(intent)
                        finish() // Ensure LoginActivity is closed
                    } else {
                        // Navigate to UserDashboardActivity for existing users
                        val intent = Intent(this@LoginActivity, UserDashboardActivity::class.java)
                        intent.putExtra("user_email", email)
                        startActivity(intent)
                        finish() // Ensure LoginActivity is closed
                    }
                }
            } catch (e: Exception) {
                // Handle login error
                withContext(Dispatchers.Main) {
                    loginButton.revertAnimation() // Revert animation on failure
                    showErrorMessage("Invalid credentials or an error occurred. Please try again.")
                }
            }
        }
    }




    private fun showErrorMessage(message: String) {
        errorTextView.text = message
        errorTextView.visibility = TextView.VISIBLE
    }
}
