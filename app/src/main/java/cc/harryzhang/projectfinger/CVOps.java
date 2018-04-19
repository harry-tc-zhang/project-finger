package cc.harryzhang.projectfinger;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
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

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static android.content.ContentValues.TAG;
import static org.opencv.core.Core.inRange;
import static org.opencv.imgcodecs.Imgcodecs.imread;
import static org.opencv.imgproc.Imgproc.GaussianBlur;
import static org.opencv.imgproc.Imgproc.MORPH_RECT;
import static org.opencv.imgproc.Imgproc.contourArea;
import static org.opencv.imgproc.Imgproc.contourMoments;
import static org.opencv.imgproc.Imgproc.convexHull;
import static org.opencv.imgproc.Imgproc.convexityDefects;
import static org.opencv.imgproc.Imgproc.cvtColor;
import static org.opencv.imgproc.Imgproc.dilate;
import static org.opencv.imgproc.Imgproc.erode;
import static org.opencv.imgproc.Imgproc.findContours;
import static org.opencv.imgproc.Imgproc.getStructuringElement;
import static org.opencv.imgproc.Imgproc.minAreaRect;
import static org.opencv.imgproc.Imgproc.resize;

/**
 * Created by ztc on 2/12/18.
 * OpenCV Setup: http://blog.codeonion.com/2015/11/25/creating-a-new-opencv-project-in-android-studio/
 * Moments calculation missing in Java API: https://github.com/opencv/opencv/issues/5017
 * Skin detection: https://stackoverflow.com/questions/32448653/which-is-color-space-for-hsv-skin-detector-opencv-android
 * General pipeline (changed from Python implementation): http://creat-tabu.blogspot.ro/2013/08/opencv-python-hand-gesture-recognition.html
 * Rotating an image: https://stackoverflow.com/questions/16265673/rotate-image-by-90-180-or-270-degrees
 */

public class CVOps {
    private String mTask = "load_image";
    private Context context;
    private boolean mOpenCVLoaded;
    private boolean makeProgressToast;

