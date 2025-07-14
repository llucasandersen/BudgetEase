package com.llucasandersen.lucasfbla2025bankingapp

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import com.llucasandersen.lucasfbla2025bankingapp.BuildConfig

class HelpPagerAdapter(
    private val context: Context,
    private val email: String?,
    private val onPageChange: (Int) -> Unit
) : RecyclerView.Adapter<HelpPagerAdapter.ViewHolder>() {

    private val pages = listOf(
        R.layout.help_page_welcome,
        R.layout.help_page_search_filter,
        R.layout.help_page_finances,
        R.layout.help_page_nav_menu,
        R.layout.help_page_ai_chat
    )

    var currentPageIndex: Int = 0 // Track the current page index

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(viewType, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        when (position) {
            0 -> setupWelcomePage(holder)
            1 -> setupSearchFilterPage(holder)
            2 -> setupFinancesPage(holder)
            3 -> setupNavMenuPage(holder)
            4 -> setupAIChatPage(holder)
        }
    }

    override fun getItemCount(): Int = pages.size

    override fun getItemViewType(position: Int): Int = pages[position]

    // Method to navigate to the next page
    fun navigateToNextPage() {
        if (currentPageIndex < pages.lastIndex) {
            currentPageIndex++
            onPageChange(currentPageIndex)
        }
    }

    private fun setupWelcomePage(holder: ViewHolder) {
        val nextButton = holder.itemView.findViewById<Button>(R.id.nextButton)
        nextButton?.setOnClickListener {
            currentPageIndex = 1
            onPageChange(1)
        }
    }

    private fun setupSearchFilterPage(holder: ViewHolder) {
        val nextButton = holder.itemView.findViewById<Button>(R.id.nextButton)
        nextButton?.setOnClickListener {
            currentPageIndex = 2
            onPageChange(2)
        }
    }

    private fun setupFinancesPage(holder: ViewHolder) {
        val nextButton = holder.itemView.findViewById<Button>(R.id.nextButton)
        nextButton?.setOnClickListener {
            currentPageIndex = 3
            onPageChange(3)
        }
    }

    private fun setupNavMenuPage(holder: ViewHolder) {
        val nextButton = holder.itemView.findViewById<Button>(R.id.nextButton)
        nextButton?.setOnClickListener {
            currentPageIndex = 4
            onPageChange(4)
        }
    }

    private fun setupAIChatPage(holder: ViewHolder) {
        val chatMessages = holder.itemView.findViewById<TextView>(R.id.chatMessages)
        val sendButton = holder.itemView.findViewById<ImageButton>(R.id.sendButton)
        val chatInputField = holder.itemView.findViewById<EditText>(R.id.chatInputField)
        val chatScrollView = holder.itemView.findViewById<ScrollView>(R.id.chatScrollView)
        val beginBudgetEaseButton = holder.itemView.findViewById<Button>(R.id.beginBudgetEaseButton)

        sendButton.setOnClickListener {
            val userMessage = chatInputField.text.toString()
            if (userMessage.isNotEmpty()) {
                chatInputField.text.clear()
                chatMessages.append("\nYou: $userMessage\n")

                CoroutineScope(Dispatchers.IO).launch {
                    val aiResponse = fetchAIResponse(userMessage)
                    withContext(Dispatchers.Main) {
                        chatMessages.append("\nAI Assistant: $aiResponse")
                        chatScrollView.post { chatScrollView.fullScroll(View.FOCUS_DOWN) }
                    }
                }
            }
        }

        beginBudgetEaseButton.setOnClickListener {
            val intent = Intent(context, UserDashboardActivity::class.java)
            intent.putExtra("user_email", email)
            context.startActivity(intent)
        }
    }

    private suspend fun fetchAIResponse(userMessage: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .build()

                // Constructing the correct JSON payload
                val json = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", userMessage)
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

                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=${BuildConfig.GEMINI_API_KEY}")
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create("application/json; charset=utf-8".toMediaTypeOrNull(), json.toString()))
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: ""
                    println("Raw Response: $responseBody") // Debugging: Log the raw response
                    try {
                        val jsonResponse = JSONObject(responseBody)

                        // Access the candidates array
                        val candidates = jsonResponse.getJSONArray("candidates")
                        if (candidates.length() > 0) {
                            val firstCandidate = candidates.getJSONObject(0)

                            // Access the content object
                            val content = firstCandidate.optJSONObject("content")
                            if (content != null) {
                                val parts = content.optJSONArray("parts")
                                if (parts != null && parts.length() > 0) {
                                    val textContent = StringBuilder()

                                    // Loop through the parts array and extract text
                                    for (i in 0 until parts.length()) {
                                        val part = parts.getJSONObject(i)
                                        textContent.append(part.optString("text", ""))
                                    }

                                    // Return only the concatenated text
                                    return@withContext textContent.toString()
                                } else {
                                    return@withContext "No parts available in content"
                                }
                            } else {
                                return@withContext "No content found in candidate"
                            }
                        } else {
                            return@withContext "No candidates in response"
                        }
                    } catch (e: Exception) {
                        return@withContext "Failed to parse AI response: ${e.message}"
                    }
                } else {
                    val errorBody = response.body?.string() ?: "No error body"
                    println("Error Response: $errorBody") // Debugging: Log the error response
                    return@withContext "Error Code: ${response.code}, Error Body: $errorBody"
                }


            } catch (e: Exception) {
                "There was an issue connecting to the AI. Please try again later. Error: ${e.message}"
            }
        }
    }










    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
}
