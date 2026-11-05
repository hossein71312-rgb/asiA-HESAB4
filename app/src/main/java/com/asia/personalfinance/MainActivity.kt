package com.asia.personalfinance

import android.app.DatePickerDialog
import android.os.Bundle
import android.os.Build
import android.content.pm.PackageManager
import androidx.work.*
import java.util.concurrent.TimeUnit
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

private val Navy = Color(0xFF142B45)
private val Teal = Color(0xFF159A9C)
private val Page = Color(0xFFF2F5F8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dao = FinanceDatabase.get(this).dao()
        scheduleCheckReminders()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 501)
        }
        setContent {
            val vm: FinanceViewModel = viewModel(factory = FinanceViewModel.Factory(dao))
            AsiaTheme { AsiaApp(vm) }
        }
    }
}

@Composable
private fun AsiaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(primary = Navy, secondary = Teal, background = Page, surface = Color.White),
        content = content
    )
}

class FinanceViewModel(private val dao: FinanceDao) : ViewModel() {
    val accounts = dao.observeAccounts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val transactions = dao.observeTransactions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val budgets = dao.observeBudgets().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val debts = dao.observeDebts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val checks = dao.observeChecks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addAccount(name: String, kind: String, opening: Long) = viewModelScope.launch {
        dao.addAccount(AccountEntity(name = name, kind = kind, openingBalanceRial = opening))
    }
    fun addTransaction(accountId: Long, title: String, amount: Long, income: Boolean, category: String) = viewModelScope.launch {
        dao.addTransaction(TransactionEntity(accountId = accountId, title = title, amountRial = amount,
            type = if (income) "INCOME" else "EXPENSE", category = category))
    }
    fun addBudget(category: String, limit: Long, month: String) = viewModelScope.launch {
        dao.saveBudget(BudgetEntity(category = category, limitRial = limit, monthKey = month))
    }
    fun addDebt(person: String, title: String, amount: Long, direction: String, due: Long?) = viewModelScope.launch {
        dao.addDebt(DebtEntity(person = person, title = title, originalRial = amount,
            remainingRial = amount, direction = direction, dueAt = due))
    }
    fun addCheck(number: String, bank: String, person: String, amount: Long, due: Long, direction: String) = viewModelScope.launch {
        dao.addCheck(CheckEntity(number = number, bank = bank, person = person, amountRial = amount,
            dueAt = due, direction = direction))
    }
    fun updateCheck(item: CheckEntity, status: String) = viewModelScope.launch {
        dao.updateCheck(item.copy(status = status))
    }
    fun deleteAccount(item: AccountEntity) = viewModelScope.launch { dao.deleteAccount(item) }
    fun deleteTransaction(item: TransactionEntity) = viewModelScope.launch { dao.deleteTransaction(item) }
    fun deleteBudget(item: BudgetEntity) = viewModelScope.launch { dao.deleteBudget(item) }
    fun deleteDebt(item: DebtEntity) = viewModelScope.launch { dao.deleteDebt(item) }
    fun deleteCheck(item: CheckEntity) = viewModelScope.launch { dao.deleteCheck(item) }

    class Factory(private val dao: FinanceDao) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = FinanceViewModel(dao) as T
    }
}

