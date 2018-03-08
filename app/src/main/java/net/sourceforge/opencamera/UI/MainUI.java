package net.sourceforge.opencamera.UI;

import android.util.Log;
import android.widget.ImageButton;

import net.sourceforge.opencamera.MainActivity;
import net.sourceforge.opencamera.MyDebug;
import net.sourceforge.opencamera.R;

/**
 * This contains functionality related to the main UI.
 */
public class MainUI {
    private static final String TAG = "MainUI";
    private final MainActivity main_activity;

    public MainUI(MainActivity main_activity) {
        this.main_activity = main_activity;
    }

    /**
     * Set icons for taking photos vs videos.
     * Also handles content descriptions for the take photo button and switch video button.
     */
    public void setVideoIcon() {
        if (main_activity.getPreview() != null) {
            ImageButton view = main_activity.findViewById(R.id.record_video);
            int resource;
            int content_description;
            if (main_activity.getPreview().isVideo()) {
                resource = main_activity.getPreview().isVideoRecording() ? R.drawable.take_video_recording : R.drawable.take_video_selector;
                content_description = main_activity.getPreview().isVideoRecording() ? R.string.stop_video : R.string.start_video;
                view.setImageResource(resource);
                view.setContentDescription(main_activity.getResources().getString(content_description));
                view.setTag(resource); // for testing
            }
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

}
