package com.rmal30.housepainter;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.os.Bundle;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    static final int CAMERA_CODE = 38947;
    static final int PHOTO_CODE = 38342;
    Uri photoUri;
    ImageView imageView;
    Bitmap imageBitmap;
    ArrayList<Bitmap> layers = new ArrayList<>();
    boolean imageLoaded;
    int initial = Color.WHITE;
    int fillColor = Color.WHITE;
    int lightColor = Color.WHITE;
    int tempColor;
    int colValue;
    double[] luminanceArr;
    int[] colorArr;
    double li;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        imageView = (ImageView) findViewById(R.id.imageView);
        imageLoaded = false;
        final Button iBtn = (Button) findViewById(R.id.initColor);
        final Button lBtn = (Button) findViewById(R.id.lightColor);
        final Button pBtn = (Button) findViewById(R.id.paintColor);
        iBtn.setBackgroundColor(initial);
        lBtn.setBackgroundColor(lightColor);
        pBtn.setBackgroundColor(fillColor);
        imageView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (imageLoaded) {

                        final ProgressBar progressBar = (ProgressBar) findViewById(R.id.progressBar);
                        progressBar.setMax(100);
                        progressBar.setIndeterminate(true);
                        progressBar.setVisibility(View.VISIBLE);
                        final int x = (int) event.getX();
                        final int y = (int) event.getY();
                        Thread thr = new Thread(){
                            public void run(){
                                colorRegion(x, y);
                                runOnUiThread(new Runnable(){
                                    public void run(){
                                        imageView.setImageBitmap(imageBitmap);
                                        progressBar.setIndeterminate(false);
                                        progressBar.setVisibility(View.GONE);
                                    }
                                });

                            }
                        };
                        thr.start();
                    }
                }
                return true;
            }
        });
        ActivityCompat.requestPermissions(MainActivity.this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                PHOTO_CODE);
    }

    public void colorRegion(int x, int y){
        int width = imageBitmap.getWidth();
        int height = imageBitmap.getHeight();
        imageLoaded = false;
        //Bitmap newbmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Runtime.getRuntime().gc();
        long freeMemory = Runtime.getRuntime().maxMemory() - (Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory());
        if(freeMemory <(long) height*width*8){
            layers.clear();
        }
        Runtime.getRuntime().gc();
        freeMemory = Runtime.getRuntime().maxMemory() - (Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory());
        if(freeMemory <= (long) height*width*5 + 500){
            imageLoaded = true;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "Out of memory", Toast.LENGTH_SHORT).show();
                }
            });
            return;
        }
        Runtime.getRuntime().gc();
        int[] pixels = new int[height*width];
        imageBitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        int rl = Color.red(lightColor);
        int gl = Color.green(lightColor);
        int bl = Color.blue(lightColor);
        li = luminance(Color.red(initial)*rl, Color.green(initial)*gl, Color.blue(initial)*bl);
        double rFactor = ((double) (rl*Color.red(fillColor)))/li;
        double gFactor = ((double) (gl*Color.green(fillColor)))/li;
        double bFactor = ((double) (bl*Color.blue(fillColor)))/li;
        int numPixels = height*width;
        int xPos, yPos;
        ArrayList<Integer> pendingPixelList = new ArrayList<>();
        boolean[] visited = new boolean[numPixels];
        pendingPixelList.add(x+y*width);
        visited[x+y*width] = true;
        System.out.println(System.currentTimeMillis());
        double lu;
        int index, newIndex;
        int color;
        double threshold = 2;
        ArrayList<Integer> tempPixelList;
        while(!pendingPixelList.isEmpty()){
            tempPixelList =  new ArrayList<>();
            while(pendingPixelList.size()>0){
                index = pendingPixelList.get(0);
                xPos = index%width;
                yPos = index/width;
                color = colorArr[index];
                lu = luminanceArr[index];
                pixels[index] =  transformColor(lu, rFactor, gFactor, bFactor);
                pendingPixelList.remove(0);
                if(xPos<width-1){
                    newIndex = index+1;
                    addPixelsToQueue(colorArr, newIndex, threshold, color, visited, tempPixelList);
                }
                if(yPos<height-1){
                    newIndex = index+width;
                    addPixelsToQueue(colorArr, newIndex, threshold, color, visited, tempPixelList);
                }
                if(xPos>0){
                    newIndex = index-1;
                    addPixelsToQueue(colorArr, newIndex, threshold, color, visited, tempPixelList);
                }
                if(yPos>0){
                    newIndex = index-width;
                    addPixelsToQueue(colorArr, newIndex, threshold, color, visited, tempPixelList);
                }

            }
            pendingPixelList.addAll(tempPixelList);
        }
        System.out.println(System.currentTimeMillis());
        //newbmp.setPixels(pixels, 0, width, 0, 0, width, height);
        imageBitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        System.out.println(System.currentTimeMillis());
        freeMemory = Runtime.getRuntime().maxMemory() - (Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory());
        if(freeMemory <(long) numPixels*10){
            layers.clear();
        }

        Bitmap copy = imageBitmap.copy(imageBitmap.getConfig(), imageBitmap.isMutable());
        layers.add(copy);

        imageLoaded = true;
    }

    public void addPixelsToQueue(int[] pixels, int index, double threshold, int color, boolean[] visited, ArrayList<Integer> tempPixelList){
        int color2 = pixels[index];
        int dR =  Color.red(color)-Color.red(color2);
        int dG =  Color.green(color)-Color.green(color2);
        int dB =  Color.blue(color)-Color.blue(color2);
        double diff = luminance(dR*dR,dG*dG,dB*dB);
        if(diff<threshold && !visited[index]){
            tempPixelList.add(index);
            visited[index] = true;
        }
    }
