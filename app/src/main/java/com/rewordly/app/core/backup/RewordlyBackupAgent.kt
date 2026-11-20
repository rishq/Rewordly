package com.rewordly.app.core.backup

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.os.ParcelFileDescriptor

class RewordlyBackupAgent : BackupAgent() {
    override fun onFullBackup(data: FullBackupDataOutput?) {
        // Skipping the system implementation uploads nothing; the XML rules still apply when enabled.
        if (BackupSettings.isEnabled(this)) super.onFullBackup(data)
    }

    override fun onBackup(oldState: ParcelFileDescriptor?, data: BackupDataOutput?, newState: ParcelFileDescriptor?) =
        Unit

    override fun onRestore(data: BackupDataInput?, appVersionCode: Int, newState: ParcelFileDescriptor?) = Unit
}
