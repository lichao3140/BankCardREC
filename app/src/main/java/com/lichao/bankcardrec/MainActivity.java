package com.lichao.bankcardrec;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import org.opencv.android.OpenCVLoader;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * OpenCV 银行卡号识别
 */
public class MainActivity extends AppCompatActivity implements View.OnClickListener{
    private String TAG = "OPenCV-Android";
    private int REQUEST_CAPTURE_IMAGE = 1;
    private Uri fileUri;

    private Button btn_take_picture;
    private Button btn_select_picture;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        btn_take_picture = (Button) this.findViewById(R.id.take_picture_btn);
        btn_select_picture = (Button) this.findViewById(R.id.select_picture_btn);

        btn_take_picture.setOnClickListener(this);
        btn_select_picture.setOnClickListener(this);

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
        int id = view.getId();
        switch (id) {
            case R.id.take_picture_btn:
                start2Camera();
                break;
            case R.id.select_picture_btn:
                pickUpImage();
                break;
            default:
                break;
        }
    }

    /**
     * 选择图片
     */
    private void pickUpImage() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "图像选择..."), REQUEST_CAPTURE_IMAGE);
    }

    /**
     * 拍照
     */
    private void start2Camera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        fileUri = Uri.fromFile(getSaveFilePath());
        intent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);
        startActivityForResult(intent, REQUEST_CAPTURE_IMAGE);
    }

    private File getSaveFilePath() {
        String status = Environment.getExternalStorageState();
        if(!status.equals(Environment.MEDIA_MOUNTED)) {
            Log.i(TAG, "SD Card is not suitable...");
            return null;
        }
        SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_hhmmss");
        String name = df.format(new Date(System.currentTimeMillis()))+ ".jpg";
        File filedir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "myOcrImages");
        filedir.mkdirs();
        String fileName = filedir.getAbsolutePath() + File.separator + name;
        File imageFile = new File(fileName);
        return imageFile;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == REQUEST_CAPTURE_IMAGE && resultCode == RESULT_OK) {
            if(data == null) {
                Intent intent = new Intent(getApplicationContext(), CardOCRActivity.class);
                intent.putExtra("PICTURE-URL", fileUri);
                startActivity(intent);
            } else {
                Uri uri = data.getData();
                Intent intent = new Intent(getApplicationContext(), CardOCRActivity.class);
                File f = new File(getRealPath(uri));
                intent.putExtra("PICTURE-URL", Uri.fromFile(f));
                startActivity(intent);
            }
        }
    }

    private String getRealPath(Uri uri) {
        String filePath = null;
        if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT){//4.4及以上
            String wholeID = DocumentsContract.getDocumentId(uri);
            String id = wholeID.split(":")[1];
            String[] column = { MediaStore.Images.Media.DATA };
            String sel = MediaStore.Images.Media._ID + "=?";
            Cursor cursor = getApplicationContext().getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, column,
                    sel, new String[] { id }, null);
            int columnIndex = cursor.getColumnIndex(column[0]);
            if (cursor.moveToFirst()) {
                filePath = cursor.getString(columnIndex);
            }
            cursor.close();
        }else{//4.4以下，即4.4以下获取路径的方法
            String[] projection = { MediaStore.Images.Media.DATA };
            Cursor cursor = getApplicationContext().getContentResolver().query(uri, projection, null, null, null);
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            filePath = cursor.getString(column_index);
        }
        Log.i("CV_TAG", "selected image path : " + filePath);
        return filePath;
    }
}
