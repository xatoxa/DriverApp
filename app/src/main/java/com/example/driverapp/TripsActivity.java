package com.example.driverapp;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.view.ViewGroup.LayoutParams;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;


import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class TripsActivity extends AppCompatActivity {

    //url to trips
    private static final String url_trips = "http://a0534961.xsph.ru/trip_for_driver.php";

    //JSON node names
    private static final String TAG_TRIP_ID = "t_id";
    private static final String TAG_TRIP_NAME = "t_name";
    private static final String TAG_START_TIME = "t_start_time";
    private static final String TAG_NEXT_TRIP = "t_next_trip";
    private static final String TAG_DRIV_ID = "driv_id";
    private static final String TAG_SUCCESS = "success";
    private static final String TAG_TRIPS = "trips";
    private static final String TAG_DRIV_NAME = "driv_name";
    private static final String TAG_B_ID = "b_id";

    Button btnDone;
    String driv_id, driv_name;
    Intent i;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trips);

        i = new Intent(TripsActivity.this, WorkActivity.class);

        //сбор переданных данных от предыдущего активити
        driv_id = getIntent().getStringExtra(TAG_DRIV_ID);
        driv_name = getIntent().getStringExtra(TAG_DRIV_NAME);

        //вывод Имени пользователя
        String[] spl = driv_name.split(" ");
        TextView txtHello = findViewById(R.id.txtHello);
        txtHello.append(" " + spl[1]);

        //запуск фоновой загрузки поездок
        new GetTrips().execute();

        btnDone = findViewById(R.id.btnTrDone);
        btnDone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                i.putExtra(TAG_DRIV_ID, driv_id);
                i.putExtra(TAG_DRIV_NAME, driv_name);


                startActivity(i);
                finish();
            }
        });
    }

    class GetTrips extends AsyncTask<String, String, String> {
        @Override
        protected void onPreExecute(){
            super.onPreExecute();
            Snackbar.make(findViewById(R.id.TripsActivity), "Поиск в базе данных..", Snackbar.LENGTH_SHORT).show();
        }

        protected String doInBackground(String... args){
            List<List<Integer>> listTrips = new ArrayList<List<Integer>>();
            Stack<Integer> stackTrips = new Stack<>();

            try {
                OkHttpClient client = new OkHttpClient();
                RequestBody formBody =
                        new FormBody.Builder()
                                .add(TAG_DRIV_ID, driv_id)
                                .build();
                Request request =
                        new Request.Builder()
                                .url(url_trips)
                                .post(formBody)
                                .build();
                Response response = client.newCall(request).execute();
                if (!response.isSuccessful()) throw new IOException("Unexpected code" + response);

                JSONObject json = new JSONObject(response.body().string());

                //Log.d("trips", json.toString());
                int success = json.getInt(TAG_SUCCESS);
                JSONArray trips = json.getJSONArray(TAG_TRIPS);

                if(success >= 1){
                    TableLayout tableLayout = findViewById(R.id.tableTrips);
                    int b_id = 0;

                    for(int i = 0; i < success; i++)
                    {
                        JSONObject trip = trips.getJSONObject(i);
                        b_id = trip.getInt(TAG_B_ID);

                        //заполнение таблицы полученными данными
                        TableRow tableRow = new TableRow(TripsActivity.this);
                        tableRow.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

                        String name = trip.getString(TAG_TRIP_NAME);
                        String[] splitName = name.split("\r\n");

                        TextView txtR = new TextView(TripsActivity.this);
                        TextView txtN = new TextView(TripsActivity.this);
                        TextView txtT = new TextView(TripsActivity.this);

                        txtR.setText(splitName[0]);
                        txtN.setText(splitName[1]);
                        txtT.setText(splitName[2]);

                        tableRow.addView(txtR);
                        tableRow.addView(txtN);
                        tableRow.addView(txtT);

                        //работа с таблицей в основном потоке
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                // Stuff that updates the UI
                                tableLayout.addView(tableRow);
                            }
                        });

                        //заполнение массива для передачи в другой активити
                        listTrips.add(Arrays.asList(trip.getInt(TAG_TRIP_ID), trip.getInt(TAG_NEXT_TRIP)));
                    }

                    //формирование стека: поиск по следующим id и добавление текущих id в стек
                    int link = 0;
                    for(int i = 0; i < listTrips.size(); i++) {
                        for (List<Integer> row : listTrips) {
                            if (row.get(1) == link) {
                                link = row.get(0);
                                stackTrips.push(row.get(0));
                            }
                        }
                    }
                    i.putExtra(TAG_TRIPS, stackTrips);
                    i.putExtra(TAG_B_ID, b_id);
                    listTrips.clear();
                    Snackbar.make(findViewById(R.id.TripsActivity), "Готово", Snackbar.LENGTH_SHORT).show();

                    //btnDone.setEnabled(true);
                }else
                {
                    //not found
                    Snackbar.make(findViewById(R.id.TripsActivity), "Не найдено.", Snackbar.LENGTH_SHORT).show();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // Stuff that updates the UI
                            btnDone.setEnabled(false);
                        }
                    });

                }
            }catch (Exception e){
                e.printStackTrace();
            }

            return null;
        }

        protected void onPostExecute(String file_url){
            //Snackbar.make(findViewById(R.id.MainActivity), "Готово!", Snackbar.LENGTH_SHORT).show();
        }
    }
}
