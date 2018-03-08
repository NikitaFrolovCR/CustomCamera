package net.sourceforge.opencamera;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.StatFs;
import android.preference.PreferenceManager;
import android.renderscript.RenderScript;
import android.speech.tts.TextToSpeech;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import net.sourceforge.opencamera.CameraController.CameraControllerManager2;
import net.sourceforge.opencamera.Preview.Preview;
import net.sourceforge.opencamera.UI.MainUI;

import java.io.File;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Map;

/**
 * The main Activity for Open Camera.
 */
public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    // application shortcuts:
    private final Map<Integer, Bitmap> preloaded_bitmap_resources = new Hashtable<>();
    final private int MY_PERMISSIONS_REQUEST_CAMERA = 0;
    final private int MY_PERMISSIONS_REQUEST_STORAGE = 1;
    final private int MY_PERMISSIONS_REQUEST_RECORD_AUDIO = 2;

    private MainUI mainUI;

    //private boolean ui_placement_right = true;
    private MyApplicationInterface applicationInterface;
    private Preview preview;
    private boolean supports_camera2;

    private TextToSpeech textToSpeech;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        long debug_time = 0;
        if (MyDebug.LOG) {
            Log.d(TAG, "onCreate: " + this);
            debug_time = System.currentTimeMillis();
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false); // initialise any unset preferences to their default values


        // set up components
        mainUI = new MainUI(this);
        applicationInterface = new MyApplicationInterface(this, savedInstanceState);

        // determine whether we support Camera2 API
        initCamera2Support();

        // set up the camera and its preview
        preview = new Preview(applicationInterface, ((ViewGroup) this.findViewById(R.id.preview)));
        if (MyDebug.LOG)
            Log.d(TAG, "onCreate: time after creating preview: " + (System.currentTimeMillis() - debug_time));

        // initialise on-screen button visibility
        View switchCameraButton = findViewById(R.id.switch_camera);
        switchCameraButton.setVisibility(preview.getCameraControllerManager().getNumberOfCameras() > 1 ? View.VISIBLE : View.GONE);
        View pauseVideoButton = findViewById(R.id.pause_video);
        pauseVideoButton.setVisibility(View.GONE);

        // We initialise optional controls to invisible/gone, so they don't show while the camera is opening - the actual visibility is
        // set in cameraSetup().
        // Note that ideally we'd set this in the xml, but doing so for R.id.zoom causes a crash on Galaxy Nexus startup beneath
        // setContentView()!
        // To be safe, we also do so for take_photo and zoom_seekbar (we already know we've had no reported crashes for focus_seekbar,
        // however).


        setDeviceDefaults();
    }

    /* This method sets the preference defaults which are set specific for a particular device.
     * This method should be called when Open Camera is run for the very first time after installation,
	 * or when the user has requested to "Reset settings".
	 */
    void setDeviceDefaults() {
        if (MyDebug.LOG)
            Log.d(TAG, "setDeviceDefaults");
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean is_samsung = Build.MANUFACTURER.toLowerCase(Locale.US).contains("samsung");
        boolean is_oneplus = Build.MANUFACTURER.toLowerCase(Locale.US).contains("oneplus");
        //boolean is_nexus = Build.MODEL.toLowerCase(Locale.US).contains("nexus");
        //boolean is_nexus6 = Build.MODEL.toLowerCase(Locale.US).contains("nexus 6");
        //boolean is_pixel_phone = Build.DEVICE != null && Build.DEVICE.equals("sailfish");
        //boolean is_pixel_xl_phone = Build.DEVICE != null && Build.DEVICE.equals("marlin");
        if (MyDebug.LOG) {
            Log.d(TAG, "is_samsung? " + is_samsung);
            Log.d(TAG, "is_oneplus? " + is_oneplus);
            //Log.d(TAG, "is_nexus? " + is_nexus);
            //Log.d(TAG, "is_nexus6? " + is_nexus6);
            //Log.d(TAG, "is_pixel_phone? " + is_pixel_phone);
            //Log.d(TAG, "is_pixel_xl_phone? " + is_pixel_xl_phone);
        }
        if (is_samsung || is_oneplus) {
            // workaround needed for Samsung S7 at least (tested on Samsung RTL)
            // workaround needed for OnePlus 3 at least (see http://forum.xda-developers.com/oneplus-3/help/camera2-support-t3453103 )
            // update for v1.37: significant improvements have been made for standard flash and Camera2 API. But OnePlus 3T still has problem
            // that photos come out with a blue tinge if flash is on, and the scene is bright enough not to need it; Samsung devices also seem
            // to work okay, testing on S7 on RTL, but still keeping the fake flash mode in place for these devices, until we're sure of good
            // behaviour
            if (MyDebug.LOG)
                Log.d(TAG, "set fake flash for camera2");
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(PreferenceKeys.Camera2FakeFlashPreferenceKey, true);
            editor.apply();
        }
        /*if( is_nexus6 ) {
            // Nexus 6 captureBurst() started having problems with Android 7 upgrade - images appeared in wrong order (and with wrong order of shutter speeds in exif info), as well as problems with the camera failing with serious errors
			// we set this even for Nexus 6 devices not on Android 7, as at some point they'll likely be upgraded to Android 7
			// Update: now fixed in v1.37, this was due to bug where we set RequestTag.CAPTURE for all captures in takePictureBurstExpoBracketing(), rather than just the last!
			if( MyDebug.LOG )
				Log.d(TAG, "disable fast burst for camera2");
			SharedPreferences.Editor editor = sharedPreferences.edit();
			editor.putBoolean(PreferenceKeys.getCamera2FastBurstPreferenceKey(), false);
			editor.apply();
		}*/
    }

    /**
     * Determine whether we support Camera2 API.
     */
    private void initCamera2Support() {
        if (MyDebug.LOG)
            Log.d(TAG, "initCamera2Support");
        supports_camera2 = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CameraControllerManager2 manager2 = new CameraControllerManager2(this);
            supports_camera2 = true;
            if (manager2.getNumberOfCameras() == 0) {
                if (MyDebug.LOG)
                    Log.d(TAG, "Camera2 reports 0 cameras");
                supports_camera2 = false;
            }
            for (int i = 0; i < manager2.getNumberOfCameras() && supports_camera2; i++) {
                if (!manager2.allowCamera2Support(i)) {
                    if (MyDebug.LOG)
                        Log.d(TAG, "camera " + i + " doesn't have limited or full support for Camera2 API");
                    supports_camera2 = false;
                }
            }
        }
        if (MyDebug.LOG)
            Log.d(TAG, "supports_camera2? " + supports_camera2);
    }

    @Override
    protected void onDestroy() {
        if (MyDebug.LOG) {
            Log.d(TAG, "onDestroy");
            Log.d(TAG, "size of preloaded_bitmap_resources: " + preloaded_bitmap_resources.size());
        }
        preview.onDestroy();
        if (applicationInterface != null) {
            applicationInterface.onDestroy();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // see note in HDRProcessor.onDestroy() - but from Android M, renderscript contexts are released with releaseAllContexts()
            // doc for releaseAllContexts() says "If no contexts have been created this function does nothing"
            RenderScript.releaseAllContexts();
        }
        // Need to recycle to avoid out of memory when running tests - probably good practice to do anyway
        for (Map.Entry<Integer, Bitmap> entry : preloaded_bitmap_resources.entrySet()) {
            if (MyDebug.LOG)
                Log.d(TAG, "recycle: " + entry.getKey());
            entry.getValue().recycle();
        }
        preloaded_bitmap_resources.clear();
        if (textToSpeech != null) {
            // http://stackoverflow.com/questions/4242401/tts-error-leaked-serviceconnection-android-speech-tts-texttospeech-solved
            Log.d(TAG, "free textToSpeech");
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech = null;
        }
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        long debug_time = 0;
        if (MyDebug.LOG) {
            Log.d(TAG, "onResume");
            debug_time = System.currentTimeMillis();
        }
        super.onResume();

        // Set black window background; also needed if we hide the virtual buttons in immersive mode
        // Note that we do it here rather than customising the theme's android:windowBackground, so this doesn't affect other views - in particular, the MyPreferenceFragment settings
        getWindow().getDecorView().getRootView().setBackgroundColor(Color.BLACK);

        preview.onResume();

        if (MyDebug.LOG) {
            Log.d(TAG, "onResume: total time to resume: " + (System.currentTimeMillis() - debug_time));
        }
    }

    @Override
    protected void onPause() {
        long debug_time = 0;
        if (MyDebug.LOG) {
            Log.d(TAG, "onPause");
            debug_time = System.currentTimeMillis();
        }
        super.onPause(); // docs say to call this before freeing other things

        preview.onPause();
        if (MyDebug.LOG) {
            Log.d(TAG, "onPause: total time to pause: " + (System.currentTimeMillis() - debug_time));
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        if (MyDebug.LOG)
            Log.d(TAG, "onConfigurationChanged()");
        // configuration change can include screen orientation (landscape/portrait) when not locked (when settings is open)
        // needed if app is paused/resumed when settings is open and device is in portrait mode
        preview.setCameraDisplayOrientation();
        super.onConfigurationChanged(newConfig);
    }

    public void clickedTakePhoto(View view) {
        if (MyDebug.LOG)
            Log.d(TAG, "clickedTakePhoto");
        this.takePicture(true);
    }

    public void clickedPauseVideo(View view) {
        if (MyDebug.LOG)
            Log.d(TAG, "clickedPauseVideo");
        if (preview.isVideoRecording()) { // just in case
            preview.pauseVideo();
            mainUI.setPauseVideoContentDescription();
        }
    }

    /* Returns the cameraId that the "Switch camera" button will switch to.
     */
    public int getNextCameraId() {
        if (MyDebug.LOG)
            Log.d(TAG, "getNextCameraId");
        int cameraId = preview.getCameraId();
        if (MyDebug.LOG)
            Log.d(TAG, "current cameraId: " + cameraId);
        if (this.preview.canSwitchCamera()) {
            int n_cameras = preview.getCameraControllerManager().getNumberOfCameras();
            cameraId = (cameraId + 1) % n_cameras;
        }
        if (MyDebug.LOG)
            Log.d(TAG, "next cameraId: " + cameraId);
        return cameraId;
    }

    public void clickedSwitchCamera(View view) {
        if (MyDebug.LOG)
            Log.d(TAG, "clickedSwitchCamera");
        if (preview.isOpeningCamera()) {
            if (MyDebug.LOG)
                Log.d(TAG, "already opening camera in background thread");
            return;
        }
        if (this.preview.canSwitchCamera()) {
            int cameraId = getNextCameraId();
            View switchCameraButton = findViewById(R.id.switch_camera);
            switchCameraButton.setEnabled(false); // prevent slowdown if user repeatedly clicks
            this.preview.setCamera(cameraId);
            switchCameraButton.setEnabled(true);
            // no need to call mainUI.setSwitchCameraContentDescription - this will be called from PreviewcameraSetup when the
            // new camera is opened
        }
    }

    /**
     * Listens for the response from the Storage Access Framework dialog to select a folder
     * (as opened with openFolderChooserDialogSAF()).
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (MyDebug.LOG)
            Log.d(TAG, "onActivityResult: " + requestCode);
        if (requestCode == 42) {
            if (resultCode == RESULT_OK && resultData != null) {
                Uri treeUri = resultData.getData();
                if (MyDebug.LOG)
                    Log.d(TAG, "returned treeUri: " + treeUri);
                // from https://developer.android.com/guide/topics/providers/document-provider.html#permissions :
                final int takeFlags = resultData.getFlags()
                        & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                getContentResolver().takePersistableUriPermission(treeUri, takeFlags);
            }
        }
    }

    /**
     * User has pressed the take picture button, or done an equivalent action to request this (e.g.,
     * volume buttons, audio trigger).
     *
     * @param photo_snapshot If true, then the user has requested taking a photo whilst video
     *                       recording. If false, either take a photo or start/stop video depending
     *                       on the current mode.
     */
    public void takePicture(boolean photo_snapshot) {
        if (MyDebug.LOG)
            Log.d(TAG, "takePicture");

        this.preview.takePicturePressed(photo_snapshot);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        if (MyDebug.LOG)
            Log.d(TAG, "onSaveInstanceState");
        super.onSaveInstanceState(state);
        if (this.applicationInterface != null) {
            applicationInterface.onSaveInstanceState(state);
        }
    }

    void cameraSetup() {
        mainUI.setVideoIcon();
        mainUI.setSwitchCameraContentDescription();
    }

    public boolean supportsCamera2() {
        return this.supports_camera2;
    }

    /**
     * Return free memory in MB.
     */
    @SuppressWarnings("deprecation")
    public long freeMemory() { // return free memory in MB
        if (MyDebug.LOG)
            Log.d(TAG, "freeMemory");
        try {
            File folder = applicationInterface.getStorageUtils().getImageFolder();
            if (folder == null) {
                throw new IllegalArgumentException(); // so that we fall onto the backup
            }
            StatFs statFs = new StatFs(folder.getAbsolutePath());
            // cast to long to avoid overflow!
            long blocks = statFs.getAvailableBlocks();
            long size = statFs.getBlockSize();
            return (blocks * size) / 1048576;
        } catch (IllegalArgumentException e) {
            // this can happen if folder doesn't exist, or don't have read access
            // if the save folder is a subfolder of DCIM, we can just use that instead
            try {
                if (!applicationInterface.getStorageUtils().isUsingSAF()) {
                    // StorageUtils.getSaveLocation() only valid if !isUsingSAF()
                    String folder_name = applicationInterface.getStorageUtils().getSaveLocation();
                    if (!folder_name.startsWith("/")) {
                        File folder = StorageUtils.getBaseFolder();
                        StatFs statFs = new StatFs(folder.getAbsolutePath());
                        // cast to long to avoid overflow!
                        long blocks = statFs.getAvailableBlocks();
                        long size = statFs.getBlockSize();
                        return (blocks * size) / 1048576;
                    }
                }
            } catch (IllegalArgumentException e2) {
                // just in case
            }
        }
        return -1;
    }

    public Preview getPreview() {
        return this.preview;
    }

    public MainUI getMainUI() {
        return this.mainUI;
    }

    /**
     * Show a "rationale" to the user for needing a particular permission, then request that permission again
     * once they close the dialog.
     */
    private void showRequestPermissionRationale(final int permission_code) {
        String[] permissions = null;
        if (permission_code == MY_PERMISSIONS_REQUEST_CAMERA) {
            permissions = new String[]{Manifest.permission.CAMERA};
        } else if (permission_code == MY_PERMISSIONS_REQUEST_STORAGE) {
            permissions = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE};
        } else if (permission_code == MY_PERMISSIONS_REQUEST_RECORD_AUDIO) {
            permissions = new String[]{Manifest.permission.RECORD_AUDIO};
        }
        ActivityCompat.requestPermissions(MainActivity.this, permissions, permission_code);
    }

    void requestCameraPermission() {
        if (MyDebug.LOG)
            Log.d(TAG, "requestCameraPermission");
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            if (MyDebug.LOG)
                Log.e(TAG, "shouldn't be requesting permissions for pre-Android M!");
            return;
        }

        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
            // Show an explanation to the user *asynchronously* -- don't block
            // this thread waiting for the user's response! After the user
            // sees the explanation, try again to request the permission.
            showRequestPermissionRationale(MY_PERMISSIONS_REQUEST_CAMERA);
        } else {
            // Can go ahead and request the permission
            if (MyDebug.LOG)
                Log.d(TAG, "requesting camera permission...");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, MY_PERMISSIONS_REQUEST_CAMERA);
        }
    }

    void requestStoragePermission() {
        if (MyDebug.LOG)
            Log.d(TAG, "requestStoragePermission");
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            if (MyDebug.LOG)
                Log.e(TAG, "shouldn't be requesting permissions for pre-Android M!");
            return;
        }

        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            // Show an explanation to the user *asynchronously* -- don't block
            // this thread waiting for the user's response! After the user
            // sees the explanation, try again to request the permission.
            showRequestPermissionRationale(MY_PERMISSIONS_REQUEST_STORAGE);
        } else {
            // Can go ahead and request the permission
            if (MyDebug.LOG)
                Log.d(TAG, "requesting storage permission...");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, MY_PERMISSIONS_REQUEST_STORAGE);
        }
    }

    void requestRecordAudioPermission() {
        if (MyDebug.LOG)
            Log.d(TAG, "requestRecordAudioPermission");
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            if (MyDebug.LOG)
                Log.e(TAG, "shouldn't be requesting permissions for pre-Android M!");
            return;
        }

        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)) {
            // Show an explanation to the user *asynchronously* -- don't block
            // this thread waiting for the user's response! After the user
            // sees the explanation, try again to request the permission.
            showRequestPermissionRationale(MY_PERMISSIONS_REQUEST_RECORD_AUDIO);
        } else {
            // Can go ahead and request the permission
            if (MyDebug.LOG)
                Log.d(TAG, "requesting record audio permission...");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, MY_PERMISSIONS_REQUEST_RECORD_AUDIO);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        if (MyDebug.LOG)
            Log.d(TAG, "onRequestPermissionsResult: requestCode " + requestCode);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            if (MyDebug.LOG)
                Log.e(TAG, "shouldn't be requesting permissions for pre-Android M!");
            return;
        }

        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_CAMERA: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    if (MyDebug.LOG)
                        Log.d(TAG, "camera permission granted");
                    preview.retryOpenCamera();
                } else {
                    if (MyDebug.LOG)
                        Log.d(TAG, "camera permission denied");
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    // Open Camera doesn't need to do anything: the camera will remain closed
                }
                return;
            }
            case MY_PERMISSIONS_REQUEST_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    if (MyDebug.LOG)
                        Log.d(TAG, "storage permission granted");
                    preview.retryOpenCamera();
                } else {
                    if (MyDebug.LOG)
                        Log.d(TAG, "storage permission denied");
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    // Open Camera doesn't need to do anything: the camera will remain closed
                }
                return;
            }
            case MY_PERMISSIONS_REQUEST_RECORD_AUDIO: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    if (MyDebug.LOG)
                        Log.d(TAG, "record audio permission granted");
                    // no need to do anything
                } else {
                    if (MyDebug.LOG)
                        Log.d(TAG, "record audio permission denied");
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    // no need to do anything
                    // note that we don't turn off record audio option, as user may then record video not realising audio won't be recorded - best to be explicit each time
                }
                return;
            }
            default: {
                if (MyDebug.LOG)
                    Log.e(TAG, "unknown requestCode " + requestCode);
            }
        }
    }

}
