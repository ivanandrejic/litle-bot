package com.home.litlebot;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private Button buttonStop;

    private Button buttonRun;

    private TextView cycle;

    private TextView click;

    private TextView textViewResult;
    private TextView textViewBadCycles;
    private TextView textViewWiFi;

    private SeekBar wifi;

    private String result;

    private int badCycles = 0;

    private boolean badCycle = false;


    private static boolean RUNNING = false;

    private static final int WI_FI_COUNT_DEFAULT = 500;
    private static final int CLICK_COUNT_DEFAULT = 10;
    private static final int CLICK_SLEEP_DEFAULT = 1000;
    private static final int WIFI_SLEEP_DEFAULT = 12000;

    private static int wiFiSleep = WIFI_SLEEP_DEFAULT;

    static List wiFiCounter = new ArrayList();

    private static RestTemplate restTemplate = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        buttonStop = (Button) findViewById(R.id.buttonStop);

        buttonStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                RUNNING = false;
            }
        });

        buttonRun = (Button) findViewById(R.id.buttonRun);

        buttonRun.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!RUNNING) {
                    RUNNING = true;
                    buttonRun.setEnabled(false);
                    wiFiCounter.clear();
                    badCycles = 0;
                    badCycle = false;
                    textViewBadCycles.setText("BAD CYCLES: " + badCycles);
                    Runnable runCycle = new RunCycle();
                    new Handler().postDelayed(runCycle, CLICK_SLEEP_DEFAULT);
                }
            }
        });

        cycle = (TextView) findViewById(R.id.textViewCycle);

        click = (TextView) findViewById(R.id.textViewClick);

        textViewResult = (TextView) findViewById(R.id.textViewResult);

        textViewBadCycles = (TextView) findViewById(R.id.textViewBadCycles);

        textViewWiFi = (TextView) findViewById(R.id.textViewWiFiSleep);

        wifi = (SeekBar) findViewById(R.id.seekBarWiFi);

        wifi.incrementProgressBy(1000);
        wifi.setMax(30000);
        wifi.setProgress(WIFI_SLEEP_DEFAULT);

        wifi.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean b) {

                progress = progress / 1000;
                progress = progress * 1000;

                wiFiSleep = progress;

                textViewWiFi.setText("WiFi sleep: " + (progress/1000) + "s");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        textViewWiFi.setText("WiFi sleep: " + (WIFI_SLEEP_DEFAULT/1000) + "s");
    }

    private class RunCycle implements Runnable {

        WifiManager wifiManager = (WifiManager) getBaseContext().getSystemService(Context.WIFI_SERVICE);

        @Override
        public void run() {

            Log.d(TAG, "turn on");

            wifiManager.setWifiEnabled(true);
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {

                    Log.d(TAG, "turn off");
                    wifiManager.setWifiEnabled(false);

                    if (badCycle) {
                        badCycles++;
                        textViewBadCycles.setText("BAD CYCLES: " + badCycles);
                    }

                    badCycle = false;

                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            wiFiCounter.add(true);

                            Log.d(TAG, "run");
                            if (wiFiCounter.size() >= WI_FI_COUNT_DEFAULT || !RUNNING) {
                                Log.d(TAG, "end");
                                buttonRun.setEnabled(true);
                            } else {
                                cycle.setText("RUNNING CYCLE: " + wiFiCounter.size());
                                RunClick runClick = new RunClick();
                                runClick.counter.add(true);
                                new Handler().postDelayed(runClick, CLICK_SLEEP_DEFAULT);
                            }
                        }
                    }, wiFiSleep);

                }
            }, wiFiSleep);
        }
    }

    private class RunClick implements Runnable {

        List counter = new ArrayList();

        @Override
        public void run() {
            new Click().execute();
            Log.d(TAG, "click");
            click.setText("CLICK: " + counter.size());
            if (counter.size() >= CLICK_COUNT_DEFAULT || wiFiCounter.size() >= WI_FI_COUNT_DEFAULT || badCycle) {
                new Handler().postDelayed(new RunCycle(), CLICK_SLEEP_DEFAULT * 2);
            } else {
                RunClick runClick = new RunClick();
                runClick.counter = counter;
                runClick.counter.add(true);
                new Handler().postDelayed(runClick, CLICK_SLEEP_DEFAULT);
            }
        }
    }

    private class Click extends AsyncTask<Void, Void, Boolean> {

        @Override
        protected Boolean doInBackground(Void... params) {
            try {
                result = plainGet("http://t9.bjfao.gov.cn/t9/t9/subsys/oa/voteEnglish/act/T9VoteEnglishAct/getIntValue.act?articleId=92910&parentId=1164&_=1480934303100");

            } catch (Exception e) {
                Log.e(TAG, e.getLocalizedMessage(), e);
                return false;
            }

            return true;
        }

        @Override
        protected void onPostExecute(final Boolean success) {
            if (success) {
                badCycle = result.contains("var html = '10,0'");
                textViewResult.setText(result);
            } else {

                textViewResult.setText("ERROR");
            }
        }

    }

    private String plainGet(String url) {

        HttpEntity<?> requestEntity = new HttpEntity<Object>(new HttpHeaders());
        ResponseEntity<String> response = null;
        try {
            // Make the HTTP GET request to the Basic Auth protected URL
            Log.d(TAG, "request: " + requestEntity.toString());
            response = getRestTemplate().exchange(url, HttpMethod.GET, requestEntity, String.class);
            Log.d(TAG, "response: " + response.getBody().toString());
        } catch (HttpClientErrorException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
            // TODO Handle 401 Unauthorized response
        }
        assert response != null;
        return response.getBody();
    }



    public RestTemplate getRestTemplate() {

        if (restTemplate == null) {
            restTemplate = new RestTemplate();

            MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter();
            jsonConverter.setObjectMapper(new ObjectMapper());
            List<MediaType> list = new ArrayList<>();
            list.add(new MediaType("application", "json", MappingJackson2HttpMessageConverter.DEFAULT_CHARSET));
            list.add(new MediaType("text", "javascript", MappingJackson2HttpMessageConverter.DEFAULT_CHARSET));
            jsonConverter.setSupportedMediaTypes(list);

            restTemplate.getMessageConverters().add(jsonConverter);
            restTemplate.getMessageConverters().add(new StringHttpMessageConverter());
        }
        return restTemplate;

    }
}
