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

import net.semanticmetadata.lire.DocumentBuilder;
import org.apache.lucene.document.Document;

import java.util.HashMap;

public class Classification extends HashMap<String, SampleType> {
    private static void ASSERT_NEVER_REACHED() {
        System.out.println("SOMETHING WENT WRONG...YOU SHOULD NEVER SEE THIS!");
        System.exit(-1);
    }

    private String imagePath = null;
    private SampleType expectedSampleType = null;

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_BLACK = "\u001B[30m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_BLUE = "\u001B[34m";
    private static final String ANSI_PURPLE = "\u001B[35m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_WHITE = "\u001B[37m";

    public Classification(Document imageDocument) {
        imagePath = imageDocument.get(DocumentBuilder.FIELD_NAME_IDENTIFIER);
        expectedSampleType = Utils.getSampleTypeFromDocument(imageDocument);
    }

    public void insert(String featureName, SampleType sampleType) {
        put(featureName, sampleType);
    }

    public String getImagePath() {
        return imagePath;
    }

    void print() {
        System.out.print("\n" + Utils.extractFileName(imagePath) + " -> ");
        for (Entry<String, SampleType> entry : entrySet()) {
            String featureName = entry.getKey();
            SampleType detectedSampleType = entry.getValue();
            String color = ANSI_RESET;
            if (detectedSampleType == SampleType.POSITIVE)
                color = ANSI_BLUE;
            if (detectedSampleType != expectedSampleType)
                color = ANSI_RED;
            System.out.print(featureName + ":" + color + detectedSampleType.toString() + ANSI_RESET + " ");
        }
    }

    void evaluate(ClassificationMetrics metrics) {
        for (Entry<String, SampleType> entry : entrySet()) {
            String featureName = entry.getKey();
            SampleType detectedSampleType = entry.getValue();
            ClassificationRate rate = metrics.get(featureName);

            switch (detectedSampleType) {
                case POSITIVE:
                    if (detectedSampleType == expectedSampleType) {
                        rate.truePositives.increment();
                    } else {
                        rate.falsePositives.increment();
                    }
                    break;
                case NEGATIVE:
                    if (detectedSampleType == expectedSampleType) {
                        rate.trueNegatives.increment();
                    } else {
                        rate.falseNegatives.increment();
                    }
                    break;
                default:
                    ASSERT_NEVER_REACHED();
            }
        }

    }
}
