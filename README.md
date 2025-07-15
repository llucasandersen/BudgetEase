# 📊 Budget Ease 📱

[![CI](https://github.com/llucasandersen/BudgetEase/actions/workflows/android-ci.yml/badge.svg)](https://github.com/llucasandersen/BudgetEase/actions/workflows/android-ci.yml) [![License](https://img.shields.io/github/license/llucasandersen/BudgetEase)](LICENSE) [![Issues](https://img.shields.io/github/issues/llucasandersen/BudgetEase)](https://github.com/llucasandersen/BudgetEase/issues)

Welcome to **Budget Ease**, a mobile app created by **Lucas Andersen** for the **2024-2025 FBLA Coding and Programming competition**. Budget Ease is designed to help students effectively manage their personal finances with ease and precision.

🏆 **FBLA 2025 NLC National Competitor**

Budget Ease was a national competitor at the **2025 FBLA National Leadership Conference (NLC)**, where it proudly showcased its features.

---

## 🎯 Key Features 🔑

### 1. Secure Login System 🔒
- Automatically redirects users to the correct dashboard based on their role (Admin or Regular User).
- Built with a Self-Hosted Appwrite backend for a secure and seamless authentication experience.

### 2. Home Dashboard 🏠
- Displays your total balance, monthly income/expenses, and weekly income/expenses at a glance.
- Allows users to view transactions, search by name or date, and filter by:
  - 📅 Date: Oldest/Newest
  - 💰 Cost: High/Low
  - 🏢 Company: A–Z/Z–A

### 3. Finances Page 📈
- Track balance changes over time with visualizations like:
  - Interactive Donut Charts (Income vs. Expenses)
  - Line Charts
- AI-powered analysis reports using the Gemini API 🧠.
- Export customized reports to PDF for easy sharing and record-keeping.

### 4. Settings Page ⚙️
- View your username, update your password, or securely log out.

### 5. Help Page 🆘
- New users are redirected to a Help/Tutorial page for a guided onboarding experience with an engaging Budget Ease AI support.

### 6. Admin Dashboard 🛠️
- Manage user accounts, view/edit balances, and oversee transactions efficiently.

---

## 🌟 Made with ❤️ by Lucas Andersen

"Budget Ease simplifies student finances, offering the tools to spend smarter and save better!"

## 🔑 API Key Setup

To enable AI-powered features, supply your own Gemini API key by either:

1. Creating a `local.properties` file with `geminiApiKey=YOUR_KEY`.
2. Or setting the `GEMINI_API_KEY` environment variable.

The repository does not include an API key for security reasons.
