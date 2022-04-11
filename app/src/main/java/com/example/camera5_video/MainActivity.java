package com.example.camera5_video;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;

import java.io.File;
import java.sql.Array;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

public class MainActivity extends AppCompatActivity {
    public static final String LOG_TAG = "myLogs";

    CameraService myCameras = null;
    private CameraManager mCameraManager = null;
    private Button mButtonOpenCamera1 = null;
    private TextureView mImageView = null;
    private boolean isStartRecording = false;

    private File mCurrentFile;
    private MediaRecorder mMediaRecorder = null;
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler = null;

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    //Слушатель, создался экран или нет, нужен для автоматического вывода изображения на экран при включении
    private TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            myCameras.openCamera();//когда экран создался, выводим на него изображение с камеры
        }
        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {        }
        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {   return false;        }
        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {        }
    };

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);// убираем заголовок
        setContentView(R.layout.activity_main);
        View myUserRecord = findViewById(R.id.userRecord); // находим иконку отвечающую за Принудительную запись
        // Запрашиваем разрешение на использования камеры и папок
        // БЕЗ ЭТОГО НЕ ЗАРАБОТАЕТ
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                ||
                (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
        ) {
            requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }
        mImageView = findViewById(R.id.textureView);   //находим экран
        mButtonOpenCamera1 = findViewById(R.id.button1);   //находим кнопку
        mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        myCameras = new CameraService(mCameraManager);
        mButtonOpenCamera1.setOnClickListener(new View.OnClickListener() { // слушатель нажатия на кнопку
            @Override
            public void onClick(View v) {//одна кнопка на включение и выключение
                myCameras.surfaceList.clear();
                if (!isStartRecording) {
                    isStartRecording = true;// сообщаем что камера включена
                    myUserRecord.setVisibility(View.VISIBLE);// делаем значок принудительной записи на панели видимым
                    setUpMediaRecorder();
                    myCameras.startCameraPreviewSession();
                    mMediaRecorder.start();
                } else if (isStartRecording) {
                    isStartRecording = false;// сообщаем что камера выключена
                    myUserRecord.setVisibility(View.INVISIBLE); // делаем значок принудительной записи на панели не видимым
                    myCameras.stopRecordingVideo();
                    myCameras.startCameraPreviewSession();
                }
            }
        });
        Log.i(LOG_TAG, " mSurfaceTextureListener 2");
        mImageView.setSurfaceTextureListener(mSurfaceTextureListener); // опрос создался ли экран
    }

    private void setUpMediaRecorder() {
        mMediaRecorder = new MediaRecorder();
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mCurrentFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), fileName());
        mMediaRecorder.setOutputFile(mCurrentFile.getAbsolutePath());
        CamcorderProfile profile = CamcorderProfile.get(CamcorderProfile.QUALITY_480P);
        mMediaRecorder.setVideoFrameRate(profile.videoFrameRate);
        mMediaRecorder.setVideoSize(profile.videoFrameWidth, profile.videoFrameHeight);
        mMediaRecorder.setVideoEncodingBitRate(profile.videoBitRate);
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        try {
            mMediaRecorder.prepare();
            Log.i(LOG_TAG, " запустили медиа рекордер");
        } catch (Exception e) {
            Log.i(LOG_TAG, "не запустили медиа рекордер");
        }
    }

    private String fileName() { // название файла в виде дата,месяц,год_час,минута,секунда
        Date dateNow = new Date();//("yyyy.MM.dd 'и время' hh:mm:ss a zzz");
        SimpleDateFormat formatForDateNow = new SimpleDateFormat("dd.MM.yyyy_hh:mm:ss");
        String date = "" + formatForDateNow.format(dateNow) + ".mp4";
        return date;
    }

    public class CameraService {
        private String mCameraID = "0"; // выбираем какую камеру использовать 0 - задняя, 1 - фронтальная
        private CameraDevice mCameraDevice = null;
        private CaptureRequest.Builder mPreviewBuilder;
        private CameraCaptureSession mSession;
        private List<Surface> surfaceList = new ArrayList<Surface>();
        public CameraService(CameraManager cameraManager) {
            mCameraManager = cameraManager;
        }

        private CameraDevice.StateCallback mCameraCallback = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(CameraDevice camera) {
                mCameraDevice = camera;
                startCameraPreviewSession();
            }
            @Override
            public void onDisconnected(CameraDevice camera) {            }
            @Override
            public void onError(CameraDevice camera, int error) {
                Log.i(LOG_TAG, "error! CameraService");
            }
        };

        private void startCameraPreviewSession() {  // вывод изображения на экран во время записи
            SurfaceTexture texture = mImageView.getSurfaceTexture();
            texture.setDefaultBufferSize(1920, 1080);
            Surface surface = new Surface(texture);
            try {
                mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                mPreviewBuilder.addTarget(surface);
                surfaceList.add(0,surface);
                try {
                    mPreviewBuilder.addTarget(mMediaRecorder.getSurface());
                    surfaceList.add(1,mMediaRecorder.getSurface());
                }catch (Exception e){
                    Log.i(LOG_TAG, "NullPointerException ");
                }
                Log.i(LOG_TAG, "surfaceList = " + surfaceList);
                mCameraDevice.createCaptureSession(surfaceList,
                        new CameraCaptureSession.StateCallback() {
                            @Override
                            public void onConfigured(CameraCaptureSession session) {
                                mSession = session;
                                try {
                                    mSession.setRepeatingRequest(mPreviewBuilder.build(), null, mBackgroundHandler);
                                } catch (CameraAccessException e) {
                                    e.printStackTrace();
                                }
                            }
                            @Override
                            public void onConfigureFailed(CameraCaptureSession session) {
                            }
                        }, mBackgroundHandler);

            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
        @SuppressLint("NewApi")
        public void openCamera() {//проверяем, получено ли разрешение на использование камеры
            try {
                if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    mCameraManager.openCamera(mCameraID, mCameraCallback, null);
                }
            } catch (CameraAccessException e) {
            }
        }
        public void stopRecordingVideo() {
            try {
                mSession.stopRepeating();
                mSession.abortCaptures();
                mSession.close();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            mMediaRecorder.stop();
            mMediaRecorder = null;
            mBackgroundHandler = null;
            //mMediaRecorder.release();
        }
    }
    @Override
    public void onPause() {
        stopBackgroundThread();
        super.onPause();
    }
    @Override
    public void onResume() {
        super.onResume();
        startBackgroundThread();
    }
}