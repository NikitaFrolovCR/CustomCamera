package net.sourceforge.opencamera.UI;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.preference.PreferenceManager;
import android.util.Log;

import net.sourceforge.opencamera.CameraController.CameraController;
import net.sourceforge.opencamera.GyroSensor;
import net.sourceforge.opencamera.MainActivity;
import net.sourceforge.opencamera.MyApplicationInterface;
import net.sourceforge.opencamera.MyDebug;
import net.sourceforge.opencamera.PreferenceKeys;
import net.sourceforge.opencamera.Preview.Preview;
import net.sourceforge.opencamera.R;

import java.text.DecimalFormat;
import java.util.Locale;

public class DrawPreview {
	private static final String TAG = "DrawPreview";

	private final MainActivity main_activity;
	private final MyApplicationInterface applicationInterface;

	// store to avoid calling PreferenceManager.getDefaultSharedPreferences() repeatedly
	private final SharedPreferences sharedPreferences;

	// avoid doing things that allocate memory every frame!
	private final Paint p = new Paint();
	private final static DecimalFormat decimalFormat = new DecimalFormat("#0.0");
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


	private Bitmap last_thumbnail; // thumbnail of last picture taken
	private volatile boolean thumbnail_anim; // whether we are displaying the thumbnail animation; must be volatile for test project reading the state


	private boolean show_last_image;
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
		this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
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
    
	public boolean hasThumbnailAnimation() {
		return this.thumbnail_anim;
	}
	
	/** Displays the thumbnail as a fullscreen image (used for pause preview option).
	 */
	public void showLastImage() {
		if( MyDebug.LOG )
			Log.d(TAG, "showLastImage");
		this.show_last_image = true;
	}
	
