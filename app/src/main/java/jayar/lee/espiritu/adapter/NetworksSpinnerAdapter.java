package jayar.lee.espiritu.adapter;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import com.jepongdevxyz.fablevpn.R;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

public class NetworksSpinnerAdapter extends ArrayAdapter<JSONObject> {

    private TextView tv1,tv2;

    private ImageView im;

    private String path;
    public NetworksSpinnerAdapter(Context c, ArrayList<JSONObject> a) {
        super(c, R.layout.payload_spinner, a);
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        return v(position, convertView, parent);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return v(position, convertView, parent);
    }

    public void setPath(String path) {
        this.path = path;
    }

    private View v(int position, View c, ViewGroup parent) {
        c = LayoutInflater.from(getContext()).inflate(R.layout.payload_spinner, parent, false);
        tv1 = c.findViewById(R.id.pName);
        tv2 = c.findViewById(R.id.pInfo);
        im = c.findViewById(R.id.pIcon);
        tv1.setTextSize(TypedValue.COMPLEX_UNIT_DIP,11);
        tv2.setTextSize(TypedValue.COMPLEX_UNIT_DIP,9);
        try {
            tv1.setText(getItem(position).getString("Name"));
            tv2.setText(getItem(position).getString("Info"));
            getPayloadIcon(position, im);
        } catch (Exception e) {
            tv1.setText(e.getMessage());
        }
        return c;
    }

    private void getPayloadIcon(int position, ImageView im) throws Exception {
        try {
            InputStream open = getContext().getAssets().open(new StringBuffer().append("networks/").append("icon_").append(getItem(position).getString("FLAG")).append(".png").toString());
            im.setImageDrawable(Drawable.createFromStream(open, (String) null));
            if (open != null) {
                open.close();
            }
        } catch (JSONException e) {
            im.setImageResource(R.drawable.ic_launcher);
        } catch (IOException e) {}
    }

}
