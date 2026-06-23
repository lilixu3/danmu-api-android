package com.example.danmuapiapp.xposed;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;

import java.util.ArrayList;

final class DanmuXposedHostBackgrounds {
    private static final String SETTINGS_OVERLAY_TAG = "com.example.danmuapiapp.APP_DANMU_SETTINGS_OVERLAY";

    interface WarningLogger {
        void warn(String message);
    }

    private DanmuXposedHostBackgrounds() {
    }

    static Drawable resolveHostPageBackground(Activity activity, View backgroundAnchor, View cropAnchor, WarningLogger logger) {
        Drawable wall = captureHostWallpaperBackground(activity, cropAnchor, logger);
        if (wall != null) return wall;
        Drawable resourceWall = captureHostWallpaperResourceBackground(activity, cropAnchor, logger);
        if (resourceWall != null) return resourceWall;
        Drawable fromTree = findMeaningfulAncestorBackgroundDrawable(backgroundAnchor);
        if (fromTree != null) return fromTree;
        return buildFallbackHostBackground(activity);
    }

    static Drawable resolveRowTemplateBackground(View backgroundAnchor) {
        Drawable background = backgroundAnchor == null ? null : backgroundAnchor.getBackground();
        if (background != null) {
            Drawable cloned = cloneDrawable(background);
            if (cloned != null) return cloned;
        }
        return buildFallbackRowBackground(backgroundAnchor == null ? null : backgroundAnchor.getContext());
    }

    private static Drawable findMeaningfulAncestorBackgroundDrawable(View backgroundAnchor) {
        ViewParent parent = backgroundAnchor == null ? null : backgroundAnchor.getParent();
        while (parent instanceof View) {
            View view = (View) parent;
            Drawable background = view.getBackground();
            int w = view.getWidth();
            int h = view.getHeight();
            if (background != null && w > 0 && h > 0 && HostBackgroundColorPolicy.isMeaningfulDrawable(background)) {
                Drawable cloned = cloneDrawable(background);
                if (cloned != null) return cloned;
            }
            parent = view.getParent();
        }
        return null;
    }

    private static Drawable captureHostWallpaperBackground(Activity activity, View cropAnchor, WarningLogger logger) {
        try {
            if (activity == null) return null;
            View target = resolveBackgroundCaptureTarget(activity, cropAnchor);
            if (target == null || target.getWidth() <= 0 || target.getHeight() <= 0) return null;
            View wall = findHostWallpaperView(activity);
            if (wall == null || wall.getWidth() <= 0 || wall.getHeight() <= 0) {
                return captureWindowBackground(activity, target, logger);
            }
            Bitmap bitmap = Bitmap.createBitmap(target.getWidth(), target.getHeight(), Bitmap.Config.ARGB_8888);
            bitmap.setDensity(activity.getResources().getDisplayMetrics().densityDpi);
            Canvas canvas = new Canvas(bitmap);
            if (target != wall) {
                int[] wallLoc = new int[2];
                int[] targetLoc = new int[2];
                wall.getLocationOnScreen(wallLoc);
                target.getLocationOnScreen(targetLoc);
                canvas.translate(wallLoc[0] - targetLoc[0], wallLoc[1] - targetLoc[1]);
            }
            wall.draw(canvas);
            BitmapDrawable drawable = new BitmapDrawable(activity.getResources(), bitmap);
            drawable.setTargetDensity(activity.getResources().getDisplayMetrics());
            drawable.setGravity(Gravity.FILL);
            return drawable;
        } catch (Throwable throwable) {
            warn(logger, "capture host wallpaper failed: " + throwable.getMessage());
            return null;
        }
    }

    private static View resolveBackgroundCaptureTarget(Activity activity, View cropAnchor) {
        if (cropAnchor != null && cropAnchor.getWidth() > 0 && cropAnchor.getHeight() > 0) return cropAnchor;
        View content = activity == null ? null : activity.findViewById(android.R.id.content);
        if (content != null && content.getWidth() > 0 && content.getHeight() > 0) return content;
        Window window = activity == null ? null : activity.getWindow();
        View decor = window == null ? null : window.getDecorView();
        return decor != null && decor.getWidth() > 0 && decor.getHeight() > 0 ? decor : null;
    }

    private static Drawable captureWindowBackground(Activity activity, View target, WarningLogger logger) {
        try {
            if (activity == null || target == null || target.getWidth() <= 0 || target.getHeight() <= 0) return null;
            Window window = activity.getWindow();
            View decor = window == null ? null : window.getDecorView();
            if (decor == null || decor.getWidth() <= 0 || decor.getHeight() <= 0) return null;
            Drawable background = decor.getBackground();
            if (background == null || !HostBackgroundColorPolicy.isMeaningfulDrawable(background)) {
                background = findMeaningfulWindowBackgroundDrawable(decor);
            }
            if (background == null || !HostBackgroundColorPolicy.isMeaningfulDrawable(background)) return null;
            return captureScreenAlignedDrawable(activity, background, decor, target);
        } catch (Throwable throwable) {
            warn(logger, "capture window background failed: " + throwable.getMessage());
            return null;
        }
    }

