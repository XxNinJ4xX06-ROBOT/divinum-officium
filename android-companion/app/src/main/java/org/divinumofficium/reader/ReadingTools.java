package org.divinumofficium.reader;

import android.app.*;
import android.content.*;
import android.webkit.WebView;
import android.widget.*;
import org.json.*;

/** Native notes and browser find work with JavaScript disabled. */
final class ReadingTools {
    static void show(Activity a,JSONObject ref,WebView web){
        String[] actions=web==null?new String[]{"Favoritar esta página","Adicionar trecho ou anotação","Ver trechos deste livro","Buscar neste PDF"}:new String[]{"Favoritar este ponto","Adicionar trecho ou anotação","Ver trechos desta leitura","Buscar neste texto","Próxima ocorrência"};
        new AlertDialog.Builder(a).setTitle("Sua leitura").setItems(actions,(d,w)->{
            if(w==0)try{PersonalStore.save(a,ref,"","",true,null);toast(a,"Ponto salvo em Favoritos e notas.");}catch(Exception e){toast(a,e.getMessage());}
            if(w==1)edit(a,ref,null,null);
            if(w==2)list(a,ref,web);
            if(w==3){if(web!=null)find(a,web);else a.startActivity(new Intent(a,OfflineSearchActivity.class).putExtra("bookId",ref.optString("book")));}
            if(w==4&&web!=null)web.findNext(true);
        }).setNegativeButton("Fechar",null).show();
    }
    static void edit(Activity a,JSONObject ref,JSONObject item,Runnable changed){
        LinearLayout form=new LinearLayout(a);form.setOrientation(LinearLayout.VERTICAL);int pad=Math.round(20*a.getResources().getDisplayMetrics().density);form.setPadding(pad,pad,pad,pad);
        EditText quote=new EditText(a);quote.setHint("Trecho: cole ou digite o texto");quote.setMinLines(2);quote.setMaxLines(5);quote.setText(item==null?"":item.optString("quote"));form.addView(quote);
        EditText note=new EditText(a);note.setHint("Sua anotação pessoal");note.setMinLines(3);note.setMaxLines(6);note.setText(item==null?"":item.optString("note"));form.addView(note);
        CheckBox favorite=new CheckBox(a);favorite.setText("Adicionar aos favoritos");favorite.setChecked(item==null||item.optBoolean("favorite"));form.addView(favorite);
        ScrollView scroll=new ScrollView(a);scroll.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(a).setTitle(item==null?"Guardar trecho / anotação":"Editar anotação").setView(scroll).setNegativeButton("Cancelar",null).setPositiveButton("Salvar",null).create();
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{if(quote.getText().toString().trim().isEmpty()&&note.getText().toString().trim().isEmpty()&&!favorite.isChecked())throw new IllegalArgumentException("Escreva um trecho, uma nota ou marque como favorito.");
                PersonalStore.save(a,ref,quote.getText().toString(),note.getText().toString(),favorite.isChecked(),item==null?null:item.getString("id"));dialog.dismiss();toast(a,"Registro salvo neste aparelho.");if(changed!=null)changed.run();
            }catch(Exception e){toast(a,e.getMessage());}
        }));dialog.show();
    }
    private static void list(Activity a,JSONObject ref,WebView web){
        JSONArray items=PersonalStore.items(a);java.util.ArrayList<JSONObject> matches=new java.util.ArrayList<>();java.util.ArrayList<String> labels=new java.util.ArrayList<>();
        for(int i=items.length()-1;i>=0;i--){JSONObject item=items.optJSONObject(i);if(PersonalStore.identity(ref).equals(PersonalStore.identity(item.optJSONObject("ref")))){matches.add(item);String label=item.optString("quote").isEmpty()?item.optString("note"):item.optString("quote");labels.add(label.isEmpty()?item.optJSONObject("ref").optString("title","Ponto salvo"):label);}}
        if(matches.isEmpty()){toast(a,"Nenhum trecho ou nota para esta leitura.");return;}
        new AlertDialog.Builder(a).setTitle("Trechos e notas").setItems(labels.toArray(new String[0]),(d,w)->{
            JSONObject item=matches.get(w);new AlertDialog.Builder(a).setTitle("Trecho guardado").setMessage(item.optString("quote")+"\n\n"+item.optString("note"))
                .setPositiveButton(web==null?"Abrir página marcada":"Destacar no texto",(dialog,which)->{if(web!=null&&!item.optString("quote").isEmpty())web.findAllAsync(item.optString("quote"));else if(web==null){try{a.startActivity(PersonalStore.readingIntent(a,item.getJSONObject("ref")));}catch(Exception e){toast(a,e.getMessage());}}else toast(a,"Este registro guarda um ponto de leitura, sem trecho para destacar.");})
                .setNeutralButton("Editar",(dialog,which)->edit(a,ref,item,null)).setNegativeButton("Fechar",null).show();
        }).setNegativeButton("Fechar",null).show();
    }
    static void find(Activity a,WebView web){
        EditText query=new EditText(a);query.setHint("Palavra ou trecho");query.setSingleLine();
        new AlertDialog.Builder(a).setTitle("Buscar neste texto").setView(query).setPositiveButton("Buscar",(d,w)->{
            String text=query.getText().toString().trim();if(text.isEmpty())return;
            web.setFindListener((active,total,done)->{if(done)toast(a,total+" ocorrências. Use Próxima ocorrência em Sua leitura.");});web.findAllAsync(text);
        }).setNeutralButton("Limpar destaques",(d,w)->web.clearMatches()).setNegativeButton("Cancelar",null).show();
    }
    static void toast(Context c,String s){Toast.makeText(c,s==null?"Não foi possível concluir.":s,Toast.LENGTH_LONG).show();}
}
