package jayar.lee.espiritu.thread;

import android.content.Context;
import java.io.File;
import java.io.IOException;
import java.io.FileOutputStream;
import android.os.Build.*;
import android.os.Build;

public class TopExceptionHandler implements Thread.UncaughtExceptionHandler {
    private static final String FILE_ERROR = "stack.trace";
    private static TopExceptionHandler mExceptionHandler;
    private Thread.UncaughtExceptionHandler defaultUEH;
    private Context mContext;
    private File mFileTemp;

    public static void init(Context context) {
        if (mExceptionHandler == null) {
            mExceptionHandler = new TopExceptionHandler(context);
        }
        Thread.setDefaultUncaughtExceptionHandler(mExceptionHandler);
    }

    private TopExceptionHandler(Context context) {
        this.defaultUEH = Thread.getDefaultUncaughtExceptionHandler();
        this.mContext = context;
        this.mFileTemp = new File(mContext.getFilesDir(), FILE_ERROR);
    }

    public void uncaughtException(Thread t, Throwable e) {
        StackTraceElement[] arr = e.getStackTrace();

        String report = e.toString()+"\n\n";
        report += "--------- Stack trace ---------\n\n";
        for (int i = 0; i < arr.length; i++) {
            report += "    " + arr[i].toString() + "\n";
        }
        report += "-------------------------------\n\n";

        // If the exception was thrown in a background thread inside
        // AsyncTask, then the actual exception can be found with getCause
        report += "--------- Cause ---------\n\n";
        Throwable cause = e.getCause();
        if (cause != null) {
            report += cause.toString() + "\n\n";
            arr = cause.getStackTrace();
            for (int i = 0; i < arr.length; i++) {
                report += "    " + arr[i].toString() + "\n";
            }
        }
        report += "-------------------------------\n\n";
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("************ APPLICATION CRASH ************\n\n");
        stringBuilder.append(report);
        stringBuilder.append("\n************ DEVICE INFORMATION ***********\n");
        stringBuilder.append("Brand: ");
        stringBuilder.append(Build.BRAND);
        stringBuilder.append("\n");
        stringBuilder.append("Device: ");
        stringBuilder.append(Build.DEVICE);
        stringBuilder.append("\n");
        stringBuilder.append("Model: ");
        stringBuilder.append(Build.MODEL);
        stringBuilder.append("\n");
        stringBuilder.append("Id: ");
        stringBuilder.append(Build.ID);
        stringBuilder.append("\n");
        stringBuilder.append("Product: ");
        stringBuilder.append(Build.PRODUCT);
        stringBuilder.append("\n");
        stringBuilder.append("\n************ FIRMWARE ************\n");
        stringBuilder.append("SDK: ");
        stringBuilder.append(VERSION.SDK);
        stringBuilder.append("\n");
        stringBuilder.append("Release: ");
        stringBuilder.append(VERSION.RELEASE);
        stringBuilder.append("\n");
        stringBuilder.append("Incremental: ");
        stringBuilder.append(VERSION.INCREMENTAL);
        stringBuilder.append("\n");
        stringBuilder.append("Report issue to Developer \n Jay-Ar Lee Espiritu");
        stringBuilder.append("\n");

        writeToFileLog(stringBuilder.toString(), mContext);

        defaultUEH.uncaughtException(t, e);
    }

    private void writeToFileLog(String logError, Context context) {
        File logFile = new File(context.getExternalFilesDir("Bugs"), "AppErrors.txt");
        writeToFile(logError, logFile);
        writeToFile(logError, mFileTemp);
    }

    private void writeToFile(String txt, File file) {
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch(IOException error) {
            }
        }
        try {
            FileOutputStream trace = new FileOutputStream(file);
            trace.write(txt.getBytes());
            trace.close();
        } catch(IOException ioe) {
        }
    }
}