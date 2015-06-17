package com.malykhin.http;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;

import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;

import com.malykhin.io.OutputStreamWithProgress;
import com.malykhin.io.OutputStreamWithProgress.ProgressListener;

/**
 * 
 * @author Andrey Malykhin
 *
 */
public class MultipartEntityWithProgress extends MultipartEntity {

	private final ProgressListener progressListener;

    public MultipartEntityWithProgress(final ProgressListener listener) {
        super();
        this.progressListener = listener;
    }

    public MultipartEntityWithProgress(final HttpMultipartMode mode, 
    		final ProgressListener listener) 
    {
        super(mode);
        this.progressListener = listener;
    }

    public MultipartEntityWithProgress(HttpMultipartMode mode, final String boundary,
            final Charset charset, final ProgressListener listener) 
    {
        super(mode, boundary, charset);
        this.progressListener = listener;
    }

    @Override
    public void writeTo(final OutputStream outstream) throws IOException {
        super.writeTo(new OutputStreamWithProgress(outstream, this.progressListener));
    }

}
