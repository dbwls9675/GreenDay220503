package com.example.ex_07_plane;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;

import java.util.Collection;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    TextView myTextView;
    GLSurfaceView mSurfaceView;
    MainRenderer mRenderer;

    Session mSession;
    Config mConfig;

    boolean mUserRequestedInstall = true, mTouched = false;
    float mCurrentX, mCurrentY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        hideStatusBarANdTitleBar();
        setContentView(R.layout.activity_main);

        mSurfaceView = (GLSurfaceView) findViewById(R.id.gl_surface_view);
        myTextView = (TextView) findViewById(R.id.myTextView);

        DisplayManager displayManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);

        if (displayManager != null) {
            displayManager.registerDisplayListener(new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int i) {
                }

                @Override
                public void onDisplayRemoved(int i) {
                }

                @Override
                public void onDisplayChanged(int i) {
                    synchronized (this) {
                        mRenderer.mViewportChanged = true;
                    }
                }
            }, null);
        }
        mRenderer = new MainRenderer(this, new MainRenderer.RenderCallback() {
            @Override
            public void preRender() {
                if (mRenderer.mViewportChanged) {
                    Display display = getWindowManager().getDefaultDisplay();
                    int displayRotation = display.getRotation();
                    mRenderer.updateSession(mSession, displayRotation);
                }
                mSession.setCameraTextureName(mRenderer.getTextuserId());
                Frame frame = null;
                try {
                    frame = mSession.update();
                } catch (CameraNotAvailableException e) {
                    e.printStackTrace();
                }
                if (frame.hasDisplayGeometryChanged()) {
                    mRenderer.mCamera.transformDisplayGeometry(frame);
                }

                PointCloud pointCloud = frame.acquirePointCloud();
                mRenderer.mPointCloud.update(pointCloud);
                pointCloud.release();

                //?????????????????? ????????? ????????? ~ ?????????.
                if (mTouched) {
                    List<HitResult> results = frame.hitTest(mCurrentX, mCurrentY);
                    for (HitResult result : results) {
                        Pose pose = result.getHitPose(); //????????????????????? ??????
                        float[] modelMatrix = new float[16];
                        pose.toMatrix(modelMatrix, 0);//????????? ????????? matrix ??????

                        //??????????????? ????????? ?????? ????????? ????????????.(Plane ??? ?????? ??????????)
                        Trackable trackable = result.getTrackable();

                        //????????? ?????? ????????? Plane ???????
                        if (trackable instanceof Plane &&
                                //Plane ?????????(???)?????? ????????? ??????????
                                ((Plane) trackable).isPoseInPolygon(pose)
                        ) {
                            //????????? modelMatrix??? ????????? ???????????? modelMatrix??? ??????
                            //mRenderer.mCube.setModelMatrix(modelMatrix);
                            mRenderer.mObj.setModelMatrix(modelMatrix);
                        }
                    }
                    mTouched = false;
                }

                // ?????? ?????? ????????? ?????????
                // Session???????????? ???????????? ???????????? ???????????? ??? ????????? ?????? ??? ??????.
                //                                plane     point
                Collection<Plane> planes = mSession.getAllTrackables(Plane.class);
                //ARCore ?????? Plane?????? ?????????.

                boolean isPlaneDetected = false;

                for (Plane plane : planes) {
                    //plane??? ???????????????
                    if (plane.getTrackingState() == TrackingState.TRACKING &&
                            plane.getSubsumedBy() == null) {
                        isPlaneDetected = true;
                        //??????????????? plane ????????? ???????????? ??????
                        mRenderer.mPlane.update(plane);
                    }
                }

                if (isPlaneDetected) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            myTextView.setText("????????? ????????????.");
                        }
                    });
                } else {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            myTextView.setText("????????? ????????? ???????");
                        }
                    });
                }

                Camera camera = frame.getCamera();
                float[] proMatrix = new float[16];
                camera.getProjectionMatrix(proMatrix, 0, 0.1f, 100f);
                float[] viewMatrix = new float[16];
                camera.getViewMatrix(viewMatrix, 0);

                mRenderer.setProjectionMatrix(proMatrix);
                mRenderer.updateViewMatrix(viewMatrix);
            }
        });

        mSurfaceView.setPreserveEGLContextOnPause(true);
        mSurfaceView.setEGLContextClientVersion(2);
        mSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        mSurfaceView.setRenderer(mRenderer);
    }

    @Override
    protected void onResume() {
        super.onResume();
        requestCameraPermission();
        try {
            if (mSession == null) {
                switch (ArCoreApk.getInstance().requestInstall(this, mUserRequestedInstall)) {
                    case INSTALLED:
                        mSession = new Session(this);
                        Log.d("??????", "ARCore session ??????");
                        break;
                    case INSTALL_REQUESTED:
                        Log.d("??????", "ARCore ????????? ?????????");
                        mUserRequestedInstall = false;
                        break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        mConfig = new Config(mSession);
        mSession.configure(mConfig);

        try {
            mSession.resume();
        } catch (CameraNotAvailableException e) {
            e.printStackTrace();
        }

        mSurfaceView.onResume();
        mSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    }

    @Override
    protected void onPause() {
        super.onPause();

        mSurfaceView.onPause();
        mSession.pause();
    }

    void hideStatusBarANdTitleBar() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
    }

    void requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA},
                    0
            );
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            mTouched = true;
            mCurrentX = event.getX();
            mCurrentY = event.getY();
        }
        return true;
    }

}



























