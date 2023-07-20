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

public class MoleculeArchiveFSSource implements MoleculeArchiveSource {

    /**
     * The archive file or virtual store directory with .yama.store
     * extension
     */
    private File file;

    private String storeFileExtension = ".sml";

    /**
     * Use to read from an archive or create one.
     *
     * @param file the file or directory.
     */
    public MoleculeArchiveFSSource(File file) {
        this.file = file;
    }

    /**
     * Use to read from an archive or create one.
     *
     * @param path the full file path.
     */
    public MoleculeArchiveFSSource(String path) {
        this.file = new File(path);
    }
    @Override
    public void initializeLocation() {
        //Create directories if they do not exist.
        if (!file.exists()) file.mkdirs();

        File metadataDir = new File(file.getAbsolutePath() +
                "/" + METADATA_SUBDIRECTORY_NAME);
        if (!metadataDir.exists()) metadataDir.mkdirs();

        File moleculesDir = new File(file.getAbsolutePath() +
                "/" + MOLECULES_SUBDIRECTORY_NAME);
        if (!moleculesDir.exists()) moleculesDir.mkdirs();

        //Check for encoding. Default to smile.
        if (new File(file.getAbsolutePath() +
                "/" + PROPERTIES_FILE_NAME + ".sml").exists()) storeFileExtension = ".sml";
        else if (new File(file.getAbsolutePath() +
                "/" + PROPERTIES_FILE_NAME + ".json").exists()) {
            storeFileExtension = ".json";
        } else storeFileExtension = ".sml";
    }

    @Override
    public void setPath(String path) {
        this.file = new File(path);
    }

    @Override
    public String getPath() {
        return file.getAbsolutePath();
    }

    public String getName() {
        return file.getName();
    }

    @Override
    public boolean isVirtual() {
        return file.isDirectory();
    }

    public InputStream getInputStream() throws IOException {
        return Files.newInputStream(file.toPath());
    }

    public OutputStream getOutputStream() throws IOException {
        return Files.newOutputStream(file.toPath());
    }

    public String getArchiveType() throws IOException {
        // We will automatically detect the format of the JSON file
        // Can be JSON text or Smile encoded binary file...
        JsonFactory jsonF = new JsonFactory();
        SmileFactory smileF = new SmileFactory();
        DataFormatDetector det = new DataFormatDetector(jsonF,
                smileF);

        if (file.isDirectory()) {
            File propertiesFile = new File(file.getAbsolutePath() +
                    "/" + PROPERTIES_FILE_NAME + ".json");
            if (propertiesFile.exists()) storeFileExtension = ".json";
            else {
                storeFileExtension = ".sml";
                propertiesFile = new File(file.getAbsolutePath() +
                        "/" + PROPERTIES_FILE_NAME + ".sml");
            }
            InputStream propertiesInputStream = new BufferedInputStream(
                    Files.newInputStream(propertiesFile.toPath()));
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
        } else {
            InputStream inputStream = new BufferedInputStream(Files.newInputStream(file.toPath()));
            DataFormatMatcher match = det.findFormat(inputStream);
            JsonParser jParser = match.createParserWithMatch();

            String archiveType = "de.mpg.biochem.mars.molecule.SingleMoleculeArchive";

            jParser.nextToken();
            jParser.nextToken();
            if ("properties".equals(jParser.getCurrentName()) ||
                    "MoleculeArchiveProperties".equals(jParser.getCurrentName()))
            {
                jParser.nextToken();
                while (jParser.nextToken() != JsonToken.END_OBJECT) {
                    String fieldName = jParser.getCurrentName();

                    if ("archiveType".equals(fieldName) || "ArchiveType".equals(
                            fieldName))
                    {
                        jParser.nextToken();
                        archiveType = jParser.getText();
                        break;
                    }
                }
            }

            jParser.close();
            inputStream.close();

            return archiveType;
        }
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
        File moleculeFile = new File(file.getAbsolutePath() + "/" + MOLECULES_SUBDIRECTORY_NAME + "/" +
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
        File metadataFile = new File(file.getAbsolutePath() + "/" + METADATA_SUBDIRECTORY_NAME + "/" +
                metaUID + storeFileExtension);
        if (metadataFile.exists()) metadataFile.delete();
    }

    public List<String> getMoleculeUIDs() {
        String[] moleculeFileNameIndex = new File(file.getAbsolutePath() +
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
        String[] metadataFileNameIndex = new File(file.getAbsolutePath() +
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
        File newfile = new File(file.getAbsolutePath() + subPath);
        return Files.newInputStream(newfile.toPath());
    }

    private OutputStream getOutputStream(String subPath) throws IOException {
        File newfile = new File(file.getAbsolutePath() + subPath);
        return Files.newOutputStream(newfile.toPath());
    }

    public String getFileExtension() {
        return storeFileExtension;
    }

    @Override
    public String getURI() {
        return "/";
    }

    @Override
    public boolean exists(String pathName) throws IOException {
        return new File(pathName).exists();
    }

    @Override
    public String[] list(String pathName) throws IOException {
        pathName = (pathName.startsWith(getGroupSeparator())) ? pathName : getGroupSeparator() + pathName;
        File path = new File(pathName);
        if (!path.isDirectory()
                || path.getName().endsWith("." + MOLECULE_ARCHIVE_STORE_ENDING)
                || path.getName().endsWith("." + N5_DATASET_DIRECTORY_ENDING))
            return new String[0];

        return path.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                if (name.startsWith(".") || name.startsWith("~$")) return false;
                return true;
            }
        });
    }
}
