package jayar.lee.espiritu.config;


import android.database.sqlite.*;
import android.content.*;
import android.database.*;

public class ConfigDataBase extends SQLiteOpenHelper
{
    public static final  String TABLE_NAME = "Harlies_table";
    SQLiteDatabase db;
    public ConfigDataBase(Context context,String name){
        super(context, name+".db", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_NAME + " (ID INTEGER PRIMARY KEY AUTOINCREMENT,DATA TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS "+TABLE_NAME);
    }

    public Cursor getData(String id) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor res =  db.rawQuery( "select * from " + TABLE_NAME +" where id="+id+"", null );
        return res;
    }

    public boolean insertData(String data){
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put("DATA", data);
        long result = db.insert(TABLE_NAME,null,contentValues);
        db.close();
        if(result==-1){
            return false;
        }else{
            return true;
        }
    }

    public boolean updateData(String id,String data){
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put("DATA",data);
        int result =db.update(TABLE_NAME,contentValues,"ID =?",new String[]{id});
        if(result>0){
            return true;
        }else
        {
            return false;
        }
    }
    public Cursor getAllData() { 
        SQLiteDatabase db = this.getWritableDatabase(); 
        Cursor res = db.rawQuery("SELECT * FROM "+TABLE_NAME, null); 
        return res; 
    } 
    public String getData(){
        Cursor rs = getData("1");
        rs.moveToFirst();
        return rs.getString(rs.getColumnIndex("DATA"));
    }
    public Cursor view() {
        Cursor cur = db.rawQuery("SELECT * FROM "+TABLE_NAME, null);
        return cur;

    }
    public boolean isExist(String id){
        SQLiteDatabase db = this.getReadableDatabase();
        String sql ="SELECT ID FROM "+TABLE_NAME+" WHERE ID="+id; 
        Cursor cursor= db.rawQuery(sql, null);
        //Log("Cursor Count : " + cursor.getCount());
        if (cursor.getCount() > 0){
            //PID Found
            return true;
        }else{
            //PID Not Found 
            return false;
        }

    }
}


