package com.blogspot.mathjoy.hypervision;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.Button;

public class HyperView extends View implements OnTouchListener {
    double shift = 0;
    double depth3D = 1.125;
    double depth4D = 1.5;
    double frameRate = 60.0;
    Paint pointPaint = new Paint();
    Paint linePaint = new Paint();
    Paint pointPaint2 = new Paint();
    Paint linePaint2 = new Paint();
    static ArrayList<Point> originalPoints = new ArrayList<>();
    static ArrayList<Point> points = new ArrayList<>();
    ArrayList<Point> points2 = new ArrayList<>();
    static ArrayList<Line> lines = new ArrayList<>();
    int dimension = 4;
    int numPoints = (int) Math.pow(2, dimension);
    double size;
    double sizeAdjust = 1;
    double panX;
    double panY;
    int pointThickness = 4;
    double prevX;
    double prevY;
    double currentX;
    double currentY;
    double velocityX;
    double velocityY;
    boolean touching = false;
    int down = 0;
    public int rotateDim = 3;
    public static final int OFF_3D = 0;
    public static final int RED_CYAN_3D = 1;
    public static final int CROSS_EYE_3D = 2;
    public static final int PARALLEL_3D = 3;
    int stereo3D = OFF_3D;
    double rotate3DMagnitude = 7;
    double rotate3D;
    static double rotate3DAdjust = 0;
    boolean setup = true;
    DrawPoint2 drawPoint2;
    DrawLine2 drawLine2;
    Path path = new Path();
    private Bitmap bitmap;
    Canvas myCanvas;
    private double dpi;
    boolean expand = false;
    boolean shrink = false;
    boolean respondToTouch = true;
    GestureDetector gestureDetector;

