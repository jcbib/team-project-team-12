package cse110.ucsd.team12wwr;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.Context;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.text.DecimalFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cse110.ucsd.team12wwr.fitness.FitnessService;
import cse110.ucsd.team12wwr.fitness.FitnessServiceFactory;
import cse110.ucsd.team12wwr.fitness.GoogleFitAdapter;

public class MainActivity extends AppCompatActivity {
    /* constants */
    final int HEIGHT_FACTOR = 12;
    final double STRIDE_CONVERSION = 0.413;
    final int MILE_FACTOR = 63360;

    SharedPreferences spf;

    /* steps */
    TextView textStep;
    long numSteps = 0;

    /* GoogleFit */
    private static final String TAG = "MainActivity";
    private FitnessService fitnessService;
    private final String fitnessServiceKey = "GOOGLE_FIT";


    /*SERVICE*/
    private PedometerService pedService;
    private boolean isBound = false;
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service){
            PedometerService.LocalService localService = (PedometerService.LocalService)service;
            pedService = localService.getService();
            isBound = true;
            Log.i("MainActivity.onServiceConnected", "PedometerService Connected");
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {isBound = false;}
    };

    /* distance */
    TextView textDist;
    int totalHeight;
    double strideLength;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button launchIntentionalWalkActivity = (Button) findViewById(R.id.btn_start_walk);
        launchIntentionalWalkActivity.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                launchActivity();
            }
        });
        Toolbar toolbar = findViewById(R.id.toolbar);


        // Create and adapt the FitnessService
        FitnessServiceFactory.put(fitnessServiceKey, new FitnessServiceFactory.BluePrint() {
            @Override
            public FitnessService create(MainActivity mainActivity) {
                return new GoogleFitAdapter(mainActivity);
            }
        });
        fitnessService = FitnessServiceFactory.create(fitnessServiceKey, this);
        
        textDist = findViewById(R.id.num_miles);
        textStep = findViewById(R.id.num_steps);

        setSupportActionBar(toolbar);
        closeOptionsMenu();

        // Collect the height from the previous page
        spf = getSharedPreferences("height", MODE_PRIVATE);
        int feet = spf.getInt("feet", 0);
        int inches = spf.getInt("inches", 0);

        totalHeight = inches + ( HEIGHT_FACTOR * feet );
        strideLength = totalHeight * STRIDE_CONVERSION;

        /* PEDOMETER START */
        Log.i("MainActivity.onCreate", "calling fitnessService.setup()");
        fitnessService.setup();
        startStepUpdaterMethod();

    }
    public void startStepUpdaterMethod(){
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.i("MainActivity.startStepUpdaterMethod", "start runner called");
                if(!isBound) {
                    Log.i("MainActivity.startStepUpdaterMethod", "Still waiting for successful bind");
                    handler.postDelayed(this, 2000);
                }
                else{
                    Log.i("MainActivity.startStepUpdaterMethod", "Successful bind achieved");
                    pedService.beginStepTracking(fitnessService);
                }
            }
        }, 1500);
    }
    public void continueStepUpdaterMethod(){
        final Handler bindCheckHandler = new Handler();
        bindCheckHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.i("MainActivity.continueStepUpdaterMethod", "continue Runner Called");
                if(!isBound) {
                    Log.i("MainActivity.continueStepUpdaterMethod", "Still waiting for successful bind");
                    bindCheckHandler.postDelayed(this, 2000);
                }
                else{
                    Log.i("MainActivity.continueStepUpdaterMethod", "Successful bind achieved");
                    final Handler updateStepsHandler = new Handler();
                    updateStepsHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            setStepCount(pedService.getCurrentSteps());
                            updateStepsHandler.postDelayed(this, 3000);
                        }
                    }, 3000);
                }
            }
        }, 1500);
    }
    public void bindPedService(){
        Intent intent = new Intent(this, PedometerService.class);
        //Log.i("MainActivity.bindTheThing", "COMMENCE THE BINDING");
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        //Log.i("MainActivity.bindTheThing", "BIND COMPLETE");
    }
    public void unbindPedometerService(){
        unbindService(serviceConnection);
    }

    public void launchActivity() {
        Intent intent = new Intent(this, IntentionalWalkActivity.class);
        startActivity(intent);
    }
    @Override
    protected void onPause() {
        super.onPause();
        Log.i("MainActivity.onPause", "onPause() has been called");
        stopService(new Intent(this, PedometerService.class));
        isBound = false;
        unbindPedometerService();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i("MainActivity.onResume", "onResume() has been called");
        bindPedService();
        continueStepUpdaterMethod();

        ExecutorService databaseWriteExecutor = Executors.newFixedThreadPool(1);
        databaseWriteExecutor.execute(() -> {
            WalkDatabase walkDb = WalkDatabase.getInstance(this);
            WalkDao dao = walkDb.walkDao();

            Walk newestWalk = dao.findNewestEntry();
            if (newestWalk != null) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TextView stepsWalkText = findViewById(R.id.text_steps_value);
                        TextView distWalkText = findViewById(R.id.text_distance_value);
                        TextView timeWalkText = findViewById(R.id.text_time_value);

                        stepsWalkText.setText(newestWalk.steps);
                        distWalkText.setText(newestWalk.distance);
                        timeWalkText.setText(newestWalk.duration);
                    }
                });
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    //Updates numSteps with pedometer data, sets textDist and textStep
    public void setStepCount(long stepCount) {
        numSteps = stepCount;
        DecimalFormat df = new DecimalFormat("#.##");
        textDist.setText(df.format((strideLength / MILE_FACTOR) * numSteps));
        textStep.setText(""+numSteps);
    }
    //For GoogleFit
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
//       If authentication was required during google fit setup, this will be called after the user authenticates
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == fitnessService.getRequestCode()) {
                fitnessService.updateStepCount();
            }
        } else {
            Log.e(TAG, "ERROR, google fit result code: " + resultCode);
        }
    }

    @Override
    protected void onDestroy() {
        Log.i("MainActivity.onDestroy", "onDestroy() has been called");
        if(isBound){
            unbindService(serviceConnection);
            isBound = false;
        }
        super.onDestroy();
    }

}