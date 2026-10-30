package jayar.lee.espiritu.utils;

import android.content.*;
import android.widget.*;
import java.util.*;
import org.json.*;

public class JsonUtils
{
    public static Comparator getComparator(final Context context, final String tagJSON,final int type)
    {
        Comparator c =  new Comparator()
        {
            public int compare(Object a, Object b)
            {
                try
                {
                    JSONObject    ja = (JSONObject)a;
                    JSONObject    jb = (JSONObject)b;

                    switch (type)
                    {
                        case 1:// String
                            return ja.optString(tagJSON, "").toLowerCase()
                                .compareTo(jb.optString(tagJSON, "").toLowerCase());
                        case 2:// int
                            int valA =  ja.getInt(tagJSON);
                            int valB =  jb.getInt(tagJSON);
                            if(valA > valB)
                                return 1;
                            if(valA < valB)
                                return -1;

                        case 3:// double
                            String v1 = ja.getString(tagJSON).replace(",",".");
                            String v2 = jb.getString(tagJSON).replace(",",".");

                            double valAd = new Double(v1);// ja.getDouble(tagJSON);
                            double valBd = new Double(v2);//  jb.getDouble(tagJSON);
                            if(valAd > valBd)
                                return 1;
                            if(valAd < valBd)
                                return -1;

                    }
                }
                catch (Exception e)
                {
                    Toast.makeText(context, e.getMessage(), 1).show();
                }
                return 0;
            }
        };

        return c;
    }

    public static JSONArray sort(JSONArray array, Comparator c){
        List    asList = new ArrayList(array.length());
        for (int i=0; i<array.length(); i++){
            asList.add(array.opt(i));
        }
        Collections.sort(asList, c);
        JSONArray  res = new JSONArray();
        for (Object o : asList){
            res.put(o);
        }
        return res;
    }
}
