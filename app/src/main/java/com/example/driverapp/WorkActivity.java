package com.example.driverapp;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Stack;
import java.sql.Time;
import java.util.Timer;
import java.util.TimerTask;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class WorkActivity extends AppCompatActivity implements DriverLocListenerInterface{
    //переменные местоположения
    LocationManager locationManager;
    DriverLocationListener driverLocationListener;
    double lambda = 0.0006;                         //радиус квадрата заданной координаты остановки

    //JSON node names
    private static final String TAG_TRIP_ID = "t_id";
    private static final String TAG_DRIV_ID = "driv_id";
    private static final String TAG_SUCCESS = "success";
    private static final String TAG_TRIPS = "trips";
    private static final String TAG_DRIV_NAME = "driv_name";
    private static final String TAG_SCHEDULE = "schedule";
    private static final String TAG_S_NAME = "stop_name";
    private static final String TAG_S_TIME = "s_time";
    private static final String TAG_S_GEOTAG = "geotag";
    private static final String TAG_S_REAL_TIME = "real_time";
    private static final String TAG_B_ID = "b_id";
    private static final String TAG_B_LOC = "location";
    private static final String TAG_TEXT = "msg_text";

    //url to
    private static final String url_schedule = "http://a0534961.xsph.ru/schedule_load.php";
    private static final String url_insert = "http://a0534961.xsph.ru/real_arrival_insert.php";
    private static final String url_update = "http://a0534961.xsph.ru/update_bus_location.php";
    private static final String url_check_messages_driv = "http://a0534961.xsph.ru/check_messages_driv.php";
    private static final String url_send_message = "http://a0534961.xsph.ru/send_message_from_driver.php";

    //локальные переменные
    String driv_id, driv_name;
    int b_id;
    Stack<Integer> stackTrips = new Stack<>();
    Button btnUploadDB, btnUpdateDB, btnDispMsg;
    TextView txtDelay;
    SQLiteDatabase scheduleDB;

    private void init()
    {
        //инициализация переменных местоположения
        locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        driverLocationListener = new DriverLocationListener();
        driverLocationListener.setDriverLocLisInterface(this);

        //сбор переданных данных от предыдущего активити
        driv_id = getIntent().getStringExtra(TAG_DRIV_ID);
        driv_name = getIntent().getStringExtra(TAG_DRIV_NAME);
        b_id = getIntent().getIntExtra(TAG_B_ID, 0);
        stackTrips.addAll(getIntent().getIntegerArrayListExtra(TAG_TRIPS));

        //Log.d("trips", stackTrips.toString());

        //иницализация View
        btnUpdateDB = findViewById(R.id.btnUpdateDB);
        btnUploadDB = findViewById(R.id.btnUploadDB);
        btnDispMsg = findViewById(R.id.btnDispMsg);

        txtDelay = findViewById(R.id.countDelay);

        //открытие локальной БД и создание таблицы, если её нет
        scheduleDB = openOrCreateDatabase("driverApp.db", MODE_PRIVATE, null);
        scheduleDB.execSQL("CREATE TABLE IF NOT EXISTS schedule (t_id INT(10), stop_name VARCHAR(200), " +
                "s_time TIME, geotag VARCHAR(200))");

        //запрос функции проверки разрешений
        checkPermissions();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_work);

        //инициализация переменных
        init();

        //загрузка БД с сервера в локальную БД
        btnUploadDB.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(stackTrips.size() > 0){
                    scheduleDB.delete("schedule", null, null);
                    new UploadDB().execute();
                    btnUploadDB.setEnabled(false);
                }else{
                    Snackbar.make(findViewById(R.id.WorkActivity), "Не найдено.", Snackbar.LENGTH_SHORT).show();
                }
            }
        });

        //принудительное обновление таблицы в активити из SQLite
        btnUpdateDB.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //new UpdateTable().execute();
                updateTable();

            }
        });

        //вывод диалогового окна для отправки сообщений диспетчеру
        btnDispMsg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LayoutInflater li = LayoutInflater.from(WorkActivity.this);
                View msgSendView = li.inflate(R.layout.dialog_send_message, null);
                //Создаем AlertDialog
                AlertDialog.Builder mDialogBuilder = new AlertDialog.Builder(WorkActivity.this);

                //Настраиваем prompt.xml для нашего AlertDialog:
                mDialogBuilder.setView(msgSendView);

                //Настраиваем отображение поля для ввода текста в открытом диалоге:
                final EditText eTxtMsg = (EditText) msgSendView.findViewById(R.id.eTxtMsg);

                //Настраиваем сообщение в диалоговом окне:
                mDialogBuilder
                        .setCancelable(false)
                        .setPositiveButton("Отправить",
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog,int id) {
                                        //Вводим текст и отображаем в строке ввода на основном экране:
                                        String msgText = eTxtMsg.getText().toString();
                                        if(!msgText.equals("")) {
                                            new SendMessage().execute(msgText, Integer.toString(stackTrips.peek()));
                                        }else{
                                            Snackbar.make(findViewById(R.id.WorkActivity), "Введите текст", Snackbar.LENGTH_SHORT).show();

                                        }
                                    }
                                })
                        .setNegativeButton("Отмена",
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog,int id) {
                                        dialog.cancel();
                                    }
                                });

                //Создаем AlertDialog:
                AlertDialog alertDialog = mDialogBuilder.create();

                //и отображаем его:
                alertDialog.show();
            }
        });

        Timer myTimer = new Timer(); // Создаем таймер
        myTimer.schedule(new TimerTask() { // Определяем задачу
            @Override
            public void run() {
                new CheckMessagesDriv().execute();
            };
        }, 0L, 10L * 1000); // интервал - 10000 миллисекунд, 0 миллисекунд до первого запуска.
    }

    public void updateTable()
    {
        TableLayout table = findViewById(R.id.tableStops);
//        int count = table.getChildCount();
//        Log.d("count", Integer.toString(count));
//        if(count > 0) {
//            for (int i = 0; i < count; i++) {
//                View child = table.getChildAt(i);
//                if (child instanceof TableRow) ((ViewGroup) child).removeAllViews();
//            }
//        }
//        Log.d("count_after", Integer.toString(count));

        table.removeAllViews();

        if(stackTrips.size() != 0) {
            try {
                //запрос к БД SQLite
                Cursor cursor = scheduleDB.query("schedule",
                        new String[]{"stop_name", "s_time"},
                        "t_id = ?",
                        new String[]{stackTrips.peek().toString()},
                        null,
                        null,
                        "s_time ASC");

                //заполнение таблицы расписания
                int i = 0;
                while (cursor.moveToNext()) {
                    //Log.d("schedule", cursor.toString());

                    TableRow tableRow = new TableRow(WorkActivity.this);
                    tableRow.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                    TextView txtStopName = new TextView(WorkActivity.this);
                    TextView txtStopTime = new TextView(WorkActivity.this);
                    if (i == 0) {
                        txtStopName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
                        txtStopTime.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
                        txtStopName.setTextColor(Color.BLACK);
                        txtStopTime.setTextColor(Color.BLACK);
                    } else {
                        txtStopName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
                        txtStopTime.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
                    }
                    i++;

                    txtStopName.setText(cursor.getString(0));
                    txtStopTime.setText(cursor.getString(1).substring(0, 5));

                    tableRow.addView(txtStopName);
                    tableRow.addView(txtStopTime);
                    table.addView(tableRow);
                }

                cursor.close();
            } catch (NullPointerException e) {
                e.printStackTrace();
            }
        }
    }

    //проверка разрешений
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == 100 && grantResults[0] == RESULT_OK)
        {
            checkPermissions();
        }
        /*else
        {
            Toast.makeText(this,"Выдайте все разрешения приложению.", Toast.LENGTH_SHORT).show();
        }*/
    }

    //запрос разрешений
    private void checkPermissions()
    {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED)
        {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION}, 100);
        }
        else
        {
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    10000,
                    2,
                    driverLocationListener);
        }
    }

    //действия при изменении местоположения
    @Override
    public void onLocationChanged(Location location) {
        if(location != null) {
            //инициализация переменных
            ArrayList<String> listSLoc = new ArrayList<>();
            ArrayList<Time> listSTime = new ArrayList<>();
            ArrayList<String> listSName = new ArrayList<>();

            try {
                //запрос к локальной БД SQLite
                Cursor cursor = scheduleDB.query("schedule",
                        new String[]{"s_time", "geotag", "stop_name"},
                        "t_id = ?",
                        new String[]{stackTrips.peek().toString()},
                        null,
                        null,
                        "s_time ASC");

                //заполнение таблицы
                while (cursor.moveToNext()) {
                    listSTime.add(Time.valueOf(cursor.getString(0)));
                    listSLoc.add(cursor.getString(1));
                    listSName.add(cursor.getString(2));
                }
                cursor.close();

                //отправка местоположения на сервер
                new UpdateLocOnServer().execute(
                        Integer.toString(b_id),
                        Double.toString(location.getLatitude()),
                        Double.toString(location.getLongitude()));

            } catch (NullPointerException e) {
                e.printStackTrace();
            }

            //поиск совпадений местоположения
            for (int i = 0; i < listSLoc.size(); i++) {
                double lat, lon;
                if(listSLoc.get(i).equals("")) continue;
                String[] spl = listSLoc.get(i).split(", ");
                lat = Double.parseDouble(spl[0]);
                lon = Double.parseDouble(spl[1]);

                //проверка позиционирования в заданных координатах
                if (location.getLatitude() < (lat + lambda) && location.getLatitude() > (lat - lambda)
                        && location.getLongitude() < (lon + lambda) && location.getLongitude() > (lon - lambda)) {
                    //обновление TextView Опоздание
                    String[] nTime = listSTime.get(i).toString().split(":");
                    int nHour = Integer.parseInt(nTime[0]);
                    int nMin = Integer.parseInt(nTime[1]);

                    SimpleDateFormat spf = new SimpleDateFormat("HH:mm:ss");
                    Date cDate = new Date();

                    String[] cTime = spf.format(cDate).split(":");
                    int cHour = Integer.parseInt(cTime[0]);
                    int cMin = Integer.parseInt(cTime[1]);
                    int delay = (cHour-nHour)*60 + cMin - nMin;
                    txtDelay.setText(Integer.toString(delay));

                    //создание строки в таблице real_arrival на сервере
                    new InsertIntoDB().execute(listSName.get(i), spf.format(cDate));

                    //удаление остановки из таблицы и локальной БД
                    scheduleDB.delete("schedule",
                            "t_id = ? AND s_time = ? AND geotag = ?",
                            new String[]{stackTrips.peek().toString(), listSTime.get(i).toString(), listSLoc.get(i)});

                    //---проверка, что не последняя остановка---
                    Cursor cursor = scheduleDB.query("schedule",
                            new String[]{"t_id"},
                            "t_id = ?",
                            new String[] {stackTrips.peek().toString()},
                            null,
                            null,
                            null);

                    //если остановки этого рейса закончились и остались ещё рейсы
                    if(cursor.getCount() == 0 && stackTrips.size()-1 != 0)
                    {
                        stackTrips.pop();
                        btnUploadDB.callOnClick();
                    }else
                        if(cursor.getCount() == 0 && stackTrips.size()-1 == 0) //если рейсов больше нет
                        {
                            stackTrips.clear();
                            txtDelay.setText("0");
                            Snackbar.make(findViewById(R.id.WorkActivity), "Спасибо за работу!", Snackbar.LENGTH_SHORT).show();
                        }

                    updateTable();
                    cursor.close();

                    break;
                }
            }
            listSLoc.clear();
            listSTime.clear();
        }
    }

    //класс загрузки данных из БД на сервере
    class UploadDB extends AsyncTask<String, String, String> {
        @Override
        protected void onPreExecute(){
            super.onPreExecute();
        }

        protected String doInBackground(String... args){
            try {
                OkHttpClient client = new OkHttpClient();
                RequestBody formBody =
                        new FormBody.Builder()
                                .add(TAG_TRIP_ID, stackTrips.peek().toString())
                                .build();
                Request request =
                        new Request.Builder()
                                .url(url_schedule)
                                .post(formBody)
                                .build();
                Response response = client.newCall(request).execute();
                if (!response.isSuccessful()) throw new IOException("Unexpected code" + response);

                JSONObject json = new JSONObject(response.body().string());

                //Log.d("schedule", json.toString());

                int success = json.getInt(TAG_SUCCESS);
                JSONArray schedule = json.getJSONArray(TAG_SCHEDULE);

                if(success >= 1){

                    for(int i = 0; i < success; i++)
                    {
                        JSONObject stop = schedule.getJSONObject(i);

                        ContentValues row = new ContentValues();
                        row.put(TAG_TRIP_ID, stop.getInt(TAG_TRIP_ID));
                        row.put(TAG_S_NAME, stop.getString(TAG_S_NAME));
                        row.put(TAG_S_TIME, stop.getString(TAG_S_TIME));
                        row.put(TAG_S_GEOTAG, stop.getString(TAG_S_GEOTAG));

                        scheduleDB.insert("schedule", null, row);
                    }
                    Snackbar.make(findViewById(R.id.WorkActivity), "Готово.", Snackbar.LENGTH_SHORT).show();

                }else
                {
                    //not found
                    Snackbar.make(findViewById(R.id.WorkActivity), "Не найдено.", Snackbar.LENGTH_SHORT).show();

                }
            }catch (Exception e){
                e.printStackTrace();
            }
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // Stuff that updates the UI
                    updateTable();
                }
            });


            return null;
        }

        protected void onPostExecute(String file_url){
        }
    }

    //класс загрузки данных из БД на сервере
    class InsertIntoDB extends AsyncTask<String, String, String> {
        @Override
        protected void onPreExecute(){
            super.onPreExecute();
        }

        protected String doInBackground(String... args){
            try {
                OkHttpClient client = new OkHttpClient();
                RequestBody formBody =
                        new FormBody.Builder()
                                .add(TAG_TRIP_ID, stackTrips.peek().toString())
                                .add(TAG_S_NAME, args[0])
                                .add(TAG_S_REAL_TIME, args[1])
                                .build();
                Request request =
                        new Request.Builder()
                                .url(url_insert)
                                .post(formBody)
                                .build();
                Response response = client.newCall(request).execute();
                if (!response.isSuccessful()) throw new IOException("Unexpected code" + response);

                JSONObject json = new JSONObject(response.body().string());
                int success = json.getInt(TAG_SUCCESS);
                //Log.d(TAG_SUCCESS, Integer.toString(success));
            }catch (Exception e){
                e.printStackTrace();
            }
            return null;
        }

        protected void onPostExecute(String file_url){
        }
    }

    //класс обновления местоположения в таблице на сервере
    class UpdateLocOnServer extends AsyncTask<String, String, String> {
        @Override
        protected void onPreExecute(){
            super.onPreExecute();
        }

        protected String doInBackground(String... args){
            try {
                OkHttpClient client = new OkHttpClient();
                String loc = args[1] + ", " + args[2];
                Log.d(TAG_B_ID, args[0]);
                Log.d(TAG_B_LOC, loc);
                RequestBody formBody =
                        new FormBody.Builder()
                                .add(TAG_B_ID, args[0])
                                .add(TAG_B_LOC, loc)
                                .build();
                Request request =
                        new Request.Builder()
                                .url(url_update)
                                .post(formBody)
                                .build();
                Response response = client.newCall(request).execute();
                if (!response.isSuccessful()) throw new IOException("Unexpected code" + response);

                JSONObject json = new JSONObject(response.body().string());
                int success = json.getInt(TAG_SUCCESS);
                Log.d(TAG_SUCCESS, Integer.toString(success));
            }catch (Exception e){
                e.printStackTrace();
            }
            return null;
        }

        protected void onPostExecute(String file_url){
        }
    }

    class CheckMessagesDriv extends AsyncTask<String, String, String> {
        @Override
        protected void onPreExecute(){
            super.onPreExecute();
        }

        protected String doInBackground(String... args){
            try {
                OkHttpClient client = new OkHttpClient();
                RequestBody formBody =
                        new FormBody.Builder()
                                .add(TAG_DRIV_ID, driv_id)
                                .build();
                Request request =
                        new Request.Builder()
                                .url(url_check_messages_driv)
                                .post(formBody)
                                .build();
                Response response = client.newCall(request).execute();
                if (!response.isSuccessful()) throw new IOException("Unexpected code" + response);

                JSONObject json = new JSONObject(response.body().string());

                int success = json.getInt(TAG_SUCCESS);
                JSONArray messages = json.getJSONArray("messages");

                if(success >= 1){
                    for (int i = 0; i < success; i++)
                    {
                        JSONObject message = messages.getJSONObject(i);
                        String text = message.getString("msg_text");

                        //работа в основном потоке
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                // Stuff that updates the UI
                                //это для приёма сообщений диспетчера. Вставить в обработчик и при обновлении с сервера выводить.
                                AlertDialog.Builder builder = new AlertDialog.Builder(WorkActivity.this);
                                builder.setTitle("Сообщение от диспетчера!");
                                builder.setMessage(text);
                                builder.setCancelable(true);
                                builder.setPositiveButton("Прочитано", new DialogInterface.OnClickListener() { // Кнопка ОК
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss(); // Отпускает диалоговое окно
                                    }
                                });
                                AlertDialog dialog = builder.create();
                                dialog.show();
                            }
                        });
                    }
                }
            }catch (Exception e){
                e.printStackTrace();
            }

            return null;
        }

        protected void onPostExecute(String file_url){
        }
    }

    class SendMessage extends AsyncTask<String, String, String> {
        @Override
        protected void onPreExecute(){
            super.onPreExecute();
            //Snackbar.make(findViewById(R.id.MessagesActivity), "Поиск в базе данных..", Snackbar.LENGTH_SHORT).show();
        }

        protected String doInBackground(String... args){
            try {
                OkHttpClient client = new OkHttpClient();
                RequestBody formBody =
                        new FormBody.Builder()
                                .add(TAG_DRIV_ID, driv_id)
                                .add(TAG_TEXT, args[0])
                                .add(TAG_TRIP_ID, args[1])
                                .build();
                Request request =
                        new Request.Builder()
                                .url(url_send_message)
                                .post(formBody)
                                .build();
                Response response = client.newCall(request).execute();
                if (!response.isSuccessful()) throw new IOException("Unexpected code" + response);

                JSONObject json = new JSONObject(response.body().string());

                int success = json.getInt(TAG_SUCCESS);
                Log.d("success", TAG_SUCCESS);

                if(success > 0){
                    Snackbar.make(findViewById(R.id.WorkActivity), "Отправлено", Snackbar.LENGTH_SHORT).show();
                }else{
                    Snackbar.make(findViewById(R.id.WorkActivity), "Ошибка отправки", Snackbar.LENGTH_SHORT).show();
                }
            }catch (Exception e){
                e.printStackTrace();
            }

            return null;
        }

        protected void onPostExecute(String file_url){
        }
    }

}