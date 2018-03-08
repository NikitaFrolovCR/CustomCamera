package net.sourceforge.opencamera;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;

import net.sourceforge.opencamera.Preview.ApplicationInterface;
import net.sourceforge.opencamera.Preview.VideoProfile;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Calendar;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

/** Our implementation of ApplicationInterface, see there for details.
 */
public class MyApplicationInterface implements ApplicationInterface {
	private static final String TAG = "MyApplicationInterface";

    
	private final MainActivity main_activity;
	private final StorageUtils storageUtils;

	private File last_video_file = null;
	private Uri last_video_file_saf = null;

	private final Timer subtitleVideoTimer = new Timer();
	private TimerTask subtitleVideoTimerTask;

    private boolean used_front_screen_flash ;

	// store to avoid calling PreferenceManager.getDefaultSharedPreferences() repeatedly
	private final SharedPreferences sharedPreferences;
	
	// camera properties which are saved in bundle, but not stored in preferences (so will be remembered if the app goes into background, but not after restart)
	private int cameraId = 0;
	private int zoom_factor = 0;
	private float focus_distance = 0.0f;

	MyApplicationInterface(MainActivity main_activity, Bundle savedInstanceState) {
		long debug_time = 0;
		if( MyDebug.LOG ) {
			Log.d(TAG, "MyApplicationInterface");
			debug_time = System.currentTimeMillis();
		}
		this.main_activity = main_activity;
		this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
		if( MyDebug.LOG )
			Log.d(TAG, "MyApplicationInterface: time after creating location supplier: " + (System.currentTimeMillis() - debug_time));
		this.storageUtils = new StorageUtils(main_activity);
		if( MyDebug.LOG )
			Log.d(TAG, "MyApplicationInterface: time after creating storage utils: " + (System.currentTimeMillis() - debug_time));
		
        if( savedInstanceState != null ) {
			// load the things we saved in onSaveInstanceState().
            if( MyDebug.LOG )
                Log.d(TAG, "read from savedInstanceState");
    		cameraId = savedInstanceState.getInt("cameraId", 0);
			if( MyDebug.LOG )
				Log.d(TAG, "found cameraId: " + cameraId);
    		zoom_factor = savedInstanceState.getInt("zoom_factor", 0);
			if( MyDebug.LOG )
				Log.d(TAG, "found zoom_factor: " + zoom_factor);
			focus_distance = savedInstanceState.getFloat("focus_distance", 0.0f);
			if( MyDebug.LOG )
				Log.d(TAG, "found focus_distance: " + focus_distance);
        }

		if( MyDebug.LOG )
			Log.d(TAG, "MyApplicationInterface: total time to create MyApplicationInterface: " + (System.currentTimeMillis() - debug_time));
	}

	/** Here we save states which aren't saved in preferences (we don't want them to be saved if the
	 *  application is restarted from scratch), but we do want to preserve if Android has to recreate
	 *  the application (e.g., configuration change, or it's destroyed while in background).
	 */
	void onSaveInstanceState(Bundle state) {
		if( MyDebug.LOG )
			Log.d(TAG, "onSaveInstanceState");
		if( MyDebug.LOG )
			Log.d(TAG, "save cameraId: " + cameraId);
    	state.putInt("cameraId", cameraId);
		if( MyDebug.LOG )
			Log.d(TAG, "save zoom_factor: " + zoom_factor);
    	state.putInt("zoom_factor", zoom_factor);
		if( MyDebug.LOG )
			Log.d(TAG, "save focus_distance: " + focus_distance);
    	state.putFloat("focus_distance", focus_distance);
	}
	
	void onDestroy() {
		if( MyDebug.LOG )
			Log.d(TAG, "onDestroy");
	}
	
	StorageUtils getStorageUtils() {
		return storageUtils;
	}

    @Override
	public Context getContext() {
    	return main_activity;
    }
    
    @Override
	public boolean useCamera2() {
        if( main_activity.supportsCamera2() ) {
    		return sharedPreferences.getBoolean(PreferenceKeys.UseCamera2PreferenceKey, false);
        }
        return false;
    }
	
	@Override
	public int createOutputVideoMethod() {
        String action = main_activity.getIntent().getAction();
        if( MediaStore.ACTION_VIDEO_CAPTURE.equals(action) ) {
			if( MyDebug.LOG )
				Log.d(TAG, "from video capture intent");
	        Bundle myExtras = main_activity.getIntent().getExtras();
	        if (myExtras != null) {
	        	Uri intent_uri = myExtras.getParcelable(MediaStore.EXTRA_OUTPUT);
	        	if( intent_uri != null ) {
	    			if( MyDebug.LOG )
	    				Log.d(TAG, "save to: " + intent_uri);
	        		return VIDEOMETHOD_URI;
	        	}
	        }
        	// if no EXTRA_OUTPUT, we should save to standard location, and will pass back the Uri of that location
			if( MyDebug.LOG )
				Log.d(TAG, "intent uri not specified");
			// note that SAF URIs don't seem to work for calling applications (tested with Grabilla and "Photo Grabber Image From Video" (FreezeFrame)), so we use standard folder with non-SAF method
			return VIDEOMETHOD_FILE;
        }
        boolean using_saf = storageUtils.isUsingSAF();
		return using_saf ? VIDEOMETHOD_SAF : VIDEOMETHOD_FILE;
	}

