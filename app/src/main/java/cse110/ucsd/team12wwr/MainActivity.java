package cse110.ucsd.team12wwr;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;

import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

import com.google.firebase.firestore.QueryDocumentSnapshot;


import java.text.DecimalFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cse110.ucsd.team12wwr.fitness.GoogleFitUtility;


import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import cse110.ucsd.team12wwr.firebase.FirebaseWalkDao;
import cse110.ucsd.team12wwr.firebase.Walk;

import cse110.ucsd.team12wwr.roomdb.WWRDatabase;
import cse110.ucsd.team12wwr.roomdb.WalkDao;

public class MainActivity extends AppCompatActivity {

    /* constants */
    private static final String TAG = "MainActivity";

    final int HEIGHT_FACTOR = 12;
    final double STRIDE_CONVERSION = 0.413;
    final int MILE_FACTOR = 63360;
    final String FIRST_LAUNCH_KEY = "HAVE_HEIGHT";
    final String HEIGHT_SPF_NAME = "HEIGHT";
    final String FEET_KEY = "FEET";
    final String INCHES_KEY = "INCHES";

    /* height */
    SharedPreferences spf, spf2, prefs;

    /* steps */
    TextView textStep;
    long numSteps = 0;

    /* Testing */
    public static boolean unitTestFlag = false;


    /* distance */
    TextView textDist;
    int totalHeight;
    double strideLength;

    /* GoogleFit Refactor */
    int RC_SIGN_IN = 4;
    GoogleFitUtility gFitUtil;
    public boolean googleSubscribedStatus = false;
    public boolean gFitUtilLifecycleFlag;


    /* Team Related Variables */
    String userEmail;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.i("MainActivity.onCreate", "onCreate() called");



        /* START GOOGLE LOGIN */
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build();
        GoogleSignInClient mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();

        startActivityForResult(signInIntent, RC_SIGN_IN);

        Toolbar toolbar = findViewById(R.id.toolbar);

//        // Create and adapt the FitnessService
//        FitnessServiceFactory.put(fitnessServiceKey, new FitnessServiceFactory.BluePrint() {
//            @Override
//            public FitnessService create(MainActivity mainActivity) {
//                return new GoogleFitAdapter(mainActivity);
//            }
//        });
//        fitnessService = FitnessServiceFactory.create(fitnessServiceKey, this);

        textDist = findViewById(R.id.num_miles);
        textStep = findViewById(R.id.num_steps);

        setSupportActionBar(toolbar);
        closeOptionsMenu();

