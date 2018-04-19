package cc.harryzhang.projectfinger;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfInt4;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;

import java.io.File;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static android.content.ContentValues.TAG;
import static org.opencv.core.Core.inRange;
import static org.opencv.core.Core.max;
import static org.opencv.imgcodecs.Imgcodecs.imread;
import static org.opencv.imgproc.Imgproc.GaussianBlur;
import static org.opencv.imgproc.Imgproc.MORPH_DILATE;
import static org.opencv.imgproc.Imgproc.MORPH_ELLIPSE;
import static org.opencv.imgproc.Imgproc.MORPH_ERODE;
import static org.opencv.imgproc.Imgproc.MORPH_RECT;
import static org.opencv.imgproc.Imgproc.arrowedLine;
import static org.opencv.imgproc.Imgproc.circle;
import static org.opencv.imgproc.Imgproc.contourArea;
import static org.opencv.imgproc.Imgproc.contourMoments;
import static org.opencv.imgproc.Imgproc.convexHull;
import static org.opencv.imgproc.Imgproc.convexityDefects;
import static org.opencv.imgproc.Imgproc.cvtColor;
import static org.opencv.imgproc.Imgproc.dilate;
import static org.opencv.imgproc.Imgproc.drawContours;
import static org.opencv.imgproc.Imgproc.erode;
import static org.opencv.imgproc.Imgproc.findContours;
import static org.opencv.imgproc.Imgproc.getStructuringElement;
import static org.opencv.imgproc.Imgproc.line;
import static org.opencv.imgproc.Imgproc.minAreaRect;
import static org.opencv.imgproc.Imgproc.moments;
import static org.opencv.imgproc.Imgproc.resize;

/**
 * Created by ztc on 2/10/18.
 * References:
 * Displaying an image via OpenCV: http://blog.udn.com/mark52home/30919081
 */

