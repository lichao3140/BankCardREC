package com.lichao.util;

import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 卡号识别图片处理
 * Created by Administrator on 2017-11-12.
 */

public class TextImageProcessor {
    public static final String FEATURE_FILE_PATH = Environment.getExternalStorageDirectory() + "/tesseract/traindata/";

    /**
     * 根据图片找到银行卡区域
     * @param fileUri
     * @return
     */
    public Mat findCard(Uri fileUri) {
        Mat src = Imgcodecs.imread(fileUri.getPath());
        if (src.empty()) {
            return null;
        }

        Mat grad_x = new Mat();//X 方向梯度
        Mat grad_y = new Mat();//Y方向梯度
        Mat abs_grad_x = new Mat();
        Mat abs_grad_y = new Mat();

        Imgproc.Scharr(src, grad_x, CvType.CV_32F, 1, 0);//梯度求取
        Imgproc.Scharr(src, grad_y, CvType.CV_32F, 0, 1);
        Core.convertScaleAbs(grad_x, abs_grad_x);//
        Core.convertScaleAbs(grad_y, abs_grad_y);
        grad_x.release();//内存释放
        grad_y.release();

        Mat grad = new Mat();
        Core.addWeighted(abs_grad_x, 0.5, abs_grad_y, 0.5, 0, grad);
        abs_grad_x.release();
        abs_grad_y.release();

        Mat binary = new Mat();//二值化
        Imgproc.cvtColor(grad, binary, Imgproc.COLOR_BGR2GRAY);
        Imgproc.threshold(binary, binary, 40, 255, Imgproc.THRESH_BINARY);
        grad.release();

        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3));
        Imgproc.morphologyEx(binary, binary, Imgproc.MORPH_OPEN, kernel);//形态学开操作

        //轮廓发现
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hireachy = new Mat();
        Imgproc.findContours(binary, contours, hireachy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE, new Point(0, 0));
        int hw = binary.cols() / 2;
        int hh = binary.rows() / 2;
        Rect roi = new Rect();
        for (int i = 0; i < contours.size(); i++) {
            Rect rect = Imgproc.boundingRect(contours.get(i));
            if (rect.width > hw) {
                roi.x = rect.x;
                roi.y = rect.y;
                roi.width = rect.width;
                roi.height = rect.height;
                break;
            }
        }
        if(roi.width == 0 || roi.height == 0) {
            return null;
        }
        Mat card = new Mat();
        src.submat(roi).copyTo(card);
        src.release();
        binary.release();
        return card;
    }

    /**
     * 找到银行卡号数字区域
     * @param card
     * @return
     */
    public Mat findCardNumBlock(Mat card) {
        Mat hsv = new Mat();
        Mat binary = new Mat();

        //色彩空间转换  BGR-HSV
        Imgproc.cvtColor(card, hsv, Imgproc.COLOR_BGR2HSV);
        //根据hsv颜色值进行过滤   这里是要获得蓝色，不同银行卡不同的颜色
        Core.inRange(hsv, new Scalar(30, 40, 45), new Scalar(180, 255, 255), binary);

        //形态学操作-去噪
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(5, 5));
        //形态学开操作
        Imgproc.morphologyEx(binary, binary, Imgproc.MORPH_OPEN, kernel);

        //轮廓发现
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hireachy = new Mat();
        Imgproc.findContours(binary, contours, hireachy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE, new Point(0, 0));
        int offsetx = binary.cols()/3;
        int offsety = binary.rows()/3;
        Rect numberROI = new Rect();//银行卡号区域
        for(int i=0; i<contours.size(); i++) {
            Rect roi = Imgproc.boundingRect(contours.get(i));
            if(Imgproc.contourArea(contours.get(i)) < 200) {
                continue;
            }
            if(roi.x < offsetx && roi.y < offsety) {
                numberROI.x = roi.x;
                numberROI.y = roi.y + 2* roi.height - 20;
                numberROI.width = binary.cols() - roi.x - 100;
                numberROI.height = (int)(roi.height * 0.7);
                break;
            }
        }

        if(numberROI.width == 0 || numberROI.height == 0) {
            return null;
        }

        Mat textImage = new Mat();
        card.submat(numberROI).copyTo(textImage);
        card.release();
        hsv.release();
        binary.release();
        return textImage;
    }

    /**
     * 分割字符
     * @param textImage
     * @return
     */
    public List<Mat> splitNumberBlock(Mat textImage) {
        List<Mat> numberImgs = new ArrayList<>();
        Mat gray = new Mat();
        Mat binary = new Mat();

        Imgproc.cvtColor(textImage, gray, Imgproc.COLOR_BGR2GRAY);//转换灰度

        // 二值化和去噪声
        Imgproc.threshold(gray, binary, 0, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3));
        Imgproc.morphologyEx(binary, binary, Imgproc.MORPH_CLOSE, kernel);//闭操作

        //去除噪声和填充
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hireachy = new Mat();
        Mat contourBin = binary.clone();
        Core.bitwise_not(contourBin, contourBin);
        Imgproc.findContours(contourBin, contours, hireachy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE, new Point(0, 0));
        int minh = binary.rows() / 3;
        for(int i=0; i<contours.size(); i++) {
            Rect roi = Imgproc.boundingRect(contours.get(i));
            double area = Imgproc.contourArea(contours.get(i));
            if(area < 200) {
                Imgproc.drawContours(binary, contours, i, new Scalar(255), -1);
                continue;
            }
            if(roi.height <= minh) {
                Imgproc.drawContours(binary, contours, i, new Scalar(255), -1);
                continue;
            }
        }

        //粘连字符分隔
        contours.clear();
        binary.copyTo(contourBin);
        Core.bitwise_not(contourBin, contourBin);//取反-变成黑底白字
        Imgproc.findContours(contourBin, contours, hireachy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE, new Point(0, 0));
        Rect[] textBoxes = new Rect[contours.size()];
        int index = 0;
        int minWidth = 1000000;
        Mat contoursImage = new Mat(contourBin.size(), CvType.CV_8UC1);
        contoursImage.setTo(new Scalar(255));
        for(int i=0; i<contours.size(); i++) {
            Rect bounding = Imgproc.boundingRect(contours.get(i));
            minWidth = Math.min(bounding.width, minWidth);
            textBoxes[index++] = bounding;
            Imgproc.drawContours(contoursImage, contours, i, new Scalar(0), 1);
        }
        minWidth = minWidth * 2;

        //排序
        for(int i = 0; i < textBoxes.length - 1; i++) {
            for(int j = i + 1; j < textBoxes.length; j++) {
                if(textBoxes[i].x > textBoxes[j].x) {
                    Rect temp = textBoxes[j];
                    textBoxes[j] = textBoxes[i];
                    textBoxes[i] = temp;
                }
            }
        }

        for(int k=0; k < textBoxes.length; k++) {
            if(textBoxes[k].height <= minh)
                continue;
            if(textBoxes[k].width > minWidth) {//防止两个数字连在一起
                Mat twoText = new Mat();
                contoursImage.submat(textBoxes[k]).copyTo(twoText);
                int xpos = getSplitLinePos(binary.submat(textBoxes[k]));
                numberImgs.add(twoText.submat(new Rect(0, 0, xpos-1, textBoxes[k].height)));
                numberImgs.add(twoText.submat(new Rect(xpos+1, 0, textBoxes[k].width-xpos-1, textBoxes[k].height)));
            } else {
                Mat oneText = new Mat();
                contoursImage.submat(textBoxes[k]).copyTo(oneText);
                numberImgs.add(oneText);
            }
        }

        // 内存释放
        textBoxes = null;
        binary.release();
        gray.release();
        contourBin.release();
        contoursImage.release();
        return numberImgs;
    }

    public int recognitionChar(Mat charImage) {
        String result = "";
        File file = new File(FEATURE_FILE_PATH);
        File[] featureDataFiles = file.listFiles();
        double minDistance = Double.MAX_VALUE;
        float[] fv = extractFeatureData(charImage);
        for(File f : featureDataFiles) {
            double dist = calculateDistance(fv, readFeatureVector(f));
            if(minDistance > dist) {
                minDistance = dist;
                result = f.getName();
            }
        }
        Log.i("OCR-INFO", result);
        return Integer.parseInt(result.substring(0, 1));
    }

    private double calculateDistance(float[] v1, float[] v2) {
        double x = 0, y = 0, z = 0, zf = 0;
        for(int i=0; i<40; i++) {
            x = v1[i];
            y = v2[i];
            z = x - y;
            z *=z;
            zf += z;
        }
        return zf;
    }

    /**
     * 获取中间位置
     * @param mtexts
     * @return
     */
    private int getSplitLinePos(Mat mtexts) {
        int hx = mtexts.cols() / 2;
        int width = mtexts.cols();
        int height = mtexts.rows();
        byte[] data = new byte[width*height];
        mtexts.get(0, 0, data);
        int startx = hx - 10;
        int endx = hx + 10;
        int linepos = hx;
        int min = 1000000;
        int c = 0;
        for(int col=startx; col<=endx; col++) {
            int total = 0;
            for(int row=0; row<height; row++) {
                c = data[row*width+col]&0xff;
                if(c == 0) {
                    total++;
                }
            }
            if(total < linepos) {
                linepos = total;
                linepos = col;
            }
        }
        return linepos;
    }

    /**
     * 提取影射特征向量
     * @param txtImage
     * @return
     */
    public float[] extractFeatureData(Mat txtImage) {
        float[] vectordata = new float[40];
        Arrays.fill(vectordata, 0);
        float width = txtImage.cols();
        float height = txtImage.rows();
        byte[] data = new byte[(int)(width*height)];
        txtImage.get(0, 0, data);
        float bins = 10.0f;
        float xstep = width / 4.0f;
        float ystep = height / 5.0f;
        int index = 0;

        //20个像素点
        for(float y=0; y<height; y+=ystep) {
            for(float x=0; x<width; x+=xstep) {
                vectordata[index] = getWeightBlackNumber(data, width, height, x, y, xstep, ystep);
                index++;
            }
        }

        //计算Y影射
        xstep = width / bins;
        for(float x=0; x<width; x+=xstep) {
            if((xstep+x) - width > 1)
                continue;
            vectordata[index] = getWeightBlackNumber(data, width, height, x, 0, xstep, height);
            index++;
        }

        //计算X影射
        ystep = height / bins;
        for(float y=0; y<height; y+=ystep) {
            if((y+ystep) - height > 1)continue;
            vectordata[index] = getWeightBlackNumber(data, width, height, 0, y, width, ystep);
            index++;
        }

        // 归一化
        float sum = 0;
        for(int i=0; i<20; i++) {
            sum+=vectordata[i];
        }
        for(int i=0; i<20; i++) {
            vectordata[i] = vectordata[i]/sum;
        }

        // Y
        sum = 0;
        for(int i=20; i<30; i++) {
            sum += vectordata[i];
        }
        for(int i=20; i<30; i++) {
            vectordata[i] = vectordata[i]/sum;
        }

        // X
        sum = 0;
        for(int i=30; i<40; i++) {
            sum += vectordata[i];
        }
        for(int i=30; i<40; i++) {
            vectordata[i] = vectordata[i]/sum;
        }
        return vectordata;
    }

    /**
     * 获取黑色数字的权重
     * @param data
     * @param width
     * @param height
     * @param x
     * @param y
     * @param xstep
     * @param ystep
     * @return
     */
    private float getWeightBlackNumber(byte[] data, float width, float height, float x, float y, float xstep, float ystep) {
        float weightNum = 0;

        // 整数部分
        int nx = (int)Math.floor(x);
        int ny = (int)Math.floor(y);

        // 小数部分
        float fx = x - nx;
        float fy = y - ny;

        // 宽度与高度
        float w = x+xstep;
        float h = y+ystep;
        if(w > width) {
            w = width - 1;
        }
        if(h > height) {
            h = height - 1;
        }

        // 宽高整数部分
        int nw = (int)Math.floor(w);
        int nh = (int)Math.floor(h);

        // 小数部分
        float fw = w - nw;
        float fh = h - nh;

        // 统计黑色像素个数
        int c = 0;
        int ww = (int)width;
        float weight = 0;
        int row=0;
        int col=0;
        for(row=ny; row<nh; row++) {
            for(col=nx; col<nw; col++) {
                c = data[row*ww+col]&0xff;
                if(c == 0) {
                    weight++;
                }
            }
        }

        // 计算小数部分黑色像素权重加和
        float w1=0, w2=0, w3=0, w4=0;
        // calculate w1
        if(fx > 0) {
            col = nx+1;
            if(col > width - 1) {
                col = col - 1;
            }
            float count = 0;
            for(row=ny; row<nh; row++) {
                c = data[row*ww+col]&0xff;
                if(c == 0){
                    count++;
                }
            }
            w1 = count*fx;
        }

        // calculate w2
        if(fy > 0) {
            row = ny+1;
            if(row > height - 1) {
                row = row - 1;
            }
            float count = 0;
            for(col=nx; col<nw; col++) {
                c = data[row*ww+col]&0xff;
                if(c == 0){
                    count++;
                }
            }
            w2 = count*fy;
        }

        if(fw > 0) {
            col = nw + 1;
            if(col > width - 1) {
                col = col - 1;
            }
            float count = 0;
            for(row=ny; row<nh; row++) {
                c = data[row*ww+col]&0xff;
                if(c == 0) {
                    count++;
                }
            }
            w3 = count*fw;
        }

        if(fh > 0) {
            row = nh + 1;
            if(row > height - 1) {
                row = row - 1;
            }
            float count = 0;
            for(col=nx; col<nw; col++) {
                c = data[row*ww+col]&0xff;
                if(c == 0) {
                    count++;
                }
            }
            w4 = count*fh;
        }

        weightNum = (weight - w1 - w2 + w3 + w4);
        if(weightNum < 0) {
            weightNum = 0;
        }
        return weightNum;
    }

    /**
     *读取特征数据
     * @param f
     * @return
     */
    private float[] readFeatureVector(File f) {
        float[] fv = new float[40];
        try {
            if(f.exists()) {
                BufferedReader br = new BufferedReader(new FileReader(f));
                for(int i=0; i<40; i++) {
                    float currVal = Float.parseFloat(br.readLine());
                    fv[i] = currVal;
                }
            }
        } catch (IOException ioe) {
            Log.i("IO-ERR", ioe.getMessage());
        }
        return fv;
    }

    public void dumpFeature(float[] fv, int textNum) {
        try {
            File file = new File(FEATURE_FILE_PATH + File.separator + "feature_" + textNum + ".txt");
            if (file.exists()) {
                file.delete();
            }
            if (file.createNewFile()) {
                PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(file)));
                for (int k = 0; k < fv.length; k++) {
                    pw.println(String.valueOf(fv[k]));
                }
                pw.close();
            }
        } catch (IOException ioe) {
            System.err.println(ioe);
        }
    }
}
