/*
 * This file provided by Facebook is for non-commercial testing and evaluation
 * purposes only.  Facebook reserves all rights not expressly granted.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * FACEBOOK BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
 * ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package samples.zoomable;

import android.annotation.TargetApi;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.Build;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.ImageView;

import java.math.BigDecimal;

import samples.gestures.TransformGestureDetector;

/**
 * Zoomable controller that calculates transformation based on touch events.
 */
public class DefaultZoomableController implements ZoomableController, TransformGestureDetector.Listener
{
    private boolean is_pinch_zooming = false;
    private TransformGestureDetector mGestureDetector;

    private Listener mListener = null;

    protected boolean mIsEnabled = false;
    protected boolean mIsRotationEnabled = false;
    protected boolean mIsScaleEnabled = true;
    protected boolean mIsTranslationEnabled = true;

    protected float mMinScaleFactor = 1.0f;
    protected float mMaxScaleFactor = 3.0f;

    protected final RectF mViewBounds = new RectF(); //is the height and width of the imageview
    protected final RectF mImageBounds = new RectF(); //is the height and width of the actual image inside the imageview
    protected final RectF mTransformedImageBounds = new RectF(); //is the height and width of the image after it is scaled

    protected final Matrix mPreviousTransform = new Matrix();
    protected final Matrix mActiveTransform = new Matrix();
    protected final Matrix mActiveTransformInverse = new Matrix();
    protected final float[] mTempValues = new float[9];

    protected float saveScale = 1f;

//    protected ImageView imageview;

    public DefaultZoomableController(TransformGestureDetector gestureDetector)
    {
        mGestureDetector = gestureDetector;
        mGestureDetector.setListener(this);
    }

    public static DefaultZoomableController newInstance()
    {
        return new DefaultZoomableController(TransformGestureDetector.newInstance());
    }

    @Override
    public void setListener(Listener listener)
    {
        mListener = listener;
    }

    /**
     * Rests the controller.
     * Resets the Matrixes and the Gesture Detecotor
     */
    public void reset()
    {
        mGestureDetector.reset();
        mPreviousTransform.reset();
        mActiveTransform.reset();
    }

    /**
     * Sets whether the controller is enabled or not.
     */
    @Override
    public void setEnabled(boolean enabled)
    {
        mIsEnabled = enabled;
        if (!enabled)
        {
            reset();
        }
    }

    /**
     * Returns whether the controller is enabled or not.
     */
    @Override
    public boolean isEnabled()
    {
        return mIsEnabled;
    }

    /**
     * Sets whether the rotation gesture is enabled or not.
     */
    public void setRotationEnabled(boolean enabled)
    {
        mIsRotationEnabled = enabled;
    }

    /**
     * Gets whether the rotation gesture is enabled or not.
     */
    public boolean isRotationEnabled()
    {
        return mIsRotationEnabled;
    }

    /**
     * Sets whether the scale gesture is enabled or not.
     */
    public void setScaleEnabled(boolean enabled)
    {
        mIsScaleEnabled = enabled;
    }

    /**
     * Gets whether the scale gesture is enabled or not.
     */
    public boolean isScaleEnabled()
    {
        return mIsScaleEnabled;
    }

    /**
     * Sets whether the translation gesture is enabled or not.
     */
    public void setTranslationEnabled(boolean enabled)
    {
        mIsTranslationEnabled = enabled;
    }

    /**
     * Gets whether the translations gesture is enabled or not.
     */
    public boolean isTranslationEnabled()
    {
        return mIsTranslationEnabled;
    }

    /**
     * Sets the image bounds before zoomable transformation is applied.
     */
    @Override
    public void setImageBounds(RectF imageBounds)
    {
        mImageBounds.set(imageBounds);
    }

    /**
     * Sets the view bounds.
     */
    @Override
    public void setViewBounds(RectF viewBounds)
    {
        mViewBounds.set(viewBounds);
    }

    /**
     * Maps point from the view's to the image's relative coordinate system.
     * This takes into account the zoomable transformation.
     */
    public PointF mapViewToImage(PointF viewPoint)
    {
        float[] points = mTempValues;
        points[0] = viewPoint.x;
        points[1] = viewPoint.y;
        mActiveTransform.invert(mActiveTransformInverse);
        mActiveTransformInverse.mapPoints(points, 0, points, 0, 1);
        mapAbsoluteToRelative(points, points, 1);
        return new PointF(points[0], points[1]);
    }

    /**
     * Maps point from the image's relative to the view's coordinate system.
     * This takes into account the zoomable transformation.
     */
    public PointF mapImageToView(PointF imagePoint)
    {
        float[] points = mTempValues;
        points[0] = imagePoint.x;
        points[1] = imagePoint.y;
        mapRelativeToAbsolute(points, points, 1);
        mActiveTransform.mapPoints(points, 0, points, 0, 1);
        return new PointF(points[0], points[1]);
    }

