package com.sms2vps.app;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.Cursor;
import android.content.ContentValues;
import java.util.ArrayList;
import java.util.List;
import android.util.Log;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "sms_database";
    private static final int DATABASE_VERSION = 6; // Versio päivitetty Google-sarakkeiden lisäämisen vuoksi
    private static final String TABLE_SMS_MESSAGES = "sms_messages";
    private static final String TABLE_MMS_MESSAGES = "mms_messages";
    private static final String COLUMN_SENDER = "sender";
    private static final String COLUMN_MESSAGE = "message";
    private static final String COLUMN_TIMESTAMP = "timestamp";
    private static final String COLUMN_IS_READ = "is_read";
    private static final String COLUMN_UPLOADED_TO_VPS = "uploaded_to_vps"; // Uusi sarake
    private static final String COLUMN_UPLOAD_FAILED = "upload_failed"; // Uusi sarake
    private static final String COLUMN_GOOGLE_SENT = "google_sent"; // Google-lähetyksen tila
    private static final String COLUMN_GOOGLE_FAILED = "google_failed"; // Google-lähetyksen virhetila
    private static final String COLUMN_DRIVE_SENT = "drive_sent";
    private static final String COLUMN_DRIVE_FAILED = "drive_failed";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_SMS_TABLE = "CREATE TABLE " + TABLE_SMS_MESSAGES + "("
                + COLUMN_SENDER + " TEXT, "
                + COLUMN_MESSAGE + " TEXT, "
                + COLUMN_TIMESTAMP + " INTEGER DEFAULT CURRENT_TIMESTAMP, "
                + COLUMN_IS_READ + " INTEGER DEFAULT 0, "
                + COLUMN_UPLOADED_TO_VPS + " INTEGER DEFAULT 0, "
                + COLUMN_UPLOAD_FAILED + " INTEGER DEFAULT 0, "
                + COLUMN_GOOGLE_SENT + " INTEGER DEFAULT 0, "
                + COLUMN_GOOGLE_FAILED + " INTEGER DEFAULT 0, "
                + COLUMN_DRIVE_SENT + " INTEGER DEFAULT 0, "
                + COLUMN_DRIVE_FAILED + " INTEGER DEFAULT 0)"; 
        db.execSQL(CREATE_SMS_TABLE);

        String CREATE_MMS_TABLE = "CREATE TABLE " + TABLE_MMS_MESSAGES + "("
                + COLUMN_SENDER + " TEXT, "
                + COLUMN_MESSAGE + " TEXT, "
                + COLUMN_TIMESTAMP + " INTEGER DEFAULT CURRENT_TIMESTAMP)"; 
        db.execSQL(CREATE_MMS_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE " + TABLE_SMS_MESSAGES + 
                      " ADD COLUMN " + COLUMN_IS_READ + " INTEGER DEFAULT 0");
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE " + TABLE_SMS_MESSAGES + 
                      " ADD COLUMN " + COLUMN_UPLOADED_TO_VPS + " INTEGER DEFAULT 0");
            db.execSQL("ALTER TABLE " + TABLE_SMS_MESSAGES + 
                      " ADD COLUMN " + COLUMN_UPLOAD_FAILED + " INTEGER DEFAULT 0");
        }
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE " + TABLE_SMS_MESSAGES + 
                      " ADD COLUMN " + COLUMN_GOOGLE_SENT + " INTEGER DEFAULT 0");
            db.execSQL("ALTER TABLE " + TABLE_SMS_MESSAGES + 
                      " ADD COLUMN " + COLUMN_GOOGLE_FAILED + " INTEGER DEFAULT 0");
        }
        if (oldVersion < 6) {
            db.execSQL("ALTER TABLE " + TABLE_SMS_MESSAGES + 
                      " ADD COLUMN " + COLUMN_DRIVE_SENT + " INTEGER DEFAULT 0");
            db.execSQL("ALTER TABLE " + TABLE_SMS_MESSAGES + 
                      " ADD COLUMN " + COLUMN_DRIVE_FAILED + " INTEGER DEFAULT 0");
        }
    }

    public List<SMSConversation> getAllConversations() {
        List<SMSConversation> conversations = new ArrayList<>();
        String query = "SELECT sender, "
                    + "(SELECT message FROM " + TABLE_SMS_MESSAGES + " m2 "
                    + "WHERE m2.sender = m1.sender "
                    + "ORDER BY m2.timestamp DESC LIMIT 1) as latest_message, "
                    + "MAX(timestamp) as latest_timestamp, "
                    + "COUNT(CASE WHEN " + COLUMN_IS_READ + " = 0 THEN 1 END) as unread_count, "
                    + "MAX(" + COLUMN_UPLOADED_TO_VPS + ") as is_uploaded, "
                    + "MAX(" + COLUMN_UPLOAD_FAILED + ") as has_failed, "
                    + "MAX(" + COLUMN_GOOGLE_SENT + ") as is_google_sent, "
                    + "MAX(" + COLUMN_GOOGLE_FAILED + ") as has_google_failed, "
                    + "MAX(" + COLUMN_DRIVE_SENT + ") as is_drive_sent, "
                    + "MAX(" + COLUMN_DRIVE_FAILED + ") as has_drive_failed "
                    + "FROM " + TABLE_SMS_MESSAGES + " m1 "
                    + "GROUP BY sender "
                    + "ORDER BY latest_timestamp DESC";

        try (SQLiteDatabase db = this.getReadableDatabase();
             Cursor cursor = db.rawQuery(query, null)) {

            while (cursor.moveToNext()) {
                SMSConversation conversation = new SMSConversation(
                    cursor.getString(0),  // sender
                    cursor.getString(1),  // latest_message
                    String.valueOf(cursor.getLong(2)),  // latest_timestamp
                    cursor.getInt(3),  // unread_count
                    cursor.getInt(4) == 1,  // uploaded_to_vps
                    cursor.getInt(5) == 1,  // upload_failed
                    cursor.getInt(6) == 1,  // google_sent
                    cursor.getInt(7) == 1,  // google_failed
                    cursor.getInt(8) == 1,  // drive_sent
                    cursor.getInt(9) == 1   // drive_failed
                );
                conversations.add(conversation);
            }
        }
        return conversations;
    }

    public List<SMSMessage> getMessagesForPhoneNumber(String phoneNumber) {
        List<SMSMessage> messages = new ArrayList<>();
        String query = "SELECT * FROM " + TABLE_SMS_MESSAGES +
                      " WHERE " + COLUMN_SENDER + " = ? " +
                      " ORDER BY " + COLUMN_TIMESTAMP + " DESC";

        try (SQLiteDatabase db = this.getReadableDatabase();
             Cursor cursor = db.rawQuery(query, new String[]{phoneNumber})) {

            while (cursor.moveToNext()) {
                SMSMessage message = new SMSMessage(
                    cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SENDER)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MESSAGE)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP)),
                    cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_IS_READ)) == 1,
                    cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_UPLOADED_TO_VPS)) == 1,
                    cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_UPLOAD_FAILED)) == 1,
                    cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_GOOGLE_SENT)) == 1,
                    cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_GOOGLE_FAILED)) == 1,
                    cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_DRIVE_SENT)) == 1,
                    cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_DRIVE_FAILED)) == 1
                );
                messages.add(message);
            }
        }
        return messages;
    }

    public void markConversationAsRead(String phoneNumber) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_IS_READ, 1);

        db.update(TABLE_SMS_MESSAGES,
                  values,
                  COLUMN_SENDER + " = ?",
                  new String[]{phoneNumber});
    }

    /**
     * Poistaa viestin tietokannasta annetun puhelinnumeron ja aikaleiman perusteella.
     * Tämä metodi poistaa vain yhden viestin kerrallaan.
     *
     * @param phoneNumber Puhelinnumero, jonka viesti poistetaan
     * @param timestamp Viestin aikaleima millisekunteina
     */
    public void deleteMessage(String phoneNumber, long timestamp) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_SMS_MESSAGES,
                COLUMN_SENDER + " = ? AND " + COLUMN_TIMESTAMP + " = ?",
                new String[]{phoneNumber, String.valueOf(timestamp)});
    }

    public void updateVpsUploadStatus(String phoneNumber, long timestamp, boolean uploaded, boolean failed) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_UPLOADED_TO_VPS, uploaded ? 1 : 0);
        values.put(COLUMN_UPLOAD_FAILED, failed ? 1 : 0);
        int rows = db.update(TABLE_SMS_MESSAGES,
                  values,
                  COLUMN_SENDER + " = ? AND " + COLUMN_TIMESTAMP + " = ?",
                  new String[]{phoneNumber, String.valueOf(timestamp)});
        Log.d("VPSStatusUpdate", "updateVpsUploadStatus: phoneNumber=" + phoneNumber + ", timestamp=" + timestamp + ", uploaded=" + uploaded + ", failed=" + failed + ", rowsUpdated=" + rows);
    }
    
    /**
     * Päivittää viestin Google-lähetyksen tilan tietokantaan.
     *
     * @param phoneNumber Puhelinnumero, jonka viestin tila päivitetään
     * @param timestamp Viestin aikaleima millisekunteina
     * @param sent True jos viesti on lähetetty onnistuneesti Googleen
     * @param failed True jos viestin lähetys Googleen epäonnistui
     */
    public void updateGoogleUploadStatus(String phoneNumber, long timestamp, boolean sent, boolean failed) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_GOOGLE_SENT, sent ? 1 : 0);
        values.put(COLUMN_GOOGLE_FAILED, failed ? 1 : 0);
        
        db.update(TABLE_SMS_MESSAGES,
                  values,
                  COLUMN_SENDER + " = ? AND " + COLUMN_TIMESTAMP + " = ?",
                  new String[]{phoneNumber, String.valueOf(timestamp)});
    }
    
    /**
     * Päivittää viestin Drive-lähetyksen tilan tietokantaan.
     *
     * @param phoneNumber Puhelinnumero, jonka viestin tila päivitetään
     * @param timestamp Viestin aikaleima millisekunteina
     * @param sent True jos viesti on lähetetty onnistuneesti Driveen
     * @param failed True jos viestin lähetys Driveen epäonnistui
     */
    public void updateDriveUploadStatus(String phoneNumber, long timestamp, boolean sent, boolean failed) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_DRIVE_SENT, sent ? 1 : 0);
        values.put(COLUMN_DRIVE_FAILED, failed ? 1 : 0);
        
        db.update(TABLE_SMS_MESSAGES,
                  values,
                  COLUMN_SENDER + " = ? AND " + COLUMN_TIMESTAMP + " = ?",
                  new String[]{phoneNumber, String.valueOf(timestamp)});
    }
    
    /**
     * Poistaa kaikki tietyn puhelinnumeron viestit tietokannasta.
     *
     * @param phoneNumber Puhelinnumero, jonka kaikki viestit poistetaan
     */
    public void deleteConversation(String phoneNumber) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_SMS_MESSAGES,
                COLUMN_SENDER + " = ?",
                new String[]{phoneNumber});
    }
    
    /**
     * Hakee viimeisimmän viestin tietyltä lähettäjältä.
     *
     * @param phoneNumber Puhelinnumero, jonka viimeisin viesti haetaan
     * @return Viimeisin SMSMessage-olio tai null jos viestejä ei löydy
     */
    public SMSMessage getLatestMessage(String phoneNumber) {
        String normalizedPhone = normalizePhoneNumber(phoneNumber);
        String query = "SELECT * FROM " + TABLE_SMS_MESSAGES +
                      " WHERE " + COLUMN_SENDER + " = ? " +
                      " ORDER BY " + COLUMN_TIMESTAMP + " DESC LIMIT 1";

        try (SQLiteDatabase db = this.getReadableDatabase();
             Cursor cursor = db.rawQuery(query, new String[]{normalizedPhone})) {

            if (cursor.moveToFirst()) {
                return new SMSMessage(
                    cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SENDER)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MESSAGE)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP)),
                    cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_IS_READ)) == 1,
                    cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_UPLOADED_TO_VPS)) == 1,
                    cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_UPLOAD_FAILED)) == 1,
                    cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_GOOGLE_SENT)) == 1,
                    cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_GOOGLE_FAILED)) == 1,
                    cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_DRIVE_SENT)) == 1,
                    cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_DRIVE_FAILED)) == 1
                );
            }
        }
        return null;
    }
    
    /**
     * Tarkistaa, onko tietyllä puhelinnumerolla viestejä.
     *
     * @param phoneNumber Puhelinnumero, jonka viestit tarkistetaan
     * @return true jos viestejä löytyy, muuten false
     */
    public boolean hasMessages(String phoneNumber) {
        String query = "SELECT COUNT(*) FROM " + TABLE_SMS_MESSAGES +
                      " WHERE " + COLUMN_SENDER + " = ?";

        try (SQLiteDatabase db = this.getReadableDatabase();
             Cursor cursor = db.rawQuery(query, new String[]{phoneNumber})) {

            if (cursor.moveToFirst()) {
                return cursor.getInt(0) > 0; // Palauttaa true jos viestejä on
            }
        }
        return false; // Jos kysely epäonnistuu, oletetaan että viestejä ei ole
    }
    
    /**
     * Lisää uuden viestin tietokantaan.
     *
     * @param sender Viestin lähettäjän puhelinnumero
     * @param message Viestin sisältö
     * @param timestamp Viestin aikaleima millisekunteina
     */
    public void insertMessage(String sender, String message, long timestamp) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        
        // Normalisoidaan puhelinnumero ennen tallennusta
        String normalizedSender = normalizePhoneNumber(sender);
        
        values.put(COLUMN_SENDER, normalizedSender);
        values.put(COLUMN_MESSAGE, message);
        values.put(COLUMN_TIMESTAMP, timestamp);
        values.put(COLUMN_IS_READ, 0); // Uusi viesti on lukematon
        values.put(COLUMN_UPLOADED_TO_VPS, 0); // Ei vielä lähetetty VPS:lle
        values.put(COLUMN_UPLOAD_FAILED, 0); // Ei vielä epäonnistunut
        values.put(COLUMN_GOOGLE_SENT, 0); // Ei vielä lähetetty Googlelle
        values.put(COLUMN_GOOGLE_FAILED, 0); // Ei vielä epäonnistunut Google-lähetys
        values.put(COLUMN_DRIVE_SENT, 0);
        values.put(COLUMN_DRIVE_FAILED, 0);
        
        db.insert(TABLE_SMS_MESSAGES, null, values);
     }
     
    /**
     * Normalisoi puhelinnumeron poistamalla siitä kaikki muut merkit paitsi numerot ja plus-merkki.
     * Tämä helpottaa puhelinnumeroiden vertailua ja hakua tietokannasta.
     *
     * @param phoneNumber Normalisoitava puhelinnumero
     * @return Normalisoitu puhelinnumero
     */
    public static String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null) {
            return "";
        }
        // Säilytetään vain numerot ja plus-merkki
        return phoneNumber.replaceAll("[^0-9+]", "");
    }

    /**
     * Päivittää viestin VPS-upload-statuksen timestampin perusteella.
     */
    public void updateVPSStatus(long timestamp, boolean uploaded, boolean failed) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_UPLOADED_TO_VPS, uploaded ? 1 : 0);
        values.put(COLUMN_UPLOAD_FAILED, failed ? 1 : 0);
        db.update(TABLE_SMS_MESSAGES, values, COLUMN_TIMESTAMP + "=?", new String[]{String.valueOf(timestamp)});
        db.close();
    }
}
