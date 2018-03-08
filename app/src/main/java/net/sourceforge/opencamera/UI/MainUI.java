package net.sourceforge.opencamera.UI;

import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;

import net.sourceforge.opencamera.MainActivity;
import net.sourceforge.opencamera.MyDebug;
import net.sourceforge.opencamera.PreferenceKeys;
import net.sourceforge.opencamera.R;

import java.util.Hashtable;
import java.util.Map;

/**
 * This contains functionality related to the main UI.
 */
public class MainUI {
    private static final String TAG = "MainUI";
    private final MainActivity main_activity;
    // for testing:
    private final Map<String, View> test_ui_buttons = new Hashtable<>();
    private boolean ui_placement_right = true;
    private boolean immersive_mode;
    private boolean show_gui_video = true;

    public MainUI(MainActivity main_activity) {
        if (MyDebug.LOG)
            Log.d(TAG, "MainUI");
        this.main_activity = main_activity;

    }

    /**
     * Set icons for taking photos vs videos.
     * Also handles content descriptions for the take photo button and switch video button.
     */
    public void setTakePhotoIcon() {
        if (MyDebug.LOG)
            Log.d(TAG, "setTakePhotoIcon()");
        if (main_activity.getPreview() != null) {
            ImageButton view = (ImageButton) main_activity.findViewById(R.id.take_photo);
            int resource;
            int content_description;
            if (main_activity.getPreview().isVideo()) {
                if (MyDebug.LOG)
                    Log.d(TAG, "set icon to video");
                resource = main_activity.getPreview().isVideoRecording() ? R.drawable.take_video_recording : R.drawable.take_video_selector;
                content_description = main_activity.getPreview().isVideoRecording() ? R.string.stop_video : R.string.start_video;
            } else {

                //TODO Remove
                if (MyDebug.LOG)
                    Log.d(TAG, "set icon to photo");
                resource = R.drawable.take_photo_selector;
                content_description = R.string.take_photo;
            }
            view.setImageResource(resource);
            view.setContentDescription(main_activity.getResources().getString(content_description));
            view.setTag(resource); // for testing
        }
    }

    /**
     * Set content description for switch camera button.
     */
    public void setSwitchCameraContentDescription() {
        if (MyDebug.LOG)
            Log.d(TAG, "setSwitchCameraContentDescription()");
        if (main_activity.getPreview() != null && main_activity.getPreview().canSwitchCamera()) {
            ImageButton view = (ImageButton) main_activity.findViewById(R.id.switch_camera);
            int content_description;
            int cameraId = main_activity.getNextCameraId();
            if (main_activity.getPreview().getCameraControllerManager().isFrontFacing(cameraId)) {
                content_description = R.string.switch_to_front_camera;
            } else {
                content_description = R.string.switch_to_back_camera;
            }
            if (MyDebug.LOG)
                Log.d(TAG, "content_description: " + main_activity.getResources().getString(content_description));
            view.setContentDescription(main_activity.getResources().getString(content_description));
        }
    }

    /**
     * Set content description for pause video button.
     */
    public void setPauseVideoContentDescription() {
        if (MyDebug.LOG)
            Log.d(TAG, "setPauseVideoContentDescription()");
        ImageButton pauseVideoButton = (ImageButton) main_activity.findViewById(R.id.pause_video);
        int content_description;
        if (main_activity.getPreview().isVideoRecordingPaused()) {
            content_description = R.string.resume_video;
            pauseVideoButton.setImageResource(R.drawable.ic_play_circle_outline_white_48dp);
        } else {
            content_description = R.string.pause_video;
            pauseVideoButton.setImageResource(R.drawable.ic_pause_circle_outline_white_48dp);
        }
        if (MyDebug.LOG)
            Log.d(TAG, "content_description: " + main_activity.getResources().getString(content_description));
        pauseVideoButton.setContentDescription(main_activity.getResources().getString(content_description));
    }

    public boolean getUIPlacementRight() {
        return this.ui_placement_right;
    }

    public void setImmersiveMode(final boolean immersive_mode) {
        if (MyDebug.LOG)
            Log.d(TAG, "setImmersiveMode: " + immersive_mode);
        this.immersive_mode = immersive_mode;
        main_activity.runOnUiThread(new Runnable() {
            public void run() {
                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
                // if going into immersive mode, the we should set GONE the ones that are set GONE in showGUI(false)
                final int visibility = immersive_mode ? View.GONE : View.VISIBLE;
                if (MyDebug.LOG)
                    Log.d(TAG, "setImmersiveMode: set visibility: " + visibility);
                // n.b., don't hide share and trash buttons, as they require immediate user input for us to continue
                View switchCameraButton = main_activity.findViewById(R.id.switch_camera);
                if (main_activity.getPreview().getCameraControllerManager().getNumberOfCameras() > 1)
                    switchCameraButton.setVisibility(visibility);

                String pref_immersive_mode = sharedPreferences.getString(PreferenceKeys.ImmersiveModePreferenceKey, "immersive_mode_low_profile");
                if (pref_immersive_mode.equals("immersive_mode_everything")) {
                    if (sharedPreferences.getBoolean(PreferenceKeys.ShowTakePhotoPreferenceKey, true)) {
                        View takePhotoButton = main_activity.findViewById(R.id.take_photo);
                        takePhotoButton.setVisibility(visibility);
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && main_activity.getPreview().isVideoRecording()) {
                        View pauseVideoButton = main_activity.findViewById(R.id.pause_video);
                        pauseVideoButton.setVisibility(visibility);
                    }
                }
                if (!immersive_mode) {
                    // make sure the GUI is set up as expected
                    showGUI();
                }
            }
        });
    }

    public boolean inImmersiveMode() {
        return immersive_mode;
    }

    public void showGUI(final boolean show, final boolean is_video) {
        if (MyDebug.LOG) {
            Log.d(TAG, "showGUI: " + show);
            Log.d(TAG, "is_video: " + is_video);
        }
            this.show_gui_video = show;
        showGUI();
    }

    private void showGUI() {
        if (MyDebug.LOG) {
            Log.d(TAG, "showGUI");
            Log.d(TAG, "show_gui_video: " + show_gui_video);
        }
        if (inImmersiveMode())
            return;
        if (show_gui_video) {
            // call to reset the timer
            main_activity.initImmersiveMode();
        }
        main_activity.runOnUiThread(new Runnable() {
            public void run() {
                final int visibility = show_gui_video ? View.VISIBLE : View.GONE; // for UI that is hidden while taking photo or video
                View switchCameraButton = main_activity.findViewById(R.id.switch_camera);
                if (main_activity.getPreview().getCameraControllerManager().getNumberOfCameras() > 1)
                    switchCameraButton.setVisibility(visibility);
            }
        });
    }

}
