package com.lichao.bankcardrec;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{
    private String TAG = "OPenCV-Android";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button button = (Button) this.findViewById(R.id.process_btn);
        button.setOnClickListener(this);
        iniLoadOpenCV();
    }

    private void iniLoadOpenCV() {
        boolean success = OpenCVLoader.initDebug();
        if(success) {
            Log.i(TAG, "OpenCV Library loaded...");
        } else {
            Toast.makeText(this.getApplicationContext(), "could not load opencv lib...", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onClick(View view) {
        convert2Gray();
    }

    private void convert2Gray() {
        Bitmap bitmap = BitmapFactory.decodeResource(this.getResources(), R.drawable.test);
        Mat src = new Mat();
        Mat gray = new Mat();
        Utils.bitmapToMat(bitmap, src);
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY);
        Utils.matToBitmap(gray, bitmap);
        ImageView imageView = (ImageView)this.findViewById(R.id.test_image);
        imageView.setImageBitmap(bitmap);
    }

}
