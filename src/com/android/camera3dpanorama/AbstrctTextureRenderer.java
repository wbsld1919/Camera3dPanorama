package com.android.camera3dpanorama;

import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLUtils;
import android.util.Log;

public abstract class AbstrctTextureRenderer implements Runnable{


	private static final String TAG = "TextureSurfaceRenderer";
	
	private static final int EGL_OPENGL_ES2_BIT = 4;
	private static final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
	
	protected SurfaceTexture texture;
	
	protected int width;
    protected int height;
    private boolean running; 
	
    private EGLDisplay eglDisplay;
    private EGLContext eglContext;
    private EGLSurface displaySurface;
    
	/**
     * @param texture Surface texture on which to render. This has to be called AFTER the texture became available
     * @param width Width of the passed surface
     * @param height Height of the passed surface
     */
    public AbstrctTextureRenderer(SurfaceTexture texture, int width, int height)
    {
        this.texture = texture;
        this.width = width;
        this.height = height;
        this.running = true;
        Thread thrd = new Thread(this);
        thrd.start();
    }
	
	
	@Override
	public void run() {
		// TODO Auto-generated method stub
		initGL();
		initGLComponents();
		Log.d(TAG, "OpenGL init OK.");
		
		while (running) {
			 if (draw()) {
	            EGL14.eglSwapBuffers(eglDisplay, displaySurface);
			 }
		}
	     deinitGLComponents();
	     deinitGL();
	}
	
    /**
     * Call when activity pauses. This stops the rendering thread and deinitializes OpenGL.
     */
    public void onPause() {
        running = false;
    }
	
	private void initGL() {
		
		//创建一个EGLDisplay实例,获取显示设备的句柄
		eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
		
		//初始化EGLDisplay，获取EGL版本号
		int[] version = new int[2];
        EGL14.eglInitialize(eglDisplay, version, 0, version, 1);
        
        //选择Config，选择EGL配置参数，配置一个期望并尽可能接近一个有效的系统配置。
        EGLConfig eglConfig = chooseEglConfig();
        
        //创建OpenGL运行环境
        eglContext = createContext(eglDisplay, eglConfig);
        
        //创建缓冲数据的surface
        int[] surfaceAttribs = {
                EGL14.EGL_NONE
        };
        
        displaySurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, texture, surfaceAttribs, 0);
        
        if (displaySurface == null || displaySurface == EGL14.EGL_NO_SURFACE)
        {
            throw new RuntimeException("GL Error: " + GLUtils.getEGLErrorString(EGL14.eglGetError()));
        }
        
        //将OpenGL context与surface绑定,并设置OpenGL当前环境
        if (!EGL14.eglMakeCurrent(eglDisplay, displaySurface, displaySurface, eglContext))
        {
            throw new RuntimeException("GL Make current error: " + GLUtils.getEGLErrorString(EGL14.eglGetError()));
        }
        
	}
	
	private void deinitGL() {
		EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
		EGL14.eglDestroySurface(eglDisplay, displaySurface);
		EGL14.eglDestroyContext(eglDisplay, eglContext);
		EGL14.eglTerminate(eglDisplay);
		Log.d(TAG, "OpenGL deinit OK.");
	}
	
    /**
     * Main draw function, subclass this and add custom drawing code here. The rendering thread will attempt to limit
     * FPS to 60 to keep CPU usage low.
     */
    protected abstract boolean draw();
	
	/**
     * OpenGL component initialization funcion. This is called after OpenGL context has been initialized on the rendering thread.
     * Subclass this and initialize shaders / textures / other GL related components here.
     */
    protected abstract void initGLComponents();
    protected abstract void deinitGLComponents();
	
	private EGLContext createContext(EGLDisplay eglDisplay, EGLConfig eglConfig) {
        EGLContext context = null;
        int[] attribList = { EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE };
        try
        {
            context = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, attribList, 0);
        }
        catch(Exception e)
        {
            Log.e(TAG, "createContext error: " + e);
        }

        return context;
    }
	
	private EGLConfig chooseEglConfig() {
        int[] configsCount = new int[1];
        EGLConfig[] configs = new EGLConfig[1];
        int[] configSpec = getConfig();

        if (!EGL14.eglChooseConfig(eglDisplay, configSpec, 0, configs, 0, 1, configsCount, 0))
        {
            throw new IllegalArgumentException("Failed to choose config: " + GLUtils.getEGLErrorString(EGL14.eglGetError()));
        }
        else if (configsCount[0] > 0)
        {
            return configs[0];
        }

        return null;
    }
	
	private int[] getConfig() {
        return new int[] {
                EGL14.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_DEPTH_SIZE, 0,
                EGL14.EGL_STENCIL_SIZE, 0,
                EGL14.EGL_NONE
        };
    }
	
	protected abstract void onStartRecording();
	
	protected abstract void onStopRecording();
	
}
