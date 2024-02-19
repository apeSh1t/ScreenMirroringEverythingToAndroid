package com.harmony;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.RequiresApi;
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import com.huawei.castpluskit.Constant;
import com.huawei.castpluskit.HiSightSurfaceView;
import com.xindawn.R;

public class PlayActivity extends AppCompatActivity {
    private static final String TAG = "SinkTesterPlayActivity";
    private static final int INVALID_NETWORK_QUALITY = -1000;


    private Drawable mDrawableNetworkWorse;
    private Drawable mDrawableNetworkBad;
    private Drawable mDrawableNetworkGeneral;
    private Drawable mVectorAnimDrawableNetworkWorse;
    private Drawable mVectorAnimDrawableNetworkGeneral;
    private Drawable mVectorAnimDrawableNetworkBad;


    private boolean mIsFinishSelfBehavior = true;
    private ImageView mWlanImageView;

    private boolean needVectorAnimShow = true;
    private AnimatedVectorDrawableCompat animatedImageDrawable;
    private AnimatedVectorDrawable animatedVectorDrawable;

    public static HiSightSurfaceView mHiView;
    public static volatile boolean isSurfaceReady = false;


    private SurfaceHolder.Callback mSurfaceHolderCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            Log.d(TAG, "surfaceCreated() called.");
            isSurfaceReady = true;
            sendBroadcast(HarmonyService.BROADCAST_ACTION_PLAY);
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Log.d(TAG, "surfaceChanged() called.");
            isSurfaceReady = true;
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            Log.d(TAG, "surfaceDestroyed() called.");
            isSurfaceReady = false;
            sendBroadcast(HarmonyService.BROADCAST_ACTION_PAUSE);
        }
    };


    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
