package com.hierynomus.smbj.share;

import com.hierynomus.mssmb2.messages.SMB2ReadResponse;
import java.util.concurrent.Future;

/** Exposes SMBJ's package-private asynchronous read for bounded read-ahead. */
public final class AsyncFileReader {
    private AsyncFileReader() {}

    public static Future<SMB2ReadResponse> read(File file, long offset, int length) {
        return file.readAsync(offset, length);
    }
}
