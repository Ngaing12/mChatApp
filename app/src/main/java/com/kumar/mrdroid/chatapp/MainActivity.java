package com.kumar.mrdroid.chatapp;

import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class MainActivity extends AppCompatActivity {

    private FirebaseDatabase mFirebaseDatabase;
    private DatabaseReference mMessageDatabaseReference;
    private ChildEventListener mChildEventListener;
    private FirebaseAuth mFirebaseAuth;
    private FirebaseAuth.AuthStateListener mAuthStateListener;
    List<AuthUI.IdpConfig> providers;
    private FirebaseStorage mFirebaseStorage;
    private StorageReference mPhotoStorageReference;
    private FirebaseRemoteConfig mFirebaseRemoteConfig;

    private static final String TAG = "MainActivity";
    private static final String ANYNOMOUS = "anynomous";
    private static final int DEFAULT_MSG_LENGTH_LIMIT = 1000;
    private static final String MCHAT_MSG_LENGTH_KEY = "mchat_msg_length";
    private static final int RC_SIGN_IN= 1;
    private static final int RC_PHOTO_PICKER = 2;

    private ListView mMessageList;
    private MessageAdapter mAdapter;
    private ProgressBar mProgressbar;
    private ImageButton mImagePickerButton;
    private EditText mMessage;
    private Button mSendButton;

    private String mUserName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mUserName = ANYNOMOUS;

        /***
         * Firebase Realtime Database
         */

        mFirebaseDatabase = FirebaseDatabase.getInstance();
        providers = new ArrayList<>();
        mFirebaseAuth = FirebaseAuth.getInstance();
        mFirebaseStorage = FirebaseStorage.getInstance();
        mFirebaseRemoteConfig = FirebaseRemoteConfig.getInstance();

        mMessageDatabaseReference = mFirebaseDatabase.getReference().child("messages");
        mPhotoStorageReference = mFirebaseStorage.getReference().child("chat_images");

        mProgressbar = findViewById(R.id.progressBar);
        mMessageList = findViewById(R.id.messageListView);
        mImagePickerButton = findViewById(R.id.imagePickerbutton);
        mMessage = findViewById(R.id.et_message);
        mSendButton = findViewById(R.id.button_send);

        //set adapter to listview
        List<Message>  mMessageArrayList = new ArrayList<>();
        mAdapter = new MessageAdapter(this, R.layout.item_message, mMessageArrayList);
        mMessageList.setAdapter(mAdapter);

        mProgressbar.setVisibility(View.INVISIBLE);

        /***
         *  Define click event of mImagePickerButton
         */
        mImagePickerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                // define click event of mImagePickerButton
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                startActivityForResult(Intent.createChooser(intent, "Complete action using"), RC_PHOTO_PICKER);

            }
        });

        /***
         * Enable send button when there is text available to send
         */
        mMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if(charSequence.toString().trim().length() > 0){
                    mSendButton.setEnabled(true);
                }else{
                    mSendButton.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });

        /***
         * set filter for character limit
         */
        mMessage.setFilters(new InputFilter[]{ new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)});

        /***
         * Send Button Sends a message and clear a edit text
         */
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                // add functionality of send button to real time database
                Message message = new Message(mMessage.getText().toString().trim(), mUserName, null);
                mMessageDatabaseReference.push().setValue(message);

                // clear edittext on send button click
                mMessage.setText("");
            }
        });


        mAuthStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                
                if(user != null){
                    //Already Sign In
                    onSignedInIntialized(user.getDisplayName());
                }else{
                    //user is Sign out
                    onSignedOutCleanup();
                    providers.add(new AuthUI.IdpConfig.Builder(AuthUI.EMAIL_PROVIDER).build());
                    providers.add(new AuthUI.IdpConfig.Builder(AuthUI.GOOGLE_PROVIDER).build());
                    startActivityForResult(
                            AuthUI.getInstance()
                                    .createSignInIntentBuilder()
                                    .setIsSmartLockEnabled(false)
                                    .setAvailableProviders(providers)
                                    .build(),
                            RC_SIGN_IN);
                }
            }
        };

        // Firebase Remote Config
        FirebaseRemoteConfigSettings configSettings= new FirebaseRemoteConfigSettings.Builder()
                .setDeveloperModeEnabled(BuildConfig.DEBUG)
                .build();
        mFirebaseRemoteConfig.setConfigSettings(configSettings);

        Map<String, Object> defaultConfigMap = new HashMap<>();
        defaultConfigMap.put(MCHAT_MSG_LENGTH_KEY, DEFAULT_MSG_LENGTH_LIMIT);
        mFirebaseRemoteConfig.setDefaults(defaultConfigMap);

        //method for fetch config
        fetchConfig();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case R.id.action_sign_out:
                AuthUI.getInstance().signOut(this);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == RC_SIGN_IN){
            // if signed in
            if(resultCode == RESULT_OK){
                Toast.makeText(this, "Singed In .", Toast.LENGTH_SHORT).show();
            }
            //if signed in Cancelled
            else if(resultCode == RESULT_CANCELED){
                Toast.makeText(this, "Singed In Cancelled !", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
        else if(requestCode == RC_PHOTO_PICKER && resultCode == RESULT_OK){
            Uri selectedImageUri = data.getData();
            StorageReference photoRef = mPhotoStorageReference.child(selectedImageUri.getLastPathSegment());

            // upload file to firebase storage
            photoRef.putFile(selectedImageUri).addOnSuccessListener(
                    this, new OnSuccessListener<UploadTask.TaskSnapshot>() {
                        @Override
                        public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {

                            Uri downloadUrl = taskSnapshot.getDownloadUrl();
                            Message message = new Message(null, mUserName, downloadUrl.toString());
                            mMessageDatabaseReference.push().setValue(message);
                        }
                    });
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        /***
         * Attach AuthStatelistener to FirebaseAuth
         */
        if(mAuthStateListener != null){
            mFirebaseAuth.removeAuthStateListener(mAuthStateListener);
        }
        detachDatabaseReadListener();
        mAdapter.clear();
    }

    @Override
    protected void onResume() {
        super.onResume();
        /***
         * Attach AuthStatelistener to FirebaseAuth
         */
        mFirebaseAuth.addAuthStateListener(mAuthStateListener);
    }

    private void onSignedInIntialized(String username){
        mUserName = username;
        attachDatabaseReadListener();

    }
    private void onSignedOutCleanup(){
        mUserName = ANYNOMOUS;
        mAdapter.clear();
        detachDatabaseReadListener();

    }

    private void attachDatabaseReadListener(){
        if(mChildEventListener == null){
            /***
             * Read from Realtime Database
             */
            mChildEventListener = new ChildEventListener() {
                @Override
                public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                    // get message from Realtime Database with position
                    Message message = dataSnapshot.getValue(Message.class);
                    // add message to adapter
                    mAdapter.add(message);
                }

                @Override
                public void onChildChanged(DataSnapshot dataSnapshot, String s) {}

                @Override
                public void onChildRemoved(DataSnapshot dataSnapshot) {}

                @Override
                public void onChildMoved(DataSnapshot dataSnapshot, String s) {}

                @Override
                public void onCancelled(DatabaseError databaseError) {}
            };
            // listener to mMessagedatabseLisener
            mMessageDatabaseReference.addChildEventListener(mChildEventListener);
        }
    }

    private void detachDatabaseReadListener(){
        if(mChildEventListener != null){
            mMessageDatabaseReference.removeEventListener(mChildEventListener);
            mChildEventListener = null;
        }
    }

    private void fetchConfig(){
        // set cache expiration time
        long cacheExp = 3600;
        if(mFirebaseRemoteConfig.getInfo().getConfigSettings().isDeveloperModeEnabled()){
            cacheExp = 0;
        }
        mFirebaseRemoteConfig.fetch(cacheExp)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        mFirebaseRemoteConfig.activateFetched();
                        applyRetrieveLengthLimit();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(TAG, "Error fetching Config ", e);
                        applyRetrieveLengthLimit();

                    }
                });
    }

    private void applyRetrieveLengthLimit(){
        Long mchat_msg_length = mFirebaseRemoteConfig.getLong(MCHAT_MSG_LENGTH_KEY);
        mMessage.setFilters(new InputFilter[]{new InputFilter.LengthFilter(mchat_msg_length.intValue())});
        Log.d(TAG, MCHAT_MSG_LENGTH_KEY + " = " + mchat_msg_length);
    }
}
