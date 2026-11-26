package com.zakkerni.app;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Lets the user choose one or more pictures for a photo widget (also used to change them later). */
public class PhotoPickActivity extends Activity {
    private int id = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Bundle x = getIntent().getExtras();
        if (x != null) id = x.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        setResult(RESULT_CANCELED, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id));
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return; }
        if (b != null) return;
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
                .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try { startActivityForResult(pick, 1); }
        catch (Exception e) { startActivityForResult(new Intent(Intent.ACTION_GET_CONTENT).setType("image/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true), 1); }
        Toast.makeText(this, "اختر صورة أو أكثر (حتى ٣٠ صورة)", Toast.LENGTH_SHORT).show();
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        List<Uri> uris = new ArrayList<>();
        if (res == RESULT_OK && data != null) {
            ClipData cd = data.getClipData();
            if (cd != null) for (int i = 0; i < cd.getItemCount() && uris.size() < 30; i++) uris.add(cd.getItemAt(i).getUri());
            else if (data.getData() != null) uris.add(data.getData());
        }
        if (uris.isEmpty()) {
            // keep the widget (showing "add photos") even if the picker was dismissed
            PhotoWidget.refresh(this, id);
            setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id));
            finish(); return;
        }
        File dir = PhotoWidget.dir(this, id); dir.mkdirs();
        File[] old = dir.listFiles(); if (old != null) for (File o : old) o.delete();
        int n = 0;
        for (Uri u : uris) {
            try (InputStream in = getContentResolver().openInputStream(u)) {
                BitmapFactory.Options op = new BitmapFactory.Options(); op.inSampleSize = 1;
                Bitmap bm = BitmapFactory.decodeStream(in);
                if (bm == null) continue;
                float s = Math.min(1f, 1080f / Math.max(bm.getWidth(), bm.getHeight()));
                if (s < 1f) bm = Bitmap.createScaledBitmap(bm, Math.round(bm.getWidth() * s), Math.round(bm.getHeight() * s), true);
                bm = rotate(u, bm);
                try (FileOutputStream os = new FileOutputStream(new File(dir, String.format(java.util.Locale.US, "%03d.jpg", n++)))) { bm.compress(Bitmap.CompressFormat.JPEG, 88, os); }
            } catch (Exception ignored) { }
        }
        getSharedPreferences("photo_w", MODE_PRIVATE).edit().putInt("i" + id, 0).apply();
        PhotoWidget.refresh(this, id);
        Toast.makeText(this, "تمت إضافة " + n + (n == 1 ? " صورة" : " صور") + " — اضغط على الويدجت للتالية", Toast.LENGTH_LONG).show();
        setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id));
        finish();
    }

    /** Respect the camera's EXIF orientation. */
    private Bitmap rotate(Uri u, Bitmap bm) {
        try (InputStream in = getContentResolver().openInputStream(u)) {
            android.media.ExifInterface ex = new android.media.ExifInterface(in);
            int o = ex.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1);
            int deg = o == 6 ? 90 : o == 3 ? 180 : o == 8 ? 270 : 0;
            if (deg == 0) return bm;
            android.graphics.Matrix m = new android.graphics.Matrix(); m.postRotate(deg);
            return Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), m, true);
        } catch (Exception e) { return bm; }
    }
}
