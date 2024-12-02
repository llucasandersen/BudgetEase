package com.llucasandersen.lucasfbla2025bankingapp

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.navigation.NavigationView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import io.appwrite.Client
import io.appwrite.services.Databases
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HelpActivity : AppCompatActivity() {

    private lateinit var databases: Databases
    private var isNewUser = false
    private lateinit var viewPager: ViewPager2
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var adapter: HelpPagerAdapter // Declare the adapter at the class level

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help)

        val email = intent.getStringExtra("user_email")
        isNewUser = intent.getBooleanExtra("isnew", true)
        drawerLayout = findViewById(R.id.drawer_layout)

        // Initialize Appwrite client and databases
        val client = Client(this)
            .setEndpoint("https://append.lucasserver.cloud/v1") // Replace with your Appwrite endpoint
            .setProject("6709882f194aa1acc096")
            .setSelfSigned(true)

        databases = Databases(client)

        if (isNewUser) {
            updateIsNewField(email)
        }

        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        viewPager = findViewById(R.id.viewPager)

        // Disable swiping between pages
        viewPager.isUserInputEnabled = false

        // Initialize and assign HelpPagerAdapter
        adapter = HelpPagerAdapter(this, email) { pageIndex ->
            viewPager.currentItem = pageIndex // Navigate to selected page
        }
        viewPager.adapter = adapter

        val navView = findViewById<NavigationView>(R.id.nav_view)
        navView.setNavigationItemSelectedListener { menuItem ->
            handleNavigation(menuItem)
            true
        }

        // Link TabLayout with ViewPager
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Welcome"
                1 -> "Search & Filter"
                2 -> "Finances"
                3 -> "Navigation Menu"
                4 -> "AI Assistant"
                else -> "Page $position"
            }
        }.attach()
    }

    private fun handleNavigation(item: MenuItem): Boolean {
        if (item.itemId == R.id.nav_next) {
            adapter.navigateToNextPage() // Tell the adapter to move to the next page
            drawerLayout.closeDrawer(GravityCompat.START) // Close the drawer
        }
        return true
    }

    private fun updateIsNewField(email: String?) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = databases.listDocuments(
                    databaseId = "670b28f7d3c283d1d07c",
                    collectionId = "670b29153753e63ec2b7",
                    queries = listOf("username=$email")
                )
                val documentId = response.documents.firstOrNull()?.id ?: return@launch
                databases.updateDocument(
                    databaseId = "670b28f7d3c283d1d07c",
                    collectionId = "670b29153753e63ec2b7",
                    documentId = documentId,
                    data = mapOf("isnew" to false)
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
