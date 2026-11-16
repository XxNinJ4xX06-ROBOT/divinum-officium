package org.divinumofficium.reader;
import android.app.*;
import android.os.*;
import android.content.*;
import android.widget.*;
import java.util.Locale;

public class ReminderActivity extends DailyActivity {
    private LinearLayout list;
    @Override public void onCreate(Bundle state){super.onCreate(state);buildRoot("Lembretes de oração","Opcionais · horários deste aparelho",true);
        paragraph("Escolha as horas que deseja lembrar. Os avisos não precisam de internet. O Android pode atrasá-los para economizar bateria; os horários são sugestões pessoais.",14);
        ScrollView scroll=new ScrollView(this);list=column();list.setPadding(dp(20),0,dp(20),dp(20));scroll.addView(list);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));render();
    }
    private void render(){list.removeAllViews();if(!ReminderPlanner.permitted(this)){Button permission=button("Permitir notificações");permission.setOnClickListener(v->{if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},20);else startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName()));});list.addView(permission);}
        for(int i=0;i<8;i++){final int hour=i;int minute=ReminderPlanner.prefs(this).getInt("minute:"+hour,ReminderPlanner.DEFAULT[hour]);
            Switch enabled=new Switch(this);enabled.setText(ReadingRequest.LABELS[i]);enabled.setTextSize(18);enabled.setTextColor(colors.ink);enabled.setPadding(0,dp(18),0,dp(12));enabled.setChecked(ReminderPlanner.prefs(this).getBoolean("enabled:"+i,false));enabled.setEnabled(ReminderPlanner.permitted(this));
            enabled.setOnCheckedChangeListener((v,yes)->{ReminderPlanner.prefs(this).edit().putBoolean("enabled:"+hour,yes).apply();ReminderPlanner.schedule(this,hour);});list.addView(enabled);
            Button time=button(String.format(Locale.ROOT,"%02d:%02d",minute/60,minute%60));time.setContentDescription("Horário de "+ReadingRequest.LABELS[i]);
            time.setOnClickListener(v->new TimePickerDialog(this,(picker,h,m)->{ReminderPlanner.prefs(this).edit().putInt("minute:"+hour,h*60+m).apply();ReminderPlanner.schedule(this,hour);render();},minute/60,minute%60,true).show());list.addView(time);
        }
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] result){super.onRequestPermissionsResult(request,permissions,result);if(request==20){ReminderPlanner.all(this);render();}}
    @Override protected void onResume(){super.onResume();if(list!=null){ReminderPlanner.all(this);render();}}
}
