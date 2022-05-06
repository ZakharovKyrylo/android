package com.example.camera5_video;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

class ScreenDetector  implements Runnable {
    private static final String myLog = "My Log";
    private static final int scaleNum = 5;
    private static int detected = 0;
    private static boolean firstStart = true;

    //private static File screenFile = new File(Environment.DIRECTORY_PICTURES, "newScreen.jpg");
    private static File screenFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "test1.jpg");
    private static String currentPhotoPath  = screenFile.getAbsolutePath() ;
    private static Bitmap oldScreenBitmap;
    private Bitmap newBitmap;
    MainActivity.CameraService myCameras = null;
    private final Image newImage;

    ScreenDetector(Image image, MainActivity.CameraService myCameras) {
        this.newImage =  image;
        this.myCameras = myCameras;
    }

    @Override
    public void run() {
        ByteBuffer buffer = newImage.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        FileOutputStream output = null;
        try {
            output = new FileOutputStream(screenFile);
            output.write(bytes);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            newImage.close();
            if (null != output) {
                try {
                    output.close();
                    setPic();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void setPic() {
        // Get the dimensions of the View
      //  int targetW = newImage.getWidth();
      //  int targetH = newImage.getHeight();

        // Get the dimensions of the bitmap
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;

        BitmapFactory.decodeFile(currentPhotoPath, bmOptions);

        int photoW = bmOptions.outWidth;
        int photoH = bmOptions.outHeight;

        // Determine how much to scale down the image
        int scaleFactor = 5; // Math.max(1, Math.min(photoW/targetW, photoH/targetH));

        // Decode the image file into a Bitmap sized to fill the View
        bmOptions.inJustDecodeBounds = false;
        bmOptions.inSampleSize = scaleFactor;
        bmOptions.inPurgeable = true;

        newBitmap = BitmapFactory.decodeFile(currentPhotoPath, bmOptions);
        if(firstStart){
            oldScreenBitmap = newBitmap;// Bitmap.createScaledBitmap(newBitmap , scaleNum , scaleNum , true);
            firstStart = false;
        }
        Log.i(myLog , "newBitmap" );
     //   newBitmap = Bitmap.createScaledBitmap(newBitmap , scaleNum , scaleNum , true);
        detectMotion();
    }

    public void detectMotion(){
        double difference = getDifferencePercent();
        if (difference > 10) { // customize accuracy
            // motion detected
            Log.i(myLog , "detectMotion  > 10 " );
            //myCameras.startRecButton();
        }
        else Log.i(myLog , "no motion" );
        oldScreenBitmap = newBitmap;
    }

    private double getDifferencePercent(){
        detected = 0;
        for (int x = 0 ; x < newBitmap.getWidth() ; x++){
            for (int y = 0 ; y < newBitmap.getHeight() ; y++){
                detected += pixelDiff(newBitmap.getPixel(x,y) , oldScreenBitmap.getPixel(x , y));
            }
        }
        long maxDif = 3L * 255 * newBitmap.getHeight() * newBitmap.getWidth();
        return 100.0*detected/maxDif;
    }

    public final int pixelDiff(int rgb1, int rgb2) {
        int r1 = rgb1 >> 16 & 255;
        int g1 = rgb1 >> 8 & 255;
        int b1 = rgb1 & 255;
        int r2 = rgb2 >> 16 & 255;
        int g2 = rgb2 >> 8 & 255;
        int b2 = rgb2 & 255;
        return Math.abs(r1 - r2) + Math.abs(g1 - g2) + Math.abs(b1 - b2);
    }
}