	public void clearLastImage() {
		if( MyDebug.LOG )
			Log.d(TAG, "clearLastImage");
		this.show_last_image = false;
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
	
	public void turnFrontScreenFlashOn() {
		if( MyDebug.LOG )
			Log.d(TAG, "turnFrontScreenFlashOn");
		front_screen_flash = true;
	}

	public void onCaptureStarted() {
		if( MyDebug.LOG )
			Log.d(TAG, "onCaptureStarted");
		capture_started = true;
	}
	
	public void setGyroDirectionMarker(float x, float y, float z) {
		enable_gyro_target_spot = true;
		gyro_direction[0] = x;
		gyro_direction[1] = y;
		gyro_direction[2] = z;
	}

	public void clearGyroDirectionMarker() {
		enable_gyro_target_spot = false;
	}

    private String getTimeStringFromSeconds(long time) {
    	int secs = (int)(time % 60);
    	time /= 60;
    	int mins = (int)(time % 60);
    	time /= 60;
    	long hours = time;
    	return hours + ":" + String.format(Locale.getDefault(), "%02d", mins) + ":" + String.format(Locale.getDefault(), "%02d", secs);
    }

	private void drawCropGuides(Canvas canvas) {
		Preview preview = main_activity.getPreview();
		CameraController camera_controller = preview.getCameraController();
		if( preview.isVideo()) {
			String preference_crop_guide = sharedPreferences.getString(PreferenceKeys.ShowCropGuidePreferenceKey, "crop_guide_none");
			if( camera_controller != null && preview.getTargetRatio() > 0.0 && !preference_crop_guide.equals("crop_guide_none") ) {
				p.setStyle(Paint.Style.STROKE);
				p.setColor(Color.rgb(255, 235, 59)); // Yellow 500
				double crop_ratio = -1.0;
				switch(preference_crop_guide) {
					case "crop_guide_1":
						crop_ratio = 1.0;
						break;
					case "crop_guide_1.25":
						crop_ratio = 1.25;
						break;
					case "crop_guide_1.33":
						crop_ratio = 1.33333333;
						break;
					case "crop_guide_1.4":
						crop_ratio = 1.4;
						break;
					case "crop_guide_1.5":
						crop_ratio = 1.5;
						break;
					case "crop_guide_1.78":
						crop_ratio = 1.77777778;
						break;
					case "crop_guide_1.85":
						crop_ratio = 1.85;
						break;
					case "crop_guide_2.33":
						crop_ratio = 2.33333333;
						break;
					case "crop_guide_2.35":
						crop_ratio = 2.35006120; // actually 1920:817
						break;
					case "crop_guide_2.4":
						crop_ratio = 2.4;
						break;
				}
				if( crop_ratio > 0.0 && Math.abs(preview.getTargetRatio() - crop_ratio) > 1.0e-5 ) {
		    		/*if( MyDebug.LOG ) {
		    			Log.d(TAG, "crop_ratio: " + crop_ratio);
		    			Log.d(TAG, "preview_targetRatio: " + preview_targetRatio);
		    			Log.d(TAG, "canvas width: " + canvas.getWidth());
		    			Log.d(TAG, "canvas height: " + canvas.getHeight());
		    		}*/
					int left = 1, top = 1, right = canvas.getWidth()-1, bottom = canvas.getHeight()-1;
					if( crop_ratio > preview.getTargetRatio() ) {
						// crop ratio is wider, so we have to crop top/bottom
						double new_hheight = ((double)canvas.getWidth()) / (2.0f*crop_ratio);
						top = (canvas.getHeight()/2 - (int)new_hheight);
						bottom = (canvas.getHeight()/2 + (int)new_hheight);
					}
					else {
						// crop ratio is taller, so we have to crop left/right
						double new_hwidth = (((double)canvas.getHeight()) * crop_ratio) / 2.0f;
						left = (canvas.getWidth()/2 - (int)new_hwidth);
						right = (canvas.getWidth()/2 + (int)new_hwidth);
					}
					canvas.drawRect(left, top, right, bottom, p);
				}
				p.setStyle(Paint.Style.FILL); // reset
			}
		}
	}

    /** Formats the level_angle double into a string.
	 *  Beware of calling this too often - shouldn't be every frame due to performance of DecimalFormat
	 *  (see http://stackoverflow.com/questions/8553672/a-faster-alternative-to-decimalformat-format ).
     */
	public static String formatLevelAngle(double level_angle) {
        String number_string = decimalFormat.format(level_angle);
        if( Math.abs(level_angle) < 0.1 ) {
            // avoids displaying "-0.0", see http://stackoverflow.com/questions/11929096/negative-sign-in-case-of-zero-in-java
            // only do this when level_angle is small, to help performance
            number_string = number_string.replaceAll("^-(?=0(.0*)?$)", "");
        }
        return number_string;
    }

	/** This includes drawing of the UI that requires the canvas to be rotated according to the preview's
	 *  current UI rotation.
	 */
	private void drawUI(Canvas canvas, long time_ms) {
		Preview preview = main_activity.getPreview();
		CameraController camera_controller = preview.getCameraController();
		int ui_rotation = preview.getUIRotation();
		boolean ui_placement_right = main_activity.getMainUI().getUIPlacementRight();
		boolean has_level_angle = preview.hasLevelAngle();
		double level_angle = preview.getLevelAngle();
		boolean has_geo_direction = preview.hasGeoDirection();
		double geo_direction = preview.getGeoDirection();

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
            	if( main_activity.isScreenLocked() ) {
            		// writing in reverse order, bottom to top
            		applicationInterface.drawTextWithBackground(canvas, p, getContext().getResources().getString(R.string.screen_lock_message_2), color, Color.BLACK, canvas.getWidth() / 2, text_base_y - pixels_offset_y);
            		pixels_offset_y += text_y;
            		applicationInterface.drawTextWithBackground(canvas, p, getContext().getResources().getString(R.string.screen_lock_message_1), color, Color.BLACK, canvas.getWidth() / 2, text_base_y - pixels_offset_y);
            		pixels_offset_y += text_y;
            	}
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
			//canvas.drawRect(0.0f, 0.0f, 100.0f, 100.0f, p);
			//canvas.drawRGB(255, 0, 0);
			//canvas.drawRect(0.0f, 0.0f, canvas.getWidth(), canvas.getHeight(), p);
		}

		canvas.restore();
	}

