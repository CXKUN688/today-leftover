package com.jinyuyuliang.budget.ui
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jinyuyuliang.budget.*
import java.time.LocalDate
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun EditorFrame(title:String,onDismiss:()->Unit,content:@Composable ColumnScope.()->Unit){Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)){Surface(Modifier.fillMaxSize(),color=Soft){Scaffold(containerColor=Soft,topBar={TopAppBar(title={Text(title)},navigationIcon={IconButton(onClick=onDismiss){Icon(Icons.Outlined.Close,"关闭")}},colors=TopAppBarDefaults.topAppBarColors(containerColor=Soft))}){padding->Column(Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(15.dp),content=content)}}}}
@Composable fun Field(value:String,onChange:(String)->Unit,label:String,money:Boolean=false){OutlinedTextField(value=value,onValueChange=onChange,label={Text(label)},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp),keyboardOptions=KeyboardOptions(keyboardType=if(money)KeyboardType.Decimal else KeyboardType.Text),singleLine=true)}
@Composable fun Choice(label:String,selected:String,options:List<Pair<String,String>>,onPick:(String)->Unit){var expanded by remember{mutableStateOf(false)};Column{Caption(label);Box{OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth()){Text(options.find{it.first==selected}?.second?:"请选择")};DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}){options.forEach{(id,name)->DropdownMenuItem(text={Text(name)},onClick={onPick(id);expanded=false})}}}}}
@Composable fun EntryEditor(initial:Entry,s:LedgerState,candidateId:String?,onDismiss:()->Unit,onSave:(Entry)->Unit,onDelete:()->Unit){var amount by remember{mutableStateOf(if(initial.amount==0L)""else Money.format(initial.amount))};var kind by remember{mutableStateOf(initial.kind)};var category by remember{mutableStateOf(initial.category)};var merchant by remember{mutableStateOf(initial.merchant)};var note by remember{mutableStateOf(initial.note)};var method by remember{mutableStateOf(initial.method)};var date by remember{mutableStateOf(initial.date)};var reserve by remember{mutableStateOf(initial.reserveId.orEmpty())};var origin by remember{mutableStateOf(initial.originalId.orEmpty())};var income by remember{mutableStateOf(initial.addToBudget)};var error by remember{mutableStateOf<String?>(null)};var confirmDelete by remember{mutableStateOf(false)};var saving by remember{mutableStateOf(false)}
 EditorFrame(if(candidateId!=null)"核对账单"else if(s.entries.any{it.id==initial.id})"编辑记录"else"记一笔",onDismiss){
  Field(amount,{amount=it;error=null},"金额（元）",true)
  Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){Kind.entries.forEach{k->FilterChip(selected=kind==k,onClick={kind=k},label={Text(kindName(k))})}}
  if(kind==Kind.TRANSFER)Caption("仅适用于你自己账户之间转账。转给别人购买商品或服务，应记为支出。")
  Choice("消费分类",category,(s.categories+category).distinct().map{it to it}){category=it}
  Field(merchant,{merchant=it},"商户 / 用在什么地方")
  Field(note,{note=it},"用途备注")
  Choice("付款方式",method,listOf("微信","支付宝","现金","银行卡","其他").map{it to it}){method=it}
  Field(date,{date=it},"日期 YYYY-MM-DD")
  if(kind==Kind.EXPENSE)Choice("使用预留资金（可选）",reserve,listOf("" to "不关联预留")+s.reserves.filter{it.month==date.take(7)}.map{it.id to (it.name+" · ¥ "+Money.format(it.amount))}){reserve=it}
  if(kind==Kind.REFUND){Choice("关联原消费（可选）",origin,listOf("" to "未关联原消费，人工确认退款")+s.entries.filter{it.kind==Kind.EXPENSE&&it.date<=date}.sortedByDescending{it.date}.map{it.id to (it.date+" "+it.merchant.ifBlank{it.category}+" ¥"+Money.format(it.amount))}){origin=it};Caption("关联退款累计不能超过原消费，退款按到账月份回补预算。")}
  if(kind==Kind.INCOME)Row{Checkbox(checked=income,onCheckedChange={income=it});Text("将这笔收入加入本月生活费",Modifier.padding(top=12.dp))}
  if(candidateId!=null)Caption("请检查是否与已记账记录重复。确认后才会影响预算。")
  error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
  Button(onClick={try{val value=Money.parse(amount);LocalDate.parse(date);require(date<=LocalDate.now().toString()){"不能记录未来交易"};val e=initial.copy(amount=value,kind=kind,category=category,merchant=merchant.trim(),note=note.trim(),method=method,date=date,reserveId=if(kind==Kind.EXPENSE)reserve.ifBlank{null}else null,originalId=if(kind==Kind.REFUND)origin.ifBlank{null}else null,addToBudget=kind==Kind.INCOME&&income);Ledger.validate(s.copy(entries=s.entries.filterNot{it.id==e.id}+e));onSave(e)}catch(e:Exception){error=e.message?:"请检查输入"}},modifier=Modifier.fillMaxWidth().height(52.dp)){Text(if(candidateId!=null)"确认入账"else"保存记录",fontSize=17.sp)}
  if(candidateId==null&&s.entries.any{it.id==initial.id})TextButton(onClick={confirmDelete=true},modifier=Modifier.fillMaxWidth()){Text("删除这笔记录",color=MaterialTheme.colorScheme.error)}
  Spacer(Modifier.height(24.dp))
 }
 if(confirmDelete)AlertDialog(onDismissRequest={confirmDelete=false},title={Text("删除这笔记录？")},text={Text("删除后会重新计算预算。有关联退款时，请先处理退款记录。")},confirmButton={TextButton(onClick={confirmDelete=false;onDelete()}){Text("确认删除")}},dismissButton={TextButton(onClick={confirmDelete=false}){Text("取消")}})
}
@Composable fun BudgetEditor(initial:MonthBudget,onDismiss:()->Unit,onSave:(MonthBudget)->Unit){var amount by remember{mutableStateOf(Money.format(initial.amount))};var spent by remember{mutableStateOf(Money.format(initial.openingSpent))};var cutoff by remember{mutableStateOf(initial.cutoff?:LocalDate.now().minusDays(1).toString())};var error by remember{mutableStateOf<String?>(null)};EditorFrame("本月生活费",onDismiss){Caption(initial.month+" · 修改后更新当日参考额度及每日可花预测");Field(amount,{amount=it},"本月生活费（元）",true);Field(spent,{spent=it},"开始使用前已经花了（元）",true);Caption("如果已经导入历史明细，这里只填写未被明细覆盖的金额，避免重复扣减。");Field(cutoff,{cutoff=it},"此前已花覆盖到哪一天 YYYY-MM-DD");error?.let{Text(it,color=MaterialTheme.colorScheme.error)};Button(onClick={try{val a=Money.parse(amount,true);val p=Money.parse(spent,true);if(p>0){LocalDate.parse(cutoff);require(cutoff.startsWith(initial.month)&&cutoff<=LocalDate.now().toString()){"截止日期须属于本月且不能在未来"}};onSave(initial.copy(amount=a,openingSpent=p,cutoff=if(p>0)cutoff else null))}catch(e:Exception){error=e.message}},modifier=Modifier.fillMaxWidth()){Text("保存预算")}}}
@Composable fun ReserveEditor(initial:Reserve,onDismiss:()->Unit,onSave:(Reserve)->Unit){var name by remember{mutableStateOf(initial.name)};var amount by remember{mutableStateOf(if(initial.amount==0L)""else Money.format(initial.amount))};var recurring by remember{mutableStateOf(initial.recurring)};var error by remember{mutableStateOf<String?>(null)};EditorFrame("预留一笔钱",onDismiss){Field(name,{name=it},"名称，如房租、话费、储蓄");Field(amount,{amount=it},"预留金额（元）",true);Row{Checkbox(checked=recurring,onCheckedChange={recurring=it});Text("新月份自动沿用此预留",Modifier.padding(top=12.dp))};Caption("这笔钱会从普通可用预算中先扣除。实际付款时关联预留项，即可避免重复扣款。");error?.let{Text(it,color=MaterialTheme.colorScheme.error)};Button(onClick={try{require(name.isNotBlank()){"请填写名称"};onSave(initial.copy(name=name.trim(),amount=Money.parse(amount),recurring=recurring))}catch(e:Exception){error=e.message}},modifier=Modifier.fillMaxWidth()){Text("保存预留")}}}
@Composable fun CategoriesEditor(s:LedgerState,onDismiss:()->Unit,onSave:(List<String>,List<Rule>)->Unit){var names by remember{mutableStateOf(s.categories.joinToString("\n"))};var rules by remember{mutableStateOf(s.rules.joinToString("\n"){it.keyword+"="+it.category})};var error by remember{mutableStateOf<String?>(null)};EditorFrame("分类与商户规则",onDismiss){Caption("每行一个分类。已有账目保留原分类，不因改名自动变化。");OutlinedTextField(names,{names=it},label={Text("分类名称")},minLines=6,modifier=Modifier.fillMaxWidth());Caption("商户关键词规则，每行“关键词=分类”，例如“食堂=餐饮”。仅用于后续候选记录。");OutlinedTextField(rules,{rules=it},label={Text("自动分类规则")},minLines=4,modifier=Modifier.fillMaxWidth());error?.let{Text(it,color=MaterialTheme.colorScheme.error)};Button(onClick={try{val categories=names.lines().map{it.trim()}.filter{it.isNotBlank()}.distinct();require(categories.isNotEmpty()){"至少保留一个分类"};val list=rules.lines().filter{it.isNotBlank()}.map{val parts=it.split('=',limit=2);require(parts.size==2&&parts[0].isNotBlank()&&parts[1].trim() in categories){"规则格式应为 关键词=已有分类"};Rule(parts[0].trim(),parts[1].trim())};onSave(categories,list)}catch(e:Exception){error=e.message}},modifier=Modifier.fillMaxWidth()){Text("保存分类和规则")}}}
