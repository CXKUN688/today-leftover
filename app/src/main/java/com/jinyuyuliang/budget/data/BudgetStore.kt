package com.jinyuyuliang.budget.data
import android.content.Context
import androidx.room.*
import androidx.room.withTransaction
import com.jinyuyuliang.budget.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
@Entity(tableName="ledger") data class LedgerRow(@PrimaryKey val id:Int=1,val json:String)
@Dao interface BudgetDao { @Query("SELECT * FROM ledger WHERE id=1") suspend fun read():LedgerRow?; @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun put(row:LedgerRow) }
@Database(entities=[LedgerRow::class],version=1,exportSchema=false) abstract class BudgetDatabase:RoomDatabase(){abstract fun dao():BudgetDao}
class BudgetStore private constructor(context:Context) {
 private val db=Room.databaseBuilder(context.applicationContext,BudgetDatabase::class.java,"daily-budget.db").build()
 private val lock=Mutex();private val mutable=MutableStateFlow<LedgerState?>(null);val state=mutable.asStateFlow()
 private val errors=MutableStateFlow<String?>(null);val error=errors.asStateFlow()
 private val appContext=context.applicationContext
 val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
 init { scope.launch { runCatching { refresh() }.onFailure{errors.value="账本加载失败，请勿卸载应用："+it.message} } }
 private fun read(raw:LedgerRow?):LedgerState=raw?.let{Backup.decode(it.json)}?:LedgerState()
 suspend fun refresh()=withContext(Dispatchers.IO){lock.withLock{db.withTransaction{val s=Ledger.ensureDay(read(db.dao().read()),LocalDate.now().toString());db.dao().put(LedgerRow(json=Backup.encode(s)));mutable.value=s}}}
 suspend fun apply(m:Mutation)=withContext(Dispatchers.IO){lock.withLock{db.withTransaction{val s=Ledger.apply(read(db.dao().read()),m,LocalDate.now().toString());db.dao().put(LedgerRow(json=Backup.encode(s)));mutable.value=s}};if(m !is Mutation.Delivered)dispatch()}
 suspend fun restore(raw:String)=withContext(Dispatchers.IO){val restored=Backup.decode(raw);lock.withLock{db.withTransaction{val s=Ledger.ensureDay(restored.copy(alerts=restored.alerts.map{it.copy(delivered=true)}),LocalDate.now().toString());db.dao().put(LedgerRow(json=Backup.encode(s)));mutable.value=s}}}
 suspend fun export():String=withContext(Dispatchers.IO){lock.withLock{Backup.encode(read(db.dao().read()))}}
 private suspend fun dispatch(){val manager=appContext.getSystemService(android.app.NotificationManager::class.java);manager.createNotificationChannel(android.app.NotificationChannel("budget_alerts","生活费预警",android.app.NotificationManager.IMPORTANCE_DEFAULT));for(event in state.value?.alerts.orEmpty().filter{!it.delivered}){if(event.date==LocalDate.now().toString()&&manager.areNotificationsEnabled()){val intent=android.content.Intent(appContext,MainActivity::class.java);val pending=android.app.PendingIntent.getActivity(appContext,0,intent,android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE);val n=android.app.Notification.Builder(appContext,"budget_alerts").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("今日余粮 · 消费提醒").setContentText(event.message).setStyle(android.app.Notification.BigTextStyle().bigText(event.message)).setOnlyAlertOnce(true).setAutoCancel(true).setContentIntent(pending).build();try{manager.notify(event.id.hashCode(),n)}catch(_:SecurityException){}};apply(Mutation.Delivered(event.id))}}
 companion object { @Volatile private var instance:BudgetStore?=null;fun get(context:Context):BudgetStore=instance?:synchronized(this){instance?:BudgetStore(context).also{instance=it}} }
}
