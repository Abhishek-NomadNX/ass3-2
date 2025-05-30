package Assign32starter;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

import javax.imageio.ImageIO;

/**
 * Creates the square image grid used in Assignment 3
 * <p>
 * Usage: gradle Maker --args="<image to slice> <size>"
 */
public class GridMaker {

    public GridMaker() {

    }

    /**
     * Crops an image to the specified region. Creates a single image cell.
     *
     * @param bufferedImage the image that will be cropped
     * @param x             the upper left x coordinate that this region will start
     * @param y             the upper left y coordinate that this region will start
     * @param width         the width of the region that will be crop
     * @param height        the height of the region that will be crop
     * @return the image that was cropped
     * <p>
     * https://javapointers.com/java/java-core/crop-image-java/
     */
    public BufferedImage cropImage(BufferedImage bufferedImage, int x, int y, int width, int height) {
        BufferedImage croppedImage = bufferedImage.getSubimage(x, y, width, height);
        return croppedImage;
    }

    /**
     * Resize a buffered image to a specific size
     *
     * @param image  to resize
     * @param width  of result image
     * @param height of result image
     * @return resized image
     */
    public BufferedImage resize(BufferedImage image, int width, int height) {
        if (width < 1 || height < 1)
            return null;
        // create output buffer
        BufferedImage resizedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resizedImage.createGraphics();
        // draw at new size
        g.drawImage(image, 0, 0, width, height, null);
        g.dispose();
        return resizedImage;
    }

    public Stack<String> createImages(String filename, int dimension) throws IOException {
        Stack<String> images = new Stack<String>();
        File file = new File(filename);
        //extract the different path pieces - see below for definition
        FancyPath path = new FancyPath(file);
        if (!file.exists()) {
            System.err.println("Cannot find file: " + file.getAbsolutePath());
            System.exit(-1);
        }

        // Read in image and adjust
        BufferedImage img = ImageIO.read(file);
        int divisibleHeight = img.getHeight() - (img.getHeight() % dimension);
        int divisibleWidth = img.getWidth() - (img.getWidth() % dimension);
        img = resize(img, divisibleWidth, divisibleHeight);

        // calculate crop size
        int cellHeight = divisibleHeight / dimension;
        int cellWidth = divisibleWidth / dimension;

        String oldFilename = path.getFilename();
        // for each crop section
        for (int r = 0; r < dimension; ++r) {
            for (int c = 0; c < dimension; ++c) {
                // crop and output
                BufferedImage output = cropImage(img, c * cellWidth, r * cellHeight, cellWidth, cellHeight);
                images.push(oldFilename + "_" + r + "_" + c + ".jpg");
                path.setFilename(oldFilename + "_" + r + "_" + c);
                path.setExtension("jpg");
                File pathFile = new File(path.toString());
                ImageIO.write(output, "jpg", pathFile);
            }
        }
        // finish with useful info
        System.out.println("Output image dimension: " + new Dimension(img.getWidth(), img.getHeight()));
        System.out.println("Cell output dimension: " + new Dimension(cellWidth, cellHeight));
        return images;
    }

    /**
     * Tokenizes and analyzes a file path to allow for manipulation
     */
    public class FancyPath {
        // determine *nix vs Windows
        private String delimiter;
        // whole original path
        private String absolutePath;
        // path up to file
        private String folderPath;
        // specific file name
        private String filename;
        // specific file extension
        private String extension;

        public FancyPath(File file) {
            absolutePath = file.getAbsolutePath();
            // *nix or windows?
            delimiter = absolutePath.startsWith("/") ? "/" : "\\";

            folderPath = absolutePath.substring(0, absolutePath.lastIndexOf(delimiter) + 1);

            String filenameWithExt = absolutePath.substring(absolutePath.lastIndexOf(delimiter) + 1);

            int lastPeriod = filenameWithExt.lastIndexOf('.');
            if (lastPeriod > 0) { // has extension?
                // separate extension from filename
                filename = filenameWithExt.substring(0, lastPeriod);
                extension = filenameWithExt.substring(lastPeriod + 1);
            } else {
                // no extension
                filename = filenameWithExt;
                extension = "";
            }
        }

        /**
         * Gets filename
         *
         * @return filename
         */
        public String getFilename() {
            return filename;
        }

        /**
         * Sets filename
         *
         * @param newFilename to set to filename
         */
        public void setFilename(String newFilename) {
            filename = newFilename;
        }

        /**
         * Gets file extension
         *
         * @return file extension
         */
        public String getExtension() {
            return extension;
        }

        /**
         * Sets file extension
         *
         * @param newExtension to set file extension
         */
        public void setExtension(String newExtension) {
            extension = newExtension;
        }

        /**
         * Get built file and folder path
         */
        @Override
        public String toString() {
            return folderPath + filename + "." + extension;
        }
    }
}
