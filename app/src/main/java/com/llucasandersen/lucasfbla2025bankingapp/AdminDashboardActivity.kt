package com.llucasandersen.lucasfbla2025bankingapp

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import io.appwrite.Client
import io.appwrite.services.Databases
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class AdminDashboardActivity : AppCompatActivity() {

    private lateinit var appwriteClient: Client
    private lateinit var databases: Databases
    private lateinit var resultTextView: TextView
    private lateinit var userDropdown: Spinner
    private val activityScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_dashboard)

        appwriteClient = Client(applicationContext)
            .setEndpoint("https://append.lucasserver.cloud/v1") // Your Appwrite endpoint
            .setProject("6709882f194aa1acc096") // Your project ID
            .setSelfSigned(true)

        databases = Databases(appwriteClient)
        resultTextView = findViewById(R.id.resultTextView)
        userDropdown = findViewById(R.id.userDropdown)

        loadUserDropdown()

        findViewById<Button>(R.id.viewBalancesButton).setOnClickListener {
            viewBalances()
        }

        findViewById<Button>(R.id.manageBalancesButton).setOnClickListener {
            manageBalances()
        }

        findViewById<Button>(R.id.viewTransactionsButton).setOnClickListener {
            viewTransactions()
        }
    }

    private fun loadUserDropdown() {
        activityScope.launch(Dispatchers.IO) {
            try {
                val balances = databases.listDocuments(
                    "670b28f7d3c283d1d07c", // Database ID
                    "670b29153753e63ec2b7"  // Balances Collection ID
                )
                val usernames = balances.documents.map { it.data["username"].toString() }

                withContext(Dispatchers.Main) {
                    val adapter = ArrayAdapter(
                        this@AdminDashboardActivity,
                        android.R.layout.simple_spinner_dropdown_item,
                        usernames
                    )
                    userDropdown.adapter = adapter
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AdminDashboardActivity, "Failed to load users: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun viewBalances() {
        activityScope.launch(Dispatchers.IO) {
            try {
                val balances = databases.listDocuments(
                    "670b28f7d3c283d1d07c", // Database ID
                    "670b29153753e63ec2b7"  // Balances Collection ID
                )
                val formattedBalances = balances.documents.joinToString("\n\n") { doc ->
                    "Username: ${doc.data["username"]}\nBalance: $${doc.data["balance"]}"
                }
                withContext(Dispatchers.Main) {
                    resultTextView.text = formattedBalances
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    resultTextView.text = "Failed to fetch balances: ${e.message}"
                }
            }
        }
    }

    private fun manageBalances() {
        val inputAmount = EditText(this).apply { hint = "Amount (+/-)" }
        val selectedUsername = userDropdown.selectedItem.toString()

        showDialogWithInput(
            "Manage Balances for $selectedUsername", "", inputAmount
        ) {
            val amount = inputAmount.text.toString().toDoubleOrNull()
            if (amount != null) {
                updateBalance(selectedUsername, amount)
            } else {
                Toast.makeText(this, "Invalid amount.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateBalance(username: String, amount: Double) {
        activityScope.launch(Dispatchers.IO) {
            try {
                // Fetch all documents from the balances collection
                val documents = databases.listDocuments(
                    databaseId = "670b28f7d3c283d1d07c",
                    collectionId = "670b29153753e63ec2b7"
                )

                // Find the document ID for the given username
                val matchingDocument = documents.documents.find {
                    it.data["username"] == username
                }

                if (matchingDocument != null) {
                    val documentId = matchingDocument.id
                    val currentBalance = matchingDocument.data["balance"].toString().toDouble()
                    val newBalance = currentBalance + amount

                    // Update the balance in the database
                    databases.updateDocument(
                        databaseId = "670b28f7d3c283d1d07c",
                        collectionId = "670b29153753e63ec2b7",
                        documentId = documentId,
                        data = mapOf("balance" to newBalance)
                    )

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@AdminDashboardActivity, "Balance updated successfully for $username.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@AdminDashboardActivity, "User not found in balances collection.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    resultTextView.text = "Failed to update balance: ${e.message}"
                }
            }
        }
    }

    private fun setupAdminActions() {
        findViewById<Button>(R.id.manageBalancesButton).setOnClickListener {
            val selectedUsername = userDropdown.selectedItem.toString()
            val inputAmount = EditText(this).apply { hint = "Enter amount (+/-)" }

            showDialogWithInput(
                "Manage Balances for $selectedUsername", "",
                inputAmount
            ) {
                val amount = inputAmount.text.toString().toDoubleOrNull()
                if (amount != null) {
                    updateBalance(selectedUsername, amount)
                } else {
                    Toast.makeText(this, "Invalid amount.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }



    private fun viewTransactions() {
        activityScope.launch(Dispatchers.IO) {
            try {
                val transactions = databases.listDocuments(
                    "670b28f7d3c283d1d07c", // Database ID
                    "670b292655fd525a2783"  // Transactions Collection ID
                )
                withContext(Dispatchers.Main) {
                    val formattedTransactions = transactions.documents.joinToString("\n") { doc ->
                        val username = doc.data["username"].toString()
                        val amount = doc.data["moneyamount"].toString()
                        val type = doc.data["expensetype"].toString()
                        val time = formatTransactionTime(doc.data["time"].toString())

                        "Username: $username\nAmount: $$amount\nTime: $time\nType: $type\n\n"
                    }
                    resultTextView.text = formattedTransactions
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    resultTextView.text = "Failed to fetch transactions: ${e.message}"
                }
            }
        }
    }

    private fun formatTransactionTime(timestamp: String): String {
        return try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault())
            val transactionDate = dateFormat.parse(timestamp)!!
            val now = Date()

            val diffInMillis = now.time - transactionDate.time
            val days = TimeUnit.MILLISECONDS.toDays(diffInMillis)
            val hours = TimeUnit.MILLISECONDS.toHours(diffInMillis) % 24
            val minutes = TimeUnit.MILLISECONDS.toMinutes(diffInMillis) % 60

            when {
                days > 0 -> "$days day(s) ago"
                hours > 0 -> "$hours hour(s) ago"
                minutes > 0 -> "$minutes minute(s) ago"
                else -> "Just now"
            }
        } catch (e: Exception) {
            "Invalid date"
        }
    }

    private fun getTimeDifference(date: Date?): String {
        date ?: return "Unknown"
        val diff = System.currentTimeMillis() - date.time
        return when {
            diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
            diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)} minutes ago"
            diff < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diff)} hours ago"
            else -> "${TimeUnit.MILLISECONDS.toDays(diff)} days ago"
        }
    }

    private fun showDialogWithInput(
        title: String,
        user: String,
        inputAmount: EditText,
        onSubmit: () -> Unit
    ) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(inputAmount)
        }

        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("$title for $user")
            .setView(layout)
            .setPositiveButton("Submit") { _, _ -> onSubmit() }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .create()
        dialog.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }
}
