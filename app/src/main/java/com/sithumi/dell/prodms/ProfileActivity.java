package com.sithumi.dell.prodms;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.face.FirebaseVisionFace;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetector;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class ProfileActivity extends AppCompatActivity implements LocationListener{

    private static final int TAKE_PHOTO = 104;
    private static final int REQUEST_LOCATION=1;
    private static final int REQUEST_WRITE_EXTERNAL_STORAGE=2;
    private static final int REQUEST_READ_EXTERNAL_STORAGE=3;
    private Button btn_takephoto;
    private Button btn_logout;

    private static final String TAG="Info:ProfileActivity";

    private String imageName;
    private Button btn_upload;
    private StorageReference mStorageRef;
    private Uri tempUri;
    private File finalFile;
    private String downloadURL;
    private String userid;
    private Date date;
    private String email;

//    private String databasePath;
    private FirebaseDatabase database;
    private DatabaseReference databaseReference;
//    private String imageuploadId;
    private FirebaseAuth auth;

    private ImageView imageView;
    private Bitmap img;

    protected LocationManager locationManager;
    TextView txtLat;
    TextView txtLon;
    protected Double latitude, longitude;
    protected String latVal, longVal;

    private static final AtomicLong LAST_TIME_MS= new AtomicLong();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        date=new Date();
//        btn_upload.setEnabled(true);
        btn_takephoto = findViewById(R.id.btn_takephoto);
        btn_logout = findViewById(R.id.btn_logout);
        imageView = findViewById(R.id.img_mosquito);
        btn_upload=findViewById(R.id.btn_upload);
        mStorageRef= FirebaseStorage.getInstance().getReference();
//        databaseReference= FirebaseDatabase.getInstance().getReference(databasePath);
        auth=FirebaseAuth.getInstance();
        database=FirebaseDatabase.getInstance();

        //how to set values to real time database

        checkFilePermissions();


        btn_upload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkFilePermissions();
                FirebaseUser user =auth.getCurrentUser();
                userid=user.getUid();
                email=user.getEmail();


                String name=imageName;
                Log.i("info","String name is"+ name);
                if(!name.equals("")) {
                    Uri urix=Uri.fromFile(new File(finalFile.getAbsolutePath()));
                    final StorageReference storageReference = mStorageRef.child("images/" + userid + "/" + name);
                    Log.i("storageref", storageReference.getPath());
                   UploadTask uploadTask =storageReference.putFile(urix);

                   Task<Uri> uriTask=uploadTask.continueWithTask(new Continuation<UploadTask.TaskSnapshot, Task<Uri>>() {
                       @Override
                       public Task<Uri> then(@NonNull Task<UploadTask.TaskSnapshot> task) throws Exception {

                           if (!task.isSuccessful()) {
                               throw task.getException();
                           }

                           // Continue with the task to get the download URL
                           return storageReference.getDownloadUrl();
                       }

                   }).addOnCompleteListener(new OnCompleteListener<Uri>() {
                       @Override
                       public void onComplete(@NonNull Task<Uri> task) {
                           if (task.isSuccessful()) {
                               Uri downloadUri = task.getResult();
                               downloadURL = downloadUri.toString();
                               long uniqueid=uniqueCurrentTimeMS();
                               databaseReference=database.getReference("users/"+userid+"/"+uniqueid+"/date");
                               databaseReference.setValue(date.toString());
                               databaseReference=database.getReference("users/"+userid+"/"+uniqueid+"/image");
                               databaseReference.setValue(downloadURL);
                               databaseReference=database.getReference("users/"+userid+"/"+uniqueid+"/email");
                               databaseReference.setValue(email);
                               databaseReference=database.getReference("users/"+userid+"/"+uniqueid+"/latitude");
                               databaseReference.setValue(latitude);
                               databaseReference=database.getReference("users/"+userid+"/"+uniqueid+"/longitude");
                               databaseReference.setValue(longitude);
                               Toast.makeText(getApplicationContext(),"upload successful",Toast.LENGTH_LONG).show();
                           } else{
                               Log.i("info", "Task failed");
                           }
                       }
                   }).addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Toast.makeText(getApplicationContext(),"upload failed",Toast.LENGTH_LONG).show();
                        }
                    })
                    ;

                }
            }
        });

        btn_takephoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                startActivityForResult(i,TAKE_PHOTO);

            }
        });

        btn_logout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                auth.signOut();

                Intent login = new Intent(getApplicationContext(), MainActivity.class);
                startActivity(login);
            }
        });
    }

    private void checkFilePermissions(){
        if(Build.VERSION.SDK_INT>Build.VERSION_CODES.LOLLIPOP){

            if(ActivityCompat.checkSelfPermission(this,android.Manifest.permission.WRITE_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED){

                Log.i(TAG,"checkBTPermissions: No need to check permissions, SDK version < Lollipop");
            }else{
                ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},REQUEST_WRITE_EXTERNAL_STORAGE);
//                Manifest.permission.READ_EXTERNAL_STORAGE
                return;
            }
        }
    }

    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions,
                                           int[] grantResults) {

        switch (requestCode) {
            case 1: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(getApplicationContext(), "Location access provided", Toast.LENGTH_LONG).show();
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Toast.makeText(getApplicationContext(), "Location access not provided", Toast.LENGTH_LONG).show();
                }
                return;
            }

            case 2: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(getApplicationContext(), "Storage access provided", Toast.LENGTH_LONG).show();
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Toast.makeText(getApplicationContext(), "Storage access not provided", Toast.LENGTH_LONG).show();
                }
                return;
            }

        }
    }
    public void getLocation(){
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
              //permission already granted
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this);
        } else{
            //check permission now
//            ActivityCompat.requestPermissions( this, new String[] {Manifest.permission.ACCESS_COARSE_LOCATION  },REQUEST_LOCATION );
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION},REQUEST_LOCATION);
            return;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable final Intent data) {
        if(requestCode == TAKE_PHOTO){
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if(data!=null){
                         img = (Bitmap) data.getExtras().get("data");

                        FirebaseVisionFaceDetectorOptions options = new FirebaseVisionFaceDetectorOptions.Builder()
                                .setLandmarkMode(FirebaseVisionFaceDetectorOptions.ALL_LANDMARKS)
                                .setContourMode(FirebaseVisionFaceDetectorOptions.ALL_CONTOURS).build();
                        FirebaseVisionImage image = FirebaseVisionImage.fromBitmap(img);

                        FirebaseVisionFaceDetector detector = FirebaseVision.getInstance().getVisionFaceDetector(options);

                        Task<List<FirebaseVisionFace>> result =
                                detector.detectInImage(image)
                                        .addOnSuccessListener(
                                                new OnSuccessListener<List<FirebaseVisionFace>>() {
                                                    @Override
                                                    public void onSuccess(List<FirebaseVisionFace> faces) {
//                                                    Toast.makeText(getApplicationContext(),"This Image Contain "+ faces.size()+" Faces", Toast.LENGTH_LONG).show();
                                                        if(faces.size()==0){
                                                            Toast.makeText(getApplicationContext(),"Fetching location",Toast.LENGTH_LONG).show();
                                                            getLocation();
                                                        }
                                                        else {
                                                            Toast.makeText(getApplicationContext(),"This is not a photo of dengue breeding places",Toast.LENGTH_LONG).show();
                                                        }

                                                    }
                                                })
                                        .addOnFailureListener(
                                                new OnFailureListener() {
                                                    @Override
                                                    public void onFailure(@NonNull Exception e) {
                                                        Toast.makeText(getApplicationContext(),"Error detecting faces", Toast.LENGTH_LONG).show();

                                                    }
                                                });

                        imageView.setImageBitmap(img);

                        tempUri = getImageUri(getApplicationContext(), img);
                        finalFile = new File(getRealPathFromURI(tempUri));
                        imageName=finalFile.getName();
                        Log.i("info-imageName",imageName);
                        Log.i("File path",finalFile.getAbsolutePath());


                    }else{
                        imageView.setImageResource(R.drawable.mosquito);
                    }



                }
            });

        }
    }

    @Override
    public void onLocationChanged(final Location location) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                txtLat = findViewById(R.id.latitude);
                txtLon=findViewById(R.id.longitude);
                //getting values
                latitude=location.getLatitude();
                longitude=location.getLongitude();
                latVal="Latitude: "+latitude;
                longVal="Longitude: "+longitude;
                txtLat.setText(latVal);
                txtLon.setText(longVal);

