package com.android.camera3dpanorama;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.os.Environment;
import android.os.SystemClock;
import android.util.Log;

public class RecordTextureRenderer extends AbstrctTextureRenderer{

	private static final String TAG = "RecordTextureRenderer";

	private static final String vertexShaderCode =
                    "attribute vec4 vPosition;" +
                    "attribute vec4 vTexCoordinate;" +
                    "uniform mat4 textureTransform;" +
                    "varying vec2 v_TexCoordinate;" +
                    "void main() {" +
                    "   v_TexCoordinate = (textureTransform * vTexCoordinate).xy;" +
                    "   gl_Position = vPosition;" +
                    "}";
	
    private static final String fragmentShaderCodeforCamera =
            "#extension GL_OES_EGL_image_external : require\n" +
                    "precision mediump float;" +
                    "uniform samplerExternalOES texture;" +
                    "varying vec2 v_TexCoordinate;" +
                    "void main () {" +
                    "    vec4 color = texture2D(texture, v_TexCoordinate);" +
                    "        gl_FragColor = color;" +
                    "}";
	
	
	private static float squareSize = 1.0f;
    private static float squareCoords[] = { 
    	    -squareSize,  squareSize, 0.0f,   // top left
            -squareSize, -squareSize, 0.0f,   // bottom left
             squareSize, -squareSize, 0.0f,   // bottom right
             squareSize,  squareSize, 0.0f }; // top right
	
    private float textureCoords1[] = { 
    		0.0f, 1.0f, 0.0f, 1.0f,
            0.0f, 0.0f, 0.0f, 1.0f,
            1.0f, 0.0f, 0.0f, 1.0f,
            1.0f, 1.0f, 0.0f, 1.0f };
    
    private float textureCoords[] = { 
    		0.0f, 0.0f, 0.0f, 1.0f,
            1.0f, 0.0f, 0.0f, 1.0f,
            1.0f, 1.0f, 0.0f, 1.0f,
            0.0f, 1.0f, 0.0f, 1.0f };
    
    private static short drawOrder[] = { 0, 1, 2, 0, 2, 3};
    
    private int[] textures = new int[1];
    
    private FloatBuffer vertexBuffer;
    private FloatBuffer textureBuffer;
    private ShortBuffer drawListBuffer;
    
    private SurfaceTexture cameraTexture;
    private float[] textureTransform;
    private boolean cameraFrameAvailable = false;
    
    private int vertexShaderHandle;
    private int fragmentCameraShaderHandle;
    private int shaderProgramForCamera;
    
    private Context mContext;
    private boolean adjustViewport = false;
    private boolean dontAdjust = false; // tmp
    
    private boolean isRecording = false;
    
	public RecordTextureRenderer(Context context, SurfaceTexture texture, int width, int height) {
		super(texture, width, height);
		this.mContext = context;
		textureTransform = new float[16];
	}

	@Override
	protected void initGLComponents() {
		setupVertexBuffer();
		setupTexture(mContext);
		loadShaders();
	}
	 
	private void setupVertexBuffer() {
		// Draw list buffer
		ByteBuffer dlb1 = ByteBuffer.allocateDirect(drawOrder.length * 2);
		dlb1.order(ByteOrder.nativeOrder());
		drawListBuffer = dlb1.asShortBuffer();
		drawListBuffer.put(drawOrder);
		drawListBuffer.position(0);

		// Initialize the texture holder
//		ByteBuffer bb = ByteBuffer.allocateDirect(squareCoords.length * 4);
//		bb.order(ByteOrder.nativeOrder());

		ByteBuffer aa = ByteBuffer.allocateDirect(squareCoords.length * 4);
		aa.order(ByteOrder.nativeOrder());

		vertexBuffer = aa.asFloatBuffer();
		vertexBuffer.put(squareCoords);
		vertexBuffer.position(0);
	}

	private void setupTexture(Context context) {

		ByteBuffer texturebf = ByteBuffer.allocateDirect(textureCoords.length * 4);
		texturebf.order(ByteOrder.nativeOrder());

		textureBuffer = texturebf.asFloatBuffer();
		textureBuffer.put(textureCoords);
		textureBuffer.position(0);

		// Generate the actual texture
		//GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
		GLES20.glGenTextures(1, textures, 0);
		//checkGlError("Texture generate");

		//GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0]);
		checkGlError("Texture bind");

		cameraTexture = new SurfaceTexture(textures[0]);
		cameraTexture.setOnFrameAvailableListener(mCameraonFrameAvailable);
	}
	 
