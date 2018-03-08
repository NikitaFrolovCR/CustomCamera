package net.sourceforge.opencamera.UI;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.Log;

import net.sourceforge.opencamera.CameraController.CameraController;
import net.sourceforge.opencamera.MainActivity;
import net.sourceforge.opencamera.MyApplicationInterface;
import net.sourceforge.opencamera.MyDebug;
import net.sourceforge.opencamera.Preview.Preview;
import net.sourceforge.opencamera.R;

import java.util.Locale;

public class DrawPreview {
	private static final String TAG = "DrawPreview";

	private final MainActivity main_activity;
	private final MyApplicationInterface applicationInterface;

	// avoid doing things that allocate memory every frame!
	private final Paint p = new Paint();
	private final float scale;
	private final float stroke_width;


	private Bitmap location_bitmap;
	private Bitmap location_off_bitmap;

	private Bitmap raw_bitmap;
	private Bitmap auto_stabilise_bitmap;
	private Bitmap hdr_bitmap;
	private Bitmap nr_bitmap;
	private Bitmap photostamp_bitmap;
	private Bitmap flash_bitmap;
	private Bitmap face_detection_bitmap;

	private final RectF last_image_src_rect = new RectF();
	private final RectF last_image_dst_rect = new RectF();
	private final Matrix last_image_matrix = new Matrix();


    private boolean taking_picture; // true iff camera is in process of capturing a picture (including any necessary prior steps such as autofocus, flash/precapture)
	private boolean capture_started; // true iff the camera is capturing
    private boolean front_screen_flash; // true iff the front screen display should maximise to simulate flash
    
	private boolean continuous_focus_moving;

	private boolean enable_gyro_target_spot;
	private final float [] gyro_direction = new float[3];
	private final float [] transformed_gyro_direction = new float[3];

	public DrawPreview(MainActivity main_activity, MyApplicationInterface applicationInterface) {
		if( MyDebug.LOG )
			Log.d(TAG, "DrawPreview");
		this.main_activity = main_activity;
		this.applicationInterface = applicationInterface;
		// n.b., don't call updateSettings() here, as it may rely on things that aren't yet initialise (e.g., the preview)
		// see testHDRRestart

		p.setAntiAlias(true);
        p.setStrokeCap(Paint.Cap.ROUND);
		scale = getContext().getResources().getDisplayMetrics().density;
		this.stroke_width = (1.0f * scale + 0.5f); // convert dps to pixels
		p.setStrokeWidth(stroke_width);
	}
	
	public void onDestroy() {
		if( MyDebug.LOG )
			Log.d(TAG, "onDestroy");
		// clean up just in case
		if( location_bitmap != null ) {
			location_bitmap.recycle();
			location_bitmap = null;
		}
		if( location_off_bitmap != null ) {
			location_off_bitmap.recycle();
			location_off_bitmap = null;
		}
		if( raw_bitmap != null ) {
			raw_bitmap.recycle();
			raw_bitmap = null;
		}
		if( auto_stabilise_bitmap != null ) {
			auto_stabilise_bitmap.recycle();
			auto_stabilise_bitmap = null;
		}
		if( hdr_bitmap != null ) {
			hdr_bitmap.recycle();
			hdr_bitmap = null;
		}
		if( nr_bitmap != null ) {
			nr_bitmap.recycle();
			nr_bitmap = null;
		}
		if( photostamp_bitmap != null ) {
			photostamp_bitmap.recycle();
			photostamp_bitmap = null;
		}
		if( flash_bitmap != null ) {
			flash_bitmap.recycle();
			flash_bitmap = null;
		}
		if( face_detection_bitmap != null ) {
			face_detection_bitmap.recycle();
			face_detection_bitmap = null;
		}
	}

	private Context getContext() {
    	return main_activity;
    }

	public void cameraInOperation(boolean in_operation) {
    	if( in_operation && !main_activity.getPreview().isVideo() ) {
    		taking_picture = true;
    	}
    	else {
    		taking_picture = false;
    		front_screen_flash = false;
			capture_started = false;
    	}
    }

    private String getTimeStringFromSeconds(long time) {
    	int secs = (int)(time % 60);
    	time /= 60;
    	int mins = (int)(time % 60);
    	time /= 60;
    	long hours = time;
    	return hours + ":" + String.format(Locale.getDefault(), "%02d", mins) + ":" + String.format(Locale.getDefault(), "%02d", secs);
    }

