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

package no.simula.indexer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import net.semanticmetadata.lire.DocumentBuilder;
import net.semanticmetadata.lire.DocumentBuilderFactory;
import net.semanticmetadata.lire.imageanalysis.*;
import net.semanticmetadata.lire.imageanalysis.joint.JointHistogram;
import net.semanticmetadata.lire.imageanalysis.joint.LocalBinaryPatternsAndOpponent;
import net.semanticmetadata.lire.imageanalysis.joint.RankAndOpponent;
import net.semanticmetadata.lire.imageanalysis.OpponentHistogram;
import net.semanticmetadata.lire.impl.ChainedDocumentBuilder;
import net.semanticmetadata.lire.impl.GenericDocumentBuilder;
import net.semanticmetadata.lire.utils.FileUtils;
import net.semanticmetadata.lire.utils.LuceneUtils;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.FSDirectory;

public class Indexer {
    static ExecutorService pool = null;
    static IndexWriter indexWriter = null;
    static Boolean silent = false;

    private static synchronized void protectedAddDocument(Document document) throws java.io.IOException {
       indexWriter.addDocument(document);
    }

    private static boolean deleteDirectory(File directory) {
        if (directory.isDirectory()) {
            String[] children = directory.list();
            for (int i = 0; i < children.length; i++) {
                boolean success = deleteDirectory(new File(directory, children[i]));
                if (!success) return false;
            }
        }
        return directory.delete();
    }

    private static final char ESC = 27;
    private static Boolean s_isConsole = null;
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

    private static int currentProgress = 0;
    private synchronized static void showProgress(int index, int numDocs) {
        if (silent) return;
        if (index < currentProgress) return;
        currentProgress = index;
        clearScreen();
        System.out.println(clearLine() + (int) (100.0 / (float) numDocs * (float) currentProgress) + "%");
    }

    public static void main(ArrayList<String> directories, ArrayList<String> featureNames, Boolean silent) throws IOException {
        try {
            Class.forName("javax.imageio.ImageIO");
            Class.forName("java.awt.color.ICC_ColorSpace");
            Class.forName("sun.java2d.cmm.lcms.LCMS");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            System.exit(1);
        }
        Indexer.silent = silent;

        for (String samplePath : directories) {
            System.out.println("Indexing images in " + samplePath);
            currentProgress=0;
            File f = new File(samplePath);
            if (!f.exists() || !f.isDirectory()) {
                System.out.println("No directory given as first argument.");
                System.out.println("Run \"Indexer <directory>\" to index files of a directory.");
                System.exit(1);
            }

            List<String> images = Collections.synchronizedList(FileUtils.getAllImages(new File(samplePath), true));
            DocumentBuilder builder = getCustomDocumentBuilder(featureNames);

            String indexPath = samplePath + "/index";
            File indexFile = new File(indexPath);
            if (indexFile.exists()) {
                if (indexFile.isDirectory()) {
                    if (!deleteDirectory(indexFile)) {
                        System.out.println("Failed to delete old index (" + indexPath + ").");
                        System.exit(-1);
                    }
                    System.out.println("Old index file was deleted.");
                } else {
                    System.out.println("Index (" + indexPath + ") already exists, and I do not feel safe removing it for you.");
                    System.out.println("Please delete this file manually and re-run the indexer.");
                    System.exit(-1);
                }
            }

            IndexWriterConfig conf = new IndexWriterConfig(LuceneUtils.LUCENE_VERSION, new WhitespaceAnalyzer(LuceneUtils.LUCENE_VERSION));
            indexWriter = new IndexWriter(FSDirectory.open(new File(indexPath)), conf);

            int numCores = Runtime.getRuntime().availableProcessors() / 2; // most of our machines have hyper threading.
            if (numCores < 1) numCores = 1;
            final int numThreads = numCores;
            System.out.println("using " + numThreads + " threads for indexing.");
            pool = Executors.newFixedThreadPool(numThreads);

            long startTime = new Date().getTime();
            final int numImages = images.size();
            for (int runnableId = 0; runnableId < numThreads; ++runnableId) {
                final int thisRunnableId = runnableId; // lambdas can only access final objects
                Runnable r = () -> {
                    for (int index = thisRunnableId; index < numImages; index += numThreads) {
                        try {
                            String imageFilePath = images.get(index);
                            BufferedImage img = ImageIO.read(new FileInputStream(imageFilePath));
                            Document document = builder.createDocument(img, imageFilePath);
                            protectedAddDocument(document);
                        } catch (Exception e) {
                            System.err.println("Error reading image or indexing it.");
                            e.printStackTrace();
                            System.exit(-1);
                        }
                        showProgress(index, numImages);
                    }
                };
                pool.execute(r);
            }
            pool.shutdown();
            try {
                if (!pool.awaitTermination(365, TimeUnit.DAYS)) { // we are waiting for a whole year.
                    pool.shutdownNow();
                    if (!pool.awaitTermination(60, TimeUnit.SECONDS))
                        System.err.println("Pool did not terminate");
                }
            } catch (InterruptedException ie) {
                pool.shutdownNow();
                Thread.currentThread().interrupt();
            }

            long endTime = new Date().getTime();
            System.out.println("Indexing time: " + (endTime - startTime) / 1000.0f + " seconds");

            indexWriter.close();
        }
        System.out.println("Finished indexing.");
    }

    public static DocumentBuilder getCustomDocumentBuilder(ArrayList<String> featureNames) {
        ChainedDocumentBuilder builder = new ChainedDocumentBuilder();
        for (String featureName : featureNames)
            try {
                String className = getClassName(featureName);
                Class<? extends LireFeature> c = (Class<? extends LireFeature>) Class.forName(className);
                builder.addBuilder(new GenericDocumentBuilder(c, true));
            } catch (ClassNotFoundException e) {
                System.out.println("invalid feature name: " + featureName);
                e.printStackTrace();
                System.exit(-1);
            }
        return builder;
    }

    private static String getClassName(String featurename){
        String className = "net.semanticmetadata.lire.imageanalysis.";
        if (featurename.equals("JointHistogram") || featurename.equals("LocalBinaryPatternsAndOpponent") || featurename.equals("RankAndOpponent"))
            return className + "joint." + featurename;
        return className + featurename;
    }
}