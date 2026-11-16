package org.divinumofficium.reader;
import android.app.*;
import android.content.*;

public class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent intent){
        if(!"org.divinumofficium.reader.REMIND".equals(intent.getAction())){ReminderPlanner.all(c);return;}
        int hour=intent.getIntExtra("hour",-1);if(hour<0||hour>=8)return;
        if(ReminderPlanner.prefs(c).getBoolean("enabled:"+hour,false)&&ReminderPlanner.permitted(c)){
            ReminderPlanner.channel(c);Intent open=new Intent(c,MainActivity.class).putExtra("reminderHour",hour).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pending=PendingIntent.getActivity(c,1300+hour,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            Notification notification=new Notification.Builder(c,ReminderPlanner.CHANNEL).setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(ReadingRequest.LABELS[hour]).setContentText("Seu horário de oração. Toque para abrir a leitura de hoje.")
                    .setContentIntent(pending).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).build();
            c.getSystemService(NotificationManager.class).notify(1400+hour,notification);
        }ReminderPlanner.schedule(c,hour);
    }
}
