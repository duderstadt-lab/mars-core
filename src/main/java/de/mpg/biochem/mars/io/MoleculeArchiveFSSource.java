package de.mpg.biochem.mars.io;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.format.DataFormatDetector;
import com.fasterxml.jackson.core.format.DataFormatMatcher;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;

import java.io.*;
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

    public String getArchiveType() throws IOException {
        InputStream inputStream = new BufferedInputStream(Files.newInputStream(file.toPath()));

        // Here we automatically detect the format of the JSON file
        // Can be JSON text or Smile encoded binary file...
        JsonFactory jsonF = new JsonFactory();
        SmileFactory smileF = new SmileFactory();
        DataFormatDetector det = new DataFormatDetector(jsonF,
                smileF);
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
            //logService.warn("The file " + file.getName() +
            //        " doesn't have a MoleculeArchiveProperties field. Is this a proper yama file?");

        jParser.close();
        inputStream.close();

        return archiveType;
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
