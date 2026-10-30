package jayar.lee.espiritu.view;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.util.Log;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.XAxis.XAxisPosition;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.jepongdevxyz.fablevpn.R;
import jayar.lee.espiritu.config.ConfigUtil;
import jayar.lee.espiritu.logger.TrafficHistory;
import jayar.lee.espiritu.logger.hLogStatus;

import java.util.ArrayList;
import java.util.LinkedList;
import static java.lang.Math.max;
import java.util.Locale;

public class GraphHelper {
    LinkedList<Entry> dataIn;
    LinkedList<Entry> dataOut;
    private int mColourIn;
    private int mColourOut;
    private static Handler mHandler;
    private static GraphHelper m_GraphHelper;
    private String TAG = "GraphHelper";
    private int mColor;
    private Context mContext;
    private LineChart mLineChart;
    private boolean mLogScale = false;
    private ConfigUtil mConfig;
    private static final int TIME_PERIOD_MINUTES = 1;
    private static final int TIME_PERIOD_HOURS = 2;
    private StatisticGraphData.DataTransferStats _upDateBytes;
    
    public Runnable triggerRefresh = new Runnable() {
        @Override
        public void run() {
            getGraphView();
            GraphHelper.mHandler.postDelayed(this, (long) 1000);
        }
    };

    public static synchronized GraphHelper getHelper() {
        GraphHelper graphHelper;
        synchronized (GraphHelper.class) {
            if (m_GraphHelper == null) {
                m_GraphHelper = new GraphHelper();
            }
            if (mHandler == null) {
                mHandler = new Handler();
            }
            graphHelper = m_GraphHelper;
        }
        return graphHelper;
    }

    public GraphHelper color(int i) {
        this.mColor = i;
        return m_GraphHelper;
    }

    public GraphHelper chart(LineChart lineChart) {
        this.mLineChart = lineChart;
        return m_GraphHelper;
    }

    public GraphHelper with(Context c) {
        this.mContext = c;
        mConfig = ConfigUtil.getInstance(c);
        mColourIn = Color.GREEN;
        mColourOut = Color.RED;
        _upDateBytes = StatisticGraphData.getStatisticData().getDataTransferStats();
        return m_GraphHelper;
    }

    public void getGraphView() {
        try {
            this.mLineChart.getDescription().setEnabled(false);
            this.mLineChart.setTouchEnabled(false);
            this.mLineChart.setDrawGridBackground(false);
            this.mLineChart.getLegend().setEnabled(true);
            this.mLineChart.getLegend().setTextColor(Color.WHITE);
            XAxis xAxis = this.mLineChart.getXAxis();
            xAxis.setPosition(XAxisPosition.BOTTOM);
            xAxis.setDrawGridLines(false);
            xAxis.setLabelCount(0, false);
            xAxis.setDrawAxisLine(false);
            xAxis.setDrawLabels(true);
            xAxis.setTextSize(7);
            xAxis.setTextColor(Color.WHITE);
            xAxis.setValueFormatter((value, axis) -> String.format(Locale.getDefault(), "%.0f\u2009s ago", (axis.getAxisMaximum() - value) / 10));
            YAxis axisLeft = this.mLineChart.getAxisLeft();
            axisLeft.setLabelCount(5, false);
            axisLeft.setDrawAxisLine(false);
            axisLeft.setTextColor(Color.WHITE);
            axisLeft.setValueFormatter((value, axis) -> {
                if (mLogScale && value < 2.1f)
                    return "< 100\u2009bit/s";
                if (mLogScale)
                    value = (float) Math.pow(10, value) / 8;
                return humanReadableByteCount((long) value, true);
            });
            this.mLineChart.getAxisRight().setEnabled(false);
            LineData dataSet = getDataSet1(0);
            /*if(mConfig.getServerType().equals("UDP") || mConfig.getServerType().equals("v2ray")){
                dataSet = getDataSet2(0);
            }else {
                dataSet = getDataSet1(0);
            }*/
            float yMax = dataSet.getYMax();
            if (this.mLogScale) {
                axisLeft.setAxisMinimum(2.0f);
                axisLeft.setAxisMaximum((float) Math.ceil((double) yMax));
                axisLeft.setLabelCount((int) Math.ceil((double) (yMax - 2.0f)));
            } else {
                axisLeft.setAxisMinimum(0.0f);
                axisLeft.resetAxisMaximum();
                axisLeft.setLabelCount(5);
            }
            if (dataSet.getDataSetByIndex(0).getEntryCount() < 1) {
                this.mLineChart.setData((LineData) null);
            } else {
                this.mLineChart.setData(dataSet);
            }
            this.mLineChart.invalidate();
        } catch (Exception e) {
            Log.e(this.TAG, e.toString());
        }
    }
    