    public CVOps(Context context, boolean makeToast) {
        this.context = context;
        this.makeProgressToast = makeToast;

        if(!OpenCVLoader.initDebug()) {
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0, context, mLoaderCallback);
        } else {
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public Mat loadImage(String imgPath) {
        if(mOpenCVLoaded) {
            Log.d(TAG, imgPath.toString());
            Mat imgBGR = imread(imgPath.toString());
            Log.d(TAG, "Image size: (" + String.valueOf(imgBGR.cols()) + ", " + String.valueOf(imgBGR.rows()) + ")");
            if(imgBGR.cols() > imgBGR.rows()) {
                Core.flip(imgBGR.t(), imgBGR, 1);
            }
            Mat imgRGB = new Mat();
            cvtColor(imgBGR, imgRGB, Imgproc.COLOR_BGR2RGB);
            if(makeProgressToast)
                Toast.makeText(context, "Image loaded", Toast.LENGTH_SHORT).show();
            return imgRGB;
        }
        return null;
    }

    public Mat detectSkin(Mat imgBGR) {
        if(mOpenCVLoaded) {
            Mat imgHSV = new Mat();
            cvtColor(imgBGR, imgHSV, Imgproc.COLOR_RGB2HSV);

            double scaleSatLower = 0.28;
            scaleSatLower = 0.15;
            double scaleSatUpper = 0.68;
            scaleSatUpper = 0.8;
            Scalar skinLower = new Scalar(0, scaleSatLower * 255, 0);
            Scalar skinUpper = new Scalar(25, scaleSatUpper * 255, 255);
            Mat resultSkin = new Mat();
            inRange(imgHSV, skinLower, skinUpper, resultSkin);
            if(makeProgressToast)
                Toast.makeText(context, "Skin detection complete", Toast.LENGTH_SHORT).show();
            return resultSkin;
        }
        return null;
    }

    public Mat noiseReduction(Mat resultSkin) {
        if(mOpenCVLoaded) {
            Mat resultNR = new Mat();
            resize(resultSkin, resultNR, new Size(resultSkin.cols() / 2, resultSkin.rows() / 2));
            erode(resultNR, resultNR, getStructuringElement(MORPH_RECT, new Size(20, 20)));
            dilate(resultNR, resultNR, getStructuringElement(MORPH_RECT, new Size(20, 20)));
            GaussianBlur(resultNR, resultNR, new Size(31, 31), 5);
            if(makeProgressToast)
                Toast.makeText(context, "Noise reduction complete", Toast.LENGTH_SHORT).show();
            return resultNR;
        }
        return null;
    }

    public MatOfPoint findMaxContour(Mat resultNR) {
        if(mOpenCVLoaded) {
            ArrayList<MatOfPoint> contours = new ArrayList<MatOfPoint>();
            Mat hierarchy = new Mat();
            findContours(resultNR, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE, new Point(0, 0));
            double maxArea = 0;
            int maxIndex = -1;
            for (int i = 0; i < contours.size(); i++) {
                double area = contourArea(contours.get(i));
                if (area > maxArea) {
                    maxIndex = i;
                    maxArea = area;
                }
            }
            if(makeProgressToast)
                Toast.makeText(context, "Contour finding complete", Toast.LENGTH_SHORT).show();
            return contours.get(maxIndex);
        }
        return null;
    }

    public MatOfInt findConvexHull(MatOfPoint maxContour) {
        if(mOpenCVLoaded) {
            MatOfInt hull = new MatOfInt();
            convexHull(maxContour, hull);
            Toast.makeText(context, "Convex hull finding complete", Toast.LENGTH_SHORT).show();
            return hull;
        }
        return null;
    }

    public ArrayList<Double> getBoundingRectParams(MatOfPoint maxContour) {
        if(mOpenCVLoaded) {
            RotatedRect boundingRect = minAreaRect(new MatOfPoint2f(maxContour.toArray()));
            Point[] rectPts = new Point[4];
            boundingRect.points(rectPts);
            double contourSizeMetric = Math.sqrt(
                    Math.pow(rectPts[0].x - boundingRect.center.x, 2)
                            + Math.pow(rectPts[0].y - boundingRect.center.y, 2)
            );
            double rectL1 = Math.sqrt(
                    Math.pow(rectPts[0].x - rectPts[1].x, 2)
                            + Math.pow(rectPts[0].y - rectPts[1].y, 2)
            );
            double rectL2 = Math.sqrt(
                    Math.pow(rectPts[1].x - rectPts[2].x, 2)
                            + Math.pow(rectPts[1].y - rectPts[2].y, 2)
            );
            double contourRatioMetric = rectL1 / rectL2;

            double rectArea = rectL1 * rectL2;
            double contourArea = contourArea(maxContour);
            double contourAreaMetric = contourArea / rectArea;

            if(contourRatioMetric < 1) {
                contourRatioMetric = rectL2 / rectL1;
            }
            ArrayList<Double> metrics = new ArrayList<>();
            metrics.add(contourSizeMetric);
            metrics.add(contourRatioMetric);
            metrics.add(contourAreaMetric);
            if(makeProgressToast)
               Toast.makeText(context,
                        "Bounding rect: sizeMetric = " + String.valueOf(contourSizeMetric) + "; ratioMetric = " + String.valueOf(contourRatioMetric) + "; areaMetric = " + contourAreaMetric,
                        Toast.LENGTH_SHORT
               ).show();
            return metrics;
        }
        return null;
    }

    public HashMap<String, ArrayList<Integer[]>> getConvexDefects(MatOfPoint maxContour, MatOfInt convexHull, double contourSizeMetric) {
        if(mOpenCVLoaded) {
            MatOfInt4 defects = new MatOfInt4();
            convexityDefects(maxContour, convexHull, defects);
            List<Integer> defectsData = defects.toList();
            ArrayList<Integer[]> defectTuples = new ArrayList<>();
            for(int i = 0; i < defectsData.size(); i ++) {
                if(i % 4 == 0) {
                    defectTuples.add(new Integer[]{
                            defectsData.get(i), defectsData.get(i + 1),
                            defectsData.get(i + 2), defectsData.get(i + 3)
                    });
                }
            }
            Collections.sort(defectTuples, new Comparator<Integer[]>() {
                @Override
                public int compare(Integer[] item1, Integer[] item2) {
                    return - item1[3].compareTo(item2[3]);
                }
            });

            ArrayList<Integer[]> longTuples = new ArrayList<>();
            ArrayList<Integer[]> wideTuples = new ArrayList<>();
            ArrayList<Integer[]> invalidTuples = new ArrayList<>();

            List<Point> contourPts = maxContour.toList();

            for(int i = 0; i < defectTuples.size(); i ++) {
                Map<String, Double> defectResult = calcDefect(contourPts, defectTuples.get(i), contourSizeMetric);
                if(defectResult.get("validity").doubleValue() > 0) {
                    wideTuples.add(defectTuples.get(i));
                } else if(defectResult.get("validity").doubleValue() < 0) {
                    invalidTuples.add(defectTuples.get(i));
                } else {
                    longTuples.add(defectTuples.get(i));
                }
            }

            if(makeProgressToast)
                Toast.makeText(context,
                        "Convexity defects: " + String.valueOf(longTuples.size()) + " long, " + String.valueOf(wideTuples.size()) + " wide",
                        Toast.LENGTH_SHORT).show();

            HashMap<String, ArrayList<Integer[]>> retMap = new HashMap<>();

            retMap.put("long", longTuples);
            retMap.put("wide", wideTuples);
            retMap.put("invalid", invalidTuples);

            return retMap;
        }
        return null;
    }

    private Map<String, Double> calcDefect(List<Point> contourPts, Integer[] defectTuple, double sizeMetric) {
        Map<String, Double> result = new HashMap<String, Double>();
        double validity = 0;

        Integer startIndex = defectTuple[0];
        Integer endIndex = defectTuple[1];
        double distance = Math.sqrt(
                Math.pow(contourPts.get(startIndex).x - contourPts.get(endIndex).x, 2)
                        + Math.pow(contourPts.get(startIndex).y - contourPts.get(endIndex).y, 2)
        ) / sizeMetric;

        Point midPoint = new Point();
        midPoint.x = (contourPts.get(startIndex).x + contourPts.get(endIndex).x) / 2.0;
        midPoint.y = (contourPts.get(startIndex).y + contourPts.get(endIndex).y) / 2.0;

        Integer tipIndex = defectTuple[2];
        //double height = defectTuple[3].doubleValue() / 256.0 / sizeMetric;
        double height = 2 * Math.sqrt(
                Math.pow(contourPts.get(tipIndex).x - midPoint.x, 2)
                        + Math.pow(contourPts.get(tipIndex).y - midPoint.y, 2)
        ) / sizeMetric;

        double hwRatio = height / distance;

        if((hwRatio >= 1) && (hwRatio <= 10) && (height > 0.3) && (height < 1.7)) {
            Log.d(TAG, "Long: (" + String.valueOf(distance) + ", " + String.valueOf(height) + "," + String.valueOf(height / distance) + ")");
            validity = 0;
        } else if((hwRatio < 1) && (hwRatio > 0.1) && (height > 0.3) && (height < 1.7)) {
            Log.d(TAG, "Wide: (" + String.valueOf(distance) + ", " + String.valueOf(height) + "," + String.valueOf(height / distance) + ")");
            validity = 1;
        } else {
            Log.d(TAG, "Invalid: (" + String.valueOf(distance) + ", " + String.valueOf(height) + "," + String.valueOf(height / distance) + ")");
            validity = -1;
        }

        result.put("distance", distance);
        result.put("height", height);
        result.put("ratio", hwRatio);
        result.put("validity", validity);

        return result;
    }

    public Point getCentroid(MatOfPoint maxContour) {
        if(mOpenCVLoaded) {
            Moments cMoments = contourMoments(maxContour);
            int centerX = (int) (cMoments.get_m10() / cMoments.get_m00());
            int centerY = (int) (cMoments.get_m01() / cMoments.get_m00());
            Point centroid = new Point(centerX, centerY);
            if(makeProgressToast)
                Toast.makeText(context,
                        "Centroid: (" + String.valueOf(centerX) + ", " + String.valueOf(centerY) + ")",
                        Toast.LENGTH_SHORT).show();
            return centroid;
        }
        return null;
    }

    public String getPosition(Size imageSize, Point centroid) {
        double cornerThresholdX = 0.4;
        double cornerThresholdY = 0.3;
        if(mOpenCVLoaded) {
            double xRatio = centroid.x / imageSize.width;
            double yRatio = centroid.y / imageSize.height;
            String result = "";
            if((xRatio < cornerThresholdX) && (yRatio < cornerThresholdY)) {
                result = "corner";
            } else if((xRatio < cornerThresholdX) && (yRatio > (1.0 - cornerThresholdY))) {
                result = "corner";
            } else if((xRatio > (1.0 - cornerThresholdX)) && (yRatio < cornerThresholdY)) {
                result = "corner";
            } else if((xRatio > (1.0 - cornerThresholdX)) && (yRatio > (1.0 - cornerThresholdY))) {
                result = "corner";
            } else if((xRatio > cornerThresholdX) && (xRatio < (1.0 - cornerThresholdX))
                    && (yRatio > cornerThresholdY) && (yRatio < (1.0 - cornerThresholdY))) {
                result = "center";
            } else {
                result = "edge";
            }
            if(makeProgressToast)
                Toast.makeText(context, "Position: " + result, Toast.LENGTH_LONG).show();
            return result;
        }
        return null;
    }

    public HashMap<String, String> runPipeline(String imgPath) {
        if(mOpenCVLoaded) {
            Mat imgBGR = loadImage(imgPath);
            Mat resultSkin = detectSkin(imgBGR);
            Mat resultNR = noiseReduction(resultSkin);
            MatOfPoint resultContour = findMaxContour(resultNR);
            MatOfInt resultConvexHull = findConvexHull(resultContour);
            ArrayList<Double> boundingRectMetrics = getBoundingRectParams(resultContour);
            HashMap<String, ArrayList<Integer[]>> defectResults = getConvexDefects(resultContour, resultConvexHull, boundingRectMetrics.get(0));
            Point centroid = getCentroid(resultContour);
            String position = getPosition(resultNR.size(), centroid);
            String value = getValue(defectResults, boundingRectMetrics);

            HashMap<String, String> result = new HashMap<>();
            result.put("value", value);
            result.put("position", position);
            if(makeProgressToast)
                Toast.makeText(context, value + ", " + position, Toast.LENGTH_SHORT).show();
            return result;
        }
        return null;
    }

    public String getValue(HashMap<String, ArrayList<Integer[]>> defects, ArrayList<Double> contourParams) {
        if(defects.get("long").size() >= 1) {
            int value = defects.get("long").size() + 1;
            if(value > 5)
                value = 5;
            return String.valueOf(value);
        }
        if((contourParams.get(2).doubleValue() > 0.7) || (contourParams.get(1).doubleValue() < 1.3)) {
            return String.valueOf(0);
        }
        return String.valueOf(1);
    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(context) {
        @Override
        public void onManagerConnected(int status) {
            super.onManagerConnected(status);
            switch(status) {
                case LoaderCallbackInterface.SUCCESS:
                    CVOps.this.mOpenCVLoaded = true;
                    Toast.makeText(context, "OpenCV loaded", Toast.LENGTH_SHORT).show();
            }
        }
    };
}
