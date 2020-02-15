package cse110.ucsd.team12wwr;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

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
    long numSteps;

    /* GoogleFit */
    private static final String TAG = "MainActivity";
    private FitnessService fitnessService;
    private final String fitnessServiceKey = "GOOGLE_FIT";


    /*SERVICE*/
    private PedometerService pedoService;
    private boolean isBound;
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service){
            PedometerService.LocalService localService = (PedometerService.LocalService)service;
            pedoService = localService.getService();
            isBound = true;
            pedoService.gimmethemsteppies(fitnessService);
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

        Button btnDebugIncSteps = findViewById(R.id.btn_debug_increment_steps);
        setSupportActionBar(toolbar);
        closeOptionsMenu();

        // Collect the height from the previous page
        spf = getSharedPreferences("height", MODE_PRIVATE);
        int feet = spf.getInt("feet", 0);
        int inches = spf.getInt("inches", 0);

        //System.out.println("feet: " + feet + " inches: "  + inches);

        totalHeight = inches + ( HEIGHT_FACTOR * feet );
        strideLength = totalHeight * STRIDE_CONVERSION;

        numSteps = 0;


        /* PEDOMETER START */
        fitnessService.setup();


    }/*
    @Override
    protected void onStart(){
        super.onStart();
        Log.i("onStart", "ON CREATE HAS COMPLETED AT THIS POINT");
        Intent intent = new Intent(this, PedometerService.class);
        Log.i("Main Activity", "COMMENCE THE BINDING");
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);



    }*/
    public void bindTheThing(){
        Intent intent = new Intent(this, PedometerService.class);
        Log.i("Main Activity", "COMMENCE THE BINDING");
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    public void launchActivity() {
        Intent intent = new Intent(this, IntentionalWalkActivity.class);
        startActivity(intent);
    }
    @Override
    protected void onPause() {
        super.onPause();

        //pedoService.onUnbind();
        //getApplicationContext().unbindService(serviceConnection);
        //pedoService.onDestroy();


    }

    @Override
    protected void onResume() {
        super.onResume();

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
        if(isBound){
            unbindService(serviceConnection);
            isBound = false;
        }
        super.onDestroy();
    }

}