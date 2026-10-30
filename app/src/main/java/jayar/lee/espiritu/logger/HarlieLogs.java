package jayar.lee.espiritu.logger;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.database.DataSetObserver;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.Html;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.Vector;
import android.os.Handler.Callback;
import android.widget.Toast;

import com.jepongdevxyz.fablevpn.R;
import jayar.lee.espiritu.activities.OpenVPNClientBase;


public class HarlieLogs extends OpenVPNClientBase {

    private static final String LOGTIMEFORMAT = "logtimeformat";
    public static LogWindowListAdapter ladapter;

    class LogWindowListAdapter implements ListAdapter, hLogStatus.LogListener, Callback
    {

        @Override
        public CharSequence[] getAutofillOptions() {
            return null;
        }

        @Override
        public void onClear() {
        }

        private static final int MESSAGE_NEWLOG = 0;
        private static final int MESSAGE_CLEARLOG = 1;
        private static final int MESSAGE_NEWTS = 2;
        private static final int MESSAGE_NEWLOGLEVEL = 3;
        public static final int TIME_FORMAT_NONE = 0;
        public static final int TIME_FORMAT_SHORT = 1;
        public static final int TIME_FORMAT_ISO = 2;
        private static final int MAX_STORED_LOG_ENTRIES = 1000;
        private Vector<LogItem> allEntries = new Vector<>();
        private Vector<LogItem> currentLevelEntries = new Vector<LogItem>();
        private Handler mHandler;
        private Vector<DataSetObserver> observers = new Vector<DataSetObserver>();
        private int mTimeFormat = 0;
        private int mLogLevel = 3;
        private Context mContext;
        public LogWindowListAdapter(Context mContext) {
            this.mContext = mContext;
            initLogBuffer();
            if (mHandler == null) {
                mHandler = new Handler(this);
            }
            hLogStatus.addLogListener(this);
        }

        private void initLogBuffer() {
            allEntries.clear();
            Collections.addAll(allEntries, hLogStatus.getlogbuffer());
            initCurrentMessages();
        }

        /*String getLogStr() {
            String str = "";
            for (LogItem entry : allEntries) {
                str += getTime(entry, TIME_FORMAT_ISO) + entry.getString(getActivity()) + '\n';
            }
            return str;
        }*/

        @Override
        public void registerDataSetObserver(DataSetObserver observer) {
            observers.add(observer);

        }

        @Override
        public void unregisterDataSetObserver(DataSetObserver observer) {
            observers.remove(observer);
        }

        @Override
        public int getCount() {
            return currentLevelEntries.size();
        }

        @Override
        public Object getItem(int position) {
            return currentLevelEntries.get(position);
        }

        @Override
        public long getItemId(int position) {
            return ((Object) currentLevelEntries.get(position)).hashCode();
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            convertView = LayoutInflater.from(mContext).inflate(R.layout.log_ovpn,parent,false);
            LogItem logItem = currentLevelEntries.get(position);
            String msg = logItem.getString(mContext);
            String time = getTime(logItem, mTimeFormat);
            TextView msgText = convertView.findViewById(R.id.msgText);
            TextView Textime = convertView.findViewById(R.id.Textime);
            msgText.setTextSize(11);
            Textime.setTextSize(11);
            msgText.setText(Html.fromHtml(msg));
            Textime.setText("["+time+"]");
            return convertView;
        }

        private String getTime(LogItem le, int time) {
            if (time != TIME_FORMAT_NONE) {
                Date d = new Date(le.getLogtime());
                java.text.DateFormat timeformat;
                if (time == TIME_FORMAT_ISO)
                    timeformat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                else
                    timeformat = DateFormat.getTimeFormat(mContext);

                return timeformat.format(d) + " ";

            } else {
                return "";
            }
        }

        @Override
        public int getItemViewType(int position) {
            return 0;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return currentLevelEntries.isEmpty();

        }

        @Override
        public boolean areAllItemsEnabled() {
            return true;
        }

        @Override
        public boolean isEnabled(int position) {
            return true;
        }

        @Override
        public void newLog(LogItem logMessage) {
            Message msg = Message.obtain();
            assert (msg != null);
            msg.what = MESSAGE_NEWLOG;
            Bundle bundle = new Bundle();
            bundle.putParcelable("logmessage", logMessage);
            msg.setData(bundle);
            mHandler.sendMessage(msg);
        }

        @Override
        public boolean handleMessage(Message msg) {
            // We have been called
            if (msg.what == MESSAGE_NEWLOG) {

                LogItem logMessage = msg.getData().getParcelable("logmessage");
                if (addLogMessage(logMessage))
                    for (DataSetObserver observer : observers) {
                        observer.onChanged();
                    }
            } else if (msg.what == MESSAGE_CLEARLOG) {
                for (DataSetObserver observer : observers) {
                    observer.onInvalidated();
                }
                initLogBuffer();
            } else if (msg.what == MESSAGE_NEWTS) {
                for (DataSetObserver observer : observers) {
                    observer.onInvalidated();
                }
            } else if (msg.what == MESSAGE_NEWLOGLEVEL) {
                initCurrentMessages();

                for (DataSetObserver observer : observers) {
                    observer.onChanged();
                }
            }
            return true;
        }

        private void initCurrentMessages() {
            currentLevelEntries.clear();
            for (LogItem li : allEntries) {
                if (li.getVerbosityLevel() <= mLogLevel || mLogLevel == 4)
                    currentLevelEntries.add(li);
            }
        }

        private boolean addLogMessage(LogItem logmessage) {
            allEntries.add(logmessage);

            if (allEntries.size() > MAX_STORED_LOG_ENTRIES) {
                Vector<LogItem> oldAllEntries = allEntries;
                allEntries = new Vector<>(allEntries.size());
                for (int i = 50; i < oldAllEntries.size(); i++) {
                    allEntries.add(oldAllEntries.elementAt(i));
                }
                initCurrentMessages();
                return true;
            } else {
                if (logmessage.getVerbosityLevel() <= mLogLevel) {
                    currentLevelEntries.add(logmessage);
                    return true;
                } else {
                    return false;
                }
            }
        }

        void clearLog() {
            hLogStatus.clearLog();
            mHandler.sendEmptyMessage(MESSAGE_CLEARLOG);
        }

    }

    public void clear(){
        ladapter.clearLog();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.log_fragment);
        ladapter = new LogWindowListAdapter(HarlieLogs.this);
        ladapter.mTimeFormat = this.getPreferences(0).getInt(LOGTIMEFORMAT, 1);
        ListView mLogListView = findViewById(android.R.id.list);
        mLogListView.setAdapter(ladapter);
        mLogListView.setSelection(ladapter.getCount());
        mLogListView.setOnItemLongClickListener((parent, view, position, id) -> {
            String value = parent.getItemAtPosition(position).toString();
            ClipboardManager clipboard = (ClipboardManager)
                getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Log Entry", value);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(HarlieLogs.this, "Copied Entry", Toast.LENGTH_LONG).show();
            return true;
        });
        ((ImageView)findViewById(R.id.clearLogs)).setOnClickListener(p1 -> {
            clear();
        });
    }
    
}