	@Override
	public File createOutputVideoFile() throws IOException {
		last_video_file = storageUtils.createOutputMediaFile(StorageUtils.MEDIA_TYPE_VIDEO, "", "mp4", new Date());
		return last_video_file;
	}

	@Override
	public Uri createOutputVideoSAF() throws IOException {
		last_video_file_saf = storageUtils.createOutputMediaFileSAF(StorageUtils.MEDIA_TYPE_VIDEO, "", "mp4", new Date());
		return last_video_file_saf;
	}

	@Override
	public Uri createOutputVideoUri() {
        String action = main_activity.getIntent().getAction();
        if( MediaStore.ACTION_VIDEO_CAPTURE.equals(action) ) {
			if( MyDebug.LOG )
				Log.d(TAG, "from video capture intent");
	        Bundle myExtras = main_activity.getIntent().getExtras();
	        if (myExtras != null) {
	        	Uri intent_uri = myExtras.getParcelable(MediaStore.EXTRA_OUTPUT);
	        	if( intent_uri != null ) {
	    			if( MyDebug.LOG )
	    				Log.d(TAG, "save to: " + intent_uri);
	    			return intent_uri;
	        	}
	        }
        }
        throw new RuntimeException(); // programming error if we arrived here
	}

	@Override
	public int getCameraIdPref() {
		return cameraId;
	}
	
    @Override
	public String getFlashPref() {
		return sharedPreferences.getString(PreferenceKeys.getFlashPreferenceKey(cameraId), "");
    }

    @Override
	public String getFocusPref(boolean is_video) {
		return sharedPreferences.getString(PreferenceKeys.getFocusPreferenceKey(cameraId, is_video), "");
    }

    @Override
	public boolean isVideoPref() {
		return sharedPreferences.getBoolean(PreferenceKeys.IsVideoPreferenceKey, false);
    }

    @Override
	public String getSceneModePref() {
		return sharedPreferences.getString(PreferenceKeys.SceneModePreferenceKey, "auto");
    }
    
    @Override
    public String getColorEffectPref() {
		return sharedPreferences.getString(PreferenceKeys.ColorEffectPreferenceKey, "none");
    }

    @Override
    public String getWhiteBalancePref() {
		return sharedPreferences.getString(PreferenceKeys.WhiteBalancePreferenceKey, "auto");
    }

	@Override
	public int getWhiteBalanceTemperaturePref() {
		return sharedPreferences.getInt(PreferenceKeys.WhiteBalanceTemperaturePreferenceKey, 5000);
	}

    @Override
	public Pair<Integer, Integer> getCameraResolutionPref() {
		String resolution_value = sharedPreferences.getString(PreferenceKeys.getResolutionPreferenceKey(cameraId), "");
		if( MyDebug.LOG )
			Log.d(TAG, "resolution_value: " + resolution_value);
		if( resolution_value.length() > 0 ) {
			// parse the saved size, and make sure it is still valid
			int index = resolution_value.indexOf(' ');
			if( index == -1 ) {
				if( MyDebug.LOG )
					Log.d(TAG, "resolution_value invalid format, can't find space");
			}
			else {
				String resolution_w_s = resolution_value.substring(0, index);
				String resolution_h_s = resolution_value.substring(index+1);
				if( MyDebug.LOG ) {
					Log.d(TAG, "resolution_w_s: " + resolution_w_s);
					Log.d(TAG, "resolution_h_s: " + resolution_h_s);
				}
				try {
					int resolution_w = Integer.parseInt(resolution_w_s);
					if( MyDebug.LOG )
						Log.d(TAG, "resolution_w: " + resolution_w);
					int resolution_h = Integer.parseInt(resolution_h_s);
					if( MyDebug.LOG )
						Log.d(TAG, "resolution_h: " + resolution_h);
					return new Pair<>(resolution_w, resolution_h);
				}
				catch(NumberFormatException exception) {
					if( MyDebug.LOG )
						Log.d(TAG, "resolution_value invalid format, can't parse w or h to int");
				}
			}
		}
		return null;
    }

