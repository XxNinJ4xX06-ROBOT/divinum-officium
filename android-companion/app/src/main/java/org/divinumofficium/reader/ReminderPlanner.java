package org.divinumofficium.reader;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import java.time.*;

/** Optional local reminders, inexact alarms and wall-clock time in the device's zone. */
final class ReminderPlanner {
    static final int[] DEFAULT={5*60,6*60,7*60,9*60,12*60,15*60,18*60,21*60};
    static final String CHANNEL="canonical-hours";
    static android.content.SharedPreferences prefs(Context c){return c.getSharedPreferences("reminders",0);}
    static boolean permitted(Context c){return (Build.VERSION.SDK_INT<33||c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)&&c.getSystemService(NotificationManager.class).areNotificationsEnabled();}
    static long next(long now,int minute,ZoneId zone){if(minute<0||minute>=1440)throw new IllegalArgumentException();ZonedDateTime current=Instant.ofEpochMilli(now).atZone(zone);LocalTime time=LocalTime.of(minute/60,minute%60);ZonedDateTime target=current.toLocalDate().atTime(time).atZone(zone);if(!target.toInstant().isAfter(current.toInstant()))target=current.toLocalDate().plusDays(1).atTime(time).atZone(zone);return target.toInstant().toEpochMilli();}
    static PendingIntent pending(Context c,int hour){return PendingIntent.getBroadcast(c,1200+hour,new Intent(c,ReminderReceiver.class).setAction("org.divinumofficium.reader.REMIND").putExtra("hour",hour),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    static void schedule(Context c,int hour){AlarmManager alarms=c.getSystemService(AlarmManager.class);PendingIntent p=pending(c,hour);alarms.cancel(p);
        if(!prefs(c).getBoolean("enabled:"+hour,false)||!permitted(c))return;
        int minute=prefs(c).getInt("minute:"+hour,DEFAULT[hour]);long time=next(System.currentTimeMillis(),minute,ZoneId.systemDefault());alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,time,p);
    }
    static void all(Context c){for(int i=0;i<8;i++)schedule(c,i);}
    static void channel(Context c){c.getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL,"Horas de oração",NotificationManager.IMPORTANCE_DEFAULT));}
}
