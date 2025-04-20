package com.example.face_detection;

import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.widget.Toast;
import android.Manifest;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.face_detection.databinding.ActivityMainBinding;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

public class MainActivity extends AppCompatActivity {
    String TAG = "MAIN_ACTIVITY";
    private ActivityMainBinding binding;
    private ExecutorService cameraExecutor;
    private CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
    private boolean isCameraRunning = false;
    private FaceDetector faceDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        cameraExecutor = Executors.newSingleThreadExecutor();

        // FaceDetector 초기화
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST) // 빠른 성능을 위해 설정
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)       // 랜드마크 검출 안함
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)  // 얼굴 감지만
                .build();
        faceDetector = FaceDetection.getClient(options);  // FaceDetector 초기화

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA},
                    10
            );
        }

        binding.captureButton.setOnClickListener(v -> {
            if (isCameraRunning) {
                stopCamera();
            } else {
                startCamera();
            }
            isCameraRunning = !isCameraRunning;
        });

        // 카메라 전환 버튼 클릭 리스너
        binding.switchCameraButton.setOnClickListener(v -> {
            switchCamera(); // 카메라 전환
        });
    }

    private void stopCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                cameraProvider.unbindAll(); // 카메라 스트림 해제
                isCameraRunning = false; // 상태 갱신
                Log.d(TAG, "Camera stopped");
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder()
                        .build();
                preview.setSurfaceProvider(binding.previewView.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
                    Image mediaImage = imageProxy.getImage();
                    if (mediaImage != null) {
                        InputImage image = InputImage.fromMediaImage(
                                mediaImage, imageProxy.getImageInfo().getRotationDegrees());

                        faceDetector.process(image)
                                .addOnSuccessListener(faces -> {
                                    List<RectF> faceRects = new ArrayList<>();

                                    for (Face face : faces) {
                                        Rect boundingBox = face.getBoundingBox();

                                        // 좌표 변환
                                        RectF transformedRect = mapCoordinate(
                                                boundingBox,
                                                new Size(mediaImage.getWidth(), mediaImage.getHeight()), // 원본 이미지 크기
                                                new Size(binding.previewView.getWidth(), binding.previewView.getHeight()), // 프리뷰 화면 크기
                                                imageProxy.getImageInfo().getRotationDegrees()
                                        );
                                        Log.d(TAG, String.format("%s => %s", boundingBox.toString(), transformedRect.toString()));

                                        faceRects.add(transformedRect);
                                    }

                                    runOnUiThread(() -> {
                                        binding.faceOverlay.setFaceBoundingBoxes(faceRects);
                                    });
                                })
                                .addOnFailureListener(Throwable::printStackTrace)
                                .addOnCompleteListener(task -> imageProxy.close());
                    } else {
                        imageProxy.close();
                    }
                });

                cameraProvider.unbindAll(); // 기존 바인딩 해제

                // 카메라가 정상적으로 바인딩 되었는지 체크
                Camera camera = cameraProvider.bindToLifecycle(
                        this,
                        cameraSelector,
                        preview,
                        imageAnalysis // 분석기 추가
                );

                isCameraRunning = true; // 상태 갱신

            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void switchCamera() {
        // 현재 카메라가 후면이면 전면으로, 전면이면 후면으로 전환
        if (cameraSelector.equals(CameraSelector.DEFAULT_BACK_CAMERA)) {
            cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
        } else {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
        }

        // 카메라 재시작 (전환된 카메라로)
        startCamera();
    }

    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 10 && allPermissionsGranted()) {
            startCamera();
        } else {
            Toast.makeText(this, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    public RectF mapCoordinate(Rect imageRect, Size imageSize, Size previewSize, int rotationDegree) {
        Log.d(TAG, String.format("imageSize: %s, previewSize: %s, rotationDegree: %d", imageSize.toString(), previewSize.toString(), rotationDegree));

        // 화면 비율과 이미지 비율을 비교하여 적절한 스케일 팩터를 계산합니다.
        float viewAspectRatio = (float) previewSize.getWidth() / previewSize.getHeight();
        float imageAspectRatio = (float) imageSize.getWidth() / imageSize.getHeight();

        float scaleFactor;
        float postScaleWidthOffset = 0;
        float postScaleHeightOffset = 0;

        if (viewAspectRatio > imageAspectRatio) {
            scaleFactor = (float) previewSize.getWidth() / imageSize.getWidth();
            postScaleHeightOffset = ((float) previewSize.getWidth() / imageAspectRatio - previewSize.getHeight()) / 2;
        } else {
            scaleFactor = (float) previewSize.getHeight() / imageSize.getHeight();
            postScaleWidthOffset = ((float) previewSize.getHeight() * imageAspectRatio - previewSize.getWidth()) / 2;
        }
        Log.d(TAG, String.format("viewAspectRatio: %f, imageAspectRatio: %f", viewAspectRatio, imageAspectRatio));
        Log.d(TAG, String.format("postScaleWidthOffset: %f, postScaleHeightOffset: %f, factor: %f", postScaleWidthOffset, postScaleHeightOffset, scaleFactor));

        Matrix matrix = new Matrix();
        matrix.setScale(scaleFactor, scaleFactor);

        // 1:1 비율일 경우, offset, rotate 실행 순서와 무관하게 위치 맞음
        matrix.postTranslate(-postScaleHeightOffset, -postScaleWidthOffset);
        // 전면일 경우, 좌우 반전
        if (rotationDegree == 270) {
            matrix.postScale(-1, 1, previewSize.getWidth() / 2f, previewSize.getHeight() / 2f);
        }

        // RectF로 변환
        RectF faceRectF = new RectF(imageRect);
        matrix.mapRect(faceRectF);

        return faceRectF;
    }
}