@Composable
private fun AsiaApp(vm: FinanceViewModel) {
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val transactions by vm.transactions.collectAsStateWithLifecycle()
    val budgets by vm.budgets.collectAsStateWithLifecycle()
    val debts by vm.debts.collectAsStateWithLifecycle()
    val checks by vm.checks.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    val prefs = remember { context.getSharedPreferences("asia_settings", android.content.Context.MODE_PRIVATE) }
    var unitToman by remember { mutableStateOf(prefs.getBoolean("unit_toman", true)) }
    var dialog by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingMessage by remember { mutableStateOf("") }
    val askDelete: (String, () -> Unit) -> Unit = { message, action -> pendingMessage = message; pendingDelete = action }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            containerColor = Page,
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    val labels = listOf("خانه", "حساب‌ها", "بودجه", "بدهی و طلب", "چک‌ها", "تنظیمات")
                    val icons = listOf(Icons.Default.Home, Icons.Default.AccountBalance, Icons.Default.PieChart,
                        Icons.Default.People, Icons.Default.ReceiptLong, Icons.Default.Settings)
                    labels.forEachIndexed { index, label ->
                        NavigationBarItem(selected = tab == index, onClick = { tab = index },
                            icon = { Icon(icons[index], null) }, label = { Text(label, fontSize = 9.sp) })
                    }
                }
            },
            floatingActionButton = {
                if (tab in 1..4) FloatingActionButton(onClick = {
                    dialog = when (tab) { 1 -> "account"; 2 -> "budget"; 3 -> "debt"; else -> "check" }
                }, containerColor = Teal, contentColor = Color.White) { Icon(Icons.Default.Add, "افزودن") }
                else if (tab == 0) FloatingActionButton(onClick = { dialog = "transaction" },
                    containerColor = Teal, contentColor = Color.White) { Icon(Icons.Default.Add, "ثبت تراکنش") }
            }
        ) { padding ->
            when (tab) {
                0 -> HomeScreen(Modifier.padding(padding), accounts, transactions, checks, unitToman, { dialog = "transaction" }) { item ->
                    askDelete("این تراکنش حذف شود؟") { vm.deleteTransaction(item) }
                }
                1 -> AccountsScreen(Modifier.padding(padding), accounts, transactions, unitToman, { dialog = "account" }) { item ->
                    askDelete("حساب «${item.name}» و همه تراکنش‌های آن حذف شود؟") { vm.deleteAccount(item) }
                }
                2 -> BudgetsScreen(Modifier.padding(padding), budgets, transactions, unitToman, { dialog = "budget" }) { item ->
                    askDelete("این بودجه حذف شود؟") { vm.deleteBudget(item) }
                }
                3 -> DebtsScreen(Modifier.padding(padding), debts, unitToman, { dialog = "debt" }) { item ->
                    askDelete("این مورد حذف شود؟") { vm.deleteDebt(item) }
                }
                4 -> ChecksScreen(Modifier.padding(padding), checks, unitToman, vm) { item ->
                    askDelete("چک ${item.number} حذف شود؟") { vm.deleteCheck(item) }
                }
                else -> SettingsScreen(Modifier.padding(padding), unitToman, {
                    unitToman = it
                    prefs.edit().putBoolean("unit_toman", it).apply()
                }, context, accounts, transactions, budgets, debts, checks)
            }
        }

        pendingDelete?.let { action ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text("تأیید حذف") },
                text = { Text(pendingMessage) },
                confirmButton = { TextButton(onClick = { action(); pendingDelete = null }) { Text("حذف", color = Color(0xFFD45B5B)) } },
                dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("انصراف") } }
            )
        }

        when (dialog) {
            "account" -> AccountDialog({ dialog = "" }) { n, k, b -> vm.addAccount(n, k, b); dialog = "" }
            "transaction" -> TransactionDialog(accounts, { dialog = "" }) { a, t, m, i, c ->
                vm.addTransaction(a, t, m, i, c); dialog = ""
            }
            "budget" -> BudgetDialog({ dialog = "" }) { c, m -> vm.addBudget(c, m, monthKey()); dialog = "" }
            "debt" -> DebtDialog({ dialog = "" }) { p, t, a, d -> vm.addDebt(p, t, a, d, null); dialog = "" }
            "check" -> CheckDialog({ dialog = "" }) { n, b, p, a, due, d ->
                vm.addCheck(n, b, p, a, due, d); dialog = ""
            }
        }
    }
}

@Composable
private fun ScreenColumn(modifier: Modifier, title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title, color = Navy, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        if (subtitle != null) Text(subtitle, color = Color.Gray, fontSize = 12.sp)
        content()
    }
}