	public void onDrawPreview(Canvas canvas) {
		Preview preview = main_activity.getPreview();
		CameraController camera_controller = preview.getCameraController();
		int ui_rotation = preview.getUIRotation();

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
		drawCropGuides(canvas);

		if( show_last_image && last_thumbnail != null ) {
			// If changing this code, ensure that pause preview still works when:
			// - Taking a photo in portrait or landscape - and check rotating the device while preview paused
			// - Taking a photo with lock to portrait/landscape options still shows the thumbnail with aspect ratio preserved
			p.setColor(Color.rgb(0, 0, 0)); // in case image doesn't cover the canvas (due to different aspect ratios)
			canvas.drawRect(0.0f, 0.0f, canvas.getWidth(), canvas.getHeight(), p); // in case
			last_image_src_rect.left = 0;
			last_image_src_rect.top = 0;
			last_image_src_rect.right = last_thumbnail.getWidth();
			last_image_src_rect.bottom = last_thumbnail.getHeight();
			if( ui_rotation == 90 || ui_rotation == 270 ) {
				last_image_src_rect.right = last_thumbnail.getHeight();
				last_image_src_rect.bottom = last_thumbnail.getWidth();
			}
			last_image_dst_rect.left = 0;
			last_image_dst_rect.top = 0;
			last_image_dst_rect.right = canvas.getWidth();
			last_image_dst_rect.bottom = canvas.getHeight();
			/*if( MyDebug.LOG ) {
				Log.d(TAG, "thumbnail: " + last_thumbnail.getWidth() + " x " + last_thumbnail.getHeight());
				Log.d(TAG, "canvas: " + canvas.getWidth() + " x " + canvas.getHeight());
			}*/
			last_image_matrix.setRectToRect(last_image_src_rect, last_image_dst_rect, Matrix.ScaleToFit.CENTER); // use CENTER to preserve aspect ratio
			if( ui_rotation == 90 || ui_rotation == 270 ) {
				// the rotation maps (0, 0) to (tw/2 - th/2, th/2 - tw/2), so we translate to undo this
				float diff = last_thumbnail.getHeight() - last_thumbnail.getWidth();
				last_image_matrix.preTranslate(diff/2.0f, -diff/2.0f);
			}
			last_image_matrix.preRotate(ui_rotation, last_thumbnail.getWidth()/2.0f, last_thumbnail.getHeight()/2.0f);
			canvas.drawBitmap(last_thumbnail, last_image_matrix, p);
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

		if( enable_gyro_target_spot ) {
			GyroSensor gyroSensor = main_activity.getApplicationInterface().getGyroSensor();
			if( gyroSensor.isRecording() ) {
				gyroSensor.getRelativeInverseVector(transformed_gyro_direction, gyro_direction);
				// note that although X of gyro_direction represents left to right on the device, because we're in landscape mode,
				// this is y coordinates on the screen
				float angle_x = - (float)Math.asin(transformed_gyro_direction[1]);
				float angle_y = - (float)Math.asin(transformed_gyro_direction[0]);
				if( Math.abs(angle_x) < 0.5f*Math.PI && Math.abs(angle_y) < 0.5f*Math.PI ) {
					float camera_angle_x = preview.getViewAngleX();
					float camera_angle_y = preview.getViewAngleY();
					float angle_scale_x = (float) (canvas.getWidth() / (2.0 * Math.tan(Math.toRadians((camera_angle_x / 2.0)))));
					float angle_scale_y = (float) (canvas.getHeight() / (2.0 * Math.tan(Math.toRadians((camera_angle_y / 2.0)))));
					angle_scale_x *= preview.getZoomRatio();
					angle_scale_y *= preview.getZoomRatio();
					float distance_x = angle_scale_x * (float) Math.tan(angle_x); // angle_scale is already in pixels rather than dps
					float distance_y = angle_scale_y * (float) Math.tan(angle_y); // angle_scale is already in pixels rather than dps
					p.setColor(Color.WHITE);
					drawGyroSpot(canvas, 0.0f, 0.0f); // draw spot for the centre of the screen, to help the user orient the device
					p.setColor(Color.BLUE);
					drawGyroSpot(canvas, distance_x, distance_y);
				}
			}
		}
    }

    private void drawGyroSpot(Canvas canvas, float distance_x, float distance_y) {
		p.setAlpha(64);
		float radius = (45 * scale + 0.5f); // convert dps to pixels
		canvas.drawCircle(canvas.getWidth()/2.0f + distance_x, canvas.getHeight()/2.0f + distance_y, radius, p);
		p.setAlpha(255);
	}
}
