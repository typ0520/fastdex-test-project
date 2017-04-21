package com.android.tools.fd.client;

/**
 * Created by tong on 17/4/21.
 */

public class NullLogger implements ILogger {
    @Override
    public void error(Throwable t, String msgFormat, Object... args) {

    }

    @Override
    public void warning(String msgFormat, Object... args) {

    }

    @Override
    public void info(String msgFormat, Object... args) {

    }

    @Override
    public void verbose(String msgFormat, Object... args) {

    }
}