//            Log.d(TAG, "Broadcast received, action: " + action);
            Log.e(TAG, "onReceive:------------- " + action);
            if (HarmonyService.BROADCAST_ACTION_FINISH_PLAY_ACTIVITY.equals(action)) {
                mIsFinishSelfBehavior = false;
                finish();
            } else if (HarmonyService.BROADCAST_ACTION_NETWORK_QUALITY.equals(action)) {
                int networkQuality = intent.getIntExtra("networkquality", INVALID_NETWORK_QUALITY);
                Log.e(TAG, "onReceive: " + "              ----------------" + networkQuality);
                switch (networkQuality) {
                    case Constant.NETWORK_QUALITY_EXCEPTION:
                        Log.d(TAG, "network exception.");
                        mWlanImageView.setVisibility(View.GONE);
                        networkBreakToast();
                        break;
                    case Constant.NETWORK_QUALITY_WORSE:
                    case Constant.NETWORK_QUALITY_BAD:
                    case Constant.NETWORK_QUALITY_GENERAL:
                    case Constant.NETWORK_QUALITY_GOOD:
                        Log.e(TAG, "networkQuality:---->" + networkQuality);
                        dispatchDrawableSet(networkQuality);
                        break;
                    default:
                        break;
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate() called.");
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN); //去掉信息栏 Remove the information bar.
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_play);

        IntentFilter broadcastFilter = new IntentFilter();
        broadcastFilter.addAction(HarmonyService.BROADCAST_ACTION_FINISH_PLAY_ACTIVITY);
        broadcastFilter.addAction(HarmonyService.BROADCAST_ACTION_NETWORK_QUALITY);

        registerReceiver(mBroadcastReceiver, broadcastFilter);

        mWlanImageView = findViewById(R.id.wlan_imageview);

        mDrawableNetworkWorse = getResources().getDrawable(R.drawable.ic_network_worse_icon);
        mDrawableNetworkBad = getResources().getDrawable(R.drawable.ic_network_bad_icon);
        mDrawableNetworkGeneral = getResources().getDrawable(R.drawable.ic_network_general_icon);

        mVectorAnimDrawableNetworkWorse = getResources().getDrawable(R.drawable.vector_anim_worse_show);
        mVectorAnimDrawableNetworkGeneral = getResources().getDrawable(R.drawable.vector_anim_general_show);
        mVectorAnimDrawableNetworkBad = getResources().getDrawable(R.drawable.vector_anim_bad_show);


        mHiView = (HiSightSurfaceView) findViewById(R.id.HiSightSurfaceView);
        if (mHiView != null) {
            mHiView.setSecure(true);
            SurfaceHolder surfaceHolder = mHiView.getHolder();
            if (surfaceHolder != null) {
                surfaceHolder.addCallback(mSurfaceHolderCallback);
            } else {
                Log.e(TAG, "surfaceHolder is null.");
            }
        } else {
            Log.e(TAG, "mHiView is null.");
        }
        Log.d(TAG, "onCreate() end.");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy() called.");

        if (animatedImageDrawable != null) {
            if (animatedImageDrawable.isRunning()) {
                animatedImageDrawable.stop();
                animatedImageDrawable = null;
            }
        }

        if (mIsFinishSelfBehavior) {
            Log.d(TAG, "Finish is self behavior, send broadcast to service.");
            Intent disconnectIntent = new Intent();
            disconnectIntent.setAction(HarmonyService.BROADCAST_ACTION_DISCONNECT);
            sendBroadcast(disconnectIntent);
        }
        unregisterReceiver(mBroadcastReceiver);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause() called.");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume() called.");
    }

    private void sendBroadcast(String broadcastAction) {
        Intent intent = new Intent();
        intent.setAction(broadcastAction);
        sendBroadcast(intent);
    }



    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void dispatchDrawableSet(int networkQuality ){
        if(networkQuality == Constant.NETWORK_QUALITY_GOOD){
            mWlanImageView.clearAnimation();
            mWlanImageView.setVisibility(View.GONE);
            needVectorAnimShow = true;
        }else {
            mWlanImageView.setVisibility(View.VISIBLE);
            if (needVectorAnimShow) {
                setVectorAnimDrawable(networkQuality);
                needVectorAnimShow = false;
            }else {
                setVectorDrawable(networkQuality);
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void setVectorAnimDrawable(int networkQuality) {
        switch (networkQuality) {
            case Constant.NETWORK_QUALITY_WORSE:
                mWlanImageView.setImageDrawable(mVectorAnimDrawableNetworkWorse);
                startVectorAnimator();
                break;
            case Constant.NETWORK_QUALITY_BAD:
                mWlanImageView.setImageDrawable(mVectorAnimDrawableNetworkBad);
                startVectorAnimator();
                break;
            case Constant.NETWORK_QUALITY_GENERAL:
                mWlanImageView.setImageDrawable(mVectorAnimDrawableNetworkGeneral);
                startVectorAnimator();
            default:
                break;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void startVectorAnimator(){
        if (animatedImageDrawable != null && animatedImageDrawable.isRunning()) {
            animatedImageDrawable.stop();
            animatedImageDrawable = null;
        }
        if(animatedVectorDrawable != null && animatedVectorDrawable.isRunning()){
            animatedVectorDrawable.stop();
            animatedVectorDrawable = null;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
            animatedVectorDrawable = (AnimatedVectorDrawable) mWlanImageView.getDrawable();
            animatedVectorDrawable.start();
        }else {
            animatedImageDrawable = (AnimatedVectorDrawableCompat) mWlanImageView.getDrawable();
            animatedImageDrawable.start();
        }



    }

    private void setVectorDrawable(int networkQuality){
        switch (networkQuality) {
            case Constant.NETWORK_QUALITY_WORSE:
                mWlanImageView.setImageDrawable(mDrawableNetworkWorse);
                break;

            case Constant.NETWORK_QUALITY_BAD:
                mWlanImageView.setImageDrawable(mDrawableNetworkBad);
                break;

            case Constant.NETWORK_QUALITY_GENERAL:
                mWlanImageView.setImageDrawable(mDrawableNetworkGeneral);
                break;

            default:
                break;
        }
    }

    private void networkBreakToast(){
        ImageView imageView = new ImageView(getApplicationContext());
        imageView.setImageDrawable(getResources().getDrawable(R.drawable.ic_toast));
        Toast toast = new Toast(getApplicationContext());
        toast.setGravity(Gravity.BOTTOM,0,10);
        toast.setDuration(Toast.LENGTH_LONG);
        toast.setView(imageView);
        toast.show();
    }

}
