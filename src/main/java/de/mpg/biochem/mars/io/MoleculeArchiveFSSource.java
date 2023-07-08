package de.mpg.biochem.mars.io;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;

public class MoleculeArchiveFSSource implements MoleculeArchiveSource {

    /**
     * The archive file with .yama extension.
     */
    protected File file;

    public MoleculeArchiveFSSource(File file) {
        this.file = file;
    }

    public File getFile() {
        return file;
    }

    public String getName() {
        return file.getName();
    }

    public InputStream getInputStream() throws IOException {
        return Files.newInputStream(file.toPath());
    }

    public OutputStream getOutputStream() throws IOException {
        return Files.newOutputStream(file.toPath());
    }

}
