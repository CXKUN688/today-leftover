package com.jinyuyuliang.budget
import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.jinyuyuliang.budget.data.BudgetStore
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
class PaymentListenerService:NotificationListenerService(){
 override fun onNotificationPosted(sbn:StatusBarNotification){
  if(sbn.packageName !in listOf("com.tencent.mm","com.eg.android.AlipayGphone"))return
  val extras=sbn.notification.extras;val title=extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty();val text=(extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?:extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
  val date=Instant.ofEpochMilli(sbn.postTime).atZone(ZoneId.systemDefault()).toLocalDate().toString();val candidate=PaymentParser.parse(sbn.packageName,sbn.key,title,text,date)?:return
  val store=BudgetStore.get(this);store.scope.launch{runCatching{store.apply(Mutation.AddCandidates(listOf(candidate)))}}
 }
}
