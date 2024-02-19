package com.harmony;

import static com.harmony.PlayActivity.mHiView;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.huawei.castpluskit.AuthInfo;
import com.huawei.castpluskit.ConnectRequestChoice;
import com.huawei.castpluskit.Constant;
import com.huawei.castpluskit.DeviceInfo;
import com.huawei.castpluskit.DisplayInfo;
import com.huawei.castpluskit.Event;
import com.huawei.castpluskit.HiSightCapability;
import com.huawei.castpluskit.IEventListener;
import com.huawei.castpluskit.PlayerClient;
import com.huawei.castpluskit.ProjectionDevice;
import com.huawei.castpluskit.TrackControl;

import android.app.Service;

import androidx.annotation.Nullable;

public class HarmonyService extends Service {
    private static final String TAG = "HarmonyService";
    public static final String BROADCAST_ACTION_DISCONNECT = "castplus.intent.action.disconnect";
    public static final String BROADCAST_ACTION_SET_DISCOVERABLE = "castplus.intent.action" +
            ".setdiscoverable";
    public static final String BROADCAST_ACTION_SET_AUTH_MODE = "castplus.intent.action" +
            ".setauthmode";
    public static final String BROADCAST_ACTION_REJECT_CONNECTION = "castplus.intent.action" +
            ".rejectconnection";
    public static final String BROADCAST_ACTION_ALLOW_CONNECTION_ALWAYS = "castplus.intent.action" +
            ".allowconnection"+".always";

    public static final String BROADCAST_ACTION_ALLOW_CONNECTION_ONCE = "castplus.intent.action" +
            ".allowconnection"+".once";

    public static final String BROADCAST_ACTION_PLAY = "castplus.intent.action" +
            ".play";
    public static final String BROADCAST_ACTION_PAUSE = "castplus.intent.action" +
            ".pause";

    public static final String BROADCAST_ACTION_LANDSCAPE = "castplus.intent.action" +
            ".landscape";
    public static final String BROADCAST_ACTION_PORTRAIT = "castplus.intent.action" +
            ".portrait";

    public static final String BROADCAST_ACTION_FINISH_PIN_ACTIVITY = "castplus.intent.action.finishpinactivity";

    public static final String BROADCAST_ACTION_FINISH_PLAY_ACTIVITY = "castplus.intent.action.finishplayactivity";

    public static final String BROADCAST_ACTION_NETWORK_QUALITY = "castplus.intent.action.networkquality";

    private static final int OPTIMIZATION_TAG_CODEC_CONFIGURE_FLAG = 1;
    private static final int OPTIMIZATION_TAG_MEDIA_FORMAT_INTEGER = 2;
    private static final int OPTIMIZATION_TAG_MEDIA_FORMAT_FLOAT = 4;
    private static final int OPTIMIZATION_TAG_MEDIA_FORMAT_LONG = 8;
    private static final int OPTIMIZATION_TAG_MEDIA_FORMAT_STRING = 16;

    private boolean mIsPinShown = false;

    public ProjectionDevice mProjectionDevice;
    private boolean mIsDiscoverable;
    private BluetoothAdapter mBluetoothAdapter;
    private Context mContext;
    private PlayerClient mPlayerClient;
    private CallbackHandler mCallbackHandler;
    private boolean mCastServiceReady = false;

    private int mVideoWidth = 1920;
    private int mVideoHeight = 1080;


    // 获取PlayerClient实例
    public void initPlayerClient() {
        this.mPlayerClient = PlayerClient.getInstance();
    }

