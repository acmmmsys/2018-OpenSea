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


public class ClassificationRate {
    public Utils.MutableInt truePositives = new Utils.MutableInt();
    public Utils.MutableInt trueNegatives = new Utils.MutableInt();
    public Utils.MutableInt falsePositives = new Utils.MutableInt();
    public Utils.MutableInt falseNegatives = new Utils.MutableInt();

    public String toString() {
        double tp = truePositives.get();
        double tn = trueNegatives.get();
        double fp = falsePositives.get();
        double fn = falseNegatives.get();
        double precision = getPrecision(tp, fp);
        double recall = getRecall(tp, fn);
        double tnRate = getTrueNegativeRate(tn, fp);
        double accuracy = getAccuracy(tp, fp, tn, fn);
        double fpRate = getFalsePositiveRate(fp, tn);
        double fMeasure = getFmeasure(precision, recall);
        double mccMeasure = getMccMeasure(tp, fp, tn, fn);
        double weightedFMeasure = getWFM(tp, fp, tn, fn, fMeasure);

        return Utils.padRight(String.valueOf(truePositives.get()), 10)
                + Utils.padRight(String.valueOf(trueNegatives.get()), 10)
                + Utils.padRight(String.valueOf(falsePositives.get()), 10)
                + Utils.padRight(String.valueOf(falseNegatives.get()), 10)
                + Utils.padRight(String.format("%.6f", precision), 10)
                + Utils.padRight(String.format("%.6f", recall), 10)
                + Utils.padRight(String.format("%.6f", tnRate), 10)
                + Utils.padRight(String.format("%.6f", fpRate), 10)
                + Utils.padRight(String.format("%.6f", accuracy), 10)
                + Utils.padRight(String.format("%.6f", fMeasure), 10)
                + Utils.padRight(String.format("%.6f", weightedFMeasure), 10)
                + Utils.padRight(String.format("%.6f", mccMeasure), 10);
    }

    public static String getHeaders() {
        return Utils.padRight("TP", 10)
                + Utils.padRight("TN", 10)
                + Utils.padRight("FP", 10)
                + Utils.padRight("FN", 10)
                + Utils.padRight("Precision", 10)
                + Utils.padRight("Recall", 10)
                + Utils.padRight("TNRate", 10)
                + Utils.padRight("FPRate", 10)
                + Utils.padRight("Accuracy", 10)
                + Utils.padRight("FMeaseure", 10)
                + Utils.padRight("WFMeaseure", 10)
                + Utils.padRight("MccMeasure", 10);
    }


    //Mesures
    private static double getPrecision(double tp, double fp) {
        return tp / (tp + fp);
    }

    private static double getRecall(double tp, double fn) {
        return tp / (tp + fn);
    }

    private static double getTrueNegativeRate(double tn, double fp) {
        return tn / (tn + fp);
    }

    private static double getAccuracy(double tp, double fp, double tn, double fn) {
        return (tp + tn) / (tp + tn + fp + fn);
    }

    private static double getFalsePositiveRate(double fp, double tn) {
        return fp / (fp + tn);
    }

    private static double getFmeasure(double precision, double recall) {
        return 2 * ((precision * recall) / (precision + recall));
    }

    private static double getMccMeasure(double tp, double fp, double tn, double fn) {
        return ((tp * tn) - (fp * fn)) / Math.sqrt((tp + fp) * (tp + fn) * (tn + fp) * (tn + fn));
    }

    private static double getWFM(double tp, double fp, double tn, double fn, double fMeasure) {
        double allN = tp + fp + tn + fn;
        double nPrec = tn / (tn + fn);
        double nRec = tn / (tn + fp);
        double nF1 = 2 * (nPrec * nRec) / (nPrec + nRec);

        return (fMeasure * (tp + fn) + nF1 * (fp + tn)) / allN;
    }
}

