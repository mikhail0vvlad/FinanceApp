package ru.shmr.finance.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import ru.shmr.finance.R

enum class Destination(
    val route: String,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
) {
    Expenses("expenses", R.string.nav_expenses, R.drawable.ic_nav_expenses),
    Income("income", R.string.nav_income, R.drawable.ic_nav_income),
    Accounts("accounts", R.string.nav_accounts, R.drawable.ic_nav_accounts),
}