@Composable
private fun HomeScreen(modifier: Modifier, accounts: List<AccountEntity>, tx: List<TransactionEntity>,
                       checks: List<CheckEntity>, toman: Boolean, add: () -> Unit,
                       onDelete: (TransactionEntity) -> Unit) {
    val income = tx.filter { it.type == "INCOME" }.sumOf { it.amountRial }
    val expense = tx.filter { it.type == "EXPENSE" }.sumOf { it.amountRial }
    val dueSoon = checks.count { it.status == "PENDING" && it.dueAt <= System.currentTimeMillis() + 7L * 86400000 }
    ScreenColumn(modifier, "آسیا حساب", "مدیریت امور مالی شخصی") {
        Surface(color = Navy, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("مانده خالص تراکنش‌ها", color = Color.White.copy(alpha = .75f))
                Text(money(income - expense, toman), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("درآمد: ${money(income, toman)}", color = Color(0xFFB7F0D4), fontSize = 12.sp)
                    Text("هزینه: ${money(expense, toman)}", color = Color(0xFFFFD2D2), fontSize = 12.sp)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("حساب‌ها", accounts.size.toString(), Modifier.weight(1f))
            MetricCard("تراکنش‌ها", tx.size.toString(), Modifier.weight(1f))
            MetricCard("چک نزدیک سررسید", dueSoon.toString(), Modifier.weight(1f))
        }
        Button(onClick = add, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Text("ثبت درآمد یا هزینه") }
        Text("آخرین تراکنش‌ها", color = Navy, fontWeight = FontWeight.Bold)
        if (tx.isEmpty()) EmptyState("هنوز تراکنشی ثبت نشده است")
        tx.take(30).forEach { TransactionCard(it, toman) { onDelete(it) } }
        if (dueSoon > 0) Text("توجه: $dueSoon چک در هفت روز آینده سررسید می‌شود.", color = Color(0xFFB45309))
    }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier) {
    Surface(modifier, color = Color.White, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(title, color = Color.Gray, fontSize = 10.sp)
            Text(value, color = Navy, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
    }
}

@Composable
private fun AccountsScreen(modifier: Modifier, accounts: List<AccountEntity>, tx: List<TransactionEntity>,
                           toman: Boolean, add: () -> Unit, onDelete: (AccountEntity) -> Unit) {
    ScreenColumn(modifier, "حساب‌ها و کیف پول", "افزودن حساب بانکی، نقدی یا کیف پول") {
        Button(onClick = add, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Text("افزودن حساب جدید") }
        if (accounts.isEmpty()) EmptyState("هنوز حسابی تعریف نشده است")
        accounts.forEach { account ->
            val net = tx.filter { it.accountId == account.id }.sumOf { if (it.type == "INCOME") it.amountRial else -it.amountRial }
            Surface(color = Color.White, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(account.name, color = Navy, fontWeight = FontWeight.Bold)
                    Text(account.kind, color = Color.Gray, fontSize = 11.sp)
                    Text("موجودی: ${money(account.openingBalanceRial + net, toman)}", color = Teal, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { onDelete(account) }) { Text("حذف حساب", color = Color(0xFFD45B5B)) }
                }
            }
        }
    }
}

@Composable
private fun BudgetsScreen(modifier: Modifier, budgets: List<BudgetEntity>, tx: List<TransactionEntity>, toman: Boolean,
                          add: () -> Unit, onDelete: (BudgetEntity) -> Unit) {
    ScreenColumn(modifier, "بودجه‌بندی", "سقف هزینه برای دسته‌های مختلف (ماه شمسی جاری)") {
        Button(onClick = add, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Text("تعریف بودجه") }
        if (budgets.isEmpty()) EmptyState("بودجه‌ای ثبت نشده است")
        budgets.forEach { b ->
            Surface(color = Color.White, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(b.category, color = Navy, fontWeight = FontWeight.Bold)
                    Text("سقف بودجه: ${money(b.limitRial, toman)}", color = Teal)
                    Text("دوره: ${b.monthKey}", color = Color.Gray, fontSize = 11.sp)
                    val spent = tx.filter {
                        it.type == "EXPENSE" && it.category.trim() == b.category.trim() && monthKey(it.createdAt) == b.monthKey
                    }.sumOf { it.amountRial }
                    val over = spent > b.limitRial
                    val fraction = if (b.limitRial > 0) (spent.toFloat() / b.limitRial.toFloat()).coerceIn(0f, 1f) else 0f
                    LinearProgressIndicator(
                        progress = { fraction },
                        color = if (over) Color(0xFFD45B5B) else Teal,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "مصرف‌شده: ${money(spent, toman)}" + if (over) " (بیش از سقف)" else "",
                        color = if (over) Color(0xFFD45B5B) else Color.Gray, fontSize = 11.sp
                    )
                    TextButton(onClick = { onDelete(b) }) { Text("حذف بودجه", color = Color(0xFFD45B5B)) }
                }
            }
        }
    }
}