	 private void loadShaders() {
		 
		 int[] statuscamera = new int[1];
		 
		 vertexShaderHandle = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
	     GLES20.glShaderSource(vertexShaderHandle, vertexShaderCode);
	     GLES20.glCompileShader(vertexShaderHandle);
	     checkGlError("Vertex shader compile");
	     
	     fragmentCameraShaderHandle = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
	     GLES20.glShaderSource(fragmentCameraShaderHandle, fragmentShaderCodeforCamera);
	     GLES20.glCompileShader(fragmentCameraShaderHandle);
	     checkGlError("Pixel shader compile");
	     
	     shaderProgramForCamera = GLES20.glCreateProgram();
	     GLES20.glAttachShader(shaderProgramForCamera, vertexShaderHandle);
	     GLES20.glAttachShader(shaderProgramForCamera, fragmentCameraShaderHandle);
	     GLES20.glLinkProgram(shaderProgramForCamera);
	     checkGlError("Shader program compile");
	     
	     GLES20.glGetProgramiv(shaderProgramForCamera, GLES20.GL_LINK_STATUS, statuscamera, 0);
	     if (statuscamera[0] != GLES20.GL_TRUE) {
	         String error = GLES20.glGetProgramInfoLog(shaderProgramForCamera);
	         Log.e(TAG, "Error while linking program for camera:\n" + error);
	     }

	     // enable alpha blending
	     GLES20.glEnable(GLES20.GL_BLEND);
	     GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
		 
	 }
	 
	 private ExecutorService cachedThreadPool = Executors.newFixedThreadPool(20);
	 
	@Override
	protected boolean draw() {
		synchronized (this) {
			if (!cameraFrameAvailable) {
				return false;
			}
		}
		
		Log.d(TAG, "---A Frame is drawing---");
		
		GLES20.glViewport(0, 0, width, height);

		GLES20.glClearColor(0, 0, 0, 0);
		GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

		synchronized (this) {
			//this called and the next onFrameAvailable will be called again.
			cameraFrameAvailable = false;
			cameraTexture.updateTexImage();
		}
		
		cameraTexture.getTransformMatrix(textureTransform);

		GLES20.glUseProgram(shaderProgramForCamera);

		int textureParamHandleCamera = GLES20.glGetUniformLocation(shaderProgramForCamera, "texture");
		int textureCoordinateHandleCamera = GLES20.glGetAttribLocation(shaderProgramForCamera, "vTexCoordinate");
		int positionHandleCamera = GLES20.glGetAttribLocation(shaderProgramForCamera, "vPosition");
		int textureTranformHandleCamera = GLES20.glGetUniformLocation(shaderProgramForCamera, "textureTransform");

		GLES20.glEnableVertexAttribArray(positionHandleCamera);
		GLES20.glVertexAttribPointer(positionHandleCamera, 3, GLES20.GL_FLOAT, false, 4 * 3, vertexBuffer);

		//GLES20.glBindTexture(GLES20.GL_TEXTURE0, textures[0]);
		//GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
		//GLES20.glUniform1i(textureParamHandleCamera, 0);//不要这一句貌似也可以

		GLES20.glEnableVertexAttribArray(textureCoordinateHandleCamera);
		GLES20.glVertexAttribPointer(textureCoordinateHandleCamera, 4, GLES20.GL_FLOAT, false, 0, textureBuffer);

		GLES20.glUniformMatrix4fv(textureTranformHandleCamera, 1, false, textureTransform, 0);

		GLES20.glDrawElements(GLES20.GL_TRIANGLES, drawOrder.length, GLES20.GL_UNSIGNED_SHORT, drawListBuffer);
		
		if (saveover && count1 % 3 == 0 && isRecording) { 
			Log.d(TAG, "sendImage start to save");
			saveover = false;
			sendImage(width, height);
		}
		count1++;
		GLES20.glDisableVertexAttribArray(positionHandleCamera);
		GLES20.glDisableVertexAttribArray(textureCoordinateHandleCamera);

		return true;
	 }
	
