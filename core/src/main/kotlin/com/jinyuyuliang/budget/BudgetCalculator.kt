package com.jinyuyuliang.budget
import java.time.LocalDate
import java.time.YearMonth
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Locale
object Money {
 const val MAX=1_000_000_000_000L
 fun parse(s:String,allowZero:Boolean=false):Long { require(Regex("[0-9]+(\\.[0-9]{1,2})?").matches(s.trim())){"请输入最多两位小数的金额"}; val v=try{BigDecimal(s.trim()).movePointRight(2).longValueExact()}catch(e:Exception){throw IllegalArgumentException("金额过大")}; require(v in (if(allowZero)0L else 1L)..MAX){"金额须大于零且不超过100亿元"};return v }
 fun format(v:Long):String=BigDecimal.valueOf(v,2).setScale(2).toPlainString()
}
object BudgetCalculator {
 private fun sum(e:List<Entry>)=e.fold(0L){a,x->Math.addExact(a,x.amount)}
 fun reserveRemaining(s:LedgerState,r:Reserve,date:String):Long {val expense=s.entries.filter{it.kind==Kind.EXPENSE&&it.reserveId==r.id&&it.date<=date};val ids=expense.map{it.id}.toSet();val refund=s.entries.filter{it.kind==Kind.REFUND&&it.originalId in ids&&it.date.startsWith(r.month)&&it.date<=date};return (r.amount-sum(expense)+sum(refund)).coerceAtLeast(0)}
 fun todayUsed(s:LedgerState,date:String):Long {
  val expenses=s.entries.filter{it.date==date&&it.kind==Kind.EXPENSE}; val refunds=s.entries.filter{it.date==date&&it.kind==Kind.REFUND}
  fun net(e:List<Entry>):Long {val ids=e.map{it.id}.toSet();return (sum(e)-sum(refunds.filter{it.originalId in ids})).coerceAtLeast(0)}
  var ordinary=net(expenses.filter{it.reserveId==null})
  for(r in s.reserves.filter{it.month==date.take(7)}) { val today=net(expenses.filter{it.reserveId==r.id});val before=s.entries.filter{it.kind==Kind.EXPENSE&&it.reserveId==r.id&&it.date<date}; val ids=before.map{it.id}.toSet();val returned=s.entries.filter{it.kind==Kind.REFUND&&it.originalId in ids&&it.date<=date&&it.date.startsWith(r.month)};val pool=(r.amount-sum(before)+sum(returned)).coerceAtLeast(0);ordinary=Math.addExact(ordinary,(today-pool).coerceAtLeast(0)) };return ordinary
 }
 fun calculate(s:LedgerState,date:String):Summary {
  val d=LocalDate.parse(date);val month=date.take(7);val budget=s.budgets.find{it.month==month};val entries=s.entries.filter{it.date.startsWith(month)&&it.date<=date};val spent=sum(entries.filter{it.kind==Kind.EXPENSE});val returned=sum(entries.filter{it.kind==Kind.REFUND});val income=sum(entries.filter{it.kind==Kind.INCOME&&it.addToBudget});val reserved=s.reserves.filter{it.month==month}.fold(0L){a,r->Math.addExact(a,reserveRemaining(s,r,date))};val available=Math.subtractExact(Math.addExact(Math.addExact(budget?.amount?:0,income),returned),Math.addExact(Math.addExact(spent,budget?.openingSpent?:0),reserved));val days=d.lengthOfMonth()-d.dayOfMonth+1;val used=todayUsed(s,date);val allowance=s.days.find{it.date==date}?.allowance?:Math.floorDiv(available+used,days.toLong());return Summary(available,Math.floorDiv(available,days.toLong()),days,used,allowance-used,reserved,spent)
 }
}
object Alerts {
 fun level(allowance:Long,used:Long,n:Int,d:Int):Int {if(used<=0)return 0;if(allowance<=0)return 2;if(used>=allowance)return 2;return if(BigInteger.valueOf(used)*BigInteger.valueOf(d.toLong())>=BigInteger.valueOf(allowance)*BigInteger.valueOf(n.toLong()))1 else 0}
}