	/** getImageQualityPref() returns the image quality used for the Camera Controller for taking a
	 *  photo - in some cases, we may set that to a higher value, then perform processing on the
	 *  resultant JPEG before resaving. This method returns the image quality setting to be used for
	 *  saving the final image (as specified by the user).
     */
	private int getSaveImageQualityPref() {
		if( MyDebug.LOG )
			Log.d(TAG, "getSaveImageQualityPref");
		String image_quality_s = sharedPreferences.getString(PreferenceKeys.QualityPreferenceKey, "90");
		int image_quality;
		try {
			image_quality = Integer.parseInt(image_quality_s);
		}
		catch(NumberFormatException exception) {
			if( MyDebug.LOG )
				Log.e(TAG, "image_quality_s invalid format: " + image_quality_s);
			image_quality = 90;
		}
		return image_quality;
	}
    
	@Override
	public boolean getFaceDetectionPref() {
		return sharedPreferences.getBoolean(PreferenceKeys.FaceDetectionPreferenceKey, false);
    }
    
	@Override
	public String getVideoQualityPref() {
		return sharedPreferences.getString(PreferenceKeys.getVideoQualityPreferenceKey(cameraId), "");
	}
	
    @Override
	public boolean getVideoStabilizationPref() {
		return sharedPreferences.getBoolean(PreferenceKeys.getVideoStabilizationPreferenceKey(), false);
    }
    
    @Override
	public boolean getForce4KPref() {
		if( cameraId == 0 && sharedPreferences.getBoolean(PreferenceKeys.getForceVideo4KPreferenceKey(), false) ) {
			return true;
		}
		return false;
    }
    
    @Override
    public String getVideoBitratePref() {
    	return sharedPreferences.getString(PreferenceKeys.getVideoBitratePreferenceKey(), "default");
    }

    @Override
    public String getVideoFPSPref() {
    	return sharedPreferences.getString(PreferenceKeys.getVideoFPSPreferenceKey(), "default");
    }
    
    @Override
    public long getVideoMaxDurationPref() {
		String video_max_duration_value = sharedPreferences.getString(PreferenceKeys.getVideoMaxDurationPreferenceKey(), "0");
		long video_max_duration;
		try {
			video_max_duration = (long)Integer.parseInt(video_max_duration_value) * 1000;
		}
        catch(NumberFormatException e) {
    		if( MyDebug.LOG )
    			Log.e(TAG, "failed to parse preference_video_max_duration value: " + video_max_duration_value);
    		e.printStackTrace();
    		video_max_duration = 0;
        }
		return video_max_duration;
    }

    @Override
    public int getVideoRestartTimesPref() {
		String restart_value = sharedPreferences.getString(PreferenceKeys.getVideoRestartPreferenceKey(), "0");
		int remaining_restart_video;
		try {
			remaining_restart_video = Integer.parseInt(restart_value);
		}
        catch(NumberFormatException e) {
    		if( MyDebug.LOG )
    			Log.e(TAG, "failed to parse preference_video_restart value: " + restart_value);
    		e.printStackTrace();
    		remaining_restart_video = 0;
        }
		return remaining_restart_video;
    }

	long getVideoMaxFileSizeUserPref() {
		if( MyDebug.LOG )
			Log.d(TAG, "getVideoMaxFileSizeUserPref");
		String video_max_filesize_value = sharedPreferences.getString(PreferenceKeys.getVideoMaxFileSizePreferenceKey(), "0");
		long video_max_filesize;
		try {
			video_max_filesize = Integer.parseInt(video_max_filesize_value);
		}
        catch(NumberFormatException e) {
    		if( MyDebug.LOG )
    			Log.e(TAG, "failed to parse preference_video_max_filesize value: " + video_max_filesize_value);
    		e.printStackTrace();
    		video_max_filesize = 0;
        }
		if( MyDebug.LOG )
			Log.d(TAG, "video_max_filesize: " + video_max_filesize);
		return video_max_filesize;
	}

	private boolean getVideoRestartMaxFileSizeUserPref() {
    	return sharedPreferences.getBoolean(PreferenceKeys.getVideoRestartMaxFileSizePreferenceKey(), true);
	}