@Composable
private fun DebtsScreen(modifier: Modifier, debts: List<DebtEntity>, toman: Boolean, add: () -> Unit, onDelete: (DebtEntity) -> Unit) {
    ScreenColumn(modifier, "بدهی و مطالبات", "ثبت طلب‌ها و بدهی‌های شخصی") {
        Button(onClick = add, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Text("ثبت بدهی یا طلب") }
        if (debts.isEmpty()) EmptyState("موردی ثبت نشده است")
        debts.forEach { d ->
            Surface(color = Color.White, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(d.person, color = Navy, fontWeight = FontWeight.Bold)
                    Text(d.title, color = Color.Gray, fontSize = 12.sp)
                    Text(if (d.direction == "OWED_TO_ME") "طلب من" else "بدهی من", color = Teal)
                    Text("مانده: ${money(d.remainingRial, toman)}", color = Navy, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { onDelete(d) }) { Text("حذف", color = Color(0xFFD45B5B)) }
                }
            }
        }
    }
}

@Composable
private fun ChecksScreen(modifier: Modifier, checks: List<CheckEntity>, toman: Boolean, vm: FinanceViewModel, onDelete: (CheckEntity) -> Unit) {
    ScreenColumn(modifier, "مدیریت چک‌ها", "چک‌های دریافتی و پرداختی، سررسید و وضعیت") {
        val pending = checks.filter { it.status == "PENDING" }
        val overdue = pending.count { it.dueAt < startOfToday() }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("در انتظار", pending.size.toString(), Modifier.weight(1f))
            MetricCard("سررسید گذشته", overdue.toString(), Modifier.weight(1f))
        }
        if (checks.isEmpty()) EmptyState("هنوز چکی ثبت نشده است")
        checks.forEach { check ->
            val late = check.status == "PENDING" && check.dueAt < startOfToday()
            Surface(color = Color.White, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("چک ${check.number}", color = Navy, fontWeight = FontWeight.Bold)
                        Text(statusFa(check.status), color = if (late) Color(0xFFB45309) else Teal, fontSize = 11.sp)
                    }
                    Text("${check.bank} • ${check.person}", color = Color.Gray, fontSize = 12.sp)
                    Text("${if (check.direction == "RECEIVED") "دریافتی" else "پرداختی"} | ${money(check.amountRial, toman)}", color = Navy)
                    Text("سررسید: ${dateFa(check.dueAt)}", color = if (late) Color(0xFFB45309) else Color.Gray, fontSize = 12.sp)
                    if (check.status == "PENDING") {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            TextButton(onClick = { vm.updateCheck(check, "PAID") }) { Text("وصول/پرداخت شد") }
                            TextButton(onClick = { vm.updateCheck(check, "BOUNCED") }) { Text("برگشت خورد") }
                            TextButton(onClick = { vm.updateCheck(check, "CANCELLED") }) { Text("لغو") }
                        }
                    }
                    TextButton(onClick = { onDelete(check) }) { Text("حذف چک", color = Color(0xFFD45B5B)) }
                }
            }
        }
        Text("یادآوری سررسید چک‌ها در برنامه و به‌صورت اعلان روزانه فعال است.", color = Color.Gray, fontSize = 10.sp)
    }
}

