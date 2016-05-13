package com.cuong.driveexample;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;

import com.cuong.lib_nvc_android.util.DataUtils;
import com.cuong.lib_nvc_android.util.ToastUtils;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi.DriveContentsResult;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.Metadata;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private DriverHelper driverHelper;
    private SharedPreferences pref;
    private SharedPreferences.Editor editor;

    private Button btnUploadFile;
    private ListView lvFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        btnUploadFile = (Button) findViewById(R.id.btnUploadFile);
        lvFile = (ListView) findViewById(R.id.lvFile);
        driverHelper = new DriverHelper(MainActivity.this);

        pref = getSharedPreferences("drive_example", MODE_PRIVATE);
        editor = pref.edit();

        btnUploadFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (driverHelper.isDriveConnected()) {

                    // get folder id saved
                    final String folderId = pref.getString("folder_id", null);

                    // check folder created or not, if not, create
                    if (DataUtils.isNull(folderId)) {
                        // create folder
                        driverHelper.createFolder("Milky", new DriverHelper.OnCreateFolderResult() {
                            @Override
                            public void onResult(DriveFolder.DriveFolderResult result) {
                                if (!result.getStatus().isSuccess()) {
                                    ToastUtils.show(MainActivity.this, "Error while trying to create the folder");
                                    return;
                                }
                                editor.putString("folder_id", result.getDriveFolder().getDriveId().toString());
                                editor.commit();
                                ToastUtils.show(MainActivity.this, "Created a folder: " + result.getDriveFolder().getDriveId());
                            }
                        });
                    } else {
                        // folder created, get folder
                        driverHelper.checkExistsFolder(folderId, new DriverHelper.CheckFolderResult() {
                            @Override
                            public void error() {
                                ToastUtils.show(MainActivity.this, "Problem while trying to fetch metadata.");
                            }

                            @Override
                            public void trashed() {
                                ToastUtils.show(MainActivity.this, "Folder is trashed.");
                            }

                            @Override
                            public void notTrashed() {
                                String backupPath = Environment.getExternalStorageDirectory() + "/Milkey/Milkey_20160513.db";
                                File fileBackup = new File(backupPath);
                                String mineType = "application/x-sqlite3";
                                FileInputStream fileInputStream = null;
                                try {
                                    fileInputStream = new FileInputStream(backupPath);
                                } catch (FileNotFoundException e) {
                                    e.printStackTrace();
                                    return;
                                }
                                driverHelper.uploadFile(fileInputStream, folderId, fileBackup.getName(), mineType, new DriverHelper.OnCreateFileResult() {
                                    @Override
                                    public void createContentsError(DriveContentsResult result) {
                                        ToastUtils.show(MainActivity.this, "Error while trying to create new file contents");
                                    }

                                    @Override
                                    public void createFileError(DriveFolder.DriveFileResult result) {
                                        ToastUtils.show(MainActivity.this, "Error while trying to create the file");
                                    }

                                    @Override
                                    public void createFileSuccess(DriveFolder.DriveFileResult result) {
                                        ToastUtils.show(MainActivity.this, "Created a file: " + result.getDriveFile().getDriveId());
                                    }
                                });
                            }
                        });
                    }
                }
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        driverHelper.connect(new DriverHelper.ConnectionCallbacks() {
            @Override
            public void connected() {
                ToastUtils.show(MainActivity.this, "Connected");
//                DriveFolder rootFolder = Drive.DriveApi.getRootFolder(driverHelper.getGoogleApiClient());
                final String folderId = pref.getString("folder_id", null);
                driverHelper.fetchListFile(folderId, new DriverHelper.FetchListFile() {
                    @Override
                    public void onResult(List<DriverHelper.DriveEntity> result) {
                        if (result != null && result.size() > 0) {
                            List<String> files = new ArrayList<String>();
                            for (DriverHelper.DriveEntity md : result) {
                                files.add(md.getTitle() + " isTrashed: "+md.isTrashed());
                            }
                            ArrayAdapter adapter = new ArrayAdapter(MainActivity.this, android.R.layout.simple_list_item_1, files);
                            lvFile.setAdapter(adapter);
                        }
                    }
                });
            }

            @Override
            public void suspended() {
                ToastUtils.show(MainActivity.this, "ConnectionSuspended");
            }
        }, new DriverHelper.OnConnectFailed() {
            @Override
            public void onFailed(ConnectionResult connectionResult) {
                driverHelper.resolveConnectionRequest(connectionResult);
            }
        });
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        driverHelper.onActivityResult(requestCode, resultCode, data);
    }
}
