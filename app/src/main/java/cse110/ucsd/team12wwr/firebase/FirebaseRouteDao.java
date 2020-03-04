package cse110.ucsd.team12wwr.firebase;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import cse110.ucsd.team12wwr.MainActivity;

public class FirebaseRouteDao {
    FirebaseFirestore db;
    public FirebaseRouteDao() {
        System.err.println("The testing flag is " + MainActivity.unitTestFlag);
        if (!MainActivity.unitTestFlag) {
            System.err.println("Initializing actual Firestore database");
            db = FirebaseFirestore.getInstance();
        }
    }

    public void insertAll(Route... r) {
        if (db == null) {
            return;
        }

        for (Route route : r) {
            db.collection("routes").document(route.name).set(route);
        }
    }

    public void delete(String routeName) {
        if (db == null) {
            return;
        }

        db.collection("routes").document(routeName).delete();
    }

//    @Query("SELECT * FROM Route r ORDER BY name ASC")
    public Task<QuerySnapshot> retrieveAllRoutes() {
        if (db == null) {
            return new MockTask<>();
        }

        return db.collection("routes").orderBy("name", Query.Direction.ASCENDING).get();
    }

//    @Query("SELECT * FROM Route r WHERE r.name=:routeName")
    public Task<QuerySnapshot> findName(String routeName) {
        if (db == null) {
            return new MockTask<>();
        }

        return db.collection("routes").whereEqualTo("name", routeName).get();
    }
}