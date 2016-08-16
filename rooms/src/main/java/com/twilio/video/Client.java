package com.twilio.video;

import android.Manifest;
import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.util.Log;

import com.twilio.common.AccessManager;
import com.twilio.video.internal.Logger;
import com.twilio.video.internal.ReLinker;

import java.lang.ref.WeakReference;
import java.util.EnumMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Client allows user to create or participate in Rooms.
 */
public class Client {

    // TODO: Check which of these error codes are still valid
    /**
     * Authenticating your Client failed due to invalid auth credentials.
     */
    public static int INVALID_AUTH_DATA = 100;
    /**
     * The SIP account was invalid.
     */
    public static int INVALID_SIP_ACCOUNT = 102;
    /**
     * There was an error during Client registration.
     */
    public static int CLIENT_REGISTATION_ERROR = 103;
    /**
     * The Conversation was invalid.
     */
    public static int INVALID_CONVERSATION = 105;
    /**
     * The Conversation was terminated due to an unforeseen error.
     */
    public static int CONVERSATION_TERMINATED = 110;
    /**
     * Establishing a media connection with the remote peer failed.
     */
    public static int PEER_CONNECTION_FAILED = 111;
    /**
     * The client disconnected unexpectedly.
     */
    public static int CLIENT_DISCONNECTED = 200;
    /**
     * Too many active Conversations.
     */
    public static int TOO_MANY_ACTIVE_CONVERSATIONS = 201;
    /**
     * A track was created with constraints that could not be satisfied.
     */
    public static int TRACK_CREATION_FAILED = 207;
    /**
     * Too many tracks were added to the local media.
     *
     * @note: The current maximum is one video track at a time.
     */
    public static int TOO_MANY_TRACKS = 300;
    /**
     * An invalid video capturer was added to the local media
     *
     * @note: At the moment, only {@link CameraCapturer} is supported.
     */
    public static int INVALID_VIDEO_CAPTURER = 301;
    /**
     * An attempt was made to add or remove a track that is already being operated on.
     *
     * @note: Retry your request at a later time.
     */
    public static int TRACK_OPERATION_IN_PROGRESS = 303;
    /**
     * An attempt was made to remove a track that has already ended.
     *
     * @note: The video track is in the {@link MediaTrackState} ENDED state.
     */
    public static int INVALID_VIDEO_TRACK_STATE = 305;

    private static final String[] REQUIRED_PERMISSIONS = {
            // Required permissions granted upon install
            Manifest.permission.INTERNET,
            Manifest.permission.MODIFY_AUDIO_SETTINGS,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_WIFI_STATE
    };

    private static LogLevel level = LogLevel.OFF;
    private static Map<LogModule, LogLevel> moduleLogLevel = new EnumMap(LogModule.class);
    private static volatile boolean libraryIsLoaded = false;
    private static final Logger logger = Logger.getLogger(Client.class);

    private final Handler handler;
    private final Context applicationContext;
    private AccessManager accessManager;
    private long nativeClientContext;
    // Using listener native handle as key
    private Map<Long, WeakReference<Room>> roomMap = new ConcurrentHashMap<>();

    public Client(Context context, AccessManager accessManager) {
        if (context == null) {
            throw new NullPointerException("applicationContext must not be null");
        }
        if (accessManager == null) {
            throw new NullPointerException("accessManager must not be null");
        }

        this.applicationContext = context.getApplicationContext();
        this.accessManager = accessManager;
        this.handler = Util.createCallbackHandler();

        checkPermissions(context);

        if (!libraryIsLoaded) {
            ReLinker.loadLibrary(this.applicationContext, "jingle_peerconnection_so");
            libraryIsLoaded = true;
        }

        /*
         * The user may have set the log level prior to the native library being loaded.
         * Attempt to set the core log level now that the native library has loaded.
         */
        trySetCoreLogLevel(level.ordinal());

        /*
         * It is possible that the user has tried to set the log level for a specific module
         * before the library has loaded. Here we apply the log level for the module because we
         * know the native library is available
         */
        for (LogModule module : moduleLogLevel.keySet()) {
            trySetCoreModuleLogLevel(module.ordinal(), moduleLogLevel.get(module).ordinal());
        }

        if (!nativeInitialize(applicationContext)) {
            throw new RuntimeException("Failed to initialize the Video Client");
        }
        nativeClientContext = nativeCreateClient(accessManager, null);

    }

