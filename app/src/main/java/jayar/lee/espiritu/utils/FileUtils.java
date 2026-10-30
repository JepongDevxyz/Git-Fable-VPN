package jayar.lee.espiritu.utils;

import android.content.Context;
import android.net.Uri;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Scanner;

import jayar.lee.espiritu.HarliesApplication;
import com.jepongdevxyz.fablevpn.R;
import jayar.lee.espiritu.config.ConfigUtil;
import jayar.lee.espiritu.logger.hLogStatus;
import jayar.lee.espiritu.utils.de.De;
import jayar.lee.espiritu.utils.de.De2;
import jayar.lee.espiritu.utils.en.En;
import jayar.lee.espiritu.utils.en.En2;
import android.annotation.SuppressLint;

public class FileUtils {
    @SuppressLint("NewApi")
    @SuppressWarnings("deprecation")
    public static boolean copyToClipboard(Context context, String text){
        try{
            int sdk = android.os.Build.VERSION.SDK_INT;
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) context.getSystemService(context.CLIPBOARD_SERVICE);
            if (sdk < android.os.Build.VERSION_CODES.HONEYCOMB) {
                clipboard.setText(text);
            }
            else {
                android.content.ClipData clip = android.content.ClipData.newPlainText("Message", text);
                clipboard.setPrimaryClip(clip);
            }
            return (clipboard!=null)?true:false;
        } catch (Exception e) {
            hLogStatus.logDebug(e.getMessage());
            return false;
        }
    }
    public static boolean saveTextFile(File file, String contents){
        try {
            if (!file.exists()) {
                file.createNewFile();
            }
            FileWriter writer = new FileWriter(file, false);
            writer.write(contents);
            writer.close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    public static String readFromRaw(Context context, int resId) {
        InputStream in = context.getResources().openRawResource(resId);
        Scanner scanner = new Scanner(in,"UTF-8").useDelimiter("\\A");
        StringBuilder sb = new StringBuilder();
        while (scanner.hasNext()) {
            sb.append(scanner.next());
        }
        scanner.close();
        return sb.toString();
    }
    public static String readTextFile(Context c,Uri uri){
        BufferedReader reader = null;
        StringBuilder builder = new StringBuilder();
        try {
            reader = new BufferedReader(new InputStreamReader(c.getContentResolver().openInputStream(uri)));
            String line = "";
            while ((line = reader.readLine()) != null) {
                builder.append(line);
                builder.append("\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (reader != null){
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return builder.toString();
    }

    public static String showJson(String msg){
        try {
            return XxTea.decryptBase64StringToString(msg, ConfigUtil._a());
        }catch (Exception e){
            hLogStatus.logDebug(e.getMessage());
            return "";
        }
    }

    public static String hideJson(String msg){
        try {
            return XxTea.encryptToBase64String(msg, ConfigUtil._a());
        }catch (Exception e){
            hLogStatus.logDebug(e.getMessage());
            return "";
        }
    }

    private static String str = new String(new byte[]{57,57,53,48,});
    public static String _d2(String msg){
        try {
            De de = new De();
            de.setMethod(new De2());
            String d1 = XxTea.decryptBase64StringToString(msg, ConfigUtil._a());
            return de.decryptString(d1, Integer.parseInt(str));
        }catch (Exception e){
            hLogStatus.logDebug(e.getMessage());
            return "";
        }
    }

    public static String _e2(String msg){
        try {
            En encrypter = new En();
            encrypter.setMethod(new En2());
            String e1 = encrypter.encryptString(msg,Integer.parseInt(str));
            return XxTea.encryptToBase64String(e1, ConfigUtil._a());
        }catch (Exception e){
            hLogStatus.logDebug(e.getMessage());
            return "";
        }
    }

    public static String _d1(String msg){
        try {
            De de = new De();
            de.setMethod(new De2());
            return de.decryptString(msg, Integer.parseInt(str));
        }catch (Exception e){
            hLogStatus.logDebug(e.getMessage());
            return "";
        }
    }

    public static class FileTooLarge extends IOException {
        public FileTooLarge(String fn, long max_size) {
            super(String.format(HarliesApplication.resString(R.string.file_too_large), new Object[]{fn, Long.valueOf(max_size)}));
        }
    }
    public static String readFile(String path, long max_len) throws IOException {
        return readStream(new FileInputStream(path), max_len, path);
    }
    public static String readUri(Context context, Uri uri, long max_len) throws IOException {
        return readStream(context.getContentResolver().openInputStream(uri), max_len, uriBasename(uri));
    }
    public static String readAsset(Context context, String filename) throws IOException {
        return readStream(context.getResources().getAssets().open(filename), 0, filename);
    }
    public static String readFileAppPrivate(Context context, String filename) throws IOException {
        return readStream(context.openFileInput(filename), 0, filename);
    }
    public static void writeFileAppPrivate(Context context, String filename, String content) throws IOException {
        FileOutputStream fos = context.openFileOutput(filename, 0);
        try {
            fos.write(content.getBytes());
        } finally {
            fos.close();
        }
    }
    public static String readStream(InputStream stream, long max_len, String fn) throws IOException {
        try {
            Reader reader = new BufferedReader(new InputStreamReader(stream));
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[4096];
            while (true) {
                int read = reader.read(buffer, 0, buffer.length);
                if (read <= 0) {
                    break;
                }
                builder.append(buffer, 0, read);
                if (max_len > 0 && ((long) builder.length()) > max_len) {
                    throw new FileTooLarge(fn, max_len);
                }
            }
            String stringBuilder = builder.toString();
            return stringBuilder;
        } finally {
            stream.close();
        }
    }
    public static byte[] readFileByteArray(String path, long max_len) throws IOException {
        File file = new File(path);
        InputStream is = new FileInputStream(file);
        try {
            long length = file.length();
            if ((max_len <= 0 || length <= max_len) && length <= 2147483647L) {
                byte[] bytes = new byte[((int) length)];
                int offset = 0;
                while (offset < bytes.length) {
                    int numRead = is.read(bytes, offset, bytes.length - offset);
                    if (numRead < 0) {
                        break;
                    }
                    offset += numRead;
                }
                if (offset >= bytes.length) {
                    return bytes;
                }
                throw new IOException("Could not completely read file: " + path);
            }
            throw new FileTooLarge(path, max_len);
        } finally {
            is.close();
        }
    }
    public static boolean deleteFile(String path) {
        if (path != null) {
            return new File(path).delete();
        }
        return false;
    }

    public static boolean renameFile(String from_path, String to_path) {
        if (from_path == null || to_path == null) {
            return false;
        }
        return new File(from_path).renameTo(new File(to_path));
    }

    public static String basename(String path) {
        if (path != null) {
            return new File(path).getName();
        }
        return null;
    }

    public static String dirname(String path) {
        if (path != null) {
            return new File(path).getParent();
        }
        return null;
    }

    public static String uriBasename(Uri uri) {
        if (uri != null) {
            return uri.getLastPathSegment();
        }
        return null;
    }
}
