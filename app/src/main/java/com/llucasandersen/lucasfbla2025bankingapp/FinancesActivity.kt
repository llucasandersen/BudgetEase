package com.llucasandersen.lucasfbla2025bankingapp

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.MenuItem
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.navigation.NavigationView
import io.appwrite.Client
import io.appwrite.Query
import io.appwrite.exceptions.AppwriteException
import io.appwrite.services.Databases
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okio.IOException
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class FinancesActivity : AppCompatActivity() {

    private val geminiApiKey = "AIzaSyANCEYsx6C7oAPed3kgVojTdfJzF3IrgPI"

    private lateinit var lineChart: LineChart
    private lateinit var pieChart: PieChart
    private lateinit var appwriteClient: Client
    private lateinit var databases: Databases
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var aiAnalysisTextView: TextView
    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private lateinit var username: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_finances)

        // Initialize UI components
        lineChart = findViewById(R.id.lineChart)
        pieChart = findViewById(R.id.pieChart)
        aiAnalysisTextView = findViewById(R.id.aiAnalysisTextView)
        drawerLayout = findViewById(R.id.drawer_layout)

        // Initialize Appwrite Client
        appwriteClient = Client(applicationContext)
            .setEndpoint("https://append.lucasserver.cloud/v1")
            .setProject("6709882f194aa1acc096")
            .setSelfSigned(true)

        databases = Databases(appwriteClient)

        // Retrieve username from intent and assign it to the class-level variable
        username = intent.getStringExtra("username") ?: ""

        // Fetch and display initial data
        fetchDataAndDisplayCharts()

        // Sorting buttons
        val btnSortByDate = findViewById<Button>(R.id.btnSortByDate)
        val btnSortByAmount = findViewById<Button>(R.id.btnSortByAmount)
        btnSortByDate.setOnClickListener { fetchDataAndDisplayCharts(sortBy = "date") }
        btnSortByAmount.setOnClickListener { fetchDataAndDisplayCharts(sortBy = "amount") }

        // Navigation drawer setup
        val navView = findViewById<NavigationView>(R.id.nav_view)
        navView.setNavigationItemSelectedListener { menuItem ->
            handleNavigation(menuItem)
            true
        }
    }

    private fun fetchDataAndDisplayCharts(sortBy: String = "date") {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val transactions = fetchTransactions(sortBy)
                val (incomeTotal, expenseTotal) = calculateIncomeAndExpense(transactions)
                val balanceOverTime = calculateBalanceOverTime(transactions)

                // Run AI analysis based on transactions and total balance
                val totalBalance = incomeTotal - expenseTotal
                analyzeSpendingWithAI(transactions, totalBalance)

                withContext(Dispatchers.Main) {
                    setupLineChart(balanceOverTime)
                    setupPieChart(incomeTotal, expenseTotal)
                    aiAnalysisTextView.text = "AI Analysis: Based on your spending patterns, here are some recommendations..."
                }
            } catch (e: AppwriteException) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FinancesActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun fetchTransactions(sortBy: String): List<Pair<Date, Float>> {
        return withContext(Dispatchers.IO) {
            val response = databases.listDocuments(
                databaseId = "670b28f7d3c283d1d07c",
                collectionId = "670b292655fd525a2783",
                queries = listOf(Query.equal("username", username))
            )
            val transactions = mutableListOf<Pair<Date, Float>>()
            for (document in response.documents) {
                val timeString = document.data["time"] as? String ?: "Unknown"
                val moneyAmount = (document.data["moneyamount"] as? Double)?.toFloat() ?: 0f
                val expensetype = document.data["expensetype"] as? String ?: "unknown"
                val time = sdf.parse(timeString) ?: Date()

                // Adjust the amount based on type (income/expense)
                val amount = if (expensetype == "income") moneyAmount else -moneyAmount
                transactions.add(Pair(time, amount))
            }
            if (sortBy == "amount") transactions.sortByDescending { it.second } else transactions.sortBy { it.first }
            transactions
        }
    }

    private fun calculateIncomeAndExpense(transactions: List<Pair<Date, Float>>): Pair<Float, Float> {
        var incomeTotal = 0f
        var expenseTotal = 0f
        transactions.forEach { (_, amount) ->
            if (amount >= 0) incomeTotal += amount else expenseTotal += -amount
        }
        return Pair(incomeTotal, expenseTotal)
    }

    private fun calculateBalanceOverTime(transactions: List<Pair<Date, Float>>): List<Pair<Date, Float>> {
        var currentBalance = 0f
        return transactions.map { (time, amount) ->
            currentBalance += amount
            Pair(time, currentBalance)
        }
    }

    private fun setupLineChart(transactions: List<Pair<Date, Float>>) {
        // Map each transaction to an Entry with its transaction date and amount directly
        val transactionEntries = transactions
            .filterIndexed { index, _ -> index % 5 == 0 } // Reduce data points for readability
            .mapIndexed { index, (date, amount) ->
                Entry(index.toFloat(), amount).apply {
                    data = getRelativeTimeAgo(date) // Store relative time as label for x-axis
                }
            }

        val lineDataSet = LineDataSet(transactionEntries, "Balance Changes Over Time").apply {
            color = Color.BLUE
            lineWidth = 2f
            valueTextColor = Color.BLACK
            valueTextSize = 12f // Larger text for readability
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return "$${String.format("%,.2f", value)}" // Format with $ for clarity
                }
            }
        }

        lineChart.apply {
            data = LineData(lineDataSet)
            description.text = "Balance Changes"
            description.textSize = 14f // Larger description text
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return transactionEntries.getOrNull(value.toInt())?.data as? String ?: ""
                }
            }
            xAxis.granularity = 1f
            xAxis.labelRotationAngle = -45f // Rotate labels for better readability
            xAxis.textSize = 12f // Increase x-axis label text size
            axisRight.isEnabled = false
            axisLeft.textSize = 12f // Increase y-axis label text size
            setTouchEnabled(true)
            setPinchZoom(true)
            invalidate()
        }
    }

    private fun setupPieChart(incomeTotal: Float, expenseTotal: Float) {
        val entries = listOf(PieEntry(incomeTotal, "Income"), PieEntry(expenseTotal, "Expense"))
        val pieDataSet = PieDataSet(entries, "Income vs Expenses").apply {
            colors = listOf(Color.GREEN, Color.RED)
            valueTextSize = 16f
            sliceSpace = 3f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return "$${String.format("%,.2f", value)}"
                }
            }
        }

        pieChart.apply {
            data = PieData(pieDataSet)
            description.isEnabled = false
            isDrawHoleEnabled = true
            setTouchEnabled(true)
            invalidate()
        }
    }

    private fun analyzeSpendingWithAI(transactions: List<Pair<Date, Float>>, totalBalance: Float) {
        val client = OkHttpClient.Builder()
            .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        // Prepare the transactions message to send
        val transactionText = transactions.joinToString("\n") { (date, amount) ->
            "${sdf.format(date)}: ${if (amount < 0) "-" else "+"}$${String.format("%.2f", kotlin.math.abs(amount))}"
        }
        val message = "Here are the user's transactions:\n$transactionText\nTotal balance: $totalBalance. Analyze spending habits, increase or decrease from last week, and recommend cost-saving tips. You are Budget Ease's AI Financial Advisor Make your response short and concise make it super easy for the user to understand and get better like say you spend too much here or you wont have enough money if you keep buying this okay this is a one on one comversation they dont supply you this information the app does from their transactions do not break character and remember be short and concise and understandable. still be specific of what is causing them a problem like their habits or tell them what to do instead."

        // Create JSON request data
        val json = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", message)
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 1)
                put("topK", 40)
                put("topP", 0.95)
                put("maxOutputTokens", 8192)
                put("responseMimeType", "text/plain")
            })
        }

        // Build the request with the Google Gemini API endpoint
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-pro-002:generateContent?key=$geminiApiKey")
            .addHeader("Content-Type", "application/json")
            .post(RequestBody.create("application/json; charset=utf-8".toMediaTypeOrNull(), json.toString()))
            .build()

        // Make the asynchronous API call to Google Gemini
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {

                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    try {
                        val jsonResponse = JSONObject(responseBody)
                        val textContent = jsonResponse
                            .getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text")

                        // Replace markdown-like syntax with HTML
                        val formattedText = textContent
                            .replace("\\*\\*(.*?)\\*\\*".toRegex(), "<b>$1</b>")  // Bold text
                            .replace("\\*(.*?)\\*".toRegex(), "<i>$1</i>")        // Italic text
                            .replace("\n", "<br>")                               // Line breaks
                            .replace("(?m)^- (.*?)$".toRegex(), "<li>$1</li>")   // Unordered list items
                            .replace(
                                "(?m)^\\* (.*?)$".toRegex(),
                                "<li>$1</li>"
                            ) // Unordered list items (alternative syntax)
                            .replace("(?m)^\\d+\\. (.*?)$".toRegex(), "<li>$1</li>") // Ordered list items
                            .replace("(<li>.*?</li>)".toRegex(), "<ul>$1</ul>")  // Wrap list items in <ul> tags

                        runOnUiThread {
                            // Display formatted HTML
                            aiAnalysisTextView.text =
                                android.text.Html.fromHtml(formattedText, android.text.Html.FROM_HTML_MODE_LEGACY)
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            Toast.makeText(this@FinancesActivity, "Failed to parse AI response", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                }
            }

        })
    }

    private fun getRelativeTimeAgo(date: Date): String {
        val now = System.currentTimeMillis()
        val diff = now - date.time
        val days = diff / (1000 * 60 * 60 * 24)
        val hours = (diff / (1000 * 60 * 60)) % 24
        val minutes = (diff / (1000 * 60)) % 60
        return when {
            days > 0 -> "$days day${if (days > 1) "s" else ""} ago"
            hours > 0 -> "$hours hour${if (hours > 1) "s" else ""} ago"
            minutes > 0 -> "$minutes minute${if (minutes > 1) "s" else ""} ago"
            else -> "just now"
        }
    }

    private fun handleNavigation(item: MenuItem) {
        when (item.itemId) {
            R.id.nav_home -> {
                val userEmail = intent.getStringExtra("user_email") ?: ""
                val intent = Intent(this, UserDashboardActivity::class.java)
                intent.putExtra("user_email", userEmail)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(intent)
                drawerLayout.closeDrawer(GravityCompat.START)
            }
            R.id.nav_finances -> drawerLayout.closeDrawer(Gravity.START)
            R.id.nav_settings -> {
                val userEmail = intent.getStringExtra("username") ?: ""
                val intent = Intent(this, SettingsActivity::class.java)
                intent.putExtra("username", userEmail)
                startActivity(intent)
                drawerLayout.closeDrawer(GravityCompat.START)

            }
        }
    }
}