    /**
     * Sets the audio output speaker for the device.
     * <p/>
     * Bluetooth headset is not supported.
     * <p/>
     * To use volume up/down keys call
     * 'setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);' in your Android Activity.
     *
     * @param audioOutput that should be used by the system
     */
    public void setAudioOutput(AudioOutput audioOutput) {
        logger.d("setAudioOutput");
        AudioManager audioManager = (AudioManager) applicationContext.getSystemService(Context.AUDIO_SERVICE);
        if (audioOutput == AudioOutput.SPEAKERPHONE) {
            audioManager.setSpeakerphoneOn(true);
        } else {
            audioManager.setSpeakerphoneOn(false);
        }
    }

    /**
     * Audio output speaker for the current client device
     *
     * @return audio output speaker
     */
    public AudioOutput getAudioOutput() {
        logger.d("getAudioOutput");
        AudioManager audioManager = (AudioManager) applicationContext.getSystemService(Context.AUDIO_SERVICE);
        return audioManager.isSpeakerphoneOn() ? AudioOutput.SPEAKERPHONE : AudioOutput.HEADSET;
    }

    public Room connect(Room.Listener roomListener) {
        if (roomListener == null) {
            throw new NullPointerException("roomListener must not be null");
        }
        ConnectOptions connectOptions = new ConnectOptions.Builder().build();
        return connect(connectOptions, roomListener);
    }

    public Room connect(ConnectOptions connectOptions, Room.Listener roomListener) {
        if (connectOptions == null) {
            throw new NullPointerException("connectOptions must not be null");
        }
        if (roomListener == null) {
            throw new NullPointerException("roomListener must not be null");
        }
        InternalRoomListenerHandle roomListenerHandle = new InternalRoomListenerHandle(roomListener);
        Room room;
        synchronized (roomListenerHandle) {
            long nativeRoomHandle = nativeConnect(
                    nativeClientContext, roomListenerHandle.get(), connectOptions);
            room = new Room(nativeRoomHandle, connectOptions.getName());
            room.setState(Room.State.CONNECTING);
            roomMap.put(roomListenerHandle.get(), new WeakReference<Room>(room));
        }

        return room;
    }

    /**
     * Returns the version of the Rooms SDK.
     *
     * @return the version of the SDK
     */
    public static String getVersion() {
        return BuildConfig.VERSION_NAME;
    }

    /**
     * Gets the logging level for messages logged by the Rooms SDK.
     *
     * @return the logging level
     */
    public static LogLevel getLogLevel() {
        return LogLevel.values()[tryGetCoreLogLevel()];
    }

    /**
     * Sets the logging level for messages logged by the Rooms SDK.
     *
     * @param level The logging level
     */
    public static void setLogLevel(LogLevel level) {
        setSDKLogLevel(level);
        trySetCoreLogLevel(level.ordinal());
        // Save the log level
        Client.level = level;
    }

    /**
     * Sets the logging level for messages logged by a specific module.
     *
     * @param module The module for this log level
     * @param level  The logging level
     */
    public static void setModuleLogLevel(LogModule module, LogLevel level) {
        if (module == LogModule.PLATFORM) {
            setSDKLogLevel(level);
        }
        trySetCoreModuleLogLevel(module.ordinal(), level.ordinal());
        // Save the module log level
        Client.moduleLogLevel.put(module, level);
    }

    private static void setSDKLogLevel(LogLevel level) {
         /*
         * The Log Levels are defined differently in the Logger
         * which is based off android.util.Log.
         */
        switch (level) {
            case OFF:
                Logger.setLogLevel(Log.ASSERT);
                break;
            case FATAL:
                Logger.setLogLevel(Log.ERROR);
                break;
            case ERROR:
                Logger.setLogLevel(Log.ERROR);
                break;
            case WARNING:
                Logger.setLogLevel(Log.WARN);
                break;
            case INFO:
                Logger.setLogLevel(Log.INFO);
                break;
            case DEBUG:
                Logger.setLogLevel(Log.DEBUG);
                break;
            case TRACE:
                Logger.setLogLevel(Log.VERBOSE);
                break;
            case ALL:
                Logger.setLogLevel(Log.VERBOSE);
                break;
            default:
                // Set the log level to assert/disabled if the value passed in is unknown
                Logger.setLogLevel(Log.ASSERT);
                break;
        }
    }

