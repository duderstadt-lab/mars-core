package de.mpg.biochem.mars.io;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class MoleculeArchiveFSVirtualSource implements MoleculeArchiveVirtualSource {

    /**
     * The archive virtual store directory with .yama.store
     * extension
     */
    private File directory;

    private String storeFileExtension = ".sml";

    /**
     * Use to read from a virtual source or create a virtual source
     * in directory.
     *
     * @param directory the directory for virtual source reading and writing.
     */
    public MoleculeArchiveFSVirtualSource(File directory) {
        this.directory = directory;

        //Create directories if they do not exist.
        if (!directory.exists()) directory.mkdirs();

        File metadataDir = new File(directory.getAbsolutePath() +
                "/Metadata");
        if (!metadataDir.exists()) metadataDir.mkdirs();

        File moleculesDir = new File(directory.getAbsolutePath() +
                "/Molecules");
        if (!moleculesDir.exists()) moleculesDir.mkdirs();

        //Check for encoding. Default to smile.
        if (new File(directory.getAbsolutePath() +
                "/MoleculeArchiveProperties.sml").exists()) storeFileExtension = ".sml";
        else if (new File(directory.getAbsolutePath() +
                "/MoleculeArchiveProperties.json").exists()) {
            storeFileExtension = ".json";
        } else storeFileExtension = ".sml";
    }

    public File getStoreDirectory() {
        return directory;
    }

    public String getName() {
        return directory.getName();
    }

    public InputStream getPropertiesInputStream() throws IOException {
        return getInputStream("/MoleculeArchiveProperties" + storeFileExtension);
    }

    public OutputStream getPropertiesOutputStream() throws IOException {
        return getOutputStream("/MoleculeArchiveProperties" + storeFileExtension);
    }

    public InputStream getIndexesInputStream() throws IOException {
        return getInputStream("/indexes" + storeFileExtension);
    }

    public OutputStream getIndexesOutputStream() throws IOException {
        return getOutputStream("/indexes" + storeFileExtension);
    }

    public InputStream getMoleculeInputStream(String UID) throws IOException {
        return getInputStream("/Molecules/" + UID + storeFileExtension);
    }

    public OutputStream getMoleculeOutputStream(String UID) throws IOException {
        return getOutputStream("/Molecules/" + UID + storeFileExtension);
    }

    @Override
    public void removeMolecule(String UID) {
        File moleculeFile = new File(directory.getAbsolutePath() + "/Molecules/" +
                UID + storeFileExtension);
        if (moleculeFile.exists()) moleculeFile.delete();
    }

    public InputStream getMetadataInputStream(String metaUID) throws IOException {
        return getInputStream("/Metadata/" + metaUID + storeFileExtension);
    }

    public OutputStream getMetadataOutputStream(String metaUID) throws IOException {
        return getOutputStream("/Metadata/" + metaUID + storeFileExtension);
    }

    public void removeMetadata(String metaUID) {
        File metadataFile = new File(directory.getAbsolutePath() + "/Metadata/" +
                metaUID + storeFileExtension);
        if (metadataFile.exists()) metadataFile.delete();
    }

    public List<String> getMoleculeUIDs() {
        String[] moleculeFileNameIndex = new File(directory.getAbsolutePath() +
                "/Molecules").list((dir, name) -> name.endsWith(storeFileExtension));

        if (moleculeFileNameIndex != null) {
            List<String> UIDs = new ArrayList<>();
            for (String fileNameIndex : moleculeFileNameIndex) {
                String UID = fileNameIndex.substring(0,
                        fileNameIndex.length() - storeFileExtension.length());
                UIDs.add(UID);
            }
            return UIDs;
        } else return new ArrayList<>();
    }

    public List<String> getMetadataUIDs() {
        String[] metadataFileNameIndex = new File(directory.getAbsolutePath() +
                "/Metadata").list((dir, name) -> name.endsWith(storeFileExtension));

        if (metadataFileNameIndex != null) {
            List<String> UIDs = new ArrayList<>();
            for (String fileNameIndex : metadataFileNameIndex) {
                String UID = fileNameIndex.substring(0,
                        fileNameIndex.length() - storeFileExtension.length());
                UIDs.add(UID);
            }
            return UIDs;
        } else return new ArrayList<>();
    }

    private InputStream getInputStream(String subPath) throws IOException {
        File file = new File(directory.getAbsolutePath() + subPath);
        return Files.newInputStream(file.toPath());
    }

    private OutputStream getOutputStream(String subPath) throws IOException {
        File file = new File(directory.getAbsolutePath() + subPath);
        return Files.newOutputStream(file.toPath());
    }

    public String getFileExtension() {
        return storeFileExtension;
    }
}