    @Override
	public VideoMaxFileSize getVideoMaxFileSizePref() throws NoFreeStorageException {
		if( MyDebug.LOG )
			Log.d(TAG, "getVideoMaxFileSizePref");
		VideoMaxFileSize video_max_filesize = new VideoMaxFileSize();
		video_max_filesize.max_filesize = getVideoMaxFileSizeUserPref();
		video_max_filesize.auto_restart = getVideoRestartMaxFileSizeUserPref();
		
		/* Also if using internal memory without storage access framework, try to set the max filesize so we don't run out of space.
		   This is the only way to avoid the problem where videos become corrupt when run out of space - MediaRecorder doesn't stop on
		   its own, and no error is given!
		   If using SD card, it's not reliable to get the free storage (see https://sourceforge.net/p/opencamera/tickets/153/ ).
		   If using storage access framework, in theory we could check if this was on internal storage, but risk of getting it wrong...
		   so seems safest to leave (the main reason for using SAF is for SD cards, anyway).
		   */
		if( !storageUtils.isUsingSAF() ) {
    		String folder_name = storageUtils.getSaveLocation();
    		if( MyDebug.LOG )
    			Log.d(TAG, "saving to: " + folder_name);
    		boolean is_internal = false;
    		if( !folder_name.startsWith("/") ) {
    			is_internal = true;
    		}
    		else {
    			// if save folder path is a full path, see if it matches the "external" storage (which actually means "primary", which typically isn't an SD card these days)
    			File storage = Environment.getExternalStorageDirectory();
        		if( MyDebug.LOG )
        			Log.d(TAG, "compare to: " + storage.getAbsolutePath());
    			if( folder_name.startsWith( storage.getAbsolutePath() ) )
    				is_internal = true;
    		}
    		if( is_internal ) {
        		if( MyDebug.LOG )
        			Log.d(TAG, "using internal storage");
        		long free_memory = main_activity.freeMemory() * 1024 * 1024;
        		final long min_free_memory = 50000000; // how much free space to leave after video
        		// min_free_filesize is the minimum value to set for max file size:
        		//   - no point trying to create a really short video
        		//   - too short videos can end up being corrupted
        		//   - also with auto-restart, if this is too small we'll end up repeatedly restarting and creating shorter and shorter videos
        		final long min_free_filesize = 20000000;
        		long available_memory = free_memory - min_free_memory;
        		if( test_set_available_memory ) {
        			available_memory = test_available_memory;
        		}
        		if( MyDebug.LOG ) {
        			Log.d(TAG, "free_memory: " + free_memory);
        			Log.d(TAG, "available_memory: " + available_memory);
        		}
        		if( available_memory > min_free_filesize ) {
        			if( video_max_filesize.max_filesize == 0 || video_max_filesize.max_filesize > available_memory ) {
        				video_max_filesize.max_filesize = available_memory;
        				// still leave auto_restart set to true - because even if we set a max filesize for running out of storage, the video may still hit a maximum limit before hand, if there's a device max limit set (typically ~2GB)
        				if( MyDebug.LOG )
        					Log.d(TAG, "set video_max_filesize to avoid running out of space: " + video_max_filesize);
        			}
        		}
        		else {
    				if( MyDebug.LOG )
    					Log.e(TAG, "not enough free storage to record video");
        			throw new NoFreeStorageException();
        		}
    		}
		}
		
		return video_max_filesize;
	}

    @Override
    public boolean getVideoFlashPref() {
    	return sharedPreferences.getBoolean(PreferenceKeys.getVideoFlashPreferenceKey(), false);
    }
    
    @Override
    public boolean getVideoLowPowerCheckPref() {
    	return sharedPreferences.getBoolean(PreferenceKeys.getVideoLowPowerCheckPreferenceKey(), true);
    }
    
    @Override
	public String getPreviewSizePref() {
		return sharedPreferences.getString(PreferenceKeys.PreviewSizePreferenceKey, "preference_preview_size_wysiwyg");
    }
    
    @Override
    public String getPreviewRotationPref() {
    	return sharedPreferences.getString(PreferenceKeys.getRotatePreviewPreferenceKey(), "0");
    }
    
    @Override
    public String getLockOrientationPref() {
    	return sharedPreferences.getString(PreferenceKeys.getLockOrientationPreferenceKey(), "none");
    }

    @Override
    public boolean getTouchCapturePref() {
    	String value = sharedPreferences.getString(PreferenceKeys.TouchCapturePreferenceKey, "none");
    	return value.equals("single");
    }
    
    @Override
	public boolean getDoubleTapCapturePref() {
    	String value = sharedPreferences.getString(PreferenceKeys.TouchCapturePreferenceKey, "none");
    	return value.equals("double");
    }

    @Override
	public boolean getShowToastsPref() {
    	return sharedPreferences.getBoolean(PreferenceKeys.ShowToastsPreferenceKey, true);
    }
    
    @Override
    public boolean getShutterSoundPref() {
    	return sharedPreferences.getBoolean(PreferenceKeys.getShutterSoundPreferenceKey(), true);
    }

    @Override
	public boolean getStartupFocusPref() {
    	return sharedPreferences.getBoolean(PreferenceKeys.getStartupFocusPreferenceKey(), true);
    }

    @Override
	public boolean getRecordAudioPref() {
    	return sharedPreferences.getBoolean(PreferenceKeys.getRecordAudioPreferenceKey(), true);
    }
    