    private static Drawable findMeaningfulWindowBackgroundDrawable(View root) {
        if (root == null) return null;
        ArrayList<View> stack = new ArrayList<>();
        stack.add(root);
        while (!stack.isEmpty()) {
            View view = stack.remove(stack.size() - 1);
            if (view == null || view.getVisibility() != View.VISIBLE) continue;
            if (SETTINGS_OVERLAY_TAG.equals(view.getTag()) || view.getTag() instanceof SettingsOverlayState) continue;
            Drawable background = view.getBackground();
            if (background != null && view.getWidth() > 0 && view.getHeight() > 0 && HostBackgroundColorPolicy.isMeaningfulDrawable(background)) {
                return background;
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = group.getChildCount() - 1; i >= 0; i--) {
                    stack.add(group.getChildAt(i));
                }
            }
        }
        return null;
    }

    private static Drawable captureHostWallpaperResourceBackground(Activity activity, View cropAnchor, WarningLogger logger) {
        try {
            if (activity == null) return null;
            Drawable resourceWall = loadHostWallpaperResource(activity);
            if (resourceWall == null) return null;
            View target = resolveBackgroundCaptureTarget(activity, cropAnchor);
            if (target == null || target.getWidth() <= 0 || target.getHeight() <= 0) return null;
            Window window = activity.getWindow();
            View decor = window == null ? null : window.getDecorView();
            View boundsAnchor = decor != null && decor.getWidth() > 0 && decor.getHeight() > 0 ? decor : target;
            return captureScreenAlignedDrawable(activity, resourceWall, boundsAnchor, target);
        } catch (Throwable throwable) {
            warn(logger, "capture wallpaper resource failed: " + throwable.getMessage());
            return null;
        }
    }

    private static Drawable captureScreenAlignedDrawable(Activity activity, Drawable source, View boundsAnchor, View target) {
        if (activity == null || source == null || boundsAnchor == null || target == null) return null;
        if (boundsAnchor.getWidth() <= 0 || boundsAnchor.getHeight() <= 0 || target.getWidth() <= 0 || target.getHeight() <= 0) return null;
        Bitmap bitmap = Bitmap.createBitmap(target.getWidth(), target.getHeight(), Bitmap.Config.ARGB_8888);
        bitmap.setDensity(activity.getResources().getDisplayMetrics().densityDpi);
        Canvas canvas = new Canvas(bitmap);
        int[] boundsLoc = new int[2];
        int[] targetLoc = new int[2];
        boundsAnchor.getLocationOnScreen(boundsLoc);
        target.getLocationOnScreen(targetLoc);
        canvas.translate(boundsLoc[0] - targetLoc[0], boundsLoc[1] - targetLoc[1]);

        Drawable drawable = cloneDrawable(source);
        boolean drawingOriginal = drawable == null;
        if (drawingOriginal) drawable = source;
        Rect oldBounds = drawingOriginal ? drawable.copyBounds() : null;
        try {
            drawable.setBounds(0, 0, boundsAnchor.getWidth(), boundsAnchor.getHeight());
            drawable.draw(canvas);
        } finally {
            if (drawingOriginal && oldBounds != null) {
                drawable.setBounds(oldBounds);
            }
        }
        BitmapDrawable bitmapDrawable = new BitmapDrawable(activity.getResources(), bitmap);
        bitmapDrawable.setTargetDensity(activity.getResources().getDisplayMetrics());
        bitmapDrawable.setGravity(Gravity.FILL);
        return bitmapDrawable;
    }

    private static View findHostWallpaperView(Activity activity) {
        try {
            ViewGroup content = activity == null ? null : activity.findViewById(android.R.id.content);
            View directLayer = findDirectHostWallpaperLayer(content);
            return directLayer;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static View findDirectHostWallpaperLayer(ViewGroup content) {
        if (content == null) return null;
        for (int i = 0; i < content.getChildCount(); i++) {
            View child = content.getChildAt(i);
            if (child == null || child.getVisibility() != View.VISIBLE) continue;
            String className = child.getClass().getName();
            if (isKnownHostWallpaperLayerClass(className)) return child;
        }
        return null;
    }

    private static boolean isKnownHostWallpaperLayerClass(String className) {
        return "s30".equals(className) || "Q3.k".equals(className);
    }

    private static Drawable loadHostWallpaperResource(Activity activity) {
        if (activity == null) return null;
        try {
            String pkg = activity.getPackageName();
            int id = activity.getResources().getIdentifier("wallpaper_1", "drawable", pkg);
            if (id == 0) return null;
            return activity.getResources().getDrawable(id, activity.getTheme());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Drawable buildFallbackHostBackground(Context context) {
        GradientDrawable d = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[]{0xFFD8F7F1, 0xFFA7E4E0, 0xFF7DCBC1, 0xFF45CFA4}
        );
        d.setShape(GradientDrawable.RECTANGLE);
        return d;
    }

    private static Drawable buildFallbackRowBackground(Context context) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(0x4DFFFFFF);
        if (context != null) {
            float density = context.getResources().getDisplayMetrics().density;
            d.setCornerRadius(16f * density);
        } else {
            d.setCornerRadius(16f);
        }
        d.setStroke(1, 0x22FFFFFF);
        return d;
    }

    private static Drawable cloneDrawable(Drawable drawable) {
        if (drawable == null) return null;
        try {
            Drawable.ConstantState state = drawable.getConstantState();
            if (state != null) return state.newDrawable().mutate();
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void warn(WarningLogger logger, String message) {
        if (logger != null) logger.warn(message);
    }
}
