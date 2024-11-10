package com.llucasandersen.lucasfbla2025bankingapp

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import io.appwrite.Client
import io.appwrite.exceptions.AppwriteException
import io.appwrite.services.Account
import kotlinx.coroutines.*

class SettingsActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var usernameTextView: TextView
    private lateinit var errorTextView: TextView
    private lateinit var appwriteClient: Client
    private lateinit var userEmail: String
    private val activityScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        drawerLayout = findViewById(R.id.drawer_layout)
        usernameTextView = findViewById(R.id.usernameTextView)
        errorTextView = findViewById(R.id.errorTextView) // TextView for displaying errors
        val navView: NavigationView = findViewById(R.id.nav_view)

        // Initialize Appwrite Client
        appwriteClient = Client(applicationContext)
            .setEndpoint("https://append.lucasserver.cloud/v1")
            .setProject("6709882f194aa1acc096")
            .setSelfSigned(true)

        // Get user email from intent
        userEmail = intent.getStringExtra("username") ?: "Unknown User"
        usernameTextView.text = userEmail

        // Setup Navigation Drawer
        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> navigateToHome()
                R.id.nav_finances -> navigateToFinances()
                R.id.nav_settings -> drawerLayout.closeDrawer(GravityCompat.START)
            }
            true
        }

        findViewById<TextView>(R.id.changePassword)?.setOnClickListener {
            openChangePasswordDialog()
        }

        findViewById<TextView>(R.id.logout)?.setOnClickListener {
            logout()
        }
    }

    private fun navigateToHome() {
        val intent = Intent(this, UserDashboardActivity::class.java)
        intent.putExtra("user_email", userEmail)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
    }

    private fun navigateToFinances() {
        val intent = Intent(this, FinancesActivity::class.java)
        intent.putExtra("username", userEmail)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
    }

    private fun openChangePasswordDialog() {
        val dialog = ChangePasswordDialog()
        dialog.setListener(object : ChangePasswordDialog.ChangePasswordListener {
            override fun onPasswordChangeSuccess() {
                Toast.makeText(this@SettingsActivity, "Password changed successfully!", Toast.LENGTH_LONG).show()
            }

            override fun onPasswordChangeFailed(error: String) {
                displayError(error)
            }
        })
        dialog.show(supportFragmentManager, "ChangePasswordDialog")
    }

    private fun logout() {
        val accountService = Account(appwriteClient)
        activityScope.launch(Dispatchers.IO) {
            try {
                accountService.deleteSession("current")
                withContext(Dispatchers.Main) {
                    val intent = Intent(this@SettingsActivity, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                }
            } catch (e: AppwriteException) {
                withContext(Dispatchers.Main) {
                    displayError("Logout failed: ${e.message}")
                }
            }
        }
    }

    private fun displayError(message: String) {
        errorTextView.apply {
            text = message
            setTextColor(Color.RED)
            visibility = View.VISIBLE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.coroutineContext[Job]?.cancel()
    }
}