/*
    public void edgeDetect(){
        int size = luminanceArr.length;
        int height = imageBitmap.getHeight();
        int width = imageBitmap.getWidth();
        double sum;
        int count;
        int colorLevel;
        int[] pixels = new int[size];
        boolean right, left, up, down;
        Bitmap newbmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        for(int j=0; j<height; j++){
            for(int i=0; i<width; i++){
                sum=0; count = 0;
                right = i<width-1;
                left = i>0; up = j>0;
                down = j<height-1;
                if(right){
                    sum-=luminanceArr[j*width+i+1];
                    count++;
                    if(down){
                        sum-=luminanceArr[(j+1)*width+i+1];
                        count++;
                    }
                    if(up){
                        sum-=luminanceArr[(j-1)*width+i+1];
                        count++;
                    }
                }
                if(left){
                    sum-=luminanceArr[j*width+i-1];
                    count++;
                    if(down){
                        sum-=luminanceArr[(j+1)*width+i-1];
                        count++;
                    }
                    if(up){
                        sum-=luminanceArr[(j-1)*width+i-1];
                        count++;
                    }
                }
                if(down){
                    count++;
                    sum-=luminanceArr[(j+1)*width+i];
                }
                if(up){
                    count++;
                    sum-=luminanceArr[(j-1)*width+i];
                }
                sum+=luminanceArr[j*width+i]*count;
                colorLevel = (int) Math.min(255,Math.max(0, Math.round(255*sum/luminance(255, 255, 255))));
                pixels[j*width+i] = Color.rgb(colorLevel, colorLevel, colorLevel);
            }
        }
        newbmp.setPixels(pixels, 0, width, 0, 0, width, height);
        imageView.setImageBitmap(newbmp);
    }
*/
    public int transformColor(double l1, double rfactor, double gfactor, double bfactor){
        int red =(int) Math.round(rfactor*l1);
        int green =(int) Math.round(gfactor*l1);
        int blue = (int) Math.round(bfactor*l1);
        if((red>>8)>0){
            red = 255;
        }
        if((green>>8)>0){
            green = 255;
        }
        if((blue>>8)>0){
            blue = 255;
        }
        return rgbColor(red,green,blue);
    }
    public double luminance(int red, int green, int blue){
        return 0.2126*((double) red)+0.7156*((double) green)+0.0722*((double) blue);
    }
    public int rgbColor(int r, int g, int b){
        return -1<<24 | r<<16 | g<<8 | b;
    }
    public void chooseColor(View v) {
        final int viewID = v.getId();
        String help = "";
        tempColor = Color.WHITE;
        switch (viewID) {
            case R.id.initColor:
                help = "Please choose the initial color of the wall or ceiling:";
                tempColor = initial;
                break;
            case R.id.lightColor:
                help = "Please choose the color of the light source:";
                tempColor = lightColor;
                break;
            case R.id.paintColor:
                help = "Please choose a paint color for the walls or ceiling:";
                tempColor = fillColor;
                break;
        }
        final Button btn = (Button) v;
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle("Choose a color:");
        builder.setMessage(help);
        final View colorPicker = getLayoutInflater().inflate(R.layout.color_picker, null);
        final TextView sr = (TextView) colorPicker.findViewById(R.id.rstr);
        final TextView sg = (TextView) colorPicker.findViewById(R.id.gstr);
        final TextView sb = (TextView) colorPicker.findViewById(R.id.bstr);
        colorPicker.setBackgroundColor(tempColor);
        SeekBar sbred = (SeekBar) colorPicker.findViewById(R.id.red);
        final SeekBar sbgreen = (SeekBar) colorPicker.findViewById(R.id.green);
        SeekBar sbblue = (SeekBar) colorPicker.findViewById(R.id.blue);
        int red = Color.red(tempColor);
        int green = Color.green(tempColor);
        int blue = Color.blue(tempColor);

        sbred.setProgress(red);
        sbgreen.setProgress(green);
        sbblue.setProgress(blue);
        sr.setText(String.format("Red: %d", red));
        sg.setText(String.format("Green: %d", green));
        sb.setText(String.format("Blue: %d", blue));
        final int thresh = 180;
        if(red>=thresh){
            setSliderColor(sbred,sr, Color.BLACK);
        }else{
            setSliderColor(sbred,sr, Color.RED);
        }
        if(green>=thresh){
            setSliderColor(sbgreen,sg, Color.BLACK);
        }else{
            setSliderColor(sbgreen,sg, Color.GREEN);
        }
        if(blue>=thresh){
            setSliderColor(sbblue,sb, Color.BLACK);
        }else{
            setSliderColor(sbblue,sb, Color.BLUE);
        }

        sbred.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progressValue, boolean fromUser) {
                colValue = seekBar.getProgress();

                if(colValue>=thresh && Color.red(tempColor)<thresh){
                    setSliderColor(seekBar,sr, Color.BLACK);
                }else if(colValue<thresh && Color.red(tempColor)>=thresh){
                    setSliderColor(seekBar,sr, Color.RED);
                }

                tempColor = Color.rgb(colValue, Color.green(tempColor), Color.blue(tempColor));
                colorPicker.setBackgroundColor(tempColor);
                sr.setText("Red: "+String.valueOf(colValue));
            }

            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        sbgreen.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progressValue, boolean fromUser) {
                colValue = seekBar.getProgress();
                if(colValue>=thresh && Color.green(tempColor)<thresh){
                    setSliderColor(seekBar,sg, Color.BLACK);
                }else if(colValue<thresh && Color.green(tempColor)>=thresh){
                    setSliderColor(seekBar,sg, Color.GREEN);
                }
                tempColor = Color.rgb(Color.red(tempColor), colValue, Color.blue(tempColor));
                colorPicker.setBackgroundColor(tempColor);
                sg.setText("Green: "+ String.valueOf(colValue));
            }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        sbblue.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progressValue, boolean fromUser) {
                colValue = seekBar.getProgress();
                if(colValue>=thresh && Color.blue(tempColor)<thresh){
                    setSliderColor(seekBar,sb, Color.BLACK);
                }else if(colValue<thresh && Color.blue(tempColor)>=thresh){
                    setSliderColor(seekBar,sb, Color.BLUE);
                }
                tempColor = Color.rgb(Color.red(tempColor), Color.green(tempColor), colValue);
                colorPicker.setBackgroundColor(tempColor);
                sb.setText(String.format("Blue: %d", colValue));
            }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        builder.setView(colorPicker);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    btn.setBackgroundColor(tempColor);
                    switch (viewID) {
                        case R.id.initColor:
                            initial = tempColor;
                            break;
                        case R.id.lightColor:
                            lightColor = tempColor;
                            break;
                        case R.id.paintColor:
                            fillColor = tempColor;
                            break;
                        default: break;
                    }
                    dialog.dismiss();
                }
        });
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    public void setSliderColor(SeekBar sb,TextView tv, int color){
        sb.getProgressDrawable().setColorFilter(color, PorterDuff.Mode.SRC_IN);
        if(Build.VERSION.SDK_INT>=16){
            sb.getThumb().setColorFilter(color, PorterDuff.Mode.SRC_IN);
        }
        tv.setTextColor(color);
    }

    public void takePhoto(View v){
        Intent photoIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        if (photoIntent.resolveActivity(getPackageManager()) != null) {
            String fileName = "IMG_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date())+".jpg";
            try {
                int status = ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
                if(status == PackageManager.PERMISSION_GRANTED){
                    File storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                    File image = new File(storageDir, fileName);
                    //photoPath = "file:" + image.getAbsolutePath();
                    if(Build.VERSION.SDK_INT>=21){
                        photoUri = FileProvider.getUriForFile(this,BuildConfig.APPLICATION_ID + ".provider", image);
                    }else{
                        photoUri = Uri.fromFile(image);
                    }
                    photoIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                    startActivityForResult(photoIntent, CAMERA_CODE);
                }else{
                    Toast.makeText(MainActivity.this, "Please enable permission to use external storage", Toast.LENGTH_SHORT).show();
                }

            }catch(Exception e){
               e.printStackTrace();
            }
        }
    }

    public void chooseImage(View v){
        int status = ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if(status == PackageManager.PERMISSION_GRANTED) {
            Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
            photoPickerIntent.setType("image/*");
            startActivityForResult(photoPickerIntent, PHOTO_CODE);
        }else{
            Toast.makeText(MainActivity.this, "Please enable permission to use external storage", Toast.LENGTH_SHORT).show();
        }
    }

    public void share(View v){
        if(layers.size()>0){
            try {
                int status = ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
                if(status == PackageManager.PERMISSION_GRANTED) {
                    Intent photoPickerIntent = new Intent(Intent.ACTION_SEND);
                    photoPickerIntent.setType("*/*");
                    File file = new File(this.getExternalCacheDir().getPath(), "paintedImage.jpg");
                    OutputStream os = new BufferedOutputStream(new FileOutputStream(file));
                    imageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, os);
                    os.close();
                    Uri uri;
                    if(Build.VERSION.SDK_INT>=14){
                        uri = FileProvider.getUriForFile(this,BuildConfig.APPLICATION_ID + ".provider", file);
                    }else{
                        uri = Uri.fromFile(file);
                    }
                    photoPickerIntent.putExtra(Intent.EXTRA_STREAM, uri);
                    startActivity(Intent.createChooser(photoPickerIntent, "Share image ..."));
                }else{
                    Toast.makeText(MainActivity.this, "Please enable permission to use external storage", Toast.LENGTH_SHORT).show();
                }
            }catch(Exception e){
                e.printStackTrace();
            }
        }
    }

