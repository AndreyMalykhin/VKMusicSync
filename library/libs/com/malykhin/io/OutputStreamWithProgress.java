package com.malykhin.io;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * 
 * @author Andrey Malykhin
 *
 */
public class OutputStreamWithProgress extends FilterOutputStream {

	public interface ProgressListener {
		public void onProgressChanged(long transferredBytesCount);

		/**
		 * How often listener will be notified about changed progress. In milliseconds.
		 */
		public int getNotificationFrequency();
	}
	
    private final ProgressListener progressListener;
    private long transferredBytesCount = 0;
    private long previousTickTime;

    public OutputStreamWithProgress(final OutputStream out, 
    		final ProgressListener progressListener) 
    {
        super(out);
        this.progressListener = progressListener;
        previousTickTime = System.currentTimeMillis();
    }

    public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
        transferredBytesCount += len;
        notifyAboutProgressChange();
    }

    public void write(int b) throws IOException {
        out.write(b);
        transferredBytesCount++;
        notifyAboutProgressChange();
    }
    
    private void notifyAboutProgressChange() {
    	long currentTime = System.currentTimeMillis();
    	
    	if (currentTime >= previousTickTime + progressListener.getNotificationFrequency()) {
    		progressListener.onProgressChanged(transferredBytesCount);
    		previousTickTime = currentTime;
    	}
    }
}