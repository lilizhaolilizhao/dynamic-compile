package com.oneapm.compiler;

import java.io.*;

public class ConcatenatingReader extends FilterReader {
    // Any leftover characters go here
    private char[] curBuf;
    private int curPos;
    private final BufferedReader inReader;
    private static final String NEW_LINE = System.getProperty("line.separator");

    /** This class requires that the input reader be a BufferedReader so
    it can do line-oriented operations. */
    public ConcatenatingReader(BufferedReader in) {
        super(in);
        this.inReader = in;
    }

    @Override
    public int read() throws IOException {
        char[] tmp = new char[1];
        int num = read(tmp, 0, 1);
        if (num < 0) {
            return -1;
        }
        return tmp[0];
    }

    // It's easier not to support mark/reset since we don't need it
    @Override
    public boolean markSupported() {
        return false;
    }

    @Override
    public void mark(int readAheadLimit) throws IOException {
        throw new IOException("mark/reset not supported");
    }

    @Override
    public void reset() throws IOException {
        throw new IOException("mark/reset not supported");
    }

    @Override
    public boolean ready() throws IOException {
        return curBuf != null || inReader.ready();
    }

    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
        if (curBuf == null) {
            nextLine();
        }

        if (curBuf == null) {
            return -1;
        }

        int numRead = 0;

        while ((len > 0) && (curBuf != null) && (curPos < curBuf.length)) {
            cbuf[off] = curBuf[curPos];
            ++curPos;
            ++off;
            --len;
            ++numRead;
            if (curPos == curBuf.length) {
                nextLine();
            }
        }

        return numRead;
    }

    @Override
    public long skip(long n) throws IOException {
        long numSkipped = 0;

        while (n > 0) {
            int intN = (int) n;
            char[] tmp = new char[intN];
            int numRead = read(tmp, 0, intN);
            n -= numRead;
            numSkipped += numRead;
            if (numRead < intN) {
                break;
            }
        }
        return numSkipped;
    }

    private void nextLine() throws IOException {
        String cur = inReader.readLine();
        if (cur == null) {
            curBuf = null;
            return;
        }
        // The trailing newline was trimmed by the readLine() method. See
        // whether we have to put it back or not, depending on whether the
        // last character of the line is the concatenation character.
        int numChars = cur.length();
        boolean needNewline = true;
        if ((numChars > 0) &&
                (cur.charAt(cur.length() - 1) == '\\')) {
            --numChars;
            needNewline = false;
        }
        char[] buf = new char[numChars + (needNewline ? NEW_LINE.length() : 0)];
        cur.getChars(0, numChars, buf, 0);
        if (needNewline) {
            NEW_LINE.getChars(0, NEW_LINE.length(), buf, numChars);
        }
        curBuf = buf;
        curPos = 0;
    }
}