@Composable
private fun SettingsScreen(
    modifier: Modifier,
    toman: Boolean,
    onUnit: (Boolean) -> Unit,
    context: android.content.Context,
    accounts: List<AccountEntity>,
    transactions: List<TransactionEntity>,
    budgets: List<BudgetEntity>,
    debts: List<DebtEntity>,
    checks: List<CheckEntity>
) {
    ScreenColumn(modifier, "تنظیمات", "شخصی‌سازی و نگهداری اطلاعات آسیا حساب") {
        Surface(color = Color.White, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("واحد نمایش مبالغ", color = Navy, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("تومان")
                    Spacer(Modifier.weight(1f))
                    Switch(checked = !toman, onCheckedChange = { onUnit(!it) })
                    Text("ریال")
                }
                Text("انتخاب واحد پس از بستن برنامه نیز حفظ می‌شود.", color = Color.Gray, fontSize = 10.sp)
            }
        }
        Button(
            onClick = {
                val backup = buildString {
                    appendLine("ASIA HESAB BACKUP - v${BuildConfig.VERSION_NAME}")
                    appendLine("[ACCOUNTS]")
                    accounts.forEach { appendLine("${it.id},${it.name},${it.kind},${it.openingBalanceRial}") }
                    appendLine("[TRANSACTIONS]")
                    transactions.forEach { appendLine("${it.id},${it.accountId},${it.title},${it.amountRial},${it.type},${it.category},${it.createdAt}") }
                    appendLine("[BUDGETS]")
                    budgets.forEach { appendLine("${it.id},${it.category},${it.limitRial},${it.monthKey}") }
                    appendLine("[DEBTS]")
                    debts.forEach { appendLine("${it.id},${it.person},${it.title},${it.originalRial},${it.remainingRial},${it.direction},${it.dueAt}") }
                    appendLine("[CHECKS]")
                    checks.forEach { appendLine("${it.id},${it.number},${it.bank},${it.person},${it.amountRial},${it.dueAt},${it.direction},${it.status}") }
                }
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_SUBJECT, "پشتیبان اطلاعات آسیا حساب")
                    putExtra(android.content.Intent.EXTRA_TEXT, backup)
                }
                context.startActivity(android.content.Intent.createChooser(send, "ذخیره یا ارسال پشتیبان"))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Backup, null)
            Spacer(Modifier.width(8.dp))
            Text("تهیه و اشتراک‌گذاری پشتیبان متنی")
        }
        SettingsItem(Icons.Default.Security, "امنیت برنامه", "قفل برنامه و اثر انگشت هنوز در صف توسعه است")
        SettingsItem(Icons.Default.CalendarMonth, "تقویم", "همه تاریخ‌ها شمسی (جلالی) نمایش داده می‌شوند")
        SettingsItem(Icons.Default.Info, "درباره برنامه", "آسیا حساب • نسخه ${BuildConfig.VERSION_NAME}")
        Text("اطلاعات عملیاتی در دیتابیس محلی Room ذخیره می‌شوند. فایل پشتیبان متنی برای انتقال دستی است و هنوز قابلیت بازیابی خودکار ندارد.", color = Color.Gray, fontSize = 11.sp)
    }
}

@Composable
private fun SettingsItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Surface(color = Color.White, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Teal)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, color = Navy, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = Color.Gray, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun TransactionCard(tx: TransactionEntity, toman: Boolean, onDelete: () -> Unit) {
    Surface(color = Color.White, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (tx.type == "INCOME") Icons.Default.ArrowDownward else Icons.Default.ArrowUpward, null,
                tint = if (tx.type == "INCOME") Teal else Color(0xFFD45B5B))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(tx.title, color = Navy, fontWeight = FontWeight.SemiBold)
                Text("${tx.category} • ${dateFa(tx.createdAt)}", color = Color.Gray, fontSize = 10.sp)
            }
            Text((if (tx.type == "INCOME") "+" else "−") + money(tx.amountRial, toman),
                color = if (tx.type == "INCOME") Teal else Color(0xFFD45B5B), fontWeight = FontWeight.Bold)
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "حذف", tint = Color.Gray) }
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Surface(color = Color.White, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Text(text, Modifier.padding(22.dp), color = Color.Gray)
    }
}

@Composable
private fun AccountDialog(close: () -> Unit, save: (String, String, Long) -> Unit) {
    var name by remember { mutableStateOf("") }; var kind by remember { mutableStateOf("بانکی") }; var balance by remember { mutableStateOf("") }
    SimpleDialog("افزودن حساب", close, {
        Field(name, { name = it }, "نام حساب")
        Field(kind, { kind = it }, "نوع حساب (بانکی، نقدی، کیف پول)")
        Field(balance, { balance = it }, "موجودی اولیه به ریال", true)
    }, { if (name.isNotBlank() && (balance.toLongOrNull() ?: -1) >= 0) save(name, kind, balance.toLong()) })
}

