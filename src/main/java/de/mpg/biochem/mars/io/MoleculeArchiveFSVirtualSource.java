package de.mpg.biochem.mars.io;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.format.DataFormatDetector;
import com.fasterxml.jackson.core.format.DataFormatMatcher;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;

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
                "/" + METADATA_SUBDIRECTORY_NAME);
        if (!metadataDir.exists()) metadataDir.mkdirs();

        File moleculesDir = new File(directory.getAbsolutePath() +
                "/" + MOLECULES_SUBDIRECTORY_NAME);
        if (!moleculesDir.exists()) moleculesDir.mkdirs();

        //Check for encoding. Default to smile.
        if (new File(directory.getAbsolutePath() +
                "/" + PROPERTIES_FILE_NAME + ".sml").exists()) storeFileExtension = ".sml";
        else if (new File(directory.getAbsolutePath() +
                "/" + PROPERTIES_FILE_NAME + ".json").exists()) {
            storeFileExtension = ".json";
        } else storeFileExtension = ".sml";
    }

    public File getStoreDirectory() {
        return directory;
    }

    public String getName() {
        return directory.getName();
    }

    public String getArchiveType() throws IOException {
        File propertiesFile = new File(directory.getAbsolutePath() +
                "/" + PROPERTIES_FILE_NAME + ".json");
        if (propertiesFile.exists()) storeFileExtension = ".json";
        else {
            storeFileExtension = ".sml";
            propertiesFile = new File(directory.getAbsolutePath() +
                    "/" + PROPERTIES_FILE_NAME + ".sml");
        }
        InputStream propertiesInputStream = new BufferedInputStream(
                Files.newInputStream(propertiesFile.toPath()));

        // Here we automatically detect the format of the JSON file
        // Can be JSON text or Smile encoded binary file...
        JsonFactory jsonF = new JsonFactory();
        SmileFactory smileF = new SmileFactory();
        DataFormatDetector det = new DataFormatDetector(jsonF,
                smileF);
        DataFormatMatcher match = det.findFormat(propertiesInputStream);
        JsonParser jParser = match.createParserWithMatch();

        String archiveType = "de.mpg.biochem.mars.molecule.SingleMoleculeArchive";

        while (jParser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = jParser.getCurrentName();

            if ("archiveType".equals(fieldName) || "ArchiveType".equals(fieldName)) {
                jParser.nextToken();
                archiveType = jParser.getText();
                break;
            }
        }

        jParser.close();
        propertiesInputStream.close();

        return archiveType;
    }

    public InputStream getPropertiesInputStream() throws IOException {
        return getInputStream("/" + PROPERTIES_FILE_NAME + storeFileExtension);
    }

    public OutputStream getPropertiesOutputStream() throws IOException {
        return getOutputStream("/" + PROPERTIES_FILE_NAME + storeFileExtension);
    }

    public InputStream getIndexesInputStream() throws IOException {
        return getInputStream("/" + INDEXES_FILE_NAME + storeFileExtension);
    }

    public OutputStream getIndexesOutputStream() throws IOException {
        return getOutputStream("/" + INDEXES_FILE_NAME + storeFileExtension);
    }

    public InputStream getMoleculeInputStream(String UID) throws IOException {
        return getInputStream("/" + MOLECULES_SUBDIRECTORY_NAME + "/" + UID + storeFileExtension);
    }

    public OutputStream getMoleculeOutputStream(String UID) throws IOException {
        return getOutputStream("/" + MOLECULES_SUBDIRECTORY_NAME + "/" + UID + storeFileExtension);
    }

    @Override
    public void removeMolecule(String UID) {
        File moleculeFile = new File(directory.getAbsolutePath() + "/" + MOLECULES_SUBDIRECTORY_NAME + "/" +
                UID + storeFileExtension);
        if (moleculeFile.exists()) moleculeFile.delete();
    }

    public InputStream getMetadataInputStream(String metaUID) throws IOException {
        return getInputStream("/" + METADATA_SUBDIRECTORY_NAME + "/" + metaUID + storeFileExtension);
    }

    public OutputStream getMetadataOutputStream(String metaUID) throws IOException {
        return getOutputStream("/" + METADATA_SUBDIRECTORY_NAME + "/" + metaUID + storeFileExtension);
    }

    public void removeMetadata(String metaUID) {
        File metadataFile = new File(directory.getAbsolutePath() + "/" + METADATA_SUBDIRECTORY_NAME + "/" +
                metaUID + storeFileExtension);
        if (metadataFile.exists()) metadataFile.delete();
    }

    public List<String> getMoleculeUIDs() {
        String[] moleculeFileNameIndex = new File(directory.getAbsolutePath() +
                "/" + MOLECULES_SUBDIRECTORY_NAME).list((dir, name) -> name.endsWith(storeFileExtension));

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
                "/" + METADATA_SUBDIRECTORY_NAME).list((dir, name) -> name.endsWith(storeFileExtension));

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
