package net.sourceforge.opencamera.UI;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.RelativeLayout;

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
    private int current_orientation;
    private boolean ui_placement_right = true;
    private boolean immersive_mode;
    private boolean show_gui_photo = true; // result of call to showGUI() - false means a "reduced" GUI is displayed, whilst taking photo or video
    private boolean show_gui_video = true;

    public MainUI(MainActivity main_activity) {
        if (MyDebug.LOG)
            Log.d(TAG, "MainUI");
        this.main_activity = main_activity;

        this.setIcon(R.id.gallery);
        this.setIcon(R.id.trash);
    }

    private void setIcon(int id) {
        if (MyDebug.LOG)
            Log.d(TAG, "setIcon: " + id);
        ImageButton button = (ImageButton) main_activity.findViewById(id);
        button.setBackgroundColor(Color.argb(63, 63, 63, 63)); // n.b., rgb color seems to be ignored for Android 6 onwards, but still relevant for older versions
    }

 /**
     * Similar view.setRotation(ui_rotation), but achieves this via an animation.
     */
    private void setViewRotation(View view, float ui_rotation) {
        //view.setRotation(ui_rotation);
        float rotate_by = ui_rotation - view.getRotation();
        if (rotate_by > 181.0f)
            rotate_by -= 360.0f;
        else if (rotate_by < -181.0f)
            rotate_by += 360.0f;
        // view.animate() modifies the view's rotation attribute, so it ends up equivalent to view.setRotation()
        // we use rotationBy() instead of rotation(), so we get the minimal rotation for clockwise vs anti-clockwise
        view.animate().rotationBy(rotate_by).setDuration(100).setInterpolator(new AccelerateDecelerateInterpolator()).start();
    }

    public void layoutUI() {
        layoutUI(false);
    }

    private void layoutUI(boolean popup_container_only) {
        long debug_time = 0;
        if (MyDebug.LOG) {
            Log.d(TAG, "layoutUI");
            debug_time = System.currentTimeMillis();
        }
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
        String ui_placement = sharedPreferences.getString(PreferenceKeys.UIPlacementPreferenceKey, "ui_right");
        // we cache the preference_ui_placement to save having to check it in the draw() method
        this.ui_placement_right = ui_placement.equals("ui_right");
        if (MyDebug.LOG)
            Log.d(TAG, "ui_placement: " + ui_placement);
        // new code for orientation fixed to landscape
        // the display orientation should be locked to landscape, but how many degrees is that?
        int rotation = main_activity.getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
            default:
                break;
        }
        // getRotation is anti-clockwise, but current_orientation is clockwise, so we add rather than subtract
        // relative_orientation is clockwise from landscape-left
        //int relative_orientation = (current_orientation + 360 - degrees) % 360;
        int relative_orientation = (current_orientation + degrees) % 360;
        if (MyDebug.LOG) {
            Log.d(TAG, "    current_orientation = " + current_orientation);
            Log.d(TAG, "    degrees = " + degrees);
            Log.d(TAG, "    relative_orientation = " + relative_orientation);
        }
        int ui_rotation = (360 - relative_orientation) % 360;
        main_activity.getPreview().setUIRotation(ui_rotation);
        int align_left = RelativeLayout.ALIGN_LEFT;
        int align_right = RelativeLayout.ALIGN_RIGHT;
        //int align_top = RelativeLayout.ALIGN_TOP;
        //int align_bottom = RelativeLayout.ALIGN_BOTTOM;
        int left_of = RelativeLayout.LEFT_OF;
        int right_of = RelativeLayout.RIGHT_OF;
        int above = RelativeLayout.ABOVE;
        int below = RelativeLayout.BELOW;
        int align_parent_left = RelativeLayout.ALIGN_PARENT_LEFT;
        int align_parent_right = RelativeLayout.ALIGN_PARENT_RIGHT;
        int align_parent_top = RelativeLayout.ALIGN_PARENT_TOP;
        int align_parent_bottom = RelativeLayout.ALIGN_PARENT_BOTTOM;
        if (!ui_placement_right) {
            //align_top = RelativeLayout.ALIGN_BOTTOM;
            //align_bottom = RelativeLayout.ALIGN_TOP;
            above = RelativeLayout.BELOW;
            below = RelativeLayout.ABOVE;
            align_parent_top = RelativeLayout.ALIGN_PARENT_BOTTOM;
            align_parent_bottom = RelativeLayout.ALIGN_PARENT_TOP;
        }

        if (!popup_container_only) {
            // we use a dummy button, so that the GUI buttons keep their positioning even if the Settings button is hidden (visibility set to View.GONE)
            View view = main_activity.findViewById(R.id.gui_anchor);
            RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) view.getLayoutParams();
            layoutParams.addRule(align_parent_left, 0);
            layoutParams.addRule(align_parent_right, RelativeLayout.TRUE);
            layoutParams.addRule(align_parent_top, RelativeLayout.TRUE);
            layoutParams.addRule(align_parent_bottom, 0);
            layoutParams.addRule(left_of, 0);
            layoutParams.addRule(right_of, 0);
            view.setLayoutParams(layoutParams);
            setViewRotation(view, ui_rotation);

            view = main_activity.findViewById(R.id.gallery);
            layoutParams = (RelativeLayout.LayoutParams) view.getLayoutParams();
            layoutParams.addRule(align_parent_top, RelativeLayout.TRUE);
            layoutParams.addRule(align_parent_bottom, 0);
            layoutParams.addRule(left_of, R.id.gui_anchor);
            layoutParams.addRule(right_of, 0);
            view.setLayoutParams(layoutParams);
            setViewRotation(view, ui_rotation);

            view = main_activity.findViewById(R.id.take_photo);
            layoutParams = (RelativeLayout.LayoutParams) view.getLayoutParams();
            layoutParams.addRule(align_parent_left, 0);
            layoutParams.addRule(align_parent_right, RelativeLayout.TRUE);
            view.setLayoutParams(layoutParams);
            setViewRotation(view, ui_rotation);

            view = main_activity.findViewById(R.id.switch_camera);
            layoutParams = (RelativeLayout.LayoutParams) view.getLayoutParams();
            layoutParams.addRule(align_parent_left, 0);
            layoutParams.addRule(align_parent_right, RelativeLayout.TRUE);
            view.setLayoutParams(layoutParams);
            setViewRotation(view, ui_rotation);

            view = main_activity.findViewById(R.id.pause_video);
            layoutParams = (RelativeLayout.LayoutParams) view.getLayoutParams();
            layoutParams.addRule(align_parent_left, 0);
            layoutParams.addRule(align_parent_right, RelativeLayout.TRUE);
            view.setLayoutParams(layoutParams);
            setViewRotation(view, ui_rotation);

            view = main_activity.findViewById(R.id.switch_video);
            layoutParams = (RelativeLayout.LayoutParams) view.getLayoutParams();
            layoutParams.addRule(align_parent_left, 0);
            layoutParams.addRule(align_parent_right, RelativeLayout.TRUE);
            view.setLayoutParams(layoutParams);
            setViewRotation(view, ui_rotation);

        }
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
            int switch_video_content_description;
            if (main_activity.getPreview().isVideo()) {
                if (MyDebug.LOG)
                    Log.d(TAG, "set icon to video");
                resource = main_activity.getPreview().isVideoRecording() ? R.drawable.take_video_recording : R.drawable.take_video_selector;
                content_description = main_activity.getPreview().isVideoRecording() ? R.string.stop_video : R.string.start_video;
                switch_video_content_description = R.string.switch_to_photo;
            } else {
                if (MyDebug.LOG)
                    Log.d(TAG, "set icon to photo");
                resource = R.drawable.take_photo_selector;
                content_description = R.string.take_photo;
                switch_video_content_description = R.string.switch_to_video;
            }
            view.setImageResource(resource);
            view.setContentDescription(main_activity.getResources().getString(content_description));
            view.setTag(resource); // for testing

            view = (ImageButton) main_activity.findViewById(R.id.switch_video);
            view.setContentDescription(main_activity.getResources().getString(switch_video_content_description));
            resource = main_activity.getPreview().isVideo() ? R.drawable.take_photo : R.drawable.take_video;
            view.setImageResource(resource);
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

    public void onOrientationChanged(int orientation) {
		/*if( MyDebug.LOG ) {
			Log.d(TAG, "onOrientationChanged()");
			Log.d(TAG, "orientation: " + orientation);
			Log.d(TAG, "current_orientation: " + current_orientation);
		}*/
        if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN)
            return;
        int diff = Math.abs(orientation - current_orientation);
        if (diff > 180)
            diff = 360 - diff;
        // only change orientation when sufficiently changed
        if (diff > 60) {
            orientation = (orientation + 45) / 90 * 90;
            orientation = orientation % 360;
            if (orientation != current_orientation) {
                this.current_orientation = orientation;
                if (MyDebug.LOG) {
                    Log.d(TAG, "current_orientation is now: " + current_orientation);
                }
                layoutUI();
            }
        }
    }

    public void setImmersiveMode(final boolean immersive_mode) {
        if (MyDebug.LOG)
            Log.d(TAG, "setImmersiveMode: " + immersive_mode);
        this.immersive_mode = immersive_mode;
        main_activity.runOnUiThread(new Runnable() {
            public void run() {
                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
                // if going into immersive mode, the we should set GONE the ones that are set GONE in showGUI(false)
                //final int visibility_gone = immersive_mode ? View.GONE : View.VISIBLE;
                final int visibility = immersive_mode ? View.GONE : View.VISIBLE;
                if (MyDebug.LOG)
                    Log.d(TAG, "setImmersiveMode: set visibility: " + visibility);
                // n.b., don't hide share and trash buttons, as they require immediate user input for us to continue
                View switchCameraButton = main_activity.findViewById(R.id.switch_camera);
                View switchVideoButton = main_activity.findViewById(R.id.switch_video);
                View galleryButton = main_activity.findViewById(R.id.gallery);
                if (main_activity.getPreview().getCameraControllerManager().getNumberOfCameras() > 1)
                    switchCameraButton.setVisibility(visibility);
                switchVideoButton.setVisibility(visibility);
                galleryButton.setVisibility(visibility);

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
        if (is_video)
            this.show_gui_video = show;
        else
            this.show_gui_photo = show;
        showGUI();
    }

    private void showGUI() {
        if (MyDebug.LOG) {
            Log.d(TAG, "showGUI");
            Log.d(TAG, "show_gui_photo: " + show_gui_photo);
            Log.d(TAG, "show_gui_video: " + show_gui_video);
        }
        if (inImmersiveMode())
            return;
        if ((show_gui_photo || show_gui_video) && main_activity.usingKitKatImmersiveMode()) {
            // call to reset the timer
            main_activity.initImmersiveMode();
        }
        main_activity.runOnUiThread(new Runnable() {
            public void run() {
                final int visibility = (show_gui_photo && show_gui_video) ? View.VISIBLE : View.GONE; // for UI that is hidden while taking photo or video
                View switchCameraButton = main_activity.findViewById(R.id.switch_camera);
                View switchVideoButton = main_activity.findViewById(R.id.switch_video);
                if (main_activity.getPreview().getCameraControllerManager().getNumberOfCameras() > 1)
                    switchCameraButton.setVisibility(visibility);
                switchVideoButton.setVisibility(visibility);
            }
        });
    }

    /**
     * Returns a (possibly translated) user readable string for a white balance preference value.
     * If the value is not recognised (this can happen for the old Camera API, some devices can
     * have device-specific options), then the received value is returned.
     */
    public String getEntryForWhiteBalance(String value) {
        int id = -1;
        switch (value) {
            case "auto":
                id = R.string.white_balance_auto;
                break;
            case "cloudy-daylight":
                id = R.string.white_balance_cloudy;
                break;
            case "daylight":
                id = R.string.white_balance_daylight;
                break;
            case "fluorescent":
                id = R.string.white_balance_fluorescent;
                break;
            case "incandescent":
                id = R.string.white_balance_incandescent;
                break;
            case "shade":
                id = R.string.white_balance_shade;
                break;
            case "twilight":
                id = R.string.white_balance_twilight;
                break;
            case "warm-fluorescent":
                id = R.string.white_balance_warm;
                break;
            case "manual":
                id = R.string.white_balance_manual;
                break;
            default:
                break;
        }
        String entry;
        if (id != -1) {
            entry = main_activity.getResources().getString(id);
        } else {
            entry = value;
        }
        return entry;
    }

    /**
     * Returns a (possibly translated) user readable string for a scene mode preference value.
     * If the value is not recognised (this can happen for the old Camera API, some devices can
     * have device-specific options), then the received value is returned.
     */
    public String getEntryForSceneMode(String value) {
        int id = -1;
        switch (value) {
            case "action":
                id = R.string.scene_mode_action;
                break;
            case "barcode":
                id = R.string.scene_mode_barcode;
                break;
            case "beach":
                id = R.string.scene_mode_beach;
                break;
            case "candlelight":
                id = R.string.scene_mode_candlelight;
                break;
            case "auto":
                id = R.string.scene_mode_auto;
                break;
            case "fireworks":
                id = R.string.scene_mode_fireworks;
                break;
            case "landscape":
                id = R.string.scene_mode_landscape;
                break;
            case "night":
                id = R.string.scene_mode_night;
                break;
            case "night-portrait":
                id = R.string.scene_mode_night_portrait;
                break;
            case "party":
                id = R.string.scene_mode_party;
                break;
            case "portrait":
                id = R.string.scene_mode_portrait;
                break;
            case "snow":
                id = R.string.scene_mode_snow;
                break;
            case "sports":
                id = R.string.scene_mode_sports;
                break;
            case "steadyphoto":
                id = R.string.scene_mode_steady_photo;
                break;
            case "sunset":
                id = R.string.scene_mode_sunset;
                break;
            case "theatre":
                id = R.string.scene_mode_theatre;
                break;
            default:
                break;
        }
        String entry;
        if (id != -1) {
            entry = main_activity.getResources().getString(id);
        } else {
            entry = value;
        }
        return entry;
    }

    /**
     * Returns a (possibly translated) user readable string for a color effect preference value.
     * If the value is not recognised (this can happen for the old Camera API, some devices can
     * have device-specific options), then the received value is returned.
     */
    public String getEntryForColorEffect(String value) {
        int id = -1;
        switch (value) {
            case "aqua":
                id = R.string.color_effect_aqua;
                break;
            case "blackboard":
                id = R.string.color_effect_blackboard;
                break;
            case "mono":
                id = R.string.color_effect_mono;
                break;
            case "negative":
                id = R.string.color_effect_negative;
                break;
            case "none":
                id = R.string.color_effect_none;
                break;
            case "posterize":
                id = R.string.color_effect_posterize;
                break;
            case "sepia":
                id = R.string.color_effect_sepia;
                break;
            case "solarize":
                id = R.string.color_effect_solarize;
                break;
            case "whiteboard":
                id = R.string.color_effect_whiteboard;
                break;
            default:
                break;
        }
        String entry;
        if (id != -1) {
            entry = main_activity.getResources().getString(id);
        } else {
            entry = value;
        }
        return entry;
    }

    // for testing
    public View getUIButton(String key) {
        if (MyDebug.LOG) {
            Log.d(TAG, "getPopupButton(" + key + "): " + test_ui_buttons.get(key));
            Log.d(TAG, "this: " + this);
            Log.d(TAG, "popup_buttons: " + test_ui_buttons);
        }
        return test_ui_buttons.get(key);
    }

}