	private boolean mPixelsReadOver = true;
	private boolean saveover = true;
	private int count1 = 0;
	private int count = 0;
	private String mImagePath;
	private String mFileName;
	private void sendImage(final int width, final int height) {
	    final ByteBuffer rgbaBuf = ByteBuffer.allocateDirect(width * height * 9/4);
	    rgbaBuf.position(0);
	    GLES20.glReadPixels(width/8, height/8, width*3/4, height*3/4, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, rgbaBuf);
	    mImagePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/gl_dump/";
	    File file = new File(mImagePath + mFileName);
	    if(!file.exists()) {
            file.mkdirs();
        }
		cachedThreadPool.execute(new Runnable() {
		@Override
		public void run() {
			count++;
        	saveRgb2Bitmap(rgbaBuf, mImagePath + mFileName + "/" + count + "_" + height + "x" + width + ".3dp", width*3/4, height*3/4);
		}
	});
	    saveover = true;
	}

	private void saveRgb2Bitmap(Buffer buf, String filename, int width, int height) {
	    Log.d("TryOpenGL", "Creating " + filename);
	    BufferedOutputStream bos = null;
	    try {
	        bos = new BufferedOutputStream(new FileOutputStream(filename));
	        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
	        bmp.copyPixelsFromBuffer(buf);
	        bmp.compress(Bitmap.CompressFormat.JPEG, 70, bos);
	        bmp.recycle();
	        Log.d("TryOpenGL", "Creating end" + filename);
	    } catch (IOException e) {
	        e.printStackTrace();
	    } finally {
	        if (bos != null) {
	            try {
	                bos.close();
	            } catch (IOException e) {
	                e.printStackTrace();
	            }
	        }
	    }
	}

	private int maxIndex;
	private String [] imageResource;
    private void loadImageFromSdcard(String path) {
        Log.d(TAG,"load from sdcard path ="+path);
        File file = new File(path);
        if(file.exists()) {
            maxIndex = file.list().length;
            imageResource = new String[file.list().length];
            imageResource = file.list();
            Arrays.sort(imageResource, new Comparator<String>() {
                @Override
                public int compare(String lhs, String rhs) {
                    int l = Integer.parseInt(lhs.split("_")[0]);
                    int r = Integer.parseInt(rhs.split("_")[0]);
                    if(l>r)
                        return 1;
                    else if(l<r)
                        return -1;
                    else
                        return 0;
                }
            });
            Log.d(TAG,"imageResource count="+file.list().length);
        } else {
            Log.d(TAG,"file not exists");
        }

    }
	
	protected void onStartRecording() {
		isRecording = true;
		mFileName = "IMG" + "_" + System.currentTimeMillis();
	}
	
	protected void onStopRecording() {
		isRecording = false;
		loadImageFromSdcard(mImagePath + mFileName);
		mListener.setImageResource(imageResource, mImagePath + mFileName +"/");
	}
	
	 @Override
	 protected void deinitGLComponents() {
	     Log.d(TAG, "deinitGLComponents");
	     GLES20.glDeleteTextures(1, textures, 0);
	     GLES20.glDeleteProgram(shaderProgramForCamera);
	     cameraTexture.release();
	     cameraTexture.setOnFrameAvailableListener(null);
	 }
	 
	public SurfaceTexture getCameraTexture() {
		return cameraTexture;
	}

	private final SurfaceTexture.OnFrameAvailableListener mCameraonFrameAvailable = new SurfaceTexture.OnFrameAvailableListener() {
		@Override
		public void onFrameAvailable(SurfaceTexture surfaceTexture) {
			// Log.d(TAG, "mCameraonFrameAvailable");
			synchronized (this) {
				Log.d(TAG, "--- A Frame is Available---");
				cameraFrameAvailable = true;
			}
		}
	};

	public void checkGlError(String op) {
		int error;
		while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
			Log.e(TAG, op + ": glError " + GLUtils.getEGLErrorString(error));
		}
	}
	 
	private ShowImageForControlbar mListener;
	
    public void setControlForImageListener(ShowImageForControlbar listener) {
    	mListener = listener;
    }
    
	public interface ShowImageForControlbar {
		public void setImageResource(String [] imageResource, String path);
	}
}
