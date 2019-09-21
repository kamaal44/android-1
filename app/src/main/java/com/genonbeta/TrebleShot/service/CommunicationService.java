/*
 * Copyright (C) 2019 Veli Tasalı
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package com.genonbeta.TrebleShot.service;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.collection.ArrayMap;

import com.genonbeta.CoolSocket.CoolSocket;
import com.genonbeta.TrebleShot.R;
import com.genonbeta.TrebleShot.app.Service;
import com.genonbeta.TrebleShot.config.AppConfig;
import com.genonbeta.TrebleShot.config.Keyword;
import com.genonbeta.TrebleShot.database.AccessDatabase;
import com.genonbeta.TrebleShot.exception.AssigneeNotFoundException;
import com.genonbeta.TrebleShot.exception.ConnectionNotFoundException;
import com.genonbeta.TrebleShot.exception.DeviceNotFoundException;
import com.genonbeta.TrebleShot.exception.TransferGroupNotFoundException;
import com.genonbeta.TrebleShot.fragment.FileListFragment;
import com.genonbeta.TrebleShot.object.DeviceConnection;
import com.genonbeta.TrebleShot.object.NetworkDevice;
import com.genonbeta.TrebleShot.object.PreloadedGroup;
import com.genonbeta.TrebleShot.object.TextStreamObject;
import com.genonbeta.TrebleShot.object.TransferAssignee;
import com.genonbeta.TrebleShot.object.TransferGroup;
import com.genonbeta.TrebleShot.object.TransferObject;
import com.genonbeta.TrebleShot.util.AppUtils;
import com.genonbeta.TrebleShot.util.CommunicationBridge;
import com.genonbeta.TrebleShot.util.CommunicationNotificationHelper;
import com.genonbeta.TrebleShot.util.DynamicNotification;
import com.genonbeta.TrebleShot.util.FileUtils;
import com.genonbeta.TrebleShot.util.HotspotUtils;
import com.genonbeta.TrebleShot.util.NetworkDeviceLoader;
import com.genonbeta.TrebleShot.util.NetworkUtils;
import com.genonbeta.TrebleShot.util.NotificationUtils;
import com.genonbeta.TrebleShot.util.NsdDiscovery;
import com.genonbeta.TrebleShot.util.TransferUtils;
import com.genonbeta.TrebleShot.util.UpdateUtils;
import com.genonbeta.android.database.SQLQuery;
import com.genonbeta.android.database.SQLiteDatabase;
import com.genonbeta.android.database.exception.ReconstructionFailedException;
import com.genonbeta.android.framework.io.DocumentFile;
import com.genonbeta.android.framework.io.LocalDocumentFile;
import com.genonbeta.android.framework.io.StreamInfo;
import com.genonbeta.android.framework.util.Interrupter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StreamCorruptedException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

import fi.iki.elonen.NanoHTTPD;

public class CommunicationService extends Service
{
    public static final String TAG = "CommunicationService";

    public static final String ACTION_FILE_TRANSFER = "com.genonbeta.TrebleShot.action.FILE_TRANSFER";
    public static final String ACTION_CLIPBOARD = "com.genonbeta.TrebleShot.action.CLIPBOARD";
    public static final String ACTION_CANCEL_INDEXING = "com.genonbeta.TrebleShot.action.CANCEL_INDEXING";
    public static final String ACTION_DEVICE_APPROVAL = "com.genonbeta.TrebleShot.action.DEVICE_APPROVAL";
    public static final String ACTION_END_SESSION = "com.genonbeta.TrebleShot.action.END_SESSION";
    public static final String ACTION_START_TRANSFER = "com.genonbeta.intent.action.START_TRANSFER";
    public static final String ACTION_STOP_TRANSFER = "com.genonbeta.TrebleShot.transaction.action.CANCEL_JOB";
    public static final String ACTION_TOGGLE_HOTSPOT = "com.genonbeta.TrebleShot.transaction.action.TOGGLE_HOTSPOT";
    public static final String ACTION_REQUEST_HOTSPOT_STATUS = "com.genonbeta.TrebleShot.transaction.action.REQUEST_HOTSPOT_STATUS";
    public static final String ACTION_HOTSPOT_STATUS = "com.genonbeta.TrebleShot.transaction.action.HOTSPOT_STATUS";
    public static final String ACTION_DEVICE_ACQUAINTANCE = "com.genonbeta.TrebleShot.transaction.action.DEVICE_ACQUAINTANCE";
    public static final String ACTION_SERVICE_STATUS = "com.genonbeta.TrebleShot.transaction.action.SERVICE_STATUS";
    public static final String ACTION_TASK_STATUS_CHANGE = "com.genonbeta.TrebleShot.transaction.action.TASK_STATUS_CHANGE";
    public static final String ACTION_TASK_RUNNING_LIST_CHANGE = "com.genonbeta.TrebleShot.transaction.action.TASK_RUNNNIG_LIST_CHANGE";
    public static final String ACTION_REQUEST_TASK_STATUS_CHANGE = "com.genonbeta.TrebleShot.transaction.action.REQUEST_TASK_STATUS_CHANGE";
    public static final String ACTION_REQUEST_TASK_RUNNING_LIST_CHANGE = "com.genonbeta.TrebleShot.transaction.action.REQUEST_TASK_RUNNING_LIST_CHANGE";
    public static final String ACTION_INCOMING_TRANSFER_READY = "com.genonbeta.TrebleShot.transaction.action.INCOMING_TRANSFER_READY";

    public static final String EXTRA_DEVICE_ID = "extraDeviceId";
    public static final String EXTRA_STATUS_STARTED = "extraStatusStarted";
    public static final String EXTRA_CONNECTION_ADAPTER_NAME = "extraConnectionAdapterName";
    public static final String EXTRA_REQUEST_ID = "extraRequestId";
    public static final String EXTRA_CLIPBOARD_ID = "extraTextId";
    public static final String EXTRA_GROUP_ID = "extraGroupId";
    public static final String EXTRA_IS_ACCEPTED = "extraAccepted";
    public static final String EXTRA_CLIPBOARD_ACCEPTED = "extraClipboardAccepted";
    public static final String EXTRA_HOTSPOT_ENABLED = "extraHotspotEnabled";
    public static final String EXTRA_HOTSPOT_DISABLING = "extraHotspotDisabling";
    public static final String EXTRA_HOTSPOT_NAME = "extraHotspotName";
    public static final String EXTRA_HOTSPOT_KEY_MGMT = "extraHotspotKeyManagement";
    public static final String EXTRA_HOTSPOT_PASSWORD = "extraHotspotPassword";
    public static final String EXTRA_TASK_CHANGE_TYPE = "extraTaskChangeType";
    public static final String EXTRA_TASK_LIST_RUNNING = "extraTaskListRunning";
    public static final String EXTRA_DEVICE_LIST_RUNNING = "extraDeviceListRunning";
    public static final String EXTRA_ENABLE = "extraEnable";
    public static final String EXTRA_TRANSFER_TYPE = "extraTransferType";

    public static final int TASK_STATUS_ONGOING = 0;
    public static final int TASK_STATUS_STOPPED = 1;

    private List<ProcessHolder> mActiveProcessList = new ArrayList<>();
    private CommunicationServer mCommunicationServer = new CommunicationServer();
    private WebShareServer mWebShareServer;
    private Map<Long, Interrupter> mOngoingIndexList = new ArrayMap<>();
    private ExecutorService mSelfExecutor = Executors.newFixedThreadPool(10);
    private NsdDiscovery mNsdDiscovery;
    private CommunicationNotificationHelper mNotificationHelper;
    private WifiManager.WifiLock mWifiLock;
    private MediaScannerConnection mMediaScanner;
    private HotspotUtils mHotspotUtils;
    private android.database.sqlite.SQLiteDatabase mDbInstance;
    private long mTimeTransactionSaved;

    @Override
    public IBinder onBind(Intent intent)
    {
        return null;
    }

    @Override
    public void onCreate()
    {
        super.onCreate();

        mNotificationHelper = new CommunicationNotificationHelper(getNotificationUtils());
        mNsdDiscovery = new NsdDiscovery(getApplicationContext(), getDatabase(), getDefaultPreferences());
        mMediaScanner = new MediaScannerConnection(this, null);
        mHotspotUtils = HotspotUtils.getInstance(this);
        mDbInstance = AppUtils.getDatabase(this).getWritableDatabase();
        mWifiLock = ((WifiManager) getApplicationContext().getSystemService(Service.WIFI_SERVICE))
                .createWifiLock(TAG);

        mMediaScanner.connect();
        mNsdDiscovery.registerService();

        if (getWifiLock() != null)
            getWifiLock().acquire();

        refreshServiceState();

        if (!AppUtils.checkRunningConditions(this) || !mCommunicationServer.start())
            stopSelf();

        if (getHotspotUtils() instanceof HotspotUtils.OreoAPI && Build.VERSION.SDK_INT >= 26)
            ((HotspotUtils.OreoAPI) getHotspotUtils()).setSecondaryCallback(new WifiManager.LocalOnlyHotspotCallback()
            {
                @RequiresApi(api = Build.VERSION_CODES.O)
                @Override
                public void onStarted(WifiManager.LocalOnlyHotspotReservation reservation)
                {
                    super.onStarted(reservation);
                    sendHotspotStatus(reservation.getWifiConfiguration());
                }
            });


        try {
            mWebShareServer = new WebShareServer(this, AppConfig.SERVER_PORT_WEBSHARE);
            mWebShareServer.setAsyncRunner(new WebShareServer.BoundRunner(
                    Executors.newFixedThreadPool(AppConfig.WEB_SHARE_CONNECTION_MAX)));
            mWebShareServer.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to start Web Share Server");
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, final int startId)
    {
        super.onStartCommand(intent, flags, startId);

        if (intent != null)
            Log.d(TAG, "onStart() : action = " + intent.getAction());

        if (intent != null && AppUtils.checkRunningConditions(this)) {
            if (ACTION_FILE_TRANSFER.equals(intent.getAction())) {
                final String deviceId = intent.getStringExtra(EXTRA_DEVICE_ID);
                final long groupId = intent.getLongExtra(EXTRA_GROUP_ID, -1);
                final int notificationId = intent.getIntExtra(NotificationUtils.EXTRA_NOTIFICATION_ID, -1);
                final boolean isAccepted = intent.getBooleanExtra(EXTRA_IS_ACCEPTED, false);

                getNotificationHelper().getUtils().cancel(notificationId);

                try {
                    final NetworkDevice device = new NetworkDevice(deviceId);
                    getDatabase().reconstruct(device);

                    TransferGroup group = new TransferGroup(groupId);
                    getDatabase().reconstruct(group);

                    TransferAssignee assignee = new TransferAssignee(groupId, deviceId,
                            TransferObject.Type.INCOMING);
                    getDatabase().reconstruct(assignee);

                    final DeviceConnection connection = new DeviceConnection(assignee);
                    getDatabase().reconstruct(connection);

                    CommunicationBridge.connect(getDatabase(), client -> {
                        try {
                            CoolSocket.ActiveConnection activeConnection = client.communicate(device, connection);

                            activeConnection.reply(new JSONObject()
                                    .put(Keyword.REQUEST, Keyword.REQUEST_RESPONSE)
                                    .put(Keyword.TRANSFER_GROUP_ID, groupId)
                                    .put(Keyword.TRANSFER_IS_ACCEPTED, isAccepted)
                                    .toString());

                            activeConnection.receive();
                            activeConnection.getSocket().close();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });

                    if (isAccepted)
                        startTransferAsClient(groupId, deviceId, TransferObject.Type.INCOMING);
                    else
                        getDatabase().remove(group);
                } catch (Exception e) {
                    e.printStackTrace();

                    if (isAccepted)
                        getNotificationHelper().showToast(R.string.mesg_somethingWentWrong);
                }
            } else if (ACTION_DEVICE_APPROVAL.equals(intent.getAction())) {
                String deviceId = intent.getStringExtra(EXTRA_DEVICE_ID);
                boolean isAccepted = intent.getBooleanExtra(EXTRA_IS_ACCEPTED, false);
                int notificationId = intent.getIntExtra(NotificationUtils.EXTRA_NOTIFICATION_ID, -1);

                getNotificationHelper().getUtils().cancel(notificationId);

                NetworkDevice device = new NetworkDevice(deviceId);

                try {
                    getDatabase().reconstruct(device);
                    device.isRestricted = !isAccepted;
                    getDatabase().update(device);
                    getDatabase().broadcast();
                } catch (Exception e) {
                    e.printStackTrace();
                    return START_NOT_STICKY;
                }
            } else if (ACTION_CANCEL_INDEXING.equals(intent.getAction())) {
                int notificationId = intent.getIntExtra(NotificationUtils.EXTRA_NOTIFICATION_ID, -1);
                long groupId = intent.getLongExtra(EXTRA_GROUP_ID, -1);

                getNotificationHelper().getUtils().cancel(notificationId);

                Interrupter interrupter = getOngoingIndexList().get(groupId);

                if (interrupter != null)
                    interrupter.interrupt();
            } else if (ACTION_CLIPBOARD.equals(intent.getAction()) && intent.hasExtra(EXTRA_CLIPBOARD_ACCEPTED)) {
                int notificationId = intent.getIntExtra(NotificationUtils.EXTRA_NOTIFICATION_ID, -1);
                long clipboardId = intent.getLongExtra(EXTRA_CLIPBOARD_ID, -1);
                boolean isAccepted = intent.getBooleanExtra(EXTRA_CLIPBOARD_ACCEPTED, false);

                TextStreamObject textStreamObject = new TextStreamObject(clipboardId);

                getNotificationHelper().getUtils().cancel(notificationId);

                try {
                    getDatabase().reconstruct(textStreamObject);

                    if (isAccepted) {
                        ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("receivedText", textStreamObject.text));
                        Toast.makeText(this, R.string.mesg_textCopiedToClipboard, Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else if (ACTION_END_SESSION.equals(intent.getAction())) {
                stopSelf();
            } else if (ACTION_START_TRANSFER.equals(intent.getAction()) && intent.hasExtra(EXTRA_GROUP_ID)
                    && intent.hasExtra(EXTRA_DEVICE_ID) && intent.hasExtra(EXTRA_TRANSFER_TYPE)) {
                long groupId = intent.getLongExtra(EXTRA_GROUP_ID, -1);
                String deviceId = intent.getStringExtra(EXTRA_DEVICE_ID);
                String typeString = intent.getStringExtra(EXTRA_TRANSFER_TYPE);

                try {
                    TransferObject.Type type = TransferObject.Type.valueOf(typeString);
                    ProcessHolder process = findProcessById(groupId, deviceId, type);

                    if (process == null)
                        startTransferAsClient(groupId, deviceId, type);
                    else
                        Toast.makeText(this, getString(R.string.mesg_groupOngoingNotice, process.object.name), Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else if (ACTION_STOP_TRANSFER.equals(intent.getAction()) && intent.hasExtra(EXTRA_GROUP_ID)
                    && intent.hasExtra(EXTRA_DEVICE_ID) && intent.hasExtra(EXTRA_TRANSFER_TYPE)) {
                int notificationId = intent.getIntExtra(NotificationUtils.EXTRA_NOTIFICATION_ID, -1);
                long groupId = intent.getLongExtra(EXTRA_GROUP_ID, -1);
                String deviceId = intent.getStringExtra(CommunicationService.EXTRA_DEVICE_ID);
                String typeString = intent.getStringExtra(EXTRA_TRANSFER_TYPE);

                try {
                    TransferObject.Type type = TransferObject.Type.valueOf(typeString);
                    ProcessHolder processHolder = findProcessById(groupId, deviceId, type);

                    if (processHolder == null) {
                        notifyTaskStatusChange(groupId, deviceId, type, TASK_STATUS_STOPPED);
                        getNotificationHelper().getUtils().cancel(notificationId);
                    } else {
                        processHolder.notification = getNotificationHelper().notifyStuckThread(processHolder);

                        if (!processHolder.interrupter.interrupted()) {
                            processHolder.interrupter.interrupt(true);
                        } else {
                            try {
                                if (processHolder.activeConnection != null
                                        && processHolder.activeConnection.getSocket() != null)
                                    processHolder.activeConnection.getSocket().close();
                            } catch (IOException ignored) {
                            }

                            try {
                                if (processHolder.activeConnection != null && processHolder.activeConnection.getSocket() != null)
                                    processHolder.activeConnection.getSocket().close();
                            } catch (IOException ignored) {
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else if (ACTION_TOGGLE_HOTSPOT.equals(intent.getAction())
                    && (Build.VERSION.SDK_INT < 23 || Settings.System.canWrite(this))) {
                setupHotspot();
            } else if (ACTION_REQUEST_HOTSPOT_STATUS.equals(intent.getAction())) {
                sendHotspotStatus(getHotspotUtils().getConfiguration());
            } else if (ACTION_SERVICE_STATUS.equals(intent.getAction())
                    && intent.hasExtra(EXTRA_STATUS_STARTED)) {
                boolean startRequested = intent.getBooleanExtra(EXTRA_STATUS_STARTED, false);

                if (!startRequested && !hasOngoingTasks()) {
                    Log.d(TAG, "onStartCommand(): Destroy state has been applied");
                    stopSelf();
                }
            } else if (ACTION_REQUEST_TASK_STATUS_CHANGE.equals(intent.getAction())
                    && intent.hasExtra(EXTRA_GROUP_ID) && intent.hasExtra(EXTRA_DEVICE_ID)
                    && intent.hasExtra(EXTRA_TRANSFER_TYPE)) {
                long groupId = intent.getLongExtra(EXTRA_GROUP_ID, -1);
                String deviceId = intent.getStringExtra(EXTRA_DEVICE_ID);
                String typeString = intent.getStringExtra(EXTRA_TRANSFER_TYPE);

                try {
                    TransferObject.Type type = TransferObject.Type.valueOf(typeString);

                    notifyTaskStatusChange(groupId, deviceId, type, isProcessRunning(
                            groupId, deviceId, type) ? TASK_STATUS_STOPPED : TASK_STATUS_ONGOING);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else if (ACTION_REQUEST_TASK_RUNNING_LIST_CHANGE.equals(intent.getAction())) {
                notifyTaskRunningListChange();
            }
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();

        mCommunicationServer.stop();
        mMediaScanner.disconnect();
        mNsdDiscovery.unregisterService();
        mWebShareServer.stop();

        {
            ContentValues values = new ContentValues();

            values.put(AccessDatabase.FIELD_TRANSFERGROUP_ISSHAREDONWEB, 0);
            getDatabase().update(new SQLQuery.Select(AccessDatabase.TABLE_TRANSFERGROUP)
                    .setWhere(String.format("%s = ?", AccessDatabase.FIELD_TRANSFERGROUP_ISSHAREDONWEB),
                            String.valueOf(1)), values);
        }

        if (getHotspotUtils().unloadPreviousConfig()) {
            getHotspotUtils().disable();
            Log.d(TAG, "onDestroy(): Stopping hotspot (previously started)");
        }

        if (getWifiLock() != null && getWifiLock().isHeld()) {
            getWifiLock().release();
            Log.d(TAG, "onDestroy(): Releasing Wi-Fi lock");
        }

        stopForeground(true);

        synchronized (getOngoingIndexList()) {
            for (Interrupter interrupter : getOngoingIndexList().values()) {
                interrupter.interrupt(true);
                Log.d(TAG, "onDestroy(): Ongoing indexing stopped: " + interrupter.toString());
            }
        }

        synchronized (getActiveProcessList()) {
            for (ProcessHolder processHolder : getActiveProcessList()) {
                processHolder.interrupter.interrupt(true);
                Log.d(TAG, "onDestroy(): Killing process: " + processHolder.toString());
            }
        }

        getDefaultPreferences().edit()
                .putInt(Keyword.NETWORK_PIN, -1)
                .apply();

        getDatabase().broadcast();
    }

    private synchronized void addProcess(ProcessHolder processHolder)
    {
        getActiveProcessList().add(processHolder);
    }

    private synchronized void removeProcess(ProcessHolder processHolder)
    {
        getActiveProcessList().remove(processHolder);
    }

    private void broadcastTransferState(ProcessHolder processHolder, boolean isLast)
    {
        long time = System.currentTimeMillis();

		/*if (isLast || time - mTimeTransactionSaved > AppConfig.DEFAULT_NOTIFICATION_DELAY) {
			mTimeTransactionSaved = time;

			if (getDbInstance().inTransaction()) {
				getDbInstance().setTransactionSuccessful();
				getDbInstance().endTransaction();
			}

			if (!isLast)
				getDbInstance().beginTransaction();
		}*/
        boolean delayReached = time - processHolder.lastProcessingTime > AppConfig.DEFAULT_NOTIFICATION_DELAY;

        if (delayReached && !isLast) {
            processHolder.lastProcessingTime = time;

            try {
                getNotificationHelper().notifyFileTransfer(processHolder);

                TransferObject.Flag flag = TransferObject.Flag.IN_PROGRESS;
                flag.setBytesValue(processHolder.currentBytes);

                if (TransferObject.Type.INCOMING.equals(processHolder.type))
                    processHolder.object.setFlag(flag);
                else if (TransferObject.Type.OUTGOING.equals(processHolder.type))
                    processHolder.object.putFlag(processHolder.device.id, flag);

                getDatabase().update(getDbInstance(), processHolder.object, processHolder.group);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (delayReached || isLast)
            getDatabase().broadcast();
    }

    private void handleTransferRequest(final long groupId, final String jsonIndex, final NetworkDevice device,
                                       final DeviceConnection connection, final boolean noPrompt)
    {
        getSelfExecutor().submit(() -> {
            final JSONArray jsonArray;
            final Interrupter interrupter = new Interrupter();
            TransferGroup group = new TransferGroup(groupId);
            TransferAssignee assignee = new TransferAssignee(group, device,
                    TransferObject.Type.INCOMING, connection);
            final DynamicNotification notification = getNotificationHelper().notifyPrepareFiles(group, device);

            notification.setProgress(0, 0, true);

            try {
                jsonArray = new JSONArray(jsonIndex);
            } catch (Exception e) {
                notification.cancel();
                e.printStackTrace();
                return;
            }

            notification.setProgress(0, 0, false);
            boolean usePublishing = false;

            try {
                getDatabase().reconstruct(group);
                usePublishing = true;
            } catch (Exception e) {
                e.printStackTrace();
            }

            getDatabase().publish(group);
            getDatabase().publish(assignee);

            synchronized (getOngoingIndexList()) {
                getOngoingIndexList().put(group.id, interrupter);
            }

            long uniqueId = System.currentTimeMillis(); // The uniqueIds
            List<TransferObject> pendingRegistry = new ArrayList<>();

            for (int i = 0; i < jsonArray.length(); i++) {
                if (interrupter.interrupted())
                    break;

                try {
                    if (!(jsonArray.get(i) instanceof JSONObject))
                        continue;

                    JSONObject requestIndex = jsonArray.getJSONObject(i);

                    if (requestIndex != null
                            && requestIndex.has(Keyword.INDEX_FILE_NAME)
                            && requestIndex.has(Keyword.INDEX_FILE_SIZE)
                            && requestIndex.has(Keyword.INDEX_FILE_MIME)
                            && requestIndex.has(Keyword.TRANSFER_REQUEST_ID)) {

                        TransferObject transferObject = new TransferObject(
                                requestIndex.getLong(Keyword.TRANSFER_REQUEST_ID),
                                groupId,
                                requestIndex.getString(Keyword.INDEX_FILE_NAME),
                                "." + (uniqueId++) + "." + AppConfig.EXT_FILE_PART,
                                requestIndex.getString(Keyword.INDEX_FILE_MIME),
                                requestIndex.getLong(Keyword.INDEX_FILE_SIZE),
                                TransferObject.Type.INCOMING);

                        if (requestIndex.has(Keyword.INDEX_DIRECTORY))
                            transferObject.directory = requestIndex.getString(Keyword.INDEX_DIRECTORY);

                        pendingRegistry.add(transferObject);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            SQLiteDatabase.ProgressUpdater progressUpdater = new SQLiteDatabase.ProgressUpdater()
            {
                long lastNotified = System.currentTimeMillis();

                @Override
                public void onProgressChange(int total, int current)
                {
                    if ((System.currentTimeMillis() - lastNotified) > 1000) {
                        lastNotified = System.currentTimeMillis();
                        notification.updateProgress(total, current, false);
                    }
                }

                @Override
                public boolean onProgressState()
                {
                    return !interrupter.interrupted();
                }
            };

            if (pendingRegistry.size() > 0) {
                if (usePublishing)
                    getDatabase().publish(pendingRegistry, progressUpdater);
                else
                    getDatabase().insert(pendingRegistry, progressUpdater);
            }

            notification.cancel();

            synchronized (getOngoingIndexList()) {
                getOngoingIndexList().remove(group.id);
            }

            if (interrupter.interrupted())
                getDatabase().remove(group);
            else if (pendingRegistry.size() > 0) {
                sendBroadcast(new Intent(ACTION_INCOMING_TRANSFER_READY)
                        .putExtra(EXTRA_GROUP_ID, groupId)
                        .putExtra(EXTRA_DEVICE_ID, device.id));

                if (noPrompt)
                    try {
                        startTransferAsClient(group.id, device.id, TransferObject.Type.INCOMING);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                else
                    getNotificationHelper().notifyTransferRequest(device, group,
                            TransferObject.Type.INCOMING, pendingRegistry);
            }

            getDatabase().broadcast();
        });
    }

    private void handleTransferAsReceiver(ProcessHolder processHolder)
    {
        addProcess(processHolder);
        notifyTaskStatusChange(processHolder.group.id, processHolder.device.id, processHolder.type,
                TASK_STATUS_ONGOING);
        notifyTaskRunningListChange();

        // TODO: 7/27/19 Implement task resuming
        boolean retry = false;

        try {
            TransferUtils.loadGroupInfo(this, processHolder.group, processHolder.assignee);

            while (processHolder.activeConnection.getSocket().isConnected()) {
                processHolder.currentBytes = 0;
                if (processHolder.interrupter.interrupted())
                    break;

                try {
                    TransferObject object = TransferUtils.fetchFirstValidIncomingTransfer(
                            CommunicationService.this, processHolder.group.id);

                    if (object == null) {
                        Log.d(TAG, "handleTransferAsReceiver(): Exiting because there is no " +
                                "pending file instance left");
                        break;
                    } else
                        Log.d(TAG, "handleTransferAsReceiver(): Starting to receive " + object);

                    processHolder.object = object;
                    processHolder.currentFile = FileUtils.getIncomingFile(
                            getApplicationContext(), processHolder.object, processHolder.group);
                    StreamInfo streamInfo = StreamInfo.getStreamInfo(getApplicationContext(),
                            processHolder.currentFile.getUri());
                    processHolder.currentBytes = processHolder.currentFile.length();
                    broadcastTransferState(processHolder, false);

                    {
                        JSONObject reply = new JSONObject()
                                .put(Keyword.TRANSFER_REQUEST_ID, processHolder.object.id)
                                .put(Keyword.RESULT, true);

                        if (processHolder.currentBytes > 0)
                            reply.put(Keyword.SKIPPED_BYTES, processHolder.currentBytes);

                        Log.d(TAG, "handleTransferAsReceiver(): reply: " + reply.toString());
                        processHolder.activeConnection.reply(reply.toString());
                    }

                    {
                        JSONObject response = new JSONObject(processHolder.activeConnection.receive().response);
                        Log.d(TAG, "handleTransferAsReceiver(): receive: " + response.toString());

                        if (!response.getBoolean(Keyword.RESULT)) {
                            if (response.has(Keyword.TRANSFER_JOB_DONE)
                                    && !response.getBoolean(Keyword.TRANSFER_JOB_DONE)) {
                                processHolder.interrupter.interrupt(true);
                                Log.d(TAG, "handleTransferAsReceiver(): Transfer should be closed, babe!");
                                break;
                            } else if (response.has(Keyword.FLAG) && Keyword.FLAG_GROUP_EXISTS.equals(response.getString(Keyword.FLAG))) {
                                if (response.has(Keyword.ERROR) && response.getString(Keyword.ERROR).equals(Keyword.ERROR_NOT_FOUND)) {
                                    processHolder.object.setFlag(TransferObject.Flag.REMOVED);
                                    Log.d(TAG, "handleTransferAsReceiver(): Sender says it does not have the file defined");
                                } else if (response.has(Keyword.ERROR) && response.getString(Keyword.ERROR).equals(Keyword.ERROR_NOT_ACCESSIBLE)) {
                                    processHolder.object.setFlag(TransferObject.Flag.INTERRUPTED);
                                    Log.d(TAG, "handleTransferAsReceiver(): Sender says it can't open the file");
                                } else if (response.has(Keyword.ERROR) && response.getString(Keyword.ERROR).equals(Keyword.ERROR_UNKNOWN)) {
                                    processHolder.object.setFlag(TransferObject.Flag.INTERRUPTED);
                                    Log.d(TAG, "handleTransferAsReceiver(): Sender says an unknown error occurred");
                                }
                            }
                        } else {
                            long sizeChanged = response.has(Keyword.SIZE_CHANGED) ? response.getLong(
                                    Keyword.SIZE_CHANGED) : 0;
                            boolean sizeActuallyChanged = sizeChanged > 0 && processHolder.object.size != sizeChanged;

                            if (sizeActuallyChanged) {
                                Log.d(TAG, "handleTransferAsReceiver(): Sender says the file has a new size");
                                processHolder.object.size = response.getLong(Keyword.SIZE_CHANGED);
                                boolean canContinue = processHolder.currentBytes < 1;

                                processHolder.activeConnection.reply(new JSONObject()
                                        .put(Keyword.RESULT, canContinue)
                                        .toString());

                                if (!canContinue) {
                                    Log.d(TAG, "handleTransferAsReceiver(): The change may broke the previous file which has a length. Cannot take the risk.");
                                    processHolder.object.setFlag(TransferObject.Flag.REMOVED);
                                    continue;
                                }

                                Log.d(TAG, "handleTransferAsReceiver(): receive: " +
                                        processHolder.activeConnection.receive().response);
                            }

                            processHolder.activeConnection.reply(Keyword.STUB);
                            OutputStream outputStream = null;
                            boolean completed = false;

                            try {
                                outputStream = streamInfo.openOutputStream();
                                int readLength;
                                long lastReceivedTime = 0;
                                long timeout = processHolder.activeConnection.getTimeout();
                                byte[] buffer = new byte[AppConfig.BUFFER_LENGTH_DEFAULT];
                                InputStream inputStream = processHolder.activeConnection.getSocket()
                                        .getInputStream();

                                while (processHolder.currentBytes < processHolder.object.size) {
                                    if ((readLength = inputStream.read(buffer)) > 0) {
                                        processHolder.currentBytes += readLength;
                                        outputStream.write(buffer, 0, readLength);
                                        outputStream.flush();

                                        lastReceivedTime = System.currentTimeMillis();
                                    }

                                    broadcastTransferState(processHolder, false);

                                    if (processHolder.interrupter.interrupted()) {
                                        processHolder.object.setFlag(TransferObject.Flag.INTERRUPTED);
                                        break;
                                    }

                                    if (timeout != CoolSocket.NO_TIMEOUT && System.currentTimeMillis() - lastReceivedTime > timeout)
                                        break;
                                }

                                completed = processHolder.currentBytes == processHolder.object.size;
                                processHolder.object.setFlag(completed ? TransferObject.Flag.DONE
                                        : TransferObject.Flag.INTERRUPTED);

                                Log.d(TAG, "handleTransferAsSender(): File received " + processHolder.object.name);
                            } catch (Exception e) {
                                e.printStackTrace();
                                processHolder.interrupter.interrupt(false);
                                processHolder.object.setFlag(TransferObject.Flag.INTERRUPTED);
                            } finally {
                                if (outputStream != null)
                                    outputStream.close();
                            }

                            try {
                                if (completed) {
                                    processHolder.completedBytes += processHolder.currentBytes;
                                    processHolder.completedCount++;

                                    if (processHolder.currentFile.getParentFile() != null) {
                                        processHolder.currentFile = FileUtils.saveReceivedFile(
                                                processHolder.currentFile.getParentFile(),
                                                processHolder.currentFile, processHolder.object);

                                        Log.d(TAG, "handleTransferAsReceiver(): The file is "
                                                + processHolder.currentFile.getUri().toString()
                                                + " and the name is " + processHolder.object.file);

                                        sendBroadcast(new Intent(FileListFragment.ACTION_FILE_LIST_CHANGED)
                                                .putExtra(FileListFragment.EXTRA_FILE_PARENT,
                                                        processHolder.currentFile.getParentFile().getUri())
                                                .putExtra(FileListFragment.EXTRA_FILE_NAME,
                                                        processHolder.currentFile.getName()));
                                    }

                                    if (processHolder.currentFile instanceof LocalDocumentFile
                                            && mMediaScanner.isConnected())
                                        mMediaScanner.scanFile(
                                                ((LocalDocumentFile) processHolder.currentFile)
                                                        .getFile().getAbsolutePath(),
                                                processHolder.object.mimeType);
                                }
                            } catch (Exception ignored) {
                                Log.e(TAG, "Error occurred during completion of the transfer");
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    retry = true;

                    if (!processHolder.recoverInterruptions) {
                        TransferUtils.recoverIncomingInterruptions(CommunicationService.this, processHolder.group.id);
                        processHolder.recoverInterruptions = true;
                    }

                    break;
                } finally {
                    if (processHolder.object != null) {
                        Log.d(TAG, "handleTransferAsReceiver(): Updating file instances to "
                                + processHolder.object.getFlag().toString());
                        getDatabase().update(getDbInstance(), processHolder.object, processHolder.group);
                    }
                }
            }

            try {
                DocumentFile savePath = FileUtils.getSavePath(getApplicationContext(), processHolder.group);
                boolean areFilesDone = getDatabase().getFirstFromTable(getDbInstance(),
                        TransferUtils.createIncomingSelection(processHolder.group.id,
                                TransferObject.Flag.DONE, false)) == null;
                boolean jobDone = !processHolder.interrupter.interrupted() && areFilesDone;

                processHolder.activeConnection.reply(new JSONObject()
                        .put(Keyword.RESULT, false)
                        .put(Keyword.TRANSFER_JOB_DONE, jobDone)
                        .toString());
                Log.d(TAG, "handleTransferAsReceiver(): reply: done ?? " + jobDone);

                if (!retry)
                    if (processHolder.interrupter.interruptedByUser()) {
                        processHolder.notification.cancel();
                        Log.d(TAG, "handleTransferAsReceiver(): Removing notification an error is already notified");
                    } else if (processHolder.interrupter.interrupted()) {
                        getNotificationHelper().notifyReceiveError(processHolder);
                        Log.d(TAG, "handleTransferAsReceiver(): Some files was not received");
                    } else if (processHolder.completedCount > 0) {
                        getNotificationHelper().notifyFileReceived(processHolder, savePath);
                        Log.d(TAG, "handleTransferAsReceiver(): Notify user");
                    }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            removeProcess(processHolder);
            notifyTaskStatusChange(processHolder.group.id, processHolder.assignee.deviceId,
                    processHolder.type, TASK_STATUS_STOPPED);
            notifyTaskRunningListChange();
            broadcastTransferState(processHolder, true);

            Log.d(TAG, "We have exited");

            if (retry && processHolder.attemptsLeft > 0 && !processHolder.interrupter.interruptedByUser()) {
                try {
                    startTransferAsClient(processHolder);
                    processHolder.attemptsLeft--;
                } catch (Exception e) {
                    Log.d(TAG, "handleTransferAsReceiver(): Restart is requested, but transfer" +
                            " instance failed to reconstruct");
                }
            }
        }
    }

    private void handleTransferAsSender(ProcessHolder processHolder)
    {
        addProcess(processHolder);
        notifyTaskStatusChange(processHolder.group.id, processHolder.device.id, processHolder.type,
                TASK_STATUS_ONGOING);
        notifyTaskRunningListChange();

        try {
            TransferUtils.loadGroupInfo(this, processHolder.group, processHolder.assignee);

            while (processHolder.activeConnection.getSocket().isConnected()) {
                processHolder.currentBytes = 0;
                CoolSocket.ActiveConnection.Response response = processHolder.activeConnection.receive();
                Log.d(TAG, "handleTransferAsSender(): receive: " + response.response);
                JSONObject request = new JSONObject(response.response);

                if (request.has(Keyword.RESULT) && !request.getBoolean(Keyword.RESULT)) {
                    if (request.has(Keyword.TRANSFER_JOB_DONE) && !request.getBoolean(Keyword.TRANSFER_JOB_DONE))
                        processHolder.interrupter.interrupt(true);

                    Log.d(TAG, "handleTransferAsSender(): Receiver notified that the transfer " +
                            "has stopped with interruption=" + processHolder.interrupter.interrupted());
                    return;
                } else if (processHolder.interrupter.interrupted()) {
                    processHolder.activeConnection.reply(new JSONObject()
                            .put(Keyword.RESULT, false)
                            .put(Keyword.TRANSFER_JOB_DONE, false)
                            .toString());

                    Log.d(TAG, "handleTransferAsSender(): Exiting because the interruption " +
                            "has been triggered");

                    // Wait for the next response to ensure no error occurs.
                    continue;
                }

                try {
                    Log.d(TAG, "handleTransferAsSender(): " + processHolder.type.toString());

                    processHolder.object = new TransferObject(processHolder.group.id,
                            request.getInt(Keyword.TRANSFER_REQUEST_ID), processHolder.type);

                    getDatabase().reconstruct(getDbInstance(), processHolder.object);

                    processHolder.currentFile = FileUtils.fromUri(getApplicationContext(),
                            Uri.parse(processHolder.object.file));
                    long fileSize = processHolder.currentFile.length();
                    InputStream inputStream = getContentResolver().openInputStream(
                            processHolder.currentFile.getUri());

                    if (inputStream == null)
                        throw new FileNotFoundException("The input stream for the file has failed to open.");

                    broadcastTransferState(processHolder, false);

                    if (request.has(Keyword.SKIPPED_BYTES)) {
                        long skippedBytes = request.getLong(Keyword.SKIPPED_BYTES);
                        long newPosition = inputStream.skip(skippedBytes);

                        Log.d(TAG, "handleTransferAsSender(): Has skipped bytes: " + skippedBytes);

                        if (skippedBytes > 0 && newPosition != skippedBytes) {
                            inputStream.close();
                            throw new IOException("Failed to skip bytes. The requested is " + skippedBytes
                                    + " and the result is " + newPosition);
                        }
                    }

                    JSONObject reply = new JSONObject()
                            .put(Keyword.RESULT, true);

                    if (fileSize != processHolder.object.size) {
                        processHolder.object.size = fileSize;

                        reply.put(Keyword.SIZE_CHANGED, fileSize);
                        processHolder.activeConnection.reply(reply.toString());
                        Log.d(TAG, "handleTransferAsSender(): reply: " + reply.toString());

                        JSONObject validityOfChange = new JSONObject(processHolder.activeConnection
                                .receive().response);

                        if (!validityOfChange.has(Keyword.RESULT) || !validityOfChange.getBoolean(
                                Keyword.RESULT)) {
                            processHolder.object.putFlag(processHolder.device.id, TransferObject.Flag.INTERRUPTED);
                            getDatabase().update(getDbInstance(), processHolder.object, processHolder.group);
                            continue;
                        }

                        processHolder.activeConnection.reply(Keyword.STUB);
                    } else {
                        processHolder.activeConnection.reply(reply.toString());
                        Log.d(TAG, "handleTransferAsSender(): reply: " + reply.toString());
                    }

                    processHolder.activeConnection.receive();
                    processHolder.object.putFlag(processHolder.device.id, TransferObject.Flag.IN_PROGRESS);
                    getDatabase().update(getDbInstance(), processHolder.object, processHolder.group);

                    try {
                        boolean sizeExceeded = false;
                        int readLength;
                        byte[] buffer = new byte[AppConfig.BUFFER_LENGTH_DEFAULT];
                        OutputStream outputStream = processHolder.activeConnection.getSocket()
                                .getOutputStream();

                        while ((readLength = inputStream.read(buffer)) != -1
                                && processHolder.currentBytes < processHolder.object.size
                                && !processHolder.activeConnection.getSocket().isOutputShutdown()) {
                            if (readLength > 0) {
                                if (processHolder.currentBytes + readLength > processHolder.object.size) {
                                    sizeExceeded = true;
                                    break;
                                }

                                broadcastTransferState(processHolder, false);

                                processHolder.currentBytes += readLength;
                                outputStream.write(buffer, 0, readLength);
                                outputStream.flush();
                            }

                            if (processHolder.interrupter.interrupted()) {
                                processHolder.object.putFlag(processHolder.device.id,
                                        TransferObject.Flag.INTERRUPTED);
                                break;
                            }
                        }

                        if (processHolder.currentBytes == processHolder.object.size) {
                            processHolder.completedBytes += processHolder.currentBytes;
                            processHolder.completedCount++;
                            processHolder.object.putFlag(processHolder.device.id, TransferObject.Flag.DONE);
                        } else if (sizeExceeded)
                            processHolder.object.putFlag(processHolder.device.id, TransferObject.Flag.REMOVED);
                        else
                            processHolder.object.putFlag(processHolder.device.id, TransferObject.Flag.INTERRUPTED);

                        getDatabase().update(getDbInstance(), processHolder.object, processHolder.group);

                        Log.d(TAG, "handleTransferAsSender(): File sent " + processHolder.object.name);
                    } catch (Exception e) {
                        e.printStackTrace();
                        processHolder.interrupter.interrupt(false);
                        processHolder.object.putFlag(processHolder.device.id,
                                TransferObject.Flag.INTERRUPTED);
                        getDatabase().update(getDbInstance(), processHolder.object, processHolder.group);
                    } finally {
                        inputStream.close();
                    }
                } catch (ReconstructionFailedException e) {
                    Log.d(TAG, "handleTransferAsSender(): File not found");

                    processHolder.activeConnection.reply(new JSONObject()
                            .put(Keyword.RESULT, false)
                            .put(Keyword.ERROR, Keyword.ERROR_NOT_FOUND)
                            .put(Keyword.FLAG, Keyword.FLAG_GROUP_EXISTS)
                            .toString());

                    processHolder.object.putFlag(processHolder.device.id, TransferObject.Flag.REMOVED);
                    getDatabase().update(getDbInstance(), processHolder.object, processHolder.group);
                } catch (FileNotFoundException | StreamCorruptedException e) {
                    Log.d(TAG, "handleTransferAsSender(): File is not accessible ? " + processHolder.object.name);

                    processHolder.activeConnection.reply(new JSONObject()
                            .put(Keyword.RESULT, false)
                            .put(Keyword.ERROR, Keyword.ERROR_NOT_ACCESSIBLE)
                            .put(Keyword.FLAG, Keyword.FLAG_GROUP_EXISTS)
                            .toString());

                    processHolder.object.putFlag(processHolder.device.id, TransferObject.Flag.INTERRUPTED);
                    getDatabase().update(getDbInstance(), processHolder.object, processHolder.group);
                } catch (Exception e) {
                    e.printStackTrace();

                    processHolder.activeConnection.reply(new JSONObject()
                            .put(Keyword.RESULT, false)
                            .put(Keyword.ERROR, Keyword.ERROR_UNKNOWN)
                            .put(Keyword.FLAG, Keyword.FLAG_GROUP_EXISTS)
                            .toString());

                    processHolder.object.putFlag(processHolder.device.id, TransferObject.Flag.INTERRUPTED);
                    getDatabase().update(getDbInstance(), processHolder.object, processHolder.group);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            processHolder.interrupter.interrupt();
        } finally {
            if (processHolder.notification != null)
                processHolder.notification.cancel();
            else if (processHolder.interrupter.interrupted() && !processHolder.interrupter.interruptedByUser())
                mNotificationHelper.notifyConnectionError(processHolder, null);

            synchronized (getActiveProcessList()) {
                removeProcess(processHolder);

                if (processHolder.group.id != 0 && processHolder.device.id != null)
                    notifyTaskStatusChange(processHolder.group.id, processHolder.device.id,
                            processHolder.type, TASK_STATUS_STOPPED);

                notifyTaskRunningListChange();
            }

            broadcastTransferState(processHolder, true);
        }
    }

    private boolean hasOngoingTasks()
    {
        return mCommunicationServer.getConnections().size() > 0 || getOngoingIndexList().size() > 0
                || getActiveProcessList().size() > 0 || mHotspotUtils.isStarted()
                || mWebShareServer.hadClients();
    }

    private ProcessHolder findProcessById(long groupId, @Nullable String deviceId, TransferObject.Type type)
    {
        synchronized (getActiveProcessList()) {
            for (ProcessHolder processHolder : getActiveProcessList())
                if (processHolder.group.id == groupId && type.equals(processHolder.type)
                        && (deviceId == null || deviceId.equals(processHolder.device.id)))
                    return processHolder;
        }

        return null;
    }

    private synchronized List<ProcessHolder> getActiveProcessList()
    {
        return mActiveProcessList;
    }

    public android.database.sqlite.SQLiteDatabase getDbInstance()
    {
        return mDbInstance;
    }

    private HotspotUtils getHotspotUtils()
    {
        return mHotspotUtils;
    }

    private CommunicationNotificationHelper getNotificationHelper()
    {
        return mNotificationHelper;
    }

    private synchronized Map<Long, Interrupter> getOngoingIndexList()
    {
        return mOngoingIndexList;
    }

    private ExecutorService getSelfExecutor()
    {
        return mSelfExecutor;
    }

    private WifiManager.WifiLock getWifiLock()
    {
        return mWifiLock;
    }

    private boolean isProcessRunning(long groupId, String deviceId, TransferObject.Type type)
    {
        return findProcessById(groupId, deviceId, type) != null;
    }

    private void notifyTaskStatusChange(long groupId, String deviceId, TransferObject.Type type,
                                        int state)
    {
        Intent intent = new Intent(ACTION_TASK_STATUS_CHANGE)
                .putExtra(EXTRA_TASK_CHANGE_TYPE, state)
                .putExtra(EXTRA_GROUP_ID, groupId)
                .putExtra(EXTRA_DEVICE_ID, deviceId)
                .putExtra(EXTRA_TRANSFER_TYPE, type.toString());

        sendBroadcast(intent);
    }

    private void notifyTaskRunningListChange()
    {
        List<Long> taskList = new ArrayList<>();
        ArrayList<String> deviceList = new ArrayList<>();

        synchronized (getActiveProcessList()) {
            for (ProcessHolder processHolder : getActiveProcessList()) {
                if (processHolder.group != null && processHolder.device != null) {
                    taskList.add(processHolder.group.id);
                    deviceList.add(processHolder.device.id);
                }
            }
        }

        long[] taskArray = new long[taskList.size()];

        for (int i = 0; i < taskList.size(); i++)
            taskArray[i] = taskList.get(i);

        sendBroadcast(new Intent(ACTION_TASK_RUNNING_LIST_CHANGE)
                .putExtra(EXTRA_TASK_LIST_RUNNING, taskArray)
                .putStringArrayListExtra(EXTRA_DEVICE_LIST_RUNNING, deviceList));
    }

    private void refreshServiceState()
    {
        startForeground(CommunicationNotificationHelper.SERVICE_COMMUNICATION_FOREGROUND_NOTIFICATION_ID,
                getNotificationHelper().getCommunicationServiceNotification(
                        mWebShareServer != null && mWebShareServer.isAlive()).build());
    }

    private void sendHotspotStatusDisabling()
    {
        sendBroadcast(new Intent(ACTION_HOTSPOT_STATUS)
                .putExtra(EXTRA_HOTSPOT_ENABLED, false)
                .putExtra(EXTRA_HOTSPOT_DISABLING, true));
    }

    private void sendHotspotStatus(WifiConfiguration wifiConfiguration)
    {
        Intent statusIntent = new Intent(ACTION_HOTSPOT_STATUS)
                .putExtra(EXTRA_HOTSPOT_ENABLED, wifiConfiguration != null)
                .putExtra(EXTRA_HOTSPOT_DISABLING, false);

        if (wifiConfiguration != null) {
            statusIntent.putExtra(EXTRA_HOTSPOT_NAME, wifiConfiguration.SSID)
                    .putExtra(EXTRA_HOTSPOT_PASSWORD, wifiConfiguration.preSharedKey)
                    .putExtra(EXTRA_HOTSPOT_KEY_MGMT, NetworkUtils.getAllowedKeyManagement(
                            wifiConfiguration.allowedKeyManagement));
        }

        sendBroadcast(statusIntent);
    }

    private void setupHotspot()
    {
        if (getHotspotUtils().isEnabled()) {
            getHotspotUtils().disable();

            if (Build.VERSION.SDK_INT >= 26)
                sendHotspotStatusDisabling();
        } else
            getHotspotUtils().enableConfigured(AppUtils.getHotspotName(this), null);
    }

    private void startTransferAsClient(long groupId, String deviceId, TransferObject.Type type) throws TransferGroupNotFoundException,
            DeviceNotFoundException, ConnectionNotFoundException, AssigneeNotFoundException
    {
        ProcessHolder processHolder = new ProcessHolder();
        processHolder.type = type;

        processHolder.device = new NetworkDevice(deviceId);

        try {
            getDatabase().reconstruct(getDbInstance(), processHolder.device);
        } catch (ReconstructionFailedException e) {
            throw new DeviceNotFoundException();
        }

        processHolder.group = new PreloadedGroup(groupId);

        try {
            getDatabase().reconstruct(getDbInstance(), processHolder.group);
        } catch (ReconstructionFailedException e) {
            throw new TransferGroupNotFoundException();
        }

        processHolder.assignee = new TransferAssignee(processHolder.group, processHolder.device,
                processHolder.type);

        try {
            getDatabase().reconstruct(getDbInstance(), processHolder.assignee);
        } catch (ReconstructionFailedException e) {
            throw new AssigneeNotFoundException();
        }

        processHolder.connection = new DeviceConnection(processHolder.assignee);

        try {
            getDatabase().reconstruct(getDbInstance(), processHolder.connection);
        } catch (ReconstructionFailedException e) {
            throw new ConnectionNotFoundException();
        }

        Log.d(TAG, "startTransferAsClient(): With deviceId=" + processHolder.device.id + " groupId="
                + processHolder.group.id + " adapter=" + processHolder.assignee.connectionAdapter);
        startTransferAsClient(processHolder);
    }

    private void startTransferAsClient(final ProcessHolder holder)
    {
        CommunicationBridge.connect(getDatabase(), client -> {
            try {
                holder.activeConnection = client.communicate(holder.device, holder.connection);

                {
                    JSONObject reply = new JSONObject()
                            .put(Keyword.REQUEST, Keyword.REQUEST_TRANSFER_JOB)
                            .put(Keyword.TRANSFER_GROUP_ID, holder.group.id)
                            .put(Keyword.TRANSFER_TYPE, holder.type.toString());

                    holder.activeConnection.reply(reply.toString());
                    Log.d(TAG, "startTransferAsClient(): reply: " + reply.toString());
                }

                {
                    CoolSocket.ActiveConnection.Response response = holder.activeConnection.receive();
                    JSONObject responseJSON = new JSONObject(response.response);

                    Log.d(TAG, "startTransferAsClient(): " + holder.type.toString()
                            + "; About to start with " + response.response);

                    if (responseJSON.getBoolean(Keyword.RESULT)) {
                        holder.attemptsLeft = 2;

                        if (TransferObject.Type.INCOMING.equals(holder.type)) {
                            handleTransferAsReceiver(holder);
                        } else if (TransferObject.Type.OUTGOING.equals(holder.type)) {
                            holder.activeConnection.reply(Keyword.STUB);
                            handleTransferAsSender(holder);
                            holder.activeConnection.reply(Keyword.STUB);
                        }

                        try {
                            CoolSocket.ActiveConnection.Response lastResponse
                                    = holder.activeConnection.receive();

                            Log.d(TAG, "startTransferAsClient(): Final response before " +
                                    "exit: " + lastResponse.response);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } else {
                        getNotificationHelper().notifyConnectionError(holder, responseJSON.has(
                                Keyword.ERROR) ? responseJSON.getString(Keyword.ERROR) : null);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                mNotificationHelper.notifyConnectionError(holder, null);
            }
        });
    }

    public class CommunicationServer extends CoolSocket
    {
        CommunicationServer()
        {
            super(AppConfig.SERVER_PORT_COMMUNICATION);
            setSocketTimeout(AppConfig.DEFAULT_SOCKET_TIMEOUT_LARGE);
        }

        @Override
        protected void onConnected(final ActiveConnection activeConnection)
        {
            // check if the same address has other connections and limit that to 5
            if (getConnectionCountByAddress(activeConnection.getAddress()) > 5)
                return;

            try {
                ActiveConnection.Response clientRequest = activeConnection.receive();
                JSONObject responseJSON = analyzeResponse(clientRequest);
                JSONObject replyJSON = new JSONObject();

                if (wasUpdateRequest(activeConnection, responseJSON, replyJSON))
                    return;

                boolean result = false;
                boolean shouldContinue = false;

                final int networkPin = getDefaultPreferences().getInt(Keyword.NETWORK_PIN, -1);
                final boolean isSecureConnection = networkPin != -1
                        && responseJSON.has(Keyword.DEVICE_SECURE_KEY)
                        && responseJSON.getInt(Keyword.DEVICE_SECURE_KEY) == networkPin;

                String deviceSerial = null;
                NetworkDevice device = null;
                AppUtils.applyDeviceToJSON(CommunicationService.this, replyJSON);

                if (responseJSON.has(Keyword.HANDSHAKE_REQUIRED) && responseJSON.getBoolean(Keyword.HANDSHAKE_REQUIRED)) {
                    pushReply(activeConnection, replyJSON, true);

                    if (!responseJSON.has(Keyword.HANDSHAKE_ONLY) || !responseJSON.getBoolean(Keyword.HANDSHAKE_ONLY)) {
                        try {
                            device = NetworkDeviceLoader.loadFrom(getDatabase(), responseJSON);
                        } catch (JSONException ignored) {
                            deviceSerial = responseJSON.getString(Keyword.DEVICE_INFO_SERIAL);
                        }

                        clientRequest = activeConnection.receive();
                        responseJSON = analyzeResponse(clientRequest);
                        replyJSON = new JSONObject();
                    } else
                        return;
                }

                try {
                    NetworkDevice testDevice = new NetworkDevice(device == null ? deviceSerial
                            : device.id);

                    getDatabase().reconstruct(testDevice);

                    if (isSecureConnection)
                        testDevice.isRestricted = false;

                    if (!testDevice.isRestricted)
                        shouldContinue = true;

                    if (device == null)
                        device = testDevice;
                    else
                        device.applyPreferences(testDevice);
                } catch (ReconstructionFailedException ignored) {
                    if (device == null) {
                        device = NetworkDeviceLoader.load(true, getDatabase(),
                                activeConnection.getClientAddress(), null);

                        if (device == null)
                            throw new Exception("Could not reach to the opposite server");
                    }

                    device.isTrusted = false;

                    if (isSecureConnection)
                        device.isRestricted = false;

                    getDatabase().publish(device);

                    shouldContinue = true;

                    if (device.isRestricted)
                        getNotificationHelper().notifyConnectionRequest(device);
                }

                DeviceConnection connection = NetworkDeviceLoader.processConnection(getDatabase(),
                        device, activeConnection.getClientAddress());

                getDatabase().broadcast();

                if (!shouldContinue)
                    replyJSON.put(Keyword.ERROR, Keyword.ERROR_NOT_ALLOWED);
                else if (responseJSON.has(Keyword.REQUEST)) {
                    switch (responseJSON.getString(Keyword.REQUEST)) {
                        case (Keyword.REQUEST_TRANSFER):
                            if (responseJSON.has(Keyword.FILES_INDEX) && responseJSON.has(Keyword.TRANSFER_GROUP_ID)
                                    && getOngoingIndexList().size() < 1) {
                                final long groupId = responseJSON.getLong(Keyword.TRANSFER_GROUP_ID);
                                final String jsonIndex = responseJSON.getString(Keyword.FILES_INDEX);

                                result = true;

                                handleTransferRequest(groupId, jsonIndex, device, connection, false);
                            }
                            break;
                        case (Keyword.REQUEST_RESPONSE):
                            if (responseJSON.has(Keyword.TRANSFER_GROUP_ID)) {
                                int groupId = responseJSON.getInt(Keyword.TRANSFER_GROUP_ID);
                                boolean isAccepted = responseJSON.getBoolean(Keyword.TRANSFER_IS_ACCEPTED);

                                TransferGroup group = new TransferGroup(groupId);
                                TransferAssignee assignee = new TransferAssignee(group, device,
                                        TransferObject.Type.OUTGOING);

                                try {
                                    getDatabase().reconstruct(group);
                                    getDatabase().reconstruct(assignee);

                                    if (!isAccepted) {
                                        getDatabase().remove(assignee);
                                        getDatabase().broadcast();
                                    }

                                    result = true;
                                } catch (Exception ignored) {
                                }
                            }
                            break;
                        case (Keyword.REQUEST_CLIPBOARD):
                            if (responseJSON.has(Keyword.TRANSFER_CLIPBOARD_TEXT)) {
                                TextStreamObject textStreamObject = new TextStreamObject(
                                        AppUtils.getUniqueNumber(), responseJSON.getString(
                                        Keyword.TRANSFER_CLIPBOARD_TEXT));

                                getDatabase().publish(textStreamObject);
                                getDatabase().broadcast();
                                getNotificationHelper().notifyClipboardRequest(device, textStreamObject);

                                result = true;
                            }
                            break;
                        case (Keyword.REQUEST_ACQUAINTANCE):
                            sendBroadcast(new Intent(ACTION_DEVICE_ACQUAINTANCE)
                                    .putExtra(EXTRA_DEVICE_ID, device.id)
                                    .putExtra(EXTRA_CONNECTION_ADAPTER_NAME, connection.adapterName));

                            result = true;
                            break;
                        case (Keyword.REQUEST_HANDSHAKE):
                            result = true;
                            break;
                        case (Keyword.REQUEST_TRANSFER_JOB):
                            if (responseJSON.has(Keyword.TRANSFER_GROUP_ID)) {
                                int groupId = responseJSON.getInt(Keyword.TRANSFER_GROUP_ID);
                                String typeValue = responseJSON.getString(Keyword.TRANSFER_TYPE);

                                try {
                                    TransferObject.Type type = TransferObject.Type.valueOf(typeValue);

                                    // The type is reversed to match our side
                                    if (TransferObject.Type.INCOMING.equals(type))
                                        type = TransferObject.Type.OUTGOING;
                                    else if (TransferObject.Type.OUTGOING.equals(type))
                                        type = TransferObject.Type.INCOMING;

                                    PreloadedGroup group = new PreloadedGroup(groupId);
                                    getDatabase().reconstruct(group);

                                    Log.d(CommunicationService.TAG, "CommunicationServer.onConnected(): "
                                            + "groupId=" + groupId + " typeValue=" + typeValue);

                                    if (!isProcessRunning(groupId, device.id, type)) {
                                        ProcessHolder processHolder = new ProcessHolder();
                                        processHolder.activeConnection = activeConnection;
                                        processHolder.group = group;
                                        processHolder.device = device;
                                        processHolder.type = type;
                                        processHolder.assignee = new TransferAssignee(
                                                group, device, type);

                                        getDatabase().reconstruct(processHolder.assignee);
                                        pushReply(activeConnection, new JSONObject(), true);
                                        Log.d(TAG, "CommunicationServer.onConnected(): " +
                                                "Reply sent for the connection");

                                        result = true;

                                        if (TransferObject.Type.OUTGOING.equals(type))
                                            handleTransferAsSender(processHolder);
                                        else if (TransferObject.Type.INCOMING.equals(type)) {
                                            Log.d(TAG, "CommunicationServer.onConnected(): "
                                                    + activeConnection.receive().response);

                                            if (device.isTrusted)
                                                handleTransferAsReceiver(processHolder);
                                            else {
                                                result = false;
                                                responseJSON.put(Keyword.ERROR, Keyword.ERROR_REQUIRE_TRUSTZONE);
                                            }

                                            Log.d(TAG, "CommunicationServer.onConnected(): "
                                                    + activeConnection.receive().response);
                                        } else
                                            result = false;
                                    } else
                                        responseJSON.put(Keyword.ERROR, Keyword.ERROR_NOT_ACCESSIBLE);
                                } catch (Exception e) {
                                    responseJSON.put(Keyword.ERROR, Keyword.ERROR_NOT_FOUND);
                                }
                            }
                            break;
                    }
                }

                pushReply(activeConnection, replyJSON, result);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        JSONObject analyzeResponse(ActiveConnection.Response response) throws JSONException
        {
            return response.totalLength > 0 ? new JSONObject(response.response) : new JSONObject();
        }

        void pushReply(ActiveConnection activeConnection, JSONObject reply, boolean result)
                throws JSONException, TimeoutException, IOException
        {
            activeConnection.reply(reply.put(Keyword.RESULT, result).toString());
        }

        private boolean wasUpdateRequest(ActiveConnection activeConnection, JSONObject responseJSON,
                                         JSONObject replyJSON) throws TimeoutException, JSONException, IOException
        {
            if (!responseJSON.has(Keyword.REQUEST))
                return false;

            String request = responseJSON.getString(Keyword.REQUEST);

            if (Keyword.REQUEST_UPDATE.equals(request)) {
                activeConnection.reply(replyJSON.put(Keyword.RESULT, true).toString());

                getSelfExecutor().submit(() -> {
                    try {
                        UpdateUtils.sendUpdate(getApplicationContext(), activeConnection.getClientAddress());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            } else if (Keyword.REQUEST_UPDATE_V2.equals(request)) {
                NetworkDevice thisDevice = AppUtils.getLocalDevice(CommunicationService.this);
                File file = new File(getApplicationInfo().sourceDir);

                {
                    JSONObject reply = new JSONObject()
                            .put(Keyword.RESULT, true)
                            .put(Keyword.INDEX_FILE_SIZE, file.length())
                            .put(Keyword.APP_INFO_VERSION_CODE, thisDevice.versionCode);
                    activeConnection.reply(reply.toString());
                }

                {
                    ActiveConnection.Response responseObject = activeConnection.receive();
                    JSONObject response = new JSONObject(responseObject.response);

                    if (response.getBoolean(Keyword.RESULT) && response.getBoolean(Keyword.RESULT)) {
                        OutputStream outputStream = activeConnection.getSocket().getOutputStream();
                        FileInputStream inputStream = new FileInputStream(file);

                        byte[] buffer = new byte[AppConfig.BUFFER_LENGTH_DEFAULT];
                        int len;
                        long lastRead = 0;

                        while ((len = inputStream.read(buffer)) != -1) {
                            long currentTime = System.currentTimeMillis();

                            if (len > 0) {
                                lastRead = currentTime;

                                outputStream.write(buffer, 0, len);
                                outputStream.flush();
                            }

                            if (System.currentTimeMillis() - lastRead > AppConfig.DEFAULT_SOCKET_TIMEOUT)
                                throw new TimeoutException("Did not read any bytes for 5secs.");
                        }

                        inputStream.close();
                    }
                }
            } else
                return false;

            return true;
        }
    }

    public static class ProcessHolder
    {
        // Native objects
        public Interrupter interrupter = new Interrupter();

        // Static objects
        public CoolSocket.ActiveConnection activeConnection;
        public NetworkDevice device;
        public PreloadedGroup group;
        public TransferAssignee assignee;
        public DeviceConnection connection;
        public TransferObject.Type type;

        // Changing objects
        public DynamicNotification notification;
        public TransferObject object;
        public DocumentFile currentFile;
        public long lastProcessingTime;
        public long currentBytes; // moving
        public long lastKnownBytes; // completedBytes of 2 secs ago
        public long completedBytes;
        public long timeStarted;
        public int completedCount;

        // Informative objects
        public boolean recoverInterruptions = false;
        public int attemptsLeft = 2;

        public ProcessHolder()
        {
            timeStarted = System.currentTimeMillis();
        }
    }
}