    /**
     * Maps array of 2D points from absolute to the image's relative coordinate system,
     * and writes the transformed points back into the array.
     * Points are represented by float array of [x0, y0, x1, y1, ...].
     *
     * @param destPoints destination array (may be the same as source array)
     * @param srcPoints  source array
     * @param numPoints  number of points to map
     */
    private void mapAbsoluteToRelative(float[] destPoints, float[] srcPoints, int numPoints)
    {
        for (int i = 0; i < numPoints; i++)
        {
            destPoints[i * 2 + 0] = (srcPoints[i * 2 + 0] - mImageBounds.left) / mImageBounds.width();
            destPoints[i * 2 + 1] = (srcPoints[i * 2 + 1] - mImageBounds.top) / mImageBounds.height();
        }
    }

    /**
     * Maps array of 2D points from relative to the image's absolute coordinate system,
     * and writes the transformed points back into the array
     * Points are represented by float array of [x0, y0, x1, y1, ...].
     *
     * @param destPoints destination array (may be the same as source array)
     * @param srcPoints  source array
     * @param numPoints  number of points to map
     */
    private void mapRelativeToAbsolute(float[] destPoints, float[] srcPoints, int numPoints)
    {
        for (int i = 0; i < numPoints; i++)
        {
            destPoints[i * 2 + 0] = srcPoints[i * 2 + 0] * mImageBounds.width() + mImageBounds.left;
            destPoints[i * 2 + 1] = srcPoints[i * 2 + 1] * mImageBounds.height() + mImageBounds.top;
        }
    }

    /**
     * Gets the zoomable transformation
     * Internal matrix is exposed for performance reasons and is not to be modified by the callers.
     */
    @Override
    public Matrix getTransform()
    {
        return mActiveTransform;
    }

    /**
     * Notifies controller of the received touch event.
     */
    @Override
    public boolean onTouchEvent(MotionEvent event)
    {
        if (mIsEnabled)
        {
            return mGestureDetector.onTouchEvent(event);
        }
        return false;
    }

  /* TransformGestureDetector.Listener methods  */

    @Override
    public void onGestureBegin(TransformGestureDetector detector)
    {
    }

    @Override
    public void onGestureUpdate(TransformGestureDetector detector)
    {
        is_pinch_zooming = true;
        mActiveTransform.set(mPreviousTransform);
        if (mIsRotationEnabled)
        {
            float angle = detector.getRotation() * (float) (180 / Math.PI);
            mActiveTransform.postRotate(angle, detector.getPivotX(), detector.getPivotY());
        }
        if (mIsScaleEnabled)
        {
            float scale = detector.getScale();
            mActiveTransform.postScale(scale, scale, detector.getPivotX(), detector.getPivotY());
            System.out.printf("PINCH ZOOM %f %f %f %f  \n", scale, scale, detector.getPivotX(), detector.getPivotY());
        }
        limitScale(detector.getPivotX(), detector.getPivotY());
        if (mIsTranslationEnabled)
        {
            mActiveTransform.postTranslate(detector.getTranslationX(), detector.getTranslationY());
        }
        limitTranslation();
        if (mListener != null)
        {
            mListener.onTransformChanged(mActiveTransform);
        }
    }

    @Override
    public void onGestureEnd(TransformGestureDetector detector)
    {
        mPreviousTransform.set(mActiveTransform);
        is_pinch_zooming = false;
    }

    /**
     * Gets the current scale factor.
     */
    @Override
    public float getScaleFactor()
    {
        mActiveTransform.getValues(mTempValues);
        return mTempValues[Matrix.MSCALE_X];
    }

    /**
     * Doesnt allow the image to be scaled to smaller than mMinScaleFactor which is 1.0f
     *
     * @param pivotX
     * @param pivotY
     */
    private float limitScale(float pivotX, float pivotY)
    {
        float scale = -1.0f;
        float currentScale = getScaleFactor();
        if (currentScale < mMinScaleFactor)
        {
            scale = mMinScaleFactor / currentScale;
            mActiveTransform.postScale(scale, scale, pivotX, pivotY);
        }
        return scale;
    }

    private void limitTranslation()
    {
        RectF bounds = mTransformedImageBounds;
        bounds.set(mImageBounds);
        mActiveTransform.mapRect(bounds);

        float offsetLeft = getOffset(bounds.left, bounds.width(), mViewBounds.width());
        float offsetTop = getOffset(bounds.top, bounds.height(), mViewBounds.height());
        if (offsetLeft != bounds.left || offsetTop != bounds.top)
        {
            mActiveTransform.postTranslate(offsetLeft - bounds.left, offsetTop - bounds.top);
            mGestureDetector.restartGesture();
        }
    }

