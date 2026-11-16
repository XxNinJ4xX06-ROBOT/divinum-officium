package org.divinumofficium.reader;
import android.os.Bundle;
import android.content.Intent;
import android.widget.*;
import org.json.*;

public class StudyActivity extends DailyActivity {
    private LinearLayout list;private JSONArray plan;private String level="Todos",topic="Todos";
    @Override public void onCreate(Bundle state){super.onCreate(state);try{plan=new JSONObject(BookStore.asset(this,"study.json")).getJSONArray("items");}catch(Exception e){finish();return;}
        if(state!=null){level=state.getString("level","Todos");topic=state.getString("topic","Todos");}
        buildRoot("Roteiro de estudos","Filosofia e Teologia · no seu ritmo",true);
        paragraph("Comece pelos fundamentos e avance conforme sua leitura. As etapas são sugestões; o progresso é salvo neste aparelho.",14);
        Spinner levels=new Spinner(this),topics=new Spinner(this);
        String[] ls={"Todos","Iniciante","Intermediário","Avançado"},ts={"Todos","Fé e razão","Ética","Metafísica","Doutrina","Revelação","Espiritualidade"};
        levels.setContentDescription("Filtrar nível de estudo");topics.setContentDescription("Filtrar tema de estudo");
        levels.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,ls));topics.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,ts));
        paragraph("Nível de estudo",12);root.addView(levels);paragraph("Tema",12);root.addView(topics);
        levels.setSelection(java.util.Arrays.asList(ls).indexOf(level));topics.setSelection(java.util.Arrays.asList(ts).indexOf(topic));
        ScrollView scroll=new ScrollView(this);list=column();list.setPadding(dp(20),0,dp(20),dp(24));scroll.addView(list);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        levels.setOnItemSelectedListener(listener(i->{level=ls[i];render();}));topics.setOnItemSelectedListener(listener(i->{topic=ts[i];render();}));render();
    }
    private android.widget.AdapterView.OnItemSelectedListener listener(java.util.function.IntConsumer action){return new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,android.view.View v,int n,long id){action.accept(n);}};}
    private void render(){list.removeAllViews();BookCatalog catalog=BookStore.catalog(this);int completed=0;for(BookCatalog.Book b:catalog.all())if(PersonalStore.completed(this,b.id))completed++;
        list.addView(text(completed+"/"+plan.length()+" obras concluídas",16,colors.accent));
        int shown=0;
        for(int i=0;i<plan.length();i++){JSONObject item=plan.optJSONObject(i);if(!"Todos".equals(level)&&!level.equals(item.optString("level"))||!"Todos".equals(topic)&&!topic.equals(item.optString("topic")))continue;
            BookCatalog.Book b=catalog.find(item.optString("book"));if(b==null)continue;
            shown++;
            TextView title=text((i+1)+". "+b.title,20,colors.ink);title.setPadding(0,dp(24),0,dp(8));list.addView(title);
            list.addView(text(item.optString("level")+" · "+item.optString("topic")+" · "+b.language,13,colors.accent));
            list.addView(text(item.optString("why"),15,colors.muted));
            CheckBox done=new CheckBox(this);done.setText("Concluí esta leitura");done.setTextColor(colors.ink);done.setChecked(PersonalStore.completed(this,b.id));done.setOnCheckedChangeListener((v,checked)->{try{PersonalStore.complete(this,b.id,checked);render();}catch(Exception e){ReadingTools.toast(this,e.getMessage());}});list.addView(done);
            Button open=button(BookStore.cached(this,b)?"Ler offline":b.offline?"Baixar e ler":"Consultar online");open.setOnClickListener(v->startActivity(new Intent(this,BookReaderActivity.class).putExtra("bookId",b.id)));list.addView(open);
        }
        if(shown==0)list.addView(text("Nenhuma obra nesta combinação. Escolha outro nível ou tema.",16,colors.muted));
    }
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("level",level);state.putString("topic",topic);}
}