    @Override
    public String getRecordAudioChannelsPref() {
    	return sharedPreferences.getString(PreferenceKeys.getRecordAudioChannelsPreferenceKey(), "audio_default");
    }
    
    @Override
    public String getRecordAudioSourcePref() {
    	return sharedPreferences.getString(PreferenceKeys.getRecordAudioSourcePreferenceKey(), "audio_src_camcorder");
    }

    private String getStampDateFormatPref() {
    	return sharedPreferences.getString(PreferenceKeys.StampDateFormatPreferenceKey, "preference_stamp_dateformat_default");
    }
    
    private String getStampTimeFormatPref() {
    	return sharedPreferences.getString(PreferenceKeys.StampTimeFormatPreferenceKey, "preference_stamp_timeformat_default");
    }

	private String getVideoSubtitlePref() {
		return sharedPreferences.getString(PreferenceKeys.VideoSubtitlePref, "preference_video_subtitle_no");
	}

	@Override
	public double getCalibratedLevelAngle() {
		return sharedPreferences.getFloat(PreferenceKeys.CalibratedLevelAnglePreferenceKey, 0.0f);
	}
    
    @Override
	public float getFocusDistancePref() {
    	return focus_distance;
    }

    @Override
	public boolean useCamera2FakeFlash() {
		return sharedPreferences.getBoolean(PreferenceKeys.Camera2FakeFlashPreferenceKey, false);
	}

	@Override
	public void cameraSetup() {
		main_activity.cameraSetup();
	}

	@Override
	public void touchEvent(MotionEvent event) {
		if( main_activity.usingKitKatImmersiveMode() ) {
			main_activity.setImmersiveMode(false);
		}
	}
	
	@Override
	public void startingVideo() {
		ImageButton view = (ImageButton)main_activity.findViewById(R.id.take_photo);
		view.setImageResource(R.drawable.take_video_recording);
		view.setContentDescription( getContext().getResources().getString(R.string.stop_video) );
		view.setTag(R.drawable.take_video_recording); // for testing
	}

