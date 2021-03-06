/*
 * Copyright (c) 2015-2016 by k3b.
 *
 * This file is part of AndroFotoFinder.
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>
 */
 
package de.k3b.android.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Handler;
import android.view.View;
import android.widget.TextView;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;

import de.k3b.android.androFotoFinder.R;
import de.k3b.io.FileUtils;

/**
 * Special MediaScanner that can only handle inserNew/updateExisting for directories or jp(e)g files.
 *
 * Can handle pause/resume scanning after a directory was scanned before
 * continuing scanning other dirs.
 *
 * Created by k3b on 22.10.2015.
 */
public class RecursiveMediaScanner extends MediaScanner {
    /** Either
     * - current running scanner instance
     * - or reumable instanc */
    public static RecursiveMediaScanner sScanner = null;

    // statistics displayed in the status dialog
    private String mCurrentFolder = "";
    private int mCount = 0;

    private AlertDialog mStatusDialog = null;
    private Handler mTimerHandler = null;
    private Runnable mTimerRunner = null;

    /** if not null scanner is either
     * - in resume mode (can be started without parameters to resume interrupted scan)
     * - or in pausing mode collecting all canceled scans here to be processed in resumeIfNecessary() */
    private List<String> mPaused = null;

    public RecursiveMediaScanner(Context context, String why) {
        super(context, why);
    }

    @Override
    protected Integer doInBackground(String[]... pathNames) {
        // do not call super.doInBackground here because logic is different
        int resultCount = 0;
        for (String[] pathArray : pathNames) {
            if (pathArray != null) {
                for (String pathName : pathArray) {
                    if ((pathName != null) && (pathName.length() > 0)) {
                        resultCount += scanDirOrFile(new File(pathName));
                    }
                }
            }
        }
        return resultCount;
    }

    private int scanDirOrFile(File parent) {
        int resultCount = 0;
        final String parentPath = FileUtils.tryGetCanonicalPath(parent, null);
        if (parentPath != null) {
            if (!isCancelled()) {
                if (parent.isDirectory()) {
                    String[] childFileNames = parent.list(new FilenameFilter() {
                        @Override
                        public boolean accept(File dir, String filename) {
                            return MediaScanner.isJpeg(filename);
                        }
                    });

                    if (childFileNames != null) {
                        // #33
                        // convert to absolute paths
                        for (int i = 0; i < childFileNames.length; i++) {
                            childFileNames[i] = parentPath + "/" + childFileNames[i];
                        }
                        resultCount += runScanner(parentPath, childFileNames);
                    }

                    File[] subDirs = parent.listFiles(new FileFilter() {
                        @Override
                        public boolean accept(File file) {
                            return ((file != null) && (file.isDirectory()) && (!file.getName().startsWith(".")));
                        }
                    });

                    if (subDirs != null) {
                        // #33
                        for (File subDir : subDirs) {
                            if (subDir != null) {
                                resultCount += scanDirOrFile(subDir);
                            }
                        }
                    }
                } else if (MediaScanner.isJpeg(parent.getName())) {
                    resultCount += runScanner(parentPath, parentPath);
                }
            } else if (mPaused != null) {
                mPaused.add(parentPath);
            }
        }
        return resultCount;
    }

    /** @return true if scanner was resumable and started resume operation. */
    public boolean resumeIfNeccessary() {
        if ((getStatus() == AsyncTask.Status.PENDING) && (mPaused != null))
        {
            execute(mPaused.toArray(new String[mPaused.size()]));
            mPaused = null;
            return true;
        }
        return false;
    }

    /** call the original background scanner and update the statistics */
    private Integer runScanner(String parentPath, String... fileNames) {
        this.mCurrentFolder = parentPath;
        final Integer resultCount = super.doInBackground(null, fileNames);
        if (resultCount != null) {
            this.mCount += resultCount.intValue();
        }
        return resultCount;
    }

    @Override protected void onPostExecute(Integer modifyCount) {
        super.onPostExecute(modifyCount);
        if (isCancelled()) {
            handleScannerCancel();
        } else {
            onStatusDialogEnd(null, false);
            if (sScanner == this) {
                sScanner = null;
            }
        }

    }

    private void handleScannerCancel() {
        final boolean mustCreateResumeScanner = (mPaused != null) && ((this == RecursiveMediaScanner.sScanner) || (null == RecursiveMediaScanner.sScanner));
        onStatusDialogEnd(mPaused, false);

        if (sScanner == this) {
            sScanner = null;
        }

        if (mustCreateResumeScanner) {
            RecursiveMediaScanner newScanner = new RecursiveMediaScanner(mContext,"resumed " + mWhy);
            newScanner.mPaused = this.mPaused;
            RecursiveMediaScanner.sScanner = newScanner;
        }
    }

    @Override
    protected void onCancelled(Integer result) {
        // super.onCancelled();
        handleScannerCancel();
    }

    /** returns null if scanner is not busy-active */
    public static RecursiveMediaScanner getBusyScanner() {
        if ((RecursiveMediaScanner.sScanner != null) && (RecursiveMediaScanner.sScanner.getStatus() == Status.RUNNING)) {
            return RecursiveMediaScanner.sScanner;
        }
        return null;
    }

    public void showStatusDialog(final Activity parent) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(parent);
        builder.setTitle(R.string.scanner_menu_title);
        View content = parent.getLayoutInflater().inflate(R.layout.dialog_scanner_status, null);

        final TextView folder = (TextView) content.findViewById(R.id.folder);
        final TextView count = (TextView) content.findViewById(R.id.count);

        builder.setView(content);
        builder.setNeutralButton(R.string.btn_cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                onStatusDialogEnd(null, true);
            }
        });
        builder.setNegativeButton(R.string.btn_pause, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                onStatusDialogEnd(new ArrayList<String>(), true);
            }
        });

        builder.setPositiveButton(R.string.background, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                onStatusDialogEnd(null, false);
            }
        });
        mStatusDialog = builder.create();
        mStatusDialog.show();

        mTimerHandler = new Handler();
        mTimerRunner = new Runnable() {
            @Override
            public void run() {
                if (mStatusDialog != null) {
                    RecursiveMediaScanner scanner = RecursiveMediaScanner.this;
                    folder.setText(scanner.mCurrentFolder);
                    count.setText(parent.getString(R.string.image_loading_at_position_format, scanner.mCount));
                    if (scanner.mTimerRunner != null) {
                        mTimerHandler.postDelayed(scanner.mTimerRunner, 500); // e.g. 500 milliseconds
                    }
                }
            }
        };
        this.mTimerRunner.run();
    }

    private void onStatusDialogEnd(List<String> pauseState, boolean cancelScanner) {
        // no more timer gui updates
        if ((mTimerRunner != null) && (mTimerHandler != null)) {
            mTimerHandler.removeCallbacks(mTimerRunner);
            mTimerHandler = null;
            mTimerRunner = null;
        }

        if (mStatusDialog != null) {
            mStatusDialog.dismiss();
            mStatusDialog = null;
        }

        if (cancelScanner) {
            mPaused = pauseState;
            cancel(false);

            if (RecursiveMediaScanner.sScanner == this) {
                RecursiveMediaScanner.sScanner = null;
            }
        }
    }
}