    private IEventListener mCallback = new IEventListener.Stub() {
        @Override
        // 上报显示相关事件
        public boolean onDisplayEvent(int i, DisplayInfo displayInfo) {
            return false;
        }

        @Override
        // 上报连接状态
        public boolean onEvent(Event event) {
            int eventId = event.getEventId();
            Log.e(TAG, "onEvent: " + eventId);
            return true;
        }
    };

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "Broadcast received, action: " + action);
            if (BROADCAST_ACTION_DISCONNECT.equals(action)) {
                disconnectDevice();
            } else if (BROADCAST_ACTION_SET_DISCOVERABLE.equals(action)) {
                mIsDiscoverable = intent.getBooleanExtra("discoverable", true);
                setDiscoverable(mIsDiscoverable);
            } else if (BROADCAST_ACTION_SET_AUTH_MODE.equals(action)) {
                boolean needPassword = intent.getBooleanExtra("needpassword", false);
                String password = intent.getStringExtra("password");
                boolean isNewPassword = intent.getBooleanExtra("isnewpassword", false);
                setAuthMode(needPassword, password, isNewPassword);
            } else if (BROADCAST_ACTION_ALLOW_CONNECTION_ALWAYS.equals(action)) {
                if (mPlayerClient != null) {
                    mPlayerClient.setConnectRequestChooseResult(new ConnectRequestChoice(Constant.CONNECT_REQ_CHOICE_ALWAYS, mProjectionDevice));
                } else {
                    Log.e(TAG, "mPlayerClient is null.");
                }
            }else if (BROADCAST_ACTION_ALLOW_CONNECTION_ONCE.equals(action)) {
                if (mPlayerClient != null) {
                    mPlayerClient.setConnectRequestChooseResult(new ConnectRequestChoice(Constant.CONNECT_REQ_CHOICE_ONCE, mProjectionDevice));
                } else {
                    Log.e(TAG, "mPlayerClient is null.");
                }
            }
            else if (BROADCAST_ACTION_REJECT_CONNECTION.equals(action)) {
                if (mPlayerClient != null) {
                    mPlayerClient.setConnectRequestChooseResult(new ConnectRequestChoice(Constant.CONNECT_REQ_CHOICE_REJECT, mProjectionDevice));
                } else {
                    Log.e(TAG, "mPlayerClient is null.");
                }
            } else if (BROADCAST_ACTION_PAUSE.equals(action)) {
                pause();
            } else if (BROADCAST_ACTION_PLAY.equals(action)) {
                startPlay();
            } else if (BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED.equals(action)) {
                setDiscoverable(mIsDiscoverable);
            }
        }
    };

    /**
     * onBind 是 Service 的虚方法，因此我们不得不实现它。
     * 返回 null，表示客服端不能建立到此服务的连接。
     * onBind is the virtual method of Service, and must be implemented
     */
    @Override
    public IBinder onBind(Intent intent) {
        Log.e(TAG, "onBind in");
        return new MyBinder();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.e(TAG, "onCreate called()");
        IntentFilter broadcastFilter = new IntentFilter();
        broadcastFilter.addAction(BROADCAST_ACTION_DISCONNECT);
        broadcastFilter.addAction(BROADCAST_ACTION_SET_DISCOVERABLE);
        broadcastFilter.addAction(BROADCAST_ACTION_SET_AUTH_MODE);
        broadcastFilter.addAction(BROADCAST_ACTION_REJECT_CONNECTION);
        broadcastFilter.addAction(BROADCAST_ACTION_ALLOW_CONNECTION_ALWAYS);
        broadcastFilter.addAction(BROADCAST_ACTION_ALLOW_CONNECTION_ONCE);
        broadcastFilter.addAction(BROADCAST_ACTION_PLAY);
        broadcastFilter.addAction(BROADCAST_ACTION_PAUSE);
        broadcastFilter.addAction(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED);
        registerReceiver(mBroadcastReceiver, broadcastFilter);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mIsDiscoverable = SharedPreferenceUtil.getDiscoverable(this);

        mCallbackHandler = new CallbackHandler(getMainLooper());
        mContext = this;
        // 获取PlayerClient实例
        mPlayerClient = PlayerClient.getInstance();
        // 启动服务
        mPlayerClient.init(mContext);
        // 注册回调接口
        mPlayerClient.registerCallback(mCallback);
    }

    /**
     * 暂停态
     * Paused state
     */
    private void pause() {
        if (mPlayerClient != null) {
            Log.d(TAG, "pause() called.");
            mPlayerClient.pause(new TrackControl(mProjectionDevice.getDeviceId()));
        }
    }

    private void disconnectDevice() {
        Log.d(TAG, "disconnectDevice() called.");
        if (mPlayerClient != null) {
            mPlayerClient.disconnectDevice(mProjectionDevice);
        } else {
            Log.e(TAG, "mPlayerClient is null.");
        }
    }

    /**
     * 开始投屏
     * start projecting
     */
    private void startPlay() {
        if(!mCastServiceReady || !PlayActivity.isSurfaceReady) {
            return;
        }
        if (mPlayerClient != null) {
            mPlayerClient.setHiSightSurface(mHiView.getHolder().getSurface());
            mPlayerClient.play(new TrackControl(mProjectionDevice.getDeviceId(),TrackControl.TRACK_ALL));
            mCastServiceReady = false;
        } else {
            Log.e(TAG, "mPlayerClient is null.");
        }
    }

    private void startPlayActivity() {
        Log.d(TAG, "startPlayActivity() called.");
        Intent intent = new Intent(mContext, PlayActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    /**
     *
     * @param pinCode
     * @param deviceName
     */
    private void startAlertActivity(String pinCode, String deviceName) {
        Log.d(TAG, "startAlertActivity() called.");
        Intent intent = new Intent(mContext, AlertActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("pincode", pinCode);
        intent.putExtra("devicename", deviceName);
        startActivity(intent);
    }

    private void sendBroadcastToActivity(String broadcastAction) {
        Log.d(TAG, "sendBroadcastToActivity() called.");
        Intent intent = new Intent();
        intent.setAction(broadcastAction);
        sendBroadcast(intent);
    }

    /**
     * 设置投屏模式，pin码模式或者密码模式
     * Set the projection mode, pin code mode, or password mode.
     * @param needPassword
     * @param password
     * @param isNewPassword
     */
    private void setAuthMode(boolean needPassword, String password, boolean isNewPassword) {
        Log.d(TAG, "setAuthMode() called.");
        AuthInfo authInfo = null;
        if (needPassword) {
            //TODO check password.
            Log.d(TAG, "password: $" + password + "$");
            authInfo = new AuthInfo(AuthInfo.AUTH_MODE_PWD, password, isNewPassword);
        } else {
            authInfo = new AuthInfo(AuthInfo.AUTH_MODE_GENERIC);
        }
        if (mPlayerClient != null) {
            mPlayerClient.setAuthMode(authInfo);
        } else {
            Log.e(TAG, "mPlayerClient is null.");
        }
    }

    private DeviceInfo getDeviceInfo() {
        Log.d(TAG, "getDeviceInfo() called.");
        String deviceName = null;// = "CastPlusTestDevice";
        if (mBluetoothAdapter != null) {
            deviceName = mBluetoothAdapter.getName();
        }
        if(deviceName == null) {
            deviceName = "CastPlusTestDevice";
        }
        Log.d(TAG, "getDeviceInfo(), name: " + deviceName);
        return new DeviceInfo(deviceName, DeviceInfo.TYPE_TV, DeviceInfo.HW_CAST_MIRROR, "", "", DeviceInfo.PASSIVE_BIND_TAG);

    }

    /**
     * 设置可被发现
     * Set to be discoverable
     * @param isDiscoverable
     */
    private void setDiscoverable(boolean isDiscoverable) {
        Log.d(TAG, "setDiscoverable() called.");
        DeviceInfo deviceInfo = getDeviceInfo();
        if (mPlayerClient != null) {
            mPlayerClient.setDiscoverable(isDiscoverable, deviceInfo);
        } else {
            Log.e(TAG, "mPlayerClient is null.");
        }
    }

    /**
     * 初始化设备能力
     * Initializing Device Capabilities
     * @param screenSize
     * @param videoSize
     * @param framerate
     * @param mode
     */
    private void setCapability(int screenSize, int videoSize, int framerate, int mode) {
        Log.d(TAG, "setCapability() called.");
        HiSightCapability capability = new HiSightCapability(screenSize, videoSize, screenSize, videoSize);
        capability.setVideoFps(framerate);

        //根据平台不同在此处选用HiSightCapability提供的不同方法进行解码器优化设置。
        //Select different methods provided by HiSightCapability to optimize decoder settings based on different platforms.

        //针对MTK平台并且android Q及以下版本，时延优化的配置；其他平台及版本不建议打开
        //Delay optimization configuration for the MTK platform and Android Q and earlier versions;
        //You are advised not to enable this function for other platforms and versions.

        //if ((mode & OPTIMIZATION_TAG_CODEC_CONFIGURE_FLAG) != 0) {
        //  capability.setMediaCodecConfigureFlag(2);
        //}

        if ((mode & OPTIMIZATION_TAG_MEDIA_FORMAT_INTEGER) != 0) {
        }
        if ((mode & OPTIMIZATION_TAG_MEDIA_FORMAT_FLOAT) != 0) {
        }
        if ((mode & OPTIMIZATION_TAG_MEDIA_FORMAT_LONG) != 0) {
        }
        if ((mode & OPTIMIZATION_TAG_MEDIA_FORMAT_STRING) != 0) {
        }
        if (mPlayerClient != null) {
            mPlayerClient.setCapability(capability);
        } else {
            Log.e(TAG, "mPlayerClient is null.");
        }
    }

    private class CallbackHandler extends Handler {
        public CallbackHandler(Looper mainLooper) {
            super(mainLooper);
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg == null) return;
            Log.d(TAG, "msg " + msg.what);
            DisplayInfo displayInfo;
            switch (msg.what) {
                case Constant.EVENT_ID_SERVICE_BIND_SUCCESS: //4001
                    // 标识服务启动成功，在此消息下设置大屏端设备投屏能力、大屏端设备信息、鉴权模式信息；
                    int framerate = 30;
                    int optimizationMode = 1;
                    setCapability(1920, 1080, framerate, optimizationMode);
                    setDiscoverable(mIsDiscoverable);

                    boolean needPassword = SharedPreferenceUtil.getAuthMode(mContext);
                    String password = SharedPreferenceUtil.getPassword(mContext);
                    boolean isNewPassword = false;
                    setAuthMode(needPassword, password, isNewPassword);
                    break;

                case Constant.EVENT_ID_CONNECT_REQ: { //4012
                    // 大屏端跳转到投屏界面，该消息携带手机端设备信息DisplayInfo对象，开发者通过displayInfo.getProjectionDevice()获取手机端设备信息对象
                    Log.e(TAG, "handleMessage: "+"EVENT_ID_CONNECT_REQ" );
                    if (mIsPinShown) {
                        sendBroadcastToActivity(BROADCAST_ACTION_FINISH_PIN_ACTIVITY);
                        mIsPinShown = false;
                    }
                    displayInfo = (DisplayInfo) msg.obj;
                    if (displayInfo != null) {
                        mProjectionDevice = displayInfo.getProjectionDevice();
                        startPlayActivity();
                    } else {
                        Log.e(TAG, "displayInfo is null.");
                    }
                }
                break;
//                case Constant.EVENT_ID_PIN_CODE_SHOW: { // 4010
                      // 标识有设备正在请求连接，大屏端提供界面供用户选择是否允许此次连接，若允许，通过该消息获取PIN码并展示，以供手机端输入PIN进行连接
//                    Log.e(TAG, "handleMessage: "+"EVENT_ID_PIN_CODE_SHOW" );
//                    mIsPinShown = true;
//                    displayInfo = (DisplayInfo) msg.obj;
//                    if ((displayInfo != null) && (mProjectionDevice = displayInfo.getProjectionDevice()) != null) {
//                        String pinCode = displayInfo.getPinCode();
//                        String deviceName = mProjectionDevice.getDeviceName();
//                        startAlertActivity(pinCode,deviceName);
//                    } else {
//                        Log.e(TAG, "displayInfo is null.");
//                    }
//                }
//                break;
                case Constant.EVENT_ID_CONFIRMATION_SHOW: // 4014
                    Log.e(TAG, "handleMessage: "+"EVENT_ID_CONFIRMATION_SHOW" );
                    displayInfo = (DisplayInfo) msg.obj;
                    if ((displayInfo != null) && (mProjectionDevice = displayInfo.getProjectionDevice()) != null) {
                        String deviceName = mProjectionDevice.getDeviceName();
                        startAlertActivity("",deviceName);
                    } else {
                        Log.e(TAG, "displayInfo is null.");
                    }
                    break;
                case Constant.EVENT_ID_PIN_CODE_SHOW_FINISH:
                    Log.e(TAG, "handleMessage: "+"EVENT_ID_PIN_CODE_SHOW_FINISH" );
                    if (mIsPinShown) {
                        sendBroadcastToActivity(BROADCAST_ACTION_FINISH_PIN_ACTIVITY);
                        mIsPinShown = false;
                    }
                    break;

                case Constant.EVENT_ID_DEVICE_CONNECTED:
                    Log.e(TAG, "handleMessage: "+"EVENT_ID_DEVICE_CONNECTED" );
                    break;

                case Constant.EVENT_ID_CASTING:
                    Log.e(TAG, "handleMessage: "+"EVENT_ID_CASTING" );
                    break;

                case Constant.EVENT_ID_PAUSED:
                    // 大屏端可设置Surface对象并开始播放投屏视频流
                    mCastServiceReady = true;
                    startPlay();
                    break;

                case Constant.EVENT_ID_DEVICE_DISCONNECTED:
                    Log.e(TAG, "handleMessage: "+"EVENT_ID_DEVICE_DISCONNECTED" );
                    if (mIsPinShown) {
                        sendBroadcastToActivity(BROADCAST_ACTION_FINISH_PIN_ACTIVITY);
                        mIsPinShown = false;
                    }
                    sendBroadcastToActivity(BROADCAST_ACTION_FINISH_PLAY_ACTIVITY);
                    break;

                case Constant.EVENT_ID_SET_SURFACE:
                    Log.e(TAG, "handleMessage: "+"EVENT_ID_SET_SURFACE" );
                    break;

                case Constant.EVENT_ID_NETWORK_QUALITY: {
                    Log.e(TAG, "handleMessage: "+"EVENT_ID_NETWORK_QUALITY" );
                    displayInfo = (DisplayInfo) msg.obj;
                    if (displayInfo != null) {
                        int networkQuality = displayInfo.getNetworkQuality();
                        Log.d(TAG, "networkquality update: " + networkQuality);
                        Log.d(TAG, "sendBroadcastToActivity() called.");
                        Intent intent = new Intent();
                        intent.setAction(BROADCAST_ACTION_NETWORK_QUALITY);
                        intent.putExtra("networkquality", networkQuality);
                        sendBroadcast(intent);
                    }
                }
                break;
                default:
                    break;
            }
        }
    }

    class MyBinder extends Binder {
        /**
         * 获取Service的方法
         *
         * @return 返回PlayerService
         */
        public HarmonyService getService() {
            return HarmonyService.this;
        }
    }
}
