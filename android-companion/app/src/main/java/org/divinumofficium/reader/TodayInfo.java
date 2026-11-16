package org.divinumofficium.reader;
import android.content.Context;
import java.util.regex.*;

/** Display the source's heading without calculating a feast or season locally. */
final class TodayInfo {
    static String heading(Context c,DailyOfficeStore.Spec spec){
        for(int hour:new int[]{1,0,2,3,4,5,6,7}){
            if(hour>=spec.size())continue;DailyOfficeStore.Entry e=DailyOfficeStore.get(c,spec,hour);if(e==null)continue;
            String header=e.snapshot.optString("dayHeading");
            if(header.isEmpty()){
                Matcher m=Pattern.compile("(?is)<p\\b[^>]*class=['\"][^'\"]*\\bdl-day\\b[^'\"]*['\"][^>]*>(.*?)</p>").matcher(e.snapshot.optString("fragment"));
                if(m.find())header=OfflineSearch.plain(m.group(1));
            }
            if(!header.trim().isEmpty())return header.trim();
        }return "";
    }
}