protected void onActivityResult(int requestCode, int resultCode, Intent imageReturnedIntent) {
    super.onActivityResult(requestCode, resultCode, imageReturnedIntent);
    switch (requestCode) {
        case CAMERA_CODE:
            if (resultCode == RESULT_OK) {
                loadImage();
            }
            break;

        case PHOTO_CODE:
            if(resultCode == RESULT_OK){
                photoUri = imageReturnedIntent.getData();
                //android.database.Cursor cursor = getContentResolver().query(selectedImage,new String[]{MediaStore.Images.Media.DATA}, null, null, null);
                //cursor.moveToFirst();
                //int columnIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATA);
                //photoPath = "file://"+new File(cursor.getString(columnIndex)).getAbsolutePath();
                //cursor.close();
                loadImage();
            }
            break;
        default:
    }
}

public void loadImage(){
    final ProgressBar progressBar = (ProgressBar) findViewById(R.id.progressBar);
    progressBar.setMax(100);
    progressBar.setProgress(0);
    progressBar.setVisibility(View.VISIBLE);
    Thread thr = new Thread(){
        public void run(){
            processImage(progressBar);
            runOnUiThread(new Runnable(){
                public void run(){
                    imageView.setImageBitmap(imageBitmap);
                    progressBar.setVisibility(View.GONE);
                }
            });

        }
    };
    thr.start();
}

