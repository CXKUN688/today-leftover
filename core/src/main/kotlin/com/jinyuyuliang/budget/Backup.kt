package com.jinyuyuliang.budget
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
@Serializable private data class Envelope(val version:Int,val state:LedgerState)
object Backup {
 val json=Json { encodeDefaults=true; ignoreUnknownKeys=false }
 fun encode(s:LedgerState):String=json.encodeToString(Envelope(1,s))
 fun decode(s:String):LedgerState {try{require(s.toByteArray().size<=20*1024*1024){"备份超过20MB"};val envelope=json.decodeFromString<Envelope>(s);require(envelope.version==1){"不支持的备份版本"};Ledger.validate(envelope.state);return envelope.state}catch(e:Exception){throw IllegalArgumentException("备份校验失败："+(e.message?:"格式错误"),e)}}
}
