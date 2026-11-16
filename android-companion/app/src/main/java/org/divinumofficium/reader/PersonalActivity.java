package org.divinumofficium.reader;

import android.app.AlertDialog;
import android.content.*;
import android.os.Bundle;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.json.*;

public class PersonalActivity extends DailyActivity {
    private LinearLayout list;
    private String filter="";
    private boolean favoritesOnly;
    @Override public void onCreate(Bundle state){super.onCreate(state);buildRoot("Favoritos e notas","Seus registros pessoais",true);
        paragraph("Guarde pontos de leitura, trechos e anotações pelo botão ☆ dos leitores. Os destaques de trechos usam a busca do leitor.",14);
        LinearLayout actions=row();Button export=button("Exportar dados"),importButton=button("Importar dados");
        export.setOnClickListener(v->chooseBackup(true));importButton.setOnClickListener(v->chooseBackup(false));
        actions.addView(export,new LinearLayout.LayoutParams(0,dp(52),1));actions.addView(importButton,new LinearLayout.LayoutParams(0,dp(52),1));root.addView(actions);
        EditText query=new EditText(this);query.setHint("Buscar notas e trechos…");query.setSingleLine();root.addView(query);
        CheckBox only=new CheckBox(this);only.setText("Mostrar somente favoritos");only.setTextColor(colors.ink);only.setOnCheckedChangeListener((v,checked)->{favoritesOnly=checked;render();});root.addView(only);
        query.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int n,int after){}public void afterTextChanged(android.text.Editable s){}public void onTextChanged(CharSequence s,int st,int before,int n){filter=s.toString();render();}});
        ScrollView scroll=new ScrollView(this);list=column();list.setPadding(dp(20),0,dp(20),dp(20));scroll.addView(list);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));render();
    }
    private void render(){if(list==null)return;list.removeAllViews();JSONArray items=PersonalStore.items(this);int count=0;
        for(int i=items.length()-1;i>=0;i--){JSONObject item=items.optJSONObject(i),ref=item.optJSONObject("ref");String title=ref.optString("title");
            if(favoritesOnly&&!item.optBoolean("favorite"))continue;
            if(!OfflineSearch.normalize(title+" "+item.optString("quote")+" "+item.optString("note")).contains(OfflineSearch.normalize(filter)))continue;
            TextView heading=text((item.optBoolean("favorite")?"★ ":"")+title,18,colors.accent);heading.setPadding(0,dp(18),0,dp(8));list.addView(heading);
            TextView body=text(item.optString("quote")+"\n"+item.optString("note"),15,colors.ink);list.addView(body);
            LinearLayout row=row();Button open=button("Abrir"),edit=button("Editar"),remove=button("Excluir");
            open.setOnClickListener(v->{try{startActivity(PersonalStore.readingIntent(this,ref).putExtra("highlight",item.optString("quote")));}catch(Exception e){ReadingTools.toast(this,e.getMessage());}});
            edit.setOnClickListener(v->ReadingTools.edit(this,ref,item,this::render));
            remove.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Excluir este registro?").setMessage(title).setNegativeButton("Cancelar",null).setPositiveButton("Excluir",(d,w)->{try{PersonalStore.remove(this,item.getString("id"));render();}catch(Exception e){ReadingTools.toast(this,e.getMessage());}}).show());
            for(Button b:new Button[]{open,edit,remove})row.addView(b,new LinearLayout.LayoutParams(0,dp(48),1));list.addView(row);count++;
        }if(count==0)list.addView(text("Nenhum registro encontrado. Use ☆ durante uma leitura para começar.",16,colors.muted));
    }
    private void chooseBackup(boolean export){
        if(export){Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"Divinum-dados-pessoais-"+LocalDate.now()+".json");startActivityForResult(intent,10);}
        else new AlertDialog.Builder(this).setTitle("Importar dados pessoais").setMessage("A importação une favoritos, notas e progresso e aplica as preferências do arquivo. Orações e livros baixados não fazem parte do backup. Lembretes continuam sendo configurados neste aparelho.")
            .setNegativeButton("Cancelar",null).setPositiveButton("Escolher arquivo",(d,w)->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),11)).show();
    }
    @Override protected void onActivityResult(int request,int result,Intent intent){super.onActivityResult(request,result,intent);if(result!=RESULT_OK||intent==null||intent.getData()==null)return;
        try{if(request==10){byte[] bytes=PersonalStore.exportData(this).getBytes(StandardCharsets.UTF_8);try(OutputStream stream=getContentResolver().openOutputStream(intent.getData(),"wt")){if(stream==null)throw new IOException("Destino indisponível");stream.write(bytes);}ReadingTools.toast(this,"Backup exportado: notas, favoritos, progresso e preferências. Livros e orações não são incluídos.");}
            if(request==11){String text;try(InputStream stream=getContentResolver().openInputStream(intent.getData())){if(stream==null)throw new IOException("Arquivo indisponível");text=PersonalStore.readBackup(stream);}PersonalStore.importData(this,text);ReadingTools.toast(this,"Dados pessoais importados.");recreate();}
        }catch(Exception e){ReadingTools.toast(this,"Não foi possível concluir: "+e.getMessage());}
    }
}