public void undo(View v){
    if(layers.size()>1){
        if(imageLoaded) {
            imageLoaded = false;
            layers.remove(layers.size() - 1);
            Bitmap bitcopy = layers.get(layers.size() - 1);
            if(bitcopy == null){
                return;
            }
            imageBitmap = bitcopy.copy(bitcopy.getConfig(), bitcopy.isMutable());
            imageView.setImageBitmap(imageBitmap);
            imageLoaded = true;
        }
    }else{
        Toast.makeText(this, "Nothing to undo!", Toast.LENGTH_SHORT).show();
    }
}
    public void save(View v){
        if(layers.size()>0){
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Please enter a file name:");
            final EditText input = new EditText(this);
            input.setInputType(InputType.TYPE_CLASS_TEXT);
            builder.setView(input);
            builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    String m_Text = input.getText().toString();
                    MediaStore.Images.Media.insertImage(getContentResolver(), imageBitmap,  m_Text + ".jpg", "");
                    Toast.makeText(MainActivity.this, "Image saved!", Toast.LENGTH_SHORT).show();
                }
            });
            builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            });

            builder.show();
        }else{
            Toast.makeText(this, "Nothing to save, please load an image first!", Toast.LENGTH_SHORT).show();
        }
    }

public void processImage(final ProgressBar progressBar) {
    try {
        layers.clear();
        DisplayMetrics metric = Resources.getSystem().getDisplayMetrics();
        //Bitmap image = MediaStore.Images.Media.getBitmap(MainActivity.this.getContentResolver(), photoUri);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        InputStream ims = MainActivity.this.getContentResolver().openInputStream(photoUri);
        BitmapFactory.decodeStream(ims, null, options);
        double ratio = ((double) options.outWidth) / ((double) options.outHeight);
        float density = MainActivity.this.getResources().getDisplayMetrics().density;
        int width, height;
        if(ratio<1) {
            height = metric.heightPixels - (int) (density * 250);
            width = (int) (ratio * ((double) height));
        }else{
            width = metric.widthPixels - (int) (density * 20);
            height = (int) (((double) width)/ratio);
        }
        long freeMemory = Runtime.getRuntime().maxMemory() - (Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory());
        while(freeMemory < (long) height*width*16){
            width = width/2;
            height = height/2;
        }
        options.inSampleSize = (int) Math.max(Math.floor(options.outWidth/width), 1);
        options.inJustDecodeBounds = false;
        int numPixels = height * width;
        ims.close();
        ims = MainActivity.this.getContentResolver().openInputStream(photoUri);
        freeMemory = Runtime.getRuntime().maxMemory() - (Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory());
        if(freeMemory <(long) options.outHeight*options.outWidth*4 ){
            layers.clear();
        }
        Bitmap image = BitmapFactory.decodeStream(ims, null, options);
        ims.close();
        imageLoaded = false;
        imageBitmap = Bitmap.createScaledBitmap(image, width, height, true);
        imageBitmap = imageBitmap.copy(Bitmap.Config.ARGB_8888, true);
        int xPos, yPos, color2;
        int r, g, b;
        double count;
        int[] pixels = new int[numPixels];
        luminanceArr = new double[numPixels];
        colorArr = new int[numPixels];
        imageBitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        for (int i = 0; i < numPixels; i++) {
            r = 0;
            g = 0;
            b = 0;
            count = 0;
            for (int j = 0; j < 3; j++) {
                for (int k = 0; k < 3; k++) {
                    xPos = (i % width) + j - 1;
                    yPos = (i / width) + k - 1;
                    if (xPos < width - 1 && xPos > 0 && yPos < height - 1 && yPos > 0) {
                        color2 = pixels[xPos + yPos * width];
                        r += Color.red(color2);
                        g += Color.green(color2);
                        b += Color.blue(color2);
                        count += 1;
                    }
                }
            }
            if (i % (numPixels / 50) == 0) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        progressBar.incrementProgressBy(2);
                    }
                });
            }
            r = (int) Math.round(((double) r) / count);
            g = (int) Math.round(((double) g) / count);
            b = (int) Math.round(((double) b) / count);
            colorArr[i] = rgbColor(r, g, b);
            luminanceArr[i] = luminance(r, g, b);
        }
        imageBitmap.setPixels(colorArr, 0, width, 0, 0, width, height);
        layers.add(imageBitmap.copy(imageBitmap.getConfig(), imageBitmap.isMutable()));
        imageLoaded = true;
    } catch (Exception e) {
        e.printStackTrace();
    }
    }

}