@Composable
private fun TransactionDialog(accounts: List<AccountEntity>, close: () -> Unit, save: (Long, String, Long, Boolean, String) -> Unit) {
    var title by remember { mutableStateOf("") }; var amount by remember { mutableStateOf("") }; var category by remember { mutableStateOf("متفرقه") }
    var income by remember { mutableStateOf(false) }; var accountId by remember { mutableLongStateOf(accounts.firstOrNull()?.id ?: 0L) }
    SimpleDialog("ثبت تراکنش", close, {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !income, onClick = { income = false }, label = { Text("هزینه") })
            FilterChip(selected = income, onClick = { income = true }, label = { Text("درآمد") })
        }
        Field(title, { title = it }, "عنوان")
        Field(amount, { amount = it }, "مبلغ به ریال", true)
        Field(category, { category = it }, "دسته‌بندی")
        if (accounts.isEmpty()) Text("ابتدا از بخش حساب‌ها یک حساب بسازید.", color = Color(0xFFB45309))
        else {
            Text("حساب مقصد/مبدأ", color = Navy)
            accounts.forEach { a ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = accountId == a.id, onClick = { accountId = a.id })
                    Text(a.name)
                }
            }
        }
    }, { if (accounts.isNotEmpty() && title.isNotBlank() && (amount.toLongOrNull() ?: 0) > 0) save(accountId, title, amount.toLong(), income, category) })
}

@Composable
private fun BudgetDialog(close: () -> Unit, save: (String, Long) -> Unit) {
    var category by remember { mutableStateOf("") }; var limit by remember { mutableStateOf("") }
    SimpleDialog("تعریف بودجه", close, {
        Field(category, { category = it }, "دسته‌بندی هزینه")
        Field(limit, { limit = it }, "سقف بودجه به ریال", true)
    }, { if (category.isNotBlank() && (limit.toLongOrNull() ?: 0) > 0) save(category, limit.toLong()) })
}

@Composable
private fun DebtDialog(close: () -> Unit, save: (String, String, Long, String) -> Unit) {
    var person by remember { mutableStateOf("") }; var title by remember { mutableStateOf("") }; var amount by remember { mutableStateOf("") }; var direction by remember { mutableStateOf("OWED_TO_ME") }
    SimpleDialog("ثبت بدهی یا طلب", close, {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = direction == "OWED_TO_ME", onClick = { direction = "OWED_TO_ME" }, label = { Text("طلب من") })
            FilterChip(selected = direction == "I_OWE", onClick = { direction = "I_OWE" }, label = { Text("بدهی من") })
        }
        Field(person, { person = it }, "نام شخص")
        Field(title, { title = it }, "شرح")
        Field(amount, { amount = it }, "مبلغ به ریال", true)
    }, { if (person.isNotBlank() && (amount.toLongOrNull() ?: 0) > 0) save(person, title, amount.toLong(), direction) })
}

@Composable
private fun CheckDialog(close: () -> Unit, save: (String, String, String, Long, Long, String) -> Unit) {
    var number by remember { mutableStateOf("") }; var bank by remember { mutableStateOf("") }; var person by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }; var due by remember { mutableStateOf("") }; var direction by remember { mutableStateOf("RECEIVED") }
    SimpleDialog("ثبت چک جدید", close, {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = direction == "RECEIVED", onClick = { direction = "RECEIVED" }, label = { Text("دریافتی") })
            FilterChip(selected = direction == "ISSUED", onClick = { direction = "ISSUED" }, label = { Text("پرداختی") })
        }
        Field(number, { number = it }, "شماره چک")
        Field(bank, { bank = it }, "بانک")
        Field(person, { person = it }, "در وجه / صادرکننده")
        Field(amount, { amount = it }, "مبلغ به ریال", true)
        Field(due, { due = it }, "تاریخ سررسید شمسی (مثال: 1405/08/20)", dateInput = true)
    }, {
        val millis = parseJalaliDate(due)
        if (number.isNotBlank() && (amount.toLongOrNull() ?: 0) > 0 && millis != null)
            save(number, bank, person, amount.toLong(), millis, direction)
    })
}

@Composable
private fun SimpleDialog(title: String, close: () -> Unit, body: @Composable ColumnScope.() -> Unit, save: () -> Unit) {
    AlertDialog(onDismissRequest = close, title = { Text(title) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = body) },
        confirmButton = { TextButton(onClick = save) { Text("ذخیره") } },
        dismissButton = { TextButton(onClick = close) { Text("انصراف") } })
}

