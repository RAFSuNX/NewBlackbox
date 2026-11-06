package top.niunaijun.blackbox.utils;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Parcel;
import android.system.Os;
import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FileUtils {

    private static final int BUFFER_SIZE = 8192;

    public static int count(File file) {
        if (!file.exists()) return -1;
        if (file.isFile()) return 1;
        if (file.isDirectory()) {
            String[] fs = file.list();
            return fs == null ? 0 : fs.length;
        }
        return 0;
    }

    public static String getFilenameExt(String filename) {
        int dotPos = filename.lastIndexOf('.');
        return dotPos == -1 ? "" : filename.substring(dotPos + 1);
    }

    public static File changeExt(File f, String targetExt) {
        String outPath = f.getAbsolutePath();
        if (!getFilenameExt(outPath).equals(targetExt)) {
            int dotPos = outPath.lastIndexOf(".");
            outPath = dotPos > 0
                    ? outPath.substring(0, dotPos + 1) + targetExt
                    : outPath + "." + targetExt;
            return new File(outPath);
        }
        return f;
    }

    public static boolean renameTo(File origFile, File newFile) {
        return origFile.renameTo(newFile);
    }

    public static String readToString(String fileName) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(fileName)))) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[BUFFER_SIZE];
            int len;
            while ((len = reader.read(buf)) != -1) sb.append(buf, 0, len);
            return sb.toString();
        }
    }

    public static Parcel readToParcel(File file) throws IOException {
        Parcel in = Parcel.obtain();
        byte[] bytes = toByteArray(file);
        in.unmarshall(bytes, 0, bytes.length);
        in.setDataPosition(0);
        return in;
    }

    public static void chmod(String path, int mode) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                Os.chmod(path, mode);
                return;
            } catch (Exception ignored) {
            }
        }
        File file = new File(path);
        String cmd = "chmod " + (file.isDirectory() ? "-R " : "");
        Runtime.getRuntime().exec(cmd + String.format("%o", mode) + " " + path).waitFor();
    }

    public static void createSymlink(String oldPath, String newPath) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                Os.link(oldPath, newPath);
                return;
            } catch (Throwable ignored) {
            }
        }
        Runtime.getRuntime().exec("ln -s " + oldPath + " " + newPath).waitFor();
    }

    public static boolean isSymlink(File file) throws IOException {
        if (file == null) throw new NullPointerException("File must not be null");
        File canon;
        if (file.getParent() == null) {
            canon = file;
        } else {
            File canonDir = file.getParentFile().getCanonicalFile();
            canon = new File(canonDir, file.getName());
        }
        return !canon.getCanonicalFile().equals(canon.getAbsoluteFile());
    }

    public static void writeParcelToFile(Parcel p, File file) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(p.marshall());
        }
    }

    public static void writeParcelToOutput(Parcel p, FileOutputStream fos) throws IOException {
        fos.write(p.marshall());
    }

    public static byte[] toByteArray(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            return toByteArray(fis);
        }
    }

    public static byte[] toByteArray(InputStream inStream) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[BUFFER_SIZE];
        int len;
        while ((len = inStream.read(buf)) != -1) out.write(buf, 0, len);
        return out.toByteArray();
    }

    public static int deleteDir(File dir) {
        int count = 0;
        if (dir.isDirectory()) {
            boolean link = false;
            try { link = isSymlink(dir); } catch (Exception ignored) {}
            if (!link) {
                String[] children = dir.list();
                if (children != null) {
                    for (String file : children) count += deleteDir(new File(dir, file));
                }
            }
        }
        if (dir.delete()) count++;
        return count;
    }

    public static int deleteDir(String dir) {
        return deleteDir(new File(dir));
    }

    public static void writeToFile(InputStream dataIns, File target) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(target)) {
            byte[] buf = new byte[BUFFER_SIZE];
            int len;
            while ((len = dataIns.read(buf)) != -1) fos.write(buf, 0, len);
        }
    }

    public static void writeToFile(byte[] data, File target) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(target);
             ReadableByteChannel src = Channels.newChannel(new ByteArrayInputStream(data));
             FileChannel out = fos.getChannel()) {
            out.transferFrom(src, 0, data.length);
        }
    }

    public static void copyFile(InputStream inputStream, File target) {
        try (FileOutputStream outputStream = new FileOutputStream(target)) {
            byte[] buf = new byte[BUFFER_SIZE];
            int len;
            while ((len = inputStream.read(buf)) != -1) outputStream.write(buf, 0, len);
        } catch (Throwable ignored) {
        } finally {
            closeQuietly(inputStream);
        }
    }

    public static void copyFile(File source, File target) throws IOException {
        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(target)) {
            in.getChannel().transferTo(0, source.length(), out.getChannel());
        }
    }

    public static void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try { closeable.close(); } catch (Exception ignored) {}
        }
    }

    public static int peekInt(byte[] bytes, int value, ByteOrder endian) {
        int v0, v2;
        if (endian == ByteOrder.BIG_ENDIAN) {
            v0 = value + 1; v2 = v0 + 1;
            return (bytes[value] & 255) << 24 | (bytes[v0] & 255) << 16
                    | (bytes[v2] & 255) << 8 | bytes[v2 + 1] & 255;
        } else {
            v0 = value + 1; v2 = v0 + 1;
            return bytes[value] & 255 | (bytes[v0] & 255) << 8
                    | (bytes[v2] & 255) << 16 | (bytes[v2 + 1] & 255) << 24;
        }
    }

    private static boolean isValidExtFilenameChar(char c) {
        return c != '\0' && c != '/';
    }

    public static boolean isValidExtFilename(String name) {
        return name != null && name.equals(buildValidExtFilename(name));
    }

    public static String buildValidExtFilename(String name) {
        if (TextUtils.isEmpty(name) || ".".equals(name) || "..".equals(name)) return "(invalid)";
        final StringBuilder res = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            res.append(isValidExtFilenameChar(c) ? c : '_');
        }
        return res.toString();
    }

    public static void mkdirs(File path) {
        if (!path.exists()) path.mkdirs();
    }

    public static void mkdirs(String path) {
        mkdirs(new File(path));
    }

    public static boolean isExist(String path) {
        return new File(path).exists();
    }

    public static boolean canRead(String path) {
        return new File(path).canRead();
    }

    public interface FileMode {
        int MODE_ISUID = 04000;
        int MODE_ISGID = 02000;
        int MODE_ISVTX = 01000;
        int MODE_IRUSR = 00400;
        int MODE_IWUSR = 00200;
        int MODE_IXUSR = 00100;
        int MODE_IRGRP = 00040;
        int MODE_IWGRP = 00020;
        int MODE_IXGRP = 00010;
        int MODE_IROTH = 00004;
        int MODE_IWOTH = 00002;
        int MODE_IXOTH = 00001;

        int MODE_755 = MODE_IRUSR | MODE_IWUSR | MODE_IXUSR
                | MODE_IRGRP | MODE_IXGRP | MODE_IROTH | MODE_IXOTH;
    }

    public static class FileLock {
        private static volatile FileLock singleton;
        private final Map<String, FileLockCount> mRefCountMap = new ConcurrentHashMap<>();

        public static FileLock getInstance() {
            if (singleton == null) {
                synchronized (FileLock.class) {
                    if (singleton == null) singleton = new FileLock();
                }
            }
            return singleton;
        }

        private synchronized int RefCntInc(String filePath, java.nio.channels.FileLock fileLock,
                RandomAccessFile randomAccessFile, FileChannel fileChannel) {
            if (mRefCountMap.containsKey(filePath)) {
                FileLockCount c = mRefCountMap.get(filePath);
                return c.mRefCount++;
            }
            mRefCountMap.put(filePath, new FileLockCount(fileLock, 1, randomAccessFile, fileChannel));
            return 1;
        }

        private synchronized int RefCntDec(String filePath) {
            FileLockCount c = mRefCountMap.get(filePath);
            if (c == null) return 0;
            if (--c.mRefCount <= 0) mRefCountMap.remove(filePath);
            return c.mRefCount;
        }

        public boolean LockExclusive(File targetFile) {
            if (targetFile == null) return false;
            try {
                File lockFile = new File(targetFile.getParent(), "lock");
                if (!lockFile.exists()) lockFile.createNewFile();
                RandomAccessFile raf = new RandomAccessFile(lockFile, "rw");
                FileChannel channel = raf.getChannel();
                java.nio.channels.FileLock lock = channel.lock();
                if (!lock.isValid()) { raf.close(); return false; }
                RefCntInc(lockFile.getAbsolutePath(), lock, raf, channel);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        public void unLock(File targetFile) {
            File lockFile = new File(targetFile.getParent(), "lock");
            if (!lockFile.exists()) return;
            FileLockCount c = mRefCountMap.get(lockFile.getAbsolutePath());
            if (c == null) return;
            try {
                if (RefCntDec(lockFile.getAbsolutePath()) <= 0) {
                    if (c.mFileLock != null && c.mFileLock.isValid()) c.mFileLock.release();
                    closeQuietly(c.fOs);
                    closeQuietly(c.fChannel);
                }
            } catch (IOException ignored) {}
        }

        private static class FileLockCount {
            FileChannel fChannel;
            RandomAccessFile fOs;
            java.nio.channels.FileLock mFileLock;
            int mRefCount;

            FileLockCount(java.nio.channels.FileLock fileLock, int mRefCount,
                    RandomAccessFile fOs, FileChannel fChannel) {
                this.mFileLock = fileLock;
                this.mRefCount = mRefCount;
                this.fOs = fOs;
                this.fChannel = fChannel;
            }
        }
    }

    private static String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {
        final String[] projection = {"_data"};
        try (Cursor cursor = context.getContentResolver()
                .query(uri, projection, selection, selectionArgs, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndexOrThrow("_data"));
            }
        }
        return null;
    }

    private static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    private static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    private static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }
}
