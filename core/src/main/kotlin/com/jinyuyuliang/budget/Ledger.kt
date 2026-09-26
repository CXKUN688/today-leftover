package com.jinyuyuliang.budget
import java.time.LocalDate
import java.time.YearMonth
object Ledger {
 fun ensureDay(original:LedgerState,date:String):LedgerState {
  LocalDate.parse(date); var s=original;val month=date.take(7)
  if(s.budgets.none{it.month==month}) {val prev=YearMonth.parse(month).minusMonths(1).toString();val old=s.budgets.find{it.month==prev};if(old!=null){s=s.copy(budgets=s.budgets+MonthBudget(month,old.amount),reserves=s.reserves+s.reserves.filter{it.month==prev&&it.recurring}.map{it.copy(id=it.id+":"+month,month=month)})}}
  if(s.days.any{it.date==date})return s
  val before=s.copy(entries=s.entries.filter{it.date!=date});val base=BudgetCalculator.calculate(before,date).daily
  return s.copy(days=s.days+DaySnapshot(date,base))
 }
 fun validate(s:LedgerState,date:String=LocalDate.now().toString()) {
  require(s.entries.size<=100000&&s.candidates.size<=100000){"记录数量超过上限"}
  require(s.entries.map{it.id}.distinct().size==s.entries.size){"账目ID重复"};require(s.reserves.map{it.id}.distinct().size==s.reserves.size){"预留ID重复"};require(s.budgets.map{it.month}.distinct().size==s.budgets.size){"月份重复"};require(s.days.map{it.date}.distinct().size==s.days.size){"日期基准重复"}
  require(s.entries.mapNotNull{it.sourceKey}.distinct().size==s.entries.mapNotNull{it.sourceKey}.size){"来源重复"}
  require(s.thresholdNumerator to s.thresholdDenominator in listOf(1 to 2,2 to 3,4 to 5)){"预警比例不支持"}
  s.budgets.forEach{YearMonth.parse(it.month);require(it.amount in 0..Money.MAX&&it.openingSpent in 0..Money.MAX){"预算金额无效"};it.cutoff?.let{d->LocalDate.parse(d);require(d.take(7)==it.month&&d<=date){"此前已花截止日期无效"}};require(it.openingSpent==0L||it.cutoff!=null){"请填写此前已花覆盖到哪一天"}}
  s.reserves.forEach{YearMonth.parse(it.month);require(it.amount in 1..Money.MAX&&it.name.isNotBlank()){ "预留金额或名称无效" }}
  val byId=s.entries.associateBy{it.id}
  s.entries.forEach{e->require(e.id.isNotBlank()&&e.amount in 1..Money.MAX){"账目金额无效"};LocalDate.parse(e.date);require(e.date<=date){"不能记录未来交易"};require(e.category.isNotBlank()&&e.note.length<=2000&&e.merchant.length<=300){"分类为空或文字过长"};if(e.reserveId!=null){require(e.kind==Kind.EXPENSE){"只有支出可以关联预留"};require(s.reserves.any{it.id==e.reserveId&&it.month==e.date.take(7)}){"预留项不存在或不属于该月份"}};if(e.originalId!=null){val origin=byId[e.originalId];require(e.kind==Kind.REFUND&&origin?.kind==Kind.EXPENSE&&origin.date<=e.date){"原消费不存在或退款日期不正确"}}}
  s.entries.filter{it.kind==Kind.REFUND&&it.originalId!=null}.groupBy{it.originalId}.forEach{(id,refunds)->require(refunds.sumOf{it.amount}<=byId.getValue(id!!).amount){"退款总额超过原消费，请先修改退款记录"}}
  require(s.categories.isNotEmpty()&&s.categories.all{it.isNotBlank()&&it.length<=30}){"分类无效"};require(s.rules.all{it.keyword.isNotBlank()&&it.category in s.categories}){"分类规则无效"}
 }
 private fun save(s:LedgerState,e:Entry):LedgerState {if(e.sourceKey!=null&&s.entries.any{it.sourceKey==e.sourceKey&&it.id!=e.id})return s;return s.copy(entries=s.entries.filterNot{it.id==e.id}+e)}
 fun apply(original:LedgerState,m:Mutation,date:String):LedgerState {
  var s=ensureDay(original,date)
  s=when(m){
   is Mutation.SaveEntry->save(s,m.entry)
   is Mutation.DeleteEntry->s.copy(entries=s.entries.filterNot{it.id==m.id})
   is Mutation.SaveBudget->s.copy(budgets=s.budgets.filterNot{it.month==m.budget.month}+m.budget)
   is Mutation.SaveReserve->s.copy(reserves=s.reserves.filterNot{it.id==m.reserve.id}+m.reserve)
   is Mutation.DeleteReserve->s.copy(reserves=s.reserves.filterNot{it.id==m.id})
   is Mutation.AlertConfig->s.copy(alertEnabled=m.enabled,thresholdNumerator=m.n,thresholdDenominator=m.d)
   is Mutation.AddCandidates->{val ids=s.candidates.map{it.id}.toSet();s.copy(candidates=s.candidates+m.items.filter{it.id !in ids}.distinctBy{it.id}.map{c->val e=c.entry?.let{entry->val rule=s.rules.firstOrNull{(entry.merchant+entry.note).contains(it.keyword,ignoreCase=true)};if(rule!=null)entry.copy(category=rule.category)else entry};val duplicate=e?.sourceKey!=null&&s.entries.any{it.sourceKey==e.sourceKey};val near=e!=null&&s.entries.any{it.amount==e.amount&&it.date==e.date&&it.kind==e.kind};val overlap=e!=null&&s.budgets.any{it.month==e.date.take(7)&&it.openingSpent>0&&it.cutoff!=null&&e.date<=it.cutoff};c.copy(entry=e,duplicate=duplicate,needsReview=c.needsReview||near||overlap||(e!=null&&e.category=="其他"),issue=when{duplicate->"已存在相同来源记录";overlap->"与此前已花重叠：请先调整预算设置中的此前已花";near->"存在同日同额记录，请检查是否重复";e!=null&&e.category=="其他"&&c.issue.isBlank()->"分类尚未确定，请核对";else->c.issue})})}
   is Mutation.Accept->{var n=s;for(e in m.entries){require(s.budgets.none{it.month==e.date.take(7)&&it.openingSpent>0&&it.cutoff!=null&&e.date<=it.cutoff}){"请先调整此前已花，避免重复扣减"};n=save(n,e)};n.copy(candidates=n.candidates.filterNot{it.id in m.candidateIds})}
   is Mutation.Dismiss->s.copy(candidates=s.candidates.filterNot{it.id==m.id})
   is Mutation.Categories->s.copy(categories=m.names.distinct(),rules=m.rules)
   is Mutation.Delivered->s.copy(alerts=s.alerts.map{if(it.id==m.id)it.copy(delivered=true)else it})
  }
  validate(s,date)
  if(m is Mutation.SaveBudget||m is Mutation.SaveReserve||m is Mutation.DeleteReserve){val highest=s.days.find{it.date==date}?.highest?:0;s=ensureDay(s.copy(days=s.days.filterNot{it.date==date}),date);s=s.copy(days=s.days.map{if(it.date==date)it.copy(highest=highest)else it})}
  if(m is Mutation.AddCandidates||m is Mutation.Dismiss||m is Mutation.Delivered||m is Mutation.Categories)return s
  val b=BudgetCalculator.calculate(s,date);val snap=s.days.first{it.date==date};val level=if(s.alertEnabled)Alerts.level(snap.allowance,b.todayUsed,s.thresholdNumerator,s.thresholdDenominator)else 0
  if(level>snap.highest){val text=if(snap.allowance<=0)"可用预算不足，今天已使用 ¥"+Money.format(b.todayUsed) else if(level==1)"今天已花 ¥"+Money.format(b.todayUsed)+"，今日额度还剩 ¥"+Money.format(b.todayRemaining) else if(b.todayRemaining==0L)"今日参考额度已用完" else "今日参考额度已超出 ¥"+Money.format(-b.todayRemaining);s=s.copy(days=s.days.map{if(it.date==date)it.copy(highest=level)else it},alerts=s.alerts+AlertEvent(date+":"+level,date,level,text))}
  return s
 }
}
