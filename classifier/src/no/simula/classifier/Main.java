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

import org.opencv.core.Core;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;

public class Main {

    private static HashMap<String, SampleType> classifierIndices = new HashMap<String, SampleType>();
    private static ArrayList<String> imageFeatures = new ArrayList<String>();
    private static HashMap<String, SampleType> inputDataIndices = new HashMap<String, SampleType>();
    private static MeasureType measureType = MeasureType.COUNT;
    private static String inputVideo = null;
    private static Boolean silent = false;

    public static void printUsage() {
        String execPath = Main.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        File userDir = new File(System.getProperty("user.dir"));
        File execFile = new File(execPath);
        String relativePath = userDir.toURI().relativize(execFile.toURI()).getPath();
        System.out.println("usage: " + relativePath + " -i /to/be/classified/index -c /classifier/index -f feature");
        System.out.println("       -c | -classifierIndex      Previously indexed training data.");
        System.out.println("                                  It is possible to provide indices of multiple training datasets.");
        System.out.println("       -p | -posClassifierIndex   Previously indexed training data, containing positives only.");
        System.out.println("                                  It is possible to provide indices of multiple training datasets.");
        System.out.println("       -n | -negClassifierIndex   Previously indexed training data, containing negatives only.");
        System.out.println("                                  It is possible to provide indices of multiple training datasets.");
        System.out.println("       -i | -input                Previously indexed data to be classified.");
        System.out.println("                                  Any indexed file starting with 'p' is considered a positive sample.");
        System.out.println("                                  Any indexed file starting with 'n' is considered a negative sample.");
        System.out.println("       -P | -inputPositive        Previously indexed data to be classified.");
        System.out.println("                                  The generated metrics rely on this index only containing positive samples.");
        System.out.println("       -N | -inputNegative        Previously indexed data to be classified.");
        System.out.println("                                  The generated metrics rely on this index only containing negative samples.");
        System.out.println("       -v | -inputVideo           Video file to classify frame by frame.");
        System.out.println("       -f | -feature              A feature to use for classification.");
        System.out.println("                                  Multiple features can be provided.");
        System.out.println("                                  Possible features are for example: JCD, FCTH, EdgeHistogram, ...");
        System.out.println("                                  The respective feature must be present in any index provided.");
        System.out.println("       -m | -measure              The measure to use. (any of: classCount, weightedByRank,");
        System.out.println("                                  weightedByDistance, weightedByAverageDistance)");
        System.out.println("       -s | -silent               Do not print progress messages (true / false).");
        System.out.println("");
        System.out.println("       All command line options must always be used in pairs of option and value.");
        System.exit(-1);
    }

    public static void verifyArguments(String[] args) {
        int numArgs = args.length;
        if (numArgs % 2 == 1)
            printUsage();
        for (int i = 0; i < numArgs; i+=2) {
            String argument = args[i];
            String value = args[i+1];
            if (argument.equals("-c") || argument.equals("-classifierIndex")) {
                classifierIndices.put(value, SampleType.INVALID);
            } else if (argument.equals("-p") || argument.equals("-posClassifierIndex")) {
                classifierIndices.put(value, SampleType.POSITIVE);
            } else if (argument.equals("-n") || argument.equals("-negClassifierIndex")) {
                classifierIndices.put(value, SampleType.NEGATIVE);
            } else if (argument.equals("-P") || argument.equals("-inputPositive")) {
                inputDataIndices.put(value, SampleType.POSITIVE);
            } else if (argument.equals("-N") || argument.equals("-inputNegative")) {
                inputDataIndices.put(value, SampleType.NEGATIVE);
            } else if (argument.equals("-v") || argument.equals("-inputVideo")) {
                inputVideo = value;
            } else if (argument.equals("-f") || argument.equals("-feature")) {
                imageFeatures.add(value);
            } else if (argument.equals("-i") || argument.equals("-input")) {
                inputDataIndices.put(value, SampleType.INVALID);
            } else if (argument.equals("-m") || argument.equals("-measure")) {
                if (value.equals("classCount")) measureType = MeasureType.COUNT;
                else if (value.equals("weightedByRank")) measureType = MeasureType.WEIGHTED_COUNT;
                else if (value.equals("weightedByDistance")) measureType = MeasureType.WEIGHTED_SCORE;
                else if (value.equals("weightedByAverageDistance")) measureType = MeasureType.WEIGHTED_AVERAGE_SCORE;
                else {
                    System.out.println("illegal measure type.");
                    printUsage();
                }
            } else if (argument.equals("-s") || argument.equals("-silent")) {
                silent = (value.equals("true"));
            } else {
                printUsage();
            }
        }

        if (classifierIndices.isEmpty()) {
            System.out.println("At least a single classifier index is required.");
            printUsage();
        }

        if (inputDataIndices.isEmpty() && inputVideo == null) {
            System.out.println("At least a single input data index or an input video must be specified.");
            printUsage();
        }

        if (imageFeatures.isEmpty()) {
            System.out.println("An image feature to use for classification must be specified.");
            printUsage();
        }
    }

    public static void main(String[] args) throws IOException, IllegalAccessException, InstantiationException, ClassNotFoundException {
        try {
            Class.forName("javax.imageio.ImageIO");
            Class.forName("java.awt.color.ICC_ColorSpace");
            Class.forName("sun.java2d.cmm.lcms.LCMS");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            System.exit(1);
        }
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        long startTime = System.currentTimeMillis();
        try {
            verifyArguments(args);
            Classifier classifier = new Classifier(classifierIndices, silent);

            ClassificationList classificationList = null;
            if (inputVideo != null) {
                classificationList = classifier.classifyVideo(inputVideo, imageFeatures, measureType);
                classificationList.print();
                classificationList.exportJSON(inputVideo);
            } else {
                classificationList = classifier.classifyDataset(inputDataIndices, imageFeatures, measureType);
                classificationList.print();
                classificationList.createMetrics();
                classificationList.createHTML();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        float duration = (float)(System.currentTimeMillis() - startTime) / 1000f;
        System.out.println("duration: " + duration + "seconds.");

    }
}
