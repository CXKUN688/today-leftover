package com.jinyuyuliang.budget
import kotlinx.serialization.Serializable
import java.nio.charset.CodingErrorAction
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.LocalDate
import java.util.zip.ZipInputStream
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
@Serializable data class Candidate(val id:String,val entry:Entry?=null,val issue:String="",val duplicate:Boolean=false,val needsReview:Boolean=false)
data class ParseReport(val candidates:List<Candidate>,val error:String?=null)
fun digest(s:String):String=MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString(""){"%02x".format(it)}
object BillParser {
 const val MAX=20*1024*1024
 fun parse(bytes:ByteArray,name:String):ParseReport=try {
  require(bytes.size<=MAX){"文件超过20MB，请按月份拆分"}
  val rows=if(name.endsWith(".xlsx",true))xlsx(bytes)else if(name.endsWith(".csv",true))csv(decode(bytes))else throw IllegalArgumentException("请选择已解压的 CSV 或 XLSX 账单")
  val hi=rows.indexOfFirst{r->r.any{it.contains("交易时间")||it.contains("交易创建时间")||it=="付款时间"}&&r.any{it.contains("金额")}}
  require(hi>=0){"未识别账单表头，请选择微信或支付宝导出的交易账单"};val h=rows[hi].map{it.trim().removePrefix("\uFEFF")}
  fun index(vararg names:String)=names.firstNotNullOfOrNull{n->h.indexOfFirst{it==n||it.startsWith(n+"(")||it.startsWith(n+"（")}.takeIf{it>=0}}?:-1
  val ti=index("交易时间","交易创建时间","付款时间");val ai=index("金额","交易金额");val di=index("收/支","收支","资金流向");val pi=index("交易对方","对方名称");val ni=index("商品","商品名称","商品说明");val si=index("当前状态","交易状态");val ii=index("交易单号","交易订单号","订单号");val ki=index("交易类型","交易分类");val mi=index("支付方式","收/付款方式","付款方式")
  require(ti>=0&&ai>=0&&di>=0){"账单缺少时间、金额或收支字段"};val platform=if(h.any{it=="交易订单号"||it=="交易分类"})"alipay" else "wechat"
  val result=rows.drop(hi+1).filter{r->r.any{it.isNotBlank()}}.mapIndexed{offset,row->
   val raw=row.joinToString("\u001f");val hash=digest(platform+raw);fun get(i:Int)=row.getOrElse(i){""}.trim().removePrefix("\t")
   try {
    val status=get(si);require(status.isEmpty()||listOf("成功","已支付","已收钱","已退款","已全额退款","部分退款").any{status.contains(it)}){"非已完成交易：$status"}
    val typ=get(ki);val direction=get(di);val refund=typ.contains("退款")
    val kind=if(refund)Kind.REFUND else when(direction){"支出"->Kind.EXPENSE;"收入"->Kind.INCOME;else->Kind.TRANSFER}
    var date=get(ti).replace('/','-').take(10);if(date.toDoubleOrNull()!=null){date=LocalDate.of(1899,12,30).plusDays(date.toDouble().toLong()).toString()};date=LocalDate.parse(date).toString()
    val amount=Money.parse(get(ai).replace("¥","").replace("￥","").replace(",",""));val merchant=get(pi);val note=get(ni);val external=get(ii)
    val key=platform+":"+kind+":"+(if(external.isNotBlank()&&kind!=Kind.REFUND)external else hash)
    val ambiguous=kind==Kind.TRANSFER||kind==Kind.REFUND||listOf("转账","红包","充值","还款").any{typ.contains(it)||note.contains(it)}||status.contains("退款")
    val entry=Entry(hash,date,amount,kind,category=category(merchant+note),merchant=merchant,note=note,method=if(platform=="wechat")"微信" else "支付宝",sourceKey=key)
    Candidate(hash,entry,if(ambiguous)"交易类型需确认；退款请尽量关联原消费" else "",needsReview=ambiguous)
   }catch(e:Exception){Candidate(hash,issue="第${hi+offset+2}行：${e.message?:"字段无效"}")}
  }; require(result.isNotEmpty()){"账单中没有交易记录"};ParseReport(result)
 }catch(e:Exception){ParseReport(emptyList(),e.message?:"账单解析失败")}
 fun category(text:String):String=when{listOf("饭","餐","咖啡","食堂","外卖","奶茶").any{text.contains(it)}->"餐饮";listOf("地铁","公交","打车","滴滴").any{text.contains(it)}->"交通";else->"其他"}
 private fun decode(bytes:ByteArray):String=try{Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()}catch(e:Exception){String(bytes,charset("GB18030"))}
 internal fun csv(text:String):List<List<String>> {val rows=mutableListOf<List<String>>();var row=mutableListOf<String>();val field=StringBuilder();var quoted=false;var i=0;while(i<text.length){val c=text[i];when{c=='"'->{if(quoted&&i+1<text.length&&text[i+1]=='"'){field.append('"');i++}else quoted=!quoted};c==','&&!quoted->{row.add(field.toString());field.setLength(0)};(c=='\n'||c=='\r')&&!quoted->{if(c=='\r'&&i+1<text.length&&text[i+1]=='\n')i++;row.add(field.toString());field.setLength(0);rows.add(row);row=mutableListOf();require(rows.size<=100000){"超过10万行，请拆分"}};else->field.append(c)};i++};require(!quoted){"CSV 引号未闭合"};if(field.isNotEmpty()||row.isNotEmpty()){row.add(field.toString());rows.add(row)};return rows}
 private fun xlsx(bytes:ByteArray):List<List<String>> {
  val files=mutableMapOf<String,ByteArray>();var total=0
  ZipInputStream(ByteArrayInputStream(bytes)).use{z->var entry=z.nextEntry;var count=0;while(entry!=null){require(++count<=1000){"XLSX 条目过多"};val out=java.io.ByteArrayOutputStream();val buf=ByteArray(8192);while(true){val n=z.read(buf);if(n<0)break;total+=n;require(total<=100*1024*1024){"XLSX 解压内容超过100MB"};out.write(buf,0,n)};if(entry.name.startsWith("xl/")&&entry.name.endsWith(".xml"))files[entry.name]=out.toByteArray();entry=z.nextEntry}}
  fun doc(content:ByteArray):org.w3c.dom.Document {val f=DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);f.setFeature("http://xml.org/sax/features/external-general-entities",false);f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);f.isExpandEntityReferences=false;return f.newDocumentBuilder().parse(ByteArrayInputStream(content))}
  val strings=files["xl/sharedStrings.xml"]?.let{data->val nodes=doc(data).getElementsByTagName("si");(0 until nodes.length).map{nodes.item(it).textContent}}?:emptyList()
  val sheet=files["xl/worksheets/sheet1.xml"]?:throw IllegalArgumentException("未找到第一张账单工作表")
  val nodes=doc(sheet).getElementsByTagName("row");require(nodes.length<=100000){"超过10万行，请拆分"};return (0 until nodes.length).map{n->val cells=(nodes.item(n) as Element).getElementsByTagName("c");val row=mutableListOf<String>();for(i in 0 until cells.length){val c=cells.item(i) as Element;val ref=c.getAttribute("r").takeWhile{it.isLetter()};val col=ref.fold(0){a,x->a*26+(x-'A'+1)}-1;require(col in 0..255){"工作表列数过多或坐标无效"};while(row.size<=col)row.add("");val v=c.getElementsByTagName("v").item(0)?.textContent?:"";row[col]=when(c.getAttribute("t")){"s"->strings.getOrElse(v.toInt()){throw IllegalArgumentException("无效共享字符串")};"inlineStr"->c.getElementsByTagName("is").item(0)?.textContent?:"";else->v}};row}
 }
}
object PaymentParser {
 fun parse(pkg:String,key:String,title:String,text:String,date:String):Candidate? {
  if(pkg !in listOf("com.tencent.mm","com.eg.android.AlipayGphone"))return null
  val all=title+" "+text
  if(!(title.contains("支付")||title.contains("付款")||title.contains("微信支付")))return null
  if(!listOf("支付成功","付款","成功支付").any{all.contains(it)})return null
  val amount=Regex("(?:[¥￥]\\s*([0-9]+(?:\\.[0-9]{1,2})?)|([0-9]+(?:\\.[0-9]{1,2})?)\\s*元)").find(all)?:return Candidate(digest(pkg+key),issue="交易通知缺少可识别金额",needsReview=true)
  val value=runCatching{Money.parse(amount.groupValues[1].ifEmpty{amount.groupValues[2]})}.getOrNull()?:return null
  val id=digest(pkg+key);return Candidate(id,Entry(id,date,value,method=if(pkg=="com.tencent.mm")"微信" else "支付宝",note=text.take(200),sourceKey="notice:"+id),"通知模板尚未真机验证，请核对金额、类型及重复记录",needsReview=true)
 }
}
