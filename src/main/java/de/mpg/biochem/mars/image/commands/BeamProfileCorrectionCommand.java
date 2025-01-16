/*-
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2025 Karl Duderstadt
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package de.mpg.biochem.mars.image.commands;

import de.mpg.biochem.mars.util.LogBuilder;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.Roi;
import ij.process.ImageProcessor;
import io.scif.ome.services.OMEXMLService;
import io.scif.services.TranslatorService;
import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imagej.display.ImageDisplay;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.convert.ConvertService;
import org.scijava.log.LogService;
import org.scijava.menu.MenuConstants;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.ChoiceWidget;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/**
 * This command corrects images collected with uneven illumination. The most common
 * use is to correct images collected using a fluorescence microscope with a
 * gaussian beam profile that is maximum in the middle and lowest at the edges.
 * <p>
 * The command requires two images: a 2D background image with the beam profile and the video
 * that should be corrected. For each pixel at position x, y the following is calculated:
 * </p>
 * <p>
 * (Image(x,y) - electronic_offset) / ((Background(x,y) - electronic_offset) /
 * (maximum_pixel_background - electronic_offset)).
 * </p>
 * <p>
 * When finished the active image or image provided will be corrected.
 * </p>
 *
 * @author Karl Duderstadt
 */
@Plugin(type = Command.class, label = "Beam Profile Correction", menu = {@Menu(
        label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
        mnemonic = MenuConstants.PLUGINS_MNEMONIC), @Menu(label = "Mars",
        weight = MenuConstants.PLUGINS_WEIGHT, mnemonic = 'm'), @Menu(
        label = "Image", weight = 1, mnemonic = 'i'), @Menu(label = "Util",
        weight = 7, mnemonic = 'u'), @Menu(label = "Beam Profile Corrector",
        weight = 8, mnemonic = 'b')})
