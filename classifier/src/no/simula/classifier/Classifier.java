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

import net.semanticmetadata.lire.AbstractImageSearcher;
import net.semanticmetadata.lire.DocumentBuilder;
import net.semanticmetadata.lire.ImageSearchHits;
import net.semanticmetadata.lire.imageanalysis.*;
import net.semanticmetadata.lire.imageanalysis.joint.JointHistogram;
import net.semanticmetadata.lire.imageanalysis.joint.LocalBinaryPatternsAndOpponent;
import net.semanticmetadata.lire.imageanalysis.joint.RankAndOpponent;
import net.semanticmetadata.lire.impl.BitSamplingImageSearcher;
import net.semanticmetadata.lire.impl.ChainedDocumentBuilder;
import net.semanticmetadata.lire.impl.GenericDocumentBuilder;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.store.MMapDirectory;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.highgui.Highgui;
import org.opencv.highgui.VideoCapture;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Classifier {
    private static final int maximumHits = 77;
    private static final int CV_CAP_PROP_POS_FRAMES = 1;
    private static final int CV_CAP_PROP_FRAME_COUNT = 7;
    private Boolean silent = false;
    private ExecutorService pool = null;
    private int currentProgress = 0;
    private int i_inputIndexReaders = 0;
    private int i_document = 0;
    private int i_totalDocument = 0;
    private int totalDocuments = 0;
    private int numThreads = 1;
    private boolean finishedReadingVideo = false;
    private ArrayList<AugmentedIndexReader> indexReaders = new ArrayList<AugmentedIndexReader>();
    private ArrayList<AugmentedIndexReader> inputIndexReaders = new ArrayList<AugmentedIndexReader>();
    private LinkedList<Document> videoFrames = new LinkedList<Document>();

    private class AugmentedIndexReader {
        public AugmentedIndexReader(IndexReader r, SampleType t) {
            indexReader = r;
            sampleType = t;
        }
        public IndexReader indexReader = null;
        public SampleType sampleType = SampleType.INVALID;

    }


    public Classifier(HashMap<String, SampleType> indexPaths, Boolean silent) throws IOException {
        this.silent = silent;
        numThreads = Runtime.getRuntime().availableProcessors() / 2; // most of our machines have hyper threading.
        if (numThreads < 1) numThreads = 1;
        System.out.println("using " + numThreads + " threads for classifying.");
        Iterator it = indexPaths.entrySet().iterator();
        while (it.hasNext()) {
            HashMap.Entry indexLocationInformation = (HashMap.Entry)it.next();
            String indexPath = (String)indexLocationInformation.getKey();
            SampleType indexSampleType = (SampleType)indexLocationInformation.getValue();
            IndexReader indexReader = DirectoryReader.open(MMapDirectory.open(new File(indexPath)));
            indexReaders.add(new AugmentedIndexReader(indexReader, indexSampleType));
        }
    }

    public class SearchProvider extends BitSamplingImageSearcher {
        SearchProvider(int maximumHits, String featureName, String featureDescriptor) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
            super(maximumHits
                    , featureDescriptor
                    , featureDescriptor + "_hash"
                    , (LireFeature) Class.forName(getClassName(featureName)).newInstance()
                    , 1000);
            m_featureName = featureName;
        };
        private String m_featureName;
        public String featureName() { return m_featureName; }
    }

    private void initializeInputReaders(HashMap<String, SampleType> inputDataIndices) {
        Iterator it = inputDataIndices.entrySet().iterator();
        while (it.hasNext()) {
            HashMap.Entry indexLocationInformation = (HashMap.Entry)it.next();
            String inputDataPath = (String)indexLocationInformation.getKey();
            SampleType indexSampleType = (SampleType)indexLocationInformation.getValue();
            AugmentedIndexReader augmentedReader = null;
            try {
                augmentedReader = new AugmentedIndexReader(DirectoryReader.open(MMapDirectory.open(new File((String) inputDataPath))), indexSampleType);
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(-1);
            }
            inputIndexReaders.add(augmentedReader);
            totalDocuments += augmentedReader.indexReader.numDocs();
        }
    }

    private synchronized Document getNextDocument() throws IOException {
        while (i_inputIndexReaders < inputIndexReaders.size()) {
            AugmentedIndexReader augmentedReader = inputIndexReaders.get(i_inputIndexReaders);
            if (i_document < augmentedReader.indexReader.numDocs()) {
                updateProgress(i_totalDocument++);
                Document d = augmentedReader.indexReader.document(i_document++);
                addSampleTypeToDocument(d, augmentedReader.sampleType);
                return d;
            } else {
                ++i_inputIndexReaders;
                i_document = 0;
            }
        }
        return null;
    }

    private void updateProgress(int i_document) {
        showProgress(i_document, totalDocuments);
    }

    synchronized private void incrementProcessedFramesCount()
    {
        showProgress(i_totalDocument++, totalDocuments);
    }

    private ArrayList<SearchProvider> setupSearchProviders(ArrayList<String> featureNames) {
        ArrayList<SearchProvider> searchProviders = new ArrayList<SearchProvider>(featureNames.size());
        for (String featureName : featureNames) {
            try {
                String className = getClassName(featureName);
                Class<? extends LireFeature> c = (Class<? extends LireFeature>) Class.forName(className);
                String featureDescriptor = c.newInstance().getFieldName();
                SearchProvider searchProvider = new SearchProvider(maximumHits, featureName, featureDescriptor);
                searchProviders.add(searchProvider);
            } catch(Exception e) {
                e.printStackTrace();
                System.exit(-1);
            }
        }
        return searchProviders;
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
                System.exit(-1);
                e.printStackTrace();
            }
        return builder;
    }

    private void enqueueVideoFrame(Document doc) {
        synchronized (videoFrames) {
            videoFrames.addFirst(doc);
            videoFrames.notify();
        }
    }
    private Document dequeueVideoFrame() throws InterruptedException {
        synchronized(videoFrames) {
            while (videoFrames.isEmpty()) {
                if (finishedReadingVideo)
                    return null;
                videoFrames.wait();
            }
            return videoFrames.removeLast();
        }
    }

    public ClassificationList classifyVideo(String inputVideo, ArrayList<String> featureNames, MeasureType measureType) throws IOException {
        ClassificationList classificationList = new ClassificationList();
        VideoCapture capture = new VideoCapture(inputVideo);
        totalDocuments = (int)capture.get(CV_CAP_PROP_FRAME_COUNT);
        Mat frameMat = new Mat();
        DocumentBuilder builder = getCustomDocumentBuilder(featureNames);
        pool = Executors.newFixedThreadPool(numThreads);

        Runnable videoRead = () -> {
            while (capture.read(frameMat)) {
                BufferedImage frame = Utils.mat2Img(frameMat);
                try {
                    double pos = capture.get(CV_CAP_PROP_POS_FRAMES);
                    Document doc = builder.createDocument(frame, String.valueOf((int)pos));
                    enqueueVideoFrame(doc);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            synchronized(videoFrames) {
                finishedReadingVideo = true;
                videoFrames.notifyAll();
            }
        };
        pool.execute(videoRead);

        for (int runnableId = 0; runnableId < numThreads; ++runnableId) {
            Runnable r = () -> {
                ArrayList<SearchProvider> searchProviders = setupSearchProviders(featureNames);
                try {
                    while (true) {
                        Document imageDocument = dequeueVideoFrame();
                        if (imageDocument == null) break;
                        incrementProcessedFramesCount();

                        Classification classification = new Classification(imageDocument);

                        float lateFusionValues[] = new float[SampleType.values().length];

                        for (SearchProvider searchProvider : searchProviders) {
                            SampleInformation detectedSampleInfo = getMatchingSampleTypeForDocument(imageDocument, searchProvider, measureType);
                            lateFusionValues[detectedSampleInfo.type.ordinal()] += detectedSampleInfo.confidence;
                            classification.insert(searchProvider.featureName(), detectedSampleInfo.type);
                        }

                        classification.insert("LateFusion", lateFusionValues[SampleType.POSITIVE.ordinal()] > lateFusionValues[SampleType.NEGATIVE.ordinal()] ? SampleType.POSITIVE : SampleType.NEGATIVE);
                        classificationList.insert(classification);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    System.exit(-1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    System.exit(-1);
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
        return classificationList;
    }

    public ClassificationList classifyDataset(HashMap<String, SampleType> inputDataIndices, ArrayList<String> featureNames, MeasureType measureType) throws IOException {
        initializeInputReaders(inputDataIndices);
        ClassificationList classificationList = new ClassificationList();

        pool = Executors.newFixedThreadPool(numThreads);

        for (int runnableId = 0; runnableId < numThreads; ++runnableId) {
            Runnable r = () -> {
                ArrayList<SearchProvider> searchProviders = setupSearchProviders(featureNames);
                try {
                    while (true) {
                        Document imageDocument = getNextDocument();
                        if (imageDocument == null) break;

                        Classification classification = new Classification(imageDocument);

                        float lateFusionValues[] = new float[SampleType.values().length];

                        for (SearchProvider searchProvider : searchProviders) {
                            SampleInformation detectedSampleInfo = getMatchingSampleTypeForDocument(imageDocument, searchProvider, measureType);
                            lateFusionValues[detectedSampleInfo.type.ordinal()] += detectedSampleInfo.confidence;
                            classification.insert(searchProvider.featureName(), detectedSampleInfo.type);
                        }

                        classification.insert("LateFusion", lateFusionValues[SampleType.POSITIVE.ordinal()] > lateFusionValues[SampleType.NEGATIVE.ordinal()] ? SampleType.POSITIVE : SampleType.NEGATIVE);
                        classificationList.insert(classification);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    System.exit(-1);
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
        return classificationList;
    }

    private synchronized void showProgress(int index, int numDocs) {
        if (silent) return;
        if (index < currentProgress) return;
        int newValue = (int) (100.0 / (float) numDocs * (float) index);
        if (currentProgress == newValue) return;
        currentProgress = newValue;
        Utils.clearScreen();
        System.out.println(Utils.clearLine() + newValue + "%");
    }

    private class SampleInformation {
        SampleType type = SampleType.INVALID;
        Float confidence = 0f;
    }
    public SampleInformation getMatchingSampleTypeForDocument(Document document, AbstractImageSearcher searcher, MeasureType measureType) throws IOException {
        HashMap<SampleType, Utils.MutableFloat> typeScore = new HashMap<SampleType, Utils.MutableFloat>();
        for (SampleType type : SampleType.values()) typeScore.put(type, new Utils.MutableFloat());

        Utils.MaxHeap matchingDocuments = new Utils.MaxHeap(maximumHits);

        for (AugmentedIndexReader augmentedReader : indexReaders) {
            ImageSearchHits imageSearchHits = searcher.search(document, augmentedReader.indexReader);
            for (int i = 0; i < imageSearchHits.length() && i < maximumHits; i++) {
                Document matchingDocument = imageSearchHits.doc(i);
                addSampleTypeToDocument(matchingDocument, augmentedReader.sampleType);
                float score = imageSearchHits.score(i);
                matchingDocuments.insert(score, matchingDocument);
            }
        }

        int numMatchingDocuments = matchingDocuments.size();
        while (!matchingDocuments.isEmpty()) {
            Utils.PrioritizedDocument prioritizedDocument = matchingDocuments.poll();
            SampleType sampleType = Utils.getSampleTypeFromDocument(prioritizedDocument.document);
            switch (measureType) {
                case COUNT: {
                    typeScore.get(sampleType).increment(1);
                    break;
                }
                case WEIGHTED_COUNT: {
                    typeScore.get(sampleType).increment(1.0f / ((float) matchingDocuments.size() + 1.0f));
                    break;
                }
                case WEIGHTED_SCORE:
                case WEIGHTED_AVERAGE_SCORE: {
                    float weight = 1.0f / ((float) matchingDocuments.size() + 1.0f);
                    // prioritizedDocument.score has a range from 0 to MAX_FLOAT. Where 0 is a perfect match.
                    typeScore.get(sampleType).increment(prioritizedDocument.score * weight);
                    break;
                }
            }
        }

        SampleType highestScoreType = SampleType.INVALID;
        SampleType lowestScoreType = SampleType.INVALID;
        float highestScore = 0;
        float lowestScore = Float.MAX_VALUE;
        Iterator it = typeScore.entrySet().iterator();
        while (it.hasNext()) {
            HashMap.Entry pair = (HashMap.Entry)it.next();
            if (pair.getKey() == SampleType.INVALID) continue; // This score will always be 0, and we don't want that to interfere.
            Utils.MutableFloat score = (Utils.MutableFloat)pair.getValue();
            float f = score.get();
            if (measureType == MeasureType.WEIGHTED_AVERAGE_SCORE)
                f = f / score.getIncrements();
            if (f > highestScore) {
                highestScore = f;
                highestScoreType = (SampleType) pair.getKey();
            }
            if (f < lowestScore) {
                lowestScore = f;
                lowestScoreType = (SampleType) pair.getKey();
            }
        }

        // calculate the confidence in the result
        SampleInformation info = new SampleInformation();
        switch (measureType) {
            case COUNT:
            case WEIGHTED_COUNT:
                info.confidence = (float)typeScore.get(highestScoreType).getIncrements() / numMatchingDocuments;
                info.type = highestScoreType;
                break;
            case WEIGHTED_SCORE:
            case WEIGHTED_AVERAGE_SCORE:
                info.confidence = (float)typeScore.get(lowestScoreType).getIncrements() / numMatchingDocuments;
                info.type = lowestScoreType;
                break;
        }
        return info;
    }

    private void addSampleTypeToDocument(Document matchingDocument, SampleType sampleType) {
        matchingDocument.add(new StoredField(Utils.SAMPLE_TYPE_DESCRIPTOR_NAME, sampleType.name()));
    }

    private static String getClassName(String featurename){
        String className = "net.semanticmetadata.lire.imageanalysis.";
        if (featurename.equals("JointHistogram") || featurename.equals("LocalBinaryPatternsAndOpponent") || featurename.equals("RankAndOpponent"))
            return className + "joint." + featurename;
        return className + featurename;
    }

}
