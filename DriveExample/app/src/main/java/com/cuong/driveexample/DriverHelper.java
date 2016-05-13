package com.cuong.driveexample;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.cuong.lib_nvc_android.util.Log;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.DriveResource;
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.MetadataBuffer;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.drive.metadata.CustomPropertyKey;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Created by Clover on 5/13/2016.
 * Open Source: This source has been wrote by CuongNguyen
 * Contact: vcuong11s@gmail.com or unme.rf@gmail.com
 */
public class DriverHelper {
    private static final int RESOLVE_CONNECTION_REQUEST_CODE = 11;
    private Activity context;
    private GoogleApiClient mGoogleApiClient;
    private boolean driveConnected;

    private DriveId mFolderDriveId;
    private DriveFolder mDriveFolder;

    // For connection
    private ConnectionCallbacks connectionCallbacks;
    private OnConnectFailed onConnectFailed;

    // For folder create
    private OnCreateFolderResult onCreateFolderResult;
    private OnCreateFileResult onCreateFileResult;
    private CheckFolderResult checkFolderResult;

    // For upload
    private FileInputStream fileInputStream;
    private String folderId;
    private String fileName;
    private String mineType;

    // For get list file in folder
    private FetchListFile fetchListFile;

    public DriverHelper(Activity context) {
        this.context = context;
        GoogleApiClient.OnConnectionFailedListener mOnConnectionFailedListener = new GoogleApiClient.OnConnectionFailedListener() {
            @Override
            public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
                if (onConnectFailed != null) {
                    onConnectFailed.onFailed(connectionResult);
                } else {
                    Log.e("DriverHelper", "You didn't set onConnectFailed when connect");
                }
            }
        };
        GoogleApiClient.ConnectionCallbacks mConnectionCallbacks = new GoogleApiClient.ConnectionCallbacks() {
            @Override
            public void onConnected(@Nullable Bundle bundle) {
                driveConnected = true;
                if (connectionCallbacks != null) {
                    connectionCallbacks.connected();
                } else {
                    Log.e("DriverHelper", "You didn't set connectionCallbacks when connect");
                }
            }

            @Override
            public void onConnectionSuspended(int i) {
                driveConnected = false;
                if (connectionCallbacks != null) {
                    connectionCallbacks.suspended();
                } else {
                    Log.e("DriverHelper", "You didn't set connectionCallbacks when connect");
                }
            }
        };
        mGoogleApiClient = new GoogleApiClient.Builder(context)
                .addApi(Drive.API)
                .addScope(Drive.SCOPE_FILE)
                .addConnectionCallbacks(mConnectionCallbacks)
                .addOnConnectionFailedListener(mOnConnectionFailedListener)
                .build();
    }

    public void connect(ConnectionCallbacks connectionCallbacks, OnConnectFailed onConnectFailed) {
        this.connectionCallbacks = connectionCallbacks;
        this.onConnectFailed = onConnectFailed;
        mGoogleApiClient.connect();
    }

    public void resolveConnectionRequest(ConnectionResult connectionResult) {
        if (connectionResult.hasResolution()) {
            try {
                connectionResult.startResolutionForResult(context, RESOLVE_CONNECTION_REQUEST_CODE);
            } catch (IntentSender.SendIntentException e) {
                // Unable to resolve, message user appropriately
                Log.e("DriverHelper", "Unable to resolve, message user appropriately");
            }
        } else {
            GooglePlayServicesUtil.getErrorDialog(connectionResult.getErrorCode(), context, 0).show();
        }
    }

    public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        switch (requestCode) {
            case RESOLVE_CONNECTION_REQUEST_CODE:
                if (resultCode == Activity.RESULT_OK) {
                    mGoogleApiClient.connect();
                }
                break;
        }
    }

    public void checkExistsFolder(String folderId, CheckFolderResult checkFolderResult) {
        this.checkFolderResult = checkFolderResult;
        mFolderDriveId = DriveId.decodeFromString(folderId);
        DriveFolder folder = Drive.DriveApi.getFolder(mGoogleApiClient, mFolderDriveId);
        folder.getMetadata(mGoogleApiClient).setResultCallback(metadataRetrievedCallback);
    }

    public void createFolder(String folderName, OnCreateFolderResult onCreateFolderResult) {
        this.onCreateFolderResult = onCreateFolderResult;

        MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
                .setTitle(folderName).build();
        Drive.DriveApi.getRootFolder(mGoogleApiClient).createFolder(
                mGoogleApiClient, changeSet).setResultCallback(folderCreatedCallback);
    }

    public void uploadFile(FileInputStream inputStream, String folderId, String fileName, String mineType, OnCreateFileResult onCreateFileResult) {
        this.folderId = folderId;
        this.onCreateFileResult = onCreateFileResult;
        this.fileInputStream = inputStream;
        this.fileName = fileName;
        this.mineType = mineType;
        Drive.DriveApi.newDriveContents(mGoogleApiClient)
                .setResultCallback(driveContentsCallback);
    }

    public void fetchListFile(String folderId, FetchListFile fetchListFile) {
        this.fetchListFile = fetchListFile;
        mFolderDriveId = DriveId.decodeFromString(folderId);
        DriveFolder folder = Drive.DriveApi.getFolder(mGoogleApiClient, mFolderDriveId);
        folder.listChildren(mGoogleApiClient).setResultCallback(metadataResult);
    }

    public boolean isDriveConnected() {
        return driveConnected;
    }

    public GoogleApiClient getGoogleApiClient() {
        return mGoogleApiClient;
    }

    private ResultCallback<DriveApi.MetadataBufferResult> metadataResult = new
            ResultCallback<DriveApi.MetadataBufferResult>() {
                @Override
                public void onResult(DriveApi.MetadataBufferResult result) {
                    if (fetchListFile != null) {
                        if (!result.getStatus().isSuccess()) {
                            fetchListFile.onResult(null);
                            return;
                        }
                        MetadataBuffer mdb = null;
                        List<DriveEntity> listMd = new ArrayList<DriveEntity>();
                        try {
                            mdb = result.getMetadataBuffer();
                            if (mdb != null) for (Metadata md : mdb) {
                                if (md == null || !md.isDataValid()) continue;
                                listMd.add(new DriveEntity(md));
                            }
                        } finally {
                            if (mdb != null) mdb.close();
                        }
                        fetchListFile.onResult(listMd);
                    } else {
                        Log.e("DriverHelper", "You didn't set metadataResult when list file in folder");
                    }
                }
            };

    public class DriveEntity {
        public static final int CONTENT_NOT_AVAILABLE_LOCALLY = 0;
        public static final int CONTENT_AVAILABLE_LOCALLY = 1;
        private String alternateLink;
        private int contentAvailability;
        private Date createdDate;
        private Map<CustomPropertyKey, String> customProperties;
        private String description;
        private DriveId driveId;
        private String embedLink;
        private String fileExtension;
        private long fileSize;
        private Date lastViewedByMeDate;
        private String mimeType;
        private Date modifiedByMeDate;
        private Date modifiedDate;
        private String originalFilename;
        private boolean isPinned;
        private boolean isPinnable;
        private long quotaBytesUsed;
        private Date sharedWithMeDate;
        private String title;
        private String webContentLink;
        private String webViewLink;
        private boolean isInAppFolder;
        private boolean isEditable;
        private boolean isFolder;
        private boolean isRestricted;
        private boolean isShared;
        private boolean isStarred;
        private boolean isTrashed;
        private boolean isTrashable;
        private boolean isExplicitlyTrashed;
        private boolean isViewed;


        public DriveEntity() {

        }

        public DriveEntity(Metadata md) {
            setContentAvailability(md.getContentAvailability()).setAlternateLink(md.getAlternateLink()).setCreatedDate(md.getCreatedDate()).setCustomProperties(md.getCustomProperties()).setDescription(md.getDescription()).setDriveId(md.getDriveId()).setEmbedLink(md.getEmbedLink()).setFileExtension(md.getFileExtension()).setFileSize(md.getFileSize()).setLastViewedByMeDate(md.getLastViewedByMeDate()).setMimeType(md.getMimeType()).setModifiedByMeDate(md.getModifiedByMeDate()).setModifiedDate(md.getModifiedDate()).setOriginalFilename(md.getOriginalFilename()).setPinned(md.isPinned()).setPinnable(md.isPinnable()).setQuotaBytesUsed(md.getQuotaBytesUsed()).setSharedWithMeDate(md.getSharedWithMeDate()).setTitle(md.getTitle()).setWebContentLink(md.getWebContentLink()).setWebViewLink(md.getWebViewLink()).setInAppFolder(md.isInAppFolder()).setEditable(md.isEditable()).setFolder(md.isFolder()).setRestricted(md.isRestricted()).setShared(md.isShared()).setStarred(md.isStarred()).setTrashable(md.isTrashable()).setTrashed(md.isTrashed()).setExplicitlyTrashed(md.isExplicitlyTrashed()).setViewed(md.isViewed());
        }

        public String getAlternateLink() {
            return alternateLink;
        }

        public DriveEntity setAlternateLink(String alternateLink) {
            this.alternateLink = alternateLink;
            return this;
        }

        public int getContentAvailability() {
            return contentAvailability;
        }

        public DriveEntity setContentAvailability(int contentAvailability) {
            this.contentAvailability = contentAvailability;
            return this;
        }

        public Date getCreatedDate() {
            return createdDate;
        }

        public DriveEntity setCreatedDate(Date createdDate) {
            this.createdDate = createdDate;
            return this;
        }

        public Map<CustomPropertyKey, String> getCustomProperties() {
            return customProperties;
        }

        public DriveEntity setCustomProperties(Map<CustomPropertyKey, String> customProperties) {
            this.customProperties = customProperties;
            return this;
        }

        public String getDescription() {
            return description;
        }

        public DriveEntity setDescription(String description) {
            this.description = description;
            return this;
        }

        public DriveId getDriveId() {
            return driveId;
        }

        public DriveEntity setDriveId(DriveId driveId) {
            this.driveId = driveId;
            return this;
        }

        public String getEmbedLink() {
            return embedLink;
        }

        public DriveEntity setEmbedLink(String embedLink) {
            this.embedLink = embedLink;
            return this;
        }

        public String getFileExtension() {
            return fileExtension;
        }

        public DriveEntity setFileExtension(String fileExtension) {
            this.fileExtension = fileExtension;
            return this;
        }

        public long getFileSize() {
            return fileSize;
        }

        public DriveEntity setFileSize(long fileSize) {
            this.fileSize = fileSize;
            return this;
        }

        public Date getLastViewedByMeDate() {
            return lastViewedByMeDate;
        }

        public DriveEntity setLastViewedByMeDate(Date lastViewedByMeDate) {
            this.lastViewedByMeDate = lastViewedByMeDate;
            return this;
        }

        public String getMimeType() {
            return mimeType;
        }

        public DriveEntity setMimeType(String mimeType) {
            this.mimeType = mimeType;
            return this;
        }

        public Date getModifiedByMeDate() {
            return modifiedByMeDate;
        }

        public DriveEntity setModifiedByMeDate(Date modifiedByMeDate) {
            this.modifiedByMeDate = modifiedByMeDate;
            return this;
        }

        public Date getModifiedDate() {
            return modifiedDate;
        }

        public DriveEntity setModifiedDate(Date modifiedDate) {
            this.modifiedDate = modifiedDate;
            return this;
        }

        public String getOriginalFilename() {
            return originalFilename;
        }

        public DriveEntity setOriginalFilename(String originalFilename) {
            this.originalFilename = originalFilename;
            return this;
        }

        public boolean isPinned() {
            return isPinned;
        }

        public DriveEntity setPinned(boolean pinned) {
            isPinned = pinned;
            return this;
        }

        public boolean isPinnable() {
            return isPinnable;
        }

        public DriveEntity setPinnable(boolean pinnable) {
            isPinnable = pinnable;
            return this;
        }

        public long getQuotaBytesUsed() {
            return quotaBytesUsed;
        }

        public DriveEntity setQuotaBytesUsed(long quotaBytesUsed) {
            this.quotaBytesUsed = quotaBytesUsed;
            return this;
        }

        public Date getSharedWithMeDate() {
            return sharedWithMeDate;
        }

        public DriveEntity setSharedWithMeDate(Date sharedWithMeDate) {
            this.sharedWithMeDate = sharedWithMeDate;
            return this;
        }

        public String getTitle() {
            return title;
        }

        public DriveEntity setTitle(String title) {
            this.title = title;
            return this;
        }

        public String getWebContentLink() {
            return webContentLink;
        }

        public DriveEntity setWebContentLink(String webContentLink) {
            this.webContentLink = webContentLink;
            return this;
        }

        public String getWebViewLink() {
            return webViewLink;
        }

        public DriveEntity setWebViewLink(String webViewLink) {
            this.webViewLink = webViewLink;
            return this;
        }

        public boolean isInAppFolder() {
            return isInAppFolder;
        }

        public DriveEntity setInAppFolder(boolean inAppFolder) {
            isInAppFolder = inAppFolder;
            return this;
        }

        public boolean isEditable() {
            return isEditable;
        }

        public DriveEntity setEditable(boolean editable) {
            isEditable = editable;
            return this;
        }

        public boolean isFolder() {
            return isFolder;
        }

        public DriveEntity setFolder(boolean folder) {
            isFolder = folder;
            return this;
        }

        public boolean isRestricted() {
            return isRestricted;
        }

        public DriveEntity setRestricted(boolean restricted) {
            isRestricted = restricted;
            return this;
        }

        public boolean isShared() {
            return isShared;
        }

        public DriveEntity setShared(boolean shared) {
            isShared = shared;
            return this;
        }

        public boolean isStarred() {
            return isStarred;
        }

        public DriveEntity setStarred(boolean starred) {
            isStarred = starred;
            return this;
        }

        public boolean isTrashed() {
            return isTrashed;
        }

        public DriveEntity setTrashed(boolean trashed) {
            isTrashed = trashed;
            return this;
        }

        public boolean isTrashable() {
            return isTrashable;
        }

        public DriveEntity setTrashable(boolean trashable) {
            isTrashable = trashable;
            return this;
        }

        public boolean isExplicitlyTrashed() {
            return isExplicitlyTrashed;
        }

        public DriveEntity setExplicitlyTrashed(boolean explicitlyTrashed) {
            isExplicitlyTrashed = explicitlyTrashed;
            return this;
        }

        public boolean isViewed() {
            return isViewed;
        }

        public DriveEntity setViewed(boolean viewed) {
            isViewed = viewed;
            return this;
        }
    }

    private ResultCallback<DriveResource.MetadataResult> metadataRetrievedCallback = new
            ResultCallback<DriveResource.MetadataResult>() {
                @Override
                public void onResult(DriveResource.MetadataResult result) {
                    if (checkFolderResult != null) {
                        if (!result.getStatus().isSuccess()) {
                            checkFolderResult.error();
                            return;
                        }

                        Metadata metadata = result.getMetadata();
                        if (metadata.isTrashed()) {
                            checkFolderResult.trashed();
                        } else {
                            checkFolderResult.notTrashed();
                        }
                    } else {
                        Log.e("DriverHelper", "You didn't set checkFolderResult when check folder exists");
                    }
                }
            };

    private ResultCallback<DriveApi.DriveContentsResult> driveContentsCallback = new
            ResultCallback<DriveApi.DriveContentsResult>() {
                @Override
                public void onResult(@NonNull final DriveApi.DriveContentsResult result) {
                    if (onCreateFileResult != null) {
                        if (!result.getStatus().isSuccess()) {
                            onCreateFileResult.createContentsError(result);
                            return;
                        }
                        final DriveContents driveContents = result.getDriveContents();

                        // Perform I/O off the UI thread.
                        new Thread() {
                            @Override
                            public void run() {
                                // write content to DriveContents
                                OutputStream outputStream = driveContents.getOutputStream();
                                try {
                                    byte[] buffer = new byte[1024];
                                    while (fileInputStream.read(buffer) != -1) {
                                        outputStream.write(buffer);
                                    }
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }

                                // create a file on folderId
                                mFolderDriveId = DriveId.decodeFromString(folderId);
                                mDriveFolder = Drive.DriveApi.getFolder(mGoogleApiClient, mFolderDriveId);
                                MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
                                        .setTitle(fileName)
                                        .setMimeType(mineType)
                                        .setStarred(true).build();
                                mDriveFolder.createFile(mGoogleApiClient, changeSet, result.getDriveContents())
                                        .setResultCallback(fileCallback);
                            }
                        }.start();

                    } else {
                        Log.e("DriverHelper", "You didn't set onCreateFileResult when upload file");
                    }
                }
            };

    private ResultCallback<DriveFolder.DriveFileResult> fileCallback =
            new ResultCallback<DriveFolder.DriveFileResult>() {
                @Override
                public void onResult(@NonNull DriveFolder.DriveFileResult result) {
                    if (onCreateFileResult != null) {
                        if (!result.getStatus().isSuccess()) {
                            onCreateFileResult.createFileError(result);
                        } else {
                            onCreateFileResult.createFileSuccess(result);
                        }
                    } else {
                        Log.e("DriverHelper", "You didn't set onCreateFileResult when upload file");
                    }
                }
            };

    private ResultCallback<DriveFolder.DriveFolderResult> folderCreatedCallback = new
            ResultCallback<DriveFolder.DriveFolderResult>() {
                @Override
                public void onResult(@NonNull DriveFolder.DriveFolderResult result) {
                    if (onCreateFolderResult != null) {
                        onCreateFolderResult.onResult(result);
                    } else {
                        Log.e("DriverHelper", "You didn't set onCreateFolderResult when create folder");
                    }
                }
            };

    public interface OnCreateFolderResult {
        void onResult(DriveFolder.DriveFolderResult result);
    }

    public interface OnCreateFileResult {
        void createContentsError(DriveApi.DriveContentsResult result);

        void createFileError(DriveFolder.DriveFileResult result);

        void createFileSuccess(DriveFolder.DriveFileResult result);
    }

    public interface ConnectionCallbacks {
        void connected();

        void suspended();
    }

    public interface OnConnectFailed {
        void onFailed(ConnectionResult connectionResult);
    }

    public interface CheckFolderResult {
        void error();

        void trashed();

        void notTrashed();
    }

    public interface FetchListFile {
        void onResult(List<DriveEntity> result);
    }
}