        /* PEDOMETER START */
        gFitUtil = new GoogleFitUtility(this);
        final Handler checkSubscription = new Handler();
        checkSubscription.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!gFitUtil.getSubscribed()) {
                    Log.i("checkSubscription", "Not yet subscribed, checking again in 5 seconds");
                    checkSubscription.postDelayed(this, 5000);
                }
                else{
                    Log.i("checkSubscription", "Ending handler.run");
                    googleSubscribedStatus = true;
                }
            }
        }, 5000);
    }


    @Override
    protected void onStart() {
        super.onStart();
        Log.i("MainActivity.onStart", "onStart() has been called");
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        userEmail = "account not retrieved";
        try{
            userEmail = account.getEmail();
        }
        catch(NullPointerException e){
            Log.i("ACCOUNT NOT SIGNED IN PRIOR", " No prior sign in");
        }
        Log.i("GMAIL: ", userEmail);

        prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        boolean previouslyStarted = prefs.getBoolean(FIRST_LAUNCH_KEY, false);

        if(!previouslyStarted) {
            SharedPreferences.Editor edit = prefs.edit();
            edit.putBoolean(FIRST_LAUNCH_KEY, Boolean.TRUE);
            edit.commit();
            launchHeightActivity();
        }

        // Collect the height from the height page
        spf = getSharedPreferences(HEIGHT_SPF_NAME, MODE_PRIVATE);
        int feet = spf.getInt(FEET_KEY, 0);
        int inches = spf.getInt(INCHES_KEY, 0);

        totalHeight = inches + ( HEIGHT_FACTOR * feet );
        strideLength = totalHeight * STRIDE_CONVERSION;

        BottomNavigationView navigation = findViewById(R.id.nav_view);
        Menu menu = navigation.getMenu();
        MenuItem menuItem = menu.getItem(0);
        menuItem.setChecked(true);

        navigation.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
                switch (menuItem.getItemId()) {
                    case R.id.navigation_home:
                        break;
                    case R.id.navigation_routes:
                        launchTeamRouteActivity();

                        break;
                    case R.id.navigation_walk:
                        launchActivity();

                        break;
                    case R.id.navigation_teams:
                        launchTeamScreenActivity();

                        break;
                }
                return false;
            }
        });



    }


    public void launchActivity() {
        Intent intent = new Intent(this, IntentionalWalkActivity.class);
        startActivity(intent);
    }

    public void launchRoutesScreenActivity() {
        Intent intent = new Intent(this, RoutesScreen.class);
        startActivity(intent);
    }

    public void launchTeamScreenActivity() {
        Intent intent = new Intent(this, TeamScreen.class);
        startActivity(intent);
    }

    public void launchTeamRouteActivity() {
        Intent intent = new Intent(this, TeamIndividRoutes.class);
        startActivity(intent);
    }


    @Override
    protected void onPause() {
        super.onPause();
        Log.i("MainActivity.onPause", "onPause() has been called");
        gFitUtilLifecycleFlag = false;

    }

    public void launchHeightActivity() {
        Intent intent = new Intent( this, StartPage.class );
        startActivity(intent);
    }


    @Override
    protected void onResume() {
        super.onResume();
        Log.i("MainActivity.onResume", "onResume() has been called");



        gFitUtilLifecycleFlag = true;

        final Handler stepsUpdaterHandler = new Handler();
        stepsUpdaterHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (gFitUtilLifecycleFlag) {
                    Log.i("stepsUpdaterHandler", "Updating step value");
                    gFitUtil.updateStepCount();
                    setStepCount(gFitUtil.getStepValue());
                    stepsUpdaterHandler.postDelayed(this, 4000);
                }
                else if (!gFitUtil.getSubscribed()){
                    Log.i("stepsUpdaterHandler", "NOT YET SUBSCRIBED");
                }
                else{
                    Log.i("stepsUpdaterHandler", "THREAD DISABLED");
                }
            }
        }, 4000);





        FirebaseWalkDao dao = new FirebaseWalkDao();
        dao.findNewestEntries().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Walk newestWalk = null;
                for (QueryDocumentSnapshot document : task.getResult()) {
                    if (newestWalk == null) {
                        newestWalk = document.toObject(Walk.class);
                    }
                }

                if (newestWalk != null) {
                    TextView stepsWalkText = findViewById(R.id.text_steps_value);
                    TextView distWalkText = findViewById(R.id.text_distance_value);
                    TextView timeWalkText = findViewById(R.id.text_time_value);


                    stepsWalkText.setText(newestWalk.steps);
                    distWalkText.setText(newestWalk.distance);
                    timeWalkText.setText(newestWalk.duration);
                }
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
        if (id == R.id.debug_walk) {
            Intent intent = new Intent(this, DebugWalkActivity.class);
            startActivity(intent);
        }
        else if (id == R.id.team_screen){
            Intent intent = new Intent(this, TeamScreenActivity.class);
            startActivity(intent);
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


    @Override
    protected void onDestroy() {
        Log.i("MainActivity.onDestroy", "onDestroy() has been called");
        super.onDestroy();
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Result returned from launching the Intent from GoogleSignInClient.getSignInIntent(...);
        if (requestCode == RC_SIGN_IN) {
            // The Task returned from this call is always completed, no need to attach
            // a listener.
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            handleSignInResult(task);
            gFitUtil.init();
        }
    }

    private void handleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            if(account.getEmail() != null) {
                Log.i("MainActivity.handleSignInResult() yields: ", account.getEmail());
                userEmail = account.getEmail();
            }
            else
                Log.i("MainActivity.handleSignInResult() yields: ", "NULL");
            // Signed in successfully, show authenticated UI.
            //
        } catch (ApiException e) {
            // The ApiException status code indicates the detailed failure reason.
            // Please refer to the GoogleSignInStatusCodes class reference for more information.
            Log.w(TAG, "signInResult:failed code=" + e.getStatusCode());
        }
    }

}
