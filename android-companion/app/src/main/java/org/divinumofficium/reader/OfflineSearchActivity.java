package org.divinumofficium.reader;
import android.os.Bundle;
import android.widget.*;
import java.util.concurrent.*;

public class OfflineSearchActivity extends DailyActivity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();private Future<?> task;private LinearLayout list;private TextView status;private int generation;private boolean closed;
    @Override public void onCreate(Bundle state){super.onCreate(state);BookCatalog.Book selected=BookStore.catalog(this).find(getIntent().getStringExtra("bookId"));buildRoot(selected==null?"Buscar nos downloads":"Buscar no livro",selected==null?"Orações diárias e livros HTML/PDF":selected.title,true);
        paragraph("A busca usa apenas arquivos deste aparelho. A primeira pesquisa em um PDF pode demorar para preparar o índice; PDFs com páginas só em imagem não têm texto pesquisável.",14);
        EditText query=new EditText(this);query.setHint("Palavra ou trecho…");query.setSingleLine();root.addView(query);
        Button search=button("Buscar sem internet");search.setOnClickListener(v->search(query.getText().toString()));root.addView(search);
        status=paragraph("Digite pelo menos duas letras.",14);ScrollView scroll=new ScrollView(this);list=column();list.setPadding(dp(20),0,dp(20),dp(20));scroll.addView(list);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    }
    private void search(String term){final int ticket=++generation;if(task!=null)task.cancel(true);list.removeAllViews();status.setText("Pesquisando arquivos locais…");
        task=worker.submit(()->{try{OfflineSearch.Result results=OfflineSearch.search(this,term,getIntent().getStringExtra("bookId"));runOnUiThread(()->{if(closed||ticket!=generation)return;
            status.setText(results.hits.size()+" resultados · "+results.documents+" leituras pesquisadas"+(results.limited?" · limite de 80 resultados; refine a busca":"")+(results.unreadable>0?" · "+results.unreadable+" arquivos não puderam ser lidos":""));
            for(OfflineSearch.Hit hit:results.hits){Button open=button(hit.title);open.setGravity(android.view.Gravity.START);list.addView(open);list.addView(text(hit.snippet,15,colors.ink));
                open.setOnClickListener(v->{try{startActivity(PersonalStore.readingIntent(this,hit.ref).putExtra("highlight",term));}catch(Exception e){ReadingTools.toast(this,e.getMessage());}});}
            if(results.hits.isEmpty())list.addView(text("Nenhuma ocorrência. Confira os termos ou baixe mais leituras.",16,colors.muted));
        });}catch(Exception e){if(!Thread.currentThread().isInterrupted())runOnUiThread(()->{if(!closed&&ticket==generation)status.setText(e.getMessage());});}});
    }
    @Override protected void onDestroy(){closed=true;generation++;if(task!=null)task.cancel(true);worker.shutdownNow();super.onDestroy();}
}