    private LineData getDataSet1(int timeperiod) {
        dataIn = new LinkedList<>();
        dataOut = new LinkedList<>();
        long interval;
        long totalInterval;
        LinkedList<TrafficHistory.TrafficDatapoint> list;
        switch (timeperiod) {
            case TIME_PERIOD_HOURS:
                list = hLogStatus.trafficHistory.getHours();
                interval = TrafficHistory.TIME_PERIOD_HOURS;
                totalInterval = 0;
                break;
            case TIME_PERIOD_MINUTES:
                list = hLogStatus.trafficHistory.getMinutes();
                interval = TrafficHistory.TIME_PERIOD_MINTUES;
                totalInterval = TrafficHistory.TIME_PERIOD_HOURS * TrafficHistory.PERIODS_TO_KEEP;
                break;
            default:
                list = hLogStatus.trafficHistory.getSeconds();
                interval = 2 * 1000;
                totalInterval = TrafficHistory.TIME_PERIOD_MINTUES * TrafficHistory.PERIODS_TO_KEEP;
                break;
        }
        if (list.size() == 0) {
            list = TrafficHistory.getDummyList();
        }
        long lastts = 0;
        float zeroValue;
        if (mLogScale)
            zeroValue = 2;
        else
            zeroValue = 0;
        long now = System.currentTimeMillis();
        long firstTimestamp = 0;
        long lastBytecountOut = 0;
        long lastBytecountIn = 0;
        for (TrafficHistory.TrafficDatapoint tdp : list) {
            if (totalInterval != 0 && (now - tdp.timestamp) > totalInterval)
                continue;
            if (firstTimestamp == 0) {
                firstTimestamp = list.peek().timestamp;
                lastBytecountIn = list.peek().in;
                lastBytecountOut = list.peek().out;
            }
            float t = (tdp.timestamp - firstTimestamp) / 100f;
            float in = (tdp.in - lastBytecountIn) / (float) (interval / 1000);
            float out = (tdp.out - lastBytecountOut) / (float) (interval / 1000);
            lastBytecountIn = tdp.in;
            lastBytecountOut = tdp.out;
            if (mLogScale) {
                in = max(2f, (float) Math.log10(in * 8));
                out = max(2f, (float) Math.log10(out * 8));
            }
            if (lastts > 0 && (tdp.timestamp - lastts > 2 * interval)) {
                dataIn.add(new Entry((lastts - firstTimestamp + interval) / 100f, zeroValue));
                dataIn.add(new Entry(t - interval / 100f, zeroValue));
                dataOut.add(new Entry((lastts - firstTimestamp + interval) / 100f, zeroValue));
                dataOut.add(new Entry(t - interval / 100f, zeroValue));
            }
            lastts = tdp.timestamp;
            dataIn.add(new Entry(t, in));
            dataOut.add(new Entry(t, out));
        }
        if (lastts < now - interval) {
            if (now - lastts > 2 * interval * 1000) {
                dataIn.add(new Entry((lastts - firstTimestamp + interval * 1000) / 100f, zeroValue));
                dataOut.add(new Entry((lastts - firstTimestamp + interval * 1000) / 100f, zeroValue));
            }
            dataOut.add(new Entry((now - firstTimestamp) / 100, zeroValue));
            dataIn.add(new Entry((now - firstTimestamp) / 100, zeroValue));
        }
        ArrayList<ILineDataSet> dataSets = new ArrayList<>();
        LineDataSet outdata = new LineDataSet(dataOut, "Bytes out");
        LineDataSet indata = new LineDataSet(dataIn, "Bytes in");
        indata.setColor(Color.BLACK);
        setLineDataAttributes(outdata, mColourOut);
        setLineDataAttributes(indata, mColourIn);
        dataSets.add(outdata);
        dataSets.add(indata);
        return new LineData(dataSets);
    }

    private void setLineDataAttributes(LineDataSet dataSet, int colour) {
        dataSet.setLineWidth(1);
        dataSet.setCircleRadius(1);
        dataSet.setDrawCircles(false);
        dataSet.setDrawFilled(true);
        dataSet.setFillAlpha(80);
        dataSet.setFillColor(mContext.getResources().getColor(R.color.colorAccent));
        dataSet.setColor(colour);
        dataSet.setMode(LineDataSet.Mode.LINEAR);
        dataSet.setDrawHorizontalHighlightIndicator(false);   
        dataSet.setDrawValues(false);
    }

    private String humanReadableByteCount(long bytes, boolean speed) {
        if (speed)bytes = bytes * 8;
        int unit = speed ? 1000 : 1024;
        int exp = Math.max(0, Math.min((int) (Math.log(bytes) / Math.log(unit)), 3));
        float bytesUnit = (float) (bytes / Math.pow(unit, exp));
        if (speed)
            switch (exp) {
                case 0:
                    return mContext.getString(R.string.bits_per_second, bytesUnit);
                case 1:
                    return mContext.getString(R.string.kbits_per_second, bytesUnit);
                case 2:
                    return mContext.getString(R.string.mbits_per_second, bytesUnit);
                default:
                    return mContext.getString(R.string.gbits_per_second, bytesUnit);
            }
        else
            switch (exp) {
                case 0:
                    return mContext.getString(R.string.volume_byte, bytesUnit);
                case 1:
                    return mContext.getString(R.string.volume_kbyte, bytesUnit);
                case 2:
                    return mContext.getString(R.string.volume_mbyte, bytesUnit);
                default:
                    return mContext.getString(R.string.volume_gbyte, bytesUnit);

            }
    }

    public void start() {
        GraphHelper.mHandler.removeCallbacks(triggerRefresh);
        getGraphView();
        GraphHelper.mHandler.postDelayed( triggerRefresh, (long) 1000);
    }

    public void stop() {
        mHandler.removeCallbacks(this.triggerRefresh);
        this.mLineChart.invalidate();
    }


}