	/** This includes drawing of the UI that requires the canvas to be rotated according to the preview's
	 *  current UI rotation.
	 */
	private void drawUI(Canvas canvas, long time_ms) {
		Preview preview = main_activity.getPreview();
		CameraController camera_controller = preview.getCameraController();
		int ui_rotation = preview.getUIRotation();
		boolean ui_placement_right = main_activity.getMainUI().getUIPlacementRight();
		canvas.save();
		canvas.rotate(ui_rotation, canvas.getWidth()/2.0f, canvas.getHeight()/2.0f);

		if( camera_controller != null && !preview.isPreviewPaused() ) {
			/*canvas.drawText("PREVIEW", canvas.getWidth() / 2,
					canvas.getHeight() / 2, p);*/
			int text_y = (int) (20 * scale + 0.5f); // convert dps to pixels
			// fine tuning to adjust placement of text with respect to the GUI, depending on orientation
			int text_base_y = 0;
			if( ui_rotation == ( ui_placement_right ? 0 : 180 ) ) {
				text_base_y = canvas.getHeight() - (int)(0.5*text_y);
			}
			else if( ui_rotation == ( ui_placement_right ? 180 : 0 ) ) {
				text_base_y = canvas.getHeight() - (int)(2.5*text_y); // leave room for GUI icons
			}
			else if( ui_rotation == 90 || ui_rotation == 270 ) {
				//text_base_y = canvas.getHeight() + (int)(0.5*text_y);
				/*ImageButton view = (ImageButton)main_activity.findViewById(R.id.take_photo);
				// align with "top" of the take_photo button, but remember to take the rotation into account!
				view.getLocationOnScreen(gui_location);
				int view_left = gui_location[0];
				preview.getView().getLocationOnScreen(gui_location);
				int this_left = gui_location[0];
				// diff_x is the difference from the centre of the canvas to the position we want
				int diff_x = view_left - ( this_left + canvas.getWidth()/2 );
				*/
				/*if( MyDebug.LOG ) {
					Log.d(TAG, "view left: " + view_left);
					Log.d(TAG, "this left: " + this_left);
					Log.d(TAG, "canvas is " + canvas.getWidth() + " x " + canvas.getHeight());
				}*/
				// diff_x is the difference from the centre of the canvas to the position we want
				// assumes canvas is centered
				// avoids calling getLocationOnScreen for performance
				int diff_x = preview.getView().getRootView().getRight()/2 - (int) (100 * scale + 0.5f); // convert dps to pixels
				int max_x = canvas.getWidth();
				if( ui_rotation == 90 ) {
					// so we don't interfere with the top bar info (datetime, free memory, ISO)
					max_x -= (int)(2.5*text_y);
				}
				/*if( MyDebug.LOG ) {
					Log.d(TAG, "root view right: " + preview.getView().getRootView().getRight());
					Log.d(TAG, "diff_x: " + diff_x);
					Log.d(TAG, "canvas.getWidth()/2 + diff_x: " + (canvas.getWidth()/2+diff_x));
					Log.d(TAG, "max_x: " + max_x);
				}*/
				if( canvas.getWidth()/2 + diff_x > max_x ) {
					// in case goes off the size of the canvas, for "black bar" cases (when preview aspect ratio != screen aspect ratio)
					diff_x = max_x - canvas.getWidth()/2;
				}
				text_base_y = canvas.getHeight()/2 + diff_x - (int)(0.5*text_y);
			}


			 if( preview.isVideoRecording() ) {
            	long video_time = preview.getVideoTime();
            	String time_s = getTimeStringFromSeconds(video_time/1000);
            	/*if( MyDebug.LOG )
					Log.d(TAG, "video_time: " + video_time + " " + time_s);*/
    			p.setTextSize(14 * scale + 0.5f); // convert dps to pixels
    			p.setTextAlign(Paint.Align.CENTER);
				int pixels_offset_y = 3*text_y; // avoid overwriting the zoom, and also allow a bit extra space
				int color = Color.rgb(244, 67, 54); // Red 500
				if( !preview.isVideoRecordingPaused() || ((int)(time_ms / 500)) % 2 == 0 ) { // if video is paused, then flash the video time
					applicationInterface.drawTextWithBackground(canvas, p, time_s, color, Color.BLACK, canvas.getWidth() / 2, text_base_y - pixels_offset_y);
				}
			}
			else if( taking_picture && capture_started ) {
				if( camera_controller.isManualISO() ) {
					// only show "capturing" text with time for manual exposure time >= 0.5s
					long exposure_time = camera_controller.getExposureTime();
					if( exposure_time >= 500000000L ) {
						if( ((int)(time_ms / 500)) % 2 == 0 ) {
							p.setTextSize(14 * scale + 0.5f); // convert dps to pixels
							p.setTextAlign(Paint.Align.CENTER);
							int pixels_offset_y = 3*text_y; // avoid overwriting the zoom, and also allow a bit extra space
							int color = Color.rgb(244, 67, 54); // Red 500
							applicationInterface.drawTextWithBackground(canvas, p, getContext().getResources().getString(R.string.capturing), color, Color.BLACK, canvas.getWidth() / 2, text_base_y - pixels_offset_y);
						}
					}
				}
			}

		}
		else if( camera_controller == null ) {
			/*if( MyDebug.LOG ) {
				Log.d(TAG, "no camera!");
				Log.d(TAG, "width " + canvas.getWidth() + " height " + canvas.getHeight());
			}*/
			p.setColor(Color.WHITE);
			p.setTextSize(14 * scale + 0.5f); // convert dps to pixels
			p.setTextAlign(Paint.Align.CENTER);
			int pixels_offset = (int) (20 * scale + 0.5f); // convert dps to pixels
			if( preview.hasPermissions() ) {
				if( preview.openCameraFailed() ) {
					canvas.drawText(getContext().getResources().getString(R.string.failed_to_open_camera_1), canvas.getWidth() / 2.0f, canvas.getHeight() / 2.0f, p);
					canvas.drawText(getContext().getResources().getString(R.string.failed_to_open_camera_2), canvas.getWidth() / 2.0f, canvas.getHeight() / 2.0f + pixels_offset, p);
					canvas.drawText(getContext().getResources().getString(R.string.failed_to_open_camera_3), canvas.getWidth() / 2.0f, canvas.getHeight() / 2.0f + 2 * pixels_offset, p);
				}
			}
			else {
				canvas.drawText(getContext().getResources().getString(R.string.no_permission), canvas.getWidth() / 2.0f, canvas.getHeight() / 2.0f, p);
			}
		}

		canvas.restore();
	}

