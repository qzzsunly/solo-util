package cn.sexycode.util.core.file;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;

/**
 * Helper for dealing with archives
 *
 * @author Emmanuel Bernard
 * @author Steve Ebersole
 */
public class ArchiveHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArchiveHelper.class);

    /**
     * Get the JAR URL of the JAR containing the given entry
     * Method used in a non managed environment
     *
     * @param url   URL pointing to the known file in the JAR
     * @param entry file known to be in the JAR
     * @return the JAR URL
     * @throws IllegalArgumentException if none URL is found
     */
    public static URL getJarURLFromURLEntry(URL url, String entry) throws IllegalArgumentException {
        URL jarUrl;
        String file = url.getFile();
        if (!entry.startsWith("/")) {
            entry = "/" + entry;
        }
        file = file.substring(0, file.length() - entry.length());
        if (file.endsWith("!")) {
            file = file.substring(0, file.length() - 1);
        }
        try {
            final String protocol = url.getProtocol();

            if ("jar".equals(protocol) || "wsjar".equals(protocol)) {
                //Original URL is like jar:protocol
                //WebSphere has it's own way
                jarUrl = new URL(file);
                if ("file".equals(jarUrl.getProtocol())) {
                    if (file.indexOf(' ') != -1) {
                        //not escaped, need to voodoo; goes by toURI to escape the path
                        jarUrl = new File(jarUrl.getFile()).toURI().toURL();
                    }
                }
            } else if ("zip".equals(protocol)
                    //OC4J prevent ejb.jar access (ie everything without path)
                    || "code-source".equals(url.getProtocol())
                    //if no wrapping is done
                    || "file".equals(protocol)) {
                //we have extracted the zip file, so it should be read as a file
                if (file.indexOf(' ') != -1) {
                    //not escaped, need to voodoo; goes by toURI to escape the path
                    jarUrl = new File(file).toURI().toURL();
                } else {
                    jarUrl = new File(file).toURL();
                }
            } else {
                try {
                    //We reconstruct the URL probably to make it work in some specific environments
                    //Forgot the exact details, sorry (and the Git history does not help)
                    jarUrl = new URL(protocol, url.getHost(), url.getPort(), file);
                }
                //HHH-6442: Arquilian
                catch (final MalformedURLException e) {
                    //Just use the provided URL as-is, likely it has a URLStreamHandler
                    //associated w/ the instance
                    jarUrl = url;
                }
            }
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(
                    "Unable to determine JAR Url from " + url + ". Cause: " + e.getMessage());
        }
        LOGGER.trace("JAR URL from URL Entry: " + url + " >> " + jarUrl);
        return jarUrl;
    }

    /**
     * get the URL from a given path string
     *
     * @param jarPath The path that represents a URL
     * @return The resolved URL reference
     * @throws IllegalArgumentException is something goes wrong
     */
    public static URL getURLFromPath(String jarPath) {
        URL jarUrl;
        try {
            //is it an url
            jarUrl = new URL(jarPath);
        } catch (MalformedURLException e) {
            try {
                //consider it as a file path
                jarUrl = new URL("file:" + jarPath);
            } catch (MalformedURLException ee) {
                throw new IllegalArgumentException("Unable to find jar:" + jarPath, ee);
            }
        }
        return jarUrl;
    }

    /**
     * Extracts the bytes out of an InputStream.  This form is the same as {@link #getBytesFromInputStream}
     * except that any {@link IOException} are wrapped as (runtime) {@link ArchiveException}
     *
     * @param inputStream The stream from which to extract bytes.
     * @return The bytes
     * @throws ArchiveException Indicates a problem accessing the stream
     */
    public static byte[] getBytesFromInputStreamSafely(InputStream inputStream) throws ArchiveException {
        try {
            return getBytesFromInputStream(inputStream);
        } catch (IOException e) {
            throw new ArchiveException("Unable to extract bytes from InputStream", e);
        }
    }

    /**
     * Extracts the bytes out of an InputStream.
     *
     * @param inputStream The stream from which to extract bytes.
     * @return The bytes
     * @throws IOException Indicates a problem accessing the stream
     * @see #getBytesFromInputStreamSafely(java.io.InputStream)
     */
    public static byte[] getBytesFromInputStream(InputStream inputStream) throws IOException {
        // Optimized by HHH-7835
        int size;
        final List<byte[]> data = new LinkedList<byte[]>();
        final int bufferSize = 4096;
        byte[] tmpByte = new byte[bufferSize];
        int offset = 0;
        int total = 0;
        for (; ; ) {
            size = inputStream.read(tmpByte, offset, bufferSize - offset);
            if (size == -1) {
                break;
            }

            offset += size;

            if (offset == tmpByte.length) {
                data.add(tmpByte);
                tmpByte = new byte[bufferSize];
                offset = 0;
                total += tmpByte.length;
            }
        }

        final byte[] result = new byte[total + offset];
        int count = 0;
        for (byte[] arr : data) {
            System.arraycopy(arr, 0, result, count * arr.length, arr.length);
            count++;
        }
        System.arraycopy(tmpByte, 0, result, count * tmpByte.length, offset);

        return result;
    }

    private ArchiveHelper() {
    }
}
