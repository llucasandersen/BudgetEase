package com.llucasandersen.lucasfbla2025bankingapp

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
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
import kotlin.math.absoluteValue

class UserDashboardActivity : AppCompatActivity() {

    private lateinit var client: Client
    private lateinit var databases: Databases
    private lateinit var searchBar: EditText
    private lateinit var filterButton: ImageView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private val transactions = mutableListOf<Document<Map<String, Any>>>()

    private var totalBalance = 0.0
    private var monthSpending = 0.0
    private var weekSpending = 0.0


    private val updateHandler = Handler(Looper.getMainLooper())
    private val updateInterval = 30000 // 30 seconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_dashboard)

        // Initialize UI elements
        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.nav_view)

        // Set up navigation item selection
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {
                    drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }
                R.id.nav_finances -> {
                    val userEmail = intent.getStringExtra("user_email") ?: ""
                    val intent = Intent(this, FinancesActivity::class.java)
                    intent.putExtra("username", userEmail)  // Pass the username as "username"
                    startActivity(intent)
                    drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }
                R.id.nav_settings -> {
                    val userEmail = intent.getStringExtra("user_email") ?: ""
                    val intent = Intent(this, SettingsActivity::class.java)
                    intent.putExtra("username", userEmail)
                    startActivity(intent)
                    drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }
                R.id.nav_help -> {
                    val userEmail = intent.getStringExtra("user_email") ?: ""
                    val intent = Intent(this, HelpActivity::class.java)
                    intent.putExtra("user_email", userEmail)
                    startActivity(intent)
                    drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }
                else -> false
            }
        }

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
            val expensetype = transaction.data["expensetype"] as? String ?: "epic"
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
        if (expensetype == "income") {
            totalBalance += amount
        } else {
            totalBalance -= amount
        }
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

    private fun showFilterDialog() {
        val builder = AlertDialog.Builder(this)
        val inflater = layoutInflater
        val dialogLayout = inflater.inflate(R.layout.filter_dialog, null)

        val mainFilterRadioGroup = dialogLayout.findViewById<RadioGroup>(R.id.mainFilterRadioGroup)
        val sortingOptionsContainer = dialogLayout.findViewById<LinearLayout>(R.id.sortingOptionsContainer)
        val sortingRadioGroup = dialogLayout.findViewById<RadioGroup>(R.id.sortingRadioGroup)

        // Handle main filter selection to display sorting options
        mainFilterRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            sortingOptionsContainer.visibility = View.VISIBLE
            when (checkedId) {
                R.id.radioDate -> showSortingOptions(sortingRadioGroup, "date")
                R.id.radioCost -> showSortingOptions(sortingRadioGroup, "cost")
                R.id.radioCompany -> showSortingOptions(sortingRadioGroup, "company")
            }
        }

        builder.setView(dialogLayout)
        builder.setTitle("Filter Transactions")
        builder.setPositiveButton("Apply") { _, _ ->
            applySelectedSorting(mainFilterRadioGroup, sortingRadioGroup)
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    // Show sorting options based on the selected main filter
    private fun showSortingOptions(sortingRadioGroup: RadioGroup, filterType: String) {
        sortingRadioGroup.clearCheck()
        sortingRadioGroup.findViewById<RadioButton>(R.id.radioCostAsc).visibility = if (filterType == "cost") View.VISIBLE else View.GONE
        sortingRadioGroup.findViewById<RadioButton>(R.id.radioCostDesc).visibility = if (filterType == "cost") View.VISIBLE else View.GONE
        sortingRadioGroup.findViewById<RadioButton>(R.id.radioNewestToOldest).visibility = if (filterType == "date") View.VISIBLE else View.GONE
        sortingRadioGroup.findViewById<RadioButton>(R.id.radioOldestToNewest).visibility = if (filterType == "date") View.VISIBLE else View.GONE
        sortingRadioGroup.findViewById<RadioButton>(R.id.radioAlphabeticalAsc).visibility = if (filterType == "company") View.VISIBLE else View.GONE
        sortingRadioGroup.findViewById<RadioButton>(R.id.radioAlphabeticalDesc).visibility = if (filterType == "company") View.VISIBLE else View.GONE
    }

    // Apply sorting based on selected filter and sorting order
    // Apply sorting based on selected filter and sorting order
    private fun applySelectedSorting(mainFilterRadioGroup: RadioGroup, sortingRadioGroup: RadioGroup) {
        when (mainFilterRadioGroup.checkedRadioButtonId) {
            R.id.radioDate -> {
                when (sortingRadioGroup.checkedRadioButtonId) {
                    R.id.radioNewestToOldest -> sortByDateNewestToOldest()
                    R.id.radioOldestToNewest -> sortByDateOldestToNewest()
                }
            }
            R.id.radioCost -> {
                when (sortingRadioGroup.checkedRadioButtonId) {
                    R.id.radioCostAsc -> sortByAmountLowestToHighest()
                    R.id.radioCostDesc -> sortByAmountHighestToLowest()
                }
            }
            R.id.radioCompany -> {
                when (sortingRadioGroup.checkedRadioButtonId) {
                    R.id.radioAlphabeticalAsc -> sortAlphabeticallyAsc()
                    R.id.radioAlphabeticalDesc -> sortAlphabeticallyDesc()
                }
            }
        }
        CoroutineScope(Dispatchers.IO).launch {
            displayTransactions(transactions)
        }
    }

    // Sort by amount from lowest to highest
    private fun sortByAmountLowestToHighest() {
        transactions.sortBy { (it.data["moneyamount"] as? Double)?.absoluteValue ?: 0.0 }
    }

    // Sort by amount from highest to lowest
    private fun sortByAmountHighestToLowest() {
        transactions.sortByDescending { (it.data["moneyamount"] as? Double)?.absoluteValue ?: 0.0 }
    }

    // Sort by date from oldest to newest
    private fun sortByDateOldestToNewest() {
        transactions.sortBy { it.data["time"] as? String }
    }

    // Sort by date from newest to oldest
    private fun sortByDateNewestToOldest() {
        transactions.sortByDescending { it.data["time"] as? String }
    }

    // Sort transactions alphabetically (ascending A-Z) by company name
    private fun sortAlphabeticallyAsc() {
        transactions.sortBy { it.data["transname"] as? String }
    }

    // Sort transactions alphabetically (descending Z-A) by company name
    private fun sortAlphabeticallyDesc() {
        transactions.sortByDescending { it.data["transname"] as? String }
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