	@Override
	public void startedVideo() {
		if( MyDebug.LOG )
			Log.d(TAG, "startedVideo()");
		if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ) {
			if( !( main_activity.getMainUI().inImmersiveMode() && main_activity.usingKitKatImmersiveModeEverything() ) ) {
				View pauseVideoButton = main_activity.findViewById(R.id.pause_video);
				pauseVideoButton.setVisibility(View.VISIBLE);
			}
			main_activity.getMainUI().setPauseVideoContentDescription();
		}
		final int video_method = this.createOutputVideoMethod();
		boolean dategeo_subtitles = getVideoSubtitlePref().equals("preference_video_subtitle_yes");
		if( dategeo_subtitles && video_method != ApplicationInterface.VIDEOMETHOD_URI ) {
			final String preference_stamp_dateformat = this.getStampDateFormatPref();
			final String preference_stamp_timeformat = this.getStampTimeFormatPref();
			class SubtitleVideoTimerTask extends TimerTask {
				OutputStreamWriter writer;
				private int count = 1;

				private String getSubtitleFilename(String video_filename) {
					if( MyDebug.LOG )
						Log.d(TAG, "getSubtitleFilename");
					int indx = video_filename.indexOf('.');
					if( indx != -1 ) {
						video_filename = video_filename.substring(0, indx);
					}
					video_filename = video_filename + ".srt";
					if( MyDebug.LOG )
						Log.d(TAG, "return filename: " + video_filename);
					return video_filename;
				}

				public void run() {
					if( MyDebug.LOG )
						Log.d(TAG, "SubtitleVideoTimerTask run");
					long video_time = main_activity.getPreview().getVideoTime();
					if( !main_activity.getPreview().isVideoRecording() ) {
						if( MyDebug.LOG )
							Log.d(TAG, "no longer video recording");
						return;
					}
					if( main_activity.getPreview().isVideoRecordingPaused() ) {
						if( MyDebug.LOG )
							Log.d(TAG, "video recording is paused");
						return;
					}
					Date current_date = new Date();
					Calendar current_calendar = Calendar.getInstance();
					int offset_ms = current_calendar.get(Calendar.MILLISECOND);
					if( MyDebug.LOG ) {
						Log.d(TAG, "count: " + count);
						Log.d(TAG, "offset_ms: " + offset_ms);
						Log.d(TAG, "video_time: " + video_time);
					}
					String date_stamp = TextFormatter.getDateString(preference_stamp_dateformat, current_date);
					String time_stamp = TextFormatter.getTimeString(preference_stamp_timeformat, current_date);
					String datetime_stamp = "";
					if( date_stamp.length() > 0 )
						datetime_stamp += date_stamp;
					if( time_stamp.length() > 0 ) {
						if( datetime_stamp.length() > 0 )
							datetime_stamp += " ";
						datetime_stamp += time_stamp;
					}
					String subtitles = "";
					if( datetime_stamp.length() > 0 )
						subtitles += datetime_stamp + "\n";
					long video_time_from = video_time - offset_ms;
					long video_time_to = video_time_from + 999;
					if( video_time_from < 0 )
						video_time_from = 0;
					String subtitle_time_from = TextFormatter.formatTimeMS(video_time_from);
					String subtitle_time_to = TextFormatter.formatTimeMS(video_time_to);
					try {
						synchronized( this ) {
							if( writer == null ) {
								if( video_method == ApplicationInterface.VIDEOMETHOD_FILE ) {
									String subtitle_filename = last_video_file.getAbsolutePath();
									subtitle_filename = getSubtitleFilename(subtitle_filename);
									writer = new FileWriter(subtitle_filename);
								}
								else {
									if( MyDebug.LOG )
										Log.d(TAG, "last_video_file_saf: " + last_video_file_saf);
									File file = storageUtils.getFileFromDocumentUriSAF(last_video_file_saf, false);
									String subtitle_filename = file.getName();
									subtitle_filename = getSubtitleFilename(subtitle_filename);
									Uri subtitle_uri = storageUtils.createOutputFileSAF(subtitle_filename, ""); // don't set a mimetype, as we don't want it to append a new extension
									ParcelFileDescriptor pfd_saf = getContext().getContentResolver().openFileDescriptor(subtitle_uri, "w");
									writer = new FileWriter(pfd_saf.getFileDescriptor());
								}
							}
							if( writer != null ) {
								writer.append(Integer.toString(count));
								writer.append('\n');
								writer.append(subtitle_time_from);
								writer.append(" --> ");
								writer.append(subtitle_time_to);
								writer.append('\n');
								writer.append(subtitles); // subtitles should include the '\n' at the end
								writer.append('\n'); // additional newline to indicate end of this subtitle
								writer.flush();
								// n.b., we flush rather than closing/reopening the writer each time, as appending doesn't seem to work with storage access framework
							}
						}
						count++;
					}
					catch(IOException e) {
						if( MyDebug.LOG )
							Log.e(TAG, "SubtitleVideoTimerTask failed to create or write");
						e.printStackTrace();
					}
					if( MyDebug.LOG )
						Log.d(TAG, "SubtitleVideoTimerTask exit");
				}

				public boolean cancel() {
					if( MyDebug.LOG )
						Log.d(TAG, "SubtitleVideoTimerTask cancel");
					synchronized( this ) {
						if( writer != null ) {
							if( MyDebug.LOG )
								Log.d(TAG, "close writer");
							try {
								writer.close();
							}
							catch(IOException e) {
								e.printStackTrace();
							}
							writer = null;
						}
					}
					return super.cancel();
				}
			}
			subtitleVideoTimer.schedule(subtitleVideoTimerTask = new SubtitleVideoTimerTask(), 0, 1000);
		}
	}

	@Override
	public void stoppingVideo() {
		if( MyDebug.LOG )
			Log.d(TAG, "stoppingVideo()");
		ImageButton view = (ImageButton)main_activity.findViewById(R.id.take_photo);
		view.setImageResource(R.drawable.take_video_selector);
		view.setContentDescription( getContext().getResources().getString(R.string.start_video) );
		view.setTag(R.drawable.take_video_selector); // for testing
	}

	@Override
	public void stoppedVideo(final int video_method, final Uri uri, final String filename) {
		if( MyDebug.LOG ) {
			Log.d(TAG, "stoppedVideo");
			Log.d(TAG, "video_method " + video_method);
			Log.d(TAG, "uri " + uri);
			Log.d(TAG, "filename " + filename);
		}
		View pauseVideoButton = main_activity.findViewById(R.id.pause_video);
		pauseVideoButton.setVisibility(View.GONE);
		main_activity.getMainUI().setPauseVideoContentDescription(); // just to be safe
		if( subtitleVideoTimerTask != null ) {
			subtitleVideoTimerTask.cancel();
			subtitleVideoTimerTask = null;
		}

		boolean done = false;
		if( video_method == VIDEOMETHOD_FILE ) {
			if( filename != null ) {
				File file = new File(filename);
				storageUtils.broadcastFile(file, false, true, true);
				done = true;
			}
		}
		else {
			if( uri != null ) {
				// see note in onPictureTaken() for where we call broadcastFile for SAF photos
				File real_file = storageUtils.getFileFromDocumentUriSAF(uri, false);
				if( MyDebug.LOG )
					Log.d(TAG, "real_file: " + real_file);
				if( real_file != null ) {
					storageUtils.broadcastFile(real_file, false, true, true);
				}
				else {
					// announce the SAF Uri
					storageUtils.announceUri(uri, false, true);
				}
				done = true;
			}
		}
		if( MyDebug.LOG )
			Log.d(TAG, "done? " + done);

		String action = main_activity.getIntent().getAction();
		if( MediaStore.ACTION_VIDEO_CAPTURE.equals(action) ) {
			if( done && video_method == VIDEOMETHOD_FILE ) {
				// do nothing here - we end the activity from storageUtils.broadcastFile after the file has been scanned, as it seems caller apps seem to prefer the content:// Uri rather than one based on a File
			}
			else {
				if( MyDebug.LOG )
					Log.d(TAG, "from video capture intent");
				Intent output = null;
				if( done ) {
					// may need to pass back the Uri we saved to, if the calling application didn't specify a Uri
					// set note above for VIDEOMETHOD_FILE
					// n.b., currently this code is not used, as we always switch to VIDEOMETHOD_FILE if the calling application didn't specify a Uri, but I've left this here for possible future behaviour
					if( video_method == VIDEOMETHOD_SAF ) {
						output = new Intent();
						output.setData(uri);
						if( MyDebug.LOG )
							Log.d(TAG, "pass back output uri [saf]: " + output.getData());
					}
				}
				main_activity.setResult(done ? Activity.RESULT_OK : Activity.RESULT_CANCELED, output);
				main_activity.finish();
			}
		}
	}

	@Override
	public void onVideoInfo(int what, int extra) {
		// we don't show a toast for MEDIA_RECORDER_INFO_MAX_DURATION_REACHED - conflicts with "n repeats to go" toast from Preview
		if( what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED ) {
			if( MyDebug.LOG )
				Log.d(TAG, "max filesize reached");
			int message_id = R.string.video_max_filesize;
			// in versions 1.24 and 1.24, there was a bug where we had "info_" for onVideoError and "error_" for onVideoInfo!
			// fixed in 1.25; also was correct for 1.23 and earlier
			String debug_value = "info_" + what + "_" + extra;
			SharedPreferences.Editor editor = sharedPreferences.edit();
			editor.putString("last_video_error", debug_value);
			editor.apply();
		}
	}

	@Override
	public void onFailedStartPreview() {
	}

	@Override
	public void onCameraError() {
	}

	@Override
	public void onVideoError(int what, int extra) {
		if( MyDebug.LOG ) {
			Log.d(TAG, "onVideoError: " + what + " extra: " + extra);
		}
		int message_id = R.string.video_error_unknown;
		if( what == MediaRecorder.MEDIA_ERROR_SERVER_DIED  ) {
			if( MyDebug.LOG )
				Log.d(TAG, "error: server died");
			message_id = R.string.video_error_server_died;
		}
		// in versions 1.24 and 1.24, there was a bug where we had "info_" for onVideoError and "error_" for onVideoInfo!
		// fixed in 1.25; also was correct for 1.23 and earlier
		String debug_value = "error_" + what + "_" + extra;
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString("last_video_error", debug_value);
		editor.apply();
	}
	
	@Override
	public void onVideoRecordStartError(VideoProfile profile) {
		if( MyDebug.LOG )
			Log.d(TAG, "onVideoRecordStartError");
		String error_message;
		String features = main_activity.getPreview().getErrorFeatures(profile);
		if( features.length() > 0 ) {
			error_message = getContext().getResources().getString(R.string.sorry) + ", " + features + " " + getContext().getResources().getString(R.string.not_supported);
		}
		else {
			error_message = getContext().getResources().getString(R.string.failed_to_record_video);
		}
		ImageButton view = (ImageButton)main_activity.findViewById(R.id.take_photo);
		view.setImageResource(R.drawable.take_video_selector);
		view.setContentDescription( getContext().getResources().getString(R.string.start_video) );
		view.setTag(R.drawable.take_video_selector); // for testing
	}

	@Override
	public void onVideoRecordStopError(VideoProfile profile) {
		if( MyDebug.LOG )
			Log.d(TAG, "onVideoRecordStopError");
		//main_activity.getPreview().showToast(null, R.string.failed_to_record_video);
		String features = main_activity.getPreview().getErrorFeatures(profile);
		String error_message = getContext().getResources().getString(R.string.video_may_be_corrupted);
		if( features.length() > 0 ) {
			error_message += ", " + features + " " + getContext().getResources().getString(R.string.not_supported);
		}
	}
	
	@Override
	public void onFailedReconnectError() {
	}
	
	@Override
	public void onFailedCreateVideoFileError() {
		ImageButton view = (ImageButton)main_activity.findViewById(R.id.take_photo);
		view.setImageResource(R.drawable.take_video_selector);
		view.setContentDescription( getContext().getResources().getString(R.string.start_video) );
		view.setTag(R.drawable.take_video_selector); // for testing
	}

    @Override
    public void cameraInOperation(boolean in_operation, boolean is_video) {
		if( MyDebug.LOG )
			Log.d(TAG, "cameraInOperation: " + in_operation);
    	if( !in_operation && used_front_screen_flash ) {
    		main_activity.setBrightnessForCamera(false); // ensure screen brightness matches user preference, after using front screen flash
    		used_front_screen_flash = false;
    	}
    	main_activity.getMainUI().showGUI(!in_operation, is_video);
    }

