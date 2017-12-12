package com.placelook.camera;

import android.annotation.SuppressLint;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Created by victor on 05.11.17.
 */

public class CameraHelper {
    private static final String TAG = CameraHelper.class.getSimpleName();
    private String id;
    private CameraDevice device;
    private CameraManager manager;
    private TextureView txTexture;
    private ImageReader reader;
    private int width;
    private int height;
    private Handler handler;
    private CameraCaptureSession session;
    private Queue<Frame> queue;

    public CameraHelper(CameraManager manager, String idCamera) {
        this.id = idCamera;
        this.manager = manager;
        this.device = null;
        queue = new ArrayBlockingQueue<Frame>(30, true);
    }

    public boolean isOpened() {
        return (device != null);
    }

    @SuppressLint("MissingPermission")
    public void open(int width, int height) {
        this.width = width;
        this.height = height;
        try {
            manager.openCamera(id, cdc, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        if (device != null) {
            device.close();
            device = null;
            Log.i(TAG, "Camera closed");
        }
    }

    public void setTextureView(TextureView txTexture) {
        this.txTexture = txTexture;
    }

    public void createCameraPreviewSession() {

        HandlerThread handlerThread = new HandlerThread("Image Processing Thread");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());

        SurfaceTexture texture = txTexture.getSurfaceTexture();
        texture.setDefaultBufferSize(width, height);
        Surface surface = new Surface(texture);
        reader = ImageReader.newInstance(width, height, ImageFormat.NV21, 3);
        reader.setOnImageAvailableListener(iaListener, null);
        try {
            final CaptureRequest.Builder builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(surface);
            builder.addTarget(reader.getSurface());
            device.createCaptureSession(Arrays.asList(surface, reader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    CameraHelper.this.session = session;
                    try {
                        CameraHelper.this.session.setRepeatingRequest(builder.build(), null, null);

                    } catch (CameraAccessException ex) {
                        ex.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                }
            }, null);

        } catch (CameraAccessException ex) {
            ex.printStackTrace();
        }
    }

    public Frame pop() {
        return queue.poll();
    }

    private ImageReader.OnImageAvailableListener iaListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(final ImageReader reader) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    Image image = null;
                    try {
                        image = reader.acquireNextImage();
                        if (image != null) {
                            Image.Plane[] planes = image.getPlanes();
                            if (planes.length >= 3) {
                                ByteBuffer bufferY = planes[0].getBuffer();
                                ByteBuffer bufferU = planes[1].getBuffer();
                                ByteBuffer bufferV = planes[2].getBuffer();
                                int lengthY = bufferY.remaining();
                                int lengthU = bufferU.remaining();
                                int lengthV = bufferV.remaining();
                                byte[] dataYUV = new byte[lengthY + lengthU + lengthV];
                                bufferY.get(dataYUV, 0, lengthY);
                                bufferU.get(dataYUV, lengthY, lengthU);
                                bufferV.get(dataYUV, lengthY + lengthU, lengthV);
                            }

                        }

                    } catch (IllegalStateException ex) {
                        ex.printStackTrace();
                    } finally {
                        if (image != null) image.close();
                    }
                }
            });
        }
    };
    private CameraDevice.StateCallback cdc = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            device = camera;
            createCameraPreviewSession();
            Log.i(TAG, "Camera opened");
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            device.close();
            device = null;
            Log.i(TAG, "Camera disconnected");
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Log.i(TAG, "Error camera ID: " + camera.getId() + ", error: " + error);
        }
    };
}