package com.jinyuyuliang.budget.ui
import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jinyuyuliang.budget.*
import com.jinyuyuliang.budget.data.BudgetStore
import kotlinx.coroutines.*
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import java.io.InputStream

@Composable fun BudgetApp()=BudgetTheme {
 val context=LocalContext.current;val store=remember{BudgetStore.get(context)};val state by store.state.collectAsState();val loadError by store.error.collectAsState();val scope=rememberCoroutineScope();val snack=remember{SnackbarHostState()};var tab by remember{mutableIntStateOf(0)};var today by remember{mutableStateOf(LocalDate.now().toString())};var edit by remember{mutableStateOf<Entry?>(null)};var candidateId by remember{mutableStateOf<String?>(null)};var budgetDialog by remember{mutableStateOf(false)};var reserveDialog by remember{mutableStateOf<Reserve?>(null)};var categoriesDialog by remember{mutableStateOf(false)};var restoreRaw by remember{mutableStateOf<String?>(null)};var importStatus by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)}
 fun work(block:suspend ()->Unit){scope.launch{try{block()}catch(e:Exception){snack.showSnackbar(e.message?:"操作失败，请重试")}}}
 fun mutate(m:Mutation){work{store.apply(m)}}
 val lifecycle=LocalLifecycleOwner.current.lifecycle
 DisposableEffect(lifecycle){val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_RESUME){today=LocalDate.now().toString();work{store.refresh()}}};lifecycle.addObserver(observer);onDispose{lifecycle.removeObserver(observer)}}
 LaunchedEffect(Unit){while(true){delay(30000);val date=LocalDate.now().toString();if(date!=today){today=date;store.refresh()}}}
 val notifyPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->work{snack.showSnackbar(if(granted)"已开启系统预警通知" else "系统通知未开启，应用内仍显示预警")}}
 val importFile=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)work{busy=true;try{val report=withContext(Dispatchers.IO){val name=context.contentResolver.query(uri,null,null,null,null)?.use{cursor->if(cursor.moveToFirst())cursor.getString(cursor.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME))else ""}.orEmpty();val bytes=context.contentResolver.openInputStream(uri)?.use{readBounded(it)}?:error("无法读取文件");BillParser.parse(bytes,name)};report.error?.let{error(it)};store.apply(Mutation.AddCandidates(report.candidates));importStatus="已解析 ${report.candidates.size} 行，请预览后入账";tab=2}finally{busy=false}}}
 val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->if(uri!=null)work{val text=store.export();withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri,"wt")?.use{it.write(text.toByteArray())}?:error("无法写入备份")};snack.showSnackbar("备份已保存，文件包含财务信息，请妥善保管")}}
 val restore=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)work{val text=withContext(Dispatchers.IO){context.contentResolver.openInputStream(uri)?.use{readBounded(it).toString(Charsets.UTF_8)}?:error("无法读取备份")};withContext(Dispatchers.IO){Backup.decode(text)};restoreRaw=text}}
 Scaffold(containerColor=Soft,snackbarHost={SnackbarHost(snack)},bottomBar={NavigationBar(containerColor=Color.White){listOf("首页" to Icons.Outlined.Home,"明细" to Icons.Outlined.ReceiptLong,"账单" to Icons.Outlined.Inbox,"设置" to Icons.Outlined.Tune).forEachIndexed{i,item->NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Icon(item.second,item.first)},label={Text(item.first)})}}},floatingActionButton={if(tab<2&&state!=null)ExtendedFloatingActionButton(onClick={candidateId=null;edit=Entry(UUID.randomUUID().toString(),today,0)},icon={Icon(Icons.Outlined.Add,"记一笔")},text={Text("记一笔")},containerColor=Teal,contentColor=Color.White)}){padding->
  Box(Modifier.padding(padding).fillMaxSize()){
   val s=state
   if(s==null){Column(Modifier.padding(24.dp)){if(loadError!=null)Text(loadError!!,color=MaterialTheme.colorScheme.error)else CircularProgressIndicator()}}
   else when(tab){
    0->HomeScreen(s,today,onBudget={budgetDialog=true},onEntry={edit=it;candidateId=null})
    1->LedgerScreen(s,onEntry={edit=it;candidateId=null})
    2->BillsScreen(s,busy,importStatus,onImport={importFile.launch(arrayOf("text/*","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","application/octet-stream"))},onReview={c->edit=c.entry;candidateId=c.id},onDismiss={mutate(Mutation.Dismiss(it))},onAcceptReady={work{val ready=s.candidates.filter{it.entry!=null&&!it.needsReview&&!it.duplicate};store.apply(Mutation.Accept(ready.map{c->c.entry!!},ready.map{it.id}));snack.showSnackbar("已入账 ${ready.size} 笔")}},onNotificationAccess={context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))})
    3->SettingsScreen(s,today,onBudget={budgetDialog=true},onReserve={reserveDialog=it},onDeleteReserve={mutate(Mutation.DeleteReserve(it))},onCategories={categoriesDialog=true},onAlert={enabled,n,d->mutate(Mutation.AlertConfig(enabled,n,d))},onNotification={if(Build.VERSION.SDK_INT>=33)notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))},onExport={export.launch("今日余粮备份-$today.json")},onRestore={restore.launch(arrayOf("application/json","text/*","application/octet-stream"))})
   }
  }
 }
 val s=state
 if(s!=null){edit?.let{entry->EntryEditor(entry,s,candidateId,onDismiss={edit=null;candidateId=null},onSave={e->work{if(candidateId!=null)store.apply(Mutation.Accept(listOf(e),listOf(candidateId!!)))else store.apply(Mutation.SaveEntry(e));edit=null;candidateId=null}},onDelete={work{store.apply(Mutation.DeleteEntry(entry.id));edit=null}})}
 if(budgetDialog)BudgetEditor(s.budgets.find{it.month==today.take(7)}?:MonthBudget(today.take(7),0),onDismiss={budgetDialog=false},onSave={b->work{store.apply(Mutation.SaveBudget(b));budgetDialog=false}})
 reserveDialog?.let{ReserveEditor(it,onDismiss={reserveDialog=null},onSave={r->work{store.apply(Mutation.SaveReserve(r));reserveDialog=null}})}
 if(categoriesDialog)CategoriesEditor(s,onDismiss={categoriesDialog=false},onSave={names,rules->work{store.apply(Mutation.Categories(names,rules));categoriesDialog=false}})
 }
 restoreRaw?.let{raw->AlertDialog(onDismissRequest={restoreRaw=null},title={Text("用备份替换当前账本？")},text={Text("这会整体替换当前账目、预算和设置。建议先导出当前账本作为备份。")},confirmButton={TextButton(onClick={work{store.restore(raw);restoreRaw=null;snack.showSnackbar("账本已恢复")}}){Text("确认替换")}},dismissButton={TextButton(onClick={restoreRaw=null}){Text("取消")}})}
}
private fun readBounded(stream:InputStream):ByteArray{val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);while(true){val n=stream.read(buffer);if(n<0)break;require(out.size()+n<=20*1024*1024){"文件超过20MB，请拆分后再试"};out.write(buffer,0,n)};return out.toByteArray()}
@Composable fun Heading(title:String,sub:String){Column(Modifier.padding(top=12.dp,bottom=8.dp)){Text(title,fontSize=29.sp,fontWeight=FontWeight.Bold,color=Ink);Text(sub,color=Muted,fontSize=13.sp,modifier=Modifier.padding(top=6.dp))}}
@Composable fun Panel(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit){Card(modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)}}
@Composable fun Caption(text:String){Text(text,color=Muted,fontSize=13.sp)}
@Composable fun HomeScreen(s:LedgerState,today:String,onBudget:()->Unit,onEntry:(Entry)->Unit){val b=BudgetCalculator.calculate(s,today);val snap=s.days.find{it.date==today};val budget=s.budgets.find{it.month==today.take(7)};LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
 item{Heading("今日余粮",today.replace('-','.')+"  ·  给每一天留点余地")}
 if(budget==null)item{Panel{Text("从这个月的生活费开始",fontWeight=FontWeight.Bold,fontSize=20.sp);Caption("输入预算后，每一笔消费都会更新剩余每天可花金额。");Button(onClick=onBudget){Text("设置本月生活费")}}}
 item{Card(shape=RoundedCornerShape(26.dp),colors=CardDefaults.cardColors(containerColor=Teal)){Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Text(if(b.daily>=0)"剩余每天可花 · 含今天"else"剩余日均缺口",color=Color(0xFFD9EEE5));Text("¥ "+Money.format(kotlin.math.abs(b.daily)),fontSize=42.sp,fontWeight=FontWeight.Bold,color=Color.White);Text("本月还有 ${b.daysLeft} 天，每次记账后自动重算",color=Color(0xFFD9EEE5),fontSize=13.sp);HorizontalDivider(color=Color.White.copy(alpha=.2f));Text("可用余额  ¥ ${Money.format(b.available)}",color=Color.White,fontWeight=FontWeight.Medium)}}}
 item{Panel{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Column{Caption("今日额度剩余");Text("¥ "+Money.format(b.todayRemaining),fontSize=27.sp,fontWeight=FontWeight.Bold,color=if(b.todayRemaining<0)MaterialTheme.colorScheme.error else Ink)};Column(horizontalAlignment=Alignment.End){Caption("今日参考额度");Text("¥ "+Money.format(snap?.allowance?:0),fontSize=19.sp)}};LinearProgressIndicator(progress={if((snap?.allowance?:0)>0)(b.todayUsed.toFloat()/snap!!.allowance).coerceIn(0f,1f)else 0f},modifier=Modifier.fillMaxWidth().height(7.dp),color=if(b.todayRemaining<0)MaterialTheme.colorScheme.error else Teal,trackColor=Soft);Caption("今天已用普通额度 ¥ ${Money.format(b.todayUsed)}；预留款覆盖的支出不重复占用。");Caption("今日参考额度用于预警；上方每日可花随余额变化。")}}
 s.alerts.lastOrNull{it.date==today}?.let{a->item{Card(colors=CardDefaults.cardColors(containerColor=Color(0xFFFFEDD5)),shape=RoundedCornerShape(18.dp)){Row(Modifier.padding(18.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)){Icon(Icons.Outlined.NotificationsActive,"预警",tint=Color(0xFF8A4B10));Column{Text("消费提醒",fontWeight=FontWeight.Bold,color=Color(0xFF8A4B10));Text(a.message,color=Color(0xFF754311))}}}}}
 item{Panel{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Column{Caption("本月已支出");Text("¥ "+Money.format(b.spent),fontSize=20.sp)};Column{Caption("尚未使用的预留");Text("¥ "+Money.format(b.reserved),fontSize=20.sp)}};TextButton(onClick=onBudget){Text("调整本月预算")}}}
 item{Text("最近记录",fontSize=19.sp,fontWeight=FontWeight.Bold)}
 val entries=s.entries.sortedWith(compareByDescending<Entry>{it.date}.thenByDescending{it.id}).take(5)
 if(entries.isEmpty())item{Caption("还没有账目。点击右下角“记一笔”，记录第一笔消费。")}else items(entries,key={it.id}){EntryCard(it){onEntry(it)}}
 item{Spacer(Modifier.height(80.dp))}
}}
@Composable fun EntryCard(e:Entry,onClick:()->Unit){Card(onClick=onClick,shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){Row(Modifier.fillMaxWidth().padding(16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically){Surface(shape=RoundedCornerShape(12.dp),color=Color(0xFFEAF1EA)){Text(e.category.take(1),Modifier.padding(12.dp),color=Teal,fontWeight=FontWeight.Bold)};Column(Modifier.weight(1f)){Text(e.merchant.ifBlank{e.note.ifBlank{e.category}},maxLines=2,fontWeight=FontWeight.Medium);Caption(e.date+" · "+e.method+" · "+kindName(e.kind))};Text((if(e.kind==Kind.EXPENSE)"−"else if(e.kind==Kind.TRANSFER)""else"+")+Money.format(e.amount),fontWeight=FontWeight.SemiBold,color=if(e.kind==Kind.EXPENSE)Ink else Teal)}}}
fun kindName(k:Kind)=when(k){Kind.EXPENSE->"支出";Kind.REFUND->"退款";Kind.INCOME->"收入";Kind.TRANSFER->"内部转账"}
@Composable fun LedgerScreen(s:LedgerState,onEntry:(Entry)->Unit){var month by remember{mutableStateOf(YearMonth.now())};var category by remember{mutableStateOf("全部")};val entries=s.entries.filter{it.date.startsWith(month.toString())&&(category=="全部"||it.category==category)}.sortedByDescending{it.date};LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
 item{Heading("每一笔，都有数","按月回看消费，也给下个月一点参考")}
 item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){IconButton(onClick={month=month.minusMonths(1)}){Icon(Icons.Outlined.ChevronLeft,"上个月")};Text(month.toString(),fontSize=22.sp,fontWeight=FontWeight.Bold);IconButton(onClick={month=month.plusMonths(1)},enabled=month<YearMonth.now()){Icon(Icons.Outlined.ChevronRight,"下个月")}}}
 item{Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){(listOf("全部")+s.categories).forEach{c->FilterChip(selected=category==c,onClick={category=c},label={Text(c)})}}}
 item{Panel{Text("本月消费分布",fontWeight=FontWeight.Bold);val groups=entries.filter{it.kind==Kind.EXPENSE}.groupBy{it.category}.mapValues{it.value.sumOf{e->e.amount}}.toList().sortedByDescending{it.second};val total=groups.sumOf{it.second};if(groups.isEmpty())Caption("这个月还没有支出")else groups.forEach{(name,value)->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(name);Text("¥ "+Money.format(value))};LinearProgressIndicator(progress={if(total>0)value.toFloat()/total else 0f},modifier=Modifier.fillMaxWidth(),trackColor=Soft)};Caption("支出 ¥ ${Money.format(entries.filter{it.kind==Kind.EXPENSE}.sumOf{it.amount})} · 退款 ¥ ${Money.format(entries.filter{it.kind==Kind.REFUND}.sumOf{it.amount})}");Caption("收入 ¥ ${Money.format(entries.filter{it.kind==Kind.INCOME}.sumOf{it.amount})} · 内部转账不计消费")}}
 items(entries,key={it.id}){EntryCard(it){onEntry(it)}};item{Spacer(Modifier.height(90.dp))}
}}
@Composable fun BillsScreen(s:LedgerState,busy:Boolean,status:String,onImport:()->Unit,onReview:(Candidate)->Unit,onDismiss:(String)->Unit,onAcceptReady:()->Unit,onNotificationAccess:()->Unit){val context=LocalContext.current;val manager=context.getSystemService(android.app.NotificationManager::class.java);val health by ListenerMonitor.state.collectAsState();val access=health.authorized;var limit by remember{mutableIntStateOf(100)};LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
 item{Heading("账单助手","导入、核对，再放心入账")}
 item{Panel{Text("付款通知辅助识别",fontWeight=FontWeight.Bold);Caption(if(access)"系统通知使用权：已开启"else"系统通知使用权：未开启");Text(if(health.connected)"监听服务：已连接"else"监听服务：未连接",color=if(health.connected)Teal else MaterialTheme.colorScheme.error);Caption(health.message);if(access&&!health.connected)OutlinedButton(onClick={ListenerMonitor.reconnect(context)}){Text("重新连接监听")};Caption("若后台反复断开，请在手机应用设置中允许后台自启动，并将省电策略设为无限制。清理后台或强行停止可能中断监听。");Caption("仅处理微信、支付宝交易候选。目前模板待真机验证，识别结果需你确认；通知无法覆盖全部付款记录。");OutlinedButton(onClick=onNotificationAccess){Text(if(access)"管理通知使用权"else"开启通知使用权")}}}
 item{Panel{Text("微信 / 支付宝账单",fontWeight=FontWeight.Bold);Caption("选择导出的 CSV 或 XLSX 文件，支持预览和重复检查。请先解压；单文件不超过 20MB。");Button(onClick=onImport,enabled=!busy){Text(if(busy)"正在读取…"else"选择账单文件")};if(status.isNotBlank())Caption(status)}}
 val ready=s.candidates.count{it.entry!=null&&!it.needsReview&&!it.duplicate}
 item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text("待处理 ${s.candidates.size} 条",fontWeight=FontWeight.Bold);TextButton(onClick=onAcceptReady,enabled=ready>0){Text("入账 $ready 条明确记录")}}}
 if(s.candidates.isEmpty())item{Caption("没有待处理账单。导入文件或收到可识别的交易通知后，会显示在这里。")}
 items(s.candidates.take(limit),key={it.id}){c->Panel{c.entry?.let{e->Text("¥ ${Money.format(e.amount)} · ${kindName(e.kind)}",fontWeight=FontWeight.Bold);Caption(e.date+" · "+e.merchant.ifBlank{e.method});if(e.note.isNotBlank())Caption(e.note)};if(c.issue.isNotBlank())Text(c.issue,color=if(c.duplicate)Muted else Color(0xFF955D18),fontSize=13.sp);Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){if(c.entry!=null&&!c.duplicate)Button(onClick={onReview(c)}){Text("核对并入账")};TextButton(onClick={onDismiss(c.id)}){Text(if(c.duplicate)"跳过重复"else"忽略")}}}}
 if(s.candidates.size>limit)item{TextButton(onClick={limit+=100}){Text("显示更多")}};item{Spacer(Modifier.height(24.dp))}
}}
@Composable fun SettingsScreen(s:LedgerState,today:String,onBudget:()->Unit,onReserve:(Reserve)->Unit,onDeleteReserve:(String)->Unit,onCategories:()->Unit,onAlert:(Boolean,Int,Int)->Unit,onNotification:()->Unit,onExport:()->Unit,onRestore:()->Unit){var delete by remember{mutableStateOf<String?>(null)};LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
 item{Heading("按自己的节奏","预算可以调整，记录留在你的手机里")}
 item{Panel{Text("本月生活费",fontWeight=FontWeight.Bold);Text("¥ "+Money.format(s.budgets.find{it.month==today.take(7)}?.amount?:0),fontSize=28.sp);Button(onClick=onBudget){Text("设置预算 / 此前已花")};Caption("新月份沿用上月生活费。结余默认不结转；如需结转，请手动增加新月预算。")}}
 item{Panel{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text("消费预警",fontWeight=FontWeight.Bold);Switch(checked=s.alertEnabled,onCheckedChange={onAlert(it,s.thresholdNumerator,s.thresholdDenominator)})};Caption("当日参考额度使用到指定比例时提醒；用完额度再提醒一次。");Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(Triple("50%",1,2),Triple("三分之二",2,3),Triple("80%",4,5)).forEach{(label,n,d)->FilterChip(selected=s.thresholdNumerator==n&&s.thresholdDenominator==d,onClick={onAlert(s.alertEnabled,n,d)},label={Text(label)})}};OutlinedButton(onClick=onNotification){Text("开启系统预警通知")};Caption("未授权系统通知时，首页仍保留应用内预警。")}}
 item{Panel{Text("预留资金",fontWeight=FontWeight.Bold);Caption("房租、话费或储蓄先留出来。付款时关联预留项，不重复扣预算。");s.reserves.filter{it.month==today.take(7)}.forEach{r->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f).clickable{onReserve(r)}){Text(r.name+" · ¥ "+Money.format(r.amount));Caption("未使用 ¥ "+Money.format(BudgetCalculator.reserveRemaining(s,r,today)))};IconButton(onClick={delete=r.id}){Icon(Icons.Outlined.DeleteOutline,"删除预留")}}};OutlinedButton(onClick={onReserve(Reserve(UUID.randomUUID().toString(),today.take(7),"",0))}){Text("添加预留")}}}
 item{Panel{Text("分类与商户规则",fontWeight=FontWeight.Bold);Caption(s.categories.joinToString(" · "));OutlinedButton(onClick=onCategories){Text("管理分类和规则")}}}
 item{Panel{Text("本地备份",fontWeight=FontWeight.Bold);Caption("无需账号，离线保存。卸载会删除账本；请定期导出备份。备份文件含财务数据，请妥善保管。");Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){Button(onClick=onExport){Text("导出备份")};OutlinedButton(onClick=onRestore){Text("恢复备份")}}}}
 item{Caption("今日余粮 0.1.1 · 个人试用版\n微信 / 支付宝真实通知覆盖率及小米安卓17兼容性需真机验证。")}
 }
 delete?.let{id->AlertDialog(onDismissRequest={delete=null},title={Text("删除预留项？")},text={Text("有关联消费时，请先编辑消费解除关联。")},confirmButton={TextButton(onClick={onDeleteReserve(id);delete=null}){Text("删除")}},dismissButton={TextButton(onClick={delete=null}){Text("取消")}})}
}
