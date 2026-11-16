package org.divinumofficium.reader;
import android.content.Context;
import android.text.Html;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.Normalizer;
import java.util.*;
import org.json.*;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import com.tom_roush.pdfbox.io.MemoryUsageSetting;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;

/** Local-only search. PDF text extraction runs on a worker and can be interrupted. */
final class OfflineSearch {
    static class Hit {final String title,snippet;final JSONObject ref;Hit(String title,String snippet,JSONObject ref){this.title=title;this.snippet=snippet;this.ref=ref;}}
    static class Result {final ArrayList<Hit> hits=new ArrayList<>();int documents,unreadable;boolean limited;}
    static String normalize(String text){return Normalizer.normalize(text,Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT);}
    static String plain(String html){return Html.fromHtml(html.replaceAll("(?is)<(?:style|script)\\b[^>]*>.*?</(?:style|script)\\s*>",""),Html.FROM_HTML_MODE_LEGACY).toString();}
    private static void interrupted() throws InterruptedIOException{if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Busca cancelada");}
    private static String excerpt(String text,String query){String normalized=normalize(text);int at=normalized.indexOf(query);if(at<0)return null;int start=Math.max(0,at-70),end=Math.min(text.length(),at+query.length()+120);return (start>0?"…":"")+text.substring(start,end).replaceAll("\\s+"," ")+(end<text.length()?"…":"");}
    static Result search(Context c,String term)throws Exception{return search(c,term,null);}
    static Result search(Context c,String term,String bookId) throws Exception{
        String query=normalize(term.trim());if(query.length()<2)throw new IOException("Digite pelo menos duas letras.");Result result=new Result();
        if(bookId!=null&&BookStore.catalog(c).find(bookId)==null)throw new IOException("Livro desconhecido.");
        if(bookId==null){
        for(DailyOfficeStore.Spec spec:DailyOfficeStore.packages(c))for(int hour=0;hour<spec.size();hour++){
            interrupted();DailyOfficeStore.Entry entry=DailyOfficeStore.get(c,spec,hour);if(entry==null)continue;result.documents++;
            String snippet=excerpt(plain(entry.snapshot.getString("fragment")),query);
            if(snippet!=null)result.hits.add(new Hit(spec.title(hour)+" · "+spec.displayDay()+" · "+spec.label,snippet,PersonalStore.daily(spec,hour,0)));
            if(result.hits.size()>=80){result.limited=true;return result;}
        }
        }
        for(BookCatalog.Book book:BookStore.catalog(c).all()){
            if(bookId!=null&&!book.id.equals(bookId))continue;
            interrupted();if(!BookStore.cached(c,book))continue;result.documents++;
            try{if("pdf".equals(book.format)){
                JSONArray pages=pdfPages(c,BookStore.file(c,book),book.id);
                for(int page=0;page<pages.length();page++){interrupted();String snippet=excerpt(pages.getString(page),query);if(snippet!=null)result.hits.add(new Hit(book.title+" · página "+(page+1),snippet,PersonalStore.book(book,book.url,page,0)));if(result.hits.size()>=80){result.limited=true;return result;}}
            }else{String snippet=excerpt(plain(new String(Files.readAllBytes(BookStore.file(c,book).toPath()),StandardCharsets.UTF_8)),query);if(snippet!=null)result.hits.add(new Hit(book.title,snippet,PersonalStore.book(book,book.url,0,0)));}
            }catch(InterruptedIOException e){throw e;}catch(Exception e){result.unreadable++;}
        }
        result.limited=result.hits.size()>80;if(result.limited)result.hits.subList(80,result.hits.size()).clear();return result;
    }
    static JSONArray pdfPages(Context c,File file,String id) throws Exception{
        File index=new File(c.getCacheDir(),"pdf-index-"+id+"-"+file.length()+"-"+file.lastModified()+".json");
        try(InputStream in=new AtomicFile(index).openRead()){return new JSONArray(new String(read(in,12*1024*1024),StandardCharsets.UTF_8));}catch(Exception ignored){}
        PDFBoxResourceLoader.init(c);JSONArray pages=new JSONArray();
        try(PDDocument doc=PDDocument.load(file,MemoryUsageSetting.setupTempFileOnly().setTempDir(c.getCacheDir()))){
            PDFTextStripper stripper=new PDFTextStripper();for(int page=1;page<=doc.getNumberOfPages();page++){interrupted();stripper.setStartPage(page);stripper.setEndPage(page);pages.put(stripper.getText(doc));}
        }
        byte[] data=pages.toString().getBytes(StandardCharsets.UTF_8);if(data.length<=12*1024*1024){AtomicFile atomic=new AtomicFile(index);FileOutputStream out=null;try{out=atomic.startWrite();out.write(data);atomic.finishWrite(out);}catch(IOException e){if(out!=null)atomic.failWrite(out);}}
        return pages;
    }
    private static byte[] read(InputStream in,int limit)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>limit)throw new IOException();out.write(buffer,0,n);}return out.toByteArray();}
}
