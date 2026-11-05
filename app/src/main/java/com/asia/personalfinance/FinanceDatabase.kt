package com.asia.personalfinance

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: String,
    val openingBalanceRial: Long
)

@Entity(
    tableName = "transactions",
    foreignKeys = [ForeignKey(
        entity = AccountEntity::class,
        parentColumns = ["id"],
        childColumns = ["accountId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("accountId")]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val title: String,
    val amountRial: Long,
    val type: String, // INCOME or EXPENSE
    val category: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "budgets", indices = [Index(value = ["category", "monthKey"], unique = true)])
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val limitRial: Long,
    val monthKey: String
)

@Entity(tableName = "debts")
data class DebtEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val person: String,
    val title: String,
    val originalRial: Long,
    val remainingRial: Long,
    val direction: String, // I_OWE or OWED_TO_ME
    val dueAt: Long?
)

@Entity(tableName = "checks")
data class CheckEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: String,
    val bank: String,
    val person: String,
    val amountRial: Long,
    val dueAt: Long,
    val direction: String, // RECEIVED or ISSUED
    val status: String = "PENDING" // PENDING, PAID, BOUNCED, CANCELLED
)

@Dao
interface FinanceDao {
    @Query("SELECT * FROM accounts ORDER BY id DESC")
    fun observeAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM transactions ORDER BY createdAt DESC")
    fun observeTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM budgets ORDER BY monthKey DESC, category")
    fun observeBudgets(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM debts ORDER BY dueAt")
    fun observeDebts(): Flow<List<DebtEntity>>

    @Query("SELECT * FROM checks ORDER BY dueAt")
    fun observeChecks(): Flow<List<CheckEntity>>

    @Insert
    suspend fun addAccount(item: AccountEntity): Long

    @Insert
    suspend fun addTransaction(item: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveBudget(item: BudgetEntity)

    @Insert
    suspend fun addDebt(item: DebtEntity)

    @Insert
    suspend fun addCheck(item: CheckEntity)

    @Update
    suspend fun updateCheck(item: CheckEntity)

    @Update
    suspend fun updateDebt(item: DebtEntity)

    @Delete
    suspend fun deleteAccount(item: AccountEntity)

    @Delete
    suspend fun deleteTransaction(item: TransactionEntity)

    @Delete
    suspend fun deleteBudget(item: BudgetEntity)

    @Delete
    suspend fun deleteDebt(item: DebtEntity)

    @Delete
    suspend fun deleteCheck(item: CheckEntity)

    @Query("SELECT * FROM checks WHERE status = 'PENDING' AND dueAt <= :until")
    suspend fun checksDueBy(until: Long): List<CheckEntity>

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun account(id: Long): AccountEntity?

    @Query("""
        SELECT COALESCE(SUM(CASE WHEN type='INCOME' THEN amountRial ELSE -amountRial END), 0)
        FROM transactions WHERE accountId = :accountId
    """)
    suspend fun transactionNet(accountId: Long): Long
}

@Database(
    entities = [AccountEntity::class, TransactionEntity::class, BudgetEntity::class, DebtEntity::class, CheckEntity::class],
    version = 1,
    exportSchema = false
)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun dao(): FinanceDao

    companion object {
        @Volatile private var INSTANCE: FinanceDatabase? = null

        fun get(context: Context): FinanceDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FinanceDatabase::class.java,
                    "asia_hesab.db"
                ).build().also { INSTANCE = it }
            }
    }
}