@Composable
private fun Field(value: String, onValue: (String) -> Unit, label: String, numeric: Boolean = false, dateInput: Boolean = false) {
    OutlinedTextField(value = value, onValueChange = { onValue(it.filter { c -> !numeric || c.isDigit() }) },
        label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else if (dateInput) KeyboardType.Decimal else KeyboardType.Text),
        modifier = Modifier.fillMaxWidth())
}

private fun money(rial: Long, toman: Boolean): String =
    DecimalFormat("#,##0").format(if (toman) rial / 10 else rial) + if (toman) " تومان" else " ریال"

private fun jalaliOf(millis: Long): Triple<Int, Int, Int> {
    val c = Calendar.getInstance().apply { timeInMillis = millis }
    return Jalali.toJalali(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
}
private fun monthKey(millis: Long = System.currentTimeMillis()): String {
    val (y, m, _) = jalaliOf(millis)
    return String.format(Locale.US, "%04d/%02d", y, m)
}
private fun dateFa(millis: Long): String {
    val (y, m, d) = jalaliOf(millis)
    return String.format(Locale.US, "%04d/%02d/%02d", y, m, d)
}
private fun parseJalaliDate(value: String): Long? {
    val normalized = value.trim().map { ch ->
        when (ch) {
            in '\u06F0'..'\u06F9' -> '0' + (ch - '\u06F0')
            in '\u0660'..'\u0669' -> '0' + (ch - '\u0660')
            '-', '.', '\u060C' -> '/'
            else -> ch
        }
    }.joinToString("")
    val parts = normalized.split("/")
    if (parts.size != 3) return null
    val jy = parts[0].toIntOrNull() ?: return null
    val jm = parts[1].toIntOrNull() ?: return null
    val jd = parts[2].toIntOrNull() ?: return null
    if (jy !in 1300..1700 || jm !in 1..12 || jd !in 1..31) return null
    val (gy, gm, gd) = Jalali.toGregorian(jy, jm, jd)
    if (Jalali.toJalali(gy, gm, gd) != Triple(jy, jm, jd)) return null
    return Calendar.getInstance().apply {
        clear()
        set(gy, gm - 1, gd, 12, 0, 0)
    }.timeInMillis
}
private fun startOfToday(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis
private fun statusFa(s: String): String = when (s) {
    "PENDING" -> "در انتظار"
    "PAID" -> "وصول / پرداخت شد"
    "BOUNCED" -> "برگشت خورده"
    else -> "لغو شده"
}


class CheckReminderWorker(
    appContext: android.content.Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val until = System.currentTimeMillis() + 3L * 24L * 60L * 60L * 1000L
        val dueChecks = FinanceDatabase.get(applicationContext).dao().checksDueBy(until)
        if (dueChecks.isNotEmpty()) {
            val manager = applicationContext.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val channelId = "asia_check_reminders"
            if (Build.VERSION.SDK_INT >= 26) {
                val channel = android.app.NotificationChannel(
                    channelId, "یادآوری سررسید چک", android.app.NotificationManager.IMPORTANCE_DEFAULT
                )
                manager.createNotificationChannel(channel)
            }
            val lines = dueChecks.take(5).joinToString("\n") {
                "چک ${it.number} - ${it.person} - ${DecimalFormat("#,##0").format(it.amountRial)} ریال"
            }
            val notification = androidx.core.app.NotificationCompat.Builder(applicationContext, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("یادآوری چک‌های سررسید")
                .setContentText("${dueChecks.size} چک تا سه روز آینده یا معوق دارید")
                .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(lines))
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
            try {
                androidx.core.app.NotificationManagerCompat.from(applicationContext).notify(802, notification)
            } catch (_: SecurityException) {
                // Notification permission may have been denied by the user.
            }
        }
        return Result.success()
    }
}

private fun ComponentActivity.scheduleCheckReminders() {
    val request = PeriodicWorkRequestBuilder<CheckReminderWorker>(24, TimeUnit.HOURS)
        .build()
    WorkManager.getInstance(this).enqueueUniquePeriodicWork(
        "asia_check_due_reminder",
        ExistingPeriodicWorkPolicy.KEEP,
        request
    )
    val firstRun = OneTimeWorkRequestBuilder<CheckReminderWorker>().build()
    WorkManager.getInstance(this).enqueueUniqueWork(
        "asia_check_due_reminder_initial",
        ExistingWorkPolicy.KEEP,
        firstRun
    )
}