	public void onDrawPreview(Canvas canvas) {
		Preview preview = main_activity.getPreview();
		CameraController camera_controller = preview.getCameraController();

		final long time_ms = System.currentTimeMillis();

		// see documentation for CameraController.shouldCoverPreview()
		if( preview.usingCamera2API() && ( camera_controller == null || camera_controller.shouldCoverPreview() ) ) {
			p.setColor(Color.BLACK);
			canvas.drawRect(0.0f, 0.0f, canvas.getWidth(), canvas.getHeight(), p);
		}

		if( camera_controller!= null && front_screen_flash ) {
			p.setColor(Color.WHITE);
			canvas.drawRect(0.0f, 0.0f, canvas.getWidth(), canvas.getHeight(), p);
		}
		if( main_activity.getMainUI().inImmersiveMode() ) {
				return;
		}

		if( camera_controller != null && taking_picture && !front_screen_flash ) {
			p.setColor(Color.WHITE);
			p.setStyle(Paint.Style.STROKE);
			float this_stroke_width = (5.0f * scale + 0.5f); // convert dps to pixels
			p.setStrokeWidth(this_stroke_width);
			canvas.drawRect(0.0f, 0.0f, canvas.getWidth(), canvas.getHeight(), p);
			p.setStyle(Paint.Style.FILL); // reset
			p.setStrokeWidth(stroke_width); // reset
		}
		
		drawUI(canvas, time_ms);

		CameraController.Face [] faces_detected = preview.getFacesDetected();
		if( faces_detected != null ) {
			p.setColor(Color.rgb(255, 235, 59)); // Yellow 500
			p.setStyle(Paint.Style.STROKE);
			for(CameraController.Face face : faces_detected) {
				// Android doc recommends filtering out faces with score less than 50 (same for both Camera and Camera2 APIs)
				if( face.score >= 50 ) {
					canvas.drawRect(face.rect, p);
				}
			}
			p.setStyle(Paint.Style.FILL); // reset
		}

    }
	}
