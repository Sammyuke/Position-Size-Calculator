package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "trading_accounts")
data class TradingAccount(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val currency: String = "USD",
    val balance: Double = 10000.0,
    val riskPlanDescription: String = "Risk 1% per trade. If 2 consecutive losses occur, cut risk in half to 0.5% until a winning trade.",
    val defaultRiskPercentage: Double = 1.0,
    val isDemo: Boolean = false
)

@Entity(
    tableName = "trade_logs",
    foreignKeys = [
        ForeignKey(
            entity = TradingAccount::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("accountId")]
)
data class TradeLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val pairName: String,
    val assetClass: String, // "Forex", "Crypto", "Metals", "Oils", "Stocks"
    val riskAmount: Double,
    val riskPercent: Double,
    val entryPrice: Double,
    val stopLoss: Double, // Pip count for Forex, price distance for others
    val calculatedSize: Double, // Units size (e.g. 0.05 for BTC, 15000 for EUR/USD)
    val outcome: String = "PENDING", // "PENDING", "WIN", "LOSS"
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface TradingDao {
    @Query("SELECT * FROM trading_accounts ORDER BY id ASC")
    fun getAllAccounts(): Flow<List<TradingAccount>>

    @Query("SELECT * FROM trading_accounts WHERE id = :id LIMIT 1")
    suspend fun getAccountById(id: Long): TradingAccount?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: TradingAccount): Long

    @Update
    suspend fun updateAccount(account: TradingAccount)

    @Delete
    suspend fun deleteAccount(account: TradingAccount)

    @Query("SELECT * FROM trade_logs WHERE accountId = :accountId ORDER BY timestamp DESC")
    fun getLogsForAccount(accountId: Long): Flow<List<TradeLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: TradeLog): Long

    @Update
    suspend fun updateLog(log: TradeLog)

    @Delete
    suspend fun deleteLog(log: TradeLog)
}

@Database(entities = [TradingAccount::class, TradeLog::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tradingDao(): TradingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "position_size_calculator_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