//    private int n_capture_images = 0; // how many calls to onPictureTaken() since the last call to onCaptureStarted()

	@Override
	public void cameraClosed() {
		if( MyDebug.LOG )
			Log.d(TAG, "cameraClosed");
	}
	
	@Override
	public void setCameraIdPref(int cameraId) {
		this.cameraId = cameraId;
	}

    @Override
    public void setFlashPref(String flash_value) {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(PreferenceKeys.getFlashPreferenceKey(cameraId), flash_value);
		editor.apply();
    }

	@Override
	public void setVideoPref(boolean is_video) {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putBoolean(PreferenceKeys.IsVideoPreferenceKey, is_video);
		editor.apply();
    }

    @Override
    public void setSceneModePref(String scene_mode) {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(PreferenceKeys.SceneModePreferenceKey, scene_mode);
		editor.apply();
    }
    
    @Override
	public void clearSceneModePref() {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.remove(PreferenceKeys.SceneModePreferenceKey);
		editor.apply();
    }
	
    @Override
	public void setColorEffectPref(String color_effect) {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(PreferenceKeys.ColorEffectPreferenceKey, color_effect);
		editor.apply();
    }
	
    @Override
	public void clearColorEffectPref() {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.remove(PreferenceKeys.ColorEffectPreferenceKey);
		editor.apply();
    }
	
    @Override
	public void setWhiteBalancePref(String white_balance) {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(PreferenceKeys.WhiteBalancePreferenceKey, white_balance);
		editor.apply();
    }

    @Override
	public void clearWhiteBalancePref() {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.remove(PreferenceKeys.WhiteBalancePreferenceKey);
		editor.apply();
    }

	@Override
	public void setWhiteBalanceTemperaturePref(int white_balance_temperature) {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putInt(PreferenceKeys.WhiteBalanceTemperaturePreferenceKey, white_balance_temperature);
		editor.apply();
	}

	@Override
	public void setExposureCompensationPref(int exposure) {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(PreferenceKeys.ExposurePreferenceKey, "" + exposure);
		editor.apply();
    }

    @Override
	public void clearExposureCompensationPref() {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.remove(PreferenceKeys.ExposurePreferenceKey);
		editor.apply();
    }
	
    @Override
	public void setCameraResolutionPref(int width, int height) {
		String resolution_value = width + " " + height;
		if( MyDebug.LOG ) {
			Log.d(TAG, "save new resolution_value: " + resolution_value);
		}
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(PreferenceKeys.getResolutionPreferenceKey(cameraId), resolution_value);
		editor.apply();
    }
    
    @Override
    public void setVideoQualityPref(String video_quality) {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString(PreferenceKeys.getVideoQualityPreferenceKey(cameraId), video_quality);
		editor.apply();
    }
    
    @Override
	public void setZoomPref(int zoom) {
		if( MyDebug.LOG )
			Log.d(TAG, "setZoomPref: " + zoom);
    	this.zoom_factor = zoom;
    }
    
    @Override
	public void requestCameraPermission() {
		if( MyDebug.LOG )
			Log.d(TAG, "requestCameraPermission");
		main_activity.requestCameraPermission();
    }
    
    @Override
	public void requestStoragePermission() {
		if( MyDebug.LOG )
			Log.d(TAG, "requestStoragePermission");
		main_activity.requestStoragePermission();
    }
    
    @Override
	public void requestRecordAudioPermission() {
		if( MyDebug.LOG )
			Log.d(TAG, "requestRecordAudioPermission");
		main_activity.requestRecordAudioPermission();
    }
    
    @Override
	public void setExposureTimePref(long exposure_time) {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putLong(PreferenceKeys.ExposureTimePreferenceKey, exposure_time);
		editor.apply();
	}

    @Override
	public void setFocusDistancePref(float focus_distance) {
		this.focus_distance = focus_distance;
	}

    @Override
    public void onDrawPreview(Canvas canvas) {
    }

	public enum Alignment {
		ALIGNMENT_TOP,
		ALIGNMENT_CENTRE,
		ALIGNMENT_BOTTOM
	}
	
	public boolean test_set_available_memory = false;
	public long test_available_memory = 0;
}