    public HyperView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.setDrawingCacheEnabled(true);
        initialSetup();
        gestureDetector = new GestureDetector(context, new GestureListener());
    }

    public void initialSetup() {
        dpi = getResources().getDisplayMetrics().xdpi;
        if (points.size() == 0) {
            for (int pointIndex = 0; pointIndex < numPoints; pointIndex++) {
                String binaryNum = String.format("%" + Integer.toString(dimension) + "s", Integer.toBinaryString(pointIndex)).replace(' ', '0');
                double[] coords = new double[dimension];
                for (int coordDimension = 0; coordDimension < dimension; coordDimension++) {
                    char binaryDigit = binaryNum.charAt(coordDimension);
                    if (binaryDigit == '0') {
                        coords[coordDimension] = -1;
                    } else if (binaryDigit == '1') {
                        coords[coordDimension] = 1;
                    } else {
                        throw new RuntimeException("Error when converting int to binary string.");
                    }
                }
                originalPoints.add(new Point(coords));
            }
            for (Point p : originalPoints) {
                points.add(p.clone());
            }

            for (int lineDimension = 0; lineDimension < dimension; lineDimension++) {
                int groupSize = (int) Math.pow(2, lineDimension);
                for (int pointIndex = 0; pointIndex < numPoints; pointIndex += groupSize * 2) {
                    for (int groupIndex = 0; groupIndex < groupSize; groupIndex++) {
                        lines.add(new Line(pointIndex + groupIndex, pointIndex + groupIndex + groupSize));
                    }
                }
            }
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        long startTime = System.currentTimeMillis();
        if (expand) {
            respondToTouch = false;
            expand();
            refreshSize();
            MainActivity.activity.refreshLeftRDButton();
            MainActivity.activity.refreshRightRDButton();
        } else if (shrink) {
            respondToTouch = false;
            shrink();
            refreshSize();
            MainActivity.activity.refreshLeftRDButton();
            MainActivity.activity.refreshRightRDButton();
        }
        if (setup) {
            setup();
            setup = false;
        }
        if (bitmap == null) {
            bitmap = Bitmap.createBitmap(c.getWidth(), c.getHeight(), Bitmap.Config.ARGB_8888);
            myCanvas = new Canvas(bitmap);
        }
        if (myCanvas != null) {
            drawBackground(myCanvas);
            if (touching) {
                velocityX = down * ((-currentX - -prevX) / size) * 60;
                velocityY = down * ((-currentY - -prevY) / size) * 60;
            }
            rotate(new int[]{1, rotateDim}, velocityX, points);
            rotate(new int[]{0, rotateDim}, velocityY, points);
            points2.clear();
            for (Point p : points) {
                points2.add(p.clone());
            }
            rotate(new int[]{1, 3}, rotate3D, points2);
            prevX = currentX;
            prevY = currentY;
            down = 1;
            for (Line l : lines) {
                drawLine(myCanvas, l);
            }
            for (Point p : points) {
                drawPoint(myCanvas, p);
            }
            for (Line l : lines) {
                drawLine2.doo(myCanvas, l);
            }
            for (Point p : points2) {
                drawPoint2.doo(myCanvas, p);
            }
            long timeTook = System.currentTimeMillis() - startTime;
            if (timeTook < 1000.0 / frameRate) {
                try {
                    Thread.sleep((long) (1000.0 / frameRate - timeTook));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (bitmap != null) {
                c.drawBitmap(bitmap, 0, 0, null);
            }
        }
        invalidate();
    }

    private void setup() {
        refreshSize();
        if (stereo3D == CROSS_EYE_3D) {
            rotate3D = -rotate3DMagnitude;
        } else if (stereo3D != OFF_3D) {
            rotate3D = rotate3DMagnitude;
        } else {
            rotate3D = 0;
        }

        if (stereo3D != OFF_3D) {
            drawPoint2 = new DrawPoint2() {
                @Override
                public void doo(Canvas c, Point p) {
                    double m = Math.pow(depth3D, p.getCoord(2)) * Math.pow(depth4D, p.getCoord(3) - 1);
                    c.drawCircle((float) ((panX + m * size * sizeAdjust * p.getCoord(0)) + shift), (float) (panY + m * size * sizeAdjust * p.getCoord(1)), (float) ((Math.pow(depth3D, Math.pow(depth4D, p.getCoord(3) - 1) * p.getCoord(2))) * pointThickness), pointPaint2);
                }
            };
            drawLine2 = new DrawLine2() {
                Path path = new Path();
                double m1;
                double m2;
                float width1;
                float width2;
                float x1;
                float y1;
                float x2;
                float y2;
                double theta;
                float sinTheta;
                float cosTheta;

                @Override
                public void doo(Canvas c, Line l) {
                    m1 = Math.pow(depth3D, points2.get(l.getStartIndex()).getCoord(2)) * Math.pow(depth4D, points2.get(l.getStartIndex()).getCoord(3) - 1);
                    m2 = Math.pow(depth3D, points2.get(l.getEndIndex()).getCoord(2)) * Math.pow(depth4D, points2.get(l.getEndIndex()).getCoord(3) - 1);
                    //c.drawLine((float) ((panX + m1 * size * sizeAdjust * points2.get(l.getStartIndex()).getCoord(0)) + shift), (float) (panY + m1 * size * sizeAdjust * points2.get(l.getStartIndex()).getCoord(1)), (float) ((panX + m2 * size * sizeAdjust * points2.get(l.getEndIndex()).getCoord(0)) + shift), (float) (panY + m2 * size * sizeAdjust * points2.get(l.getEndIndex()).getCoord(1)), linePaint2);
                    width1 = (float) ((Math.pow(depth3D, Math.pow(depth4D, points2.get(l.getStartIndex()).getCoord(3) - 1) * points2.get(l.getStartIndex()).getCoord(2))) * pointThickness / 2.0);
                    //					width1 = (float) ((Math.pow(depth3D, Math.pow(depth4D, points2.get(l.getStartIndex()).getCoord(3)) * points2.get(l.getStartIndex()).getCoord(2))) * (pointThickness / 2.0));
                    width2 = (float) ((Math.pow(depth3D, Math.pow(depth4D, points2.get(l.getEndIndex()).getCoord(3) - 1) * points2.get(l.getEndIndex()).getCoord(2))) * pointThickness / 2.0);
                    //					width2 = (float) ((Math.pow(depth3D, Math.pow(depth4D, points2.get(l.getEndIndex()).getCoord(3)) * points2.get(l.getEndIndex()).getCoord(2))) * (pointThickness / 2.0));
                    x1 = (float) (panX + m1 * size * sizeAdjust * points2.get(l.getStartIndex()).getCoord(0));
                    y1 = (float) (panY + m1 * size * sizeAdjust * points2.get(l.getStartIndex()).getCoord(1));
                    x2 = (float) (panX + m2 * size * sizeAdjust * points2.get(l.getEndIndex()).getCoord(0));
                    y2 = (float) (panY + m2 * size * sizeAdjust * points2.get(l.getEndIndex()).getCoord(1));
                    theta = 90;
                    if (x2 - x1 != 0) {
                        theta = Math.atan((y2 - y1) / (x2 - x1));
                    }

                    sinTheta = (float) Math.sin(theta);
                    cosTheta = (float) Math.cos(theta);
                    path.reset();
                    path.moveTo((float) (x1 + width1 * sinTheta + shift), y1 - width1 * cosTheta);
                    path.lineTo((float) (x1 - width1 * sinTheta + shift), y1 + width1 * cosTheta);
                    path.lineTo((float) (x2 - width2 * sinTheta + shift), y2 + width2 * cosTheta);
                    path.lineTo((float) (x2 + width2 * sinTheta + shift), y2 - width2 * cosTheta);
                    c.drawPath(path, linePaint2);
                }
            };

        } else {
            drawPoint2 = new DrawPoint2() {
                @Override
                public void doo(Canvas c, Point p) {
                }
            };
            drawLine2 = new DrawLine2() {
                @Override
                public void doo(Canvas c, Line l) {
                }
            };
        }


        if (stereo3D == RED_CYAN_3D) {
            pointPaint.setColor(Color.argb(Color.red(Color.DKGRAY), 255, 0, 0));
            linePaint.setColor(Color.argb(Color.red(Color.DKGRAY), 255, 0, 0));
            pointPaint2.setColor(Color.argb(Color.red(Color.DKGRAY), 0, 255, 255));
            linePaint2.setColor(Color.argb(Color.red(Color.DKGRAY), 0, 255, 255));
        } else {
            pointPaint.setColor(Color.RED);
            linePaint.setColor(Color.GRAY);
            pointPaint2.setColor(Color.RED);
            linePaint2.setColor(Color.GRAY);
        }

        rotate(new int[]{1, 3}, -rotate3D / 2.0 - rotate3DAdjust, points);
        rotate3DAdjust = -rotate3D / 2.0;
        MainActivity.activity.refreshLeftRDButton();
        MainActivity.activity.refreshRightRDButton();
    }


    public void refreshSize() {
        if (stereo3D == OFF_3D || stereo3D == RED_CYAN_3D) {
            if (getWidth() < getHeight()) {
                size = getWidth() / 4.0;
            } else {
                size = getHeight() / 4.0;
            }
            panX = getWidth() / 2.0;
            shift = 0;
        } else if (stereo3D == CROSS_EYE_3D || stereo3D == PARALLEL_3D) {
            if (getWidth() / 2.0 < getHeight()) {
                size = getWidth() / 8.0;
                shift = getWidth() / 2.0;
            } else {
                size = getHeight() / 4.0;
                shift = getHeight();
            }
            panX = (getWidth() - shift) / 2.0;
        }
        panY = getHeight() / 2.0;
        linePaint.setStrokeWidth((float) (size / 20.0));
        linePaint2.setStrokeWidth((float) (size / 20.0));
        pointThickness = (int) (size / 20.0);
    }


    private void drawLine(Canvas c, Line l) {
        double m1 = Math.pow(depth3D, points.get(l.getStartIndex()).getCoord(2)) * Math.pow(depth4D, points.get(l.getStartIndex()).getCoord(3) - 1);
        double m2 = Math.pow(depth3D, points.get(l.getEndIndex()).getCoord(2)) * Math.pow(depth4D, points.get(l.getEndIndex()).getCoord(3) - 1);

        float width1 = (float) ((Math.pow(depth3D, Math.pow(depth4D, points.get(l.getStartIndex()).getCoord(3) - 1) * points.get(l.getStartIndex()).getCoord(2))) * pointThickness / 2.0);
        float width2 = (float) ((Math.pow(depth3D, Math.pow(depth4D, points.get(l.getEndIndex()).getCoord(3) - 1) * points.get(l.getEndIndex()).getCoord(2))) * pointThickness / 2.0);
        float x1 = (float) (panX + m1 * size * sizeAdjust * points.get(l.getStartIndex()).getCoord(0));
        float y1 = (float) (panY + m1 * size * sizeAdjust * points.get(l.getStartIndex()).getCoord(1));
        float x2 = (float) (panX + m2 * size * sizeAdjust * points.get(l.getEndIndex()).getCoord(0));
        float y2 = (float) (panY + m2 * size * sizeAdjust * points.get(l.getEndIndex()).getCoord(1));
        double theta = 90;
        if (x2 - x1 != 0) {
            theta = Math.atan((y2 - y1) / (x2 - x1));
        }
        path.reset();
        float sinTheta = (float) Math.sin(theta);
        float cosTheta = (float) Math.cos(theta);
        path.moveTo(x1 + width1 * sinTheta, y1 - width1 * cosTheta);
        path.lineTo(x1 - width1 * sinTheta, y1 + width1 * cosTheta);
        path.lineTo(x2 - width2 * sinTheta, y2 + width2 * cosTheta);
        path.lineTo(x2 + width2 * sinTheta, y2 - width2 * cosTheta);
        c.drawPath(path, linePaint);
    }

    private void drawBackground(Canvas c) {
        c.drawColor(Color.BLACK);
    }

    private void drawPoint(Canvas c, Point p) {
        double m = Math.pow(depth3D, p.getCoord(2)) * Math.pow(depth4D, p.getCoord(3) - 1);
        c.drawCircle((float) (panX + m * size * sizeAdjust * p.getCoord(0)), (float) (panY + m * size * sizeAdjust * p.getCoord(1)), (float) ((Math.pow(depth3D, Math.pow(depth4D, p.getCoord(3) - 1) * p.getCoord(2))) * pointThickness), pointPaint);
    }

    public void rotate(int[] axes, double degrees, List<Point> points) {
        if (axes.length != dimension - 2) {
            throw new IllegalArgumentException("You can't rotate a " + dimension + " dimensional object around " + axes.length + " dimensions!");
        }
        double sin_t = Math.sin(Math.toRadians(degrees));
        double cos_t = Math.cos(Math.toRadians(degrees));
        for (int n = 0; n < points.size(); n++) {
            Point point = points.get(n);
            int[] affectedAxes = new int[2];
            int index = 0;
            for (int i = 0; i < axes.length + 2; i++) {
                boolean match = false;
                for (int axis : axes) {
                    if (axis == i) {
                        match = true;
                    }
                }
                if (!match) {
                    affectedAxes[index] = i;
                    index++;
                }
            }
            double first = point.getCoord(affectedAxes[0]);
            double second = point.getCoord(affectedAxes[1]);
            point.setCoord(affectedAxes[0], first * cos_t - second * sin_t);
            point.setCoord(affectedAxes[1], second * cos_t + first * sin_t);
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (respondToTouch) {
            gestureDetector.onTouchEvent(event);
            if (event.getAction() != MotionEvent.ACTION_MOVE) {
                down = 0;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                touching = false;
            } else {
                touching = true;
            }
            currentX = event.getX();
            currentY = event.getY();
            return true;
        }
        return false;
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            MainActivity.activity.switchRotateDimension();
            return true;
        }
    }

    public interface DrawPoint2 {
        void doo(Canvas c, Point p);
    }

    public interface DrawLine2 {
        void doo(Canvas c, Line l);
    }

    boolean startExpand = true;
    long startExpandTime;
    int duration = 250;
    int distance;
    int initialOuterLayoutLeft;
    int initialOuterLayoutRight;
    int initialInnerLayoutRight;
    int initialButtonLayoutRight;
    int initialButtonWidth;
    int initialRight;

    private void expand() {
        if (startExpand) {
            distance = MainActivity.activity.settingsWidth;
            initialOuterLayoutLeft = MainActivity.activity.findViewById(R.id.outerHyperViewLayout).getLeft();
            initialOuterLayoutRight = MainActivity.activity.findViewById(R.id.outerHyperViewLayout).getRight();
            initialInnerLayoutRight = MainActivity.activity.findViewById(R.id.innerHyperViewLayout).getRight();
            initialButtonLayoutRight = MainActivity.activity.findViewById(R.id.buttonLayout).getRight();
            initialButtonWidth = MainActivity.activity.findViewById(R.id.button3D1).getLayoutParams().width;
            initialRight = getRight();
            startExpand = false;
            startExpandTime = System.currentTimeMillis();
        } else if (System.currentTimeMillis() - startExpandTime < duration) {
            int currentDistance = (int) ((((double) (System.currentTimeMillis() - startExpandTime)) / duration) * distance);
            MainActivity.activity.findViewById(R.id.outerHyperViewLayout).setLeft(initialOuterLayoutLeft - currentDistance);
            MainActivity.activity.findViewById(R.id.outerHyperViewLayout).setRight(initialOuterLayoutRight + currentDistance);
            MainActivity.activity.findViewById(R.id.innerHyperViewLayout).setRight(initialInnerLayoutRight + currentDistance);
            MainActivity.activity.findViewById(R.id.buttonLayout).setRight(initialButtonLayoutRight + currentDistance);
            setRight(initialRight + currentDistance);
        } else {
            stopExpand();
        }
    }

    public void stopExpand() {
        startExpand = true;
        expand = false;
        respondToTouch = true;
    }

    boolean startShrink = true;
    long startShrinkTime;

    private void shrink() {
        if (startShrink) {
            distance = MainActivity.activity.settingsWidth;
            initialButtonWidth = MainActivity.activity.findViewById(R.id.button3D1).getLayoutParams().width;
            startShrink = false;
            startShrinkTime = System.currentTimeMillis();
        } else if (System.currentTimeMillis() - startShrinkTime < duration) {
            int currentDistance = (int) ((((double) (System.currentTimeMillis() - startShrinkTime)) / duration) * distance);
            MainActivity.activity.findViewById(R.id.outerHyperViewLayout).setLeft(initialOuterLayoutLeft + currentDistance);
            setRight(initialRight - currentDistance);
        } else {
            MainActivity.activity.findViewById(R.id.outerHyperViewLayout).setLeft(initialOuterLayoutLeft + distance);
            setRight(initialRight - distance);
            stopShrink();
        }
    }

    public void stopShrink() {
        startShrink = true;
        shrink = false;
        respondToTouch = true;
    }
}
