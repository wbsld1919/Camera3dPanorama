package com.android.camera3dpanorama;

import java.io.File;
import java.io.IOException;
import java.util.List;

import android.Manifest;
import android.app.ActionBar;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;

public class RecordActivity extends Activity implements View.OnClickListener,RecordTextureRenderer.ShowImageForControlbar{

	private static final String TAG = "MainActivity";
	
	private String[] permissions = {"android.permission.CAMERA", "android.permission.READ_EXTERNAL_STORAGE","android.permission.WRITE_EXTERNAL_STORAGE"};
	private int mRequestCode = 321;
	private Camera mCamera;
	private Camera.Parameters mParams;
	private TextureView mRecordTextureView;
	private int mSurfaceWidth, mSurfaceHeight;
	private RecordTextureRenderer mRecordRenderer;
	private boolean mCameraPermission = true;
	private boolean mStartRcord = false;
	
	private Button mButton;
	private ImageView mShowImage;
	
	private final static int SCROLL_LEFT = 0;
	private final static int SCROLL_RIGHT = 1;
	 
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN); 
        setContentView(R.layout.activity_main);

        // to compatible android x (x < 23)
        if (ContextCompat.checkSelfPermission(this, permissions[0]) != PackageManager.PERMISSION_GRANTED
        		|| ContextCompat.checkSelfPermission(this, permissions[1]) != PackageManager.PERMISSION_GRANTED
        		|| ContextCompat.checkSelfPermission(this, permissions[2]) != PackageManager.PERMISSION_GRANTED) {
        	mCameraPermission = false;
        	ActivityCompat.requestPermissions(this, permissions, mRequestCode);
        }
        
        // this is the same as ContextCompat.checkSelfPermission(...)
