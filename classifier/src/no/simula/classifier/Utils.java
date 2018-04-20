/***********************************************************************
 * Copyright 2015 Zeno Albisser, Michael Riegler
 *
 * This file is part of OpenSea.
 *
 * OpenSea is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * OpenSea is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OpenSea.  If not, see <http://www.gnu.org/licenses/>.*
 ***********************************************************************/

package no.simula.classifier;

import java.awt.image.BufferedImage;
import java.io.Console;
import java.io.File;
import java.util.Comparator;
import java.util.PriorityQueue;

import net.semanticmetadata.lire.DocumentBuilder;
import org.apache.lucene.document.Document;
import org.opencv.core.Mat;

public class Utils {
    private static Boolean s_isConsole = null;
    private static final char ESC = 27;
    public static final String SAMPLE_TYPE_DESCRIPTOR_NAME = "sampleType";

    private static boolean isConsole() {
        if (s_isConsole == null)
            s_isConsole = (null != System.console());
        return s_isConsole;
    }
    public static String clearLine() {
        if (!isConsole())
            return "";

        return ESC + "[1;1H";
    }

    public static void clearScreen() {
        if (!isConsole())
            return;
        System.out.print(ESC + "[2J");
        System.out.flush();
    }

    public static class MutableInt {
        private int value = 0;
        public void increment() { ++value; }
        public int get() { return value; }
    }

    public static class MutableFloat {
        private float value = 0;
        private int increments = 0;
        public void increment(float diff) { value += diff; ++increments; }
        public float get() { return value; }
        public int getIncrements() { return increments; }
    }

    public static String padRight(String s, int n) {
        return String.format("%1$-" + n + "s", s);
    }


    private static SampleType getSampleTypeFromName(String imagePath) {
        char firstChar = imagePath.charAt(imagePath.lastIndexOf(File.separator) + 1);
        if (firstChar == 'p') return SampleType.POSITIVE;
        if (firstChar == 'n') return SampleType.NEGATIVE;
        return SampleType.INVALID;
    }

    public static SampleType getSampleTypeFromDocument(Document document) {
        String[] sampleTypes = document.getValues(SAMPLE_TYPE_DESCRIPTOR_NAME);
        if (sampleTypes.length > 0 && !sampleTypes[0].equals(SampleType.INVALID.name()))
            return SampleType.valueOf(sampleTypes[0]);
        String imagePath = document.getValues(DocumentBuilder.FIELD_NAME_IDENTIFIER)[0];
        return getSampleTypeFromName(imagePath);
    }

    public static class MaxHeapComparator implements Comparator<PrioritizedDocument> {
        public int compare(PrioritizedDocument x, PrioritizedDocument y)
        {
            float c = y.score - x.score;
            if (c < 0) return -1;
            if (c > 0) return 1;
            return 0;
        }
    }

    public static class PrioritizedDocument {
        Document document;
        float score;
        public PrioritizedDocument(float score, Document document) {
            this.score = score;
            this.document = document;
        }
    }

    public static class MaxHeap extends PriorityQueue<PrioritizedDocument> {
        private int maximumSize;
        public MaxHeap(int maximumSize) {
            super(maximumSize, new MaxHeapComparator());
            this.maximumSize = maximumSize;
        }

        public void insert(float score, Document document) {
            PrioritizedDocument top = peek();
            if (top == null || size() < maximumSize) {
                add(new PrioritizedDocument(score, document));
            } else if (top.score > score) {
                poll();
                add(new PrioritizedDocument(score, document));
            }
        }
    }

    public static String extractFileName(String filePath)
    {
        return filePath.substring(filePath.lastIndexOf(File.separator) + 1);
    }

    public static BufferedImage mat2Img(Mat img)
    {
        int width = img.cols();
        int height = img.rows();
        byte[] data = new byte[width * height * (int)img.elemSize()];
        img.get(0, 0, data);

        int type = 0;
        if(img.channels() == 1)
            type = BufferedImage.TYPE_BYTE_GRAY;
        else
            type = BufferedImage.TYPE_3BYTE_BGR;

        BufferedImage bufferedImage = new BufferedImage(width, height, type);
        bufferedImage.getRaster().setDataElements(0, 0, width, height, data);
        return bufferedImage;
    }

}