public class ViewImageActivity extends Activity {
    ImageView imgView;
    TextView textLabel;
    Mat imgRGB;
    Mat resultSkin, resultNR, resultContours, resultHulls, resultRect, resultDefects, resultCentroid;
    MatOfPoint maxContour;
    RotatedRect boundingRect;
    List<Point> contourPts;
    ArrayList<Double> contourSizeMetrics;
    Point centroid;
    MatOfInt convexHull;
    File mediaStorageDir;
    Button cvButton;
    CVOps mCVOps;
    HashMap<String, ArrayList<Integer[]>> defectResults;
    Button bkButton, fwdButton;
    boolean mOpenCVLoaded;
    File[] images;
    int mFileIndex;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_view_img);

        imgView = (ImageView) findViewById(R.id.image_view);
        textLabel = (TextView) findViewById(R.id.text_cv);
        textLabel.setText("Original image.");

        cvButton = (Button) findViewById(R.id.btn_cv);
        cvButton.setText("Detect skin");

        mCVOps = new CVOps(getApplicationContext(), false);

        cvButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if(cvButton.getText().equals("Detect skin")) {
                            resultSkin = mCVOps.detectSkin(imgRGB);

                            displayImage(resultSkin, "color");
                            textLabel.setText("Skin detection results");

                            cvButton.setText("Noise reduction");
                        } else if(cvButton.getText().equals("Noise reduction")) {
                            resultNR = mCVOps.noiseReduction(resultSkin);
                            displayImage(resultNR, "color");

                            textLabel.setText("Noise reduction results.");

                            cvButton.setText("Find contours");
                        } else if(cvButton.getText().equals("Find contours")) {
                            resultContours = new Mat();
                            cvtColor(resultNR, resultContours, Imgproc.COLOR_GRAY2RGB);
                            MatOfPoint maxContour = mCVOps.findMaxContour(resultNR);
                            ViewImageActivity.this.maxContour = maxContour;
                            contourPts = maxContour.toList();
                            List<MatOfPoint> contours = new ArrayList<>();
                            contours.add(maxContour);
                            drawContours(resultContours, contours, 0, new Scalar(0, 0, 255), 30);
                            displayImage(resultContours, "color");
                            textLabel.setText("Largest contour results.");
                            cvButton.setText("Find convex hull");
                        } else if(cvButton.getText().equals("Find convex hull")) {
                            MatOfInt hull = mCVOps.findConvexHull(maxContour);
                            ViewImageActivity.this.convexHull = hull;
                            List<Integer> hullIdxs = hull.toList();
                            contourPts = maxContour.toList();
                            ArrayList<Point> hullPointsList = new ArrayList<Point>();
                            for (int i = 0; i < hullIdxs.size(); i++) {
                                hullPointsList.add(contourPts.get(hullIdxs.get(i)));
                            }
                            MatOfPoint hullPoints = new MatOfPoint();
                            hullPoints.fromList(hullPointsList);
                            List<MatOfPoint> hulls = new ArrayList<MatOfPoint>();
                            hulls.add(hullPoints);
                            resultHulls = resultContours.clone();
                            drawContours(resultHulls, hulls, 0, new Scalar(0, 255, 0), 30);
                            textLabel.setText("Convex hull results.");
                            cvButton.setText("Find bounding rect");
                        } else if(cvButton.getText().equals("Find bounding rect")) {
                            boundingRect = minAreaRect(new MatOfPoint2f(maxContour.toArray()));
                            Point[] rectVertices = new Point[4];
                            boundingRect.points(rectVertices);
                            resultRect = resultHulls.clone();
                            for(int i = 0; i < 4; i ++) {
                                line(resultRect, rectVertices[i], rectVertices[(i + 1) % 4], new Scalar(255, 255, 0), 30);
                            }
                            contourSizeMetrics = mCVOps.getBoundingRectParams(maxContour);

                            displayImage(resultRect, "color");
                            textLabel.setText("Bounding rect size metric: " + contourSizeMetrics.get(0).toString() + "; ratio metric: " + contourSizeMetrics.get(1).toString() + "; area metric:" + contourSizeMetrics.get(2));

                            cvButton.setText("Detect defects");
                        } else if(cvButton.getText().equals("Detect defects")) {
                            defectResults = mCVOps.getConvexDefects(maxContour, convexHull, contourSizeMetrics.get(0).doubleValue());

                            resultDefects = resultRect.clone();
                            for(int i = 0; i < defectResults.get("long").size(); i ++) {
                                arrowedLine(resultDefects, contourPts.get(defectResults.get("long").get(i)[0]),
                                        contourPts.get(defectResults.get("long").get(i)[2]),
                                        new Scalar(255, 0, 0), 20, 8, 0, 0.1);
                                arrowedLine(resultDefects, contourPts.get(defectResults.get("long").get(i)[1]),
                                        contourPts.get(defectResults.get("long").get(i)[2]),
                                        new Scalar(255, 0, 0), 20, 8, 0, 0.1);
                            }
                            for(int i = 0; i < defectResults.get("wide").size(); i ++) {
                                arrowedLine(resultDefects, contourPts.get(defectResults.get("wide").get(i)[0]),
                                        contourPts.get(defectResults.get("wide").get(i)[2]),
                                        new Scalar(0, 255, 255), 20, 8, 0, 0.1);
                                arrowedLine(resultDefects, contourPts.get(defectResults.get("wide").get(i)[1]),
                                        contourPts.get(defectResults.get("wide").get(i)[2]),
                                        new Scalar(0, 255, 255), 20, 8, 0, 0.1);
                            }
                            displayImage(resultDefects, "color");
                            textLabel.setText("Convex hull defects detection results.");

                            cvButton.setText("Detect center");
                        } else if(cvButton.getText().equals("Detect center")) {
                            centroid = mCVOps.getCentroid(maxContour);
                            resultCentroid = resultDefects.clone();
                            circle(resultCentroid, new Point(centroid.x, centroid.y), 20,
                                    new Scalar(255, 0, 255), 20);
                            displayImage(resultCentroid, "color");
                            textLabel.setText("Centroid results");

                            cvButton.setText("Show results");
                        } else if(cvButton.getText().equals("Show results")) {
                            String resultText = "Long defects: " + defectResults.get("long").size();
                            resultText += ". Bounding box w/h ratio: " + String.format("%.2f", contourSizeMetrics.get(1)) + "; fill ratio: " + String.format("%.2f", contourSizeMetrics.get(2));
                            String value = mCVOps.getValue(defectResults, contourSizeMetrics);
                            resultText += "\n<Value:" + value;
                            String position = mCVOps.getPosition(resultNR.size(), centroid);
                            resultText += ", Position: " + position + ">";

                            textLabel.setText(resultText);
                        }
                    }
                }
        );

        bkButton = (Button) findViewById(R.id.btn_scroll_back);
        bkButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if(mFileIndex < images.length - 1) {
                            mFileIndex += 1;
                            textLabel.setText("Original image.");
                            cvButton.setText("Detect skin");
                            imgRGB = mCVOps.loadImage(images[mFileIndex].getAbsolutePath());
                            displayImage(imgRGB, "color");
                        }
                    }
                }
        );

        fwdButton = (Button) findViewById(R.id.btn_scroll_forward);
        fwdButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if(mFileIndex > 0) {
                            mFileIndex -= 1;
                            textLabel.setText("Original image.");
                            cvButton.setText("Detect skin");
                            imgRGB = mCVOps.loadImage(images[mFileIndex].getAbsolutePath());
                            displayImage(imgRGB, "color");
                        }
                    }
                }
        );

        mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), getResources().getString(R.string.folder_name));

        images = mediaStorageDir.listFiles();
        Arrays.sort(images, new Comparator<File>(){
            public int compare(File f1, File f2) {
                return - Long.valueOf(f1.lastModified()).compareTo(f2.lastModified());
            }
        });
        mFileIndex = 0;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(!OpenCVLoader.initDebug()) {
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0, this, mLoaderCallback);
        } else {
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            super.onManagerConnected(status);
            switch(status) {
                case LoaderCallbackInterface.SUCCESS:
                    mOpenCVLoaded = true;

                    imgRGB = mCVOps.loadImage(images[mFileIndex].getAbsolutePath());
                    displayImage(imgRGB, "color");

                    break;
            }
        }
    };

    private void displayImage(Mat targetMat, String mode) {
        if(mOpenCVLoaded){
            Mat imgMat = new Mat();
            if(mode.equals("binary")) {
                cvtColor(targetMat, imgMat, Imgproc.COLOR_GRAY2RGB);
            } else {
                imgMat = targetMat.clone();
            }
            Bitmap imgMap = Bitmap.createBitmap(imgMat.cols(), imgMat.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(imgMat, imgMap);
            imgView.setImageBitmap(imgMap);
        }
    }
}