public class BeamProfileCorrectionCommand extends DynamicCommand implements
        Command {

    /**
     * SERVICES
     */
    @Parameter
    private LogService logService;

    @Parameter
    private StatusService statusService;

    @Parameter
    private DatasetService datasetService;

    @Parameter
    private TranslatorService translatorService;

    @Parameter
    private OMEXMLService omexmlService;

    @Parameter
    private ConvertService convertService;

    @Parameter(label = "Image to correct")
    private ImageDisplay imageDisplay;

    @Parameter(label = "Region",
            style = ChoiceWidget.RADIO_BUTTON_VERTICAL_STYLE + ", group:Input",
            choices = { "whole image", "ROI from image"}, persist = false)
    private String region = "whole image";

    @Parameter(label = "Channel", choices = {"a", "b", "c"}, persist = false)
    private String channel = "0";

    @Parameter(label = "Background image", choices = {"a", "b", "c"})
    private String backgroundImageName;

    @Parameter(label = "Electronic offset")
    private double electronicOffset = 0;

    @Parameter(label = "Threads", required = false, min = "1", max = "120")
    private int nThreads = 1;

    // For the progress thread
    private AtomicBoolean progressUpdating = new AtomicBoolean(true);
    private AtomicInteger framesDone = new AtomicInteger(0);

    ImageProcessor backgroundIp;
    double maximumPixelValue;

    private Roi imageRoi, processRegion;

    private Dataset dataset;
    private ImagePlus image;
    private ImagePlus backgroundImage;

    @Override
    public void initialize() {
        if (dataset == null && imageDisplay != null) {
            dataset = (Dataset) imageDisplay.getActiveView().getData();
            image = convertService.convert(imageDisplay, ImagePlus.class);
        }
        else return;

        if (image.getRoi() != null) {
            imageRoi = image.getRoi();
            processRegion = image.getRoi();
            region = "ROI from image";
            final MutableModuleItem<String> regionItem = getInfo().getMutableInput(
                    "region", String.class);
            regionItem.setValue(this, "ROI from image");
        }

		final MutableModuleItem<String> channelItems = getInfo().getMutableInput(
			"channel", String.class);
		long channelCount = dataset.getChannels();
		ArrayList<String> channels = new ArrayList<>();
		for (int ch = 0; ch < channelCount; ch++)
			channels.add(String.valueOf(ch));
		channelItems.setChoices(channels);
		channelItems.setValue(this, String.valueOf(image.getChannel() - 1));

        final MutableModuleItem<String> backgroundItems = getInfo().getMutableInput(
                "backgroundImageName", String.class);

        nThreads = Runtime.getRuntime().availableProcessors();

		// Super Hacky IJ1 workaround for issues in scijava/scifio related to
		// getting images.
		int numberOfImages = WindowManager.getImageCount();
		List<String> imageNames = new ArrayList<>();

        for (int i = 0; i < numberOfImages; i++) {
            ImagePlus img = WindowManager.getImage(i + 1);
            if (img.getStackSize() == 1) imageNames.add(img.getTitle());
        }

        backgroundItems.setChoices(imageNames);
    }

    @Override
    public void run() {
        backgroundImage = WindowManager.getImage(backgroundImageName);

        // Build log
        LogBuilder builder = new LogBuilder();

        String log = LogBuilder.buildTitleBlock("Beam Profile Correction");

        addInputParameterLog(builder);
        log += builder.buildParameterList();

        // Output first part of log message
        logService.info(log);

        if (image != null && imageRoi == null && image.getRoi() != null) {
            imageRoi = image.getRoi();
            processRegion = image.getRoi();
        }

        if (imageRoi == null || region.equals("whole image"))
            processRegion = new Roi(0, 0, (int) dataset.dimension(0),
                    (int) dataset.dimension(1));

        if (image != null) {
            image.deleteRoi();
            image.setOverlay(null);
        }

		// We assume there is just a single frame.
		backgroundIp = backgroundImage.getProcessor();

        // determine maximum pixel value
        maximumPixelValue = 0;

        for (int y = (int)processRegion.getYBase(); y < processRegion.getYBase() + processRegion.getFloatHeight(); y++) {
            for (int x = (int)processRegion.getXBase(); x < processRegion.getXBase() + processRegion.getFloatWidth(); x++) {
                double value = backgroundIp.getf(x, y);
                if (value > maximumPixelValue) maximumPixelValue = value;
            }
        }

		ForkJoinPool forkJoinPool = new ForkJoinPool(nThreads);
		try {
			// Start a thread to keep track of the progress of the number of frames
			// that have been processed.
			// Waiting call back to update the progress bar!!
			Thread progressThread = new Thread() {

                public synchronized void run() {
                    try {
                        while (progressUpdating.get()) {
                            sleep(100);
                            statusService.showStatus(framesDone.intValue(), image
                                    .getNFrames(), "Correcting beam profile for " + image
                                    .getTitle());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };

            progressThread.start();

			// This will spawn a bunch of threads that will correct the beam profile
			// in individual frames
			// in parallel
			forkJoinPool.submit(() -> IntStream.range(0, image.getNFrames())
				.parallel().forEach(t -> correctFrame(Integer.parseInt(channel), t)))
				.get();

            progressUpdating.set(false);

            statusService.showProgress(100, 100);
            statusService.showStatus("Beam profile correction for " + image
                    .getTitle() + " - Done!");

        } catch (InterruptedException | ExecutionException e) {
            // handle exceptions
            e.getStackTrace();
            logService.info(LogBuilder.endBlock(false));
            return;
        } finally {
            forkJoinPool.shutdown();
        }

        if (image != null && imageRoi != null) image.setRoi(imageRoi);
        if (image != null) image.updateAndDraw();
        logService.info(LogBuilder.endBlock(true));
    }

    public void correctFrame(int channel, int t) {
        ImageStack stack = image.getImageStack();
        int index = image.getStackIndex(channel + 1, 1, t + 1);
        ImageProcessor processor = stack.getProcessor(index);

        // subtract electronic offset and divide by background
        for (int y = (int)processRegion.getYBase(); y < processRegion.getYBase() + processRegion.getFloatHeight(); y++) {
            for (int x = (int)processRegion.getXBase(); x < processRegion.getXBase() + processRegion.getFloatWidth(); x++) {
                double backgroundValue = (backgroundIp.getf(x, y) - electronicOffset) /
                        (maximumPixelValue - electronicOffset);
                double value = processor.getf(x, y) - electronicOffset;
                processor.setf(x, y, (float) Math.abs(value / backgroundValue));
            }
        }
        framesDone.incrementAndGet();
    }

    private void addInputParameterLog(LogBuilder builder) {
        if (image != null) {
            builder.addParameter("Image title", image.getTitle());
            if (image.getOriginalFileInfo() != null && image
                    .getOriginalFileInfo().directory != null)
            {
                builder.addParameter("Image directory", image
                        .getOriginalFileInfo().directory);
            }
        }
        else builder.addParameter("Dataset name", dataset.getName());

        builder.addParameter("Region", region);
        if (region.equals("ROI from image") && imageRoi != null) builder
                .addParameter("ROI from image", imageRoi.toString());
        builder.addParameter("Channel", channel);
        if (backgroundImage.getTitle() != null) builder.addParameter(
                "Background Image Title", backgroundImage.getTitle());
        if (backgroundImage.getOriginalFileInfo() != null && backgroundImage
                .getOriginalFileInfo().directory != null) {
            builder.addParameter("Background Image Directory", backgroundImage
                    .getOriginalFileInfo().directory);
        }
        builder.addParameter("Electronic offset", String.valueOf(electronicOffset));
        builder.addParameter("Thread count", nThreads);
    }

    public Dataset getDataset() {
        return this.dataset;
    }

    public void setDataset(Dataset dataset) {
        this.dataset = dataset;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getRegion() {
        return region;
    }

    public void setChannel(int channel) {
        this.channel = String.valueOf(channel);
    }

	public int getChannel() {
		return Integer.parseInt(channel);
	}

    public void setImage(ImagePlus image) {
        this.image = image;
    }

    public ImagePlus getImage() {
        return image;
    }

    public void setBackgroundImage(ImagePlus backgroundImage) {
        this.backgroundImage = backgroundImage;
    }

    public ImagePlus getBackgroundImage() {
        return backgroundImage;
    }

    public void setElectronicOffset(double electronicOffset) {
        this.electronicOffset = electronicOffset;
    }

    public double getElectronicOffset() {
        return electronicOffset;
    }

    public void setThreads(int nThreads) {
        this.nThreads = nThreads;
    }

    public int getThreads() {
        return this.nThreads;
    }
}