    private float getOffset(float offset, float imageDimension, float viewDimension)
    {
        float diff = viewDimension - imageDimension;
        return (diff > 0) ? diff / 2 : limit(offset, diff, 0);
    }

    private float limit(float value, float min, float max)
    {
        return Math.min(Math.max(min, value), max);
    }

    public void zoomToImagePoint(float scale, PointF imagePoint)
    {
        //mActiveTransform.set(mPreviousTransform);

        if (mIsScaleEnabled)
        {
            // mActiveTransform.postScale(scale, scale, imagePoint.x, imagePoint.y);
            System.out.printf("ZOOM TO POINT %f %f %f %f \n", scale, scale, imagePoint.x, imagePoint.y);
        }

        //float limitscale = limitScale(imagePoint.x, imagePoint.y);
//
//        limitTranslation();
        if (mListener != null)
        {
            mListener.onTransformChanged(mActiveTransform);
        }

        startScaleAnimation(500, getScaleFactor(), scale, imagePoint);

        //mPreviousTransform.set(mActiveTransform);
        //float targetZoom = (saveScale == mMinScaleFactor) ? mMaxScaleFactor : mMinScaleFactor;

//        DoubleTapZoom doubleTap = new DoubleTapZoom(targetZoom, imagePoint.x, imagePoint.y);
//        compatPostOnAnimation(doubleTap);

//        limitTranslation();
//
//        if (mIsScaleEnabled)
//        {
//            mActiveTransform.postScale(scale, scale, imagePoint.x, imagePoint.y);
//        }
//
//        limitScale(imagePoint.x, imagePoint.y);
//        if (mIsTranslationEnabled)
//        {
//            mActiveTransform.postTranslate(imagePoint.x, imagePoint.y);
//        }
//
//
//
//        if (mListener != null)
//        {
//            mListener.onTransformChanged(mActiveTransform);
//        }
    }

    private Handler mHandler;
    private Runnable mRunnable;
    private Interpolator interpolator = new AccelerateDecelerateInterpolator();

    private void startScaleAnimation(final int total_time, final float zoomii, final float endZoom, final PointF imagePoint)
    {
        float aaa;
        if (zoomii > 1.0f)
        {
            aaa = 1.0f;
        }
        else
        {
            aaa = zoomii;
        }
        final float startZoom = aaa;
        System.out.println("START SCALE " + startZoom + " FINISH SCALE " + endZoom);
        mHandler = new Handler();
        final int fps = 40;
        mRunnable = new Runnable()
        {
            //boolean in handler or dont allow pinch to zoom when doubletap zoom in progress
            int time = 0;
            long startTime = System.currentTimeMillis();
            float zoom = startZoom;

            @Override
            public void run()
            {
                time += fps;
                if (total_time >= time)
                {
                    if (!is_pinch_zooming)
                    {
                        mHandler.postDelayed(mRunnable, fps);
                        mActiveTransform.set(mPreviousTransform);
                        float t = interpolate(startTime, total_time);
                        float deltaScale = round(calculateDeltaScale(t, zoom, endZoom), 2);
                        System.out.println("TIMETTT " + time + " deltaScale " + deltaScale + " zoom " + zoom);
                        zoom = deltaScale;
                        mActiveTransform.postScale(deltaScale, deltaScale, imagePoint.x, imagePoint.y);

                        limitScale(imagePoint.x, imagePoint.y);
                        limitTranslation();

                        if (mListener != null)
                        {
                            mListener.onTransformChanged(mActiveTransform);
                        }
                    }
                }
                else
                {
                    mPreviousTransform.set(mActiveTransform);
                }
            }
        };

        mHandler.postDelayed(mRunnable, fps);
    }

    public static float round(float d, int decimalPlace)
    {
        BigDecimal bd = new BigDecimal(Float.toString(d));
        bd = bd.setScale(decimalPlace, BigDecimal.ROUND_HALF_UP);
        return bd.floatValue();
    }

    private float calculateDeltaScale(float t, float startZoom, float targetZoom)
    {
        float zoom = startZoom + t * (targetZoom - startZoom);

        return zoom / saveScale;
    }

    private float interpolate(long startTime, float ZOOM_TIME)
    {
        long currTime = System.currentTimeMillis();

        float elapsed = (currTime - startTime) / ZOOM_TIME;

        elapsed = Math.min(1f, elapsed);

        return interpolator.getInterpolation(elapsed);
    }

    private float getImageWidth()
    {
        return mImageBounds.width() * saveScale;
    }

    private float getImageHeight()
    {
        return mImageBounds.height() * saveScale;
    }

}


