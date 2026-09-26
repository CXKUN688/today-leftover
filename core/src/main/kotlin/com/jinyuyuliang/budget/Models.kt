package com.jinyuyuliang.budget
import kotlinx.serialization.Serializable
@Serializable enum class Kind { EXPENSE, REFUND, INCOME, TRANSFER }
@Serializable data class Entry(val id:String,val date:String,val amount:Long,val kind:Kind=Kind.EXPENSE,val category:String="鍏朵粬",val merchant:String="",val note:String="",val method:String="鍏朵粬",val reserveId:String?=null,val originalId:String?=null,val addToBudget:Boolean=false,val sourceKey:String?=null)
@Serializable data class MonthBudget(val month:String,val amount:Long,val openingSpent:Long=0,val cutoff:String?=null)
@Serializable data class Reserve(val id:String,val month:String,val name:String,val amount:Long,val recurring:Boolean=false)
@Serializable data class DaySnapshot(val date:String,val allowance:Long,val highest:Int=0)
@Serializable data class AlertEvent(val id:String,val date:String,val level:Int,val message:String,val delivered:Boolean=false)
@Serializable data class LedgerState(val budgets:List<MonthBudget> = emptyList(),val entries:List<Entry> = emptyList(),val reserves:List<Reserve> = emptyList(),val days:List<DaySnapshot> = emptyList(),val alerts:List<AlertEvent> = emptyList(),val alertEnabled:Boolean=true,val thresholdNumerator:Int=2,val thresholdDenominator:Int=3,val candidates:List<Candidate> = emptyList(),val categories:List<String> = listOf("餐饮","交通","购物","娱乐","学习","住宿","医疗","其他"),val rules:List<Rule> = emptyList())
data class Summary(val available:Long,val daily:Long,val daysLeft:Int,val todayUsed:Long,val todayRemaining:Long,val reserved:Long=0,val spent:Long=0)
sealed interface Mutation { data class SaveEntry(val entry:Entry):Mutation; data class DeleteEntry(val id:String):Mutation; data class SaveBudget(val budget:MonthBudget):Mutation; data class SaveReserve(val reserve:Reserve):Mutation; data class DeleteReserve(val id:String):Mutation; data class AlertConfig(val enabled:Boolean,val n:Int,val d:Int):Mutation; data class AddCandidates(val items:List<Candidate>):Mutation; data class Accept(val entries:List<Entry>,val candidateIds:List<String>):Mutation; data class Dismiss(val id:String):Mutation; data class Categories(val names:List<String>,val rules:List<Rule>):Mutation; data class Delivered(val id:String):Mutation }

@Serializable data class Rule(val keyword:String,val category:String)
