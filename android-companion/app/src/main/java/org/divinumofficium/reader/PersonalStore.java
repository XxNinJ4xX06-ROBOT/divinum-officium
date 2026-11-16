package org.divinumofficium.reader;

import android.content.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Personal records and portable, validated backups; downloaded publications stay on device. */
final class PersonalStore {
    static final String KEY = "personalData";
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("reader", 0); }
    static JSONObject data(Context c) {
        try { return new JSONObject(prefs(c).getString(KEY, "{\"items\":[],\"completed\":[]}")); }
        catch (Exception e) { throw new IllegalStateException("Não foi possível ler suas anotações.", e); }
    }
    static JSONArray items(Context c) { return data(c).optJSONArray("items"); }
    static boolean completed(Context c, String id) {
        JSONArray a = data(c).optJSONArray("completed"); for (int i=0;i<a.length();i++) if (id.equals(a.optString(i))) return true; return false;
    }
    static synchronized void complete(Context c, String id, boolean yes) throws Exception {
        if (BookStore.catalog(c).find(id)==null) throw new IOException("Livro desconhecido");
        JSONObject d=data(c); JSONArray old=d.getJSONArray("completed"), next=new JSONArray();
        for(int i=0;i<old.length();i++) if(!id.equals(old.getString(i))) next.put(old.getString(i));
        if(yes) next.put(id); d.put("completed",next); write(c,d);
    }
    static JSONObject book(BookCatalog.Book book, String url, int page, int scroll) {
        try { return new JSONObject().put("kind","book").put("book",book.id).put("title",book.title+("pdf".equals(book.format)?" · página "+(page+1):"")).put("url",url).put("page",page).put("scroll",scroll); }
        catch(JSONException e){throw new IllegalStateException(e);}
    }
    static JSONObject daily(DailyOfficeStore.Spec spec, int hour, int scroll) {
        try { return new JSONObject().put("kind","daily").put("day",spec.day).put("version",spec.version).put("mass",spec.mass)
                .put("hour",hour).put("scroll",scroll).put("title",spec.title(hour)+" · "+spec.displayDay()); }
        catch(JSONException e){throw new IllegalStateException(e);}
    }
    static String identity(JSONObject ref) {
        switch(ref.optString("kind")) {
            case "book":return "book:"+ref.optString("book")+":"+ref.optString("url");
            case "daily":return "daily:"+ref.optString("day")+":"+ref.optString("version")+":"+ref.optBoolean("mass")+":"+ref.optInt("hour");
            default:return "web:"+ref.optString("url");
        }
    }
    static void validateRef(Context c, JSONObject r) throws Exception {
        bounded(r.getString("title"),400); int scroll=r.optInt("scroll",0),page=r.optInt("page",0);
        if(scroll<0 || scroll>100000000 || page<0 || page>100000) throw new IOException("Posição inválida");
        switch(r.getString("kind")) {
            case "book":
                BookCatalog.Book b=BookStore.catalog(c).find(r.getString("book"));
                if(b==null || !BookCatalog.allows(b,r.getString("url"))) throw new IOException("Fonte de livro inválida"); break;
            case "daily":
                DailyOfficeStore.Spec s=DailyOfficeStore.spec(c,r.getString("day"),r.getString("version"),r.getBoolean("mass")); s.id(r.getInt("hour")); break;
            case "web": if(!UrlPolicy.isOfficial(r.getString("url"))) throw new IOException("Fonte inválida"); break;
            default:throw new IOException("Tipo de leitura inválido");
        }
    }
    static synchronized void save(Context c, JSONObject ref, String quote, String note, boolean favorite, String existingId) throws Exception {
        validateRef(c,ref); bounded(quote,4000); bounded(note,8000);
        JSONObject record=new JSONObject().put("id",existingId==null?UUID.randomUUID().toString():existingId)
                .put("ref",ref).put("quote",quote.trim()).put("note",note.trim()).put("favorite",favorite).put("updated",System.currentTimeMillis());
        validateItem(c,record); JSONObject d=data(c); JSONArray old=d.getJSONArray("items"), next=new JSONArray();
        for(int i=0;i<old.length();i++) if(!record.getString("id").equals(old.getJSONObject(i).getString("id"))) next.put(old.getJSONObject(i));
        if(next.length()>=1000) throw new IOException("Limite de 1.000 registros atingido. Exporte uma cópia antes de remover itens.");
        next.put(record); d.put("items",next); write(c,d);
    }
    static synchronized void remove(Context c,String id) throws Exception {
        JSONObject d=data(c); JSONArray old=d.getJSONArray("items"),next=new JSONArray();
        for(int i=0;i<old.length();i++) if(!id.equals(old.getJSONObject(i).getString("id"))) next.put(old.getJSONObject(i));
        d.put("items",next);write(c,d);
    }
    private static void write(Context c,JSONObject d) throws IOException {
        if(!prefs(c).edit().putString(KEY,encoded(d)).commit()) throw new IOException("Não foi possível guardar os dados.");
    }
    private static String encoded(JSONObject data)throws IOException{String value=data.toString();if(value.getBytes(StandardCharsets.UTF_8).length>2*1024*1024-16384)throw new IOException("Limite dos dados pessoais atingido. Exporte uma cópia antes de remover registros antigos.");return value;}
    private static void bounded(String value,int max) throws IOException { if(value.length()>max) throw new IOException("Texto maior que o limite ("+max+")."); }
    private static void validateItem(Context c,JSONObject i) throws Exception {
        UUID.fromString(i.getString("id")); validateRef(c,i.getJSONObject("ref")); bounded(i.getString("quote"),4000); bounded(i.getString("note"),8000);
        i.getBoolean("favorite"); if(i.getLong("updated")<0) throw new IOException("Data inválida");
    }
    static String exportData(Context c) throws Exception {
        JSONObject settings=new JSONObject(); SharedPreferences p=prefs(c);
        for(String key:new String[]{"appearance","officeVersion","massVersion"}) if(p.contains(key)) settings.put(key,p.getString(key,""));
        for(String key:new String[]{"zoom","bookZoom","languageMode"}) if(p.contains(key)) settings.put(key,p.getInt(key,0));
        return new JSONObject().put("format","divinum-leitura-personal").put("schema",1).put("created",System.currentTimeMillis())
                .put("data",data(c)).put("settings",settings).toString();
    }
    static String readBackup(InputStream input) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
        while((n=input.read(buffer))!=-1){ if(out.size()+n>2*1024*1024) throw new IOException("Backup maior que 2 MB."); out.write(buffer,0,n); }
        return out.toString("UTF-8");
    }
    /** Validate everything before one atomic preferences commit; merge by id and newest timestamp. */
    static synchronized void importData(Context c,String text) throws Exception {
        if(text.getBytes(StandardCharsets.UTF_8).length>2*1024*1024) throw new IOException("Backup maior que 2 MB.");
        JSONObject backup=new JSONObject(text);
        if(!"divinum-leitura-personal".equals(backup.getString("format")) || backup.getInt("schema")!=1) throw new IOException("Formato de backup não reconhecido.");
        JSONObject incoming=backup.getJSONObject("data"),settings=backup.getJSONObject("settings"),current=data(c);
        JSONArray records=incoming.getJSONArray("items"),done=incoming.getJSONArray("completed");
        if(records.length()>1000 || done.length()>1000) throw new IOException("Backup com itens demais.");
        LinkedHashMap<String,JSONObject> merged=new LinkedHashMap<>();
        JSONArray existing=current.getJSONArray("items"); for(int i=0;i<existing.length();i++){JSONObject item=existing.getJSONObject(i);merged.put(item.getString("id"),item);}
        for(int i=0;i<records.length();i++){
            JSONObject item=records.getJSONObject(i);validateItem(c,item);String id=item.getString("id");
            if(!merged.containsKey(id) || item.getLong("updated")>merged.get(id).getLong("updated")) merged.put(id,item);
        }
        if(merged.size()>1000) throw new IOException("A união excede 1.000 registros.");
        LinkedHashSet<String> completed=new LinkedHashSet<>();
        JSONArray before=current.getJSONArray("completed");for(int i=0;i<before.length();i++) completed.add(before.getString(i));
        for(int i=0;i<done.length();i++){String id=done.getString(i);if(BookStore.catalog(c).find(id)==null)throw new IOException("Livro desconhecido no progresso.");completed.add(id);}
        SharedPreferences.Editor editor=prefs(c).edit();
        Iterator<String> keys=settings.keys();while(keys.hasNext()){
            String key=keys.next();switch(key){
                case "appearance":String theme=settings.getString(key);if(!Arrays.asList(AppAppearance.MODES).contains(theme))throw new IOException("Tema inválido");editor.putString(key,theme);break;
                case "officeVersion": case "massVersion":String version=settings.getString(key);DailyOfficeStore.spec(c,"2026-01-01",version,"massVersion".equals(key));editor.putString(key,version);break;
                case "zoom":case "bookZoom":case "languageMode":int value=settings.getInt(key);if("languageMode".equals(key)?value<0||value>2:value<70||value>220)throw new IOException("Preferência inválida");editor.putInt(key,value);break;
                default:throw new IOException("Preferência não suportada no backup.");
            }
        }
        JSONObject result=new JSONObject().put("items",new JSONArray(merged.values())).put("completed",new JSONArray(completed));
        if(!editor.putString(KEY,encoded(result)).commit())throw new IOException("Não foi possível importar os dados.");
    }
    static Intent readingIntent(Context c,JSONObject ref) throws Exception {
        validateRef(c,ref);Intent intent;
        if("daily".equals(ref.getString("kind"))){
            DailyOfficeStore.Spec spec=DailyOfficeStore.spec(c,ref.getString("day"),ref.getString("version"),ref.getBoolean("mass"));
            int hour=ref.getInt("hour");if(DailyOfficeStore.get(c,spec,hour)==null)throw new IOException("Baixe novamente este dia e rubrica para abrir o ponto salvo.");
            intent=DailyReaderActivity.intent(c,spec,hour);
        }else if("book".equals(ref.getString("kind"))) intent=new Intent(c,BookReaderActivity.class).putExtra("bookId",ref.getString("book")).putExtra("address",ref.getString("url")).putExtra("page",ref.optInt("page"));
        else intent=new Intent(c,MainActivity.class).putExtra("openUrl",UrlPolicy.secure(ref.getString("url"))).putExtra("openTitle",ref.getString("title"));
        return intent.putExtra("savedPosition",true).putExtra("scroll",ref.optInt("scroll",0));
    }
}