    /*
     * This is a convenience safety method in the event that the core log level is attempted before
     * initialization.
     *
     * @param level
     */
    private static void trySetCoreLogLevel(int level) {
        if (libraryIsLoaded) {
            nativeSetCoreLogLevel(level);
        }
    }

    /*
     * Convenience safety method for retrieving core log level.
     *
     * @return Core log level or current value that the user has set if the native library has not
     * been loaded
     */
    private static int tryGetCoreLogLevel() {
        return (libraryIsLoaded) ? (nativeGetCoreLogLevel()) : (level.ordinal());
    }

    private static void trySetCoreModuleLogLevel(int module, int level) {
        if (libraryIsLoaded) {
            nativeSetModuleLevel(module, level);
        }
    }

    private static void checkPermissions(Context context) {
        List<String> missingPermissions = new LinkedList<>();
        for (String permission : REQUIRED_PERMISSIONS) {
            if (!Util.permissionGranted(context, permission)) {
                missingPermissions.add(permission);
            }
        }

        if (!missingPermissions.isEmpty()) {
            StringBuilder builder = new StringBuilder(
                    "Your app is missing the following required permissions:");
            for (String permission : missingPermissions)
                builder.append(' ').append(permission);

            throw new RuntimeException(builder.toString());
        }
    }

    class InternalRoomListenerHandle extends NativeHandle implements Room.InternalRoomListener {

        private Room.Listener listener;

        public InternalRoomListenerHandle(Room.Listener listener) {
            super(listener);
            this.listener = listener;
        }

        /*
         * Native Handle
         */
        @Override
        protected native long nativeCreate(Object object);

        @Override
        protected native void nativeFree(long nativeHandle);

        /*
         * InternalRoomListener
         */
        @Override
        public void onConnected() {
            logger.d("onConnected()");
            final Room room = getRoom();
            room.setState(Room.State.CONNECTED);
            handler.post(new Runnable() {
                @Override
                public void run() {
                    InternalRoomListenerHandle.this.listener.onConnected(room);
                }
            });
        }

        @Override
        public void onDisconnected(final int errorCode) {
            logger.d("onDisconnected()");
            final Room room = getRoom();
            room.setState(Room.State.DISCONNECTED);
            room.release();

            handler.post(new Runnable() {
                @Override
                public void run() {
                    InternalRoomListenerHandle.this.listener
                            .onDisconnected(room, new RoomsException(errorCode, ""));
                }
            });
        }

        @Override
        public void onConnectFailure(final int errorCode) {
            logger.d("onConnectFailure()");
            final Room room = getRoom();
            room.setState(Room.State.DISCONNECTED);
            handler.post(new Runnable() {
                @Override
                public void run() {
                    InternalRoomListenerHandle.this.listener
                            .onConnectFailure(new RoomsException(errorCode, ""));
                }
            });
        }

        @Override
        public void onParticipantConnected(final Participant participant) {
            logger.d("onParticipantConnected()");
            final Room room = getRoom();

            room.addParticipant(participant);

            handler.post(new Runnable() {
                @Override
                public void run() {
                    InternalRoomListenerHandle.this.listener.onParticipantConnected(room, participant);
                }
            });
        }

        @Override
        public void onParticipantDisconnected(String participantSid) {
            logger.d("onParticipantDisconnected()");
            final Room room = getRoom();

            final Participant participant = room.removeParticipant(participantSid);
            if (participant == null) {
                logger.w("Received participant disconnected callback for non-existent participant");
                return;
            }
            participant.release();

            handler.post(new Runnable() {
                @Override
                public void run() {
                    InternalRoomListenerHandle.this.listener
                            .onParticipantDisconnected(room, participant);
                }
            });
        }

        private synchronized Room getRoom() {
            long nativeRoomListenerHandle = get();
            WeakReference<Room> roomRef = roomMap.get(nativeRoomListenerHandle);
            if (roomRef == null) {
                // TODO: handle error
                logger.w("Room reference is null");
                return null;
            }
            return roomRef.get();
        }
    }

    private native static void nativeSetCoreLogLevel(int level);

    private native static void nativeSetModuleLevel(int module, int level);

    private native static int nativeGetCoreLogLevel();

    private native boolean nativeInitialize(Context context);

    private native long nativeCreateClient(AccessManager accessManager, MediaFactory mediaFactory);

    private native long nativeConnect(long nativeClientDataHandler,
                                      long nativeRoomListenerHandle,
                                      ConnectOptions ConnectOptions);
}