/*		if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
			requestPermissions(new String[]{Manifest.permission.CAMERA}, 1);
			finish();
		}*/
		
        mRecordTextureView = (TextureView) findViewById(R.id.camera_surface);
        mRecordTextureView.setBackgroundColor(Color.argb(0, 0, 0, 0));
        mRecordTextureView.setOpaque(false);
        mRecordTextureView.setSurfaceTextureListener(mRecordSurfaceTextureListener);
        //mRecordTextureView.setRotation(45.0f);
        
        mButton = (Button) findViewById(R.id.video_button);
        mButton.setOnClickListener(this);
        
        mShowImage = (ImageView) findViewById(R.id.show_image);
        mShowImage.setOnTouchListener(new ControlProcessTouchListener());
    }
    
    /**
     * Whether the app is recording video now
     */
    private boolean mIsRecordingVideo = false;
	@Override
	public void onClick(View v) {
		// TODO Auto-generated method stub
		switch (v.getId()) {
        case R.id.video_button: {
            if (mIsRecordingVideo) {
            	mRecordRenderer.onStopRecording();
            	mButton.setText("开始录制");
            	mIsRecordingVideo = false;
            } else {
            	if (mShowImage.getVisibility() == View.VISIBLE)
            		mShowImage.setVisibility(View.INVISIBLE);
            	mRecordRenderer.onStartRecording();
            	mButton.setText("停止录制");
            	mIsRecordingVideo = true;
            }
            break;
        }
      }
	}
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    	
    	Log.d(TAG, "onRequestPermissionsResult");
    	if (requestCode == mRequestCode) {
    		if (grantResults.length == 3 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
    			mCameraPermission = true;
    			if (!mStartRcord)
    			    startRecording();
    		} else {
    			finish();
    		}
    	}
    }
    
    private void startRecording() {
    	mStartRcord = true;
    	mRecordRenderer = new RecordTextureRenderer(this, mRecordTextureView.getSurfaceTexture(), mSurfaceWidth, mSurfaceHeight);
    	mRecordRenderer.setControlForImageListener(this);
        while(mRecordRenderer.getCameraTexture() == null);
        try {
        	mCamera = Camera.open(1);
			mParams = mCamera.getParameters();
			//when DisplayOrientation is 90,RecordTextureRenderer.textureCoords1 is good,else textureCoords is good;
			//mCamera.setDisplayOrientation(90);
			mParams.setPreviewFrameRate(30);
			mParams.setPreviewFpsRange(30000,30000);
			List<String> focusModes = mParams.getSupportedFocusModes();
			if(focusModes.contains("continuous-video")){
				mParams.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
			}
			//mParams.setPreviewSize(1440, 1080);
			mCamera.setParameters(mParams);	
			mCamera.setPreviewTexture(mRecordRenderer.getCameraTexture());
			mCamera.startPreview();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
    
	@Override
	protected void onResume() {
		super.onResume();
		Log.d(TAG, "MainActivity onResume");
		if (mRecordTextureView.isAvailable()) {
			mSurfaceWidth = mRecordTextureView.getWidth();
			mSurfaceHeight = mRecordTextureView.getHeight();
			if (mCameraPermission && !mStartRcord) {
				startRecording();
			}
		}
	}
    
    @Override
    protected void onDestroy() {
    	super.onDestroy();
    	mStartRcord = false;
    	Log.d(TAG, "MainActivity onDestroy");
    }
    
    @Override
    protected void onPause() {
        // Temperate code to scan recorded video
        super.onPause();
        Log.d(TAG, "MainActivity onPause");
        mStartRcord = false;
        
        if(mRecordRenderer != null)
            mRecordRenderer.onPause();
        
        if(mCamera != null)
        {
            mCamera.stopPreview();
            mCamera.release();
        }
    }
    
    private final TextureView.SurfaceTextureListener mRecordSurfaceTextureListener = new TextureView.SurfaceTextureListener()
    {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int w, int h)
        {
            Log.v(TAG, "onSurfaceTextureAvailable");
            mSurfaceWidth = w;
            mSurfaceHeight = h;
            
            // this may be useful when previewsize is changed.
/*            RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) mRecordTextureView.getLayoutParams();

    		lp.setMargins(0, 144, 0, 336);
    		
    		mRecordTextureView.setLayoutParams(lp);*/
            
            if (mCameraPermission && !mStartRcord) {
            	startRecording();
            }
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

        }
    };

    private void updataImageView(int arg, String path , int speed) {
        if(arg==SCROLL_LEFT) {
            if(currentIndex < maxIndex) {
                if (currentIndex < 0) {
                	currentIndex = 0;
                	return;
                }
                if (currentIndex >= maxIndex){
                	currentIndex = maxIndex-1;
                	return;
                }
                String filePath = path + mImageResource[currentIndex];
                Bitmap bitmap = BitmapFactory.decodeFile(filePath);
                
                Matrix m = new Matrix();
                m.postScale(1, -1);//镜像垂直翻转
                
                Bitmap b = bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);
                mShowImage.setImageBitmap(b);
                currentIndex = currentIndex + speed;
            }
        } else if(arg == SCROLL_RIGHT) {
            if(currentIndex >= 0) {
                currentIndex = currentIndex - speed;
                if (currentIndex < 0) {
                	currentIndex = 0;
                	return;
                }
                if (currentIndex >= maxIndex){
                	currentIndex = maxIndex-1;
                	return;
                }
                String filePath = path + mImageResource[currentIndex];
                Bitmap bitmap = BitmapFactory.decodeFile(filePath);
                
                Matrix m = new Matrix();
                m.postScale(1, -1);//镜像垂直翻转

                Bitmap b = bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);
                mShowImage.setImageBitmap(b);

            }
        }
    }
    
    private VelocityTracker vTracker = null;    
    class ControlProcessTouchListener implements View.OnTouchListener{
        float newX,newY;
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            int action = event.getAction();
            float oldX = event.getX();
            float oldY = event.getY();
            Log.d(TAG,"onTouch!!!");
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    Log.d(TAG,"action down!!!");
                    
                    if(vTracker == null){    
                        vTracker = VelocityTracker.obtain();    
                    }else{    
                        vTracker.clear();    
                    }    
                    //vTracker.addMovement(event);
                    
                    break;
                case MotionEvent.ACTION_MOVE:
                    float offX = oldX-newX;
                    float offY = oldY-newY;
                    
                    vTracker.addMovement(event);    
                    vTracker.computeCurrentVelocity(1000);  
                    Log.d("liudan", "vTracker x speed = " +  vTracker.getXVelocity());
                    float mXVelocity = vTracker.getXVelocity();
                    int temp = Math.abs((int)mXVelocity / 100);
                    int speed = 0;
                    if (temp > 0 && temp <=1) {
                    	speed = 1;
                    }else if (temp > 1 && temp <= 2) {
                    	speed = 1;
                    }else if (temp > 2 && temp <= 4) {
                    	speed = 2;
                    }else if (temp > 4){
                    	speed = 3;
                    }
                    
                    if (speed == 0)
                    	break;
                    
                   // vTracker < 0, scroll right
                    if(offX > 0){
                        updataImageView(SCROLL_RIGHT, mPath, speed);
                    } else {
                    	//vTracker > 0, scroll left
                        updataImageView(SCROLL_LEFT, mPath, speed);
                    }
                    Log.d(TAG,"offx and offy "+offX+" "+offY);
                    break;
                case MotionEvent.ACTION_UP:
                    Log.d(TAG,"action up!!!");
                    break;
                case MotionEvent.ACTION_CANCEL:
                    try {
                    	// if velocityTracker won't be used should be recycled
                    	vTracker.recycle();
                    }catch(Exception e){
                        e.printStackTrace();
                    }
                    break;
            }
            newX = oldX;
            newY = oldY;
            return true;
        }
    }

    private String[] mImageResource;
    private String mPath;
	@Override
	public void setImageResource(String[] imageResource, String path) {
		// TODO Auto-generated method stub
		mImageResource = imageResource;
		mPath = path;
		initImageView(mImageResource, path);
	}
    
	private int maxIndex = 0;
	private int currentIndex = 0;
	private void initImageView(String [] imageResource,String path) {

        String filePath = path + imageResource[0];
        currentIndex = 0;
        maxIndex = imageResource.length;
        Bitmap bitmap = BitmapFactory.decodeFile(filePath);
        Matrix m = new Matrix();
        m.postScale(1, -1);//镜像垂直翻转

        Bitmap b = bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(), m, true);
        mShowImage.setImageBitmap(b);
        mShowImage.setVisibility(View.VISIBLE);
    }
}
