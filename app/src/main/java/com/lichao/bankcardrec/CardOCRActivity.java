package com.lichao.bankcardrec;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.lichao.util.TextImageProcessor;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public class CardOCRActivity extends AppCompatActivity implements View.OnClickListener{
    private String TAG = "OCR";

    private Uri fileUri;
    private Button recognitionBtn;
    private ImageView imageView;
    private TextImageProcessor processor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_card_ocr);

        recognitionBtn = (Button)this.findViewById(R.id.recognition_btn);
        recognitionBtn.setOnClickListener(this);
        fileUri = (Uri)this.getIntent().getParcelableExtra("PICTURE-URL");
        if(fileUri != null) {
            processor = new TextImageProcessor();
            displaySelectedImage();
        }
    }

    private void displaySelectedImage() {
        imageView = (ImageView)this.findViewById(R.id.imageView);
        BitmapFactory.Options options = new BitmapFactory.Options();//加载大小合适图片
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(fileUri.getPath(), options);
        int w = options.outWidth;
        int h = options.outHeight;
        int inSample = 1;
        if(w > 1000 || h > 1000) {//避免OOM
            while(Math.max(w/inSample, h/inSample) > 1000) {
                inSample *= 2;
            }
        }
        options.inJustDecodeBounds = false;
        options.inSampleSize = inSample;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bm = BitmapFactory.decodeFile(fileUri.getPath(), options);
        imageView.setImageBitmap(bm);
    }

    @Override
    public void onClick(View view) {
        Log.i(TAG, "Start to process selected image...");
        Toast.makeText(getApplicationContext(), "OCR", Toast.LENGTH_LONG).show();

        Mat card = processor.findCard(fileUri);
        if(card == null) return;

        Mat numImage = processor.findCardNumBlock(card);
        if(numImage == null) return;

        List<Mat> textList = processor.splitNumberBlock(numImage);
        if(textList != null && textList.size() > 0) {
            Log.i(TAG, "Number of digits : " + textList.size());
            int index = 0;
            String cardId = "";
            for(Mat oneText : textList) {
                int digit = processor.recognitionChar(oneText);
                if(digit == 0 || digit == 1) {
                    float w = oneText.cols();
                    float h = oneText.rows();
                    float rate = w / h;
                    if(rate > 0.5) {
                        digit = 0;
                    } else {
                        digit = 1;
                    }
                }
                cardId += digit;
                oneText.release();
            }
            Log.i("OCR-INFO", "Card Number : " + cardId);
            TextView cardNumberTextView = (TextView)this.findViewById(R.id.textView);
            cardNumberTextView.setText(cardId);
        }

        Log.i(TAG, "Find Card and Card Number Block...");
        Bitmap bitmap = Bitmap.createBitmap(numImage.cols(),numImage.rows(), Bitmap.Config.ARGB_8888);
        Imgproc.cvtColor(numImage, numImage, Imgproc.COLOR_BGR2RGBA);
        Utils.matToBitmap(numImage, bitmap);
        //saveDebugImage(bitmap);
        imageView.setImageBitmap(bitmap);
    }

    /**
     * 保存图片
     * @param bitmap
     */
    private void saveDebugImage(Bitmap bitmap) {
        File filedir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "myOcrImages");
        String name = String.valueOf(System.currentTimeMillis()) + "_ocr.jpg";
        File tempFile = new File(filedir.getAbsoluteFile()+File.separator, name);
        FileOutputStream output = null;
        try {
            output = new FileOutputStream(tempFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output);
        }catch (IOException ioe) {
            Log.e("DEBUG-ERR", ioe.getMessage());
        } finally {
            try {
                output.flush();
                output.close();
            } catch (IOException e) {
                Log.i("DEBUG-INFO", e.getMessage());
            }
        }
    }
}