//                locationManager.removeUpdates(GPS_PROVIDER);
//                locationManager=null;
            }
        });

    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        Log.d("Latitude","status");
    }

    @Override
    public void onProviderEnabled(String provider) {
        Log.d("Latitude","enable");
    }

    @Override
    public void onProviderDisabled(String provider) {
        Log.d("Latitude","disable");
    }

    public Uri getImageUri(Context inContext, Bitmap inImage) {

//        checkFilePermissions();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        inImage.compress(Bitmap.CompressFormat.JPEG, 100, bytes);
        String path = MediaStore.Images.Media.insertImage(inContext.getContentResolver(), inImage, "Title", null);
        return Uri.parse(path);
    }

    public String getRealPathFromURI(Uri uri) {
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        cursor.moveToFirst();
        int idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
        return cursor.getString(idx);
    }

//    public Uri getDownloadUrl() {
//        return downloadUrl;
//    }

//    public void setDownloadUrl(Uri downloadUrl) {
//        this.downloadUrl = downloadUrl;
//    }

    public static long uniqueCurrentTimeMS() {
        long now = System.currentTimeMillis();
        while(true) {
            long lastTime = LAST_TIME_MS.get();
            if (lastTime >= now)
                now = lastTime+1;
            if (LAST_TIME_MS.compareAndSet(lastTime, now))
                return now;
        }
    }
}