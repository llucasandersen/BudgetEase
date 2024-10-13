package com.llucasandersen.lucasfbla2025bankingapp

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import io.appwrite.Client
import io.appwrite.Query
import io.appwrite.exceptions.AppwriteException
import io.appwrite.models.Document
import io.appwrite.services.Databases
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class UserDashboardActivity : AppCompatActivity() {

    private lateinit var client: Client
    private lateinit var databases: Databases
    private lateinit var searchBar: EditText
    private lateinit var filterButton: ImageView
    private val transactions = mutableListOf<Document<Map<String, Any>>>()

    private var totalBalance = 0.0
    private var monthSpending = 0.0
    private var weekSpending = 0.0

    // Handler for periodic updates
    private val updateHandler = Handler(Looper.getMainLooper())
    private val updateInterval = 30000 // 30 seconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_dashboard)

        // Initialize Appwrite Client and Database
        client = Client(applicationContext)
            .setEndpoint("https://append.lucasserver.cloud/v1")
            .setProject("6709882f194aa1acc096")
            .setSelfSigned(true)

        databases = Databases(client)

        searchBar = findViewById(R.id.searchBar)
        filterButton = findViewById(R.id.filterButton)

        // Search functionality
        searchBar.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterTransactions(s.toString())
            }
        })

        // Filter button functionality
        filterButton.setOnClickListener {
            showFilterDialog()
        }

        // Load balance and transactions when activity starts
        CoroutineScope(Dispatchers.IO).launch {
            loadUserBalance()
            loadUserTransactions()
        }

        // Start periodic updates
        startPeriodicUpdates()
    }

    // Load user balance from Balances collection
    private suspend fun loadUserBalance() {
        try {
            val userEmail = intent.getStringExtra("user_email") ?: ""
            val balanceResponse = databases.listDocuments(
                databaseId = "670b28f7d3c283d1d07c",
                collectionId = "670b29153753e63ec2b7",
                queries = listOf(Query.equal("username", userEmail))
            )
            if (balanceResponse.documents.isNotEmpty()) {
                val balanceData = balanceResponse.documents[0].data["balance"] as? Double ?: 0.0
                totalBalance = balanceData
                withContext(Dispatchers.Main) {
                    val formattedBalance = String.format("%,.2f", totalBalance)
                    findViewById<TextView>(R.id.balanceAmountTextView).text = "$$formattedBalance"
                }

            }
        } catch (e: AppwriteException) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(this@UserDashboardActivity, "Failed to load balance", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Load user transactions from Transactions collection
    private suspend fun loadUserTransactions() {
        try {
            val userEmail = intent.getStringExtra("user_email") ?: ""
            val transactionResponse = databases.listDocuments(
                databaseId = "670b28f7d3c283d1d07c",
                collectionId = "670b292655fd525a2783",
                queries = listOf(Query.equal("username", userEmail))
            )
            transactions.clear()
            transactions.addAll(transactionResponse.documents)

            if (transactions.isNotEmpty()) {
                processTransactionsBeforeDisplay()
                withContext(Dispatchers.Main) {
                    displayTransactions(transactions)
                }
            } else {
                withContext(Dispatchers.Main) {
                    findViewById<TextView>(R.id.noTransactionsTextView).visibility = TextView.VISIBLE
                }
            }

        } catch (e: AppwriteException) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(this@UserDashboardActivity, "Failed to load transactions", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Process transactions, adjust balance, and calculate spending
    private suspend fun processTransactionsBeforeDisplay() {
        val calendar = Calendar.getInstance()
        // Subtract one month
        calendar.add(Calendar.MONTH, -1)
        val oneMonthAgo = calendar.timeInMillis

        // Reset calendar to current time and subtract one week
        calendar.timeInMillis = System.currentTimeMillis()
        calendar.add(Calendar.DAY_OF_YEAR, -7)
        val oneWeekAgo = calendar.timeInMillis

        weekSpending = 0.0
        monthSpending = 0.0

        for (transaction in transactions) {
            val time = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.getDefault())
                .parse(transaction.data["time"] as? String ?: "")?.time ?: continue

            val amount = transaction.data["moneyamount"] as? Double ?: 0.0
            val expensetype = transaction.data["expensetype"] as? String ?: "expense"
            val isRedeemed = transaction.data["isredeemed"] as? Boolean ?: false

            // Adjust balance for unredeemed transactions
            if (!isRedeemed) {
                adjustBalance(expensetype, amount)
                markTransactionAsRedeemed(transaction.id)
            }

            // Calculate monthly and weekly spending for all transactions
            if (time >= oneMonthAgo) {
                monthSpending += if (expensetype == "expense") -amount else amount
            }
            if (time >= oneWeekAgo) {
                weekSpending += if (expensetype == "expense") -amount else amount
            }
        }

        // Update balance and spending in UI
        withContext(Dispatchers.Main) {
            findViewById<TextView>(R.id.monthAmountTextView).text =
                "${if (monthSpending < 0) "-" else "+"}$${String.format("%,.2f", Math.abs(monthSpending))}"
            findViewById<TextView>(R.id.weekAmountTextView).text =
                "${if (weekSpending < 0) "-" else "+"}$${String.format("%,.2f", Math.abs(weekSpending))}"
        }
    }

    // Adjust user balance
    private suspend fun adjustBalance(expensetype: String, amount: Double) {
        totalBalance += if (expensetype == "income") amount else -amount
        updateBalanceInCollection(intent.getStringExtra("user_email") ?: "")
    }

    // Update the balance in the Balances collection
    private suspend fun updateBalanceInCollection(userEmail: String) {
        try {
            val balanceResponse = databases.listDocuments(
                databaseId = "670b28f7d3c283d1d07c",
                collectionId = "670b29153753e63ec2b7",
                queries = listOf(Query.equal("username", userEmail))
            )

            if (balanceResponse.documents.isNotEmpty()) {
                val documentId = balanceResponse.documents[0].id
                databases.updateDocument(
                    databaseId = "670b28f7d3c283d1d07c",
                    collectionId = "670b29153753e63ec2b7",
                    documentId = documentId,
                    data = mapOf("balance" to totalBalance)
                )
            }
        } catch (e: AppwriteException) {
            e.printStackTrace()
        }
    }

    // Mark transaction as redeemed
    private suspend fun markTransactionAsRedeemed(transactionId: String) {
        databases.updateDocument(
            databaseId = "670b28f7d3c283d1d07c",
            collectionId = "670b292655fd525a2783",
            documentId = transactionId,
            data = mapOf("isredeemed" to true)
        )
    }

    // Display transactions in the UI
    // Display transactions in the UI
    private suspend fun displayTransactions(transactions: List<Document<Map<String, Any>>>) {
        withContext(Dispatchers.Main) {
            val transactionListLayout = findViewById<LinearLayout>(R.id.transactionListLayout)
            transactionListLayout.removeAllViews()

            for (transaction in transactions) {
                val transactionAmount = transaction.data["moneyamount"] as? Double ?: 0.0
                val transactionType = transaction.data["transname"] as? String ?: "Unknown"
                val expensetype = transaction.data["expensetype"] as? String ?: "expense"
                val time = transaction.data["time"] as? String ?: ""
                val type = transaction.data["type"] as? String ?: "unknown"

                // Add transaction details to UI
                val transactionView = layoutInflater.inflate(R.layout.transaction_card, null)
                val transactionTypeTextView = transactionView.findViewById<TextView>(R.id.transactionTypeTextView)
                val transactionAmountTextView = transactionView.findViewById<TextView>(R.id.transactionAmountTextView)
                val transactionDateTextView = transactionView.findViewById<TextView>(R.id.transactionDateTextView)
                val transactionIcon = transactionView.findViewById<ImageView>(R.id.transactionIcon)

                transactionTypeTextView.text = transactionType

                // Fix for displaying expenses correctly
                val formattedAmount = if (expensetype == "income") {
                    "+$${String.format("%,.2f", transactionAmount)}"
                } else {
                    "-$${String.format("%,.2f", Math.abs(transactionAmount))}"
                }

                transactionAmountTextView.text = formattedAmount
                transactionDateTextView.text = calculateTimeAgo(time)

                // Set the appropriate icon based on the transaction type
                when (type) {
                    "credit" -> transactionIcon.setImageResource(R.drawable.ic_credit)
                    "cash" -> transactionIcon.setImageResource(R.drawable.ic_cash)
                    "check" -> transactionIcon.setImageResource(R.drawable.ic_check)
                    "card" -> transactionIcon.setImageResource(R.drawable.ic_card)
                    "paypal" -> transactionIcon.setImageResource(R.drawable.ic_paypal)
                    else -> transactionIcon.setImageResource(R.drawable.ic_cash) // Fallback icon
                }

                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(16, 16, 16, 16) // Add spacing
                transactionView.layoutParams = params

                transactionListLayout.addView(transactionView)
            }
        }
    }


    // Calculate how long ago the transaction happened
    private fun calculateTimeAgo(time: String): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.getDefault())
        val date = sdf.parse(time) ?: return "Unknown time"
        val now = System.currentTimeMillis()
        val diff = now - date.time

        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days > 0 -> "$days days ago"
            hours > 0 -> "$hours hours ago"
            minutes > 0 -> "$minutes minutes ago"
            else -> "$seconds seconds ago"
        }
    }

    // Filter transactions
    private fun filterTransactions(query: String) {
        val filteredList = transactions.filter { transaction ->
            val company = transaction.data["transname"].toString().lowercase()
            val date = calculateTimeAgo(transaction.data["time"].toString())
            company.contains(query.lowercase()) || date.contains(query.lowercase())
        }
        CoroutineScope(Dispatchers.IO).launch {
            displayTransactions(filteredList)
        }
    }

    // Show filter dialog
    private fun showFilterDialog() {
        val builder = AlertDialog.Builder(this)
        val inflater = layoutInflater
        val dialogLayout = inflater.inflate(R.layout.filter_dialog, null)

        val dateOption = dialogLayout.findViewById<RadioButton>(R.id.radioDate)
        val costOption = dialogLayout.findViewById<RadioButton>(R.id.radioCost)
        val companyOption = dialogLayout.findViewById<RadioButton>(R.id.radioCompany)
        val newestToOldestOption = dialogLayout.findViewById<RadioButton>(R.id.radioNewestToOldest)
        val alphabeticalAscOption = dialogLayout.findViewById<RadioButton>(R.id.radioAlphabeticalAsc)
        val alphabeticalDescOption = dialogLayout.findViewById<RadioButton>(R.id.radioAlphabeticalDesc)

        builder.setView(dialogLayout)
        builder.setTitle("Filter Transactions")
        builder.setPositiveButton("Apply") { _, _ ->
            when {
                dateOption.isChecked -> filterByDate()
                costOption.isChecked -> filterByCost()
                companyOption.isChecked -> filterByCompany()
                newestToOldestOption.isChecked -> sortByNewestToOldest()
                alphabeticalAscOption.isChecked -> sortAlphabeticallyAsc()
                alphabeticalDescOption.isChecked -> sortAlphabeticallyDesc()
            }
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    // Filter by date
    private fun filterByDate() {
        transactions.sortBy { it.data["time"] as? String }
        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.Main) {
                displayTransactions(transactions)
            }
        }
    }

    // Filter by cost
    private fun filterByCost() {
        transactions.sortBy { it.data["moneyamount"] as? Double }
        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.Main) {
                displayTransactions(transactions)
            }
        }
    }

    // Filter by company
    private fun filterByCompany() {
        transactions.sortBy { it.data["transname"] as? String }
        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.Main) {
                displayTransactions(transactions)
            }
        }
    }

    // Sort transactions by newest to oldest
    private fun sortByNewestToOldest() {
        transactions.sortByDescending { it.data["time"] as? String }
        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.Main) {
                displayTransactions(transactions)
            }
        }
    }

    // Sort transactions alphabetically (ascending)
    private fun sortAlphabeticallyAsc() {
        transactions.sortBy { it.data["transname"] as? String }
        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.Main) {
                displayTransactions(transactions)
            }
        }
    }

    // Sort transactions alphabetically (descending)
    private fun sortAlphabeticallyDesc() {
        transactions.sortByDescending { it.data["transname"] as? String }
        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.Main) {
                displayTransactions(transactions)
            }
        }
    }

    // Periodically fetch updates from the database
    private fun startPeriodicUpdates() {
        updateHandler.postDelayed(object : Runnable {
            override fun run() {
                CoroutineScope(Dispatchers.IO).launch {
                    loadUserBalance()
                    loadUserTransactions()
                    processTransactionsBeforeDisplay()
                    loadUserBalance()
                }
                updateHandler.postDelayed(this, updateInterval.toLong())
            }
        }, updateInterval.toLong())
    }

    // Stop periodic updates when the activity is destroyed
    override fun onDestroy() {
        super.onDestroy()
        updateHandler.removeCallbacksAndMessages(null)
    }
}
