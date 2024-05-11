package group.gnometrading.resources;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.MissingResourceException;

public class LibraryLoader {

    /**
     * Loads a native shared library. Attempts to load the library via System#loadLibrary
     * first. If this fails, attempts to load the library via a resource packed in the jar.
     *
     * @param name name of the library to load
     * @throws IOException if the library cannot be extracted from a jar file into a temporary file
     */
    public static void loadLibrary(final String name) throws IOException {
        try {
            System.loadLibrary(name);
        } catch (UnsatisfiedLinkError e) {
            final String filename = System.mapLibraryName(name);
            final InputStream in = LibraryLoader.class.getClassLoader().getResourceAsStream(filename);
            if (in == null) {
                throw new MissingResourceException("Cannot get resource native resource: " + filename, LibraryLoader.class.getName(), filename);
            }
            final int pos = filename.lastIndexOf('.');
            final File file = File.createTempFile(filename.substring(0, pos), filename.substring(pos));
            Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            System.load(file.getAbsolutePath());
        }
    }
}
